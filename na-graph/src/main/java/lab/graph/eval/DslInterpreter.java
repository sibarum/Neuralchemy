package lab.graph.eval;

import java.util.HashMap;
import java.util.Map;

import lab.dsl.Type;
import lab.dsl.ast.Artifact;
import lab.dsl.ast.Assign;
import lab.dsl.ast.BinOp;
import lab.dsl.ast.Expr;
import lab.dsl.ast.TypedName;
import lab.dsl.ast.UnaryOp;
import lab.graph.Value;

/**
 * Tree-walking interpreter for parsed DSL artifacts. One method per top-level shape; a
 * single {@link #evalExpr} recursive driver for the expression grammar.
 *
 * <p>Throw-on-failure: this interpreter assumes a checked artifact (zero {@code ERROR}
 * diagnostics from {@link lab.dsl.check.Checker}). It will still throw for unresolved
 * function names — those would have been reported as {@code WARN} and require a workspace
 * resolver, which is a future feature.
 *
 * <p>This is the v1 reference implementation that pairs 1:1 with dsl.md. A Truffle
 * implementation will replace it; the AST shapes are designed to migrate cleanly.
 */
public final class DslInterpreter {

    private DslInterpreter() {}

    /** Run a neuron forward. {@code inputs} must contain every {@code in:} and {@code param:}. */
    public static Map<String, Value> evalNeuron(Artifact.Neuron n, Map<String, Value> inputs) {
        Map<String, Value> scope = new HashMap<>(inputs);
        for (Assign a : n.body()) {
            scope.put(a.name(), evalExpr(a.value(), scope));
        }
        Map<String, Value> outputs = new HashMap<>();
        for (TypedName tn : n.outputs()) {
            Value v = scope.get(tn.name());
            if (v != null) outputs.put(tn.name(), v);
        }
        return outputs;
    }

    /** Evaluate an activation's body. {@code inputs} must contain every arg. */
    public static Value evalActivation(Artifact.Activation v, Map<String, Value> inputs) {
        return evalExpr(v.body(), new HashMap<>(inputs));
    }

    /** Evaluate a loss's body — always returns a scalar. */
    public static Value evalLoss(Artifact.Loss l, Map<String, Value> inputs) {
        return evalExpr(l.body(), new HashMap<>(inputs));
    }

    /**
     * Evaluate an arbitrary expression in a fixed scope. Used by the DSL backward path to run
     * symbolic-derivative {@link Expr} trees produced by {@code Differentiator}; the same
     * recursion handles those just as well as user-written bodies.
     */
    public static Value evalExpression(Expr e, Map<String, Value> scope) {
        return evalExpr(e, scope);
    }

    // ─── Expression eval ───────────────────────────────────────────────────

    private static Value evalExpr(Expr e, Map<String, Value> scope) {
        return switch (e) {
            case Expr.Num n -> new Value.Scalar((float) n.value());
            case Expr.VarRef v -> {
                Value bound = scope.get(v.name());
                if (bound != null) yield bound;
                throw new IllegalStateException("unbound name '" + v.name() + "' at " + v.span());
            }
            case Expr.Unary u -> {
                Value op = evalExpr(u.operand(), scope);
                yield (u.op() == UnaryOp.NEG) ? Algebra.neg(op) : op;
            }
            case Expr.Binary b -> evalBinary(b, scope);
            case Expr.Call c -> evalCall(c, scope);
            case Expr.FieldAccess f -> evalFieldAccess(f, scope);
            case Expr.MatrixIndex m -> evalMatrixIndex(m, scope);
            case Expr.AlgebraLit a -> evalAlgebraLit(a, scope);
        };
    }

    private static Value evalBinary(Expr.Binary b, Map<String, Value> scope) {
        Value l = evalExpr(b.lhs(), scope);
        Value r = evalExpr(b.rhs(), scope);
        return switch (b.op()) {
            case ADD -> Algebra.add(l, r);
            case SUB -> Algebra.sub(l, r);
            case MUL -> Algebra.mul(l, r);
            case DIV -> Algebra.div(l, r);
            case POW -> Algebra.pow(l, r);
            case DOT -> Algebra.dot(l, r);
        };
    }

    private static Value evalCall(Expr.Call c, Map<String, Value> scope) {
        // 1. Type constructor — vec2(x, y), complex(re, im), …
        Type t = Type.fromSource(c.name());
        if (t != null) {
            float[] comps = new float[c.args().size()];
            for (int i = 0; i < c.args().size(); i++) {
                comps[i] = scalarOf(evalExpr(c.args().get(i), scope), c.name());
            }
            return Algebra.construct(t, comps);
        }

        // 2. Algebra ops on a single value
        return switch (c.name()) {
            case "conj" -> Algebra.conj(evalExpr(c.args().get(0), scope));
            case "norm" -> Algebra.norm(evalExpr(c.args().get(0), scope));
            case "re"   -> Algebra.re  (evalExpr(c.args().get(0), scope));
            case "im"   -> Algebra.im  (evalExpr(c.args().get(0), scope));

            // 3. Built-in scalar functions
            case "exp", "log", "sin", "cos", "tan", "tanh", "sigmoid",
                 "relu", "abs", "sqrt", "floor", "ceil"
                 -> scalarFn1(c.name(), scalarOf(evalExpr(c.args().get(0), scope), c.name()));
            case "min" -> new Value.Scalar(Math.min(
                    scalarOf(evalExpr(c.args().get(0), scope), "min"),
                    scalarOf(evalExpr(c.args().get(1), scope), "min")));
            case "max" -> new Value.Scalar(Math.max(
                    scalarOf(evalExpr(c.args().get(0), scope), "max"),
                    scalarOf(evalExpr(c.args().get(1), scope), "max")));
            case "clamp" -> {
                float x  = scalarOf(evalExpr(c.args().get(0), scope), "clamp");
                float lo = scalarOf(evalExpr(c.args().get(1), scope), "clamp");
                float hi = scalarOf(evalExpr(c.args().get(2), scope), "clamp");
                yield new Value.Scalar(Math.max(lo, Math.min(hi, x)));
            }
            case "lerp" -> {
                float a = scalarOf(evalExpr(c.args().get(0), scope), "lerp");
                float bv = scalarOf(evalExpr(c.args().get(1), scope), "lerp");
                float tt = scalarOf(evalExpr(c.args().get(2), scope), "lerp");
                yield new Value.Scalar(a + (bv - a) * tt);
            }

            // 4. Anything else is an unresolved workspace symbol — checker would have warned.
            default -> throw new IllegalStateException(
                    "unresolved function '" + c.name() + "' — workspace symbol resolution is a future feature");
        };
    }

