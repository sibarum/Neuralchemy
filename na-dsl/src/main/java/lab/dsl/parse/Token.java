package lab.dsl.parse;

import lab.dsl.SourceSpan;

/**
 * A single lexed token. {@link #text} is the verbatim source slice for IDENT and NUMBER (and
 * any token where the lexer wants the original spelling); {@link #numValue} is parsed eagerly
 * for NUMBER kind so the parser doesn't repeat the work.
 */
public record Token(TokenKind kind, String text, double numValue, SourceSpan span) {

    /** Convenience constructor for tokens that don't carry a numeric value. */
    public static Token of(TokenKind kind, String text, SourceSpan span) {
        return new Token(kind, text, 0.0, span);
    }

    @Override
    public String toString() {
        return kind + (text != null && !text.isEmpty() ? "(" + text + ")" : "") + "@" + span;
    }
}
