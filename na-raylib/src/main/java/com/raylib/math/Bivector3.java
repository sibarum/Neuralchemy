package com.raylib.math;

/**
 * 3D bivector — coefficients on basis blades {@code e12, e23, e31} (cyclic order).
 * Represents an oriented plane; magnitude is the area, orientation is the plane's normal sense.
 *
 * <p>Hodge dual to a {@link Vector3}: {@code e1 ↔ e23, e2 ↔ e31, e3 ↔ e12}.
 */
public final class Bivector3 {

    public float xy, yz, zx;

    public Bivector3() {}
    public Bivector3(float xy, float yz, float zx) { this.xy = xy; this.yz = yz; this.zx = zx; }

    public Bivector3 set(float xy, float yz, float zx) {
        this.xy = xy; this.yz = yz; this.zx = zx; return this;
    }
    public Bivector3 set(Bivector3 o) { return set(o.xy, o.yz, o.zx); }
    public Bivector3 zero()           { xy = 0; yz = 0; zx = 0; return this; }

    public Bivector3 add(Bivector3 o)                    { return add(o, this); }
    public Bivector3 add(Bivector3 o, Bivector3 dest)    {
        dest.xy = xy + o.xy; dest.yz = yz + o.yz; dest.zx = zx + o.zx; return dest;
    }
    public Bivector3 sub(Bivector3 o)                    { return sub(o, this); }
    public Bivector3 sub(Bivector3 o, Bivector3 dest)    {
        dest.xy = xy - o.xy; dest.yz = yz - o.yz; dest.zx = zx - o.zx; return dest;
    }
    public Bivector3 scale(float s)                      { return scale(s, this); }
    public Bivector3 scale(float s, Bivector3 dest)      {
        dest.xy = xy * s; dest.yz = yz * s; dest.zx = zx * s; return dest;
    }
    public Bivector3 negate()                            { return negate(this); }
    public Bivector3 negate(Bivector3 dest)              {
        dest.xy = -xy; dest.yz = -yz; dest.zx = -zx; return dest;
    }

    public float magnitudeSq() { return xy * xy + yz * yz + zx * zx; }
    public float magnitude()   { return (float) Math.sqrt(magnitudeSq()); }

    public Bivector3 normalize()              { return normalize(this); }
    public Bivector3 normalize(Bivector3 dest){
        float m = magnitude();
        if (m < 1e-12f) { dest.xy = 0; dest.yz = 0; dest.zx = 0; }
        else            { dest.xy = xy / m; dest.yz = yz / m; dest.zx = zx / m; }
        return dest;
    }

    /** Hodge dual: e23 ↔ e1, e31 ↔ e2, e12 ↔ e3. Bivector → Vector. */
    public Vector3 toVector(Vector3 dest) {
        dest.x = yz;
        dest.y = zx;
        dest.z = xy;
        return dest;
    }
}
