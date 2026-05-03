package lab.ui.miniviz;

/**
 * The world-space rectangle a {@link MiniViz} represents and the pixel-space rectangle it
 * draws into. Mutable by design — recomputed when the host MiniViz is resized or the world
 * window is panned/zoomed (none of which exist yet, but the shape is ready for them).
 *
 * <p>World coordinates are mathematical: {@code y} grows up. Pixel coordinates are raylib's:
 * {@code y} grows down. {@link #projectY} flips.
 */
public final class View {

    /** World-space rectangle. */
    public float worldXmin = -1f, worldYmin = -1f, worldXmax = 1f, worldYmax = 1f;

    /** Screen-space rectangle in raylib pixels (top-left origin). */
    public int pixelX, pixelY, pixelW, pixelH;

    public View setWorld(float xmin, float ymin, float xmax, float ymax) {
        this.worldXmin = xmin; this.worldYmin = ymin;
        this.worldXmax = xmax; this.worldYmax = ymax;
        return this;
    }

    public View setPixel(int x, int y, int w, int h) {
        this.pixelX = x; this.pixelY = y;
        this.pixelW = w; this.pixelH = h;
        return this;
    }

    public int projectX(float wx) {
        float t = (wx - worldXmin) / (worldXmax - worldXmin);
        return pixelX + Math.round(t * pixelW);
    }

    public int projectY(float wy) {
        float t = (wy - worldYmin) / (worldYmax - worldYmin);
        return pixelY + pixelH - Math.round(t * pixelH);
    }

    public float worldX(int px) {
        float t = (px - pixelX) / (float) pixelW;
        return worldXmin + t * (worldXmax - worldXmin);
    }

    public float worldY(int py) {
        float t = (py - pixelY) / (float) pixelH;
        return worldYmax - t * (worldYmax - worldYmin);
    }
}
