package lab.algebra;

/**
 * 2-component real vector with elementwise multiplication — the "linear baseline" against
 * which the algebraic neurons (Complex, SplitComplex, …) are compared.
 *
 * <p>Multiplication here is just {@code (a₁·a₂, b₁·b₂)}: there's no cross-term, no rotation,
 * no hyperbolic motion. It's the no-algebra control case for the lab's parameter-mixing demo.
 */
public final class Vec2 {

    public float a, b;

    public Vec2() {}
    public Vec2(float a, float b) { this.a = a; this.b = b; }

    public Vec2 set(float a, float b) { this.a = a; this.b = b; return this; }
    public Vec2 set(Vec2 o)           { return set(o.a, o.b); }
    public Vec2 zero()                { return set(0, 0); }

    public Vec2 add(Vec2 o)         { a += o.a; b += o.b; return this; }
    public Vec2 sub(Vec2 o)         { a -= o.a; b -= o.b; return this; }
    public Vec2 scale(float k)      { a *= k;   b *= k;   return this; }
    public Vec2 negate()            { a = -a;   b = -b;   return this; }

    /** {@code this = lhs ⊙ rhs} elementwise. Aliasing-safe: lhs or rhs may equal this. */
    public Vec2 mul(Vec2 lhs, Vec2 rhs) {
        float na = lhs.a * rhs.a;
        float nb = lhs.b * rhs.b;
        this.a = na; this.b = nb;
        return this;
    }
    public Vec2 mul(Vec2 that) { return mul(this, that); }

    public float magnitude() { return (float) Math.sqrt(a*a + b*b); }
}
