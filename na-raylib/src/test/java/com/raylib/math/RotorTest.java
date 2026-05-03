package com.raylib.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotorTest {

    private static final float EPS = 1e-5f;

    private static void assertVec(float ex, float ey, float ez, Vector3 v) {
        assertEquals(ex, v.x, EPS, "x");
        assertEquals(ey, v.y, EPS, "y");
        assertEquals(ez, v.z, EPS, "z");
    }

    @Test
    void rotor3_around_z_sends_e1_to_e2() {
        Rotor3 r = new Rotor3().fromAxisAngle(0, 0, 1, (float) (Math.PI / 2));
        Vector3 v = new Vector3(1, 0, 0);
        r.apply(v, v);
        assertVec(0, 1, 0, v);
    }

    @Test
    void rotor3_around_x_sends_e2_to_e3() {
        Rotor3 r = new Rotor3().fromAxisAngle(1, 0, 0, (float) (Math.PI / 2));
        Vector3 v = new Vector3(0, 1, 0);
        r.apply(v, v);
        assertVec(0, 0, 1, v);
    }

    @Test
    void rotor3_around_y_sends_e1_to_neg_e3() {
        Rotor3 r = new Rotor3().fromAxisAngle(0, 1, 0, (float) (Math.PI / 2));
        Vector3 v = new Vector3(1, 0, 0);
        r.apply(v, v);
        assertVec(0, 0, -1, v);
    }

    @Test
    void composing_z90_then_x90_cycles_axes() {
        Rotor3 rz = new Rotor3().fromAxisAngle(0, 0, 1, (float) (Math.PI / 2));
        Rotor3 rx = new Rotor3().fromAxisAngle(1, 0, 0, (float) (Math.PI / 2));
        Rotor3 r = new Rotor3();
        rz.mul(rx, r);                  // first rx, then rz

        Vector3 e1 = new Vector3(1, 0, 0);
        Vector3 e2 = new Vector3(0, 1, 0);
        Vector3 e3 = new Vector3(0, 0, 1);

        r.apply(e1, e1); assertVec(0, 1, 0, e1);
        r.apply(e2, e2); assertVec(0, 0, 1, e2);
        r.apply(e3, e3); assertVec(1, 0, 0, e3);
    }

    @Test
    void rotor3_from_bivector_matches_axis_angle() {
        // Bivector (xy, yz, zx) of magnitude θ around the dual axis should equal axis-angle.
        // dual(z-axis) = e12 → bivector with xy = θ.
        float angle = 1.234f;
        Rotor3 fromBiv = new Rotor3().fromBivector(new Bivector3(angle, 0, 0));
        Rotor3 fromAxA = new Rotor3().fromAxisAngle(0, 0, 1, angle);

        assertEquals(fromAxA.s,  fromBiv.s,  EPS);
        assertEquals(fromAxA.xy, fromBiv.xy, EPS);
        assertEquals(fromAxA.yz, fromBiv.yz, EPS);
        assertEquals(fromAxA.zx, fromBiv.zx, EPS);
    }

    @Test
    void rotor3_inverse_round_trip_is_identity() {
        Rotor3 r = new Rotor3().fromAxisAngle(0.4f, 0.7f, -0.2f, 1.1f);
        Vector3 v = new Vector3(1, 2, 3);
        Vector3 rotated = new Vector3();
        r.apply(v, rotated);
        Rotor3 inv = new Rotor3();
        r.reverse(inv);
        Vector3 back = new Vector3();
        inv.apply(rotated, back);
        assertVec(1, 2, 3, back);
    }

    @Test
    void rotor2_quarter_turn_sends_e1_to_e2() {
        Rotor2 r = new Rotor2().fromAngle((float) (Math.PI / 2));
        Vector2 v = new Vector2(1, 0);
        r.apply(v, v);
        assertEquals(0, v.x, EPS);
        assertEquals(1, v.y, EPS);
    }
}
