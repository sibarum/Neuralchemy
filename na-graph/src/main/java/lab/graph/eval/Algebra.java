package lab.graph.eval;

import lab.dsl.Type;
import lab.graph.Value;

/**
 * Pointwise algebra ops on {@link Value} records. Same algebra → same type out; scalar
 * broadcasts onto algebras for {@code mul}/{@code div} (per dsl.md's "scalar × T" rule);
 * mismatched non-scalar algebras throw {@link AlgebraException}.
 *
 * <p>Allocation profile: every op currently allocates fresh {@link Value} records. Hot eval
 * loops can later switch to mutable scratch buffers (see project memory: "math primitives
 * must be mutable, joml-style"); for the v1 forward-pass we accept the GC traffic in exchange
 * for code that's easy to reason about and a 1:1 mapping to dsl.md's algebra table.
 */
public final class Algebra {

    private Algebra() {}

    public static final class AlgebraException extends RuntimeException {
        public AlgebraException(String msg) { super(msg); }
    }

    // ─── Unary ─────────────────────────────────────────────────────────────

    public static Value neg(Value a) {
        return switch (a) {
            case Value.Scalar       s -> new Value.Scalar(-s.v());
            case Value.Vec2         v -> new Value.Vec2(-v.x(), -v.y());
            case Value.Complex      c -> new Value.Complex(-c.re(), -c.im());
            case Value.SplitComplex c -> new Value.SplitComplex(-c.re(), -c.im());
            case Value.Quaternion   q -> new Value.Quaternion(-q.w(), -q.x(), -q.y(), -q.z());
            case Value.Coquat       q -> new Value.Coquat(-q.a(), -q.b(), -q.c(), -q.d());
            case Value.Mat2         m -> new Value.Mat2(-m.a00(), -m.a01(), -m.a10(), -m.a11());
            default -> throw mismatch("neg", a);
        };
    }

    public static Value conj(Value a) {
        return switch (a) {
            case Value.Complex      c -> new Value.Complex(c.re(), -c.im());
            case Value.SplitComplex c -> new Value.SplitComplex(c.re(), -c.im());
            case Value.Quaternion   q -> new Value.Quaternion(q.w(), -q.x(), -q.y(), -q.z());
            case Value.Coquat       q -> new Value.Coquat(q.a(), -q.b(), -q.c(), -q.d());
            default -> throw mismatch("conj", a);
        };
    }

    public static Value norm(Value a) {
        return switch (a) {
            case Value.Vec2         v -> new Value.Scalar((float) Math.sqrt(v.x() * v.x() + v.y() * v.y()));
            case Value.Complex      c -> new Value.Scalar((float) Math.sqrt(c.re() * c.re() + c.im() * c.im()));
            case Value.SplitComplex c -> new Value.Scalar(c.re() * c.re() - c.im() * c.im());      // Lorentz
            case Value.Quaternion   q -> new Value.Scalar((float) Math.sqrt(q.w() * q.w() + q.x() * q.x() + q.y() * q.y() + q.z() * q.z()));
            case Value.Coquat       q -> new Value.Scalar(q.a() * q.a() + q.b() * q.b() - q.c() * q.c() - q.d() * q.d());  // split signature
            case Value.Mat2         m -> new Value.Scalar(m.a00() * m.a11() - m.a01() * m.a10()); // det
            default -> throw mismatch("norm", a);
        };
    }

    public static Value re(Value a) {
        return switch (a) {
            case Value.Complex      c -> new Value.Scalar(c.re());
            case Value.SplitComplex c -> new Value.Scalar(c.re());
            default -> throw mismatch("re", a);
        };
    }

    public static Value im(Value a) {
        return switch (a) {
            case Value.Complex      c -> new Value.Scalar(c.im());
            case Value.SplitComplex c -> new Value.Scalar(c.im());
            default -> throw mismatch("im", a);
        };
    }

    // ─── Binary additive ───────────────────────────────────────────────────

    public static Value add(Value a, Value b) {
        if (a.getClass() != b.getClass()) {
            throw new AlgebraException("type mismatch on +: " + nameOf(a) + " vs " + nameOf(b));
        }
        return switch (a) {
            case Value.Scalar       sa -> new Value.Scalar(sa.v() + ((Value.Scalar) b).v());
            case Value.Vec2         va -> { var vb = (Value.Vec2) b; yield new Value.Vec2(va.x()+vb.x(), va.y()+vb.y()); }
            case Value.Complex      ca -> { var cb = (Value.Complex) b; yield new Value.Complex(ca.re()+cb.re(), ca.im()+cb.im()); }
            case Value.SplitComplex ca -> { var cb = (Value.SplitComplex) b; yield new Value.SplitComplex(ca.re()+cb.re(), ca.im()+cb.im()); }
            case Value.Quaternion   qa -> { var qb = (Value.Quaternion) b; yield new Value.Quaternion(qa.w()+qb.w(), qa.x()+qb.x(), qa.y()+qb.y(), qa.z()+qb.z()); }
            case Value.Coquat       qa -> { var qb = (Value.Coquat) b; yield new Value.Coquat(qa.a()+qb.a(), qa.b()+qb.b(), qa.c()+qb.c(), qa.d()+qb.d()); }
            case Value.Mat2         ma -> { var mb = (Value.Mat2) b; yield new Value.Mat2(ma.a00()+mb.a00(), ma.a01()+mb.a01(), ma.a10()+mb.a10(), ma.a11()+mb.a11()); }
            default -> throw mismatch("+", a);
        };
    }

