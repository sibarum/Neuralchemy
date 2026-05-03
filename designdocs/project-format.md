# Project Format

Status: design draft (pre-implementation)
File extension: `.nclab` (Neuralchemy Lab project)

The on-disk format for a Neuralchemy Lab project: what's in it, how it's serialized, how it survives version upgrades, and how it's backed up. The whole thing is shaped by two hard constraints:

1. **No reflection during save / load.** Every serializer is hand-rolled. This keeps the project subsystem GraalVM-native-image clean without per-class reachability config — important because we test SVM compatibility periodically and don't want the project layer to break the build.
2. **Recoverable when the format breaks.** Users will edit the workspace by hand sometimes (especially CSV data), and we'll occasionally ship a backwards-incompatible upgrade. The format and the loader both need to degrade gracefully.

## Archive layout

A project is a zip archive. The `.nclab` extension is sniffed by the OS / file picker; under the hood it's a standard zip and any zip tool can open it.

```
my-experiment.nclab/
├─ manifest.json              ← read FIRST, drives everything else
├─ workspace.json             ← editor state (open tabs, panel sizes, recent layout)
├─ dsl/
│  ├─ neurons/      *.nl
│  ├─ activations/ *.nl
│  └─ losses/      *.nl
├─ networks/        *.json
├─ data/
│  ├─ *.csv                   ← raw data, hand-editable
│  └─ subsets.json            ← named filters/splits over the CSVs
└─ assets/                    ← optional; user-attached files (notes, images)
```

`runs/` is **not** part of the project archive. Simulation results live elsewhere (see "Run results" below) and have their own format conventions.

### Why zip and not a directory?

Two reasons:

- **Atomicity.** A single file moves, syncs, and shares cleanly. A directory project on Dropbox / OneDrive / iCloud is a recipe for half-synced corruption.
- **Hash + diff in one shot.** Shipping a project to a colleague is one URL. Versioning experiments by date is one file.

Cost: editing a single DSL file means unzip-edit-rezip. We mitigate by mounting the archive into a project working directory on open (see "Working directory" below) so the user's editor flows are unaffected.

## Manifest

`manifest.json` is the only file the loader reads before deciding *anything* about the rest of the archive. Hand-rolled, fixed shape, never has unknown top-level keys.

```json
{
  "format": "neuralchemy-lab",
  "format_version": 1,
  "created": "2026-05-01T17:23:11Z",
  "last_saved": "2026-05-01T17:31:42Z",
  "name": "split-quat parameter mixer",
  "description": "comparing 2× complex vs quat vs coquat vs mat2",
  "min_app_version": "0.1.0",
  "contents": {
    "dsl_neurons":    ["dsl/neurons/split_mix.nl", "dsl/neurons/complex_mix.nl"],
    "dsl_activations":["dsl/activations/tanh_squared.nl"],
    "dsl_losses":     ["dsl/losses/mse.nl"],
    "networks":       ["networks/xor-net.json"],
    "datasets":       ["data/xor.csv"],
    "subsets":        "data/subsets.json"
  }
}
```

`format` and `format_version` are the two fields that make upgrade resilience possible. The `contents` map is an **explicit index** of what's in the archive — the loader never globs the zip's directory listing to discover files. Two reasons:

- A user-edited zip can have stray files (`.DS_Store`, half-saved temp files); the manifest is the source of truth.
- The loader's behavior is fully determined by the manifest, which makes corrupted archives diagnosable: a manifest with a path that doesn't resolve produces a specific, actionable error ("manifest references `dsl/neurons/foo.nl` but it's missing from the archive — was the archive only partially extracted?").

## Hand-rolled serializers (no reflection)

Every persistable type has two static methods on a single companion class:

```java
final class WorkspaceCodec {
  static Workspace read(JsonReader r);
  static void     write(Workspace w, JsonWriter j);
}
```

The codecs use a tiny hand-written `JsonReader` / `JsonWriter` (no Jackson, no Gson, no databind). Each codec walks fields by name explicitly:

```java
static Network read(JsonReader r) {
  r.beginObject();
  Network n = new Network();
  while (r.hasNext()) {
    switch (r.nextName()) {
      case "id"        -> n.id        = r.nextString();
      case "nodes"     -> n.nodes     = readNodes(r);
      case "edges"     -> n.edges     = readEdges(r);
      case "viewport"  -> n.viewport  = readViewport(r);
      default          -> r.skip();   // forward-compatible: ignore unknown keys
    }
  }
  r.endObject();
  return n;
}
```

Three rules every codec follows:

1. **Skip unknown fields silently** on read. A v1 reader handed v2 data just ignores the new fields. Lets future versions add stuff without breaking older readers immediately.
2. **Write only the fields you know.** No "round-trip unknown fields" — that's a reflection-shaped feature trap. If a v1 reader saves a v2 archive, fields the reader didn't understand are *lost*. The user is told this on save (see Upgrade strategy).
3. **No nullable schemas.** Every field has a default. Missing field on read = default value, never null. Avoids the entire `Optional<T>` / null-handling sprawl.

### Why not a serialization library?

The four common options each have a deal-breaker:

