package com.raylib.math;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

public final class Vector2 {

    public float x, y;

    public Vector2() {}
    public Vector2(float x, float y) { this.x = x; this.y = y; }

    public Vector2 set(float x, float y) { this.x = x; this.y = y; return this; }
    public Vector2 set(Vector2 o) { return set(o.x, o.y); }
    public Vector2 zero() { x = 0; y = 0; return this; }

    public Vector2 add(Vector2 o)               { return add(o, this); }
    public Vector2 add(Vector2 o, Vector2 dest) { dest.x = x + o.x; dest.y = y + o.y; return dest; }

    public Vector2 sub(Vector2 o)               { return sub(o, this); }
    public Vector2 sub(Vector2 o, Vector2 dest) { dest.x = x - o.x; dest.y = y - o.y; return dest; }

    public Vector2 scale(float s)               { return scale(s, this); }
    public Vector2 scale(float s, Vector2 dest) { dest.x = x * s; dest.y = y * s; return dest; }

    public Vector2 negate()             { return negate(this); }
    public Vector2 negate(Vector2 dest) { dest.x = -x; dest.y = -y; return dest; }

    public float dot(Vector2 o)    { return x * o.x + y * o.y; }
    public float lengthSq()        { return x * x + y * y; }
    public float length()          { return (float) Math.sqrt(lengthSq()); }
    public float distance(Vector2 o) {
        float dx = x - o.x, dy = y - o.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public Vector2 normalize()             { return normalize(this); }
    public Vector2 normalize(Vector2 dest) {
        float len = length();
        if (len < 1e-12f) { dest.x = 0; dest.y = 0; }
        else              { dest.x = x / len; dest.y = y / len; }
        return dest;
    }

    public Vector2 lerp(Vector2 o, float t)               { return lerp(o, t, this); }
    public Vector2 lerp(Vector2 o, float t, Vector2 dest) {
        dest.x = x + (o.x - x) * t;
        dest.y = y + (o.y - y) * t;
        return dest;
    }

    /** Wedge product: gives the signed area / 2D bivector coefficient. */
    public float wedge(Vector2 o) { return x * o.y - y * o.x; }

    public Vector2 writeTo(MemorySegment seg, long offset) {
        seg.set(JAVA_FLOAT, offset,     x);
        seg.set(JAVA_FLOAT, offset + 4, y);
        return this;
    }
    public Vector2 readFrom(MemorySegment seg, long offset) {
        x = seg.get(JAVA_FLOAT, offset);
        y = seg.get(JAVA_FLOAT, offset + 4);
        return this;
    }
}
