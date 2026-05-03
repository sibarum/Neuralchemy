package lab.project.json;

import java.io.IOException;
import java.io.Reader;

/**
 * Streaming JSON pull-parser. Gson-style API: {@code beginObject()} / {@code endObject()},
 * {@code hasNext()}, {@code nextName()}, {@code nextString()} etc. Hand-written so the
 * project layer stays reflection-free and GraalVM-native-image-clean per
 * designdocs/project-format.md.
 *
 * <p>Whitespace is RFC 8259 (space, tab, CR, LF). Strings honor the standard escape
 * sequences (backslash-quote, backslash-backslash, n / t / r / b / f, plus four-hex-digit
 * unicode escapes). Numbers are accepted in any form parseable by {@link Double#parseDouble}
 * after the lexer collects the
 * digits + optional sign / fraction / exponent.
 *
 * <p>Single-threaded; not safe to share across threads. Hold one per parse.
 */
public final class JsonReader implements AutoCloseable {

    private static final int CTX_NONE          = 0;     // top level, before any value
    private static final int CTX_OBJECT_START  = 1;     // just after '{'
    private static final int CTX_OBJECT_NAME   = 2;     // expecting a name
    private static final int CTX_OBJECT_COMMA  = 3;     // expecting ',' or '}'
    private static final int CTX_ARRAY_START   = 4;
    private static final int CTX_ARRAY_VALUE   = 5;
    private static final int CTX_ARRAY_COMMA   = 6;
    private static final int CTX_DANGLING_NAME = 7;     // just after a name + ':', expecting value

    private final Reader source;

    /** Lookahead character (-2 = unread, -1 = EOF, ≥0 = char). */
    private int next = -2;

    private int line = 1;
    private int col  = 0;

    /** Stack of CTX_* values, depth = current.length. */
    private int[] stack = new int[16];
    private int   depth = 1;
    /** When true, the parser has cached a peeked token in {@link #peekedKind} / payload fields. */
    private boolean peeked;
    private int     peekedKind;          // see TOKEN_*
    private String  peekedString;
    private double  peekedNumber;
    private boolean peekedNumberIsLong;

    public static final int TOKEN_BEGIN_OBJECT = 1;
    public static final int TOKEN_END_OBJECT   = 2;
    public static final int TOKEN_BEGIN_ARRAY  = 3;
    public static final int TOKEN_END_ARRAY    = 4;
    public static final int TOKEN_NAME         = 5;
    public static final int TOKEN_STRING       = 6;
    public static final int TOKEN_NUMBER       = 7;
    public static final int TOKEN_BOOLEAN_T    = 8;
    public static final int TOKEN_BOOLEAN_F    = 9;
    public static final int TOKEN_NULL         = 10;
    public static final int TOKEN_EOF          = 11;

    public JsonReader(Reader source) {
        if (source == null) throw new IllegalArgumentException("source");
        this.source = source;
        this.stack[0] = CTX_NONE;
    }

