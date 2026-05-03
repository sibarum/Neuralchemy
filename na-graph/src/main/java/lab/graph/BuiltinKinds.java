package lab.graph;

import java.util.List;
import java.util.Map;

import lab.dsl.Type;

/**
 * Starter set of built-in {@link NodeKind}s. Registered into {@link KindRegistry#builtin()}.
 * Each kind covers one row from node-graph.md's catalog tables.
 *
 * <p>Algebra-polymorphic kinds (the doc's {@code builtin.add}: {@code (T, T) → T}) are
 * type-specialized for now — {@code builtin.add.scalar}, {@code builtin.add.complex}, etc.
 * Wildcard-typed ports are a future feature; type-specific kinds are unambiguous and let
 * the validator stay simple. When the catalog has 15+ specializations of the same op,
 * revisit.
 *
 * <p>String constants for kind ids live alongside their builders so search-for-id-string in
 * the codebase points at the canonical definition.
 */
public final class BuiltinKinds {

    public static final String SLIDER         = "builtin.slider";
    public static final String CONSTANT_S     = "builtin.constant.scalar";
    public static final String INPUT_S        = "builtin.input.scalar";
    public static final String OUTPUT_S       = "builtin.output.scalar";
    public static final String ADD_S          = "builtin.add.scalar";
    public static final String SUB_S          = "builtin.sub.scalar";
    public static final String MUL_S          = "builtin.mul.scalar";
    public static final String SPLIT          = "builtin.split";
    public static final String JOIN           = "builtin.join";
    public static final String LEARNABLE_S    = "builtin.learnable.scalar";
    public static final String LOSS_MSE_S     = "builtin.loss.mse.scalar";
    public static final String PROBE_FIELD    = "builtin.probe.field";
    public static final String PROBE_METRIC_S = "builtin.probe.metric.scalar";

    private BuiltinKinds() {}

    static void registerInto(KindRegistry r) {
        r.register(slider());
        r.register(constantScalar());
        r.register(inputScalar());
        r.register(outputScalar());
        r.register(addScalar());
        r.register(subScalar());
        r.register(mulScalar());
        r.register(split());
        r.register(join());
        r.register(learnableScalar());
        r.register(lossMseScalar());
        r.register(probeField());
        r.register(probeMetricScalar());
    }

    // ─── Value sources (zero inputs, one or more outputs) ───────────────────

    private static NodeKind slider() {
        return new NodeKind(SLIDER, "Slider",
                List.of(),
                List.of(PortSpec.required("value", Type.SCALAR, 0)),
                Map.of("min",   new Value.Scalar(-1f),
                       "max",   new Value.Scalar( 1f),
                       "value", new Value.Scalar( 0f)));
    }

    private static NodeKind constantScalar() {
        return new NodeKind(CONSTANT_S, "Constant (scalar)",
                List.of(),
                List.of(PortSpec.required("value", Type.SCALAR, 0)),
                Map.of("value", new Value.Scalar(0f)));
    }

    private static NodeKind inputScalar() {
        return new NodeKind(INPUT_S, "Input (scalar)",
                List.of(),
                List.of(PortSpec.required("value", Type.SCALAR, 0)),
                Map.of("name", new Value.Str("x")));
    }

    // ─── Sinks (one input, zero outputs) ────────────────────────────────────

    private static NodeKind outputScalar() {
        return new NodeKind(OUTPUT_S, "Output (scalar)",
                List.of(PortSpec.required("value", Type.SCALAR, 0)),
                List.of(),
                Map.of("name", new Value.Str("y")));
    }

    private static NodeKind probeField() {
        // Field probes accept any algebra type — for now we ship the scalar variant; the
        // typed variants can mirror this once we add probe.field.complex etc.
        return new NodeKind(PROBE_FIELD, "Probe (field)",
                List.of(PortSpec.required("value", Type.SCALAR, 0)),
                List.of(),
                Map.of("colormap", new Value.Str("magenta-cyan")));
    }

    private static NodeKind probeMetricScalar() {
        return new NodeKind(PROBE_METRIC_S, "Probe (metric)",
                List.of(PortSpec.required("value", Type.SCALAR, 0)),
                List.of(),
                Map.of("name", new Value.Str("metric"),
                       "agg",  new Value.Str("mean")));
    }

    // ─── Combinators ────────────────────────────────────────────────────────

    private static NodeKind addScalar() {
        return new NodeKind(ADD_S, "Add (scalar)",
                List.of(PortSpec.required("a", Type.SCALAR, 0),
                        PortSpec.required("b", Type.SCALAR, 1)),
                List.of(PortSpec.required("sum", Type.SCALAR, 0)),
                Map.of());
    }

    private static NodeKind subScalar() {
        return new NodeKind(SUB_S, "Subtract (scalar)",
                List.of(PortSpec.required("a", Type.SCALAR, 0),
                        PortSpec.required("b", Type.SCALAR, 1)),
                List.of(PortSpec.required("diff", Type.SCALAR, 0)),
                Map.of());
    }

    private static NodeKind mulScalar() {
        return new NodeKind(MUL_S, "Multiply (scalar)",
                List.of(PortSpec.required("a", Type.SCALAR, 0),
                        PortSpec.required("b", Type.SCALAR, 1)),
                List.of(PortSpec.required("product", Type.SCALAR, 0)),
                Map.of());
    }

    private static NodeKind split() {
        return new NodeKind(SPLIT, "Split (vec2 → 2× scalar)",
                List.of(PortSpec.required("pair", Type.VEC2, 0)),
                List.of(PortSpec.required("a", Type.SCALAR, 0),
                        PortSpec.required("b", Type.SCALAR, 1)),
                Map.of());
    }

    private static NodeKind join() {
        return new NodeKind(JOIN, "Join (2× scalar → vec2)",
                List.of(PortSpec.required("a", Type.SCALAR, 0),
                        PortSpec.required("b", Type.SCALAR, 1)),
                List.of(PortSpec.required("pair", Type.VEC2, 0)),
                Map.of());
    }

    // ─── Trainable parameter ─────────────────────────────────────────────────

    private static NodeKind learnableScalar() {
        return new NodeKind(LEARNABLE_S, "Learnable (scalar)",
                List.of(),
                List.of(PortSpec.required("value", Type.SCALAR, 0)),
                Map.of("value", new Value.Scalar(0f),
                       "init",  new Value.Scalar(0f)));
    }

    // ─── Loss ────────────────────────────────────────────────────────────────

    private static NodeKind lossMseScalar() {
        return new NodeKind(LOSS_MSE_S, "MSE Loss (scalar)",
                List.of(PortSpec.required("pred",   Type.SCALAR, 0),
                        PortSpec.required("target", Type.SCALAR, 1)),
                List.of(PortSpec.required("loss", Type.SCALAR, 0)),
                Map.of());
    }
}
