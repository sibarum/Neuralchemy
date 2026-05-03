package lab.ui.screens;

import com.raylib.Canvas;
import com.raylib.Color;
import com.raylib.Keys;
import com.raylib.MouseButtons;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lab.dsl.ast.Artifact;
import lab.dsl.ast.TypedName;
import lab.dsl.check.Diagnostic;
import lab.ui.AppContext;
import lab.ui.RayGui;
import lab.ui.Screen;
import lab.ui.Theme;
import lab.ui.editor.TextEditor;
import lab.ui.workspace.DslFile;
import lab.ui.workspace.Workspace;

/**
 * Editable DSL viewer + parser-error display. Each artifact in the workspace gets a
 * persistent {@link TextEditor} session keyed by file index — switching files preserves
 * cursor position. On every revision change the editor's source is pushed back through
 * {@link Workspace#editFile}, which re-parses and re-syncs the {@code KindRegistry} so
 * palette + network editor pick up changes immediately.
 *
 * <p>Editing is intentionally minimal: insert, backspace, delete, newline, tab, arrows,
 * home/end, click-to-place. No selection, no undo, no scrolling — those land when the demo
 * needs them.
 */
public final class DslEditorScreen implements Screen {

    private static final int CURSOR_BLINK_HZ = 2;     // 2 toggles per second

    private int selected;

    /** Programmatic file selection — used by "New artifact" palette actions to focus the new file. */
    public void selectFile(int index) {
        if (index >= 0) selected = index;
    }

    /** One TextEditor per file index — preserves cursor on screen-switch and file-switch. */
    private final Map<Integer, TextEditor> editors = new HashMap<>();
    /** Tracks each editor's last-flushed revision so we know when to re-parse. */
    private final Map<Integer, Long> flushedRevisions = new HashMap<>();

    private float blinkTimer;

    @Override public String id()    { return "screen.dsl"; }
    @Override public String title() { return "DSL Editor"; }

    @Override
    public void render(AppContext ctx, Canvas c) {
        List<DslFile> files = ctx.workspace.files();
        if (files.isEmpty()) {
            ctx.font.draw("Workspace empty.", ctx.contentX + 12, ctx.contentY + 12, Theme.INK_DIM);
            return;
        }
        if (selected >= files.size()) selected = 0;
        DslFile file = files.get(selected);
        TextEditor editor = editorFor(selected, file);

        int x = ctx.contentX, y = ctx.contentY, w = ctx.contentW, h = ctx.contentH;
        int gap        = 8;
        int sidebarW   = 240;
        int inspectorW = 320;
        int codeX = x + sidebarW + gap;
        int codeW = w - sidebarW - inspectorW - gap * 2;

        renderSidebar(ctx, c, files, x, y, sidebarW, h);
        renderCodeArea(ctx, c, file, editor, codeX, y, codeW, h);
        renderInspector(ctx, c, file, x + w - inspectorW, y, inspectorW, h);

        // Keyboard input drives the active editor when the palette is closed.
        if (!ctx.paletteOpen) handleEditorInput(ctx, editor);

        // Flush back to the file if anything changed this frame.
        if (editor.revision() != flushedRevisions.getOrDefault(selected, -1L)) {
            ctx.workspace.editFile(file, editor.text());
            flushedRevisions.put(selected, editor.revision());
        }

        blinkTimer += ctx.window.frameTime();
    }

    private TextEditor editorFor(int idx, DslFile file) {
        TextEditor e = editors.get(idx);
        if (e == null) {
            e = new TextEditor(file.source());
            editors.put(idx, e);
            flushedRevisions.put(idx, e.revision());
        }
        return e;
    }

    // ─── Sidebar ────────────────────────────────────────────────────────────

    private void renderSidebar(AppContext ctx, Canvas c, List<DslFile> files,
                               int x, int y, int w, int h) {
        c.fillRect(x, y, w, h, Theme.PANEL);
        c.drawRect(x, y, w, h, Theme.FRAME);

        int rowH = ctx.font.size + 6;
        ctx.font.draw("Workspace", x + 12, y + 8, Theme.INK);
        int row = y + 8 + rowH + 4;

        String prevCategory = null;
        for (int i = 0; i < files.size(); i++) {
            DslFile f = files.get(i);
            if (!f.category.equals(prevCategory)) {
                if (prevCategory != null) row += 4;
                ctx.fontSmall.draw(f.category + "/", x + 12, row, Theme.INK_DIM);
                row += ctx.fontSmall.size + 6;
                prevCategory = f.category;
            }
            renderSidebarRow(ctx, c, f, i, x + 12, row, w - 24);
            row += rowH;
        }

        if (RayGui.button(x + 12, y + h - rowH - 12, w - 24, rowH, "+ New neuron")) {
            ctx.workspace.addFile("neurons", "untitled",
                    "neuron untitled {\n  in:  x\n  out: y\n\n  y = x\n}\n");
            selected = files.size() - 1;     // workspace.files() returns the same list; new file is last
        }
    }

