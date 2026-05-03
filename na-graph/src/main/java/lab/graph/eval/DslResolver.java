package lab.graph.eval;

import lab.dsl.ast.Artifact;

/**
 * Resolves a DSL kind id ({@code dsl.neuron.split_mix}, {@code dsl.activation.tanh_squared},
 * {@code dsl.loss.mse}) to its parsed {@link Artifact}. Implemented at the application layer
 * by the workspace; the evaluator only needs the lookup contract so {@code na-graph} stays
 * free of UI-layer types.
 */
@FunctionalInterface
public interface DslResolver {

    /** Returns {@code null} when the workspace doesn't carry an artifact for {@code kindId}. */
    Artifact resolve(String kindId);

    DslResolver NONE = id -> null;
}
