package lab.dsl.parse;

import lab.dsl.SourceSpan;

/**
 * Thrown by the lexer or parser on the first malformed token / unexpected shape. Carries a
 * {@link SourceSpan} so the editor can highlight the exact region.
 *
 * <p>Multiple-error reporting is deliberately deferred — for a v1 hand-written parser, fail
 * fast keeps the grammar small. The checker (which has more decisions to surface) accumulates
 * diagnostics instead.
 */
public final class ParseException extends RuntimeException {

    public final SourceSpan span;
    /** Message without the "{line}:{col} " prefix that {@link #getMessage()} adds for logs. */
    public final String rawMessage;

    public ParseException(String message, SourceSpan span) {
        super(span.line() + ":" + span.col() + " " + message);
        this.span = span;
        this.rawMessage = message;
    }
}
