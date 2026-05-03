package lab.dsl.check;

import lab.dsl.SourceSpan;

/**
 * One issue found during semantic checking. {@link Severity#ERROR} blocks IR generation;
 * {@link Severity#WARN} (today: only "unresolved function — assumed workspace activation")
 * lets the artifact pass single-file checks but signals to the workspace that resolution is
 * deferred.
 */
public record Diagnostic(Severity severity, String message, SourceSpan span) {

    public enum Severity { ERROR, WARN, INFO }

    public static Diagnostic error(String message, SourceSpan span) {
        return new Diagnostic(Severity.ERROR, message, span);
    }

    public static Diagnostic warn(String message, SourceSpan span) {
        return new Diagnostic(Severity.WARN, message, span);
    }

    public boolean isError() { return severity == Severity.ERROR; }

    @Override
    public String toString() { return severity + " " + span + " " + message; }
}
