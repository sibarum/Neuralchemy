package lab.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkTest {

    @Test void addNodeUsesKindDefaultStateAndAssignsId() {
        Network net = new Network();
        Node n = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        assertNotNull(n.id);
        assertEquals(BuiltinKinds.SLIDER, n.kind);
        assertEquals(new Value.Scalar(-1f), n.state.get("min"));
        assertEquals(new Value.Scalar( 1f), n.state.get("max"));
        assertEquals(new Value.Scalar( 0f), n.state.get("value"));
    }

    @Test void removeNodeAlsoRemovesItsEdges() {
        Network net = new Network();
        Node a = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node b = net.addNode(KindRegistry.builtin().get(BuiltinKinds.OUTPUT_S), 0, 0);
        ConnectionResult r = net.connect(new PortRef(a.id, "value"), new PortRef(b.id, "value"));
        assertTrue(r.ok());
        assertEquals(1, net.edgeCount());

        net.removeNode(a.id);
        assertEquals(1, net.nodeCount());
        assertEquals(0, net.edgeCount());
    }

    @Test void addingUnregisteredKindThrows() {
        Network net = new Network(new KindRegistry());     // empty registry on top of builtins
        // builtin.slider IS reachable through the parent registry — this is fine.
        net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);

        // A kind constructed locally that isn't registered anywhere should be rejected.
        NodeKind orphan = new NodeKind("custom.thing", "Thing",
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        assertThrows(IllegalStateException.class, () -> net.addNode(orphan, 0, 0));
    }

    @Test void edgesViewIsImmutable() {
        Network net = new Network();
        Node a = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node b = net.addNode(KindRegistry.builtin().get(BuiltinKinds.OUTPUT_S), 0, 0);
        net.connect(new PortRef(a.id, "value"), new PortRef(b.id, "value"));

        var view = net.edges();
        assertEquals(1, view.size());
        assertThrows(UnsupportedOperationException.class, () -> view.add(null));
    }

    @Test void kindOfReturnsRegistryEntry() {
        Network net = new Network();
        Node a = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        assertSame(KindRegistry.builtin().get(BuiltinKinds.SLIDER), net.kindOf(a));
    }

    @Test void bringToFrontReordersNodesIterationOrder() {
        Network net = new Network();
        Node a = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node b = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node c = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);

        // Initial order matches insertion.
        assertEquals(java.util.List.of(a.id, b.id, c.id),
                net.nodes().stream().map(n -> n.id).toList());

        // Bringing the middle node to front moves it last.
        assertTrue(net.bringToFront(b.id));
        assertEquals(java.util.List.of(a.id, c.id, b.id),
                net.nodes().stream().map(n -> n.id).toList());

        // Already on top — no change, returns false.
        assertFalse(net.bringToFront(b.id));
    }

    @Test void disconnectRemovesExactEdge() {
        Network net = new Network();
        Node a = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node b = net.addNode(KindRegistry.builtin().get(BuiltinKinds.OUTPUT_S), 0, 0);
        ConnectionResult r = net.connect(new PortRef(a.id, "value"), new PortRef(b.id, "value"));
        assertTrue(net.disconnect(r.edge()));
        assertFalse(net.disconnect(r.edge()));     // already gone
        assertEquals(0, net.edgeCount());
    }
}
