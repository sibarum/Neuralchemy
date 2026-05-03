package lab.dsl.ast;

import java.util.List;

import lab.dsl.SourceSpan;

/**
 * Top-level {@code .nl} declaration. Each file declares exactly one {@link Artifact}; its
 * {@link #name} matches the filename stem.
 */
public sealed interface Artifact {

    String name();
    SourceSpan span();

    /**
     * {@code neuron <name> { in: …  param: …  out: …  body }}.
     *
     * <p>Per dsl.md, {@code in:} and {@code out:} are required and must each have at least
     * one entry; {@code param:} is optional (a parameter-less neuron is a useful constant
     * function).
     */
    record Neuron(
            String name,
            List<TypedName> inputs,
            List<TypedName> params,
            List<TypedName> outputs,
            List<Assign>    body,
            SourceSpan      span
    ) implements Artifact {}

    /**
     * {@code activation <name>(args...) = <expr>}. Single-expression by definition; multi-arg
     * activations are allowed (the doc shows {@code softmax2(x, y)}).
     */
    record Activation(
            String name,
            List<TypedName> args,
            Expr            body,
            SourceSpan      span
    ) implements Artifact {}

    /**
     * {@code loss <name>(<pred>, <target>) = <expr>}. Arity-2 by convention; the checker
     * rejects anything else.
     */
    record Loss(
            String name,
            List<TypedName> args,
            Expr            body,
            SourceSpan      span
    ) implements Artifact {}
}
