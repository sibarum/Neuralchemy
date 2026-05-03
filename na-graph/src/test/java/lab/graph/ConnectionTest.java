package lab.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConnectionTest {

    private final KindRegistry kinds = KindRegistry.builtin();

    private Node mk(Network net, String kindId) {
        return net.addNode(kinds.get(kindId), 0, 0);
    }

    // ─── Happy paths ────────────────────────────────────────────────────────

    @Test void compatibleScalarToScalarConnectsCleanly() {
        Network net = new Network();
        Node src = mk(net, BuiltinKinds.SLIDER);
        Node dst = mk(net, BuiltinKinds.OUTPUT_S);
        ConnectionResult r = net.connect(new PortRef(src.id, "value"), new PortRef(dst.id, "value"));
        assertTrue(r.ok(), r.reason());
        assertEquals(1, net.edgeCount());
    }

    @Test void scalarBroadcastsIntoAlgebraInputs() {
        // join's inputs are scalar — but if they were vec2, scalar broadcasting would let a
        // slider feed in. Let's prove the broadcast policy is alive with a synthetic kind.
        KindRegistry local = new KindRegistry();
        NodeKind sink = new NodeKind("test.sink.complex", "Sink",
                java.util.List.of(PortSpec.required("in", lab.dsl.Type.COMPLEX, 0)),
                java.util.List.of(),
                java.util.Map.of());
        local.register(sink);
        Network net = new Network(local);
        Node src = mk(net, BuiltinKinds.SLIDER);
        Node dst = net.addNode(sink, 0, 0);
        ConnectionResult r = net.connect(new PortRef(src.id, "value"), new PortRef(dst.id, "in"));
        assertTrue(r.ok(), r.reason());
    }

    @Test void fanOutFromOneOutputToManyInputsOk() {
        // builtin.add has two inputs, both scalar — feed both from one slider.
        Network net = new Network();
        Node src = mk(net, BuiltinKinds.SLIDER);
        Node add = mk(net, BuiltinKinds.ADD_S);
        assertTrue(net.connect(new PortRef(src.id, "value"), new PortRef(add.id, "a")).ok());
        assertTrue(net.connect(new PortRef(src.id, "value"), new PortRef(add.id, "b")).ok());
        assertEquals(2, net.edgeCount());
    }

    // ─── Failures from node-graph.md §Constraints ───────────────────────────

    @Test void typeMismatchRejected() {
        // Source is scalar (slider), destination is vec2 — split.pair input.
        Network net = new Network();
        Node src = mk(net, BuiltinKinds.SLIDER);
        Node dst = mk(net, BuiltinKinds.SPLIT);
        // scalar → vec2 is a broadcast pattern that wires don't support; should fail unless we
        // ever decide scalar→algebra is OK on its own (we don't — only inside operators).
        // Wait — Network.typesCompatible allows scalar→algebra. Build a real vec2-only mismatch:
        // join.pair (vec2 output) → output.value (scalar input) is a true type-error.
        Node join = mk(net, BuiltinKinds.JOIN);
        Node out  = mk(net, BuiltinKinds.OUTPUT_S);
        ConnectionResult r = net.connect(new PortRef(join.id, "pair"), new PortRef(out.id, "value"));
        assertFalse(r.ok());
        assertTrue(r.reason().contains("type mismatch"), r.reason());
    }

    @Test void secondEdgeIntoSameInputRejected() {
        Network net = new Network();
        Node a = mk(net, BuiltinKinds.SLIDER);
        Node b = mk(net, BuiltinKinds.SLIDER);
        Node out = mk(net, BuiltinKinds.OUTPUT_S);
        assertTrue(net.connect(new PortRef(a.id, "value"), new PortRef(out.id, "value")).ok());
        ConnectionResult r = net.connect(new PortRef(b.id, "value"), new PortRef(out.id, "value"));
        assertFalse(r.ok());
        assertTrue(r.reason().contains("already wired"), r.reason());
    }

    @Test void cycleRejected() {
        // a.add.b ┐
        // b.add ──┘  — feed add's output back into a's input (also via add) → cycle.
        // Easier: two add nodes feeding each other.
        Network net = new Network();
        Node x = mk(net, BuiltinKinds.ADD_S);
        Node y = mk(net, BuiltinKinds.ADD_S);
        // x.sum -> y.a   (ok)
        assertTrue(net.connect(new PortRef(x.id, "sum"), new PortRef(y.id, "a")).ok());
        // y.sum -> x.a   (would form a cycle x → y → x)
        ConnectionResult r = net.connect(new PortRef(y.id, "sum"), new PortRef(x.id, "a"));
        assertFalse(r.ok());
        assertTrue(r.reason().contains("cycle"), r.reason());
    }

    @Test void selfLoopRejected() {
        Network net = new Network();
        Node a = mk(net, BuiltinKinds.ADD_S);
        ConnectionResult r = net.connect(new PortRef(a.id, "sum"), new PortRef(a.id, "a"));
        assertFalse(r.ok());
        assertTrue(r.reason().contains("self-loop"), r.reason());
    }

    @Test void unknownPortNameRejected() {
        Network net = new Network();
        Node src = mk(net, BuiltinKinds.SLIDER);
        Node dst = mk(net, BuiltinKinds.OUTPUT_S);
        ConnectionResult r = net.connect(new PortRef(src.id, "nope"), new PortRef(dst.id, "value"));
        assertFalse(r.ok());
        assertTrue(r.reason().contains("not an output"), r.reason());
    }

    @Test void canConnectDoesNotMutate() {
        Network net = new Network();
        Node src = mk(net, BuiltinKinds.SLIDER);
        Node dst = mk(net, BuiltinKinds.OUTPUT_S);
        net.canConnect(new PortRef(src.id, "value"), new PortRef(dst.id, "value"));
        assertEquals(0, net.edgeCount());
    }
}
