package lab.ui;

import com.raylib.Canvas;
import com.raylib.Keys;
import com.raylib.Window;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import lab.graph.Network;
import lab.project.Project;

import lab.ui.palette.Action;
import lab.ui.palette.ActionRegistry;
import lab.ui.palette.Palette;
import lab.ui.screens.DataScreen;
import lab.ui.screens.DslEditorScreen;
import lab.ui.screens.NetworkScreen;
import lab.ui.screens.ParamMixerScreen;
import lab.ui.screens.ProjectScreen;
import lab.ui.screens.SimulatorScreen;
import lab.ui.workspace.Workspace;
import lab.viz.AppFont;

/**
 * Top-level shell: a chrome strip at top with project name + current screen, the active screen
 * filling the rest, and the command palette overlay above everything else.
 *
 * <p>Navigation is driven through the palette (and a hidden Ctrl+digit shortcut for testing).
 * No menu bar — see designdocs/command-palette.md.
 */
public final class App {

    private final Window window;
    private final AppContext ctx;
    private final ActionRegistry registry = new ActionRegistry();
    private final Palette palette = new Palette(registry);
    private final Map<String, Screen> screens = new LinkedHashMap<>();
    private final NetworkScreen networkScreen = new NetworkScreen();
    private final DslEditorScreen dslEditorScreen = new DslEditorScreen();
    private Screen current;
    /** Last-seen workspace revision; if it ticks, DSL-derived palette actions are resynced. */
    private long lastWorkspaceRevision = -1;

    public App(Window window, AppFont font, AppFont fontSmall, AppFont fontMono) {
        this.window = window;
        Workspace workspace = new Workspace();
        workspace.loadBundled();
        this.ctx = new AppContext(window, font, fontSmall, fontMono, workspace);
        ctx.applyScale();

        // Register screens.
        register(new ProjectScreen());
        register(dslEditorScreen);
        register(networkScreen);
        register(new DataScreen());
        register(new SimulatorScreen());
        register(new ParamMixerScreen());

        registerActions();
        // First frame's revision check picks up the bundled-load revision and seeds DSL actions.

        // Auto-load last session if it exists. Otherwise the network screen builds the demo
        // on first onEnter as before.
        Path session = Project.defaultSessionPath();
        if (Files.exists(session)) {
            try {
                Network restored = Project.open(session, ctx.workspace.kinds).primaryNetwork();
                if (restored != null) networkScreen.replaceNetwork(restored);
            } catch (RuntimeException ex) {
                System.err.println("Could not load last session at " + session + ": " + ex.getMessage());
            }
        }

        navigate("screen.project");
    }

    private void register(Screen s) { screens.put(s.id(), s); }

