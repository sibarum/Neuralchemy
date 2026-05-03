package lab.graph.eval;

import java.util.HashMap;
import java.util.Map;

import lab.graph.BuiltinKinds;
import lab.graph.Value;

/**
 * Hand-written {@link Backward} functions for every trainable built-in kind. Looked up by id.
 *
 * <p>Coverage so far:
 * <ul>
 *   <li>{@code add.scalar}, {@code sub.scalar}, {@code mul.scalar} — full backward.</li>
 *   <li>{@code learnable.scalar} — output gradient flows into a state gradient on {@code value}
 *       (the parameter being trained).</li>
 *   <li>{@code loss.mse.scalar} — backward of {@code (pred − target)²}.</li>
 *   <li>Sources ({@code slider}, {@code constant}, {@code input}) and sinks ({@code output},
 *       probes) have no learnable parameters and no upstream gradient flow, so they don't
 *       appear here. Their absence from {@link #backwardOf} is silently treated by the trainer
 *       as "no gradients to propagate or apply."</li>
 * </ul>
 *
 * <p>Adding a new built-in's backward is one entry. DSL kinds are differentiated separately
 * (future work — symbolic AST differentiation in {@code na-dsl}).
 */
public final class BuiltinBackwards {

    private static final Map<String, Backward> BY_ID = buildRegistry();

    private BuiltinBackwards() {}

    /** Returns {@code null} when the kind has no backward (acceptable: the trainer just skips). */
    public static Backward backwardOf(String kindId) {
        return BY_ID.get(kindId);
    }

    private static Map<String, Backward> buildRegistry() {
        Map<String, Backward> m = new HashMap<>();

        // d(a + b)/da = 1, d(a + b)/db = 1
        m.put(BuiltinKinds.ADD_S, (in, outGrads, state) -> {
            float g = scalarOr(outGrads.get("sum"), 0f);
            return Backward.Result.inputs(Map.of(
                    "a", new Value.Scalar(g),
                    "b", new Value.Scalar(g)));
        });

        // d(a - b)/da = 1, d(a - b)/db = -1
        m.put(BuiltinKinds.SUB_S, (in, outGrads, state) -> {
            float g = scalarOr(outGrads.get("diff"), 0f);
            return Backward.Result.inputs(Map.of(
                    "a", new Value.Scalar(g),
                    "b", new Value.Scalar(-g)));
        });

        // d(a * b)/da = b, d(a * b)/db = a
        m.put(BuiltinKinds.MUL_S, (in, outGrads, state) -> {
            float g = scalarOr(outGrads.get("product"), 0f);
            float a = scalarOr(in.get("a"), 0f);
            float b = scalarOr(in.get("b"), 0f);
            return Backward.Result.inputs(Map.of(
                    "a", new Value.Scalar(g * b),
                    "b", new Value.Scalar(g * a)));
        });

        // d(loss)/d(pred) = 2*(pred - target), d(loss)/d(target) = -2*(pred - target)
        m.put(BuiltinKinds.LOSS_MSE_S, (in, outGrads, state) -> {
            float g = scalarOr(outGrads.get("loss"), 0f);
            float pred   = scalarOr(in.get("pred"),   0f);
            float target = scalarOr(in.get("target"), 0f);
            float d = pred - target;
            return Backward.Result.inputs(Map.of(
                    "pred",   new Value.Scalar(g * 2f * d),
                    "target", new Value.Scalar(g * -2f * d)));
        });

        // Output gradient flows directly into state.value (the trained parameter).
        m.put(BuiltinKinds.LEARNABLE_S, (in, outGrads, state) -> {
            float g = scalarOr(outGrads.get("value"), 0f);
            return Backward.Result.state(Map.of("value", new Value.Scalar(g)));
        });

        return Map.copyOf(m);
    }

    private static float scalarOr(Value v, float dflt) {
        return v instanceof Value.Scalar s ? s.v() : dflt;
    }
}
