package lab.dsl.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import lab.dsl.ast.BinOp;
import lab.dsl.ast.Expr;
import lab.dsl.ast.UnaryOp;
import lab.dsl.parse.Parser;

/**
 * Differentiator correctness tests. For each input expression, compute the symbolic derivative
 * with {@link Differentiator#diff}, then evaluate it at sample points and compare against the
 * numerical derivative of the original expression.
 *
 * <p>A small recursive evaluator (private to this test) handles the scalar subset; we don't
 * pull in {@code DslInterpreter} from {@code na-graph} to keep this module's tests self-contained.
 */
class DifferentiatorTest {

    private static final double EPS = 1e-4;
    private static final double TOL = 1e-3;

    @Test void diffOfConstantIsZero() {
        Expr e = parseExpr("3.5");
        assertEquals(0.0, eval(Differentiator.diff(e, "x"), Map.of("x", 1.0)), 1e-9);
    }

    @Test void diffOfVarRefIsKronecker() {
        Expr e = parseExpr("x");
        assertEquals(1.0, eval(Differentiator.diff(e, "x"), Map.of("x", 4.0)), 1e-9);
        assertEquals(0.0, eval(Differentiator.diff(e, "y"), Map.of("x", 4.0, "y", 2.0)), 1e-9);
    }

    @Test void productRule() {
        // d/dx (x * y) = y when y is treated as a constant w.r.t. x
        Expr e = parseExpr("x * y");
        Expr de = Differentiator.diff(e, "x");
        assertEquals(7.0, eval(de, Map.of("x", 3.0, "y", 7.0)), 1e-9);
    }

    @Test void quotientRule() {
        // d/dx (x / y) = 1/y when y is constant w.r.t. x
        Expr e = parseExpr("x / y");
        assertEquals(0.25, eval(Differentiator.diff(e, "x"),
                                Map.of("x", 1.0, "y", 4.0)), 1e-9);
    }

    @Test void powerRuleConstantExponent() {
        // d/dx x^3 = 3 x^2; at x=2 → 12.
        Expr e = parseExpr("x^3");
        assertEquals(12.0, eval(Differentiator.diff(e, "x"), Map.of("x", 2.0)), 1e-9);
    }

    @Test void chainRuleSinExp() {
        // d/dx sin(exp(x)) = cos(exp(x)) * exp(x).
        Expr e = parseExpr("sin(exp(x))");
        assertNumericallyAgrees(e, "x", 0.5);
        assertNumericallyAgrees(e, "x", -1.0);
    }

    @Test void tanhSquaredAgreesNumerically() {
        // The activation from dsl-samples: tanh(x)*tanh(x).
        Expr e = parseExpr("tanh(x) * tanh(x)");
        assertNumericallyAgrees(e, "x", 0.0);
        assertNumericallyAgrees(e, "x", 0.7);
        assertNumericallyAgrees(e, "x", -1.5);
    }

    @Test void mseLossAgreesNumerically() {
        // The DSL sample loss: (yhat - y)^2.
        Expr e = parseExpr("(yhat - y)^2");
        assertNumericallyAgrees(e, "yhat", 0.5,
                Map.of("yhat", 0.5, "y", 0.2));
        assertNumericallyAgrees(e, "y", 0.2,
                Map.of("yhat", 0.5, "y", 0.2));
    }

    @Test void logRule() {
        // d/dx log(x) = 1/x.
        assertEquals(1.0 / 2.5, eval(Differentiator.diff(parseExpr("log(x)"), "x"),
                                     Map.of("x", 2.5)), 1e-9);
    }

    @Test void sqrtRule() {
        // d/dx sqrt(x) = 1/(2 sqrt x); at x=4 → 0.25.
        assertEquals(0.25, eval(Differentiator.diff(parseExpr("sqrt(x)"), "x"),
                                Map.of("x", 4.0)), 1e-9);
    }

    @Test void diffOfOtherVarStaysZero() {
        Expr e = parseExpr("a*x + b*y");
        // ∂/∂a = x.
        assertEquals(3.0, eval(Differentiator.diff(e, "a"),
                Map.of("a", 1.0, "b", 2.0, "x", 3.0, "y", 4.0)), 1e-9);
        // ∂/∂y = b.
        assertEquals(2.0, eval(Differentiator.diff(e, "y"),
                Map.of("a", 1.0, "b", 2.0, "x", 3.0, "y", 4.0)), 1e-9);
    }

    // ─── Numerical-agreement helper ─────────────────────────────────────────

    private static void assertNumericallyAgrees(Expr e, String varName, double atX) {
        Map<String, Double> scope = new HashMap<>();
        scope.put(varName, atX);
        assertNumericallyAgrees(e, varName, atX, scope);
    }

