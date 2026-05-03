package lab.ui.screens;

import lab.ui.RayGui;
import com.raylib.Canvas;
import com.raylib.Color;

import lab.ui.AppContext;
import lab.ui.Screen;
import lab.ui.Theme;
import lab.viz.PixelGrid;

/**
 * The original 2D parameter-mixing demo, hosted as a screen. Three side-by-side panels render
 * {@code (a, b) ⊙ (x, y)} under three real algebras (Linear / Complex / Split-complex).
 *
 * <p>Lazy-allocates GPU pixel grids on {@link #onEnter} and releases them on {@link #onExit} so
 * we don't keep a texture per panel for the lifetime of the app.
 */
public final class ParamMixerScreen implements Screen {

    private enum Algebra { LINEAR, COMPLEX, SPLIT }

    private static final class Panel {
        final String title;
        final Algebra algebra;
        int x, y;
        final int w, h;
        final PixelGrid grid;

        Panel(String title, Algebra algebra, int w, int h) {
            this.title = title; this.algebra = algebra;
            this.w = w; this.h = h;
            this.grid = new PixelGrid(w, h);
        }

        boolean contains(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private static final float EXTENT = 2.5f;
    private static final float COLOR_SCALE = 1.5f;

    private static final Color FRAME_INK = new Color(120, 120, 120, 255);
    private static final Color AXIS  = new Color(255, 255, 255, 110);
    private static final Color INK   = new Color(40, 40, 40, 255);

    private final float[] paramA = { 1.0f };
    private final float[] paramB = { 0.5f };

    private Panel p0, p1, p2;

    @Override public String id()    { return "screen.parammixer"; }
    @Override public String title() { return "Parameter Mixer (demo)"; }

    @Override
    public void onEnter(AppContext ctx) {
        int panelW = 280, panelH = 280;
        p0 = new Panel("Linear (elementwise)", Algebra.LINEAR, panelW, panelH);
        p1 = new Panel("Complex (i² = -1)",    Algebra.COMPLEX, panelW, panelH);
        p2 = new Panel("Split-Cx (j² = +1)",   Algebra.SPLIT,   panelW, panelH);
    }

    @Override
    public void onExit(AppContext ctx) {
        if (p0 != null) p0.grid.close();
        if (p1 != null) p1.grid.close();
        if (p2 != null) p2.grid.close();
        p0 = p1 = p2 = null;
    }

    @Override
    public void render(AppContext ctx, Canvas c) {
        int x = ctx.contentX, y = ctx.contentY, w = ctx.contentW;

        // Top control row: two sliders.
        RayGui.label (x + 10,  y + 10, 60,  24, "param.a");
        RayGui.slider(x + 75,  y + 10, 280, 24, fmt(paramA[0]), null, paramA, -2f, 2f);
        RayGui.label (x + 380, y + 10, 60,  24, "param.b");
        RayGui.slider(x + 445, y + 10, 280, 24, fmt(paramB[0]), null, paramB, -2f, 2f);

        // Place the three panels under the control row.
        int panelTop = y + 56;
        int gap = 16;
        int totalW = p0.w * 3 + gap * 2;
        int startX = x + Math.max(0, (w - totalW) / 2);
        p0.x = startX;                        p0.y = panelTop;
        p1.x = startX + (p0.w + gap);         p1.y = panelTop;
        p2.x = startX + 2 * (p0.w + gap);     p2.y = panelTop;

        Panel hovered = null;
        Panel[] panels = { p0, p1, p2 };
        for (Panel p : panels) {
            renderHeatmap(p, paramA[0], paramB[0]);
            if (p.contains(ctx.mouseX, ctx.mouseY)) hovered = p;
        }
        for (Panel p : panels) {
            p.grid.upload();
            p.grid.draw(p.x, p.y);
            c.drawRect(p.x - 1, p.y - 1, p.w + 2, p.h + 2, FRAME_INK);
            int cx = p.x + p.w / 2;
            int cy = p.y + p.h / 2;
            c.drawLine(p.x + 4, cy, p.x + p.w - 4, cy, AXIS);
            c.drawLine(cx, p.y + 4, cx, p.y + p.h - 4, AXIS);
            ctx.fontSmall.draw(p.title, p.x + 6, p.y + p.h + 4, INK);
        }

        // Hover readout.
        int statusY = panelTop + p0.h + 32;
        if (hovered != null) {
            float ppu = hovered.w / (2f * EXTENT);
            float wx = (ctx.mouseX - (hovered.x + hovered.w / 2f)) / ppu;
            float wy = -(ctx.mouseY - (hovered.y + hovered.h / 2f)) / ppu;
            float ox, oy;
            switch (hovered.algebra) {
                case LINEAR  -> { ox = paramA[0] * wx;                       oy = paramB[0] * wy; }
                case COMPLEX -> { ox = paramA[0] * wx - paramB[0] * wy;      oy = paramA[0] * wy + paramB[0] * wx; }
                case SPLIT   -> { ox = paramA[0] * wx + paramB[0] * wy;      oy = paramA[0] * wy + paramB[0] * wx; }
                default      -> { ox = wx; oy = wy; }
            }
            int outX = (int) (hovered.x + hovered.w / 2f + ox * ppu);
            int outY = (int) (hovered.y + hovered.h / 2f - oy * ppu);
            c.drawLine(ctx.mouseX, ctx.mouseY, outX, outY, INK);
            c.fillCircle(outX, outY, 4, INK);
            c.drawCircle(ctx.mouseX, ctx.mouseY, 5, INK);
            ctx.font.draw(String.format("[%s]   in (%+.2f, %+.2f)   →   out (%+.2f, %+.2f)",
                    hovered.algebra.name(), wx, wy, ox, oy), x + 16, statusY, INK);
        } else {
            ctx.font.draw("hover a panel to inspect a point  ·  drag the sliders to mix",
                    x + 16, statusY, Theme.INK_DIM);
        }
    }

    private static void renderHeatmap(Panel p, float pa, float pb) {
        float ppu = p.w / (2f * EXTENT);
        float halfW = p.w / 2f;
        float halfH = p.h / 2f;
        for (int py = 0; py < p.h; py++) {
            float wy = -(py - halfH) / ppu;
            for (int px = 0; px < p.w; px++) {
                float wx = (px - halfW) / ppu;
                float ox, oy;
                switch (p.algebra) {
                    case LINEAR  -> { ox = pa * wx;            oy = pb * wy; }
                    case COMPLEX -> { ox = pa * wx - pb * wy;  oy = pa * wy + pb * wx; }
                    case SPLIT   -> { ox = pa * wx + pb * wy;  oy = pa * wy + pb * wx; }
                    default      -> { ox = wx; oy = wy; }
                }
                int rgb = encodeDisplacementColor(ox - wx, oy - wy);
                p.grid.setPixel(px, py, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
        }
    }

    private static int encodeDisplacementColor(float dx, float dy) {
        float ndx = clamp(dx / COLOR_SCALE, -1f, 1f);
        float ndy = clamp(dy / COLOR_SCALE, -1f, 1f);
        final int N = 96;
        int trxR, trxG, trxB;
        if (ndx >= 0) { trxR = 255; trxG = 0;   trxB = 255; }
        else          { trxR = 0;   trxG = 255; trxB = 255; }
        int tryR, tryG, tryB;
        if (ndy >= 0) { tryR = 255; tryG = 255; tryB = 0;   }
        else          { tryR = 0;   tryG = 128; tryB = 128; }
        float ax = Math.abs(ndx);
        float ay = Math.abs(ndy);
        int r = N + (int) ((trxR - N) * ax) + (int) ((tryR - N) * ay);
        int g = N + (int) ((trxG - N) * ax) + (int) ((tryG - N) * ay);
        int b = N + (int) ((trxB - N) * ax) + (int) ((tryB - N) * ay);
        if (r < 0) r = 0; else if (r > 255) r = 255;
        if (g < 0) g = 0; else if (g > 255) g = 255;
        if (b < 0) b = 0; else if (b > 255) b = 255;
        return (r << 16) | (g << 8) | b;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static String fmt(float v) { return String.format("%+.2f", v); }
}
