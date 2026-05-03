package com.raylib;

import com.raylib.bindings.Raylib;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pops a window for one frame to verify the full facade end-to-end. Disabled by default to keep
 * {@code mvn install} window-free; enable with {@code -Drun.window.tests=true}.
 */
@EnabledIfSystemProperty(named = "run.window.tests", matches = "true")
class WindowSmokeTest {

    @Test
    void openWindow_drawOneFrame_close() {
        // Suppress raylib's chatty stdout TraceLog spam during the smoke test.
        RaylibLoader.load();
        Raylib.SetTraceLogLevel(7); // LOG_NONE

        try (Window w = Window.create(320, 240, "na-raylib smoke")) {
            w.setTargetFPS(60);
            assertTrue(w.isReady(), "window should be ready");

            try (Canvas c = w.beginDraw()) {
                c.clearBackground(Color.RAYWHITE);
                c.fillCircle(160, 120, 40, Color.RED);
                c.drawText("hello", 10, 10, 20, Color.BLACK);
            }
        }
    }
}