    private void renderSidebarRow(AppContext ctx, Canvas c, DslFile f, int idx,
                                  int rx, int ry, int rw) {
        int rowH = ctx.font.size + 4;
        boolean sel = idx == selected;
        if (sel) {
            c.fillRect(rx - 4, ry - 2, rw + 8, rowH, Theme.ACCENT_SOFT);
        }
        if (!ctx.paletteOpen
                && ctx.window.isMouseButtonPressed(MouseButtons.LEFT)
                && ctx.mouseX >= rx - 4 && ctx.mouseX <= rx - 4 + rw + 8
                && ctx.mouseY >= ry - 2 && ctx.mouseY <= ry - 2 + rowH) {
            selected = idx;
        }
        ctx.font.draw("  " + f.filename, rx, ry, Theme.INK);

        Color badge = null;
        if (f.errorCount() > 0)      badge = Theme.ERROR_RED;
        else if (f.warnCount() > 0)  badge = Theme.WARN;
        if (badge != null) {
            c.fillCircle(rx + rw - 8, ry + rowH / 2 - 1, 4, badge);
        }
    }

    // ─── Code area ──────────────────────────────────────────────────────────

    private void renderCodeArea(AppContext ctx, Canvas c, DslFile file, TextEditor editor,
                                int x, int y, int w, int h) {
        c.fillRect(x, y, w, h, Theme.SURFACE);
        c.drawRect(x, y, w, h, Theme.FRAME);

        int headerH = ctx.font.size + 14;
        ctx.fontSmall.draw(file.path(), x + 12, y + 8, Theme.INK_DIM);
        String status = statusString(file);
        Color statusColor = file.errorCount() > 0 ? Theme.ERROR_RED
                          : file.warnCount() > 0  ? Theme.WARN
                          : Theme.OK;
        int sw = ctx.fontSmall.measure(status);
        ctx.fontSmall.draw(status, x + w - sw - 12, y + 8, statusColor);
        c.drawLine(x, y + headerH, x + w, y + headerH, Theme.FRAME);

        int diagH = Math.min(160, Math.max(60, ctx.fontMono.size * 5 + 30));
        int diagY = y + h - diagH;
        c.drawLine(x, diagY, x + w, diagY, Theme.FRAME);

        renderSource(ctx, c, file, editor, x, y + headerH, w, diagY - (y + headerH));
        renderDiagnostics(ctx, c, file, x, diagY, w, diagH);
    }

    private void renderSource(AppContext ctx, Canvas c, DslFile file, TextEditor editor,
                              int x, int y, int w, int h) {
        int lineH = ctx.fontMono.size + 4;
        int gutterW = ctx.fontMono.measureAt(maxLineNumberStr(editor), ctx.fontMono.size) + 28;
        int sx = x + gutterW + 4;
        int top = y + 8;

        int[] severityByLine = perLineSeverity(file);

        // Click-to-place cursor — translate (mouseX, mouseY) to (row, col).
        if (!ctx.paletteOpen
                && ctx.window.isMouseButtonPressed(MouseButtons.LEFT)
                && ctx.mouseX >= sx && ctx.mouseX < x + w
                && ctx.mouseY >= top && ctx.mouseY < y + h) {
            int clickedRow = Math.max(0, Math.min(editor.lineCount() - 1,
                                                  (ctx.mouseY - top) / lineH));
            String lineStr = editor.line(clickedRow);
            int clickedCol = colAtPixel(ctx, lineStr, ctx.mouseX - sx);
            editor.setCursor(clickedRow, clickedCol);
        }

        // Active-line highlight.
        int curRow = editor.cursorRow();
        int curLineY = top + curRow * lineH;
        if (curLineY <= y + h - lineH) {
            c.fillRect(x + gutterW, curLineY - 2, w - gutterW - 1, lineH, Theme.LINE_HIGHLIGHT);
        }

        for (int i = 0; i < editor.lineCount(); i++) {
            int lineY = top + i * lineH;
            if (lineY > y + h - lineH) break;
            int lineNum = i + 1;

            // Diagnostic highlighting.
            if (severityByLine[lineNum] == 1 || severityByLine[lineNum] == 2) {
                Color fill = severityByLine[lineNum] == 2
                        ? new Color(252, 232, 232, 200)
                        : new Color(252, 244, 220, 200);
                c.fillRect(x + gutterW, lineY - 2, w - gutterW - 1, lineH, fill);
            }

            String numStr = String.valueOf(lineNum);
            int numW = ctx.fontMono.measure(numStr);
            ctx.fontMono.draw(numStr, x + gutterW - numW - 16, lineY, Theme.INK_DIM);
            if (severityByLine[lineNum] == 2) {
                c.fillCircle(x + gutterW - 8, lineY + lineH / 2 - 1, 3, Theme.ERROR_RED);
            } else if (severityByLine[lineNum] == 1) {
                c.fillCircle(x + gutterW - 8, lineY + lineH / 2 - 1, 3, Theme.WARN);
            }

            ctx.fontMono.draw(editor.line(i), sx, lineY, Theme.INK);
        }

        // Cursor — vertical bar at column position. Blinks at CURSOR_BLINK_HZ.
        if (((int) (blinkTimer * CURSOR_BLINK_HZ)) % 2 == 0) {
            int cx = sx + ctx.fontMono.measureAt(
                    editor.line(curRow).substring(0, editor.cursorCol()), ctx.fontMono.size);
            c.fillRect(cx, curLineY - 1, 2, lineH, Theme.INK);
        }
    }