| Library            | Why not                                                |
|--------------------|--------------------------------------------------------|
| `Serializable` / `Externalizable` | Reflection, opaque binary, no upgrade story |
| Jackson auto-bind  | Reflection-heavy; SVM config is per-class, fragile     |
| Jackson streaming  | OK, but adds 3–4 MB of dependency for json reading     |
| Gson auto-bind     | Same as Jackson auto-bind                              |
| Protobuf / FlatBuf | Binary, schema-driven; heavyweight for project state   |
| Hand-rolled JSON   | ~200 lines of `JsonReader`/`JsonWriter`, fully SVM-clean |

Hand-rolled wins by a mile here because the project layer's serialization surface is small (~10 codec classes total), the format needs to be human-readable, and we control both ends.

## Version upgrade strategy

Three outcomes when the loader meets a `format_version` it doesn't recognize:

| App vs archive              | Behavior                                                      |
|-----------------------------|---------------------------------------------------------------|
| App version ≥ archive version | Open normally. Codecs ignore unknown fields, save in app's version on next save. |
| App version < archive version (minor) | Open read-only with a banner: "Saved by a newer version. Some features may not appear." |
| App version < archive version (major) | Refuse to open. Show: "This project requires Neuralchemy ≥ X.Y.Z." |

A "minor" bump is "added fields, kept all old ones". A "major" bump is "removed or renamed fields a v1 reader needs". The loader's decision is mechanical: minor = open read-only, major = refuse.

**On save, if any data was lost on read** (because we couldn't understand a newer-version field), the save is **blocked** with a confirmation dialog: "Saving will discard <list> from the archive. Save anyway, or cancel?" This is the only place we ever interrupt the user's flow with a dialog.

## Backups

`~/.neuralchemy/` is the standard location across all platforms (Windows: `%USERPROFILE%\.neuralchemy\`; macOS / Linux: `$HOME/.neuralchemy/`).

```
~/.neuralchemy/
├─ recent.json                        ← LRU of recently opened projects
├─ prefs.json                         ← user prefs (theme, keymap, etc.)
├─ crashes/
│  └─ 2026-05-01-1742-stack.txt       ← stack traces from uncaught exceptions
└─ backups/
   └─ <project-stem>/
      ├─ 2026-05-01-1742.nclab        ← last 10 autosaves, by timestamp
      ├─ 2026-05-01-1635.nclab
      └─ ...
```

### Backup policy

- **Trigger**: on every successful save, also write a copy to `backups/<stem>/<ts>.nclab`.
- **Retention**: per-project FIFO of the most recent 10. Pruning happens after a successful new write — if pruning fails (disk full, perms), the backup still exists.
- **Recovery**: a "Recover from backup" command in the palette lists the timestamps for the current project. Selecting one opens it as an unsaved copy.
- **Crash recovery**: on app startup, if the previous run didn't shut down cleanly, offer to open the most recent backup of the project that was open.
- **Privacy**: backups never leave the local machine. No cloud sync, no telemetry.

### Why not autosave-in-place?

Autosaving over the user's `.nclab` would mean any incorrect change persists immediately. The autosave-to-backup pattern keeps the user's "the file on disk is the version I last saved" mental model intact while still recovering from crashes.

## Working directory (open-time hydration)

When a project is opened, the archive is **expanded into a working directory** (`%TEMP%/neuralchemy/<project-id>/` on Windows, `/tmp/...` on Unix). The editor watches that directory; saving zips it back. Three reasons:

- **External editors work.** Users who want to edit a `.nl` file in IntelliJ or `data.csv` in Excel can do so without unzip-edit-rezip ceremony. Our file watcher picks up the changes and re-evaluates dependents.
- **Crash safety.** If we crash mid-save, the working directory is intact even if the zip write failed.
- **Diff-friendly.** A user who points git at the working directory gets file-level diffs instead of "binary blob changed".

The working directory is cleaned up when the project closes cleanly; left intact on crash (so we can recover it).

## Run results (separate)

Simulation runs are not project artifacts. They live in:

```
~/.neuralchemy/runs/<project-stem>/<timestamp>/
├─ config.json          ← network ID, dataset ID, hyperparameters
├─ metrics.csv          ← per-step metrics (epoch, loss, etc.)
├─ probes/              ← optional snapshots from probe nodes
│  └─ probe_3_field_e042.png
└─ summary.json         ← end-of-run aggregates
```

Format choice per file:
- `config.json` — hand-rolled JSON, same conventions as project codecs.
- `metrics.csv` — append-only CSV. Streamable mid-run; openable in any tool.
- `probes/` — whatever raw format the probe emits (PNG for fields, CSV for time-series).
- `summary.json` — final, post-hoc.

Runs are referenced from the project (a network can have a "last 5 runs" sidebar) but never *embedded*. This keeps project archives small and avoids the zip-bloat trap of "open this 200 MB project to see the network you defined in 200 lines".

## Open questions

- **Compression level.** Default zip compression is fine for everything except CSV-heavy datasets, which compress 10×. Probably default to `Deflate` level 6; revisit if save times become annoying.
- **Project ID.** The working-directory path needs a stable identifier. Hash of the absolute archive path? UUID stored in `manifest.json`? UUID is more robust to renames; planning to go with that.
- **Locking.** Two app windows open the same `.nclab`: do we lock it (single-writer) or merge? Single-writer with a clear "this project is open in another window" error is simpler and matches the rest of the never-obscure UX.
- **Schema validation.** Worth shipping a JSON Schema for the manifest and per-artifact files? Probably yes, even if we hand-roll the readers — it documents the format precisely and catches regressions in the writer.
