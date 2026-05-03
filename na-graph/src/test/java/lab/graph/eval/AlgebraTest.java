package lab.graph.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import lab.dsl.Type;
import lab.graph.Value;

class AlgebraTest {

    private static final float EPS = 1e-5f;

    private static void assertScalar(float expected, Value v) {
        assertEquals(expected, ((Value.Scalar) v).v(), EPS);
    }

    @Test void scalarAddSubMulDivPow() {
        Value a = new Value.Scalar(3);
        Value b = new Value.Scalar(2);
        assertScalar(5, Algebra.add(a, b));
        assertScalar(1, Algebra.sub(a, b));
        assertScalar(6, Algebra.mul(a, b));
        assertScalar(1.5f, Algebra.div(a, b));
        assertScalar(9, Algebra.pow(a, b));
        assertScalar(-3, Algebra.neg(a));
    }

    @Test void vec2ElementwiseMul() {
        Value a = new Value.Vec2(2, 3);
        Value b = new Value.Vec2(4, 5);
        Value r = Algebra.mul(a, b);
        assertEquals(new Value.Vec2(8, 15), r);
    }

    @Test void vec2DotProduct() {
        Value a = new Value.Vec2(3, 4);
        Value b = new Value.Vec2(1, 2);
        assertScalar(11, Algebra.dot(a, b));
    }

    @Test void complexMultiplicationFollowsAlgebra() {
        // (1 + 2i)(3 + 4i) = (3 - 8) + (4 + 6)i = -5 + 10i
        Value a = new Value.Complex(1, 2);
        Value b = new Value.Complex(3, 4);
        assertEquals(new Value.Complex(-5, 10), Algebra.mul(a, b));
    }

    @Test void splitComplexMultiplication() {
        // (1 + 2j)(3 + 4j) = (3 + 8) + (4 + 6)j = 11 + 10j
        Value a = new Value.SplitComplex(1, 2);
        Value b = new Value.SplitComplex(3, 4);
        assertEquals(new Value.SplitComplex(11, 10), Algebra.mul(a, b));
    }

    @Test void scalarBroadcastsIntoComplex() {
        Value s = new Value.Scalar(2);
        Value c = new Value.Complex(3, 4);
        assertEquals(new Value.Complex(6, 8), Algebra.mul(s, c));
        assertEquals(new Value.Complex(6, 8), Algebra.mul(c, s));
    }

    @Test void mismatchedAlgebrasOnMulRejected() {
        Value c = new Value.Complex(1, 0);
        Value q = new Value.Quaternion(1, 0, 0, 0);
        assertThrows(Algebra.AlgebraException.class, () -> Algebra.mul(c, q));
    }

    @Test void quaternionHamiltonProductIJEqualsK() {
        // i * j = k  →  (0,1,0,0) * (0,0,1,0) = (0,0,0,1)
        Value i = new Value.Quaternion(0, 1, 0, 0);
        Value j = new Value.Quaternion(0, 0, 1, 0);
        assertEquals(new Value.Quaternion(0, 0, 0, 1), Algebra.mul(i, j));
        // j * i = -k
        assertEquals(new Value.Quaternion(0, 0, 0, -1), Algebra.mul(j, i));
    }

    @Test void quaternionISquaredIsMinusOne() {
        Value i = new Value.Quaternion(0, 1, 0, 0);
        assertEquals(new Value.Quaternion(-1, 0, 0, 0), Algebra.mul(i, i));
    }

    @Test void coquatJSquaredIsPlusOne() {
        // For split-quaternion: j² = +1
        Value j = new Value.Coquat(0, 0, 1, 0);
        assertEquals(new Value.Coquat(1, 0, 0, 0), Algebra.mul(j, j));
    }

    @Test void mat2IdentityIsLeftAndRightIdentity() {
        Value id = Algebra.identityOf(Type.MAT2);
        Value m  = new Value.Mat2(2, 3, 4, 5);
        assertEquals(m, Algebra.mul(id, m));
        assertEquals(m, Algebra.mul(m, id));
    }

    @Test void mat2DeterminantViaNorm() {
        // det([[1, 2], [3, 4]]) = 1*4 - 2*3 = -2
        assertScalar(-2, Algebra.norm(new Value.Mat2(1, 2, 3, 4)));
    }

    @Test void complexConjAndComponents() {
        Value c = new Value.Complex(3, -4);
        assertEquals(new Value.Complex(3, 4), Algebra.conj(c));
        assertScalar(3,  Algebra.re(c));
        assertScalar(-4, Algebra.im(c));
        assertScalar(5,  Algebra.norm(c));     // |3 - 4i| = 5
    }

    @Test void typeLevelConstants() {
        assertEquals(new Value.Scalar(0), Algebra.zeroOf(Type.SCALAR));
        assertEquals(new Value.Complex(1, 0), Algebra.oneOf(Type.COMPLEX));
        assertEquals(new Value.Mat2(1, 0, 0, 1), Algebra.identityOf(Type.MAT2));
    }

    @Test void typeMismatchOnAddRejected() {
        Value s = new Value.Scalar(1);
        Value c = new Value.Complex(1, 1);
        assertThrows(Algebra.AlgebraException.class, () -> Algebra.add(s, c));
    }
}
