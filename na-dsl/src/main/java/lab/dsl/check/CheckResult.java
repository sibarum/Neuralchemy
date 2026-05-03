package lab.dsl.check;

import java.util.List;

import lab.dsl.ast.Artifact;

/**
 * Output of {@link Checker#check}: the (unchanged) artifact plus the diagnostics it produced.
 *
 * <p>A result is "OK" when no {@link Diagnostic#isError()} is present; warnings are allowed.
 *
 * <p>{@link #artifact} may be {@code null} when callers wrap a parse-time failure in a
 * {@code CheckResult} to keep a single error-reporting surface across the parse + check
 * pipeline. {@link Checker#check} itself never produces a null artifact.
 */
public record CheckResult(Artifact artifact, List<Diagnostic> diagnostics) {

    public boolean ok() {
        for (Diagnostic d : diagnostics) if (d.isError()) return false;
        return true;
    }

    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(Diagnostic::isError).toList();
    }
}
