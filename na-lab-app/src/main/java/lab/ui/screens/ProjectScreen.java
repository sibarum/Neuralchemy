package lab.ui.screens;

import lab.ui.RayGui;
import com.raylib.Canvas;

import lab.ui.AppContext;
import lab.ui.Screen;
import lab.ui.Theme;

/**
 * Stub project / welcome screen — landing page when no project is open. Lists recent projects
 * and exposes "New" / "Open" / "Recover from backup" entry points. See project-format.md.
 */
public final class ProjectScreen implements Screen {

    private static final String[][] RECENT = {
            { "split-quat parameter mixer", "~/projects/quat-mixer.nclab",   "today, 17:31" },
            { "xor warmup",                 "~/projects/xor-warmup.nclab",   "yesterday"    },
            { "spirals (overfit study)",    "~/scratch/spirals.nclab",       "3 days ago"   },
    };

    @Override public String id()    { return "screen.project"; }
    @Override public String title() { return "Project"; }

    @Override
    public void render(AppContext ctx, Canvas c) {
        int x = ctx.contentX, y = ctx.contentY, w = ctx.contentW, h = ctx.contentH;

        c.fillRect(x, y, w, h, Theme.SURFACE);
        c.drawRect(x, y, w, h, Theme.FRAME);

        ctx.font.draw("Neuralchemy Lab", x + 24, y + 24, Theme.INK);
        ctx.fontSmall.draw("press Ctrl+Space for the command palette",
                x + 24, y + 56, Theme.INK_DIM);

        // Action panel.
        int panelW = 260;
        int panelX = x + 24;
        int panelY = y + 96;
        c.fillRect(panelX, panelY, panelW, 180, Theme.PANEL);
        c.drawRect(panelX, panelY, panelW, 180, Theme.FRAME);
        ctx.font.draw("Start", panelX + 16, panelY + 12, Theme.INK);

        if (RayGui.button(panelX + 16, panelY + 40,  panelW - 32, 28, "New project…")) {}
        if (RayGui.button(panelX + 16, panelY + 76,  panelW - 32, 28, "Open .nclab…")) {}
        if (RayGui.button(panelX + 16, panelY + 112, panelW - 32, 28, "Recover from backup…")) {}
        if (RayGui.button(panelX + 16, panelY + 148, panelW - 32, 28, "Open parameter-mixer demo")) {
            ctx.navigateTo = "screen.parammixer";
        }

        // Recent panel.
        int rx = panelX + panelW + 24;
        int rw = w - (rx - x) - 24;
        c.fillRect(rx, panelY, rw, 240, Theme.PANEL);
        c.drawRect(rx, panelY, rw, 240, Theme.FRAME);
        ctx.font.draw("Recent", rx + 16, panelY + 12, Theme.INK);
        for (int i = 0; i < RECENT.length; i++) {
            int row = panelY + 44 + i * 56;
            c.drawLine(rx + 12, row + 48, rx + rw - 12, row + 48, Theme.FRAME);
            ctx.font.draw(RECENT[i][0],       rx + 16, row,       Theme.INK);
            ctx.fontSmall.draw(RECENT[i][1],  rx + 16, row + 22, Theme.INK_DIM);
            ctx.fontSmall.draw(RECENT[i][2],  rx + rw - 120, row + 22, Theme.INK_DIM);
        }

        // Tip strip at bottom.
        ctx.fontSmall.draw(
                "Tip: every menu item lives in the palette — there is no menu bar by design.",
                x + 24, y + h - 32, Theme.INK_DIM);
    }
}
