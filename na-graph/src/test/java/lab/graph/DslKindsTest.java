package lab.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lab.dsl.Dsl;
import lab.dsl.Type;
import lab.dsl.ast.Artifact;

class DslKindsTest {

    @Test void neuronInputsAndParamsBecomeInputPorts() {
        Artifact a = Dsl.parse("""
                neuron split_mix {
                  in:    x, y
                  param: a, b
                  out:   u, v
                  u = a*x + b*y
                  v = b*x + a*y
                }
                """);
        NodeKind k = DslKinds.kindOf(a);
        assertEquals("dsl.neuron.split_mix", k.id());
        // Inputs first, then params — index space is shared.
        assertEquals(4, k.inputs().size());
        assertEquals("x", k.inputs().get(0).name());
        assertEquals("y", k.inputs().get(1).name());
        assertEquals("a", k.inputs().get(2).name());
        assertEquals("b", k.inputs().get(3).name());
        assertEquals(2, k.outputs().size());
        assertEquals("u", k.outputs().get(0).name());
        assertEquals("v", k.outputs().get(1).name());
    }

    @Test void typedNeuronCarriesTypesThroughToPortSpecs() {
        Artifact a = Dsl.parse("""
                neuron complex_mix {
                  in:    z: complex
                  param: w: complex
                  out:   y: complex
                  y = w * z
                }
                """);
        NodeKind k = DslKinds.kindOf(a);
        assertEquals(Type.COMPLEX, k.input("z").type());
        assertEquals(Type.COMPLEX, k.input("w").type());
        assertEquals(Type.COMPLEX, k.output("y").type());
    }

    @Test void activationKindHasScalarOutput() {
        Artifact a = Dsl.parse("activation tanh_squared(x) = tanh(x) * tanh(x)");
        NodeKind k = DslKinds.kindOf(a);
        assertEquals("dsl.activation.tanh_squared", k.id());
        assertEquals(1, k.inputs().size());
        assertEquals(1, k.outputs().size());
        assertEquals(Type.SCALAR, k.output("value").type());
    }

    @Test void lossKindHasScalarLossOutput() {
        Artifact a = Dsl.parse("loss mse(yhat, y) = (yhat - y)^2");
        NodeKind k = DslKinds.kindOf(a);
        assertEquals("dsl.loss.mse", k.id());
        assertEquals(2, k.inputs().size());
        assertEquals("yhat", k.inputs().get(0).name());
        assertEquals("y",    k.inputs().get(1).name());
        assertEquals(Type.SCALAR, k.output("loss").type());
    }

    @Test void syncRegistersNewKindsAndRemovesGoneOnes() {
        KindRegistry reg = new KindRegistry();
        Artifact splitMix = Dsl.parse("""
                neuron split_mix {
                  in:    x, y
                  param: a, b
                  out:   u, v
                  u = a*x + b*y
                  v = b*x + a*y
                }
                """);
        Artifact mse = Dsl.parse("loss mse(yhat, y) = (yhat - y)^2");

        // First sync: both kinds appear.
        DslKinds.sync(reg, List.of(splitMix, mse));
        assertNotNull(reg.get("dsl.neuron.split_mix"));
        assertNotNull(reg.get("dsl.loss.mse"));

        // Drop split_mix from the workspace; sync removes its kind.
        List<String> dropped = DslKinds.sync(reg, List.of(mse));
        assertTrue(dropped.contains("dsl.neuron.split_mix"));
        assertEquals(null, reg.local().stream()
                .filter(k -> k.id().equals("dsl.neuron.split_mix"))
                .findAny().orElse(null));
    }

    @Test void neuronBecomesUsableAsAGraphNode() {
        // End-to-end: parse, derive a kind, register it, drop a node, wire it up.
        KindRegistry reg = new KindRegistry();
        Artifact a = Dsl.parse("""
                neuron passthrough {
                  in:  x
                  out: y
                  y = x
                }
                """);
        DslKinds.sync(reg, List.of(a));
        Network net = new Network(reg);
        Node passthroughNode = net.addNode(reg.get("dsl.neuron.passthrough"), 0, 0);
        Node slider = net.addNode(reg.get(BuiltinKinds.SLIDER), 0, 0);
        Node output = net.addNode(reg.get(BuiltinKinds.OUTPUT_S), 0, 0);

        assertTrue(net.connect(new PortRef(slider.id, "value"),
                               new PortRef(passthroughNode.id, "x")).ok());
        assertTrue(net.connect(new PortRef(passthroughNode.id, "y"),
                               new PortRef(output.id, "value")).ok());
        assertTrue(net.isRunnable(), net.validate().toString());
    }
}
