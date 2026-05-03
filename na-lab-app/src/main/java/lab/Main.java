package lab;

import com.raylib.Window;
import com.raylib.runtime.WindowsBootstrap;

import lab.ui.App;
import lab.ui.Theme;
import lab.viz.AppFont;

/**
 * Neuralchemy Lab entry point. Launches the shell (chrome + screens + command palette).
 *
 * <p>The original 2D parameter-mixing demo is now hosted as a screen — open it from the
 * command palette ({@code Ctrl+Space}, search "parameter mixer") or with {@code Ctrl+0}.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        WindowsBootstrap.init("na-lab-app");

        int winW = 1280, winH = 820;
        try (Window w = Window.create(winW, winH, "Neuralchemy Lab", /*resizable=*/true)) {
            w.setTargetFPS(60);
            w.setExitKey(0);     // disable raylib's default Escape-closes-window — Escape is reserved for the palette / modals.
            // Proportional fonts share one SDF atlas; the monospace font has its own. Sizes are
            // render-time only — no atlas reload when UI scale changes.
            AppFont font      = AppFont.load(Theme.BASE_FONT_PX);
            AppFont fontSmall = AppFont.load(Theme.BASE_FONT_SMALL_PX);
            AppFont fontMono  = AppFont.loadMono(Theme.BASE_FONT_PX);

            App app = new App(w, font, fontSmall, fontMono);
            app.run();
        }
    }
}
