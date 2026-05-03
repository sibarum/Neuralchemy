package lab.project.json;

import java.io.IOException;
import java.io.Writer;

/**
 * Streaming JSON writer. Pretty-prints by default (2-space indent) so saved files diff
 * cleanly under git or hand-editing — the project format is human-readable by design (per
 * project-format.md).
 *
 * <p>Tracks container nesting on a small stack; emits separator commas / colons
 * automatically. Calling endpoints in the wrong order throws {@link JsonException} rather
 * than producing invalid JSON.
 */
public final class JsonWriter implements AutoCloseable {

    private final Writer out;
    private final boolean pretty;
    private final String indent;

    private static final int CTX_TOP          = 0;
    private static final int CTX_OBJECT_FIRST = 1;
    private static final int CTX_OBJECT_NEXT  = 2;
    private static final int CTX_OBJECT_NAMED = 3;     // after name(), expecting value
    private static final int CTX_ARRAY_FIRST  = 4;
    private static final int CTX_ARRAY_NEXT   = 5;

    private int[] stack = new int[16];
    private int   depth = 1;

    public JsonWriter(Writer out)                   { this(out, true, "  "); }
    public JsonWriter(Writer out, boolean pretty)   { this(out, pretty, "  "); }
    public JsonWriter(Writer out, boolean pretty, String indent) {
        if (out == null) throw new IllegalArgumentException("out");
        this.out = out;
        this.pretty = pretty;
        this.indent = indent;
        stack[0] = CTX_TOP;
    }

    @Override
    public void close() {
        try { out.flush(); out.close(); } catch (IOException e) { throw io(e); }
    }

    public void flush() {
        try { out.flush(); } catch (IOException e) { throw io(e); }
    }

    // ─── Structure ─────────────────────────────────────────────────────────

    public JsonWriter beginObject() {
        beforeValue();
        write('{');
        push(CTX_OBJECT_FIRST);
        return this;
    }

    public JsonWriter endObject() {
        int ctx = stack[depth - 1];
        if (ctx != CTX_OBJECT_FIRST && ctx != CTX_OBJECT_NEXT) {
            throw new JsonException("endObject() outside object");
        }
        pop();
        if (pretty && ctx == CTX_OBJECT_NEXT) newlineIndent();
        write('}');
        afterValue();
        return this;
    }

    public JsonWriter beginArray() {
        beforeValue();
        write('[');
        push(CTX_ARRAY_FIRST);
        return this;
    }

    public JsonWriter endArray() {
        int ctx = stack[depth - 1];
        if (ctx != CTX_ARRAY_FIRST && ctx != CTX_ARRAY_NEXT) {
            throw new JsonException("endArray() outside array");
        }
        pop();
        if (pretty && ctx == CTX_ARRAY_NEXT) newlineIndent();
        write(']');
        afterValue();
        return this;
    }

    public JsonWriter name(String name) {
        if (name == null) throw new JsonException("null name");
        int ctx = stack[depth - 1];
        if (ctx == CTX_OBJECT_FIRST) {
            if (pretty) newlineIndent();
        } else if (ctx == CTX_OBJECT_NEXT) {
            write(',');
            if (pretty) newlineIndent();
        } else {
            throw new JsonException("name() outside object");
        }
        writeString(name);
        write(':');
        if (pretty) write(' ');
        stack[depth - 1] = CTX_OBJECT_NAMED;
        return this;
    }

    // ─── Values ────────────────────────────────────────────────────────────

    public JsonWriter value(String v) {
        beforeValue();
        if (v == null) write("null");
        else writeString(v);
        afterValue();
        return this;
    }

    public JsonWriter value(double v) {
        beforeValue();
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw new JsonException("non-finite numbers are not valid JSON: " + v);
        }
        // If the value is integer-valued, emit without trailing zero/decimal so int-shaped
        // fields don't grow.
        if (v == (long) v && Math.abs(v) < 1e15) {
            write(Long.toString((long) v));
        } else {
            write(Double.toString(v));
        }
        afterValue();
        return this;
    }

    public JsonWriter value(long v) {
        beforeValue();
        write(Long.toString(v));
        afterValue();
        return this;
    }

    public JsonWriter value(boolean v) {
        beforeValue();
        write(v ? "true" : "false");
        afterValue();
        return this;
    }

    public JsonWriter nullValue() {
        beforeValue();
        write("null");
        afterValue();
        return this;
    }

    // ─── Internals ─────────────────────────────────────────────────────────

    /** Emit the separator that needs to come before the next value or '['/'{'. */
    private void beforeValue() {
        int ctx = stack[depth - 1];
        switch (ctx) {
            case CTX_TOP -> { /* nothing — first/only top-level value */ }
            case CTX_OBJECT_NAMED -> stack[depth - 1] = CTX_OBJECT_NEXT;
            case CTX_ARRAY_FIRST -> {
                if (pretty) newlineIndent();
                stack[depth - 1] = CTX_ARRAY_NEXT;
            }
            case CTX_ARRAY_NEXT -> {
                write(',');
                if (pretty) newlineIndent();
            }
            case CTX_OBJECT_FIRST, CTX_OBJECT_NEXT ->
                    throw new JsonException("value() expected name() first");
            default -> throw new JsonException("invalid writer context " + ctx);
        }
    }

    private void afterValue() {
        // beforeValue() already advanced the OBJECT_NAMED → OBJECT_NEXT for object members.
        // Array transitions are also handled there.
    }

    private void newlineIndent() {
        try {
            out.write('\n');
            for (int i = 1; i < depth; i++) out.write(indent);
        } catch (IOException e) { throw io(e); }
    }

    private void writeString(String s) {
        try {
            out.write('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"'  -> out.write("\\\"");
                    case '\\' -> out.write("\\\\");
                    case '\n' -> out.write("\\n");
                    case '\r' -> out.write("\\r");
                    case '\t' -> out.write("\\t");
                    case '\b' -> out.write("\\b");
                    case '\f' -> out.write("\\f");
                    default -> {
                        if (c < 0x20) {
                            out.write(String.format("\\u%04x", (int) c));
                        } else {
                            out.write(c);
                        }
                    }
                }
            }
            out.write('"');
        } catch (IOException e) { throw io(e); }
    }

    private void write(char c)    { try { out.write(c); }    catch (IOException e) { throw io(e); } }
    private void write(String s)  { try { out.write(s); }    catch (IOException e) { throw io(e); } }

    private void push(int ctx) {
        if (depth == stack.length) {
            int[] grown = new int[stack.length * 2];
            System.arraycopy(stack, 0, grown, 0, stack.length);
            stack = grown;
        }
        stack[depth++] = ctx;
    }

    private void pop() {
        if (depth <= 1) throw new JsonException("pop of empty stack");
        depth--;
    }

    private static JsonException io(IOException e) { return new JsonException("I/O writing", e); }
}