    private void registerActions() {
        // Navigation actions — one per screen.
        registry.register(Action.navigate("nav.project",     "Go to: Project",       "Welcome / open / recent",      "Ctrl+1", () -> ctx.navigateTo = "screen.project"));
        registry.register(Action.navigate("nav.dsl",         "Go to: DSL Editor",     "Edit neurons, activations, losses", "Ctrl+2", () -> ctx.navigateTo = "screen.dsl"));
        registry.register(Action.navigate("nav.network",     "Go to: Network",        "Node-graph editor",                 "Ctrl+3", () -> ctx.navigateTo = "screen.network"));
        registry.register(Action.navigate("nav.data",        "Go to: Data",           "Datasets and column inspector",     "Ctrl+4", () -> ctx.navigateTo = "screen.data"));
        registry.register(Action.navigate("nav.simulator",   "Go to: Simulator",      "Run training, watch metrics",       "Ctrl+5", () -> ctx.navigateTo = "screen.simulator"));
        registry.register(Action.navigate("nav.parammixer",  "Go to: Parameter Mixer (demo)", "Original 3-algebra heatmap demo", "Ctrl+0", () -> ctx.navigateTo = "screen.parammixer"));

        // Insert actions — drop a node at the canvas center and switch to the network screen.
        registry.register(Action.action("insert.slider",     "Insert: Slider node",        "Insert", "Drop a scalar slider on the canvas",      "Alt+S",  () -> insertAndShow(lab.graph.BuiltinKinds.SLIDER)));
        registry.register(Action.action("insert.constant",   "Insert: Constant (scalar)",  "Insert", "Drop a literal scalar constant",          null,     () -> insertAndShow(lab.graph.BuiltinKinds.CONSTANT_S)));
        registry.register(Action.action("insert.input",      "Insert: Input (scalar)",     "Insert", "External input port for the network",     null,     () -> insertAndShow(lab.graph.BuiltinKinds.INPUT_S)));
        registry.register(Action.action("insert.output",     "Insert: Output (scalar)",    "Insert", "External output port for the network",    null,     () -> insertAndShow(lab.graph.BuiltinKinds.OUTPUT_S)));
        registry.register(Action.action("insert.add",        "Insert: Add (scalar)",       "Insert", "a + b → sum",                              null,     () -> insertAndShow(lab.graph.BuiltinKinds.ADD_S)));
        registry.register(Action.action("insert.mul",        "Insert: Multiply (scalar)",  "Insert", "a × b → product",                          null,     () -> insertAndShow(lab.graph.BuiltinKinds.MUL_S)));
        registry.register(Action.action("insert.split",      "Insert: Split (vec2 → 2× scalar)", "Insert", "Unpack a vec2 into two scalars",  null,     () -> insertAndShow(lab.graph.BuiltinKinds.SPLIT)));
        registry.register(Action.action("insert.join",       "Insert: Join (2× scalar → vec2)",  "Insert", "Pack two scalars into a vec2",     null,     () -> insertAndShow(lab.graph.BuiltinKinds.JOIN)));
        registry.register(Action.action("insert.probe",      "Insert: Probe (field)",       "Insert", "Mini-viz probe attached to a node",       null,     () -> insertAndShow(lab.graph.BuiltinKinds.PROBE_FIELD)));
        // DSL-derived insert actions are added dynamically by syncDslPaletteActions(); see {@link #run}.

        // "DSL: New *" — create a starter file in the workspace and jump into it.
        registry.register(Action.action("dsl.new.neuron",     "DSL: New neuron",     "DSL",
                "Create a starter .nl neuron file and open it",     null, () -> newDslArtifact("neurons",     "untitled", NEURON_TEMPLATE)));
        registry.register(Action.action("dsl.new.activation", "DSL: New activation", "DSL",
                "Create a starter .nl activation file and open it", null, () -> newDslArtifact("activations", "untitled", ACTIVATION_TEMPLATE)));
        registry.register(Action.action("dsl.new.loss",       "DSL: New loss",       "DSL",
                "Create a starter .nl loss file and open it",       null, () -> newDslArtifact("losses",      "untitled", LOSS_TEMPLATE)));

        registry.register(Action.action("project.new",  "Project: New (reset to demo)", "Project",
                "Discard the current network and rebuild the demo network",   "Ctrl+N", this::resetProject));
        registry.register(Action.action("project.open", "Project: Open last session",   "Project",
                "Read ~/.neuralchemy/last-session.nclab into the network screen", "Ctrl+O",
                () -> openProject(Project.defaultSessionPath())));
        registry.register(Action.action("project.save", "Project: Save",                "Project",
                "Write to ~/.neuralchemy/last-session.nclab",                 "Ctrl+S",
                () -> saveProject(Project.defaultSessionPath())));
        registry.register(Action.action("project.recover", "Project: Recover from backup…", "Project",
                "Pick a timestamped backup (not yet implemented)", null,
                () -> ctx.navigateTo = "screen.project"));

        registry.register(Action.action("run.start", "Run: Start training", "Run", "Begin the simulation", "F5",      () -> ctx.navigateTo = "screen.simulator"));
        registry.register(Action.action("run.step",  "Run: Single step",    "Run", "Advance one tick",     "F10",     () -> ctx.navigateTo = "screen.simulator"));
        registry.register(Action.action("run.reset", "Run: Reset state",    "Run", "Reset learnables / counters", null, () -> ctx.navigateTo = "screen.simulator"));

        registry.register(Action.toggle("view.grid",    "View: Toggle canvas grid", "View", "Show / hide the dot grid", "Ctrl+G", () -> {}));
        registry.register(Action.toggle("view.minimap", "View: Toggle minimap",     "View", "Show / hide the network minimap", null, () -> {}));
        registry.register(Action.action("view.zoom_in",  "View: Increase UI scale", "View", "Make text and chrome larger", "Ctrl+=", () -> bumpScale(+Theme.UI_SCALE_STEP)));
        registry.register(Action.action("view.zoom_out", "View: Decrease UI scale", "View", "Make text and chrome smaller","Ctrl+-", () -> bumpScale(-Theme.UI_SCALE_STEP)));
        registry.register(Action.action("view.zoom_reset","View: Reset UI scale",   "View", "Restore the default 1.0× scale", null,    () -> { ctx.uiScale = 1.0f; ctx.applyScale(); }));

        registry.register(Action.docs("help.dsl",        "Help: DSL syntax",     "How .nl files are parsed",            () -> {}));
        registry.register(Action.docs("help.shortcuts",  "Help: Keyboard map",   "All registered shortcuts",            () -> {}));
        registry.register(Action.docs("help.about",      "Help: About",          "Version, build, design-doc links",    () -> {}));
    }