    private static void assertNumericallyAgrees(Expr e, String varName, double atX,
                                                Map<String, Double> baseScope) {
        Expr de = Differentiator.diff(e, varName);

        Map<String, Double> at = new HashMap<>(baseScope);
        at.put(varName, atX);
        double symbolic = eval(de, at);

        Map<String, Double> plus  = new HashMap<>(baseScope); plus .put(varName, atX + EPS);
        Map<String, Double> minus = new HashMap<>(baseScope); minus.put(varName, atX - EPS);
        double numeric = (eval(e, plus) - eval(e, minus)) / (2 * EPS);

        assertTrue(Math.abs(symbolic - numeric) < TOL,
                "diff at " + varName + "=" + atX + ": symbolic=" + symbolic
                        + " numeric=" + numeric);
    }

    // ─── Tiny scalar evaluator for the test ─────────────────────────────────

    private static Expr parseExpr(String src) {
        // Wrap in a single-output activation so the parser accepts a bare expr.
        var artifact = (lab.dsl.ast.Artifact.Activation)
                Parser.parse("activation _t(" + collectVars(src) + ") = " + src + "\n");
        return artifact.body();
    }

    /** Heuristic — extract all distinct identifiers used in {@code src} as the activation's args. */
    private static String collectVars(String src) {
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b([a-zA-Z_]\\w*)\\b").matcher(src);
        while (m.find()) {
            String tok = m.group(1);
            if (RESERVED.contains(tok)) continue;
            seen.add(tok);
        }
        return String.join(", ", seen);
    }

    private static final java.util.Set<String> RESERVED = java.util.Set.of(
            "sin","cos","tan","tanh","sigmoid","relu","abs","sqrt","exp","log",
            "min","max","clamp","lerp","floor","ceil",
            "vec2","complex","splitcomplex","quaternion","coquat","mat2",
            "conj","norm","re","im");

    private static double eval(Expr e, Map<String, Double> scope) {
        return switch (e) {
            case Expr.Num n -> n.value();
            case Expr.VarRef v -> scope.getOrDefault(v.name(), 0.0);
            case Expr.Unary u -> u.op() == UnaryOp.NEG ? -eval(u.operand(), scope) : eval(u.operand(), scope);
            case Expr.Binary b -> {
                double l = eval(b.lhs(), scope), r = eval(b.rhs(), scope);
                yield switch (b.op()) {
                    case ADD -> l + r;
                    case SUB -> l - r;
                    case MUL -> l * r;
                    case DIV -> l / r;
                    case POW -> Math.pow(l, r);
                    case DOT -> l * r;     // scalar dot collapses to mul
                };
            }
            case Expr.Call c -> evalCall(c, scope);
            case Expr.FieldAccess f  -> 0.0;
            case Expr.MatrixIndex m  -> 0.0;
            case Expr.AlgebraLit a   -> 0.0;
        };
    }

    private static double evalCall(Expr.Call c, Map<String, Double> scope) {
        List<Expr> a = c.args();
        return switch (c.name()) {
            case "sin"   -> Math.sin(eval(a.get(0), scope));
            case "cos"   -> Math.cos(eval(a.get(0), scope));
            case "tan"   -> Math.tan(eval(a.get(0), scope));
            case "tanh"  -> Math.tanh(eval(a.get(0), scope));
            case "sigmoid" -> 1.0 / (1.0 + Math.exp(-eval(a.get(0), scope)));
            case "relu"  -> Math.max(0, eval(a.get(0), scope));
            case "abs"   -> Math.abs(eval(a.get(0), scope));
            case "sqrt"  -> Math.sqrt(eval(a.get(0), scope));
            case "exp"   -> Math.exp(eval(a.get(0), scope));
            case "log"   -> Math.log(eval(a.get(0), scope));
            case "floor" -> Math.floor(eval(a.get(0), scope));
            case "ceil"  -> Math.ceil(eval(a.get(0), scope));
            case "min"   -> Math.min(eval(a.get(0), scope), eval(a.get(1), scope));
            case "max"   -> Math.max(eval(a.get(0), scope), eval(a.get(1), scope));
            case "clamp" -> Math.max(eval(a.get(1), scope),
                                     Math.min(eval(a.get(2), scope), eval(a.get(0), scope)));
            case "lerp"  -> {
                double aa = eval(a.get(0), scope);
                double bb = eval(a.get(1), scope);
                double tt = eval(a.get(2), scope);
                yield aa + (bb - aa) * tt;
            }
            default -> 0.0;
        };
    }
}
