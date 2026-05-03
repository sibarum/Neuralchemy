package lab.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lab.dsl.Dsl;
import lab.dsl.ast.Artifact;
import lab.graph.BuiltinKinds;
import lab.graph.DslKinds;
import lab.graph.KindRegistry;
import lab.graph.Network;
import lab.graph.Node;
import lab.graph.PortRef;
import lab.graph.Value;

class ProjectRoundTripTest {

    @Test void emptyNetworkRoundTrips(@TempDir Path dir) {
        Network original = new Network(KindRegistry.builtin());
        original.viewport.set(10, 20, 1.5f);

        Path file = dir.resolve("empty.nclab");
        Project.save(file, original, "empty test", "");

        Project.Bundle b = Project.open(file, KindRegistry.builtin());
        Network restored = b.primaryNetwork();
        assertNotNull(restored);
        assertEquals(0, restored.nodeCount());
        assertEquals(0, restored.edgeCount());
        assertEquals(10, restored.viewport.x, 1e-6);
        assertEquals(20, restored.viewport.y, 1e-6);
        assertEquals(1.5f, restored.viewport.zoom, 1e-6);
    }

    @Test void networkOfBuiltinsRoundTripsExactly(@TempDir Path dir) {
        Network original = new Network(KindRegistry.builtin());
        Node a = original.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 100, 200);
        Node b = original.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 100, 300);
        Node addN = original.addNode(KindRegistry.builtin().get(BuiltinKinds.ADD_S), 300, 250);
        Node out = original.addNode(KindRegistry.builtin().get(BuiltinKinds.OUTPUT_S), 500, 250);

        a.state.put("value", new Value.Scalar(0.42f));
        b.state.put("value", new Value.Scalar(-0.13f));

        original.connect(new PortRef(a.id,    "value"), new PortRef(addN.id, "a"));
        original.connect(new PortRef(b.id,    "value"), new PortRef(addN.id, "b"));
        original.connect(new PortRef(addN.id, "sum"),   new PortRef(out.id,  "value"));

        Path file = dir.resolve("builtins.nclab");
        Project.save(file, original, "builtins test", "");

        Project.Bundle bundle = Project.open(file, KindRegistry.builtin());
        Network restored = bundle.primaryNetwork();

        assertEquals(4, restored.nodeCount());
        assertEquals(3, restored.edgeCount());

        // Node UUIDs preserved → we can look up by original ids.
        Node restoredA = restored.node(a.id);
        assertNotNull(restoredA);
        assertEquals(BuiltinKinds.SLIDER, restoredA.kind);
        assertEquals(100f, restoredA.positionX, 1e-6);
        assertEquals(new Value.Scalar(0.42f), restoredA.state.get("value"));

        Node restoredB = restored.node(b.id);
        assertEquals(new Value.Scalar(-0.13f), restoredB.state.get("value"));

        // Edges resolved against preserved UUIDs.
        assertTrue(restored.edges().stream()
                .anyMatch(e -> e.from().equals(new PortRef(a.id, "value"))
                        && e.to().equals(new PortRef(addN.id, "a"))));
    }

    @Test void networkWithDslKindRoundTrips(@TempDir Path dir) {
        // Build a workspace registry with split_mix registered.
        KindRegistry kinds = new KindRegistry();
        Artifact splitMix = Dsl.parse("""
                neuron split_mix {
                  in:    x, y
                  param: a, b
                  out:   u, v
                  u = a*x + b*y
                  v = b*x + a*y
                }
                """);
        DslKinds.sync(kinds, List.of(splitMix));

        Network original = new Network(kinds);
        Node sx  = original.addNode(kinds.get(BuiltinKinds.SLIDER), 0, 0);
        Node mix = original.addNode(kinds.get("dsl.neuron.split_mix"), 200, 0);
        original.connect(new PortRef(sx.id, "value"), new PortRef(mix.id, "x"));

        Path file = dir.resolve("dsl.nclab");
        Project.save(file, original, "dsl test", "");

        // Open with the same workspace registry — kinds resolve, edges restore.
        Network restored = Project.open(file, kinds).primaryNetwork();
        assertNotNull(restored.node(mix.id));
        assertEquals("dsl.neuron.split_mix", restored.node(mix.id).kind);
        assertEquals(1, restored.edgeCount());
    }

    @Test void manifestSurvivesUnknownFutureFields(@TempDir Path dir) throws Exception {
        // Hand-write a manifest with extra unknown fields, expect them to be skipped.
        String archive = """
                {
                  "format": "neuralchemy-lab",
                  "format_version": 1,
                  "name": "future",
                  "future_field": { "nested": "should be skipped" },
                  "contents": { "networks": [], "future_kind": "ignored" }
                }
                """;
        // We build a minimal zip manually to test the open path.
        Path file = dir.resolve("future.nclab");
        try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(file))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("manifest.json"));
            zip.write(archive.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Project.Bundle bundle = Project.open(file, KindRegistry.builtin());
        assertEquals("future", bundle.manifest().name());
        assertEquals(0, bundle.networksByPath().size());
    }

    @Test void unknownKindOnLoadIsSkippedNotRejected(@TempDir Path dir) {
        Network original = new Network(KindRegistry.builtin());
        original.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);

        Path file = dir.resolve("with-builtin.nclab");
        Project.save(file, original, "x", "");

        // Load with an empty registry chained off builtin — should still resolve the slider.
        Network restored = Project.open(file, new KindRegistry()).primaryNetwork();
        assertEquals(1, restored.nodeCount());
    }

    @Test void valueOfEachAlgebraTypeRoundTrips(@TempDir Path dir) {
        // Drive Value through every variant by pinning it on a slider's state map.
        Network original = new Network(KindRegistry.builtin());
        Node n = original.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        n.state.put("scalar", new Value.Scalar(1.5f));
        n.state.put("vec2",   new Value.Vec2(1f, 2f));
        n.state.put("complex", new Value.Complex(3f, 4f));
        n.state.put("split",  new Value.SplitComplex(5f, 6f));
        n.state.put("quat",   new Value.Quaternion(1f, 2f, 3f, 4f));
        n.state.put("coquat", new Value.Coquat(1f, 2f, 3f, 4f));
        n.state.put("mat",    new Value.Mat2(1f, 2f, 3f, 4f));
        n.state.put("name",   new Value.Str("hello"));
        n.state.put("count",  new Value.Int(42));

        Path file = dir.resolve("values.nclab");
        Project.save(file, original, "x", "");
        Network restored = Project.open(file, KindRegistry.builtin()).primaryNetwork();
        Node rn = restored.node(n.id);
        assertSame(Value.Scalar.class,        rn.state.get("scalar").getClass());
        assertEquals(new Value.Vec2(1, 2),    rn.state.get("vec2"));
        assertEquals(new Value.Complex(3, 4), rn.state.get("complex"));
        assertEquals(new Value.SplitComplex(5, 6), rn.state.get("split"));
        assertEquals(new Value.Quaternion(1, 2, 3, 4), rn.state.get("quat"));
        assertEquals(new Value.Coquat(1, 2, 3, 4), rn.state.get("coquat"));
        assertEquals(new Value.Mat2(1, 2, 3, 4), rn.state.get("mat"));
        assertEquals(new Value.Str("hello"),  rn.state.get("name"));
        assertEquals(new Value.Int(42),       rn.state.get("count"));
    }
}
