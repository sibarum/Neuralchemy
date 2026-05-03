package lab.ui;

import com.raylib.Color;

/** Shared palette / sizing for the lab shell. Read-only constants — do not mutate. */
public final class Theme {

    private Theme() {}

    public static final Color BG          = new Color(248, 248, 246, 255);
    public static final Color SURFACE     = new Color(255, 255, 255, 255);
    public static final Color PANEL       = new Color(238, 238, 234, 255);
    public static final Color INK         = new Color( 30,  30,  30, 255);
    public static final Color INK_DIM     = new Color(110, 110, 110, 255);
    public static final Color FRAME       = new Color(190, 190, 188, 255);
    public static final Color FRAME_HARD  = new Color(120, 120, 118, 255);
    public static final Color ACCENT      = new Color( 40, 110, 200, 255);
    public static final Color ACCENT_SOFT = new Color(220, 232, 248, 255);
    public static final Color OK          = new Color( 50, 150,  90, 255);
    public static final Color WARN        = new Color(200, 130,  40, 255);
    public static final Color ERROR_RED   = new Color(200,  50,  50, 255);

    /** Subtle highlight for the "current" / "selected" line in the editor. */
    public static final Color LINE_HIGHLIGHT = new Color(240, 245, 250, 255);

    public static final Color OVERLAY_DIM = new Color(30, 30, 30, 130);

    public static final int   STATUS_BAR_H = 24;
    public static final int   TITLE_BAR_H  = 28;

    /** Base font size at scale 1.0. Render size = round(BASE_FONT_PX * uiScale). */
    public static final int   BASE_FONT_PX       = 18;
    public static final int   BASE_FONT_SMALL_PX = 14;

    public static final float UI_SCALE_MIN  = 0.7f;
    public static final float UI_SCALE_MAX  = 2.5f;
    public static final float UI_SCALE_STEP = 0.1f;
}
