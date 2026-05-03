package lab.ui.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny single-buffer text editor backing the DSL editor screen. Holds an array of mutable
 * {@code StringBuilder} lines plus a cursor position; supports the basic keystroke edits
 * (insert, backspace, delete, newline) and arrow-key navigation. No selection, no undo, no
 * rendering — those are the screen's job.
 *
 * <p>Designed to be re-built from source via {@link #setText} when the workspace swaps the
 * underlying file (e.g. the user clicks a different artifact in the sidebar). The
 * {@link #revision} counter ticks on every mutation so the host can debounce or batch
 * downstream work like re-parsing.
 */
public final class TextEditor {

    private final List<StringBuilder> lines = new ArrayList<>();
    private int row, col;
    private long revision;

    public TextEditor() { setText(""); }
    public TextEditor(String initial) { setText(initial); }

    public void setText(String source) {
        lines.clear();
        if (source == null || source.isEmpty()) {
            lines.add(new StringBuilder());
        } else {
            for (String line : source.split("\n", -1)) lines.add(new StringBuilder(line));
        }
        row = 0;
        col = 0;
        revision++;
    }

    public String text() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(lines.get(i));
        }
        return out.toString();
    }

    public List<StringBuilder> lines() { return lines; }
    public int    lineCount()          { return lines.size(); }
    public String line(int i)          { return lines.get(i).toString(); }
    public int    cursorRow()          { return row; }
    public int    cursorCol()          { return col; }
    public long   revision()           { return revision; }

    /** Set cursor with clamping to valid (row, col). */
    public void setCursor(int newRow, int newCol) {
        row = Math.max(0, Math.min(lines.size() - 1, newRow));
        col = Math.max(0, Math.min(lines.get(row).length(), newCol));
    }

    // ─── Mutations ──────────────────────────────────────────────────────────

    public void insertChar(char ch) {
        lines.get(row).insert(col, ch);
        col++;
        revision++;
    }

    public void insertString(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n') splitLine();
            else            insertChar(ch);
        }
    }

    public void splitLine() {
        StringBuilder cur = lines.get(row);
        StringBuilder rest = new StringBuilder(cur.substring(col));
        cur.delete(col, cur.length());
        lines.add(row + 1, rest);
        row++;
        col = 0;
        revision++;
    }

    /** Delete the character before the cursor (or merge with previous line at column 0). */
    public void backspace() {
        if (col > 0) {
            lines.get(row).deleteCharAt(col - 1);
            col--;
            revision++;
        } else if (row > 0) {
            StringBuilder prev = lines.get(row - 1);
            int newCol = prev.length();
            prev.append(lines.get(row));
            lines.remove(row);
            row--;
            col = newCol;
            revision++;
        }
    }

    /** Delete the character at the cursor (or merge with next line at end of line). */
    public void deleteForward() {
        StringBuilder cur = lines.get(row);
        if (col < cur.length()) {
            cur.deleteCharAt(col);
            revision++;
        } else if (row < lines.size() - 1) {
            cur.append(lines.get(row + 1));
            lines.remove(row + 1);
            revision++;
        }
    }

    // ─── Navigation ─────────────────────────────────────────────────────────

    public void moveLeft() {
        if (col > 0) col--;
        else if (row > 0) {
            row--;
            col = lines.get(row).length();
        }
    }

    public void moveRight() {
        StringBuilder cur = lines.get(row);
        if (col < cur.length()) col++;
        else if (row < lines.size() - 1) {
            row++;
            col = 0;
        }
    }

    public void moveUp() {
        if (row == 0) { col = 0; return; }
        row--;
        col = Math.min(col, lines.get(row).length());
    }

    public void moveDown() {
        if (row >= lines.size() - 1) { col = lines.get(row).length(); return; }
        row++;
        col = Math.min(col, lines.get(row).length());
    }

    public void moveHome() { col = 0; }
    public void moveEnd()  { col = lines.get(row).length(); }
}
