package lab.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TopologyTest {

    private final KindRegistry kinds = KindRegistry.builtin();

    private Node mk(Network net, String kindId) {
        return net.addNode(kinds.get(kindId), 0, 0);
    }

    @Test void emptyNetworkOrderIsEmpty() {
        assertEquals(List.of(), new Network().topologicalOrder());
    }

    @Test void linearChainOrderedFromSourceToSink() {
        // slider -> add.a   (slider also feeds add.b for the test to be runnable)
        // add.sum -> output
        Network net = new Network();
        Node s = mk(net, BuiltinKinds.SLIDER);
        Node a = mk(net, BuiltinKinds.ADD_S);
        Node o = mk(net, BuiltinKinds.OUTPUT_S);
        net.connect(new PortRef(s.id, "value"), new PortRef(a.id, "a"));
        net.connect(new PortRef(s.id, "value"), new PortRef(a.id, "b"));
        net.connect(new PortRef(a.id, "sum"),   new PortRef(o.id, "value"));

        List<UUID> order = net.topologicalOrder();
        assertEquals(3, order.size());
        assertEquals(s.id, order.get(0));
        assertEquals(a.id, order.get(1));
        assertEquals(o.id, order.get(2));
    }

    @Test void disconnectedComponentsAreBothIncluded() {
        Network net = new Network();
        Node s1 = mk(net, BuiltinKinds.SLIDER);
        Node s2 = mk(net, BuiltinKinds.SLIDER);
        // No edges — both nodes are independent sources.
        List<UUID> order = net.topologicalOrder();
        assertEquals(2, order.size());
        assertTrue(order.contains(s1.id));
        assertTrue(order.contains(s2.id));
    }

    @Test void fanInIsHandled() {
        Network net = new Network();
        Node s1 = mk(net, BuiltinKinds.SLIDER);
        Node s2 = mk(net, BuiltinKinds.SLIDER);
        Node a  = mk(net, BuiltinKinds.ADD_S);
        net.connect(new PortRef(s1.id, "value"), new PortRef(a.id, "a"));
        net.connect(new PortRef(s2.id, "value"), new PortRef(a.id, "b"));
        List<UUID> order = net.topologicalOrder();
        // a must come after both s1 and s2; the relative order of s1 and s2 is implementation-defined.
        assertTrue(order.indexOf(a.id) > order.indexOf(s1.id));
        assertTrue(order.indexOf(a.id) > order.indexOf(s2.id));
    }

    @Test void runnableCheckRequiresAllRequiredInputsWired() {
        Network net = new Network();
        Node s = mk(net, BuiltinKinds.SLIDER);
        Node a = mk(net, BuiltinKinds.ADD_S);
        // Wire only one input — `b` is left dangling.
        net.connect(new PortRef(s.id, "value"), new PortRef(a.id, "a"));
        List<String> issues = net.validate();
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("b"), issues.toString());
        assertFalse(net.isRunnable());
    }

    @Test void runnableCheckPassesWhenEverythingIsWired() {
        Network net = new Network();
        Node s = mk(net, BuiltinKinds.SLIDER);
        Node a = mk(net, BuiltinKinds.ADD_S);
        net.connect(new PortRef(s.id, "value"), new PortRef(a.id, "a"));
        net.connect(new PortRef(s.id, "value"), new PortRef(a.id, "b"));
        // ADD's output is unwired but it's an output; that's fine.
        assertTrue(net.isRunnable(), net.validate().toString());
    }
}
