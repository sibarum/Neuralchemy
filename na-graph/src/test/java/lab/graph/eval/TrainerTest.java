package lab.graph.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lab.graph.BuiltinKinds;
import lab.graph.KindRegistry;
import lab.graph.Network;
import lab.graph.Node;
import lab.graph.PortRef;
import lab.graph.Value;

/**
 * Trainer correctness on small built-in networks. Each test builds a graph by hand, runs N
 * SGD steps, and asserts the trained parameter converged to the analytic optimum.
 */
class TrainerTest {

    private static final KindRegistry K = KindRegistry.builtin();

    /**
     * Single-parameter regression: learn {@code w} so that {@code w * 1 ≈ 5}.
     * Optimum: w = 5. Loss surface is a parabola centered at 5, so plain SGD converges fast.
     */
    @Test void learnableConvergesOnConstantTarget() {
        Network net = new Network();
        Node x      = net.addNode(K.get(BuiltinKinds.CONSTANT_S), 0, 0);
        Node target = net.addNode(K.get(BuiltinKinds.CONSTANT_S), 0, 0);
        Node w      = net.addNode(K.get(BuiltinKinds.LEARNABLE_S), 0, 0);
        Node mul    = net.addNode(K.get(BuiltinKinds.MUL_S), 0, 0);
        Node loss   = net.addNode(K.get(BuiltinKinds.LOSS_MSE_S), 0, 0);

        x.state.put("value",      new Value.Scalar(1f));
        target.state.put("value", new Value.Scalar(5f));
        w.state.put("value",      new Value.Scalar(0f));

        net.connect(new PortRef(x.id,   "value"),   new PortRef(mul.id,  "a"));
        net.connect(new PortRef(w.id,   "value"),   new PortRef(mul.id,  "b"));
        net.connect(new PortRef(mul.id, "product"), new PortRef(loss.id, "pred"));
        net.connect(new PortRef(target.id, "value"), new PortRef(loss.id, "target"));

        Trainer t = new Trainer(net, new PortRef(loss.id, "loss"));

        float lossVal = Float.NaN;
        for (int i = 0; i < 200; i++) lossVal = t.step(0.05f);

        Value learned = w.state.get("value");
        assertTrue(learned instanceof Value.Scalar);
        float wFinal = ((Value.Scalar) learned).v();
        assertEquals(5f, wFinal, 1e-2f);
        assertTrue(lossVal < 1e-4f, "loss should be near zero, got " + lossVal);
    }

    /**
     * Two-parameter linear regression on three (x, target) samples. Optimum is the OLS
     * coefficients; we just verify the trainer reduces loss substantially.
     */
    @Test void twoParameterDescentReducesLoss() {
        Network net = new Network();
        Node xNode      = net.addNode(K.get(BuiltinKinds.CONSTANT_S), 0, 0);
        Node targetNode = net.addNode(K.get(BuiltinKinds.CONSTANT_S), 0, 0);
        Node w          = net.addNode(K.get(BuiltinKinds.LEARNABLE_S), 0, 0);
        Node b          = net.addNode(K.get(BuiltinKinds.LEARNABLE_S), 0, 0);
        Node mul        = net.addNode(K.get(BuiltinKinds.MUL_S), 0, 0);
        Node add        = net.addNode(K.get(BuiltinKinds.ADD_S), 0, 0);
        Node loss       = net.addNode(K.get(BuiltinKinds.LOSS_MSE_S), 0, 0);

        w.state.put("value", new Value.Scalar(0f));
        b.state.put("value", new Value.Scalar(0f));

        net.connect(new PortRef(xNode.id, "value"),   new PortRef(mul.id, "a"));
        net.connect(new PortRef(w.id,     "value"),   new PortRef(mul.id, "b"));
        net.connect(new PortRef(mul.id,   "product"), new PortRef(add.id, "a"));
        net.connect(new PortRef(b.id,     "value"),   new PortRef(add.id, "b"));
        net.connect(new PortRef(add.id,   "sum"),     new PortRef(loss.id, "pred"));
        net.connect(new PortRef(targetNode.id, "value"), new PortRef(loss.id, "target"));

        Trainer t = new Trainer(net, new PortRef(loss.id, "loss"));

        // Tiny dataset: y = 2x + 1 → samples (0, 1), (1, 3), (2, 5).
        float[][] samples = { {0, 1}, {1, 3}, {2, 5} };

        // Initial loss.
        float initialLoss = singleSampleLoss(t, xNode, targetNode, samples);

        for (int epoch = 0; epoch < 600; epoch++) {
            for (float[] s : samples) {
                xNode.state.put("value",      new Value.Scalar(s[0]));
                targetNode.state.put("value", new Value.Scalar(s[1]));
                t.step(0.05f);
            }
        }

        float finalLoss = singleSampleLoss(t, xNode, targetNode, samples);
        assertTrue(finalLoss < initialLoss * 1e-3f,
                "loss should drop sharply: initial=" + initialLoss + ", final=" + finalLoss);

        // Verify learned weights are near the analytic optimum (w=2, b=1).
        float wFinal = ((Value.Scalar) w.state.get("value")).v();
        float bFinal = ((Value.Scalar) b.state.get("value")).v();
        assertEquals(2f, wFinal, 0.05f);
        assertEquals(1f, bFinal, 0.05f);
    }

