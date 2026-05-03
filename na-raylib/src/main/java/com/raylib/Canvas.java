package com.raylib;

import com.raylib.bindings.Raylib;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * A drawing scope between {@code BeginDrawing} and {@code EndDrawing}. Returned from
 * {@link Window#beginDraw()} and meant for try-with-resources. Owns a per-frame arena that recycles
 * the small native segments (Color, Rectangle) used for each draw call — so primitive draws are
 * allocation-free after the first call of each kind.
 */
public final class Canvas implements AutoCloseable {

    private final Arena frameArena = Arena.ofConfined();
    private final MemorySegment colorSeg = frameArena.allocate(4);
    private boolean ended;

    Canvas() {}

    private MemorySegment color(Color c) {
        c.writeTo(colorSeg, 0);
        return colorSeg;
    }

    // ─── Background / state ─────────────────────────────────────────────────
    public Canvas clearBackground(Color c) {
        Raylib.ClearBackground(color(c));
        return this;
    }

    // ─── Primitives ─────────────────────────────────────────────────────────
    public Canvas drawPixel(int x, int y, Color c) {
        Raylib.DrawPixel(x, y, color(c));
        return this;
    }

    public Canvas drawLine(int x1, int y1, int x2, int y2, Color c) {
        Raylib.DrawLine(x1, y1, x2, y2, color(c));
        return this;
    }

    public Canvas drawRect(int x, int y, int w, int h, Color c) {
        Raylib.DrawRectangleLines(x, y, w, h, color(c));
        return this;
    }

    public Canvas fillRect(int x, int y, int w, int h, Color c) {
        Raylib.DrawRectangle(x, y, w, h, color(c));
        return this;
    }

    public Canvas drawCircle(int cx, int cy, float radius, Color c) {
        Raylib.DrawCircleLines(cx, cy, radius, color(c));
        return this;
    }

    public Canvas fillCircle(int cx, int cy, float radius, Color c) {
        Raylib.DrawCircle(cx, cy, radius, color(c));
        return this;
    }

    // ─── Text ───────────────────────────────────────────────────────────────
    public Canvas drawText(String text, int x, int y, int fontSize, Color c) {
        MemorySegment ts = frameArena.allocateFrom(text);
        Raylib.DrawText(ts, x, y, fontSize, color(c));
        return this;
    }

    public int measureText(String text, int fontSize) {
        MemorySegment ts = frameArena.allocateFrom(text);
        return Raylib.MeasureText(ts, fontSize);
    }

    // ─── Texture ────────────────────────────────────────────────────────────
    public Canvas drawTexture(Texture tex, int x, int y, Color tint) {
        Raylib.DrawTexture(tex.segment, x, y, color(tint));
        return this;
    }

    @Override
    public void close() {
        if (ended) return;
        ended = true;
        Raylib.EndDrawing();
        frameArena.close();
    }
}