    /** Find the column whose left edge is closest to {@code pixelX} relative to line start. */
    private int colAtPixel(AppContext ctx, String line, int pixelX) {
        if (pixelX <= 0) return 0;
        // Simple scan — line lengths in our DSL are short.
        for (int i = 1; i <= line.length(); i++) {
            int wPx = ctx.fontMono.measureAt(line.substring(0, i), ctx.fontMono.size);
            if (wPx > pixelX) {
                int prev = ctx.fontMono.measureAt(line.substring(0, i - 1), ctx.fontMono.size);
                return (pixelX - prev) < (wPx - pixelX) ? i - 1 : i;
            }
        }
        return line.length();
    }

    private void renderDiagnostics(AppContext ctx, Canvas c, DslFile file, int x, int y, int w, int h) {
        c.fillRect(x + 1, y + 1, w - 2, h - 2, Theme.PANEL);
        String header = String.format("Diagnostics — %d error%s, %d warning%s",
                file.errorCount(), file.errorCount() == 1 ? "" : "s",
                file.warnCount(),  file.warnCount()  == 1 ? "" : "s");
        ctx.fontSmall.draw(header, x + 12, y + 6, Theme.INK_DIM);

        int top = y + 6 + ctx.fontSmall.size + 6;
        if (file.diagnostics().isEmpty()) {
            ctx.fontMono.draw("(none — clean parse)", x + 12, top, Theme.OK);
            return;
        }

        int rowH = ctx.fontMono.size + 4;
        int maxRows = Math.max(1, (y + h - top) / rowH);
        int n = Math.min(maxRows, file.diagnostics().size());
        for (int i = 0; i < n; i++) {
            Diagnostic d = file.diagnostics().get(i);
            int rowY = top + i * rowH;
            String tag = d.severity().name();
            Color tagColor = switch (d.severity()) {
                case ERROR -> Theme.ERROR_RED;
                case WARN  -> Theme.WARN;
                case INFO  -> Theme.INK_DIM;
            };
            ctx.fontMono.draw(tag, x + 12, rowY, tagColor);
            String pos = String.format("%2d:%-2d", d.span().line(), d.span().col());
            ctx.fontMono.draw(pos, x + 12 + 60, rowY, Theme.INK_DIM);
            ctx.fontMono.draw(d.message(), x + 12 + 130, rowY, Theme.INK);
        }
        if (file.diagnostics().size() > n) {
            ctx.fontSmall.draw("… " + (file.diagnostics().size() - n) + " more",
                    x + 12, top + n * rowH, Theme.INK_DIM);
        }
    }

    // ─── Inspector ──────────────────────────────────────────────────────────

