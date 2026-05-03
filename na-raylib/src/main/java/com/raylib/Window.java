package com.raylib;

import com.raylib.bindings.Raylib;
import com.raylib.math.Vector2;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

/**
 * Wraps raylib's window + input. Only one window may exist per process — raylib uses globals.
 * Use try-with-resources or call {@link #close()}.
 */
public final class Window implements AutoCloseable {

    private boolean closed;

    private Window() {}

    public static Window create(int width, int height, String title) {
        return create(width, height, title, false);
    }

    /**
     * Create a window, optionally with a user-resizable frame. The flag must be set before
     * {@code InitWindow}; raylib copies it into GLFW window hints there.
     */
    public static Window create(int width, int height, String title, boolean resizable) {
        RaylibLoader.load();
        if (resizable) {
            Raylib.SetConfigFlags(Raylib.FLAG_WINDOW_RESIZABLE());
        }
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment titleSeg = tmp.allocateFrom(title);
            Raylib.InitWindow(width, height, titleSeg);
        }
        return new Window();
    }

    public boolean shouldClose()         { return Raylib.WindowShouldClose(); }
    public boolean isReady()             { return Raylib.IsWindowReady(); }

    /**
     * Override the default Escape-closes-window behavior. Pass {@code 0} (or
     * {@link com.raylib.bindings.Raylib#KEY_NULL()}) to disable; pass any other key code
     * to remap. Apps with editable text fields almost always want this disabled — Escape is
     * a useful in-app "dismiss" key.
     */
    public void setExitKey(int keyCode)  { Raylib.SetExitKey(keyCode); }
    public int     width()               { return Raylib.GetScreenWidth(); }
    public int     height()              { return Raylib.GetScreenHeight(); }
    public void    setTargetFPS(int fps) { Raylib.SetTargetFPS(fps); }
    public int     fps()                 { return Raylib.GetFPS(); }
    public float   frameTime()           { return Raylib.GetFrameTime(); }

    public Canvas beginDraw() {
        Raylib.BeginDrawing();
        return new Canvas();
    }

    // ─── Keyboard ────────────────────────────────────────────────────────────
    public boolean isKeyDown(int key)     { return Raylib.IsKeyDown(key); }
    public boolean isKeyUp(int key)       { return Raylib.IsKeyUp(key); }
    public boolean isKeyPressed(int key)  { return Raylib.IsKeyPressed(key); }
    public boolean isKeyReleased(int key) { return Raylib.IsKeyReleased(key); }
    /** Next key code from this frame's queue, or 0 if drained. Drain in a loop. */
    public int     getKeyPressed()        { return Raylib.GetKeyPressed(); }
    /** Next unicode codepoint from this frame's queue, or 0 if drained. Drain in a loop. */
    public int     getCharPressed()       { return Raylib.GetCharPressed(); }

    // ─── Mouse ───────────────────────────────────────────────────────────────
    public boolean isMouseButtonDown(int b)     { return Raylib.IsMouseButtonDown(b); }
    public boolean isMouseButtonPressed(int b)  { return Raylib.IsMouseButtonPressed(b); }
    public boolean isMouseButtonReleased(int b) { return Raylib.IsMouseButtonReleased(b); }
    public int     mouseX()                     { return Raylib.GetMouseX(); }
    public int     mouseY()                     { return Raylib.GetMouseY(); }
    public float   mouseWheel()                 { return Raylib.GetMouseWheelMove(); }

    /** Read the current mouse position into {@code dest} (allocation-free). */
    public Vector2 mousePosition(Vector2 dest) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment seg = Raylib.GetMousePosition(tmp);
            dest.x = seg.get(JAVA_FLOAT, 0);
            dest.y = seg.get(JAVA_FLOAT, 4);
            return dest;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Raylib.CloseWindow();
    }
}