    @Override
    public void close() {
        try { source.close(); } catch (IOException ignored) {}
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    public void beginObject() {
        if (peekKind() != TOKEN_BEGIN_OBJECT) throw expected("{");
        consumePeek();
        push(CTX_OBJECT_START);
    }

    public void endObject() {
        if (peekKind() != TOKEN_END_OBJECT) throw expected("}");
        consumePeek();
        pop();
        valueRead();
    }

    public void beginArray() {
        if (peekKind() != TOKEN_BEGIN_ARRAY) throw expected("[");
        consumePeek();
        push(CTX_ARRAY_START);
    }

    public void endArray() {
        if (peekKind() != TOKEN_END_ARRAY) throw expected("]");
        consumePeek();
        pop();
        valueRead();
    }

    public boolean hasNext() {
        int t = peekKind();
        return t != TOKEN_END_OBJECT && t != TOKEN_END_ARRAY && t != TOKEN_EOF;
    }

    public String nextName() {
        if (peekKind() != TOKEN_NAME) throw expected("name");
        String s = peekedString;
        consumePeek();
        return s;
    }

    public String nextString() {
        if (peekKind() != TOKEN_STRING) throw expected("string");
        String s = peekedString;
        consumePeek();
        valueRead();
        return s;
    }

    public double nextDouble() {
        if (peekKind() != TOKEN_NUMBER) throw expected("number");
        double d = peekedNumber;
        consumePeek();
        valueRead();
        return d;
    }

    public long nextLong() {
        if (peekKind() != TOKEN_NUMBER) throw expected("number");
        long n = peekedNumberIsLong ? (long) peekedNumber : Math.round(peekedNumber);
        consumePeek();
        valueRead();
        return n;
    }

    public boolean nextBoolean() {
        int k = peekKind();
        if (k != TOKEN_BOOLEAN_T && k != TOKEN_BOOLEAN_F) throw expected("boolean");
        consumePeek();
        valueRead();
        return k == TOKEN_BOOLEAN_T;
    }

    public void nextNull() {
        if (peekKind() != TOKEN_NULL) throw expected("null");
        consumePeek();
        valueRead();
    }

    /** Skip the upcoming value (any depth). After this returns, the position is past the value. */
    public void skipValue() {
        int t = peekKind();
        switch (t) {
            case TOKEN_BEGIN_OBJECT -> {
                beginObject();
                while (hasNext()) {
                    nextName();
                    skipValue();
                }
                endObject();
            }
            case TOKEN_BEGIN_ARRAY -> {
                beginArray();
                while (hasNext()) skipValue();
                endArray();
            }
            case TOKEN_STRING     -> nextString();
            case TOKEN_NUMBER     -> nextDouble();
            case TOKEN_BOOLEAN_T, TOKEN_BOOLEAN_F -> nextBoolean();
            case TOKEN_NULL       -> nextNull();
            default               -> throw expected("value");
        }
    }

    // ─── Tokenization ───────────────────────────────────────────────────────

    private int peekKind() {
        if (peeked) return peekedKind;
        skipWhitespace();
        int ctx = stack[depth - 1];

        // Dispatch separator handling per context.
        switch (ctx) {
            case CTX_OBJECT_START -> {
                int c = peek();
                if (c == '}') { read(); return tokAt(TOKEN_END_OBJECT); }
                stack[depth - 1] = CTX_OBJECT_NAME;
                return peekKind();
            }
            case CTX_OBJECT_NAME -> {
                int c = peek();
                if (c != '"') throw error("expected name");
                String name = readString();
                skipWhitespace();
                int colon = read();
                if (colon != ':') throw error("expected ':' after name");
                peekedKind = TOKEN_NAME;
                peekedString = name;
                peeked = true;
                stack[depth - 1] = CTX_DANGLING_NAME;
                return TOKEN_NAME;
            }
            case CTX_OBJECT_COMMA -> {
                int c = peek();
                if (c == '}') { read(); return tokAt(TOKEN_END_OBJECT); }
                if (c != ',') throw error("expected ',' or '}' in object");
                read();
                skipWhitespace();
                stack[depth - 1] = CTX_OBJECT_NAME;
                return peekKind();
            }
            case CTX_ARRAY_START -> {
                int c = peek();
                if (c == ']') { read(); return tokAt(TOKEN_END_ARRAY); }
                stack[depth - 1] = CTX_ARRAY_VALUE;
            }
            case CTX_ARRAY_COMMA -> {
                int c = peek();
                if (c == ']') { read(); return tokAt(TOKEN_END_ARRAY); }
                if (c != ',') throw error("expected ',' or ']' in array");
                read();
                skipWhitespace();
                stack[depth - 1] = CTX_ARRAY_VALUE;
                return peekKind();
            }
            // CTX_NONE, CTX_DANGLING_NAME, CTX_ARRAY_VALUE: a value token follows directly
            default -> { /* fall through to the value lexer */ }
        }

        return lexValue();
    }

    /** Reads a single value token at the current position into the peek slot. */
    private int lexValue() {
        skipWhitespace();
        int c = peek();
        if (c == -1) {
            peekedKind = TOKEN_EOF;
            peeked = true;
            return TOKEN_EOF;
        }
        if (c == '{') { read(); peekedKind = TOKEN_BEGIN_OBJECT; peeked = true; return TOKEN_BEGIN_OBJECT; }
        if (c == '[') { read(); peekedKind = TOKEN_BEGIN_ARRAY;  peeked = true; return TOKEN_BEGIN_ARRAY;  }
        if (c == '"') { peekedString = readString(); peekedKind = TOKEN_STRING; peeked = true; return TOKEN_STRING; }
        if (c == 't' || c == 'f') {
            String word = readKeyword(c == 't' ? "true" : "false");
            peekedKind = c == 't' ? TOKEN_BOOLEAN_T : TOKEN_BOOLEAN_F;
            peekedString = word;
            peeked = true;
            return peekedKind;
        }
        if (c == 'n') {
            readKeyword("null");
            peekedKind = TOKEN_NULL;
            peeked = true;
            return TOKEN_NULL;
        }
        if (c == '-' || (c >= '0' && c <= '9')) {
            readNumber();
            peekedKind = TOKEN_NUMBER;
            peeked = true;
            return TOKEN_NUMBER;
        }
        throw error("unexpected character '" + (char) c + "'");
    }

    private int tokAt(int kind) {
        peekedKind = kind;
        peeked = true;
        return kind;
    }

    private void consumePeek() {
        if (!peeked) throw new IllegalStateException("consumePeek without peek");
        peeked = false;
        peekedString = null;
    }

    /** Called after a value (or whole subtree) is consumed — flips the container state. */
    private void valueRead() {
        int ctx = stack[depth - 1];
        switch (ctx) {
            case CTX_DANGLING_NAME -> stack[depth - 1] = CTX_OBJECT_COMMA;
            case CTX_ARRAY_VALUE   -> stack[depth - 1] = CTX_ARRAY_COMMA;
            default -> { /* CTX_NONE: end of stream is fine */ }
        }
    }

    private String readString() {
        int q = read();        // consume opening quote
        if (q != '"') throw error("expected string");
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = read();
            if (c == -1) throw error("unterminated string");
            if (c == '"') return sb.toString();
            if (c == '\\') {
                int esc = read();
                switch (esc) {
                    case '"', '\\', '/' -> sb.append((char) esc);
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        int cp = 0;
                        for (int i = 0; i < 4; i++) {
                            int h = read();
                            cp = (cp << 4) | hexDigit(h);
                        }
                        sb.append((char) cp);
                    }
                    default -> throw error("bad escape '\\" + (char) esc + "'");
                }
            } else {
                sb.append((char) c);
            }
        }
    }

    private void readNumber() {
        StringBuilder sb = new StringBuilder();
        boolean isFloat = false;
        if (peek() == '-') { sb.append((char) read()); }
        while (true) {
            int c = peek();
            if (c >= '0' && c <= '9') sb.append((char) read());
            else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                isFloat = true;
                sb.append((char) read());
            } else break;
        }
        String text = sb.toString();
        try {
            peekedNumber = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw error("malformed number '" + text + "'");
        }
        peekedNumberIsLong = !isFloat;
    }

    private String readKeyword(String expected) {
        for (int i = 0; i < expected.length(); i++) {
            int c = read();
            if (c != expected.charAt(i)) {
                throw error("expected '" + expected + "'");
            }
        }
        return expected;
    }

    private static int hexDigit(int c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + c - 'a';
        if (c >= 'A' && c <= 'F') return 10 + c - 'A';
        throw new JsonException("bad hex digit");
    }

    private void skipWhitespace() {
        while (true) {
            int c = peek();
            if (c == ' ' || c == '\t' || c == '\r') read();
            else if (c == '\n') { read(); /* line/col already updated by read() */ }
            else return;
        }
    }

    // ─── Char source ────────────────────────────────────────────────────────

    private int peek() {
        if (next == -2) {
            try { next = source.read(); }
            catch (IOException e) { throw new JsonException("I/O reading source", e); }
        }
        return next;
    }

    private int read() {
        int c = peek();
        next = -2;
        if (c == '\n') { line++; col = 0; }
        else if (c != -1) col++;
        return c;
    }

    private void push(int ctx) {
        if (depth == stack.length) {
            int[] grown = new int[stack.length * 2];
            System.arraycopy(stack, 0, grown, 0, stack.length);
            stack = grown;
        }
        stack[depth++] = ctx;
    }

    private void pop() {
        if (depth <= 1) throw new IllegalStateException("pop of empty stack");
        depth--;
    }

    private JsonException error(String msg) {
        return new JsonException(line + ":" + col + " " + msg);
    }

    private JsonException expected(String what) {
        return error("expected " + what);
    }
}
