package lab.ui.palette;

import com.raylib.Canvas;
import com.raylib.Keys;
import com.raylib.MouseButtons;
import com.raylib.Window;

import java.util.List;

import lab.ui.AppContext;
import lab.ui.Theme;

/**
 * Palette overlay. Toggle with {@code Ctrl+Space}. Search rules per command-palette.md.
 *
 * <p>Interaction:
 * <ul>
 *   <li>Type to filter; Backspace deletes; Esc dismisses.</li>
 *   <li>Arrow keys move the selection; Enter invokes.</li>
 *   <li>Mouse hover highlights a row; click invokes immediately.</li>
 *   <li>Click outside the box closes without invoking.</li>
 * </ul>
 */
public final class Palette {

    private static final int MAX_VISIBLE        = 9;
    private static final int SEARCH_ROW_H       = 32;
    private static final int RESULT_TOP_PAD     = 4;
    private static final int RESULT_ROW_PITCH   = 28;
    private static final int RESULT_ROW_HEIGHT  = 26;
    private static final int RESULT_INSET_X     = 4;

    private final ActionRegistry registry;
    private boolean open;
    private final StringBuilder query = new StringBuilder();
    private int selected;
    private List<Action> results;

    public Palette(ActionRegistry registry) {
        this.registry = registry;
        this.results = registry.all();
    }

    public boolean isOpen() { return open; }

    public void toggle() {
        if (open) close(); else openFresh();
    }

    public void openFresh() {
        open = true;
        query.setLength(0);
        selected = 0;
        results = registry.all();
    }

    public void close() {
        open = false;
    }

    /** Run input handling. Returns true if the palette consumed input this frame. */
    public boolean update(AppContext ctx) {
        Window w = ctx.window;
        if (!open) return false;

        if (w.isKeyPressed(Keys.ESCAPE)) { close(); return true; }
        if (w.isKeyPressed(Keys.UP))     { selected = Math.max(0, selected - 1); }
        if (w.isKeyPressed(Keys.DOWN))   { selected = Math.min(Math.max(0, results.size() - 1), selected + 1); }

        boolean queryChanged = false;
        if (w.isKeyPressed(Keys.BACKSPACE) && query.length() > 0) {
            query.deleteCharAt(query.length() - 1);
            queryChanged = true;
        }
        // Drain any typed characters this frame.
        for (;;) {
            int cp = w.getCharPressed();
            if (cp == 0) break;
            if (cp >= 32 && cp < 127) {
                query.append((char) cp);
                queryChanged = true;
            }
        }
        if (queryChanged) {
            results = registry.search(query.toString());
            selected = 0;
        }

        if (w.isKeyPressed(Keys.ENTER) && !results.isEmpty()) {
            invoke(selected);
            return true;
        }

        // Mouse: click on a row invokes; click outside closes; hover highlights.
        int[] box = boxRect(ctx);
        if (w.isMouseButtonPressed(MouseButtons.LEFT)) {
            if (!inside(ctx.mouseX, ctx.mouseY, box)) {
                close();
                return true;
            }
            int hit = rowAtCursor(ctx, box);
            if (hit >= 0) {
                invoke(hit);
                return true;
            }
        }
        int hover = rowAtCursor(ctx, box);
        if (hover >= 0) selected = hover;

        return true;
    }

    public void render(AppContext ctx, Canvas c) {
        if (!open) return;
        // Backdrop dim — overlay, not opaque.
        c.fillRect(0, 0, ctx.window.width(), ctx.window.height(), Theme.OVERLAY_DIM);

        int[] r = boxRect(ctx);
        int bx = r[0], by = r[1], bw = r[2], bh = r[3];

        c.fillRect(bx, by, bw, bh, Theme.SURFACE);
        c.drawRect(bx, by, bw, bh, Theme.FRAME_HARD);

        // Search input row.
        c.drawLine(bx + 8, by + SEARCH_ROW_H, bx + bw - 8, by + SEARCH_ROW_H, Theme.FRAME);
        ctx.font.draw("> " + (query.length() == 0 ? "" : query.toString()) + caret(),
                bx + 12, by + 8, Theme.INK);

        // Result rows.
        int n = Math.min(results.size(), MAX_VISIBLE);
        for (int i = 0; i < n; i++) {
            Action a = results.get(i);
            int ry = rowTopY(by, i);
            if (i == selected) {
                c.fillRect(bx + RESULT_INSET_X, ry - 2, bw - RESULT_INSET_X * 2,
                           RESULT_ROW_HEIGHT, Theme.ACCENT_SOFT);
            }
            ctx.font.draw(a.label, bx + 16, ry, Theme.INK);

            String right = (a.shortcut == null ? "" : "[" + a.shortcut + "]   ") + tag(a.kind);
            int tw = ctx.fontSmall.measure(right);
            ctx.fontSmall.draw(right, bx + bw - 18 - tw, ry + 3, Theme.INK_DIM);
        }
        if (results.isEmpty()) {
            ctx.font.draw("(no matches)", bx + 16, by + SEARCH_ROW_H + 12, Theme.INK_DIM);
        }

        // Selected description footer.
        if (!results.isEmpty()) {
            Action a = results.get(Math.min(selected, results.size() - 1));
            int fy = by + bh - 24;
            c.drawLine(bx + 8, fy - 4, bx + bw - 8, fy - 4, Theme.FRAME);
            ctx.fontSmall.draw(a.description == null ? "" : a.description,
                    bx + 12, fy, Theme.INK_DIM);
        }
    }

    private boolean invoke(int idx) {
        if (results.isEmpty()) return false;
        Action a = results.get(Math.min(idx, results.size() - 1));
        close();
        try {
            a.invoke.run();
        } catch (RuntimeException ex) {
            // Stub: log and swallow. Real app would route via AppContext error sink.
            System.err.println("Action " + a.id + " failed: " + ex);
        }
        return true;
    }

    /** Index of the result row under the cursor, or -1 if none. */
    private int rowAtCursor(AppContext ctx, int[] box) {
        int bx = box[0], by = box[1], bw = box[2];
        int n = Math.min(results.size(), MAX_VISIBLE);
        for (int i = 0; i < n; i++) {
            int ry = rowTopY(by, i);
            if (ctx.mouseX >= bx + RESULT_INSET_X
                    && ctx.mouseX <= bx + bw - RESULT_INSET_X
                    && ctx.mouseY >= ry - 2
                    && ctx.mouseY <= ry - 2 + RESULT_ROW_HEIGHT) {
                return i;
            }
        }
        return -1;
    }

    private static int rowTopY(int boxY, int i) {
        return boxY + SEARCH_ROW_H + RESULT_TOP_PAD + i * RESULT_ROW_PITCH;
    }

    private static String tag(Action.Kind k) {
        return switch (k) {
            case ACTION   -> "action";
            case TOGGLE   -> "toggle";
            case NAVIGATE -> "navigate";
            case DOCS     -> "docs";
        };
    }

    private static String caret() {
        // Crude blink: ~1 Hz based on 60 fps frame counter would need state; cheap fallback.
        return "_";
    }

    private static int[] boxRect(AppContext ctx) {
        int margin = 80;
        int bw = Math.min(720, ctx.window.width() - margin * 2);
        int bh = 320;
        int bx = (ctx.window.width() - bw) / 2;
        int by = 110;
        return new int[] { bx, by, bw, bh };
    }

    private static boolean inside(int x, int y, int[] r) {
        return x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3];
    }
}