    private static Value evalFieldAccess(Expr.FieldAccess f, Map<String, Value> scope) {
        // Type-level constants: T.zero / T.one / T.identity
        if (f.target() instanceof Expr.VarRef vr) {
            Type asType = Type.fromSource(vr.name());
            if (asType != null) {
                return switch (f.field()) {
                    case "zero"     -> Algebra.zeroOf(asType);
                    case "one"      -> Algebra.oneOf(asType);
                    case "identity" -> Algebra.identityOf(asType);
                    default -> throw new IllegalStateException(
                            "type '" + asType.sourceName() + "' has no constant '" + f.field() + "'");
                };
            }
        }

        Value target = evalExpr(f.target(), scope);
        return switch (target) {
            case Value.Vec2 v -> switch (f.field()) {
                case "x" -> new Value.Scalar(v.x());
                case "y" -> new Value.Scalar(v.y());
                default -> throw bad(f.field(), "vec2");
            };
            case Value.Complex c -> switch (f.field()) {
                case "x", "re" -> new Value.Scalar(c.re());
                case "y", "im" -> new Value.Scalar(c.im());
                default -> throw bad(f.field(), "complex");
            };
            case Value.SplitComplex c -> switch (f.field()) {
                case "x", "re" -> new Value.Scalar(c.re());
                case "y", "im" -> new Value.Scalar(c.im());
                default -> throw bad(f.field(), "splitcomplex");
            };
            case Value.Quaternion q -> switch (f.field()) {
                case "w" -> new Value.Scalar(q.w());
                case "x", "i" -> new Value.Scalar(q.x());
                case "y", "j" -> new Value.Scalar(q.y());
                case "z", "k" -> new Value.Scalar(q.z());
                default -> throw bad(f.field(), "quaternion");
            };
            case Value.Coquat q -> switch (f.field()) {
                case "w" -> new Value.Scalar(q.a());
                case "i" -> new Value.Scalar(q.b());
                case "j" -> new Value.Scalar(q.c());
                case "k" -> new Value.Scalar(q.d());
                default -> throw bad(f.field(), "coquat");
            };
            default -> throw new IllegalStateException(
                    "field access on " + (target.type() == null ? "non-algebra" : target.type().sourceName()));
        };
    }

    private static Value evalMatrixIndex(Expr.MatrixIndex m, Map<String, Value> scope) {
        Value target = evalExpr(m.target(), scope);
        if (!(target instanceof Value.Mat2 mat)) {
            throw new IllegalStateException(".[r,c] requires mat2; got " + target.type());
        }
        return switch (m.row() * 2 + m.col()) {
            case 0 -> new Value.Scalar(mat.a00());
            case 1 -> new Value.Scalar(mat.a01());
            case 2 -> new Value.Scalar(mat.a10());
            case 3 -> new Value.Scalar(mat.a11());
            default -> throw new IllegalStateException(
                    "mat2 index out of range: [" + m.row() + ", " + m.col() + "]");
        };
    }

    private static Value evalAlgebraLit(Expr.AlgebraLit a, Map<String, Value> scope) {
        // Parser emits these as Call nodes, but the AST permits direct literals — handle them.
        float[] comps = new float[a.components().size()];
        for (int i = 0; i < comps.length; i++) {
            comps[i] = scalarOf(evalExpr(a.components().get(i), scope), a.type().sourceName());
        }
        return Algebra.construct(a.type(), comps);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private static Value scalarFn1(String name, float x) {
        return new Value.Scalar((float) switch (name) {
            case "exp"   -> Math.exp(x);
            case "log"   -> Math.log(x);
            case "sin"   -> Math.sin(x);
            case "cos"   -> Math.cos(x);
            case "tan"   -> Math.tan(x);
            case "tanh"  -> Math.tanh(x);
            case "sigmoid" -> 1.0 / (1.0 + Math.exp(-x));
            case "relu"  -> Math.max(0, x);
            case "abs"   -> Math.abs(x);
            case "sqrt"  -> Math.sqrt(x);
            case "floor" -> Math.floor(x);
            case "ceil"  -> Math.ceil(x);
            default -> throw new IllegalStateException("unknown scalar fn " + name);
        });
    }

    private static float scalarOf(Value v, String ctx) {
        if (v instanceof Value.Scalar s) return s.v();
        throw new IllegalStateException("'" + ctx + "' expects scalar; got "
                + (v.type() == null ? "non-algebra" : v.type().sourceName()));
    }

    private static IllegalStateException bad(String field, String type) {
        return new IllegalStateException(type + " has no field '" + field + "'");
    }
}
