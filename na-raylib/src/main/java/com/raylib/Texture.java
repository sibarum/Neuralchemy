package com.raylib;

import com.raylib.bindings.Raylib;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * GPU texture loaded via raylib. Holds the {@code Texture} struct (id, width, height, mipmaps,
 * format) in a private arena so the segment outlives any per-frame arena.
 *
 * <p>Close to release the GPU resource. Implements {@link AutoCloseable} so try-with-resources
 * works for short-lived textures.
 */
public final class Texture implements AutoCloseable {

    static final long ID_OFFSET     = 0;
    static final long WIDTH_OFFSET  = 4;
    static final long HEIGHT_OFFSET = 8;
    static final long STRUCT_SIZE   = 20;

    private final Arena arena;
    final MemorySegment segment;
    private boolean unloaded;

    private Texture(Arena arena, MemorySegment segment) {
        this.arena = arena;
        this.segment = segment;
    }

    public static Texture load(String path) {
        Arena own = Arena.ofShared();
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment pathSeg = tmp.allocateFrom(path);
            MemorySegment seg = Raylib.LoadTexture(own, pathSeg);
            return new Texture(own, seg);
        }
    }

    public int id()     { return segment.get(JAVA_INT, ID_OFFSET); }
    public int width()  { return segment.get(JAVA_INT, WIDTH_OFFSET); }
    public int height() { return segment.get(JAVA_INT, HEIGHT_OFFSET); }

    @Override
    public void close() {
        if (unloaded) return;
        unloaded = true;
        Raylib.UnloadTexture(segment);
        arena.close();
    }
}
