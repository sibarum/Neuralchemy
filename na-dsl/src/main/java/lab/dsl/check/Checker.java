package lab.dsl.check;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lab.dsl.SourceSpan;
import lab.dsl.Type;
import lab.dsl.ast.Artifact;
import lab.dsl.ast.Assign;
import lab.dsl.ast.BinOp;
import lab.dsl.ast.Expr;
import lab.dsl.ast.TypedName;

/**
 * Compile-time validation per dsl.md §"Compile-time rules". Accumulates
 * {@link Diagnostic}s rather than failing fast — gives the editor a complete punch list per
 * keystroke rather than the first error and a wall of nothing else.
 *
 * <p>Scope: single-file. Calls to other workspace artifacts (user-defined activations) are
 * recognised as unresolved and reported as {@link Diagnostic.Severity#WARN}; the workspace
 * does the cross-file resolution later.
 *
 * <h2>What's checked</h2>
 * <ul>
 *   <li>Single-assignment of every body local.</li>
 *   <li>{@code in:} and {@code param:} names cannot be reassigned.</li>
 *   <li>Every {@code out:} name is assigned somewhere in the body.</li>
 *   <li>No duplicate names across {@code in:} / {@code param:} / {@code out:}.</li>
 *   <li>Loss arity == 2.</li>
 *   <li>Type rules on every expression: operator type compatibility, algebra dispatch on
 *       {@code *}/{@code /}, dot product operands, field/component access, matrix index
 *       within bounds, type-constructor arity.</li>
 * </ul>
 */
public final class Checker {

    private static final Set<String> SCALAR_FNS_1 = Set.of(
            "exp", "log", "sin", "cos", "tan", "tanh", "sigmoid",
            "relu", "abs", "sqrt", "floor", "ceil"
    );
    private static final Set<String> SCALAR_FNS_2 = Set.of("min", "max");
    private static final Set<String> SCALAR_FNS_3 = Set.of("clamp", "lerp");
    private static final Set<String> ALGEBRA_OPS  = Set.of("conj", "norm", "re", "im");

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private Checker() {}

    public static CheckResult check(Artifact a) {
        Checker c = new Checker();
        switch (a) {
            case Artifact.Neuron     n -> c.checkNeuron(n);
            case Artifact.Activation v -> c.checkActivation(v);
            case Artifact.Loss       l -> c.checkLoss(l);
        }
        return new CheckResult(a, List.copyOf(c.diagnostics));
    }

    // ─── Neuron ─────────────────────────────────────────────────────────────

    private void checkNeuron(Artifact.Neuron n) {
        Map<String, Type> scope = new LinkedHashMap<>();
        addAll(scope, n.inputs(), "in");
        addAll(scope, n.params(), "param");
        // Outputs are added to scope but their types become assignable rather than read-only.
        Set<String> readOnly = new HashSet<>(scope.keySet());
        for (TypedName tn : n.outputs()) {
            if (scope.containsKey(tn.name())) {
                error("output '" + tn.name() + "' shadows an in/param of the same name", tn.span());
            } else {
                scope.put(tn.name(), tn.type());
            }
        }

        Set<String> assigned = new HashSet<>();
        for (Assign asgn : n.body()) {
            if (readOnly.contains(asgn.name())) {
                error("cannot reassign '" + asgn.name() + "' (declared as input/param)", asgn.span());
                continue;
            }
            if (!assigned.add(asgn.name())) {
                error("duplicate assignment to '" + asgn.name() + "' (single-assignment rule)", asgn.span());
                continue;
            }
            Type rhsType = inferType(asgn.value(), scope);
            if (rhsType == null) continue;     // an inner error already filed; carry on
            Type declared = scope.get(asgn.name());
            if (declared == null) {
                // New body-local; record its inferred type so subsequent uses type-check.
                scope.put(asgn.name(), rhsType);
            } else if (declared != rhsType) {
                error("'" + asgn.name() + "' declared as " + declared.sourceName()
                                + " but assigned a " + rhsType.sourceName() + " expression",
                        asgn.span());
            }
        }

        for (TypedName out : n.outputs()) {
            if (!assigned.contains(out.name())) {
                error("output '" + out.name() + "' is never assigned in the body", out.span());
            }
        }
    }

    private void addAll(Map<String, Type> scope, List<TypedName> names, String section) {
        for (TypedName tn : names) {
            if (scope.containsKey(tn.name())) {
                error("duplicate name '" + tn.name() + "' in '" + section + ":'", tn.span());
            } else {
                scope.put(tn.name(), tn.type());
            }
        }
    }

    // ─── Activation / Loss ──────────────────────────────────────────────────

    private void checkActivation(Artifact.Activation a) {
        Map<String, Type> scope = new HashMap<>();
        for (TypedName arg : a.args()) {
            if (scope.put(arg.name(), arg.type()) != null) {
                error("duplicate argument '" + arg.name() + "'", arg.span());
            }
        }
        inferType(a.body(), scope);
    }

