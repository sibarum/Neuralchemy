package lab.graph.eval;

import java.util.Map;

import lab.graph.Value;

/**
 * One node's backward function. Given its forward inputs, output gradients flowing back from
 * downstream, and (immutable) state, produces gradients for each input port and (optionally)
 * each state field that should be trained.
 *
 * <p>The contract is "additive accumulation, not replacement": gradients returned here are
 * summed by the {@link Trainer} into the network-wide gradient maps. So a node that depends
 * on input {@code a} twice (e.g. {@code a*a}) returns the total {@code 2a} gradient for
 * {@code a}, not two separate calls.
 *
 * <p>Pure: implementations don't read or mutate the network. They take what they need as
 * arguments. State is passed in — never written to here. The trainer applies the SGD step.
 */
@FunctionalInterface
public interface Backward {

    /** {@code in} = forward inputs by port; {@code outGrads} = ∂L/∂output by port. */
    Result apply(Map<String, Value> in, Map<String, Value> outGrads, Map<String, Value> state);

    /**
     * Per-port input and state gradients. {@code state} grads are populated only by trainable
     * built-ins (e.g. {@link lab.graph.BuiltinKinds#LEARNABLE_S}) — every other backward leaves
     * them empty.
     */
    record Result(Map<String, Value> inputGrads, Map<String, Value> stateGrads) {

        public Result {
            if (inputGrads == null) inputGrads = Map.of();
            if (stateGrads == null) stateGrads = Map.of();
        }

        public static Result inputs(Map<String, Value> inputs) {
            return new Result(inputs, Map.of());
        }

        public static Result state(Map<String, Value> state) {
            return new Result(Map.of(), state);
        }
    }
}
