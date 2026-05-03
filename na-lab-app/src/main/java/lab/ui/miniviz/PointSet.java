package lab.ui.miniviz;

import com.raylib.Canvas;
import com.raylib.Color;

import lab.ui.AppContext;

/**
 * Scatter of symbols at world-space points. Vertices are packed {@code (x, y, x, y, …)}; one
 * shared {@link Color} keeps the layer cheap. Per-point coloring is a future extension —
 * mini-viz.md hints at {@code (x, y, r, g, b, label_idx)} packing, which we'll add when
 * a screen actually needs it.
 */
public final class PointSet extends Layer {

    public enum Symbol { CIRCLE, SQUARE, CROSS }

    public float[] vertices;
    public int count;
    public Color color;
    public Symbol symbol = Symbol.CIRCLE;
    public float size = 4f;

    public PointSet(float[] vertices, int count, Color color) {
        if (vertices == null) throw new IllegalArgumentException("vertices");
        if (count < 0 || count * 2 > vertices.length) {
            throw new IllegalArgumentException("count out of range");
        }
        if (color == null) throw new IllegalArgumentException("color");
        this.vertices = vertices;
        this.count = count;
        this.color = color;
    }

    public PointSet(float[] vertices, Color color) {
        this(vertices, vertices.length / 2, color);
    }

    public PointSet symbol(Symbol s)  { this.symbol = s; return this; }
    public PointSet size(float s)     { this.size = s; return this; }

    @Override
    public void render(View view, AppContext ctx, Canvas c) {
        for (int i = 0; i < count; i++) {
            int px = view.projectX(vertices[i * 2]);
            int py = view.projectY(vertices[i * 2 + 1]);
            switch (symbol) {
                case CIRCLE -> c.fillCircle(px, py, size, color);
                case SQUARE -> {
                    int s = (int) size;
                    c.fillRect(px - s, py - s, s * 2, s * 2, color);
                }
                case CROSS -> {
                    int s = (int) size;
                    c.drawLine(px - s, py - s, px + s, py + s, color);
                    c.drawLine(px - s, py + s, px + s, py - s, color);
                }
            }
        }
    }
}