    public void navigate(String screenId) {
        Screen next = screens.get(screenId);
        if (next == null || next == current) return;
        if (current != null) current.onExit(ctx);
        current = next;
        current.onEnter(ctx);
        ctx.highlightPulse = 0.6f;
    }

    public void run() {
        while (!window.shouldClose()) {
            ctx.mouseX = window.mouseX();
            ctx.mouseY = window.mouseY();
            ctx.paletteOpen = palette.isOpen();

            // Workspace edits (DSL editor changes) bump revision — refresh DSL palette actions.
            if (ctx.workspace.revision() != lastWorkspaceRevision) {
                syncDslPaletteActions();
                lastWorkspaceRevision = ctx.workspace.revision();
            }

            handleHotkeys();
            palette.update(ctx);
            // Re-check after palette consumed events; navigation may have been requested.
            if (ctx.navigateTo != null) {
                navigate(ctx.navigateTo);
                ctx.navigateTo = null;
            }

            int chromeH = 36;
            ctx.contentX = 0;
            ctx.contentY = chromeH;
            ctx.contentW = window.width();
            ctx.contentH = window.height() - chromeH;

            try (Canvas c = window.beginDraw()) {
                c.clearBackground(Theme.BG);
                drawChrome(c, chromeH);
                if (current != null) current.render(ctx, c);
                palette.render(ctx, c);
            }

            if (ctx.highlightPulse > 0) {
                ctx.highlightPulse -= window.frameTime();
                if (ctx.highlightPulse < 0) ctx.highlightPulse = 0;
            }

            // Late navigation requested from inside a screen render.
            if (ctx.navigateTo != null) {
                navigate(ctx.navigateTo);
                ctx.navigateTo = null;
            }
        }
        if (current != null) current.onExit(ctx);
    }

    private void handleHotkeys() {
        boolean ctrl = window.isKeyDown(Keys.LEFT_CONTROL) || window.isKeyDown(Keys.RIGHT_CONTROL);

        if (ctrl && window.isKeyPressed(Keys.SPACE)) { palette.toggle(); return; }

        if (palette.isOpen()) return;

        if (ctrl) {
            if (window.isKeyPressed(Keys.ONE))   ctx.navigateTo = "screen.project";
            if (window.isKeyPressed(Keys.TWO))   ctx.navigateTo = "screen.dsl";
            if (window.isKeyPressed(Keys.THREE)) ctx.navigateTo = "screen.network";
            if (window.isKeyPressed(Keys.FOUR))  ctx.navigateTo = "screen.data";
            if (window.isKeyPressed(Keys.FIVE))  ctx.navigateTo = "screen.simulator";
            if (window.isKeyPressed(Keys.ZERO))  ctx.navigateTo = "screen.parammixer";

            if (window.isKeyPressed(Keys.EQUAL)) bumpScale(+Theme.UI_SCALE_STEP);
            if (window.isKeyPressed(Keys.MINUS)) bumpScale(-Theme.UI_SCALE_STEP);

            if (window.isKeyPressed(Keys.S)) saveProject(Project.defaultSessionPath());
            if (window.isKeyPressed(Keys.O)) openProject(Project.defaultSessionPath());
            if (window.isKeyPressed(Keys.N)) resetProject();
        }
    }

    private static final String NEURON_TEMPLATE = """
            neuron untitled {
              in:  x
              out: y

              y = x
            }
            """;

    private static final String ACTIVATION_TEMPLATE = """
            activation untitled(x) = x
            """;

    private static final String LOSS_TEMPLATE = """
            loss untitled(pred, target) = (pred - target)^2
            """;

    /**
     * Append a new {@code .nl} file to the workspace, switch to the DSL editor, and select the
     * new file. Filename collides cleanly: {@code untitled.nl}, then {@code untitled_2.nl}, etc.
     */
    private void newDslArtifact(String category, String baseName, String template) {
        ctx.workspace.addFile(category, baseName, template);
        // The new file is the last entry in workspace.files() — select it.
        dslEditorScreen.selectFile(ctx.workspace.files().size() - 1);
        ctx.navigateTo = "screen.dsl";
    }

