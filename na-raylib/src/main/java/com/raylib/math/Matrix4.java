package com.raylib.math;

import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

/**
 * 4×4 column-major matrix matching raylib's {@code Matrix} struct.
 * Field {@code m0..m15} are stored in raylib's declaration order: m0..m3 form the first column
 * (basis vector for x), m4..m7 the second, m8..m11 the third, m12..m15 the translation column.
 */
public final class Matrix4 {

    public float m0, m1, m2, m3;
    public float m4, m5, m6, m7;
    public float m8, m9, m10, m11;
    public float m12, m13, m14, m15;

    public Matrix4() { identity(); }

    public Matrix4 identity() {
        m0  = 1; m1  = 0; m2  = 0; m3  = 0;
        m4  = 0; m5  = 1; m6  = 0; m7  = 0;
        m8  = 0; m9  = 0; m10 = 1; m11 = 0;
        m12 = 0; m13 = 0; m14 = 0; m15 = 1;
        return this;
    }

    public Matrix4 set(Matrix4 o) {
        m0  = o.m0;  m1  = o.m1;  m2  = o.m2;  m3  = o.m3;
        m4  = o.m4;  m5  = o.m5;  m6  = o.m6;  m7  = o.m7;
        m8  = o.m8;  m9  = o.m9;  m10 = o.m10; m11 = o.m11;
        m12 = o.m12; m13 = o.m13; m14 = o.m14; m15 = o.m15;
        return this;
    }

    public Matrix4 setTranslation(float tx, float ty, float tz) {
        m12 = tx; m13 = ty; m14 = tz; return this;
    }

    public Matrix4 writeTo(MemorySegment seg, long offset) {
        seg.set(JAVA_FLOAT, offset,        m0);
        seg.set(JAVA_FLOAT, offset +   4,  m1);
        seg.set(JAVA_FLOAT, offset +   8,  m2);
        seg.set(JAVA_FLOAT, offset +  12,  m3);
        seg.set(JAVA_FLOAT, offset +  16,  m4);
        seg.set(JAVA_FLOAT, offset +  20,  m5);
        seg.set(JAVA_FLOAT, offset +  24,  m6);
        seg.set(JAVA_FLOAT, offset +  28,  m7);
        seg.set(JAVA_FLOAT, offset +  32,  m8);
        seg.set(JAVA_FLOAT, offset +  36,  m9);
        seg.set(JAVA_FLOAT, offset +  40, m10);
        seg.set(JAVA_FLOAT, offset +  44, m11);
        seg.set(JAVA_FLOAT, offset +  48, m12);
        seg.set(JAVA_FLOAT, offset +  52, m13);
        seg.set(JAVA_FLOAT, offset +  56, m14);
        seg.set(JAVA_FLOAT, offset +  60, m15);
        return this;
    }

    public Matrix4 readFrom(MemorySegment seg, long offset) {
        m0  = seg.get(JAVA_FLOAT, offset);
        m1  = seg.get(JAVA_FLOAT, offset +   4);
        m2  = seg.get(JAVA_FLOAT, offset +   8);
        m3  = seg.get(JAVA_FLOAT, offset +  12);
        m4  = seg.get(JAVA_FLOAT, offset +  16);
        m5  = seg.get(JAVA_FLOAT, offset +  20);
        m6  = seg.get(JAVA_FLOAT, offset +  24);
        m7  = seg.get(JAVA_FLOAT, offset +  28);
        m8  = seg.get(JAVA_FLOAT, offset +  32);
        m9  = seg.get(JAVA_FLOAT, offset +  36);
        m10 = seg.get(JAVA_FLOAT, offset +  40);
        m11 = seg.get(JAVA_FLOAT, offset +  44);
        m12 = seg.get(JAVA_FLOAT, offset +  48);
        m13 = seg.get(JAVA_FLOAT, offset +  52);
        m14 = seg.get(JAVA_FLOAT, offset +  56);
        m15 = seg.get(JAVA_FLOAT, offset +  60);
        return this;
    }
}
