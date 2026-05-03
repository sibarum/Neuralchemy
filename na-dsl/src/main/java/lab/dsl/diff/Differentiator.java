package lab.dsl.diff;

import java.util.ArrayList;
import java.util.List;

import lab.dsl.SourceSpan;
import lab.dsl.ast.BinOp;
import lab.dsl.ast.Expr;
import lab.dsl.ast.UnaryOp;

/**
 * Symbolic differentiator over the scalar subset of the DSL. Returns a new {@link Expr} AST
 * representing {@code ∂e/∂varName} which can be evaluated by the same {@code DslInterpreter}
 * the forward pass uses — no new evaluator needed.
 *
 * <h3>Scope (v0)</h3>
 * <p>Scalar values only. Input expressions and the diff variable are assumed to type-check as
 * {@code scalar}. Constructs that don't yield scalars in normal use are passed through as
 * non-derivative shapes:
 * <ul>
 *   <li>{@link Expr.AlgebraLit}, {@link Expr.MatrixIndex} — return literal {@code 0}.</li>
 *   <li>{@link Expr.FieldAccess} — return literal {@code 0} (vector / algebra component access
 *       isn't part of the scalar subgraph we differentiate).</li>
 *   <li>{@link Expr.Binary} with operator {@link BinOp#DOT} — return literal {@code 0}.</li>
 *   <li>{@link Expr.Binary} with operator {@link BinOp#POW} where the exponent is not a numeric
 *       literal — return literal {@code 0} (general {@code u^v} differentiation needs
 *       {@code log}/{@code exp} which the DSL has but we lock it down to constant exponents
 *       for v0).</li>
 * </ul>
 * <p>{@link Expr.Call} is supported for the standard scalar builtins (sin/cos/tan/tanh/
 * sigmoid/relu/exp/log/sqrt/abs and the multi-arg min/max/clamp/lerp). Unrecognized calls
 * return literal {@code 0} — workspace-defined activations aren't yet inlined here; that's a
 * future iteration once the workspace passes a resolver in.
 *
 * <h3>Output shape</h3>
 * <p>No simplification: {@code 1*x} stays as {@code 1*x}, {@code 0+x} stays as {@code 0+x}.
 * The interpreter handles those at evaluation time, and a clean simplifier can be a separate
 * pass once we want pretty-printed derivatives.
 */
public final class Differentiator {

    private static final SourceSpan SP = SourceSpan.UNKNOWN;

    private Differentiator() {}

    /** {@code ∂expr/∂varName}. Never returns null. */
    public static Expr diff(Expr expr, String varName) {
        return switch (expr) {
            case Expr.Num n          -> num(0);
            case Expr.VarRef v       -> num(v.name().equals(varName) ? 1 : 0);
            case Expr.Unary u        -> diffUnary(u, varName);
            case Expr.Binary b       -> diffBinary(b, varName);
            case Expr.Call c         -> diffCall(c, varName);
            case Expr.FieldAccess f  -> num(0);
            case Expr.MatrixIndex m  -> num(0);
            case Expr.AlgebraLit a   -> num(0);
        };
    }

    // ─── Per-shape rules ────────────────────────────────────────────────────

    private static Expr diffUnary(Expr.Unary u, String x) {
        if (u.op() == UnaryOp.NEG) {
            return new Expr.Unary(UnaryOp.NEG, diff(u.operand(), x), SP);
        }
        return num(0);
    }

    private static Expr diffBinary(Expr.Binary b, String x) {
        Expr l = b.lhs(), r = b.rhs();
        Expr dl = diff(l, x), dr = diff(r, x);
        return switch (b.op()) {
            case ADD -> bin(BinOp.ADD, dl, dr);
            case SUB -> bin(BinOp.SUB, dl, dr);
            case MUL -> bin(BinOp.ADD,
                            bin(BinOp.MUL, dl, r),
                            bin(BinOp.MUL, l, dr));
            case DIV -> bin(BinOp.DIV,
                            bin(BinOp.SUB,
                                bin(BinOp.MUL, dl, r),
                                bin(BinOp.MUL, l, dr)),
                            bin(BinOp.MUL, r, r));
            case POW -> diffPow(l, r, dl, x);
            case DOT -> num(0);
        };
    }

