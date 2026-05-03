package lab.ui.miniviz;

import com.raylib.Canvas;

import lab.ui.AppContext;
import lab.viz.PixelGrid;

/**
 * Texture-backed heatmap. Samples a {@link Sampler} per texel, runs the value through a
 * {@link Colormap}, and blits the result stretched to the view's pixel rect.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>Constructor records resolution and sampling parameters but does not allocate GPU
 *       resources — instances are safe to construct before the GL context exists.</li>
 *   <li>The {@link PixelGrid} is created on the first {@link #render} call (which always runs
 *       inside a draw scope, so the context is live).</li>
 *   <li>{@link #close} releases the texture; must be called while the GL context is still up.
 *       The host {@link MiniViz#close} forwards.</li>
 * </ul>
 *
 * <p>Recompute policy: by default the field re-samples and re-uploads every frame. Call
 * {@link #freeze()} once to mark the contents pinned (skip recompute until {@link #markDirty()}
 * or a view change). The "every frame" default keeps live-data demos simple; freeze is for
 * heavy precomputed surfaces that don't change without explicit user action.
 */
public final class ScalarField extends Layer {

    /** Pure function from world-space coordinates to a scalar in roughly {@code [vmin, vmax]}. */
    @FunctionalInterface
    public interface Sampler { float sample(float wx, float wy); }

    public Sampler sampler;
    public Colormap colormap;
    /** Texture resolution (sample grid). Smaller = blockier, faster. */
    public final int resolutionW, resolutionH;
    /** Mapping range: {@code value} clamped to {@code [vmin, vmax]} then linearly to {@code [0, 1]}. */
    public float vmin = -1f, vmax = 1f;

    private PixelGrid grid;
    private boolean autoRecompute = true;
    private boolean dirty = true;
    private float lastWorldXmin, lastWorldYmin, lastWorldXmax, lastWorldYmax;

    public ScalarField(Sampler sampler, Colormap colormap, int resolutionW, int resolutionH) {
        if (sampler == null)  throw new IllegalArgumentException("sampler");
        if (colormap == null) throw new IllegalArgumentException("colormap");
        if (resolutionW <= 0 || resolutionH <= 0) {
            throw new IllegalArgumentException("resolution must be positive");
        }
        this.sampler = sampler;
        this.colormap = colormap;
        this.resolutionW = resolutionW;
        this.resolutionH = resolutionH;
    }

    public ScalarField range(float vmin, float vmax) {
        this.vmin = vmin; this.vmax = vmax;
        this.dirty = true;
        return this;
    }

    /** Pin the current image until {@link #markDirty()} or the world rect changes. */
    public ScalarField freeze() { this.autoRecompute = false; return this; }

    /** Force a recompute on the next {@link #render}. */
    public void markDirty() { this.dirty = true; }

    @Override
    public void render(View view, AppContext ctx, Canvas c) {
        if (grid == null) {
            grid = new PixelGrid(resolutionW, resolutionH);
        }

        boolean viewChanged =
                view.worldXmin != lastWorldXmin || view.worldYmin != lastWorldYmin
             || view.worldXmax != lastWorldXmax || view.worldYmax != lastWorldYmax;

        if (autoRecompute || dirty || viewChanged) {
            recompute(view);
            grid.upload();
            dirty = false;
            lastWorldXmin = view.worldXmin; lastWorldYmin = view.worldYmin;
            lastWorldXmax = view.worldXmax; lastWorldYmax = view.worldYmax;
        }

        grid.draw(view.pixelX, view.pixelY, view.pixelW, view.pixelH);
    }

    private void recompute(View view) {
        float wxRange = view.worldXmax - view.worldXmin;
        float wyRange = view.worldYmax - view.worldYmin;
        float invSpan = 1f / (vmax - vmin);
        for (int py = 0; py < resolutionH; py++) {
            // Texel center; flip y so world's +y is up.
            float ty = (py + 0.5f) / resolutionH;
            float wy = view.worldYmax - ty * wyRange;
            for (int px = 0; px < resolutionW; px++) {
                float tx = (px + 0.5f) / resolutionW;
                float wx = view.worldXmin + tx * wxRange;
                float v = sampler.sample(wx, wy);
                float t = (v - vmin) * invSpan;
                if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
                int rgb = colormap.sample(t);
                grid.setPixel(px, py, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
        }
    }

    @Override
    public void close() {
        if (grid != null) {
            grid.close();
            grid = null;
        }
    }
}
