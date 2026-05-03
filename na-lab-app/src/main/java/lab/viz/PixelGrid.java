package lab.viz;

import com.raylib.bindings.Image;
import com.raylib.bindings.Raylib;
import com.raylib.bindings.Rectangle;
import com.raylib.bindings.Vector2;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * RGBA8 CPU pixel buffer paired with a single GPU {@code Texture2D}. Per-frame is
 * {@code setPixel} writes (pure Java, allocation-flat) → one {@code UpdateTexture} downcall →
 * one {@code DrawTexture} downcall. Beats per-pixel {@code DrawPixel} by orders of magnitude
 * for any field/heatmap-style rendering.
 *
 * <p>Must be constructed AFTER {@link com.raylib.Window#create} (needs a live GL context) and
 * closed BEFORE the window — the GPU texture has to be released while the context is still
 * alive. With nested try-with-resources the Window's outer block handles that automatically.
 */
public final class PixelGrid implements AutoCloseable {

    /** raylib's PIXELFORMAT_UNCOMPRESSED_R8G8B8A8 enum value. */
    private static final int PIXELFORMAT_R8G8B8A8 = 7;
    /** raylib's TEXTURE_FILTER_POINT — nearest-neighbor, the only sane choice for a
     *  pixel-art / data-visualization buffer. Default is LINEAR which silently blurs. */
    private static final int TEXTURE_FILTER_POINT = 0;

    public final int width, height;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment pixels;
    private final MemorySegment texture;
    private final MemorySegment tintWhite;
    private final MemorySegment srcRect;
    private final MemorySegment dstRect;
    private final MemorySegment originZero;
    private boolean closed;

    public PixelGrid(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = arena.allocate((long) width * height * 4L);

        MemorySegment image = Image.allocate(arena);
        Image.data(image, pixels);
        Image.width(image, width);
        Image.height(image, height);
        Image.mipmaps(image, 1);
        Image.format(image, PIXELFORMAT_R8G8B8A8);

        this.texture = Raylib.LoadTextureFromImage(arena, image);
        Raylib.SetTextureFilter(texture, TEXTURE_FILTER_POINT);

        this.tintWhite = arena.allocate(4);
        tintWhite.set(JAVA_BYTE, 0, (byte) 0xFF);
        tintWhite.set(JAVA_BYTE, 1, (byte) 0xFF);
        tintWhite.set(JAVA_BYTE, 2, (byte) 0xFF);
        tintWhite.set(JAVA_BYTE, 3, (byte) 0xFF);

        // Reusable structs for scaled blits — one allocation, fields rewritten per draw call.
        this.srcRect = Rectangle.allocate(arena);
        Rectangle.x(srcRect, 0f);
        Rectangle.y(srcRect, 0f);
        Rectangle.width(srcRect, width);
        Rectangle.height(srcRect, height);

        this.dstRect = Rectangle.allocate(arena);
        this.originZero = Vector2.allocate(arena);
        Vector2.x(originZero, 0f);
        Vector2.y(originZero, 0f);
    }

    /** Write a single RGB pixel (alpha forced opaque). r/g/b are 0..255. */
    public void setPixel(int x, int y, int r, int g, int b) {
        long off = ((long) y * width + x) << 2;
        pixels.set(JAVA_BYTE, off,     (byte) r);
        pixels.set(JAVA_BYTE, off + 1, (byte) g);
        pixels.set(JAVA_BYTE, off + 2, (byte) b);
        pixels.set(JAVA_BYTE, off + 3, (byte) 0xFF);
    }

    /** Push the CPU buffer to the GPU. One downcall. Call once per frame after writing. */
    public void upload() {
        Raylib.UpdateTexture(texture, pixels);
    }

    /** Blit the texture at screen position (x, y) — 1:1 size. One downcall. */
    public void draw(int x, int y) {
        Raylib.DrawTexture(texture, x, y, tintWhite);
    }

    /**
     * Blit the texture stretched to {@code (dx, dy, dw, dh)}. Uses {@code DrawTexturePro}; the
     * dst rectangle is rewritten in place each call so this is allocation-flat.
     */
    public void draw(int dx, int dy, int dw, int dh) {
        Rectangle.x(dstRect, dx);
        Rectangle.y(dstRect, dy);
        Rectangle.width(dstRect, dw);
        Rectangle.height(dstRect, dh);
        Raylib.DrawTexturePro(texture, srcRect, dstRect, originZero, 0f, tintWhite);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Raylib.UnloadTexture(texture);
        arena.close();
    }
}
