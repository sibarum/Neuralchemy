package lab.ui.palette;

import lab.ui.AppContext;

/**
 * One entry in the command palette.
 *
 * <p>See designdocs/command-palette.md. Each action registers exactly once at startup; the
 * palette's keymap is the union of every registered shortcut.
 */
public final class Action {

    public enum Kind { ACTION, TOGGLE, NAVIGATE, DOCS }

    public final String id;
    public final String label;
    public final String category;
    public final String description;
    public final String shortcut;       // display string only (e.g. "Ctrl+Shift+P"); null for none
    public final Kind   kind;
    public final Runnable invoke;       // takes no args; reads context via closure when registering

    public Action(String id, String label, String category, String description,
                  String shortcut, Kind kind, Runnable invoke) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.description = description;
        this.shortcut = shortcut;
        this.kind = kind;
        this.invoke = invoke;
    }

    public static Action navigate(String id, String label, String description,
                                  String shortcut, Runnable nav) {
        return new Action(id, label, "Navigate", description, shortcut, Kind.NAVIGATE, nav);
    }

    public static Action action(String id, String label, String category, String description,
                                String shortcut, Runnable run) {
        return new Action(id, label, category, description, shortcut, Kind.ACTION, run);
    }

    public static Action toggle(String id, String label, String category, String description,
                                String shortcut, Runnable run) {
        return new Action(id, label, category, description, shortcut, Kind.TOGGLE, run);
    }

    public static Action docs(String id, String label, String description, Runnable open) {
        return new Action(id, label, "Help", description, null, Kind.DOCS, open);
    }
}