    /** Sum of per-sample MSE without taking another SGD step. */
    private static float singleSampleLoss(Trainer t, Node xNode, Node targetNode, float[][] samples) {
        Evaluator eval = new Evaluator(t.network(), DslResolver.NONE);
        float sum = 0f;
        for (float[] s : samples) {
            xNode.state.put("value",      new Value.Scalar(s[0]));
            targetNode.state.put("value", new Value.Scalar(s[1]));
            Value v = eval.step().get(t.lossPort());
            if (v instanceof Value.Scalar sv) sum += sv.v();
        }
        return sum;
    }

    /**
     * Train through a DSL activation. Goal: learn {@code w} so {@code tanh(w·x) ≈ target} where
     * {@code target ≈ tanh(2·1) = 0.964…}. This exercises the DSL backward path: differentiate
     * {@code tanh(x)} symbolically at parse-time, plug it into the chain rule at backward time.
     */
    @Test void dslActivationConvergesThroughBackward() {
        // Parse a tanh activation and register it so the network can use it as a node kind.
        lab.dsl.ast.Artifact.Activation tanhAct =
                (lab.dsl.ast.Artifact.Activation) lab.dsl.parse.Parser.parse(
                        "activation tanh_a(x) = tanh(x)\n");

        KindRegistry reg = new KindRegistry();
        lab.graph.NodeKind kind = lab.graph.DslKinds.kindOf(tanhAct);
        reg.register(kind);

        DslResolver resolver = id -> id.equals(kind.id()) ? tanhAct : null;

        Network net = new Network(reg);
        Node xNode      = net.addNode(reg.get(BuiltinKinds.CONSTANT_S),  0, 0);
        Node targetNode = net.addNode(reg.get(BuiltinKinds.CONSTANT_S),  0, 0);
        Node w          = net.addNode(reg.get(BuiltinKinds.LEARNABLE_S), 0, 0);
        Node mul        = net.addNode(reg.get(BuiltinKinds.MUL_S),       0, 0);
        Node act        = net.addNode(kind,                              0, 0);
        Node loss       = net.addNode(reg.get(BuiltinKinds.LOSS_MSE_S),  0, 0);

        xNode.state.put("value",      new Value.Scalar(1f));
        targetNode.state.put("value", new Value.Scalar((float) Math.tanh(2.0)));
        w.state.put("value",          new Value.Scalar(0.1f));

        net.connect(new PortRef(xNode.id, "value"),    new PortRef(mul.id, "a"));
        net.connect(new PortRef(w.id,     "value"),    new PortRef(mul.id, "b"));
        net.connect(new PortRef(mul.id,   "product"),  new PortRef(act.id, "x"));
        net.connect(new PortRef(act.id,   "value"),    new PortRef(loss.id, "pred"));
        net.connect(new PortRef(targetNode.id, "value"), new PortRef(loss.id, "target"));

        Trainer t = new Trainer(net, resolver, new PortRef(loss.id, "loss"));

        // Initial loss for the dropped-loss assertion below.
        float initialLoss = t.step(0f);    // run a step with lr=0 just to grab the forward loss

        for (int i = 0; i < 4000; i++) t.step(0.2f);

        float wFinal = ((Value.Scalar) w.state.get("value")).v();
        float finalLoss = t.step(0f);

        // tanh saturates so even w near the optimum gives many decimal places of loss decay
        // before w settles. Verify both: w moved meaningfully toward 2, and loss collapsed.
        assertTrue(wFinal > 1.5f, "w should move toward 2.0; got " + wFinal);
        assertTrue(finalLoss < initialLoss * 1e-3f,
                "loss should drop ≥ 3 orders of magnitude; initial=" + initialLoss
                        + " final=" + finalLoss);
    }
}
