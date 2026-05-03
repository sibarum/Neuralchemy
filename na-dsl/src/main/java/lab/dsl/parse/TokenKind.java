package lab.dsl.parse;

/**
 * Lexical categories. Keyword kinds (NEURON…OUT) are minted by the lexer when it sees the
 * exact word — they're not contextual, since dsl.md doesn't reuse those words anywhere else.
 */
public enum TokenKind {
    // Literals
    IDENT, NUMBER,

    // Section / declaration keywords
    NEURON, ACTIVATION, LOSS, IN, PARAM, OUT,

    // Punctuation
    LBRACE, RBRACE,
    LPAREN, RPAREN,
    LBRACKET, RBRACKET,
    COMMA, COLON, EQUALS, DOT,

    // Operators (precedence handled in Parser)
    PLUS, MINUS, STAR, SLASH, CARET, MIDDOT,

    EOF
}
