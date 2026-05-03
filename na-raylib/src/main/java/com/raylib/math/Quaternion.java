package com.raylib.math;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

/**
 * Interop-only quaternion (xyzw) for crossing into raylib APIs that expect a quaternion.
 * Prefer {@link Rotor3} for all general use; convert at the boundary via
 * {@link Rotor3#toQuaternion(Quaternion)} / {@link Rotor3#fromQuaternion(Quaternion)}.
 */
public final class Quaternion {

    public float x, y, z, w;

    public Quaternion() { x = 0; y = 0; z = 0; w = 1; }
    public Quaternion(float x, float y, float z, float w) { this.x = x; this.y = y; this.z = z; this.w = w; }

    public Quaternion set(float x, float y, float z, float w) {
        this.x = x; this.y = y; this.z = z; this.w = w; return this;
    }
    public Quaternion set(Quaternion o)  { return set(o.x, o.y, o.z, o.w); }
    public Quaternion identity()         { return set(0, 0, 0, 1); }

    public Quaternion writeTo(MemorySegment seg, long offset) {
        seg.set(JAVA_FLOAT, offset,      x);
        seg.set(JAVA_FLOAT, offset + 4,  y);
        seg.set(JAVA_FLOAT, offset + 8,  z);
        seg.set(JAVA_FLOAT, offset + 12, w);
        return this;
    }
    public Quaternion readFrom(MemorySegment seg, long offset) {
        x = seg.get(JAVA_FLOAT, offset);
        y = seg.get(JAVA_FLOAT, offset + 4);
        z = seg.get(JAVA_FLOAT, offset + 8);
        w = seg.get(JAVA_FLOAT, offset + 12);
        return this;
    }
}