    /**
     * {@code (l ^ c)' = c * l^(c-1) * dl} when {@code c} is a numeric literal. General u^v is
     * possible via {@code u^v = exp(v * log u)} but we leave that to a later pass.
     */
    private static Expr diffPow(Expr l, Expr r, Expr dl, String x) {
        if (!(r instanceof Expr.Num cn)) return num(0);
        double c = cn.value();
        Expr exponent = num(c - 1);
        Expr powered = new Expr.Binary(BinOp.POW, l, exponent, SP);
        return bin(BinOp.MUL, bin(BinOp.MUL, num(c), powered), dl);
    }

    private static Expr diffCall(Expr.Call c, String x) {
        List<Expr> args = c.args();
        return switch (c.name()) {
            case "sin"     -> chain1(c, x, a -> new Expr.Call("cos", List.of(a), SP));
            case "cos"     -> chain1(c, x, a -> new Expr.Unary(UnaryOp.NEG,
                                                new Expr.Call("sin", List.of(a), SP), SP));
            case "tan"     -> chain1(c, x, a ->
                    bin(BinOp.DIV, num(1),
                        bin(BinOp.POW, new Expr.Call("cos", List.of(a), SP), num(2))));
            case "tanh"    -> chain1(c, x, a ->
                    bin(BinOp.SUB, num(1),
                        bin(BinOp.POW, new Expr.Call("tanh", List.of(a), SP), num(2))));
            case "sigmoid" -> chain1(c, x, a -> {
                Expr s = new Expr.Call("sigmoid", List.of(a), SP);
                return bin(BinOp.MUL, s, bin(BinOp.SUB, num(1), s));
            });
            // ReLU's derivative is the indicator {x>0}. Smooth approximation: sigmoid(k·x)
            // with k large. Stays differentiable through the chain rule and is correct in the
            // limit k→∞ for any x ≠ 0.
            case "relu"    -> chain1(c, x, a -> new Expr.Call("sigmoid",
                                    List.of(bin(BinOp.MUL, num(50f), a)), SP));
            case "exp"     -> chain1(c, x, a -> new Expr.Call("exp", List.of(a), SP));
            case "log"     -> chain1(c, x, a -> bin(BinOp.DIV, num(1), a));
            case "sqrt"    -> chain1(c, x, a ->
                    bin(BinOp.DIV, num(0.5),
                        new Expr.Call("sqrt", List.of(a), SP)));
            case "abs"     -> chain1(c, x, a ->
                    bin(BinOp.DIV, a, new Expr.Call("abs", List.of(a), SP)));

            // {@code lerp(a, b, t) = a + (b - a)·t} expands cleanly and is fully diff-able.
            case "lerp"       -> diffLerp(c, x);

            // {@code min}/{@code max}/{@code clamp} are piecewise — without conditionals in
            // the v0 DSL we can't represent their derivatives. Return zero for now;
            // training-relevant subgraphs should avoid them or use {@code relu}.
            case "min", "max", "clamp" -> num(0);

            // Type constructors and unknown calls — return zero.
            default -> num(0);
        };
    }

    /** {@code (f(g(x)))' = f'(g(x)) * g'(x)} for single-arg call {@code c} = {@code f(g)}. */
    private static Expr chain1(Expr.Call c, String x, java.util.function.Function<Expr, Expr> fp) {
        Expr g  = c.args().get(0);
        Expr fg = fp.apply(g);
        return bin(BinOp.MUL, fg, diff(g, x));
    }

    /** {@code lerp(a, b, t)} = {@code a + (b - a) * t}; differentiate via that expansion. */
    private static Expr diffLerp(Expr.Call c, String x) {
        Expr a = c.args().get(0), b = c.args().get(1), t = c.args().get(2);
        Expr expanded = bin(BinOp.ADD, a,
                bin(BinOp.MUL, bin(BinOp.SUB, b, a), t));
        return diff(expanded, x);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static Expr num(double v)              { return new Expr.Num(v, SP); }
    private static Expr bin(BinOp op, Expr a, Expr b) { return new Expr.Binary(op, a, b, SP); }

    /** Convenience for callers walking many variables at once: returns a list of partials. */
    public static List<Expr> diffAll(Expr expr, List<String> varNames) {
        List<Expr> out = new ArrayList<>(varNames.size());
        for (String v : varNames) out.add(diff(expr, v));
        return out;
    }
}
