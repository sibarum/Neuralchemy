package lab.dsl;

import lab.dsl.ast.Artifact;
import lab.dsl.check.CheckResult;
import lab.dsl.check.Checker;
import lab.dsl.parse.Parser;

/**
 * Single entry point for callers outside the parser/checker internals. Tiny façade so
 * downstream modules ({@code na-lab-app}, future Truffle integration) only depend on a
 * stable surface — internal package layouts can churn.
 *
 * <p>The DSL follows dsl.md: one artifact per file (filename matches {@code <name>.nl}). The
 * parser is fail-fast on syntax errors; the checker accumulates {@link
 * lab.dsl.check.Diagnostic}s.
 */
public final class Dsl {

    private Dsl() {}

    /** Parse only — throws {@link lab.dsl.parse.ParseException} on syntax errors. */
    public static Artifact parse(String source) {
        return Parser.parse(source);
    }

    /** Parse + check — returns the artifact alongside any diagnostics from the checker. */
    public static CheckResult parseAndCheck(String source) {
        return Checker.check(parse(source));
    }
}
