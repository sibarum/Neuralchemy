package lab.project;

import java.util.List;

/**
 * Top-level archive index per project-format.md §Manifest. {@code manifest.json} is the only
 * file the loader reads before deciding anything about the rest of the archive — every other
 * file path goes through {@link #contents}.
 *
 * <p>Defaults are chosen so a partially-filled manifest still round-trips: missing fields
 * become empty strings or {@code 0} or empty lists, never {@code null}, in line with the
 * design-doc rule "no nullable schemas".
 */
public record Manifest(
        String format,
        int    formatVersion,
        String name,
        String description,
        String created,         // ISO-8601 timestamp
        String lastSaved,
        String minAppVersion,
        Contents contents
) {

    public static final String FORMAT          = "neuralchemy-lab";
    public static final int    FORMAT_VERSION  = 1;
    public static final String MIN_APP_VERSION = "0.1.0";

    /** Explicit content index — the loader never globs the zip directory. */
    public record Contents(List<String> networks) {

        public Contents {
            networks = networks == null ? List.of() : List.copyOf(networks);
        }

        public static Contents empty() { return new Contents(List.of()); }
    }

    public Manifest {
        if (format == null)        format        = FORMAT;
        if (name == null)          name          = "";
        if (description == null)   description   = "";
        if (created == null)       created       = "";
        if (lastSaved == null)     lastSaved     = "";
        if (minAppVersion == null) minAppVersion = MIN_APP_VERSION;
        if (contents == null)      contents      = Contents.empty();
    }

    /** Build a fresh manifest at the current instant with sensible defaults. */
    public static Manifest fresh(String name, String description, List<String> networks) {
        String now = java.time.Instant.now().toString();
        return new Manifest(FORMAT, FORMAT_VERSION, name, description, now, now,
                MIN_APP_VERSION, new Contents(networks));
    }

    /** Bump {@link #lastSaved} to now, keep everything else. */
    public Manifest touched() {
        return new Manifest(format, formatVersion, name, description,
                created, java.time.Instant.now().toString(), minAppVersion, contents);
    }
}
