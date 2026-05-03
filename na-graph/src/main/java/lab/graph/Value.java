package lab.graph;

import lab.dsl.Type;

/**
 * Persistable per-node state — the union of value shapes a node's {@code state} map can
 * carry. Sealed so codecs and inspectors get exhaustive switches.
 *
 * <p>This is a value-representation, not a runtime evaluation type. Forward-pass evaluation
 * uses unpacked-scalar mutable buffers per the IR design (see node-graph.md
 * §"Allocation-flat at runtime"); {@code Value} is what gets written to disk and shown in
 * inspectors.
 *
 * <p>The {@link Str}/{@link Int} variants exist for non-algebraic state — dataset IDs,
 * column names, batch counts. The algebra variants pair 1:1 with {@link Type}.
 */
public sealed interface Value {

    /** The closest {@link Type} this value occupies, or {@code null} for non-algebraic ones. */
    Type type();

    record Scalar(float v)                                                   implements Value { @Override public Type type() { return Type.SCALAR; }       }
    record Vec2(float x, float y)                                            implements Value { @Override public Type type() { return Type.VEC2; }         }
    record Complex(float re, float im)                                       implements Value { @Override public Type type() { return Type.COMPLEX; }      }
    record SplitComplex(float re, float im)                                  implements Value { @Override public Type type() { return Type.SPLITCOMPLEX; } }
    record Quaternion(float w, float x, float y, float z)                    implements Value { @Override public Type type() { return Type.QUATERNION; }   }
    record Coquat(float a, float b, float c, float d)                        implements Value { @Override public Type type() { return Type.COQUAT; }       }
    record Mat2(float a00, float a01, float a10, float a11)                  implements Value { @Override public Type type() { return Type.MAT2; }         }

    /** Free-form string — dataset ids, column names, optimizer names. No DSL {@link Type}. */
    record Str(String s)  implements Value { @Override public Type type() { return null; } }

    /** Plain int — slider counts, batch sizes. No DSL {@link Type}. */
    record Int(int n)     implements Value { @Override public Type type() { return null; } }

    /** Convenience zero value for an algebra type. */
    static Value zero(Type t) {
        return switch (t) {
            case SCALAR       -> new Scalar(0f);
            case VEC2         -> new Vec2(0f, 0f);
            case COMPLEX      -> new Complex(0f, 0f);
            case SPLITCOMPLEX -> new SplitComplex(0f, 0f);
            case QUATERNION   -> new Quaternion(0f, 0f, 0f, 0f);
            case COQUAT       -> new Coquat(0f, 0f, 0f, 0f);
            case MAT2         -> new Mat2(0f, 0f, 0f, 0f);
        };
    }
}
