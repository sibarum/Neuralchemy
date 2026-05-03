package lab.project;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import lab.graph.KindRegistry;
import lab.graph.Network;
import lab.project.codec.ManifestCodec;
import lab.project.codec.NetworkCodec;
import lab.project.json.JsonReader;
import lab.project.json.JsonWriter;

/**
 * Top-level facade for the {@code .nclab} archive format. {@link #save} writes a single zip
 * containing {@code manifest.json} plus one {@code networks/&lt;name&gt;.json} per network;
 * {@link #open} reads it back.
 *
 * <p>This is the v1 minimal subset of project-format.md. Working-directory hydration, autosave
 * backups, and the workspace's DSL files / datasets / runs are explicitly deferred — they
 * land when the workspace adds a "save bundle" concept and the editor needs external-editor
 * round-tripping.
 */
public final class Project {

    /** What an open or save call carries. v1 holds a single primary network; multi-network is trivial later. */
    public record Bundle(Manifest manifest, Map<String, Network> networksByPath) {

        public Bundle {
            if (manifest == null)       throw new IllegalArgumentException("manifest");
            if (networksByPath == null) throw new IllegalArgumentException("networksByPath");
            networksByPath = Map.copyOf(networksByPath);
        }

        /** Convenience: the first (and currently only) network in the bundle. */
        public Network primaryNetwork() {
            return networksByPath.isEmpty() ? null : networksByPath.values().iterator().next();
        }
    }

    private Project() {}

    // ─── Save ──────────────────────────────────────────────────────────────

    /**
     * Write {@code network} to {@code path} as a {@code .nclab} archive. Overwrites if the
     * file exists. Creates parent directories on demand.
     */
    public static void save(Path path, Network network, String name, String description) {
        save(path, new Bundle(
                Manifest.fresh(name, description, List.of("networks/main.json")),
                Map.of("networks/main.json", network)));
    }

    /** Write an arbitrary {@link Bundle} to disk. The manifest is touched (last-saved updated). */
    public static void save(Path path, Bundle bundle) {
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
                writeEntry(zip, "manifest.json", w -> ManifestCodec.write(bundle.manifest().touched(), w));
                for (Map.Entry<String, Network> e : bundle.networksByPath().entrySet()) {
                    writeEntry(zip, e.getKey(), w -> NetworkCodec.write(e.getValue(), w));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("save '" + path + "'", e);
        }
    }

    // ─── Open ──────────────────────────────────────────────────────────────

    /**
     * Open {@code path} and rebuild the in-memory bundle. Networks resolve their kinds via
     * {@code kinds} — typically the workspace's chained registry so DSL kinds + builtins both
     * apply.
     */
    public static Bundle open(Path path, KindRegistry kinds) {
        try {
            Map<String, String> entries = new HashMap<>();
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path))) {
                ZipEntry e;
                while ((e = zip.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    entries.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                }
            }

            String manifestText = entries.get("manifest.json");
            if (manifestText == null) {
                throw new IllegalStateException("not a .nclab archive: missing manifest.json");
            }
            Manifest manifest;
            try (JsonReader r = new JsonReader(new StringReader(manifestText))) {
                manifest = ManifestCodec.read(r);
            }

            Map<String, Network> networks = new java.util.LinkedHashMap<>();
            for (String netPath : manifest.contents().networks()) {
                String text = entries.get(netPath);
                if (text == null) continue;     // forward-compat: missing referenced file is logged, not fatal
                try (JsonReader r = new JsonReader(new StringReader(text))) {
                    networks.put(netPath, NetworkCodec.read(r, kinds));
                }
            }
            return new Bundle(manifest, networks);
        } catch (IOException e) {
            throw new UncheckedIOException("open '" + path + "'", e);
        }
    }

    // ─── Internals ─────────────────────────────────────────────────────────

    /** Functional shape used by {@link #writeEntry}. */
    private interface JsonBody { void write(JsonWriter w); }

    /** Write a JSON-formatted zip entry. The writer flushes before the entry closes. */
    private static void writeEntry(ZipOutputStream zip, String name, JsonBody body) throws IOException {
        // Buffer per entry — ZipOutputStream's underlying writer can't be flushed mid-entry
        // and the JsonWriter buffers internally; serialize into memory then write the bytes.
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (OutputStreamWriter osw = new OutputStreamWriter(buf, StandardCharsets.UTF_8);
             JsonWriter w = new JsonWriter(osw)) {
            body.write(w);
        }
        zip.putNextEntry(new ZipEntry(name));
        zip.write(buf.toByteArray());
        zip.closeEntry();
    }

    /** Hint paths for the default save location, per project-format.md §Backups. */
    public static Path defaultBackupDir() {
        return Path.of(System.getProperty("user.home"), ".neuralchemy");
    }

    /** Convenience: the path used by Save / Open palette actions when no path is selected. */
    public static Path defaultSessionPath() {
        return defaultBackupDir().resolve("last-session.nclab");
    }
}
