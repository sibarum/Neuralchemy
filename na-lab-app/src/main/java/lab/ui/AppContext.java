package lab.ui;

import com.raylib.Window;

import lab.ui.workspace.Workspace;
import lab.viz.AppFont;

/** Shared, per-frame-rebuilt state passed to screens and widgets. */
public final class AppContext {

    public final Window    window;
    public final AppFont   font;
    public final AppFont   fontSmall;
    public final AppFont   fontMono;
    public final Workspace workspace;

    /** Layout rect available for the current screen (window minus chrome). */
    public int contentX, contentY, contentW, contentH;

    /** Current pointer position; mirrored from window for convenience. */
    public int mouseX, mouseY;

    /** Set by App when navigation is requested; consumed at end of frame. */
    public String navigateTo;

    /** Set true when the palette is open; screens may suppress hotkeys while true. */
    public boolean paletteOpen;

    /** Pulse-highlight timer (seconds remaining) seeded after a NAVIGATE action. */
    public float highlightPulse;

    /** Global UI scale multiplier; 1.0 = base. Drives font sizes; layout still uses pixels. */
    public float uiScale = 1.0f;

    public AppContext(Window window, AppFont font, AppFont fontSmall, AppFont fontMono,
                      Workspace workspace) {
        this.window    = window;
        this.font      = font;
        this.fontSmall = fontSmall;
        this.fontMono  = fontMono;
        this.workspace = workspace;
    }

    /** Apply current scale to fonts + raygui. Cheap — no atlas reload (SDF scales freely). */
    public void applyScale() {
        font.size      = Math.round(Theme.BASE_FONT_PX       * uiScale);
        fontSmall.size = Math.round(Theme.BASE_FONT_SMALL_PX * uiScale);
        fontMono.size  = Math.round(Theme.BASE_FONT_PX       * uiScale);
        AppFont.setRayguiTextSize(font.size);
    }
}
