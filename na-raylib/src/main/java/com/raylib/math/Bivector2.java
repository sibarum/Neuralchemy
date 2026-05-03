package com.raylib.math;

/**
 * 2D bivector — a single coefficient on the {@code e12} basis blade.
 * Represents an oriented area; magnitude is the area, sign is the orientation.
 */
public final class Bivector2 {

    public float xy;

    public Bivector2() {}
    public Bivector2(float xy) { this.xy = xy; }

    public Bivector2 set(float xy)   { this.xy = xy; return this; }
    public Bivector2 set(Bivector2 o){ this.xy = o.xy; return this; }
    public Bivector2 zero()          { this.xy = 0; return this; }

    public Bivector2 add(Bivector2 o)                    { xy += o.xy; return this; }
    public Bivector2 add(Bivector2 o, Bivector2 dest)    { dest.xy = xy + o.xy; return dest; }
    public Bivector2 scale(float s)                      { xy *= s; return this; }
    public Bivector2 scale(float s, Bivector2 dest)      { dest.xy = xy * s; return dest; }
    public Bivector2 negate()                            { xy = -xy; return this; }
    public Bivector2 negate(Bivector2 dest)              { dest.xy = -xy; return dest; }

    public float magnitude() { return Math.abs(xy); }
}
