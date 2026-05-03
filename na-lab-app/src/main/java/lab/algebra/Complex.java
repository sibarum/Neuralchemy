package lab.algebra;

/**
 * Complex numbers {@code a + bi} with {@code i² = -1}. The "elliptic" 2D real algebra:
 * unit elements form a circle, multiplication is rotation × scaling.
 *
 * <p>{@code (a₁ + b₁i)(a₂ + b₂i) = (a₁a₂ − b₁b₂) + (a₁b₂ + a₂b₁) i}
 */
public final class Complex {

    public float a, b;

    public Complex() {}
    public Complex(float a, float b) { this.a = a; this.b = b; }

    public Complex set(float a, float b) { this.a = a; this.b = b; return this; }
    public Complex set(Complex o)        { return set(o.a, o.b); }
    public Complex zero()                { return set(0, 0); }
    public Complex one()                 { return set(1, 0); }

    public Complex add(Complex o)   { a += o.a; b += o.b; return this; }
    public Complex sub(Complex o)   { a -= o.a; b -= o.b; return this; }
    public Complex scale(float k)   { a *= k;   b *= k;   return this; }
    public Complex conj()           { b = -b; return this; }

    /** {@code this = lhs · rhs}. Aliasing-safe. */
    public Complex mul(Complex lhs, Complex rhs) {
        float na = lhs.a * rhs.a - lhs.b * rhs.b;
        float nb = lhs.a * rhs.b + lhs.b * rhs.a;
        this.a = na; this.b = nb;
        return this;
    }
    public Complex mul(Complex that) { return mul(this, that); }

    /** Euclidean norm-squared {@code a² + b²}. Always ≥ 0; zero only at the origin. */
    public float normSq()    { return a*a + b*b; }
    public float magnitude() { return (float) Math.sqrt(normSq()); }
}
