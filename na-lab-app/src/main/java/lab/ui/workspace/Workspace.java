package lab.ui.workspace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lab.dsl.ast.Artifact;
import lab.graph.DslKinds;
import lab.graph.KindRegistry;

/**
 * The set of DSL artifacts the user has open + the {@link KindRegistry} that mirrors them.
 * Held on {@link lab.ui.AppContext} so the DSL editor and the network editor see the same
 * truth — change a {@code .nl} file in one place and node-graph kinds update everywhere.
 *
 * <p>For now the workspace loads a fixed list of bundled samples once at startup. When the
 * project format lands, this is the seam where {@code .nclab} archives will hydrate.
 */
public final class Workspace {

    /** Hardcoded manifest of bundled samples. JAR resource enumeration is unreliable so we list. */
    private static final String[] BUNDLED = {
            "neurons/split_mix.nl",
            "neurons/complex_mix.nl",
            "neurons/linear_mix.nl",
            "neurons/resid.nl",
            "neurons/broken_demo.nl",
            "activations/tanh_squared.nl",
            "losses/mse.nl",
            "losses/mse_vec.nl"
    };

    public final KindRegistry kinds = new KindRegistry();

    private final List<DslFile> files = new ArrayList<>();
    /** Bumped any time a file's source changes — drivers (palette etc.) can poll for resync. */
    private long revision;

    public List<DslFile> files()  { return files; }
    public long revision()        { return revision; }

    /** True after {@link #loadBundled()} has run. Safe to call again — it's idempotent. */
    public boolean loaded() { return !files.isEmpty(); }

    /**
     * Read every bundled {@code .nl} file from the classpath, parse + check each one, and
     * register the DSL-derived {@link lab.graph.NodeKind}s into {@link #kinds}. Idempotent;
     * second call resyncs the kinds against the same files.
     */
    public void loadBundled() {
        files.clear();
        for (String path : BUNDLED) {
            int slash = path.indexOf('/');
            String category = path.substring(0, slash);
            String filename = path.substring(slash + 1);
            String source = readResource("/dsl-samples/" + category + "/" + filename);
            files.add(DslFile.fromSource(category, filename, source));
        }
        resyncKinds();
        revision++;
    }

    /**
     * Replace the source of {@code file} with {@code newSource} (re-parses) and re-sync the
     * {@link KindRegistry} so palette + network editor pick up structural changes immediately.
     */
    public void editFile(DslFile file, String newSource) {
        file.updateSource(newSource);
        resyncKinds();
        revision++;
    }

    /**
     * Append a new DSL file with a uniquely-numbered filename (e.g. {@code untitled_1.nl}).
     * Returns the new file. Bumps revision so the DSL palette actions resync.
     */
    public DslFile addFile(String category, String baseName, String source) {
        String filename = uniqueFilename(category, baseName);
        DslFile file = DslFile.fromSource(category, filename, source);
        files.add(file);
        resyncKinds();
        revision++;
        return file;
    }

    private String uniqueFilename(String category, String baseName) {
        // Try {baseName}.nl, then {baseName}_2.nl, _3.nl, ...
        String candidate = baseName + ".nl";
        int n = 2;
        while (filenameTaken(category, candidate)) {
            candidate = baseName + "_" + n + ".nl";
            n++;
        }
        return candidate;
    }

    private boolean filenameTaken(String category, String filename) {
        for (DslFile f : files) {
            if (f.category.equals(category) && f.filename.equals(filename)) return true;
        }
        return false;
    }

    /** Drop kinds that disappeared and (re-)register everything currently parseable. */
    private void resyncKinds() {
        List<Artifact> artifacts = files.stream()
                .map(DslFile::artifact)
                .filter(Objects::nonNull)
                .toList();
        DslKinds.sync(kinds, artifacts);
    }

    private static String readResource(String resource) {
        try (InputStream in = Workspace.class.getResourceAsStream(resource)) {
            if (in == null) {
                return "// Missing bundled resource: " + resource + "\n";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "// Failed to read " + resource + ": " + e.getMessage() + "\n";
        }
    }
}
