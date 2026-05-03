package com.raylib.math;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

public final class Vector3 {

    public float x, y, z;

    public Vector3() {}
    public Vector3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public Vector3 set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; return this; }
    public Vector3 set(Vector3 o) { return set(o.x, o.y, o.z); }
    public Vector3 zero() { x = 0; y = 0; z = 0; return this; }

    public Vector3 add(Vector3 o)               { return add(o, this); }
    public Vector3 add(Vector3 o, Vector3 dest) {
        dest.x = x + o.x; dest.y = y + o.y; dest.z = z + o.z; return dest;
    }

    public Vector3 sub(Vector3 o)               { return sub(o, this); }
    public Vector3 sub(Vector3 o, Vector3 dest) {
        dest.x = x - o.x; dest.y = y - o.y; dest.z = z - o.z; return dest;
    }

    public Vector3 scale(float s)               { return scale(s, this); }
    public Vector3 scale(float s, Vector3 dest) {
        dest.x = x * s; dest.y = y * s; dest.z = z * s; return dest;
    }

    public Vector3 negate()             { return negate(this); }
    public Vector3 negate(Vector3 dest) { dest.x = -x; dest.y = -y; dest.z = -z; return dest; }

    public float dot(Vector3 o)    { return x * o.x + y * o.y + z * o.z; }
    public float lengthSq()        { return x * x + y * y + z * z; }
    public float length()          { return (float) Math.sqrt(lengthSq()); }

    public Vector3 normalize()             { return normalize(this); }
    public Vector3 normalize(Vector3 dest) {
        float len = length();
        if (len < 1e-12f) { dest.x = 0; dest.y = 0; dest.z = 0; }
        else              { dest.x = x / len; dest.y = y / len; dest.z = z / len; }
        return dest;
    }

    public Vector3 lerp(Vector3 o, float t)               { return lerp(o, t, this); }
    public Vector3 lerp(Vector3 o, float t, Vector3 dest) {
        dest.x = x + (o.x - x) * t;
        dest.y = y + (o.y - y) * t;
        dest.z = z + (o.z - z) * t;
        return dest;
    }

    /** Cross product. Equivalent to the negated Hodge dual of the wedge bivector. */
    public Vector3 cross(Vector3 o, Vector3 dest) {
        float nx = y * o.z - z * o.y;
        float ny = z * o.x - x * o.z;
        float nz = x * o.y - y * o.x;
        dest.x = nx; dest.y = ny; dest.z = nz;
        return dest;
    }

    /**
     * Wedge product: returns the bivector spanned by {@code this} and {@code o}.
     * Components are (xy, yz, zx) — Hodge dual to the cross product.
     */
    public Bivector3 wedge(Vector3 o, Bivector3 dest) {
        dest.xy = x * o.y - y * o.x;
        dest.yz = y * o.z - z * o.y;
        dest.zx = z * o.x - x * o.z;
        return dest;
    }

    /** Hodge dual: e1 ↔ e23, e2 ↔ e31, e3 ↔ e12. Vector → Bivector. */
    public Bivector3 toBivector(Bivector3 dest) {
        dest.yz = x;
        dest.zx = y;
        dest.xy = z;
        return dest;
    }

    public Vector3 writeTo(MemorySegment seg, long offset) {
        seg.set(JAVA_FLOAT, offset,     x);
        seg.set(JAVA_FLOAT, offset + 4, y);
        seg.set(JAVA_FLOAT, offset + 8, z);
        return this;
    }
    public Vector3 readFrom(MemorySegment seg, long offset) {
        x = seg.get(JAVA_FLOAT, offset);
        y = seg.get(JAVA_FLOAT, offset + 4);
        z = seg.get(JAVA_FLOAT, offset + 8);
        return this;
    }
}
