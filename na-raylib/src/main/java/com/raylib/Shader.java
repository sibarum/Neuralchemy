package com.raylib;

import com.raylib.bindings.Raylib;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Compiled GPU shader pair (vertex + fragment). Wraps raylib's {@code Shader} struct (a tiny
 * {id, locations*} pair), kept in a private arena so the segment outlives any per-frame arena.
 *
 * <p>Use with try-with-resources for short-lived shaders. For app-lifetime shaders, hold the
 * instance and call {@link #close()} during shutdown.
 *
 * <p>Apply to subsequent draws within a {@link Canvas} scope:
 * <pre>{@code
 *   try (Canvas c = window.beginDraw()) {
 *       shader.begin();
 *       // … draws here go through the shader …
 *       Shader.end();
 *   }
 * }</pre>
 */
public final class Shader implements AutoCloseable {

    private final Arena arena;
    final MemorySegment segment;
    private boolean unloaded;

    private Shader(Arena arena, MemorySegment segment) {
        this.arena = arena;
        this.segment = segment;
    }

    /**
     * Compile a shader from source strings. Pass {@code null} for a stage to use raylib's
     * default. Loaded shaders survive their frame; close when done.
     */
    public static Shader fromSource(String vertexSource, String fragmentSource) {
        RaylibLoader.load();
        Arena own = Arena.ofShared();
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment vs = vertexSource   == null ? MemorySegment.NULL : tmp.allocateFrom(vertexSource);
            MemorySegment fs = fragmentSource == null ? MemorySegment.NULL : tmp.allocateFrom(fragmentSource);
            MemorySegment seg = Raylib.LoadShaderFromMemory(own, vs, fs);
            return new Shader(own, seg);
        }
    }

    /** Make this shader active for subsequent draw calls (until {@link #end} or scope ends). */
    public void begin() {
        Raylib.BeginShaderMode(segment);
    }

    /** Restore raylib's default shader. */
    public static void end() {
        Raylib.EndShaderMode();
    }

    /** Underlying memory segment for callers that need to pass it to other raylib APIs. */
    public MemorySegment segment() { return segment; }

    @Override
    public void close() {
        if (unloaded) return;
        unloaded = true;
        Raylib.UnloadShader(segment);
        arena.close();
    }
}