    /**
     * Re-derive {@code Insert: <kind>} palette actions from the workspace's currently-parseable
     * DSL kinds. Called on startup and whenever {@link Workspace#revision()} ticks (i.e. after
     * any keystroke in the DSL editor causes a re-parse). DSL kinds that disappear (artifact
     * deleted or rename) lose their action; new ones get one immediately.
     */
    private void syncDslPaletteActions() {
        registry.removeIf(a -> a.id.startsWith("insert.dsl."));
        for (lab.graph.NodeKind k : ctx.workspace.kinds.local()) {
            if (!k.id().startsWith("dsl.")) continue;
            String label = "Insert: " + describeDslKind(k);
            String description = "Drop a " + k.id() + " node on the canvas";
            registry.register(Action.action(
                    "insert." + k.id(),
                    label,
                    "Insert",
                    description,
                    null,
                    () -> insertAndShow(k.id())));
        }
    }

    /** Human-readable kind label. {@code dsl.neuron.split_mix} → {@code "Neuron split_mix"}. */
    private static String describeDslKind(lab.graph.NodeKind k) {
        String id = k.id();
        if (id.startsWith("dsl.neuron."))     return "Neuron "     + id.substring("dsl.neuron.".length());
        if (id.startsWith("dsl.activation.")) return "Activation " + id.substring("dsl.activation.".length());
        if (id.startsWith("dsl.loss."))       return "Loss "       + id.substring("dsl.loss.".length());
        return id;
    }

    /** Drop a node of {@code kindId} at the canvas center and switch to the network screen. */
    private void insertAndShow(String kindId) {
        networkScreen.insertAtCenter(ctx, kindId);
        ctx.navigateTo = "screen.network";
    }

    private void saveProject(Path path) {
        Network n = networkScreen.currentNetwork();
        if (n == null) {
            System.err.println("nothing to save (network screen never entered)");
            return;
        }
        try {
            Project.save(path, n, "session", "Neuralchemy Lab session");
            System.out.println("Saved " + path);
        } catch (RuntimeException ex) {
            System.err.println("Save failed: " + ex.getMessage());
        }
    }

    private void openProject(Path path) {
        if (!Files.exists(path)) {
            System.err.println("No session at " + path);
            return;
        }
        try {
            Network restored = Project.open(path, ctx.workspace.kinds).primaryNetwork();
            if (restored != null) {
                networkScreen.replaceNetwork(restored);
                ctx.navigateTo = "screen.network";
            }
        } catch (RuntimeException ex) {
            System.err.println("Open failed: " + ex.getMessage());
        }
    }

    private void resetProject() {
        networkScreen.resetToDemo(ctx);
        ctx.navigateTo = "screen.network";
    }

    private void bumpScale(float delta) {
        float next = ctx.uiScale + delta;
        if (next < Theme.UI_SCALE_MIN) next = Theme.UI_SCALE_MIN;
        if (next > Theme.UI_SCALE_MAX) next = Theme.UI_SCALE_MAX;
        ctx.uiScale = next;
        ctx.applyScale();
    }

    private void drawChrome(Canvas c, int chromeH) {
        c.fillRect(0, 0, window.width(), chromeH, Theme.PANEL);
        c.drawLine(0, chromeH, window.width(), chromeH, Theme.FRAME);
        ctx.font.draw("Neuralchemy Lab", 12, 8, Theme.INK);

        String breadcrumb = "·  " + (current == null ? "—" : current.title());
        ctx.font.draw(breadcrumb, 170, 8, Theme.INK_DIM);

        String hint = String.format("UI %.0f%%  ·  Ctrl+= / Ctrl+-  ·  Ctrl+Space  command palette",
                ctx.uiScale * 100);
        int tw = ctx.fontSmall.measure(hint);
        ctx.fontSmall.draw(hint, window.width() - tw - 12, 8, Theme.INK_DIM);

        if (ctx.highlightPulse > 0) {
            // Brief outline pulse on the chrome to show navigation took effect.
            int alpha = (int) Math.min(255, ctx.highlightPulse * 400);
            com.raylib.Color pulse = new com.raylib.Color(40, 110, 200, alpha);
            c.drawRect(0, 0, window.width(), chromeH, pulse);
        }
    }
}