    private void renderInspector(AppContext ctx, Canvas c, DslFile file, int x, int y, int w, int h) {
        c.fillRect(x, y, w, h, Theme.PANEL);
        c.drawRect(x, y, w, h, Theme.FRAME);
        ctx.font.draw("Inspector", x + 12, y + 8, Theme.INK);

        int rowH = ctx.fontSmall.size + 4;
        int row = y + 8 + ctx.font.size + 8;

        if (file.artifact() == null) {
            ctx.fontSmall.draw("(parse failed — see diagnostics)", x + 12, row, Theme.INK_DIM);
            return;
        }

        Artifact a = file.artifact();
        ctx.fontSmall.draw("kind:  " + kindOf(a),    x + 12, row, Theme.INK_DIM); row += rowH;
        ctx.fontSmall.draw("name:  " + a.name(),     x + 12, row, Theme.INK);     row += rowH + 4;

        switch (a) {
            case Artifact.Neuron n -> {
                row = renderTypedSection(ctx, "in:",    n.inputs(),  x + 12, row, rowH);
                row = renderTypedSection(ctx, "param:", n.params(),  x + 12, row, rowH);
                row = renderTypedSection(ctx, "out:",   n.outputs(), x + 12, row, rowH);
                row += 4;
                ctx.fontSmall.draw("body: " + n.body().size() + " statement"
                        + (n.body().size() == 1 ? "" : "s"), x + 12, row, Theme.INK_DIM);
            }
            case Artifact.Activation v -> {
                row = renderTypedSection(ctx, "args:", v.args(), x + 12, row, rowH);
            }
            case Artifact.Loss l -> {
                row = renderTypedSection(ctx, "args (pred, target):", l.args(), x + 12, row, rowH);
            }
        }
    }

    private int renderTypedSection(AppContext ctx, String label, List<TypedName> names,
                                   int x, int y, int rowH) {
        ctx.fontSmall.draw(label, x, y, Theme.INK_DIM);
        y += rowH;
        for (TypedName tn : names) {
            ctx.fontMono.draw("  " + tn.name() + ": " + tn.type().sourceName(),
                    x, y, Theme.INK);
            y += rowH;
        }
        return y + 4;
    }

    // ─── Keyboard input ─────────────────────────────────────────────────────

    private void handleEditorInput(AppContext ctx, TextEditor editor) {
        var w = ctx.window;
        boolean ctrl = w.isKeyDown(Keys.LEFT_CONTROL) || w.isKeyDown(Keys.RIGHT_CONTROL);

        // Drain typed characters first. Skip while ctrl is held (those are app shortcuts).
        if (!ctrl) {
            for (;;) {
                int cp = w.getCharPressed();
                if (cp == 0) break;
                if (cp >= 32 && cp < 127) {
                    editor.insertChar((char) cp);
                    blinkTimer = 0f;       // keep cursor visible while typing
                }
            }
        }

        // Repeating keys via raylib's IsKeyPressedRepeat would be ideal, but the binding lacks
        // it; isKeyPressed gives us one event per press which is fine for the v0 editor.
        if (w.isKeyPressed(Keys.ENTER))     { editor.splitLine();    blinkTimer = 0f; }
        if (w.isKeyPressed(Keys.BACKSPACE)) { editor.backspace();    blinkTimer = 0f; }
        if (w.isKeyPressed(Keys.DELETE))    { editor.deleteForward(); blinkTimer = 0f; }
        if (w.isKeyPressed(Keys.TAB))       { editor.insertString("  "); blinkTimer = 0f; }
        if (w.isKeyPressed(Keys.LEFT))      { editor.moveLeft();  blinkTimer = 0f; }
        if (w.isKeyPressed(Keys.RIGHT))     { editor.moveRight(); blinkTimer = 0f; }
        if (w.isKeyPressed(Keys.UP))        { editor.moveUp();    blinkTimer = 0f; }
        if (w.isKeyPressed(Keys.DOWN))      { editor.moveDown();  blinkTimer = 0f; }
        if (w.isKeyPressed(Keys.HOME))      { editor.moveHome();  blinkTimer = 0f; }
        if (w.isKeyPressed(Keys.END))       { editor.moveEnd();   blinkTimer = 0f; }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private static String kindOf(Artifact a) {
        return switch (a) {
            case Artifact.Neuron n     -> "neuron";
            case Artifact.Activation v -> "activation";
            case Artifact.Loss l       -> "loss";
        };
    }

    private static String statusString(DslFile f) {
        if (f.errorCount() > 0) return f.errorCount() + " error" + (f.errorCount() == 1 ? "" : "s");
        if (f.warnCount() > 0)  return f.warnCount()  + " warning" + (f.warnCount() == 1 ? "" : "s");
        return "OK";
    }

    private static String maxLineNumberStr(TextEditor e) {
        return String.valueOf(Math.max(e.lineCount(), 1));
    }

    private static int[] perLineSeverity(DslFile f) {
        int[] sev = new int[Math.max(f.lines().size(), 0) + 2];
        for (Diagnostic d : f.diagnostics()) {
            int line = d.span().line();
            if (line < 1 || line >= sev.length) continue;
            int rank = d.isError() ? 2 : (d.severity() == Diagnostic.Severity.WARN ? 1 : 0);
            if (rank > sev[line]) sev[line] = rank;
        }
        return sev;
    }
}
