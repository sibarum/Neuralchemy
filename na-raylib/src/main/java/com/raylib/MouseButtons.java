package com.raylib;

import com.raylib.bindings.Raylib;

public final class MouseButtons {

    private MouseButtons() {}

    public static final int LEFT    = Raylib.MOUSE_BUTTON_LEFT();
    public static final int RIGHT   = Raylib.MOUSE_BUTTON_RIGHT();
    public static final int MIDDLE  = Raylib.MOUSE_BUTTON_MIDDLE();
    public static final int SIDE    = Raylib.MOUSE_BUTTON_SIDE();
    public static final int EXTRA   = Raylib.MOUSE_BUTTON_EXTRA();
    public static final int FORWARD = Raylib.MOUSE_BUTTON_FORWARD();
    public static final int BACK    = Raylib.MOUSE_BUTTON_BACK();
}
