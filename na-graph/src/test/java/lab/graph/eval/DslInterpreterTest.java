package lab.graph.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import lab.dsl.Dsl;
import lab.dsl.ast.Artifact;
import lab.graph.Value;

class DslInterpreterTest {

    private static final float EPS = 1e-5f;

    private static float scalar(Value v) { return ((Value.Scalar) v).v(); }

    @Test void splitMixComputesAxPlusByEtAl() {
        Artifact.Neuron n = (Artifact.Neuron) Dsl.parse("""
                neuron split_mix {
                  in:    x, y
                  param: a, b
                  out:   u, v
                  u = a*x + b*y
                  v = b*x + a*y
                }
                """);
        Map<String, Value> out = DslInterpreter.evalNeuron(n, Map.of(
                "x", new Value.Scalar(2),
                "y", new Value.Scalar(3),
                "a", new Value.Scalar(4),
                "b", new Value.Scalar(5)));
        assertEquals(2*4 + 3*5, scalar(out.get("u")), EPS);
        assertEquals(2*5 + 3*4, scalar(out.get("v")), EPS);
    }

    @Test void linearMixIsElementwise() {
        Artifact.Neuron n = (Artifact.Neuron) Dsl.parse("""
                neuron linear_mix {
                  in:    x, y
                  param: a, b
                  out:   u, v
                  u = a*x
                  v = b*y
                }
                """);
        Map<String, Value> out = DslInterpreter.evalNeuron(n, Map.of(
                "x", new Value.Scalar(2),
                "y", new Value.Scalar(3),
                "a", new Value.Scalar(7),
                "b", new Value.Scalar(11)));
        assertEquals(14, scalar(out.get("u")), EPS);
        assertEquals(33, scalar(out.get("v")), EPS);
    }

    @Test void complexMixAlgebraicMatchesScalarFormulation() {
        // Algebraic form: y = w * z
        Artifact.Neuron alg = (Artifact.Neuron) Dsl.parse("""
                neuron complex_mix {
                  in:    z: complex
                  param: w: complex
                  out:   y: complex
                  y = w * z
                }
                """);
        Value yAlg = DslInterpreter.evalNeuron(alg, Map.of(
                "z", new Value.Complex(2, 3),
                "w", new Value.Complex(4, 5))).get("y");

        // Scalar form: u = a*x - b*y, v = a*y + b*x
        Artifact.Neuron sc = (Artifact.Neuron) Dsl.parse("""
                neuron complex_mix_scalar {
                  in:    x, y
                  param: a, b
                  out:   u, v
                  u = a*x - b*y
                  v = a*y + b*x
                }
                """);
        Map<String, Value> scOut = DslInterpreter.evalNeuron(sc, Map.of(
                "x", new Value.Scalar(2), "y", new Value.Scalar(3),
                "a", new Value.Scalar(4), "b", new Value.Scalar(5)));

        Value.Complex cy = (Value.Complex) yAlg;
        assertEquals(scalar(scOut.get("u")), cy.re(), EPS);
        assertEquals(scalar(scOut.get("v")), cy.im(), EPS);
    }

    @Test void residSampleEvaluates() {
        Artifact.Neuron n = (Artifact.Neuron) Dsl.parse("""
                neuron resid {
                  in:    x: vec2
                  param: a, b: scalar
                  out:   y: vec2
                  y = vec2(a*x.x + b*x.y, b*x.x + a*x.y) + x
                }
                """);
        Value y = DslInterpreter.evalNeuron(n, Map.of(
                "x", new Value.Vec2(1, 2),
                "a", new Value.Scalar(3),
                "b", new Value.Scalar(4))).get("y");
        // y = vec2(3 + 8, 4 + 6) + (1, 2) = vec2(12, 12)
        assertEquals(new Value.Vec2(12, 12), y);
    }

    @Test void tanhSquaredActivation() {
        Artifact.Activation v = (Artifact.Activation) Dsl.parse(
                "activation tanh_squared(x) = tanh(x) * tanh(x)");
        float t = (float) Math.tanh(0.5);
        Value r = DslInterpreter.evalActivation(v, Map.of("x", new Value.Scalar(0.5f)));
        assertEquals(t * t, scalar(r), EPS);
    }

    @Test void mseScalarLoss() {
        Artifact.Loss l = (Artifact.Loss) Dsl.parse("loss mse(yhat, y) = (yhat - y)^2");
        Value r = DslInterpreter.evalLoss(l, Map.of(
                "yhat", new Value.Scalar(1.5f),
                "y",    new Value.Scalar(0.5f)));
        assertEquals(1.0f, scalar(r), EPS);
    }

    @Test void mseVecLossUsesDotProduct() {
        Artifact.Loss l = (Artifact.Loss) Dsl.parse(
                "loss mse_vec(yhat: vec2, y: vec2) = (yhat - y) · (yhat - y)");
        Value r = DslInterpreter.evalLoss(l, Map.of(
                "yhat", new Value.Vec2(2, 3),
                "y",    new Value.Vec2(0, 1)));
        // (2-0, 3-1) · (2, 2) = 4 + 4 = 8
        assertEquals(8f, scalar(r), EPS);
    }

    @Test void typeLevelConstantUsableInBody() {
        Artifact.Neuron n = (Artifact.Neuron) Dsl.parse("""
                neuron seed {
                  in:  z: complex
                  out: y: complex
                  y = z + complex.zero
                }
                """);
        Value y = DslInterpreter.evalNeuron(n, Map.of(
                "z", new Value.Complex(1, 2))).get("y");
        assertEquals(new Value.Complex(1, 2), y);
    }

    @Test void componentAccessOnVec2() {
        Artifact.Neuron n = (Artifact.Neuron) Dsl.parse("""
                neuron pick {
                  in:    p: vec2
                  out:   y: scalar
                  y = p.x + p.y
                }
                """);
        Value y = DslInterpreter.evalNeuron(n, Map.of(
                "p", new Value.Vec2(7, 11))).get("y");
        assertEquals(18f, scalar(y), EPS);
    }

    @Test void scalarBuiltinsExpAndSigmoid() {
        Artifact.Activation v = (Artifact.Activation) Dsl.parse(
                "activation f(x) = sigmoid(x) + exp(0)");
        Value r = DslInterpreter.evalActivation(v, Map.of("x", new Value.Scalar(0)));
        // sigmoid(0) = 0.5, exp(0) = 1 → 1.5
        assertEquals(1.5f, scalar(r), EPS);
    }

    @Test void clampBuiltin() {
        Artifact.Activation v = (Artifact.Activation) Dsl.parse(
                "activation f(x) = clamp(x, 0, 1)");
        assertEquals(0f, scalar(DslInterpreter.evalActivation(v, Map.of("x", new Value.Scalar(-3)))), EPS);
        assertEquals(1f, scalar(DslInterpreter.evalActivation(v, Map.of("x", new Value.Scalar(3)))),  EPS);
        assertEquals(0.5f, scalar(DslInterpreter.evalActivation(v, Map.of("x", new Value.Scalar(0.5f)))), EPS);
    }
}
