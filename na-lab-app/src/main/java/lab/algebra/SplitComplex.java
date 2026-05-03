package lab.algebra;

/**
 * Split-complex numbers {@code a + bj} with {@code j² = +1} (a.k.a. perplex / hyperbolic /
 * double numbers). The "hyperbolic" 2D real algebra: unit elements form a hyperbola, and
 * multiplication is hyperbolic rotation × scaling. Squarable to zero on the diagonal lines
 * {@code a = ±b}, so it has nontrivial zero-divisors — unlike {@link Complex}.
 *
 * <p>{@code (a₁ + b₁j)(a₂ + b₂j) = (a₁a₂ + b₁b₂) + (a₁b₂ + a₂b₁) j}
 *
 * <p>Sign change vs complex: the only difference from {@link Complex#mul} is a single plus
 * where the complex formula has a minus. That single bit flip is the entire split between
 * elliptic and hyperbolic 2D algebras.
 */
public final class SplitComplex {

    public float a, b;

    public SplitComplex() {}
    public SplitComplex(float a, float b) { this.a = a; this.b = b; }

    public SplitComplex set(float a, float b) { this.a = a; this.b = b; return this; }
    public SplitComplex set(SplitComplex o)   { return set(o.a, o.b); }
    public SplitComplex zero()                { return set(0, 0); }
    public SplitComplex one()                 { return set(1, 0); }

    public SplitComplex add(SplitComplex o) { a += o.a; b += o.b; return this; }
    public SplitComplex sub(SplitComplex o) { a -= o.a; b -= o.b; return this; }
    public SplitComplex scale(float k)      { a *= k;   b *= k;   return this; }
    public SplitComplex conj()              { b = -b; return this; }

    /** {@code this = lhs · rhs}. Aliasing-safe. */
    public SplitComplex mul(SplitComplex lhs, SplitComplex rhs) {
        float na = lhs.a * rhs.a + lhs.b * rhs.b;
        float nb = lhs.a * rhs.b + lhs.b * rhs.a;
        this.a = na; this.b = nb;
        return this;
    }
    public SplitComplex mul(SplitComplex that) { return mul(this, that); }

    /** Lorentz norm {@code a² − b²}. Can be positive (timelike), zero (null), or negative
     *  (spacelike). Multiplicatively invariant: |xy| = |x|·|y|. */
    public float lorentzNorm() { return a*a - b*b; }
}
