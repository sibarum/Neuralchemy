package lab.graph.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import lab.dsl.Dsl;
import lab.dsl.ast.Artifact;
import lab.graph.BuiltinKinds;
import lab.graph.DslKinds;
import lab.graph.KindRegistry;
import lab.graph.Network;
import lab.graph.Node;
import lab.graph.PortRef;
import lab.graph.Value;

class EvaluatorTest {

    private static final float EPS = 1e-5f;

    @Test void emptyNetworkProducesEmptyValues() {
        Network net = new Network();
        Map<PortRef, Value> v = new Evaluator(net).step();
        assertTrue(v.isEmpty());
    }

    @Test void unwireNetworkReturnsEmptyValues() {
        // builtin.add has two required inputs; leaving them unwired should suppress evaluation.
        Network net = new Network();
        net.addNode(KindRegistry.builtin().get(BuiltinKinds.ADD_S), 0, 0);
        Map<PortRef, Value> v = new Evaluator(net).step();
        assertTrue(v.isEmpty());
    }

    @Test void sliderConstantAddOutputForwardPass() {
        // (slider=2) + (slider=3) → add → output. Output captures the value via its input edge.
        Network net = new Network();
        Node a = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node b = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node addN = net.addNode(KindRegistry.builtin().get(BuiltinKinds.ADD_S), 0, 0);
        Node out = net.addNode(KindRegistry.builtin().get(BuiltinKinds.OUTPUT_S), 0, 0);

        a.state.put("value", new Value.Scalar(2));
        b.state.put("value", new Value.Scalar(3));

        net.connect(new PortRef(a.id, "value"),    new PortRef(addN.id, "a"));
        net.connect(new PortRef(b.id, "value"),    new PortRef(addN.id, "b"));
        net.connect(new PortRef(addN.id, "sum"),   new PortRef(out.id, "value"));

        Evaluator ev = new Evaluator(net);
        ev.step();
        Value v = ev.valueAt(addN.id, "sum");
        assertEquals(5, ((Value.Scalar) v).v(), EPS);
    }

    @Test void splitJoinRoundTrip() {
        // Build a vec2 from two sliders, split it, and check the components match.
        Network net = new Network();
        Node a = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node b = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node join = net.addNode(KindRegistry.builtin().get(BuiltinKinds.JOIN), 0, 0);
        Node split = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SPLIT), 0, 0);

        a.state.put("value", new Value.Scalar(7));
        b.state.put("value", new Value.Scalar(11));

        net.connect(new PortRef(a.id, "value"),    new PortRef(join.id, "a"));
        net.connect(new PortRef(b.id, "value"),    new PortRef(join.id, "b"));
        net.connect(new PortRef(join.id, "pair"),  new PortRef(split.id, "pair"));

        Evaluator ev = new Evaluator(net);
        ev.step();
        assertEquals(7,  ((Value.Scalar) ev.valueAt(split.id, "a")).v(), EPS);
        assertEquals(11, ((Value.Scalar) ev.valueAt(split.id, "b")).v(), EPS);
    }

    @Test void dslNeuronEvaluatesThroughResolver() {
        // Wire 4 sliders into a dsl.neuron.split_mix and read its outputs.
        KindRegistry registry = new KindRegistry();
        Artifact splitMix = Dsl.parse("""
                neuron split_mix {
                  in:    x, y
                  param: a, b
                  out:   u, v
                  u = a*x + b*y
                  v = b*x + a*y
                }
                """);
        DslKinds.sync(registry, List.of(splitMix));

        Network net = new Network(registry);
        Node sx = net.addNode(registry.get(BuiltinKinds.SLIDER), 0, 0);
        Node sy = net.addNode(registry.get(BuiltinKinds.SLIDER), 0, 0);
        Node sa = net.addNode(registry.get(BuiltinKinds.SLIDER), 0, 0);
        Node sb = net.addNode(registry.get(BuiltinKinds.SLIDER), 0, 0);
        Node mix = net.addNode(registry.get("dsl.neuron.split_mix"), 0, 0);

        sx.state.put("value", new Value.Scalar(2));
        sy.state.put("value", new Value.Scalar(3));
        sa.state.put("value", new Value.Scalar(4));
        sb.state.put("value", new Value.Scalar(5));

        net.connect(new PortRef(sx.id, "value"), new PortRef(mix.id, "x"));
        net.connect(new PortRef(sy.id, "value"), new PortRef(mix.id, "y"));
        net.connect(new PortRef(sa.id, "value"), new PortRef(mix.id, "a"));
        net.connect(new PortRef(sb.id, "value"), new PortRef(mix.id, "b"));

        DslResolver resolver = id -> id.equals("dsl.neuron.split_mix") ? splitMix : null;
        Evaluator ev = new Evaluator(net, resolver);
        ev.step();
        // u = 4*2 + 5*3 = 23
        // v = 5*2 + 4*3 = 22
        assertEquals(23, ((Value.Scalar) ev.valueAt(mix.id, "u")).v(), EPS);
        assertEquals(22, ((Value.Scalar) ev.valueAt(mix.id, "v")).v(), EPS);
    }

    @Test void dslActivationSurfacedAsScalarOutput() {
        KindRegistry registry = new KindRegistry();
        Artifact tanh2 = Dsl.parse("activation tanh_squared(x) = tanh(x) * tanh(x)");
        DslKinds.sync(registry, List.of(tanh2));

        Network net = new Network(registry);
        Node s = net.addNode(registry.get(BuiltinKinds.SLIDER), 0, 0);
        Node act = net.addNode(registry.get("dsl.activation.tanh_squared"), 0, 0);
        s.state.put("value", new Value.Scalar(0.5f));
        net.connect(new PortRef(s.id, "value"), new PortRef(act.id, "x"));

        DslResolver resolver = id -> id.equals("dsl.activation.tanh_squared") ? tanh2 : null;
        Evaluator ev = new Evaluator(net, resolver);
        ev.step();
        float t = (float) Math.tanh(0.5);
        assertEquals(t * t, ((Value.Scalar) ev.valueAt(act.id, "value")).v(), EPS);
    }

    @Test void changingSliderStateChangesNextStepResult() {
        Network net = new Network();
        Node s = net.addNode(KindRegistry.builtin().get(BuiltinKinds.SLIDER), 0, 0);
        Node addN = net.addNode(KindRegistry.builtin().get(BuiltinKinds.ADD_S), 0, 0);
        net.connect(new PortRef(s.id, "value"), new PortRef(addN.id, "a"));
        net.connect(new PortRef(s.id, "value"), new PortRef(addN.id, "b"));

        Evaluator ev = new Evaluator(net);
        s.state.put("value", new Value.Scalar(1));
        ev.step();
        assertEquals(2, ((Value.Scalar) ev.valueAt(addN.id, "sum")).v(), EPS);

        s.state.put("value", new Value.Scalar(10));
        ev.step();
        assertEquals(20, ((Value.Scalar) ev.valueAt(addN.id, "sum")).v(), EPS);
    }
}
