package com.raylib.math;

/**
 * 3D rotor: {@code R = s + xy·e12 + yz·e23 + zx·e31}. Represents a 3D rotation.
 * Isomorphic to a unit quaternion. Bivector basis is cyclic ({@code xy, yz, zx}).
 *
 * <p>Sign convention: {@code R = exp(-B·θ/2) = cos(θ/2) - sin(θ/2)·B̂} for unit bivector {@code B̂},
 * giving a right-handed rotation under the sandwich {@code R · v · ~R}.
 */
public final class Rotor3 {

    public float s, xy, yz, zx;

    public Rotor3() { s = 1; xy = 0; yz = 0; zx = 0; }
    public Rotor3(float s, float xy, float yz, float zx) {
        this.s = s; this.xy = xy; this.yz = yz; this.zx = zx;
    }

    public Rotor3 set(float s, float xy, float yz, float zx) {
        this.s = s; this.xy = xy; this.yz = yz; this.zx = zx; return this;
    }
    public Rotor3 set(Rotor3 o) { return set(o.s, o.xy, o.yz, o.zx); }
    public Rotor3 identity()    { s = 1; xy = 0; yz = 0; zx = 0; return this; }

    /**
     * Rotor for a rotation by {@code angle} (radians) around the given axis (need not be unit).
     */
    public Rotor3 fromAxisAngle(float ax, float ay, float az, float angle) {
        float lenSq = ax * ax + ay * ay + az * az;
        if (lenSq < 1e-24f) { return identity(); }
        float invLen = 1f / (float) Math.sqrt(lenSq);
        float h = angle * 0.5f;
        float c = (float) Math.cos(h);
        float ms = -(float) Math.sin(h) * invLen;       // R = exp(-B·θ/2): the leading minus
        s  = c;
        yz = ms * ax;     // dual(e1) = e23
        zx = ms * ay;     // dual(e2) = e31
        xy = ms * az;     // dual(e3) = e12
        return this;
    }
    public Rotor3 fromAxisAngle(Vector3 axis, float angle) {
        return fromAxisAngle(axis.x, axis.y, axis.z, angle);
    }

    /**
     * Rotor whose axis-of-rotation is the bivector's plane and whose angle is its magnitude.
     * Equivalent to {@code exp(-b/2)}.
     */
    public Rotor3 fromBivector(Bivector3 b) {
        float magSq = b.magnitudeSq();
        if (magSq < 1e-24f) { return identity(); }
        float mag = (float) Math.sqrt(magSq);
        float h = mag * 0.5f;
        float c = (float) Math.cos(h);
        float ms = -(float) Math.sin(h) / mag;
        s  = c;
        xy = ms * b.xy;
        yz = ms * b.yz;
        zx = ms * b.zx;
        return this;
    }

    /** Geometric product: composes rotations (apply {@code o} first, then {@code this}). */
    public Rotor3 mul(Rotor3 o, Rotor3 dest) {
        float a = s, b = xy, c = yz, d = zx;
        float e = o.s, f = o.xy, g = o.yz, h = o.zx;
        float ns  = a * e - b * f - c * g - d * h;
        float nxy = a * f + e * b + d * g - c * h;
        float nyz = a * g + e * c + b * h - d * f;
        float nzx = a * h + e * d + c * f - b * g;
        dest.s = ns; dest.xy = nxy; dest.yz = nyz; dest.zx = nzx;
        return dest;
    }

    public Rotor3 reverse()              { return reverse(this); }
    public Rotor3 reverse(Rotor3 dest)   {
        dest.s = s; dest.xy = -xy; dest.yz = -yz; dest.zx = -zx; return dest;
    }

    public float normSq() { return s * s + xy * xy + yz * yz + zx * zx; }

    public Rotor3 normalize()             { return normalize(this); }
    public Rotor3 normalize(Rotor3 dest)  {
        float n = (float) Math.sqrt(normSq());
        if (n < 1e-12f) { dest.s = 1; dest.xy = 0; dest.yz = 0; dest.zx = 0; }
        else            { float k = 1f / n; dest.s = s*k; dest.xy = xy*k; dest.yz = yz*k; dest.zx = zx*k; }
        return dest;
    }

