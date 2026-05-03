package lab.dsl.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lab.dsl.SourceSpan;

/**
 * Hand-written tokenizer for {@code .nl} files. Whitespace and {@code //} line comments are
 * skipped silently; everything else maps to one of {@link TokenKind}.
 *
 * <p>Numbers are parsed eagerly into {@link Token#numValue} so the parser doesn't re-do
 * {@link Double#parseDouble} on every literal. Sign on numeric literals is <em>not</em>
 * lexed — leading {@code -} is a unary operator (per the precedence table in dsl.md), so
 * {@code -3} = {@code Unary(NEG, Num(3))}.
 *
 * <p>Stateful: hold one Lexer per source file; do not reuse across files.
 */
public final class Lexer {

    private static final Map<String, TokenKind> KEYWORDS = Map.of(
            "neuron",     TokenKind.NEURON,
            "activation", TokenKind.ACTIVATION,
            "loss",       TokenKind.LOSS,
            "in",         TokenKind.IN,
            "param",      TokenKind.PARAM,
            "out",        TokenKind.OUT
    );

    private final String src;
    private int pos;
    private int line = 1;
    private int col  = 1;

    public Lexer(String source) {
        this.src = source;
    }

    /** Consume the entire source and return all tokens, terminated by an EOF token. */
    public List<Token> tokenize() {
        List<Token> out = new ArrayList<>();
        while (true) {
            Token t = next();
            out.add(t);
            if (t.kind() == TokenKind.EOF) break;
        }
        return out;
    }

    private Token next() {
        skipTrivia();
        if (pos >= src.length()) return Token.of(TokenKind.EOF, "", spanHere(0));

        int startPos  = pos;
        int startLine = line;
        int startCol  = col;
        char c = src.charAt(pos);

        // Identifier or keyword
        if (isIdentStart(c)) {
            while (pos < src.length() && isIdentCont(src.charAt(pos))) advance();
            String text = src.substring(startPos, pos);
            TokenKind kw = KEYWORDS.get(text);
            return Token.of(kw != null ? kw : TokenKind.IDENT, text, span(startLine, startCol, pos - startPos));
        }

        // Number
        if (isDigit(c) || (c == '.' && pos + 1 < src.length() && isDigit(src.charAt(pos + 1)))) {
            return readNumber(startPos, startLine, startCol);
        }

        // Single- and multi-codepoint punctuation / operators
        return readSymbol(startLine, startCol);
    }

    private Token readNumber(int startPos, int startLine, int startCol) {
        boolean sawDot = false, sawExp = false;
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            if (isDigit(ch)) {
                advance();
            } else if (ch == '.' && !sawDot && !sawExp) {
                // Don't consume the dot if it's followed by a non-digit — that's field access
                // on a numeric literal, which is not a thing in this language anyway. But
                // distinguishing here keeps `1.x` (illegal) from being silently lexed as one
                // bad number; emit just `1` and let the parser complain about `.x`.
                if (pos + 1 < src.length() && !isDigit(src.charAt(pos + 1))) break;
                sawDot = true;
                advance();
            } else if ((ch == 'e' || ch == 'E') && !sawExp) {
                sawExp = true;
                advance();
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) advance();
                if (pos >= src.length() || !isDigit(src.charAt(pos))) {
                    throw new ParseException("malformed number: missing exponent digits",
                            span(startLine, startCol, pos - startPos));
                }
            } else {
                break;
            }
        }
        String text = src.substring(startPos, pos);
        double v;
        try {
            v = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new ParseException("malformed number: " + text,
                    span(startLine, startCol, pos - startPos));
        }
        return new Token(TokenKind.NUMBER, text, v, span(startLine, startCol, pos - startPos));
    }

    private Token readSymbol(int startLine, int startCol) {
        char c = src.charAt(pos);
        TokenKind kind = switch (c) {
            case '{' -> TokenKind.LBRACE;
            case '}' -> TokenKind.RBRACE;
            case '(' -> TokenKind.LPAREN;
            case ')' -> TokenKind.RPAREN;
            case '[' -> TokenKind.LBRACKET;
            case ']' -> TokenKind.RBRACKET;
            case ',' -> TokenKind.COMMA;
            case ':' -> TokenKind.COLON;
            case '=' -> TokenKind.EQUALS;
            case '.' -> TokenKind.DOT;
            case '+' -> TokenKind.PLUS;
            case '-' -> TokenKind.MINUS;
            case '*' -> TokenKind.STAR;
            case '/' -> TokenKind.SLASH;
            case '^' -> TokenKind.CARET;
            case '·' -> TokenKind.MIDDOT;            // U+00B7
            default  -> null;
        };
        if (kind == null) {
            throw new ParseException("unexpected character: '" + c + "' (U+" +
                    String.format("%04X", (int) c) + ")",
                    spanHere(1));
        }
        advance();
        return Token.of(kind, String.valueOf(c), span(startLine, startCol, 1));
    }

    private void skipTrivia() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '\n') {
                pos++; line++; col = 1;
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                // Line comment: skip to end of line (don't consume the newline — let the loop
                // handle it so line/col bookkeeping stays in one place).
                while (pos < src.length() && src.charAt(pos) != '\n') advance();
            } else {
                return;
            }
        }
    }

    private void advance() {
        pos++;
        col++;
    }

    private SourceSpan span(int startLine, int startCol, int length) {
        return new SourceSpan(startLine, startCol, length);
    }

    private SourceSpan spanHere(int length) {
        return new SourceSpan(line, col, length);
    }

    private static boolean isDigit(char c)      { return c >= '0' && c <= '9'; }
    private static boolean isIdentStart(char c) { return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'; }
    private static boolean isIdentCont(char c)  { return isIdentStart(c) || isDigit(c); }
}
