package com.raylib;

import com.raylib.runtime.WindowsBootstrap;

/**
 * End-to-end smoke for the FFM bindings — opens a small window, renders for a few seconds, exits.
 * Used as the {@code mainClass} for the {@code native} Maven profile so {@code native-image} has a
 * concrete entry point that exercises every layer (RaylibLoader → SymbolLookup → downcall).
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        WindowsBootstrap.init("na-raylib");
        int frames = args.length > 0 ? Integer.parseInt(args[0]) : 180;

        try (Window w = Window.create(800, 450, "na-raylib smoke")) {
            w.setTargetFPS(60);
            int rendered = 0;
            while (rendered < frames && !w.shouldClose()) {
                try (Canvas c = w.beginDraw()) {
                    c.clearBackground(Color.RAYWHITE);
                    c.drawText("na-raylib running on " + runtimeLabel(), 20, 20, 24, Color.DARKGRAY);
                    c.drawText("frame " + rendered + " / " + frames, 20, 60, 20, Color.GRAY);
                    c.fillCircle(400, 260, 60, Color.SKYBLUE);
                    c.drawCircle(400, 260, 60, Color.DARKBLUE);
                }
                rendered++;
            }
            System.out.println("rendered " + rendered + " frames");
        }
    }

    private static String runtimeLabel() {
        String img = System.getProperty("org.graalvm.nativeimage.kind");
        return img != null ? "native-image (" + img + ")" : "JVM " + System.getProperty("java.version");
    }
}
