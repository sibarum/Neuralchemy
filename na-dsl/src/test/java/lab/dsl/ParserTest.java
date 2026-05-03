package lab.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lab.dsl.ast.Artifact;
import lab.dsl.ast.Assign;
import lab.dsl.ast.BinOp;
import lab.dsl.ast.Expr;
import lab.dsl.ast.UnaryOp;
import lab.dsl.parse.ParseException;
import lab.dsl.parse.Parser;

class ParserTest {

    @Test void splitMixSampleParses() {
        // The lead example from dsl.md.
        Artifact a = Parser.parse("""
                neuron split_mix {
                  in:    x, y
                  param: a, b
                  out:   u, v

                  u = a*x + b*y
                  v = b*x + a*y
                }
                """);
        Artifact.Neuron n = assertInstanceOf(Artifact.Neuron.class, a);
        assertEquals("split_mix", n.name());
        assertEquals(2, n.inputs().size());
        assertEquals(2, n.params().size());
        assertEquals(2, n.outputs().size());
        assertEquals(2, n.body().size());
        assertEquals("u", n.body().get(0).name());
        assertEquals("v", n.body().get(1).name());
    }

    @Test void typedDeclarationsPickUpAnnotations() {
        Artifact a = Parser.parse("""
                neuron complex_mix {
                  in:    z: complex
                  param: w: complex
                  out:   y: complex
                  y = w * z
                }
                """);
        Artifact.Neuron n = (Artifact.Neuron) a;
        assertEquals(Type.COMPLEX, n.inputs().get(0).type());
        assertEquals(Type.COMPLEX, n.params().get(0).type());
        assertEquals(Type.COMPLEX, n.outputs().get(0).type());
    }

    @Test void activationOneLineExpressionParses() {
        Artifact a = Parser.parse("activation tanh_squared(x) = tanh(x) * tanh(x)");
        Artifact.Activation v = assertInstanceOf(Artifact.Activation.class, a);
        assertEquals("tanh_squared", v.name());
        assertEquals(1, v.args().size());
        Expr.Binary b = assertInstanceOf(Expr.Binary.class, v.body());
        assertEquals(BinOp.MUL, b.op());
    }

    @Test void lossWithVecArgParses() {
        Artifact a = Parser.parse("loss mse_vec(yhat: vec2, y: vec2) = (yhat - y) · (yhat - y)");
        Artifact.Loss l = assertInstanceOf(Artifact.Loss.class, a);
        assertEquals("mse_vec", l.name());
        assertEquals(Type.VEC2, l.args().get(0).type());
        Expr.Binary outer = assertInstanceOf(Expr.Binary.class, l.body());
        assertEquals(BinOp.DOT, outer.op());
    }

    @Test void powerIsRightAssociative() {
        Artifact a = Parser.parse("activation f(x) = x^2^3");
        Expr.Binary outer = (Expr.Binary) ((Artifact.Activation) a).body();
        assertEquals(BinOp.POW, outer.op());
        // outer = x ^ (2 ^ 3)  — RHS is the nested power
        Expr.Binary inner = assertInstanceOf(Expr.Binary.class, outer.rhs());
        assertEquals(BinOp.POW, inner.op());
    }

    @Test void unaryMinusBindsTighterThanAdditive() {
        Artifact a = Parser.parse("activation f(x) = -x + 1");
        Expr.Binary plus = (Expr.Binary) ((Artifact.Activation) a).body();
        assertEquals(BinOp.ADD, plus.op());
        assertInstanceOf(Expr.Unary.class, plus.lhs());
    }

    @Test void unaryMinusOnPowerPicksTheWholePower() {
        // -a^b == -(a^b) — power binds tighter than unary minus
        Artifact a = Parser.parse("activation f(a, b) = -a^b");
        Expr.Unary u = assertInstanceOf(Expr.Unary.class, ((Artifact.Activation) a).body());
        assertEquals(UnaryOp.NEG, u.op());
        assertInstanceOf(Expr.Binary.class, u.operand());
        assertEquals(BinOp.POW, ((Expr.Binary) u.operand()).op());
    }

    @Test void fieldAccessAndMatrixIndex() {
        Artifact a = Parser.parse("""
                neuron resid {
                  in:    x: vec2
                  param: a, b: scalar
                  out:   y: vec2
                  y = vec2(a*x.x + b*x.y, b*x.x + a*x.y) + x
                }
                """);
        Artifact.Neuron n = (Artifact.Neuron) a;
        // Just confirm the body contains a Call("vec2", ...)
        Assign assign = n.body().get(0);
        Expr.Binary plus = (Expr.Binary) assign.value();
        Expr.Call call = (Expr.Call) plus.lhs();
        assertEquals("vec2", call.name());
    }

    @Test void matrixIndexParsesAsMatrixIndexNode() {
        Artifact a = Parser.parse("""
                neuron pick {
                  in:    m: mat2
                  out:   y: scalar
                  y = m.[1, 0]
                }
                """);
        Expr.MatrixIndex mi = (Expr.MatrixIndex) ((Artifact.Neuron) a).body().get(0).value();
        assertEquals(1, mi.row());
        assertEquals(0, mi.col());
    }

    @Test void typeLevelConstantParses() {
        Artifact a = Parser.parse("""
                neuron seed {
                  in:    z: complex
                  out:   y: complex
                  y = complex.zero
                }
                """);
        Expr e = ((Artifact.Neuron) a).body().get(0).value();
        Expr.FieldAccess fa = assertInstanceOf(Expr.FieldAccess.class, e);
        assertEquals("zero", fa.field());
    }

    @Test void emptyFileRejected() {
        ParseException ex = assertThrows(ParseException.class, () -> Parser.parse(""));
        assertTrue(ex.getMessage().contains("expected 'neuron'"));
    }

    @Test void multipleArtifactsRejected() {
        ParseException ex = assertThrows(ParseException.class, () -> Parser.parse("""
                activation f(x) = x
                activation g(x) = x
                """));
        assertTrue(ex.getMessage().contains("end of file"));
    }

    @Test void neuronWithoutInputsRejected() {
        ParseException ex = assertThrows(ParseException.class, () -> Parser.parse("""
                neuron bad {
                  out: y
                  y = 1
                }
                """));
        assertTrue(ex.getMessage().contains("'in:'"));
    }

    @Test void neuronWithoutOutputsRejected() {
        ParseException ex = assertThrows(ParseException.class, () -> Parser.parse("""
                neuron bad {
                  in: x
                }
                """));
        assertTrue(ex.getMessage().contains("'out:'"));
    }

    @Test void unknownTypeAnnotationRejected() {
        ParseException ex = assertThrows(ParseException.class, () -> Parser.parse("""
                activation f(x: weirdthing) = x
                """));
        assertTrue(ex.getMessage().contains("unknown type"));
    }
}
