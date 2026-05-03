package lab.ui.miniviz;

import com.raylib.Canvas;
import com.raylib.Color;

import java.util.ArrayList;
import java.util.List;

import lab.ui.AppContext;
import lab.ui.Theme;

/**
 * Reusable visualization widget. Owns a screen rect, a {@link View} (world↔pixel mapping), and
 * an ordered stack of {@link Layer}s rendered bottom-to-top against the view.
 *
 * <p>The widget itself draws the chrome: optional title bar, optional status strip with hover
 * readout, and the always-on axis baseline (a faint grid + origin axes when origin is in
 * world). Layers draw inside the canvas region between title and status.
 *
 * <p>{@link AutoCloseable}: forwards {@code close} to every layer. A screen that holds MiniViz
 * instances should construct them in {@code onEnter} and {@code close} them in {@code onExit}
 * — texture-backed layers need a live GL context for both.
 */
public final class MiniViz implements AutoCloseable {

    public final View view = new View();
    private final List<Layer> layers = new ArrayList<>();

    public int x, y, w, h;
    public String title;
    public boolean showTitle  = true;
    public boolean showStatus = true;
    public boolean showAxes   = true;
    public boolean hoverable  = true;

    /** Optional override for the hover readout. {@code null} = default "(wx, wy)" formatting. */
    public HoverFormat hoverFormat;

    public MiniViz(int x, int y, int w, int h, String title) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.title = title;
    }

    public MiniViz worldRect(float xmin, float ymin, float xmax, float ymax) {
        view.setWorld(xmin, ymin, xmax, ymax);
        return this;
    }

    public MiniViz add(Layer layer) {
        if (layer == null) throw new IllegalArgumentException("layer");
        layers.add(layer);
        return this;
    }

    /** Replace the current rect (e.g. after a parent resize). */
    public MiniViz rect(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        return this;
    }

    public List<Layer> layers() { return layers; }

    public boolean canvasContains(int px, int py) {
        int titleH  = showTitle  ? Theme.TITLE_BAR_H  : 0;
        int statusH = showStatus ? Theme.STATUS_BAR_H : 0;
        return px >= x + 1
            && py >= y + titleH + 1
            && px <  x + w - 1
            && py <  y + h - statusH - 1;
    }

    public void render(AppContext ctx, Canvas c) {
        // Outer frame.
        c.fillRect(x, y, w, h, Theme.SURFACE);
        c.drawRect(x, y, w, h, Theme.FRAME);

        int titleH  = showTitle  ? Theme.TITLE_BAR_H  : 0;
        int statusH = showStatus ? Theme.STATUS_BAR_H : 0;

        if (showTitle) {
            c.fillRect(x + 1, y + 1, w - 2, titleH - 1, Theme.PANEL);
            c.drawLine(x, y + titleH, x + w, y + titleH, Theme.FRAME);
            ctx.fontSmall.draw(title == null ? "" : title, x + 8, y + 6, Theme.INK);
        }

        // Canvas region.
        int cx = x + 1;
        int cy = y + titleH + 1;
        int cw = w - 2;
        int ch = h - titleH - statusH - 2;
        view.setPixel(cx, cy, cw, ch);

        if (showAxes) drawAxes(c, view);

        for (Layer layer : layers) {
            layer.render(view, ctx, c);
        }

        if (showStatus) {
            int sy = y + h - statusH;
            c.fillRect(x + 1, sy, w - 2, statusH - 1, Theme.PANEL);
            c.drawLine(x, sy, x + w, sy, Theme.FRAME);
            String s;
            if (hoverable && canvasContains(ctx.mouseX, ctx.mouseY)) {
                float wx = view.worldX(ctx.mouseX);
                float wy = view.worldY(ctx.mouseY);
                s = hoverFormat != null
                        ? hoverFormat.format(wx, wy)
                        : String.format("hover  (%+.2f, %+.2f)", wx, wy);
            } else {
                s = "ready";
            }
            ctx.fontSmall.draw(s, x + 8, sy + 4, Theme.INK_DIM);
        }
    }

    @Override
    public void close() {
        for (Layer layer : layers) {
            try { layer.close(); } catch (RuntimeException ignored) {}
        }
        layers.clear();
    }

    /** Faint grid + emphasized origin axes when the origin lies inside the view. */
    private static void drawAxes(Canvas c, View view) {
        Color grid = Theme.FRAME;
        for (int i = 1; i < 4; i++) {
            int gx = view.pixelX + (view.pixelW * i) / 4;
            c.drawLine(gx, view.pixelY, gx, view.pixelY + view.pixelH, grid);
        }
        for (int i = 1; i < 3; i++) {
            int gy = view.pixelY + (view.pixelH * i) / 3;
            c.drawLine(view.pixelX, gy, view.pixelX + view.pixelW, gy, grid);
        }
        if (view.worldXmin <= 0 && view.worldXmax >= 0) {
            int ox = view.projectX(0);
            c.drawLine(ox, view.pixelY, ox, view.pixelY + view.pixelH, Theme.FRAME_HARD);
        }
        if (view.worldYmin <= 0 && view.worldYmax >= 0) {
            int oy = view.projectY(0);
            c.drawLine(view.pixelX, oy, view.pixelX + view.pixelW, oy, Theme.FRAME_HARD);
        }
    }

    @FunctionalInterface
    public interface HoverFormat { String format(float wx, float wy); }
}
