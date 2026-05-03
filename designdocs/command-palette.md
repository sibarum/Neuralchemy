# Command Palette

Status: design draft (pre-implementation)

The single, unified menu for everything the user can do in the app. Opens with `Ctrl+Shift+P`. Searchable. Shows keyboard shortcuts on the right of every entry. Doubles as the navigation surface ("take me to that data column", "open this run's metrics"). It is the only menu in the app — no menu bar, no right-click menus, no hover popups.

## Why this matters

The "never obscure anything" UX rule means we can't hide things in dropdowns or hover-revealed menus. But the app has lots of features. Without a discovery surface, those features are invisible — not just to new users but to *us* a month from now after we've forgotten what we built.

The palette resolves this: every action lives there, every action is searchable, and the palette teaches its own shortcuts as a side effect of the user using it.

## Anatomy

```
                                                    ┌── Esc to dismiss ──┐
┌────────────────────────────────────────────────────────────────────────┐
│ > sliderr_                                                             │ ← search input
├────────────────────────────────────────────────────────────────────────┤
│ ▶ Insert: Slider node                              [Alt+S]   action   │
│ ▶ Inspector: Convert to slider                     [Alt+C]   action   │
│ ▶ Go to: Learnable nodes (sliders are similar)               navigate │
│ ▶ Help: How sliders work                                     docs     │
└────────────────────────────────────────────────────────────────────────┘
```

- Vertical stack, full width of the host window's content area minus margin.
- ~50% transparent backdrop dims (does not hide) the underlying screen — the palette overlays a recognizable surface, not an obscuring veil.
- Match-highlight emphasizes which characters of each result matched the query.
- The right column shows: **shortcut** (if one exists), **action kind** (action / navigate / toggle / docs), and a chevron when the entry expands sub-options.
- Top result is selected by default; arrow keys move; `Enter` runs; `Esc` dismisses.

## Action registry

Every action in the app is registered exactly once with this shape:

```
Action {
  id:          String           // e.g. "node.insert.slider"
  label:       String           // "Insert: Slider node"
  category:    String           // "Insert" / "View" / "Project" / "Run" / ...
  description: String           // 1 line, shown when the result is selected
  shortcut?:   KeyChord         // optional, shown on the right
  enabled:     () -> boolean    // gates visibility based on context
  kind:        ACTION | TOGGLE | NAVIGATE | DOCS
  invoke:      Context -> Result
}
```

Registration happens at app startup. New screens, new node kinds, new DSL artifacts — each registers the actions they want to expose. Nothing in the app is keyboard-driven outside this registry; the global keymap is the union of every registered shortcut.

### Action kinds

| Kind       | Behavior |
|------------|----------|
| `ACTION`   | Runs once, closes the palette. Most entries. |
| `TOGGLE`   | Flips a boolean; the palette closes; the change is visible somewhere on the screen. The label includes a state hint (`Toggle: Show grid (on)`). |
| `NAVIGATE` | Switches screens / focuses a panel / opens a file. **Briefly highlights** the destination after navigation (a 600 ms outline pulse on the relevant control). |
| `DOCS`     | Opens a help topic in a side panel — never an external browser. |

### Why `NAVIGATE` is its own kind

The user's request: "(nice to have) it can take you places on the app? Highlight it for you?". This is the answer.

The highlight pulse is what makes the palette a *teaching* surface. When you palette-jump to "Insert > Slider node", you also see *where* that command lives in the screen's structure for next time, so muscle memory builds. After a few uses you skip the palette and use the screen control directly.

## Search

- Plain substring match by default — the simplest behavior is the best behavior here.
- Subsequence fallback ("ndsl" matches "Insert > Slider"): if no substring match wins, score by character-positions-in-order with a penalty for gaps.
- Recency boost: actions used in the last 5 minutes float to the top within their score bucket.
- Per-screen weight: actions whose category matches the current screen rank slightly higher than equivalent actions from other screens.
- *No* fuzzy / typo-correcting search. Wrong-character matches make the palette feel magical when it works and confusing when it doesn't; sticking to deterministic prefix / subsequence keeps the result list explainable.

## Special syntax

A small set of prefixes activate filtered modes:

| Prefix | Mode |
|--------|------|
| `>` (default) | All actions |
| `:` | Settings / preferences only |
| `@` | Symbols within the current artifact (DSL: function defs; Network: nodes by name) |
| `?` | Help / docs |

A leading `>` is implied. Typing `:theme` filters to settings actions matching "theme". Typing `@split` in the DSL editor jumps to a function named `split_mix`. The mode prefix is shown as a label inside the input field, not consumed as text the user has to delete.

## Keyboard map

The keymap is *derived* from the registered actions. Two tiers:

- **Reserved** — never bound to user actions: `Ctrl+Shift+P` (palette), `Esc` (cancel modal / close palette), `Ctrl+S` (save project), `Ctrl+Z` / `Ctrl+Shift+Z` (undo / redo). These are app-level guarantees.
- **User-overridable** — every other shortcut. The palette's "Settings: Keyboard" action shows the full registry; conflicts are flagged inline.

A single keypress that matches *both* a global action and a focused-control action goes to the focused control unless the action is in the reserved list. Tab cycles focus normally — the palette doesn't change tab order.

## Context

The palette's filter is automatically scoped by:

- **Current screen** — slightly weights screen-relevant actions higher.
- **Current selection** — actions that operate on selection (rename, delete, convert) only appear when something's selected.
- **Current panel** — actions specific to the focused panel (e.g. "Layer: Add overlay" in a focused mini-viz) appear at the top.

Context never *hides* an action that's globally available; it just affects ranking. The user can always type the action's name and it'll show up.

## Discoverability principles

1. **Every action lives in the palette.** No exceptions. If a feature exists, it's findable here.
2. **Categories are short.** Four to seven top-level categories total: `Insert`, `View`, `Run`, `Project`, `Settings`, `Help`. Anything more granular is a label, not a category.
3. **The shortcut column teaches the keymap.** Users who use the palette ten times for the same action will eventually notice and use the shortcut directly — that's the design.
4. **Description is single-line.** A search result that wraps to two lines is a result with a description that's too long. Trim ruthlessly.
5. **Recently-used floats.** No history pages, no pinned favorites — just a transient recency boost.

## Anti-patterns we're explicitly avoiding

- **Right-click context menus** — they violate the "everything is deliberate" rule (a click on the wrong place obscures the screen).
- **Ribbons / tabbed toolbars** — they hide actions behind a category click. The palette is faster and more discoverable.
- **Modal command bars** with multi-step prompts ("press X to insert, then Y to choose type"). One keypress = one fully-qualified action.
- **Hover-revealed shortcuts.** If the shortcut is in the palette, that's where users learn it.

## Open questions

- **Per-action chord shortcuts** (e.g. `g, n` for "go to network editor"). VS Code does these; they're powerful but hard to communicate. Probably skip for v1; revisit if shortcut conflicts get bad.
- **Palette-driven scripting.** A "record macro" action that captures a sequence of palette invocations and replays them. Really nice for repetitive edits. v2.
- **Search latency.** A few hundred actions is trivially fast. If we ever cross ~10k actions (large workspace + many DSL files), pre-built tries / cached match scores. Premature now.
- **Confirm-on-destructive.** Should "Delete network" prompt? We've said "no dialogs" but data loss is the one place a confirmation pays for itself. Probably: undo handles it, no dialog needed; if undo is somehow not available for an action, that action shouldn't exist.
