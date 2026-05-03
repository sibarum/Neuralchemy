package lab.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lab.dsl.check.CheckResult;
import lab.dsl.check.Diagnostic;

class CheckerTest {

    private static CheckResult check(String src) { return Dsl.parseAndCheck(src); }

    private static boolean hasError(CheckResult r, String fragment) {
        for (Diagnostic d : r.diagnostics()) {
            if (d.isError() && d.message().contains(fragment)) return true;
        }
        return false;
    }

    // ─── Happy paths from dsl.md §"Examples" ────────────────────────────────

    @Test void splitMixSampleChecksClean() {
        CheckResult r = check("""
                neuron split_mix {
                  in:    x, y
                  param: a, b
                  out:   u, v
                  u = a*x + b*y
                  v = b*x + a*y
                }
                """);
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    @Test void linearMixSampleChecksClean() {
        CheckResult r = check("""
                neuron linear_mix {
                  in:    x, y
                  param: a, b
                  out:   u, v
                  u = a*x
                  v = b*y
                }
                """);
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    @Test void complexMixAlgebraicChecksClean() {
        CheckResult r = check("""
                neuron complex_mix {
                  in:    z: complex
                  param: w: complex
                  out:   y: complex
                  y = w * z
                }
                """);
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    @Test void residSampleChecksClean() {
        CheckResult r = check("""
                neuron resid {
                  in:    x: vec2
                  param: a, b: scalar
                  out:   y: vec2
                  y = vec2(a*x.x + b*x.y, b*x.x + a*x.y) + x
                }
                """);
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    @Test void mseSampleChecksClean() {
        CheckResult r = check("loss mse(yhat, y) = (yhat - y)^2");
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    @Test void mseVecSampleChecksClean() {
        CheckResult r = check("loss mse_vec(yhat: vec2, y: vec2) = (yhat - y) · (yhat - y)");
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    @Test void tanhSquaredChecksClean() {
        CheckResult r = check("activation tanh_squared(x) = tanh(x) * tanh(x)");
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    // ─── Compile-time rules from dsl.md §"Compile-time rules" ───────────────

    @Test void singleAssignmentEnforced() {
        CheckResult r = check("""
                neuron bad {
                  in: x
                  out: u
                  u = x
                  u = x + 1
                }
                """);
        assertTrue(hasError(r, "duplicate assignment"));
    }

    @Test void outputsMustBeAssigned() {
        CheckResult r = check("""
                neuron bad {
                  in: x
                  out: u, v
                  u = x
                }
                """);
        assertTrue(hasError(r, "'v' is never assigned"));
    }

    @Test void inputsAndParamsAreReadOnly() {
        CheckResult r = check("""
                neuron bad {
                  in: x
                  param: a
                  out: u
                  a = 1
                  u = a*x
                }
                """);
        assertTrue(hasError(r, "cannot reassign 'a'"));
    }

    @Test void duplicateNameAcrossSectionsRejected() {
        CheckResult r = check("""
                neuron bad {
                  in: x
                  param: x
                  out: u
                  u = x
                }
                """);
        assertTrue(hasError(r, "duplicate name 'x'"));
    }

    @Test void unknownNameInBodyReportsError() {
        CheckResult r = check("""
                neuron bad {
                  in: x
                  out: u
                  u = nope * x
                }
                """);
        assertTrue(hasError(r, "unknown name 'nope'"));
    }

    @Test void lossArityEnforced() {
        CheckResult r = check("loss huber(yhat, y, delta) = yhat - y");
        assertTrue(hasError(r, "must take exactly (prediction, target)"));
    }

    @Test void lossMustReturnScalar() {
        CheckResult r = check("loss bad(yhat: vec2, y: vec2) = yhat - y");
        assertTrue(hasError(r, "loss body must produce a scalar"));
    }

    // ─── Type rules ────────────────────────────────────────────────────────

    @Test void mismatchedAlgebrasOnMulRejected() {
        CheckResult r = check("""
                neuron bad {
                  in:    z: complex
                  param: q: quaternion
                  out:   y: complex
                  y = z * q
                }
                """);
        assertTrue(hasError(r, "mismatched algebras"));
    }

    @Test void scalarBroadcastIntoComplexAllowed() {
        CheckResult r = check("""
                neuron ok {
                  in:    z: complex
                  param: a
                  out:   y: complex
                  y = a * z
                }
                """);
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    @Test void dotProductRequiresMatchingAlgebraTypes() {
        CheckResult r = check("""
                neuron bad {
                  in:    x: vec2, y: complex
                  out:   s: scalar
                  s = x · y
                }
                """);
        assertTrue(hasError(r, "'·' requires two operands of the same algebra type"));
    }

    @Test void dotProductOnScalarsRejected() {
        CheckResult r = check("""
                neuron bad {
                  in: x, y
                  out: s
                  s = x · y
                }
                """);
        assertTrue(hasError(r, "'·' requires"));
    }

    @Test void powRequiresScalarOperands() {
        CheckResult r = check("""
                neuron bad {
                  in: z: complex
                  out: y: complex
                  y = z ^ 2
                }
                """);
        assertTrue(hasError(r, "'^' requires both operands to be scalar"));
    }

    @Test void componentAccessOnVec2ProducesScalar() {
        CheckResult r = check("""
                neuron pick {
                  in:    p: vec2
                  out:   y: scalar
                  y = p.x + p.y
                }
                """);
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    @Test void invalidComponentNameRejected() {
        CheckResult r = check("""
                neuron pick {
                  in:    p: vec2
                  out:   y: scalar
                  y = p.z
                }
                """);
        assertTrue(hasError(r, "vec2 has no component 'z'"));
    }

    @Test void matrixIndexOutOfRange() {
        CheckResult r = check("""
                neuron pick {
                  in: m: mat2
                  out: y: scalar
                  y = m.[2, 0]
                }
                """);
        assertTrue(hasError(r, "out of range"));
    }

    @Test void typeLevelConstantHasConstructorType() {
        CheckResult r = check("""
                neuron seed {
                  in: z: complex
                  out: y: complex
                  y = complex.zero + z
                }
                """);
        assertTrue(r.ok(), r.diagnostics().toString());
    }

    @Test void typeNameAsValueRejected() {
        CheckResult r = check("""
                neuron bad {
                  in: x
                  out: y
                  y = complex
                }
                """);
        assertTrue(hasError(r, "type name 'complex' used as a value"));
    }

    @Test void scalarBuiltinArityChecked() {
        CheckResult r = check("activation f(x) = clamp(x, 0)");
        assertTrue(hasError(r, "'clamp' takes 3 scalar args"));
    }

    @Test void unknownFunctionDeferredAsWarning() {
        CheckResult r = check("activation f(x) = my_workspace_fn(x) + 1");
        assertTrue(r.ok(), "Unresolved function should not be an error: " + r.diagnostics());
        long warns = r.diagnostics().stream()
                .filter(d -> d.severity() == Diagnostic.Severity.WARN).count();
        assertEquals(1, warns);
    }

    @Test void typeConstructorArityChecked() {
        CheckResult r = check("activation f(x) = vec2(x)");
        assertTrue(hasError(r, "vec2(...) takes 2 components"));
    }

    @Test void declaredOutputTypeMustMatchAssignment() {
        CheckResult r = check("""
                neuron bad {
                  in:  x
                  out: y: complex
                  y = x
                }
                """);
        assertFalse(r.ok());
        assertTrue(hasError(r, "declared as complex"));
    }
}