    private void checkLoss(Artifact.Loss l) {
        if (l.args().size() != 2) {
            error("loss '" + l.name() + "' must take exactly (prediction, target); got "
                    + l.args().size() + " args", l.span());
        }
        Map<String, Type> scope = new HashMap<>();
        for (TypedName arg : l.args()) {
            if (scope.put(arg.name(), arg.type()) != null) {
                error("duplicate argument '" + arg.name() + "'", arg.span());
            }
        }
        Type t = inferType(l.body(), scope);
        if (t != null && t != Type.SCALAR) {
            error("loss body must produce a scalar; got " + t.sourceName(), l.body().span());
        }
    }

    // ─── Type inference ────────────────────────────────────────────────────

    /** Returns the expression's type, or {@code null} if an error was filed. */
    private Type inferType(Expr e, Map<String, Type> scope) {
        return switch (e) {
            case Expr.Num n      -> Type.SCALAR;
            case Expr.VarRef v   -> {
                // A bare type name in expression position is illegal here — those only
                // appear as the LHS of `.zero` / `.one` / `.identity`, which is the
                // FieldAccess case below.
                Type asType = Type.fromSource(v.name());
                if (asType != null) {
                    error("type name '" + v.name() + "' used as a value (did you mean "
                            + v.name() + ".zero / .one / .identity?)", v.span());
                    yield null;
                }
                Type t = scope.get(v.name());
                if (t == null) {
                    error("unknown name '" + v.name() + "'", v.span());
                    yield null;
                }
                yield t;
            }
            case Expr.Unary u    -> inferType(u.operand(), scope);
            case Expr.Binary b   -> inferBinary(b, scope);
            case Expr.Call c     -> inferCall(c, scope);
            case Expr.FieldAccess f  -> inferFieldAccess(f, scope);
            case Expr.MatrixIndex m  -> inferMatrixIndex(m, scope);
            case Expr.AlgebraLit a   -> a.type();    // already-validated literal
        };
    }

    private Type inferBinary(Expr.Binary b, Map<String, Type> scope) {
        Type l = inferType(b.lhs(), scope);
        Type r = inferType(b.rhs(), scope);
        if (l == null || r == null) return null;
        return switch (b.op()) {
            case ADD, SUB -> {
                if (l != r) {
                    error("operands of '" + b.op().symbol + "' must have the same type; got "
                            + l.sourceName() + " and " + r.sourceName(), b.span());
                    yield null;
                }
                yield l;
            }
            case MUL, DIV -> {
                // Scalar broadcast: scalar × T or T × scalar = T.
                if (l == Type.SCALAR && r == Type.SCALAR) yield Type.SCALAR;
                if (l == Type.SCALAR) yield r;
                if (r == Type.SCALAR) yield l;
                if (l == r) yield l;
                error("mismatched algebras: cannot " + b.op().symbol + " a "
                        + l.sourceName() + " and a " + r.sourceName()
                        + " (embed via a constructor first)", b.span());
                yield null;
            }
            case POW -> {
                if (l != Type.SCALAR || r != Type.SCALAR) {
                    error("'^' requires both operands to be scalar; got "
                            + l.sourceName() + " and " + r.sourceName(), b.span());
                    yield null;
                }
                yield Type.SCALAR;
            }
            case DOT -> {
                if (l != r || l == Type.SCALAR) {
                    error("'·' requires two operands of the same algebra type; got "
                            + l.sourceName() + " and " + r.sourceName(), b.span());
                    yield null;
                }
                if (l == Type.MAT2) {
                    error("'·' is not defined for mat2 (use '*' for matrix product)", b.span());
                    yield null;
                }
                yield Type.SCALAR;
            }
        };
    }

    private Type inferCall(Expr.Call c, Map<String, Type> scope) {
        // 1. Type constructor: vec2(a, b), complex(re, im), …
        Type asType = Type.fromSource(c.name());
        if (asType != null) {
            int expected = asType.components;
            if (c.args().size() != expected) {
                error(asType.sourceName() + "(...) takes " + expected + " components; got "
                        + c.args().size(), c.span());
                return asType;
            }
            for (Expr arg : c.args()) {
                Type t = inferType(arg, scope);
                if (t != null && t != Type.SCALAR) {
                    error("constructor component must be scalar; got " + t.sourceName(), arg.span());
                }
            }
            return asType;
        }

        // 2. Algebra-aware ops
        if (ALGEBRA_OPS.contains(c.name())) {
            return inferAlgebraOp(c, scope);
        }

        // 3. Built-in scalar functions
        if (SCALAR_FNS_1.contains(c.name())) return checkScalarArity(c, scope, 1);
        if (SCALAR_FNS_2.contains(c.name())) return checkScalarArity(c, scope, 2);
        if (SCALAR_FNS_3.contains(c.name())) return checkScalarArity(c, scope, 3);

        // 4. Unknown — workspace will resolve later. Type-check the args anyway.
        for (Expr arg : c.args()) inferType(arg, scope);
        diagnostics.add(Diagnostic.warn(
                "unresolved function '" + c.name() + "' — workspace will resolve at link time",
                c.span()));
        return Type.SCALAR;     // optimistic default; the linker can refine
    }