    /** Rotate {@code v} by this rotor: sandwich product {@code R · v · ~R}. */
    public Vector3 apply(Vector3 v, Vector3 dest) {
        float vx = v.x, vy = v.y, vz = v.z;
        float xyxy = xy * xy, yzyz = yz * yz, zxzx = zx * zx;
        float sxy = s * xy, syz = s * yz, szx = s * zx;
        float xyyz = xy * yz, yzzx = yz * zx, zxxy = zx * xy;

        float nx = (1f - 2f * (zxzx + xyxy)) * vx + 2f * (yzzx + sxy) * vy + 2f * (xyyz - szx) * vz;
        float ny = 2f * (yzzx - sxy) * vx + (1f - 2f * (yzyz + xyxy)) * vy + 2f * (zxxy + syz) * vz;
        float nz = 2f * (xyyz + szx) * vx + 2f * (zxxy - syz) * vy + (1f - 2f * (yzyz + zxzx)) * vz;

        dest.x = nx; dest.y = ny; dest.z = nz;
        return dest;
    }

    /** Spherical linear interpolation between unit rotors. */
    public Rotor3 slerp(Rotor3 to, float t, Rotor3 dest) {
        float cosTheta = s * to.s + xy * to.xy + yz * to.yz + zx * to.zx;
        float toS = to.s, toXy = to.xy, toYz = to.yz, toZx = to.zx;
        if (cosTheta < 0f) {
            cosTheta = -cosTheta; toS = -toS; toXy = -toXy; toYz = -toYz; toZx = -toZx;
        }
        float w0, w1;
        if (cosTheta > 0.9995f) {
            w0 = 1f - t; w1 = t;
        } else {
            float theta = (float) Math.acos(cosTheta);
            float sinTheta = (float) Math.sin(theta);
            w0 = (float) Math.sin((1f - t) * theta) / sinTheta;
            w1 = (float) Math.sin(t * theta) / sinTheta;
        }
        dest.s  = w0 * s  + w1 * toS;
        dest.xy = w0 * xy + w1 * toXy;
        dest.yz = w0 * yz + w1 * toYz;
        dest.zx = w0 * zx + w1 * toZx;
        return dest;
    }

    /** Write into a 4×4 column-major matrix laid out as raylib's {@code Matrix} struct. */
    public Matrix4 toMatrix4(Matrix4 dest) {
        float xyxy = xy * xy, yzyz = yz * yz, zxzx = zx * zx;
        float sxy = s * xy, syz = s * yz, szx = s * zx;
        float xyyz = xy * yz, yzzx = yz * zx, zxxy = zx * xy;

        dest.m0  = 1f - 2f * (zxzx + xyxy);
        dest.m1  = 2f * (yzzx - sxy);
        dest.m2  = 2f * (xyyz + szx);
        dest.m3  = 0f;

        dest.m4  = 2f * (yzzx + sxy);
        dest.m5  = 1f - 2f * (yzyz + xyxy);
        dest.m6  = 2f * (zxxy - syz);
        dest.m7  = 0f;

        dest.m8  = 2f * (xyyz - szx);
        dest.m9  = 2f * (zxxy + syz);
        dest.m10 = 1f - 2f * (yzyz + zxzx);
        dest.m11 = 0f;

        dest.m12 = 0f; dest.m13 = 0f; dest.m14 = 0f; dest.m15 = 1f;
        return dest;
    }

    /** Convert to raylib-compatible quaternion. Bivector → quaternion mapping flips signs. */
    public Quaternion toQuaternion(Quaternion dest) {
        dest.w = s;
        dest.x = -yz;
        dest.y = -zx;
        dest.z = -xy;
        return dest;
    }

    public Rotor3 fromQuaternion(Quaternion q) {
        s  =  q.w;
        yz = -q.x;
        zx = -q.y;
        xy = -q.z;
        return this;
    }
}