    public static Value sub(Value a, Value b) {
        return add(a, neg(b));
    }

    // ─── Binary multiplicative — algebra dispatched ────────────────────────

    public static Value mul(Value a, Value b) {
        // Scalar broadcast either way
        if (a instanceof Value.Scalar sa) return scaleBy(b, sa.v());
        if (b instanceof Value.Scalar sb) return scaleBy(a, sb.v());

        if (a.getClass() != b.getClass()) {
            throw new AlgebraException("mismatched algebras on *: " + nameOf(a) + " vs " + nameOf(b));
        }
        return switch (a) {
            // Vec2 * Vec2 = elementwise (vec2 has no algebra; the linear baseline per dsl.md)
            case Value.Vec2 va -> { var vb = (Value.Vec2) b; yield new Value.Vec2(va.x()*vb.x(), va.y()*vb.y()); }
            // Complex: (a+bi)(c+di) = (ac - bd) + (ad + bc)i
            case Value.Complex ca -> {
                var cb = (Value.Complex) b;
                yield new Value.Complex(ca.re()*cb.re() - ca.im()*cb.im(),
                                        ca.re()*cb.im() + ca.im()*cb.re());
            }
            // Split-complex: (a+bj)(c+dj) = (ac + bd) + (ad + bc)j
            case Value.SplitComplex ca -> {
                var cb = (Value.SplitComplex) b;
                yield new Value.SplitComplex(ca.re()*cb.re() + ca.im()*cb.im(),
                                              ca.re()*cb.im() + ca.im()*cb.re());
            }
            // Quaternion (Hamilton). w = scalar part, x/y/z = ijk parts.
            case Value.Quaternion qa -> {
                var qb = (Value.Quaternion) b;
                float w = qa.w()*qb.w() - qa.x()*qb.x() - qa.y()*qb.y() - qa.z()*qb.z();
                float x = qa.w()*qb.x() + qa.x()*qb.w() + qa.y()*qb.z() - qa.z()*qb.y();
                float y = qa.w()*qb.y() - qa.x()*qb.z() + qa.y()*qb.w() + qa.z()*qb.x();
                float z = qa.w()*qb.z() + qa.x()*qb.y() - qa.y()*qb.x() + qa.z()*qb.w();
                yield new Value.Quaternion(w, x, y, z);
            }
            // Co-quaternion (split-quat): i² = -1, j² = +1, k² = +1, ij = k.
            // Components ordered (a, b, c, d) = (w, i, j, k).
            case Value.Coquat qa -> {
                var qb = (Value.Coquat) b;
                float w = qa.a()*qb.a() - qa.b()*qb.b() + qa.c()*qb.c() + qa.d()*qb.d();
                float i = qa.a()*qb.b() + qa.b()*qb.a() - qa.c()*qb.d() + qa.d()*qb.c();
                float j = qa.a()*qb.c() - qa.b()*qb.d() + qa.c()*qb.a() + qa.d()*qb.b();
                float k = qa.a()*qb.d() + qa.b()*qb.c() - qa.c()*qb.b() + qa.d()*qb.a();
                yield new Value.Coquat(w, i, j, k);
            }
            // 2×2 real matrix product, row-major.
            case Value.Mat2 ma -> {
                var mb = (Value.Mat2) b;
                float a00 = ma.a00()*mb.a00() + ma.a01()*mb.a10();
                float a01 = ma.a00()*mb.a01() + ma.a01()*mb.a11();
                float a10 = ma.a10()*mb.a00() + ma.a11()*mb.a10();
                float a11 = ma.a10()*mb.a01() + ma.a11()*mb.a11();
                yield new Value.Mat2(a00, a01, a10, a11);
            }
            default -> throw mismatch("*", a);
        };
    }

    /** Scalar division — others throw NotImplemented for now (not used by current samples). */
    public static Value div(Value a, Value b) {
        if (a instanceof Value.Scalar sa && b instanceof Value.Scalar sb) {
            return new Value.Scalar(sa.v() / sb.v());
        }
        if (b instanceof Value.Scalar sb) {
            return scaleBy(a, 1f / sb.v());
        }
        throw new AlgebraException("/ not implemented for " + nameOf(a) + " ÷ " + nameOf(b));
    }

    public static Value pow(Value a, Value b) {
        if (a instanceof Value.Scalar sa && b instanceof Value.Scalar sb) {
            return new Value.Scalar((float) Math.pow(sa.v(), sb.v()));
        }
        throw new AlgebraException("^ requires both operands scalar; got "
                + nameOf(a) + " ^ " + nameOf(b));
    }

