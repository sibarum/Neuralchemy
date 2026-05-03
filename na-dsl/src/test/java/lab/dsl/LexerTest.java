package lab.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import lab.dsl.parse.Lexer;
import lab.dsl.parse.ParseException;
import lab.dsl.parse.Token;
import lab.dsl.parse.TokenKind;

class LexerTest {

    private static List<Token> tok(String src) { return new Lexer(src).tokenize(); }

    @Test void emptyInputProducesEofOnly() {
        List<Token> ts = tok("");
        assertEquals(1, ts.size());
        assertEquals(TokenKind.EOF, ts.get(0).kind());
    }

    @Test void identifiersAndKeywordsDisambiguate() {
        List<Token> ts = tok("neuron foo bar in: out: param: notakeyword");
        assertEquals(TokenKind.NEURON, ts.get(0).kind());
        assertEquals(TokenKind.IDENT,  ts.get(1).kind());
        assertEquals("foo",            ts.get(1).text());
        assertEquals(TokenKind.IDENT,  ts.get(2).kind());
        assertEquals(TokenKind.IN,     ts.get(3).kind());
        assertEquals(TokenKind.COLON,  ts.get(4).kind());
        assertEquals(TokenKind.OUT,    ts.get(5).kind());
        assertEquals(TokenKind.COLON,  ts.get(6).kind());
        assertEquals(TokenKind.PARAM,  ts.get(7).kind());
        assertEquals(TokenKind.COLON,  ts.get(8).kind());
        assertEquals(TokenKind.IDENT,  ts.get(9).kind());
        assertEquals("notakeyword",    ts.get(9).text());
    }

    @Test void numericLiteralsScientificAndDecimal() {
        List<Token> ts = tok("1.0  -3  2.5e-4  1e10  .5");
        // Note: `-3` lexes as MINUS NUMBER(3) because sign isn't part of the numeric literal.
        assertEquals(TokenKind.NUMBER, ts.get(0).kind()); assertEquals(1.0, ts.get(0).numValue());
        assertEquals(TokenKind.MINUS,  ts.get(1).kind());
        assertEquals(TokenKind.NUMBER, ts.get(2).kind()); assertEquals(3.0,  ts.get(2).numValue());
        assertEquals(TokenKind.NUMBER, ts.get(3).kind()); assertEquals(2.5e-4, ts.get(3).numValue(), 1e-12);
        assertEquals(TokenKind.NUMBER, ts.get(4).kind()); assertEquals(1e10, ts.get(4).numValue());
        assertEquals(TokenKind.NUMBER, ts.get(5).kind()); assertEquals(0.5,  ts.get(5).numValue());
    }

    @Test void operatorsIncludingMiddot() {
        List<Token> ts = tok("+ - * / ^ ·");
        assertEquals(TokenKind.PLUS,    ts.get(0).kind());
        assertEquals(TokenKind.MINUS,   ts.get(1).kind());
        assertEquals(TokenKind.STAR,    ts.get(2).kind());
        assertEquals(TokenKind.SLASH,   ts.get(3).kind());
        assertEquals(TokenKind.CARET,   ts.get(4).kind());
        assertEquals(TokenKind.MIDDOT,  ts.get(5).kind());
    }

    @Test void lineCommentsAreSkippedButNewlinesAreNot() {
        List<Token> ts = tok(
                "in: x  // an input\nparam: a  // a parameter\nout: u\n");
        assertEquals(TokenKind.IN, ts.get(0).kind());
        assertEquals(TokenKind.COLON, ts.get(1).kind());
        assertEquals("x", ts.get(2).text());
        assertEquals(TokenKind.PARAM, ts.get(3).kind());
        // Comment text is gone — next non-trivia is param colon.
    }

    @Test void unexpectedCharThrowsWithSpan() {
        ParseException ex = assertThrows(ParseException.class, () -> tok("@"));
        assertTrue(ex.getMessage().contains("unexpected character"));
        assertEquals(1, ex.span.line());
        assertEquals(1, ex.span.col());
    }

    @Test void lineColumnTrackingAcrossNewlines() {
        List<Token> ts = tok("a\n  b");
        Token a = ts.get(0);
        Token b = ts.get(1);
        assertEquals(1, a.span().line());
        assertEquals(1, a.span().col());
        assertEquals(2, b.span().line());
        assertEquals(3, b.span().col());
    }
}
