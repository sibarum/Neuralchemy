package lab.graph.eval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lab.dsl.SourceSpan;
import lab.dsl.ast.Artifact;
import lab.dsl.ast.Assign;
import lab.dsl.ast.Expr;
import lab.dsl.ast.TypedName;
import lab.dsl.diff.Differentiator;
import lab.graph.Value;

/**
 * Symbolic-differentiation-based {@link Backward} for DSL artifacts. Pre-computes the partial
 * derivative of each output with respect to each input/param at construction time, caches the
 * resulting {@link Expr} tree, and evaluates it against the forward inputs at backward time.
 *
 * <h3>Mechanism</h3>
 * <ol>
 *   <li><b>Inline body locals</b>. The DSL allows a neuron body to reference intermediate
 *       single-assignment locals. We walk the body in order, substituting earlier definitions
 *       into later ones so each output ends up as one big expression purely over inputs +
 *       params + literal constants.</li>
 *   <li><b>Differentiate</b>. Call {@link Differentiator#diff} once per (output, var) pair.</li>
 *   <li><b>Evaluate at backward time</b>. Given forward inputs and output gradients, the
 *       backward function computes
 *       {@code ∂L/∂var = Σ_outputs outGrads[output] · ∂output/∂var}
 *       by evaluating the cached partial-derivative expressions against the forward inputs.</li>
 * </ol>
 *
 * <h3>Limitations</h3>
 * Scalar inputs/outputs only — same scope as the {@link Differentiator}. DSL neurons that take
 * vector / algebra inputs return zero gradient through this path.
 */
public final class DslBackwards {

    private static final SourceSpan SP = SourceSpan.UNKNOWN;

    private DslBackwards() {}

    // ─── Construction ───────────────────────────────────────────────────────

    public static Backward forNeuron(Artifact.Neuron neuron) {
        Map<String, Expr> inlinedDefs = inlineBody(neuron.body());

        // Variables we need partials w.r.t. — inputs and params (treated as input ports by
        // node-graph's DslKinds.fromArtifact).
        List<String> diffVars = new ArrayList<>();
        for (TypedName tn : neuron.inputs()) diffVars.add(tn.name());
        for (TypedName tn : neuron.params()) diffVars.add(tn.name());

        // outName → (varName → ∂out/∂var)
        Map<String, Map<String, Expr>> partials = new HashMap<>();
        for (TypedName out : neuron.outputs()) {
            Expr outExpr = inlinedDefs.getOrDefault(out.name(), zero());
            Map<String, Expr> perVar = new HashMap<>();
            for (String v : diffVars) {
                perVar.put(v, Differentiator.diff(outExpr, v));
            }
            partials.put(out.name(), perVar);
        }

        return (forwardInputs, outGrads, state) ->
                Backward.Result.inputs(
                        accumulateInputGrads(partials, diffVars, forwardInputs, outGrads));
    }

    public static Backward forActivation(Artifact.Activation activation) {
        // Activation has one body expression and one output port named "value".
        Expr body = activation.body();
        List<String> diffVars = new ArrayList<>();
        for (TypedName tn : activation.args()) diffVars.add(tn.name());

        Map<String, Expr> perVar = new HashMap<>();
        for (String v : diffVars) {
            perVar.put(v, Differentiator.diff(body, v));
        }
        Map<String, Map<String, Expr>> partials = Map.of("value", perVar);

        return (forwardInputs, outGrads, state) ->
                Backward.Result.inputs(
                        accumulateInputGrads(partials, diffVars, forwardInputs, outGrads));
    }

    public static Backward forLoss(Artifact.Loss loss) {
        Expr body = loss.body();
        List<String> diffVars = new ArrayList<>();
        for (TypedName tn : loss.args()) diffVars.add(tn.name());

        Map<String, Expr> perVar = new HashMap<>();
        for (String v : diffVars) {
            perVar.put(v, Differentiator.diff(body, v));
        }
        Map<String, Map<String, Expr>> partials = Map.of("loss", perVar);

        return (forwardInputs, outGrads, state) ->
                Backward.Result.inputs(
                        accumulateInputGrads(partials, diffVars, forwardInputs, outGrads));
    }

