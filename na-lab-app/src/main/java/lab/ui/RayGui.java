package lab.ui;

import com.raygui.RayguiLoader;
import com.raygui.bindings.Raygui;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

/**
 * Immediate-mode façade over raygui. Call methods between
 * {@link com.raylib.Window#beginDraw()} and {@link com.raylib.Canvas#close()} — raygui issues
 * raylib draw calls under the hood, so it has to run inside a draw scope.
 *
 * <p>Single-threaded by design: raygui keeps process-global state (active control id, focus,
 * style table) so calls from multiple threads will corrupt each other. Mirror raygui itself.
 *
 * <p>Allocation profile: a long-lived shared arena holds one reusable Rectangle segment that
 * each call rewrites. Variable-length text strings are still allocated per call (they have to
 * be — strings are variable-length); pre-encode and cache hot labels if it matters.
 *
 * <p>This is a lab-app convenience layer, not part of the binding module. The raw raygui
 * bindings live in {@link Raygui}; this class wraps the small subset of widgets the shell
 * uses today.
 */
public final class RayGui {

    /** Property id constants from raygui.h — only the ones we actually set. */
    public static final int CONTROL_DEFAULT  = 0;
    public static final int PROP_TEXT_SIZE   = 16;
    public static final int PROP_TEXT_SPACING = 17;

    static { RayguiLoader.load(); }

    private static final Arena ARENA = Arena.ofShared();
    private static final MemorySegment RECT = ARENA.allocate(16);
    private static final MemorySegment FLOAT_REF = ARENA.allocate(4);

    private RayGui() {}

    /** Set a global raygui style property (control=DEFAULT for the global slot). */
    public static void setStyle(int control, int property, int value) {
        Raygui.GuiSetStyle(control, property, value);
    }

    private static MemorySegment rect(float x, float y, float w, float h) {
        RECT.set(JAVA_FLOAT, 0, x);
        RECT.set(JAVA_FLOAT, 4, y);
        RECT.set(JAVA_FLOAT, 8, w);
        RECT.set(JAVA_FLOAT, 12, h);
        return RECT;
    }

    /** Immediate-mode push button. Returns {@code true} on the frame the user clicks it. */
    public static boolean button(int x, int y, int w, int h, String text) {
        try (Arena tmp = Arena.ofConfined()) {
            return Raygui.GuiButton(rect(x, y, w, h), tmp.allocateFrom(text)) != 0;
        }
    }

    /** Static text inside a control rectangle. Useful for inline labels next to other widgets. */
    public static void label(int x, int y, int w, int h, String text) {
        try (Arena tmp = Arena.ofConfined()) {
            Raygui.GuiLabel(rect(x, y, w, h), tmp.allocateFrom(text));
        }
    }

    /**
     * Float slider bound to a single-element {@code float[]} (array-as-ref idiom). Rewrites
     * {@code value[0]} in-place. {@code textLeft}/{@code textRight} may be {@code null} for
     * no decoration on that side. Returns {@code true} on frames where the user moved the
     * slider (so callers can flag dirty state if needed).
     */
    public static boolean slider(int x, int y, int w, int h,
                                 String textLeft, String textRight,
                                 float[] value, float min, float max) {
        FLOAT_REF.set(JAVA_FLOAT, 0, value[0]);
        boolean changed;
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment left  = textLeft  == null ? MemorySegment.NULL : tmp.allocateFrom(textLeft);
            MemorySegment right = textRight == null ? MemorySegment.NULL : tmp.allocateFrom(textRight);
            changed = Raygui.GuiSlider(rect(x, y, w, h), left, right, FLOAT_REF, min, max) != 0;
        }
        value[0] = FLOAT_REF.get(JAVA_FLOAT, 0);
        return changed;
    }
}
