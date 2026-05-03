package lab.dsl;

/**
 * Built-in algebra types. Names match {@code dsl.md} exactly — the lexer / parser keys off
 * {@link #name()} (lowercased) when resolving type annotations like {@code z: complex}.
 *
 * <p>{@link #components} is the count of {@code float}s in the unpacked-scalar IR
 * representation — the runtime never sees an algebra type as a heap-allocated value, only as
 * its component scalars.
 */
public enum Type {
    SCALAR(1),
    VEC2(2),
    COMPLEX(2),
    SPLITCOMPLEX(2),
    QUATERNION(4),
    COQUAT(4),
    MAT2(4);

    public final int components;

    Type(int components) { this.components = components; }

    /** Lowercase name as it appears in source (e.g. {@code splitcomplex}). */
    public String sourceName() { return name().toLowerCase(); }

    /** Look up by lowercase source name; returns {@code null} for an unknown name. */
    public static Type fromSource(String name) {
        for (Type t : values()) if (t.sourceName().equals(name)) return t;
        return null;
    }

    /** True if multiplication on this type uses an algebra (not just elementwise reals). */
    public boolean hasAlgebraMul() {
        return this == COMPLEX || this == SPLITCOMPLEX
                || this == QUATERNION || this == COQUAT
                || this == MAT2;
    }
}
