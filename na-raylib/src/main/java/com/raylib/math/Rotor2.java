package com.raylib.math;

/**
 * 2D rotor: {@code R = s + xy·e12}. Represents a rotation in the plane.
 * Isomorphic to a complex number, with the same composition rule.
 *
 * <p>Construct via {@link #fromAngle(float)}. Apply via {@link #apply(Vector2, Vector2)}.
 * Sign convention: {@code R = exp(-e12·θ/2) = cos(θ/2) - sin(θ/2)·e12} → counter-clockwise rotation.
 */
public final class Rotor2 {

    public float s, xy;

    public Rotor2() { s = 1; xy = 0; }
    public Rotor2(float s, float xy) { this.s = s; this.xy = xy; }

    public Rotor2 set(float s, float xy) { this.s = s; this.xy = xy; return this; }
    public Rotor2 set(Rotor2 o)          { return set(o.s, o.xy); }
    public Rotor2 identity()             { s = 1; xy = 0; return this; }

    public Rotor2 fromAngle(float angle) {
        float h = angle * 0.5f;
        s = (float) Math.cos(h);
        xy = -(float) Math.sin(h);
        return this;
    }
    public static Rotor2 fromAngle(float angle, Rotor2 dest) { return dest.fromAngle(angle); }

    /** Geometric product: composition of rotations (apply {@code o} then {@code this}). */
    public Rotor2 mul(Rotor2 o, Rotor2 dest) {
        float ns  = s * o.s  - xy * o.xy;
        float nxy = s * o.xy + xy * o.s;
        dest.s = ns; dest.xy = nxy;
        return dest;
    }

    /** Reverse (conjugate): negates the bivector part. {@code R · ~R = 1} for unit rotors. */
    public Rotor2 reverse()              { return reverse(this); }
    public Rotor2 reverse(Rotor2 dest)   { dest.s = s; dest.xy = -xy; return dest; }

    public Rotor2 normalize()            { return normalize(this); }
    public Rotor2 normalize(Rotor2 dest) {
        float m = (float) Math.sqrt(s * s + xy * xy);
        if (m < 1e-12f) { dest.s = 1; dest.xy = 0; }
        else            { dest.s = s / m; dest.xy = xy / m; }
        return dest;
    }

    /** Sandwich product {@code R · v · ~R}: rotates {@code v} by this rotor. */
    public Vector2 apply(Vector2 v, Vector2 dest) {
        // R = (s, xy·e12). Sandwich on a vector in 2D simplifies to a 2x2 rotation:
        // [c -k] [vx]   where c = s² - xy², k = 2·s·xy
        // [k  c] [vy]
        float c = s * s - xy * xy;
        float k = 2f * s * xy;
        float nx =  c * v.x + k * v.y;
        float ny = -k * v.x + c * v.y;
        dest.x = nx; dest.y = ny;
        return dest;
    }
}
