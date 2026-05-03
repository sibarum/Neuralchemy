package lab.ui.workspace;

import java.util.List;

import lab.dsl.Dsl;
import lab.dsl.ast.Artifact;
import lab.dsl.check.CheckResult;
import lab.dsl.check.Diagnostic;
import lab.dsl.parse.ParseException;

/**
 * One {@code .nl} file resident in the workspace: source text, the parsed
 * {@link Artifact} (or {@code null} if parsing failed), and any diagnostics from the
 * checker. {@link #lines} is pre-split for the editor's row-by-row rendering — done once
 * here rather than recomputing per frame.
 *
 * <p>Mutable on {@link #source}: the editor calls {@link #updateSource} after each keystroke,
 * which re-parses and refreshes the artifact / diagnostics. Identity (category + filename)
 * is fixed for the lifetime of the instance.
 */
public final class DslFile {

    public final String category;
    public final String filename;

    private String source;
    private Artifact artifact;          // null on parse failure
    private List<Diagnostic> diagnostics;
    private List<String> lines;

    public DslFile(String category, String filename, String source,
                   Artifact artifact, List<Diagnostic> diagnostics) {
        this.category = category;
        this.filename = filename;
        this.source = source;
        this.artifact = artifact;
        this.diagnostics = diagnostics;
        this.lines = source.lines().toList();
    }

    /** Build a fresh DslFile by parsing {@code source} and running the checker. */
    public static DslFile fromSource(String category, String filename, String source) {
        try {
            CheckResult r = Dsl.parseAndCheck(source);
            return new DslFile(category, filename, source, r.artifact(), r.diagnostics());
        } catch (ParseException e) {
            return new DslFile(category, filename, source, null,
                    List.of(Diagnostic.error(e.rawMessage, e.span)));
        }
    }

    public String                source()      { return source; }
    public Artifact              artifact()    { return artifact; }
    public List<Diagnostic>      diagnostics() { return diagnostics; }
    public List<String>          lines()       { return lines; }

    /** Replace the source text and re-parse. Caller is responsible for any downstream re-sync. */
    public void updateSource(String newSource) {
        if (newSource == null) newSource = "";
        this.source = newSource;
        this.lines = newSource.lines().toList();
        try {
            CheckResult r = Dsl.parseAndCheck(newSource);
            this.artifact = r.artifact();
            this.diagnostics = r.diagnostics();
        } catch (ParseException e) {
            this.artifact = null;
            this.diagnostics = List.of(Diagnostic.error(e.rawMessage, e.span));
        }
    }

    public String path() { return "dsl/" + category + "/" + filename; }

    public int errorCount() {
        return (int) diagnostics.stream().filter(Diagnostic::isError).count();
    }

    public int warnCount() {
        return (int) diagnostics.stream()
                .filter(d -> d.severity() == Diagnostic.Severity.WARN)
                .count();
    }
}
