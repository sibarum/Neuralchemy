package lab.ui;

import com.raylib.Canvas;

/**
 * One full-window screen. Implementations own no resources beyond what they create in
 * {@link #onEnter}; {@link #onExit} releases them. {@link #render} runs inside a draw scope.
 */
public interface Screen {

    /** Stable id used by navigation actions (e.g. "screen.dsl"). */
    String id();

    /** Human-readable name shown in the title bar / palette. */
    String title();

    default void onEnter(AppContext ctx) {}
    default void onExit(AppContext ctx)  {}

    /** Per-frame logic + drawing. Called inside the canvas scope. */
    void render(AppContext ctx, Canvas c);
}
