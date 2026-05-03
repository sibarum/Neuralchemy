package com.raylib;

import com.raylib.bindings.Raylib;
import org.junit.jupiter.api.Test;

class RaylibSmokeTest {

    @Test
    void canLoadAndCallNoInitFunction() {
        RaylibLoader.load();
        // SetTraceLogLevel only writes to a global; safe to call without InitWindow.
        // If the DLL didn't load, this throws ExceptionInInitializerError on first touch of Raylib.
        Raylib.SetTraceLogLevel(7); // LOG_NONE
    }
}
