package com.raylib;

import com.raylib.bindings.Raylib;

/**
 * raylib keyboard key codes. Mirrors the {@code KEY_*} constants from raylib.h.
 * Java {@code static final}s of the values returned by jextract's accessors — read once at
 * class-init, no per-call indirection.
 */
public final class Keys {

    private Keys() {}

    public static final int NULL          = Raylib.KEY_NULL();
    public static final int APOSTROPHE    = Raylib.KEY_APOSTROPHE();
    public static final int COMMA         = Raylib.KEY_COMMA();
    public static final int MINUS         = Raylib.KEY_MINUS();
    public static final int PERIOD        = Raylib.KEY_PERIOD();
    public static final int SLASH         = Raylib.KEY_SLASH();
    public static final int EQUAL         = Raylib.KEY_EQUAL();
    public static final int ZERO          = Raylib.KEY_ZERO();
    public static final int ONE           = Raylib.KEY_ONE();
    public static final int TWO           = Raylib.KEY_TWO();
    public static final int THREE         = Raylib.KEY_THREE();
    public static final int FOUR          = Raylib.KEY_FOUR();
    public static final int FIVE          = Raylib.KEY_FIVE();
    public static final int SIX           = Raylib.KEY_SIX();
    public static final int SEVEN         = Raylib.KEY_SEVEN();
    public static final int EIGHT         = Raylib.KEY_EIGHT();
    public static final int NINE          = Raylib.KEY_NINE();

    public static final int A = Raylib.KEY_A();
    public static final int B = Raylib.KEY_B();
    public static final int C = Raylib.KEY_C();
    public static final int D = Raylib.KEY_D();
    public static final int E = Raylib.KEY_E();
    public static final int F = Raylib.KEY_F();
    public static final int G = Raylib.KEY_G();
    public static final int H = Raylib.KEY_H();
    public static final int I = Raylib.KEY_I();
    public static final int J = Raylib.KEY_J();
    public static final int K = Raylib.KEY_K();
    public static final int L = Raylib.KEY_L();
    public static final int M = Raylib.KEY_M();
    public static final int N = Raylib.KEY_N();
    public static final int O = Raylib.KEY_O();
    public static final int P = Raylib.KEY_P();
    public static final int Q = Raylib.KEY_Q();
    public static final int R = Raylib.KEY_R();
    public static final int S = Raylib.KEY_S();
    public static final int T = Raylib.KEY_T();
    public static final int U = Raylib.KEY_U();
    public static final int V = Raylib.KEY_V();
    public static final int W = Raylib.KEY_W();
    public static final int X = Raylib.KEY_X();
    public static final int Y = Raylib.KEY_Y();
    public static final int Z = Raylib.KEY_Z();

    public static final int SPACE         = Raylib.KEY_SPACE();
    public static final int ESCAPE        = Raylib.KEY_ESCAPE();
    public static final int ENTER         = Raylib.KEY_ENTER();
    public static final int TAB           = Raylib.KEY_TAB();
    public static final int BACKSPACE     = Raylib.KEY_BACKSPACE();
    public static final int INSERT        = Raylib.KEY_INSERT();
    public static final int DELETE        = Raylib.KEY_DELETE();
    public static final int RIGHT         = Raylib.KEY_RIGHT();
    public static final int LEFT          = Raylib.KEY_LEFT();
    public static final int DOWN          = Raylib.KEY_DOWN();
    public static final int UP            = Raylib.KEY_UP();
    public static final int PAGE_UP       = Raylib.KEY_PAGE_UP();
    public static final int PAGE_DOWN     = Raylib.KEY_PAGE_DOWN();
    public static final int HOME          = Raylib.KEY_HOME();
    public static final int END           = Raylib.KEY_END();

    public static final int LEFT_SHIFT    = Raylib.KEY_LEFT_SHIFT();
    public static final int LEFT_CONTROL  = Raylib.KEY_LEFT_CONTROL();
    public static final int LEFT_ALT      = Raylib.KEY_LEFT_ALT();
    public static final int LEFT_SUPER    = Raylib.KEY_LEFT_SUPER();
    public static final int RIGHT_SHIFT   = Raylib.KEY_RIGHT_SHIFT();
    public static final int RIGHT_CONTROL = Raylib.KEY_RIGHT_CONTROL();
    public static final int RIGHT_ALT     = Raylib.KEY_RIGHT_ALT();
    public static final int RIGHT_SUPER   = Raylib.KEY_RIGHT_SUPER();

    public static final int F1  = Raylib.KEY_F1();
    public static final int F2  = Raylib.KEY_F2();
    public static final int F3  = Raylib.KEY_F3();
    public static final int F4  = Raylib.KEY_F4();
    public static final int F5  = Raylib.KEY_F5();
    public static final int F6  = Raylib.KEY_F6();
    public static final int F7  = Raylib.KEY_F7();
    public static final int F8  = Raylib.KEY_F8();
    public static final int F9  = Raylib.KEY_F9();
    public static final int F10 = Raylib.KEY_F10();
    public static final int F11 = Raylib.KEY_F11();
    public static final int F12 = Raylib.KEY_F12();
}
