package lab.dsl.ast;

import lab.dsl.SourceSpan;
import lab.dsl.Type;

/**
 * A name appearing in a neuron's {@code in:} / {@code param:} / {@code out:} section, or in an
 * activation / loss argument list. {@link #type} defaults to {@link Type#SCALAR} when the
 * source omits an annotation.
 */
public record TypedName(String name, Type type, SourceSpan span) {}