    /** Dot product — defined on the two-component algebras and quaternions, returns scalar. */
    public static Value dot(Value a, Value b) {
        if (a.getClass() != b.getClass()) {
            throw new AlgebraException("· requires same type: " + nameOf(a) + " vs " + nameOf(b));
        }
        return switch (a) {
            case Value.Vec2 va         -> { var vb = (Value.Vec2) b;         yield new Value.Scalar(va.x()*vb.x() + va.y()*vb.y()); }
            case Value.Complex ca      -> { var cb = (Value.Complex) b;      yield new Value.Scalar(ca.re()*cb.re() + ca.im()*cb.im()); }
            case Value.SplitComplex ca -> { var cb = (Value.SplitComplex) b; yield new Value.Scalar(ca.re()*cb.re() - ca.im()*cb.im()); }
            case Value.Quaternion qa   -> { var qb = (Value.Quaternion) b;
                yield new Value.Scalar(qa.w()*qb.w() + qa.x()*qb.x() + qa.y()*qb.y() + qa.z()*qb.z()); }
            case Value.Coquat qa       -> { var qb = (Value.Coquat) b;
                yield new Value.Scalar(qa.a()*qb.a() + qa.b()*qb.b() - qa.c()*qb.c() - qa.d()*qb.d()); }
            default -> throw new AlgebraException("· not defined for " + nameOf(a));
        };
    }

    // ─── Type-level constants and constructors ─────────────────────────────

    /** {@code T.zero} for any algebra type. */
    public static Value zeroOf(Type t) { return Value.zero(t); }

    /** {@code T.one}: scalar 1, algebra-real-part-1. */
    public static Value oneOf(Type t) {
        return switch (t) {
            case SCALAR       -> new Value.Scalar(1f);
            case VEC2         -> new Value.Vec2(1f, 1f);
            case COMPLEX      -> new Value.Complex(1f, 0f);
            case SPLITCOMPLEX -> new Value.SplitComplex(1f, 0f);
            case QUATERNION   -> new Value.Quaternion(1f, 0f, 0f, 0f);
            case COQUAT       -> new Value.Coquat(1f, 0f, 0f, 0f);
            case MAT2         -> new Value.Mat2(1f, 1f, 1f, 1f);
        };
    }

    /** Multiplicative identity. Same as {@link #oneOf} except for {@code mat2} (identity matrix). */
    public static Value identityOf(Type t) {
        if (t == Type.MAT2) return new Value.Mat2(1f, 0f, 0f, 1f);
        return oneOf(t);
    }

    /** Build a value of {@code t} from N scalar components (constructor {@code complex(re, im)} etc.). */
    public static Value construct(Type t, float[] comps) {
        if (comps.length != t.components) {
            throw new AlgebraException(t.sourceName() + "(...) expects "
                    + t.components + " components; got " + comps.length);
        }
        return switch (t) {
            case SCALAR       -> new Value.Scalar(comps[0]);
            case VEC2         -> new Value.Vec2(comps[0], comps[1]);
            case COMPLEX      -> new Value.Complex(comps[0], comps[1]);
            case SPLITCOMPLEX -> new Value.SplitComplex(comps[0], comps[1]);
            case QUATERNION   -> new Value.Quaternion(comps[0], comps[1], comps[2], comps[3]);
            case COQUAT       -> new Value.Coquat(comps[0], comps[1], comps[2], comps[3]);
            case MAT2         -> new Value.Mat2(comps[0], comps[1], comps[2], comps[3]);
        };
    }

    // ─── Internals ─────────────────────────────────────────────────────────

    /** Multiply every component of {@code v} by scalar {@code s}. */
    private static Value scaleBy(Value v, float s) {
        return switch (v) {
            case Value.Scalar       a -> new Value.Scalar(a.v() * s);
            case Value.Vec2         a -> new Value.Vec2(a.x() * s, a.y() * s);
            case Value.Complex      a -> new Value.Complex(a.re() * s, a.im() * s);
            case Value.SplitComplex a -> new Value.SplitComplex(a.re() * s, a.im() * s);
            case Value.Quaternion   a -> new Value.Quaternion(a.w() * s, a.x() * s, a.y() * s, a.z() * s);
            case Value.Coquat       a -> new Value.Coquat(a.a() * s, a.b() * s, a.c() * s, a.d() * s);
            case Value.Mat2         a -> new Value.Mat2(a.a00() * s, a.a01() * s, a.a10() * s, a.a11() * s);
            default -> throw mismatch("scale", v);
        };
    }

    private static AlgebraException mismatch(String op, Value v) {
        return new AlgebraException(op + " is not defined on " + nameOf(v));
    }

    private static String nameOf(Value v) {
        Type t = v.type();
        return t == null ? v.getClass().getSimpleName().toLowerCase() : t.sourceName();
    }
}