    /** Resolve a kind id + an artifact to a backward, or null if the artifact shape is unsupported. */
    public static Backward forArtifact(Artifact a) {
        return switch (a) {
            case Artifact.Neuron n     -> forNeuron(n);
            case Artifact.Activation v -> forActivation(v);
            case Artifact.Loss l       -> forLoss(l);
        };
    }

    // ─── Body inlining ──────────────────────────────────────────────────────

    /**
     * Walk {@code body} in order, substituting each prior assignment's expression into later
     * ones. The result maps each name (output or local) to its fully-expanded definition over
     * inputs/params/literals only.
     */
    private static Map<String, Expr> inlineBody(List<Assign> body) {
        Map<String, Expr> defs = new HashMap<>();
        for (Assign s : body) {
            Expr expanded = substitute(s.value(), defs);
            defs.put(s.name(), expanded);
        }
        return defs;
    }

    /** Replace every {@link Expr.VarRef} whose name is a key in {@code defs} with the bound expression. */
    private static Expr substitute(Expr e, Map<String, Expr> defs) {
        return switch (e) {
            case Expr.Num n -> n;
            case Expr.VarRef v -> defs.getOrDefault(v.name(), v);
            case Expr.Unary u -> new Expr.Unary(u.op(), substitute(u.operand(), defs), SP);
            case Expr.Binary b -> new Expr.Binary(b.op(),
                    substitute(b.lhs(), defs),
                    substitute(b.rhs(), defs), SP);
            case Expr.Call c -> {
                List<Expr> args = new ArrayList<>(c.args().size());
                for (Expr a : c.args()) args.add(substitute(a, defs));
                yield new Expr.Call(c.name(), args, SP);
            }
            case Expr.FieldAccess f -> new Expr.FieldAccess(substitute(f.target(), defs), f.field(), SP);
            case Expr.MatrixIndex m -> new Expr.MatrixIndex(substitute(m.target(), defs), m.row(), m.col(), SP);
            case Expr.AlgebraLit a -> {
                List<Expr> comps = new ArrayList<>(a.components().size());
                for (Expr c : a.components()) comps.add(substitute(c, defs));
                yield new Expr.AlgebraLit(a.type(), comps, SP);
            }
        };
    }

    // ─── Backward-time evaluation ───────────────────────────────────────────

    /**
     * Evaluate every cached partial against the forward inputs, multiply by the corresponding
     * output gradient, and accumulate per input variable.
     */
    private static Map<String, Value> accumulateInputGrads(
            Map<String, Map<String, Expr>> partials,
            List<String> diffVars,
            Map<String, Value> forwardInputs,
            Map<String, Value> outGrads) {

        Map<String, Float> accum = new HashMap<>();
        for (String v : diffVars) accum.put(v, 0f);

        for (Map.Entry<String, Map<String, Expr>> outEntry : partials.entrySet()) {
            Value g = outGrads.get(outEntry.getKey());
            if (!(g instanceof Value.Scalar gs)) continue;

            for (String v : diffVars) {
                Expr partial = outEntry.getValue().get(v);
                Value dv = DslInterpreter.evalExpression(partial, forwardInputs);
                if (!(dv instanceof Value.Scalar dvs)) continue;
                accum.merge(v, gs.v() * dvs.v(), Float::sum);
            }
        }

        Map<String, Value> result = new HashMap<>();
        for (Map.Entry<String, Float> e : accum.entrySet()) {
            result.put(e.getKey(), new Value.Scalar(e.getValue()));
        }
        return result;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static Expr zero() { return new Expr.Num(0, SP); }
}
