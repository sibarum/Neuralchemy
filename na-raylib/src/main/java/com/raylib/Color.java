package com.raylib;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * Mutable RGBA color, four bytes (0–255). Layout-compatible with raylib's {@code Color} struct.
 *
 * <p>The named constants ({@link #RAYWHITE}, {@link #RED}, …) are shared instances — treat them as
 * read-only. If you need a color you can mutate, copy via {@code new Color().set(Color.RED)}.
 */
public final class Color {

    public int r, g, b, a;

    public Color() { this(0, 0, 0, 255); }
    public Color(int r, int g, int b)        { this(r, g, b, 255); }
    public Color(int r, int g, int b, int a) { set(r, g, b, a); }

    public Color set(int r, int g, int b, int a) {
        this.r = r; this.g = g; this.b = b; this.a = a; return this;
    }
    public Color set(int r, int g, int b) { return set(r, g, b, 255); }
    public Color set(Color o)             { return set(o.r, o.g, o.b, o.a); }
    public Color setRGBA(int rgba) {
        return set((rgba >>> 24) & 0xff, (rgba >>> 16) & 0xff, (rgba >>> 8) & 0xff, rgba & 0xff);
    }

    public Color writeTo(MemorySegment seg, long offset) {
        seg.set(JAVA_BYTE, offset,     (byte) r);
        seg.set(JAVA_BYTE, offset + 1, (byte) g);
        seg.set(JAVA_BYTE, offset + 2, (byte) b);
        seg.set(JAVA_BYTE, offset + 3, (byte) a);
        return this;
    }

    public Color readFrom(MemorySegment seg, long offset) {
        r = seg.get(JAVA_BYTE, offset)     & 0xff;
        g = seg.get(JAVA_BYTE, offset + 1) & 0xff;
        b = seg.get(JAVA_BYTE, offset + 2) & 0xff;
        a = seg.get(JAVA_BYTE, offset + 3) & 0xff;
        return this;
    }

    public static final Color LIGHTGRAY = new Color(200, 200, 200, 255);
    public static final Color GRAY      = new Color(130, 130, 130, 255);
    public static final Color DARKGRAY  = new Color( 80,  80,  80, 255);
    public static final Color YELLOW    = new Color(253, 249,   0, 255);
    public static final Color GOLD      = new Color(255, 203,   0, 255);
    public static final Color ORANGE    = new Color(255, 161,   0, 255);
    public static final Color PINK      = new Color(255, 109, 194, 255);
    public static final Color RED       = new Color(230,  41,  55, 255);
    public static final Color MAROON    = new Color(190,  33,  55, 255);
    public static final Color GREEN     = new Color(  0, 228,  48, 255);
    public static final Color LIME      = new Color(  0, 158,  47, 255);
    public static final Color DARKGREEN = new Color(  0, 117,  44, 255);
    public static final Color SKYBLUE   = new Color(102, 191, 255, 255);
    public static final Color BLUE      = new Color(  0, 121, 241, 255);
    public static final Color DARKBLUE  = new Color(  0,  82, 172, 255);
    public static final Color PURPLE    = new Color(200, 122, 255, 255);
    public static final Color VIOLET    = new Color(135,  60, 190, 255);
    public static final Color DARKPURPLE = new Color(112, 31, 126, 255);
    public static final Color BEIGE     = new Color(211, 176, 131, 255);
    public static final Color BROWN     = new Color(127, 106,  79, 255);
    public static final Color DARKBROWN = new Color( 76,  63,  47, 255);
    public static final Color WHITE     = new Color(255, 255, 255, 255);
    public static final Color BLACK     = new Color(  0,   0,   0, 255);
    public static final Color BLANK     = new Color(  0,   0,   0,   0);
    public static final Color MAGENTA   = new Color(255,   0, 255, 255);
    public static final Color RAYWHITE  = new Color(245, 245, 245, 255);
}
