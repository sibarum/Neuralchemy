package lab.dsl;

/**
 * 1-based line/column anchor + length-in-characters of a source-text region. Carried by every
 * token and AST node so error messages and IDE features (hover, goto, rename) all share a
 * single coordinate system.
 *
 * <p>{@link #length} is in characters, not UTF-8 bytes — the lexer counts codepoints. For
 * multi-line spans, {@link #length} is the count of characters from {@code (line, col)} to the
 * end of the span <em>treating newlines as one character each</em>.
 */
public record SourceSpan(int line, int col, int length) {

    public static final SourceSpan UNKNOWN = new SourceSpan(0, 0, 0);

    /** Span covering the whole region from the start of {@code a} to the end of {@code b}. */
    public static SourceSpan join(SourceSpan a, SourceSpan b) {
        if (a == UNKNOWN) return b;
        if (b == UNKNOWN) return a;
        // We don't know the absolute offset, so length is best-effort: assume both spans are
        // on the same line if a.line == b.line, otherwise approximate.
        if (a.line == b.line) {
            int newLen = (b.col - a.col) + b.length;
            return new SourceSpan(a.line, a.col, Math.max(newLen, a.length));
        }
        return new SourceSpan(a.line, a.col, a.length);
    }

    @Override
    public String toString() { return line + ":" + col; }
}
