package lab.viz;

import com.raygui.RayguiLoader;
import com.raygui.bindings.Raygui;
import com.raylib.Color;
import com.raylib.Shader;
import com.raylib.bindings.Font;
import com.raylib.bindings.Raylib;

import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Signed-distance-field (SDF) fonts. Three atlases are loaded once at process start:
 *
 * <ol>
 *   <li>A primary SDF atlas (Segoe UI) at {@link #SDF_BASE_SIZE} px, drawn through the
 *       {@code shaders/sdf.fs} fragment shader. This gives crisp text at <em>any</em>
 *       rendered size — no atlas reload when UI scale changes.</li>
 *   <li>A monospace SDF atlas (Consolas) at {@link #SDF_BASE_SIZE} px, used by code-display
 *       widgets (the DSL editor). Same shader, separate texture.</li>
 *   <li>A plain bitmap atlas (Segoe UI) at {@link #PLAIN_BASE_SIZE} px, handed to raygui via
 *       {@code GuiSetFont}. raygui draws filled rectangles in the same call as text, so it
 *       can't render under the SDF shader (the shader treats alpha as distance, which would
 *       corrupt fills) — it gets its own non-SDF font.</li>
 * </ol>
 *
 * <p>Each {@code AppFont} instance is a lightweight handle: a render size + a pointer to one
 * of the SDF atlases. Mutate {@link #size} freely — that's the global-UI-scale knob.
 */
public final class AppFont {

    /** Atlas size for SDF fonts. Bigger = more texture memory but cleaner glyphs at extreme scales. */
    public static final int SDF_BASE_SIZE   = 64;
    /** Atlas size for the plain raygui font. Picked high enough that raygui downscales rather than upscales. */
    public static final int PLAIN_BASE_SIZE = 32;

    /** Number of glyphs we bake (ASCII printable: 32..126). */
    private static final int DEFAULT_GLYPH_COUNT = 95;

    private static MemorySegment sdfFont;       // proportional SDF atlas (Segoe UI)
    private static MemorySegment monoFont;      // monospace SDF atlas (Consolas)
    private static MemorySegment plainFont;     // non-SDF atlas for raygui
    private static Shader        sdfShader;     // shared SDF fragment shader
    private static MemorySegment posScratch;
    private static MemorySegment colorScratch;
    private static volatile boolean initialized;

    /** Render size in pixels. Mutable so a global UI scale can rewrite it. */
    public int size;

    /** Which atlas this handle draws against — {@link #sdfFont} or {@link #monoFont}. */
    private final MemorySegment atlas;

    private AppFont(int size, MemorySegment atlas) {
        this.size = size;
        this.atlas = atlas;
    }

    /** Proportional SDF text (Segoe UI). The default for chrome and inspectors. */
    public static AppFont load(int size) {
        ensureInit();
        return new AppFont(size, sdfFont);
    }

    /** Monospace SDF text (Consolas). For code, gutters, anywhere column alignment matters. */
    public static AppFont loadMono(int size) {
        ensureInit();
        return new AppFont(size, monoFont);
    }

    public void draw(String text, int x, int y, Color color) {
        drawAt(text, x, y, size, color);
    }

    public void drawAt(String text, int x, int y, int renderSize, Color color) {
        posScratch.set(JAVA_FLOAT, 0, x);
        posScratch.set(JAVA_FLOAT, 4, y);
        colorScratch.set(JAVA_BYTE, 0, (byte) color.r);
        colorScratch.set(JAVA_BYTE, 1, (byte) color.g);
        colorScratch.set(JAVA_BYTE, 2, (byte) color.b);
        colorScratch.set(JAVA_BYTE, 3, (byte) color.a);
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment textSeg = tmp.allocateFrom(text);
            sdfShader.begin();
            try {
                Raylib.DrawTextEx(atlas, textSeg, posScratch, renderSize, 1.0f, colorScratch);
            } finally {
                Shader.end();
            }
        }
    }

    /** Update raygui's text size to track the user's UI scale. */
    public static void setRayguiTextSize(int px) {
        Raygui.GuiSetStyle(0 /* DEFAULT */, 16 /* TEXT_SIZE */, px);
    }

    /** Pixel width of {@code text} when rendered at {@link #size}, using this handle's atlas. */
    public int measure(String text) {
        return measureAt(text, size);
    }

    public int measureAt(String text, int renderSize) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment textSeg = tmp.allocateFrom(text);
            MemorySegment v2 = Raylib.MeasureTextEx(tmp, atlas, textSeg, renderSize, 1.0f);
            return Math.round(v2.get(JAVA_FLOAT, 0));
        }
    }

    // ─── Initialization ──────────────────────────────────────────────────────

    private static synchronized void ensureInit() {
        if (initialized) return;
        RayguiLoader.load();   // raygui needs to be in the process before GuiSetFont

        Arena keep = Arena.global();

        byte[] mainTtf = readTtfBytes("lab.font",     "C:\\Windows\\Fonts\\segoeui.ttf");
        byte[] monoTtf = readTtfBytes("lab.fontMono", "C:\\Windows\\Fonts\\consola.ttf",
                                                       "C:\\Windows\\Fonts\\cour.ttf",
                                                       "C:\\Windows\\Fonts\\segoeui.ttf");

        MemorySegment mainData = copyToNative(keep, mainTtf);
        MemorySegment monoData = copyToNative(keep, monoTtf);

        sdfFont   = buildFont(keep, mainData, mainTtf.length, SDF_BASE_SIZE,   Raylib.FONT_SDF(),     /*pad=*/0, /*pack=*/1, /*bilinear=*/true);
        monoFont  = buildFont(keep, monoData, monoTtf.length, SDF_BASE_SIZE,   Raylib.FONT_SDF(),     /*pad=*/0, /*pack=*/1, /*bilinear=*/true);
        plainFont = buildFont(keep, mainData, mainTtf.length, PLAIN_BASE_SIZE, Raylib.FONT_DEFAULT(), /*pad=*/4, /*pack=*/0, /*bilinear=*/false);

        String fragSrc = readResourceText("/shaders/sdf.fs");
        sdfShader = Shader.fromSource(null, fragSrc);

        posScratch   = keep.allocate(8);
        colorScratch = keep.allocate(4);

        Raygui.GuiSetFont(plainFont);
        Raygui.GuiSetStyle(0 /* DEFAULT */, 16 /* TEXT_SIZE */, 18);

        initialized = true;
    }

    /**
     * Codepoints we bake on top of the printable-ASCII default. Currently just the middle
     * dot (U+00B7) used by the DSL for dot products. Extend if more non-ASCII glyphs become
     * common in user-visible text.
     */
    private static final int[] EXTRA_CODEPOINTS = { 0x00B7 };

    /**
     * Build a Font struct from a TTF buffer + a glyph generator type. Returns a segment owned
     * by {@code keep} (never freed during the process — fonts live as long as the window).
     */
    private static MemorySegment buildFont(Arena keep, MemorySegment fileData, int dataSize,
                                           int baseSize, int fontType, int padding, int packMethod,
                                           boolean bilinear) {
        try (Arena scratch = Arena.ofConfined()) {
            // Build full codepoint set: printable ASCII 32..126 + EXTRA_CODEPOINTS.
            int[] all = new int[95 + EXTRA_CODEPOINTS.length];
            for (int i = 0; i < 95; i++) all[i] = 32 + i;
            System.arraycopy(EXTRA_CODEPOINTS, 0, all, 95, EXTRA_CODEPOINTS.length);
            MemorySegment cpSeg = scratch.allocate(JAVA_INT, all.length);
            for (int i = 0; i < all.length; i++) cpSeg.setAtIndex(JAVA_INT, i, all[i]);

            MemorySegment glyphCountOut = scratch.allocate(JAVA_INT);
            MemorySegment glyphsPtr = Raylib.LoadFontData(
                    fileData, dataSize, baseSize, cpSeg, all.length, fontType, glyphCountOut);
            int glyphCount = glyphCountOut.get(JAVA_INT, 0);
            if (glyphCount <= 0) glyphCount = DEFAULT_GLYPH_COUNT;
            if (glyphsPtr.address() == 0) {
                throw new IllegalStateException("LoadFontData returned NULL — TTF rejected by raylib?");
            }

            MemorySegment recsPtrSlot = scratch.allocate(ADDRESS);
            MemorySegment imageVal = Raylib.GenImageFontAtlas(
                    scratch, glyphsPtr, recsPtrSlot, glyphCount, baseSize, padding, packMethod);
            MemorySegment recsPtr = recsPtrSlot.get(ADDRESS, 0);

            MemorySegment textureVal = Raylib.LoadTextureFromImage(scratch, imageVal);
            if (bilinear) {
                Raylib.SetTextureFilter(textureVal, Raylib.TEXTURE_FILTER_BILINEAR());
            }
            Raylib.UnloadImage(imageVal);

            MemorySegment font = Font.allocate(keep);
            Font.baseSize(font, baseSize);
            Font.glyphCount(font, glyphCount);
            Font.glyphPadding(font, padding);
            Font.texture(font, textureVal);
            Font.recs(font, recsPtr);
            Font.glyphs(font, glyphsPtr);
            return font;
        }
    }

    private static MemorySegment copyToNative(Arena keep, byte[] bytes) {
        MemorySegment seg = keep.allocate(bytes.length);
        MemorySegment.copy(bytes, 0, seg, JAVA_BYTE, 0, bytes.length);
        return seg;
    }

    /**
     * Try {@code -D<sysprop>}, then each {@code candidate} TTF path in order. Falls back to
     * the last candidate if none of the others exist (so a missing Consolas can degrade to
     * Segoe rather than crash — proportional-as-mono looks ugly but the editor still renders).
     */
    private static byte[] readTtfBytes(String sysprop, String... candidates) {
        String prop = sysprop == null ? null : System.getProperty(sysprop);
        if (prop != null && !prop.isEmpty()) {
            byte[] b = tryRead(prop);
            if (b != null) return b;
        }
        for (String c : candidates) {
            byte[] b = tryRead(c);
            if (b != null) return b;
        }
        throw new IllegalStateException("No usable TTF found for " + sysprop
                + " (tried: " + String.join(", ", candidates) + ")");
    }

    private static byte[] tryRead(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) return null;
        Path p = Path.of(pathStr);
        if (!Files.exists(p)) return null;
        try {
            return Files.readAllBytes(p);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read TTF at " + pathStr, e);
        }
    }

    private static String readResourceText(String resource) {
        try (InputStream in = AppFont.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read " + resource, e);
        }
    }
}
