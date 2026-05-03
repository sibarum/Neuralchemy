package lab.ui.miniviz;

import com.raylib.Canvas;
import com.raylib.Color;

import lab.ui.AppContext;

/**
 * Connected-segment polyline through world-space vertices. Cheap — one line draw per segment,
 * which raylib batches under the hood.
 *
 * <p>Vertices are packed {@code (x, y, x, y, …)} in a single float array. {@link #count} is
 * the number of vertices actually present (so the array can be over-allocated and reused for
 * streaming data).
 */
public final class LineStrip extends Layer {

    public float[] vertices;
    public int count;
    public Color color;
    public boolean closed;

    public LineStrip(float[] vertices, int count, Color color) {
        if (vertices == null) throw new IllegalArgumentException("vertices");
        if (count < 0 || count * 2 > vertices.length) {
            throw new IllegalArgumentException("count out of range");
        }
        if (color == null) throw new IllegalArgumentException("color");
        this.vertices = vertices;
        this.count = count;
        this.color = color;
    }

    public LineStrip(float[] vertices, Color color) {
        this(vertices, vertices.length / 2, color);
    }

    /** Convenience for time-series — y[i] paired with x = i. Allocates once. */
    public static LineStrip series(float[] ys, Color color) {
        float[] verts = new float[ys.length * 2];
        for (int i = 0; i < ys.length; i++) {
            verts[i * 2]     = i;
            verts[i * 2 + 1] = ys[i];
        }
        return new LineStrip(verts, ys.length, color);
    }

    @Override
    public void render(View view, AppContext ctx, Canvas c) {
        if (count < 2) return;
        int prevX = view.projectX(vertices[0]);
        int prevY = view.projectY(vertices[1]);
        for (int i = 1; i < count; i++) {
            int x = view.projectX(vertices[i * 2]);
            int y = view.projectY(vertices[i * 2 + 1]);
            c.drawLine(prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
        if (closed) {
            int firstX = view.projectX(vertices[0]);
            int firstY = view.projectY(vertices[1]);
            c.drawLine(prevX, prevY, firstX, firstY, color);
        }
    }
}