    private Type checkScalarArity(Expr.Call c, Map<String, Type> scope, int expected) {
        if (c.args().size() != expected) {
            error("'" + c.name() + "' takes " + expected + " scalar arg" + (expected == 1 ? "" : "s")
                    + "; got " + c.args().size(), c.span());
        }
        for (Expr arg : c.args()) {
            Type t = inferType(arg, scope);
            if (t != null && t != Type.SCALAR) {
                error("'" + c.name() + "' expects scalar; got " + t.sourceName(), arg.span());
            }
        }
        return Type.SCALAR;
    }

    private Type inferAlgebraOp(Expr.Call c, Map<String, Type> scope) {
        if (c.args().size() != 1) {
            error("'" + c.name() + "' takes 1 argument", c.span());
            return null;
        }
        Type t = inferType(c.args().get(0), scope);
        if (t == null) return null;
        return switch (c.name()) {
            case "norm", "re", "im" -> {
                if (t == Type.SCALAR) {
                    error("'" + c.name() + "' is not defined on scalar", c.span());
                    yield null;
                }
                if ((c.name().equals("re") || c.name().equals("im"))
                        && t != Type.COMPLEX && t != Type.SPLITCOMPLEX) {
                    error("'" + c.name() + "' is only defined on complex / splitcomplex", c.span());
                    yield null;
                }
                yield Type.SCALAR;
            }
            case "conj" -> {
                if (t == Type.SCALAR || t == Type.VEC2 || t == Type.MAT2) {
                    error("'conj' is only defined on complex / splitcomplex / quaternion / coquat",
                            c.span());
                    yield null;
                }
                yield t;
            }
            default -> null;     // unreachable — gated by ALGEBRA_OPS
        };
    }

    private Type inferFieldAccess(Expr.FieldAccess f, Map<String, Type> scope) {
        // Type-level constants: T.zero / T.one / T.identity
        if (f.target() instanceof Expr.VarRef vr) {
            Type asType = Type.fromSource(vr.name());
            if (asType != null) {
                return switch (f.field()) {
                    case "zero", "one"   -> asType;
                    case "identity"      -> {
                        // .identity is only meaningful for algebras with multiplicative identity.
                        if (!asType.hasAlgebraMul() && asType != Type.SCALAR && asType != Type.VEC2) {
                            // (no-op for now — every type has .identity in v1)
                        }
                        yield asType;
                    }
                    default -> {
                        error("type '" + asType.sourceName() + "' has no constant '" + f.field()
                                + "' (expected zero / one / identity)", f.span());
                        yield null;
                    }
                };
            }
        }

        Type targetType = inferType(f.target(), scope);
        if (targetType == null) return null;
        return switch (targetType) {
            case SCALAR -> {
                error("scalar has no fields (use the value directly)", f.span());
                yield null;
            }
            case VEC2 -> switch (f.field()) {
                case "x", "y" -> Type.SCALAR;
                default -> {
                    error("vec2 has no component '" + f.field() + "' (expected x / y)", f.span());
                    yield null;
                }
            };
            case COMPLEX, SPLITCOMPLEX -> switch (f.field()) {
                case "x", "y", "re", "im" -> Type.SCALAR;
                default -> {
                    error(targetType.sourceName() + " has no component '" + f.field()
                            + "' (expected x / y / re / im)", f.span());
                    yield null;
                }
            };
            case QUATERNION, COQUAT -> switch (f.field()) {
                case "w", "x", "y", "z", "i", "j", "k" -> Type.SCALAR;
                default -> {
                    error(targetType.sourceName() + " has no component '" + f.field()
                            + "' (expected w / x / y / z or i / j / k)", f.span());
                    yield null;
                }
            };
            case MAT2 -> {
                error("mat2 elements are addressed via " + ".[row, col], not '." + f.field() + "'",
                        f.span());
                yield null;
            }
        };
    }

    private Type inferMatrixIndex(Expr.MatrixIndex m, Map<String, Type> scope) {
        Type t = inferType(m.target(), scope);
        if (t == null) return null;
        if (t != Type.MAT2) {
            error("'.[r, c]' is only defined on mat2; got " + t.sourceName(), m.span());
            return null;
        }
        if (m.row() < 0 || m.row() > 1 || m.col() < 0 || m.col() > 1) {
            error("mat2 index out of range: [" + m.row() + ", " + m.col() + "] (must be 0 or 1)",
                    m.span());
        }
        return Type.SCALAR;
    }

    // ─── Diagnostic helpers ────────────────────────────────────────────────

    private void error(String message, SourceSpan span) {
        diagnostics.add(Diagnostic.error(message, span));
    }
}
