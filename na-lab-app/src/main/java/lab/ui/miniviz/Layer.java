package lab.ui.miniviz;

import com.raylib.Canvas;

import lab.ui.AppContext;

/**
 * One drawable layer of a {@link MiniViz}. Stacked in z-order; rendered bottom-to-top. Layers
 * own no per-frame allocations — scratch buffers are member fields sized at construction.
 *
 * <p>{@link #close()} releases any GPU resources the layer holds (e.g. a {@code ScalarField}'s
 * texture). The default no-op fits layers backed only by Canvas primitives.
 */
public abstract class Layer implements AutoCloseable {

    /** Render against the given view. Called every frame the host MiniViz is visible. */
    public abstract void render(View view, AppContext ctx, Canvas c);

    @Override
    public void close() {}
}
