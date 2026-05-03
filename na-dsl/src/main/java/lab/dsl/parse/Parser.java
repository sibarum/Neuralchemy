package lab.dsl.parse;

import java.util.ArrayList;
import java.util.List;

import lab.dsl.SourceSpan;
import lab.dsl.Type;
import lab.dsl.ast.Artifact;
import lab.dsl.ast.Assign;
import lab.dsl.ast.BinOp;
import lab.dsl.ast.Expr;
import lab.dsl.ast.TypedName;
import lab.dsl.ast.UnaryOp;

/**
 * Recursive-descent parser. One artifact per file (per dsl.md); the file kind is determined
 * by the first keyword token.
 *
 * <p>Expression precedence (highest to lowest, matching dsl.md verbatim):
 * <pre>
 *   ^                  power (right-associative)
 *   unary -            negation
 *   * /                multiplicative — algebra dispatch happens at check time
 *   + -                additive
 *   ·                  dot product (lowest)
 * </pre>
 *
 * <p>Statement separation: there is none. Bodies are sequences of {@code name = expr}; the
 * expression parser is greedy, so an assignment ends naturally when the parser sees the next
 * IDENT followed by EQUALS (or {@code RBRACE} / EOF). dsl.md's "no significant whitespace"
 * rule is what makes this work without ambiguity — there's no expression form that ends with
 * a bare IDENT followed by another IDENT.
 */
public final class Parser {

    private final List<Token> tokens;
    private int idx;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Artifact parse(String source) {
        return new Parser(new Lexer(source).tokenize()).parseFile();
    }

    public Artifact parseFile() {
        Token first = peek();
        Artifact a = switch (first.kind()) {
            case NEURON     -> parseNeuron();
            case ACTIVATION -> parseActivation();
            case LOSS       -> parseLoss();
            default -> throw error("expected 'neuron', 'activation', or 'loss' at file start", first.span());
        };
        if (peek().kind() != TokenKind.EOF) {
            throw error("expected end of file (one artifact per .nl file); got " + peek().kind(),
                    peek().span());
        }
        return a;
    }

    // ─── Top-level forms ────────────────────────────────────────────────────

    private Artifact.Neuron parseNeuron() {
        Token kw = expect(TokenKind.NEURON);
        String name = expect(TokenKind.IDENT).text();
        expect(TokenKind.LBRACE);

        List<TypedName> ins    = List.of();
        List<TypedName> params = List.of();
        List<TypedName> outs   = List.of();

        // Sections in the fixed order; each is optional except in: and out:.
        if (peek().kind() == TokenKind.IN) {
            ins = parseSection(TokenKind.IN);
        }
        if (peek().kind() == TokenKind.PARAM) {
            params = parseSection(TokenKind.PARAM);
        }
        if (peek().kind() == TokenKind.OUT) {
            outs = parseSection(TokenKind.OUT);
        }

        if (ins.isEmpty()) {
            throw error("neuron '" + name + "' must declare at least one 'in:' name", kw.span());
        }
        if (outs.isEmpty()) {
            throw error("neuron '" + name + "' must declare at least one 'out:' name", kw.span());
        }

        List<Assign> body = new ArrayList<>();
        while (peek().kind() != TokenKind.RBRACE && peek().kind() != TokenKind.EOF) {
            body.add(parseAssign());
        }
        Token closing = expect(TokenKind.RBRACE);

        return new Artifact.Neuron(name, ins, params, outs, body,
                SourceSpan.join(kw.span(), closing.span()));
    }

    private Artifact.Activation parseActivation() {
        Token kw = expect(TokenKind.ACTIVATION);
        String name = expect(TokenKind.IDENT).text();
        expect(TokenKind.LPAREN);
        List<TypedName> args = parseTypedNameList();
        expect(TokenKind.RPAREN);
        expect(TokenKind.EQUALS);
        Expr body = parseExpr();
        return new Artifact.Activation(name, args, body,
                SourceSpan.join(kw.span(), body.span()));
    }

    private Artifact.Loss parseLoss() {
        Token kw = expect(TokenKind.LOSS);
        String name = expect(TokenKind.IDENT).text();
        expect(TokenKind.LPAREN);
        List<TypedName> args = parseTypedNameList();
        expect(TokenKind.RPAREN);
        expect(TokenKind.EQUALS);
        Expr body = parseExpr();
        // Arity-2 enforcement is the checker's job (it has better error formatting context).
        return new Artifact.Loss(name, args, body,
                SourceSpan.join(kw.span(), body.span()));
    }

    private List<TypedName> parseSection(TokenKind sectionKw) {
        expect(sectionKw);
        expect(TokenKind.COLON);
        return parseTypedNameList();
    }

    /**
     * Comma-separated typed-name list. Terminates when the next-after-comma token isn't an
     * IDENT — covers both "next section keyword" and "first body assignment" cases without
     * needing the caller to pass a sentinel.
     */
    private List<TypedName> parseTypedNameList() {
        List<TypedName> result = new ArrayList<>();
        if (peek().kind() != TokenKind.IDENT) {
            return result;     // empty list (e.g. an activation with no args, if the user dared)
        }
        result.add(parseTypedName());
        while (peek().kind() == TokenKind.COMMA) {
            advance();
            if (peek().kind() != TokenKind.IDENT) {
                throw error("expected a name after ','", peek().span());
            }
            result.add(parseTypedName());
        }
        return result;
    }

    private TypedName parseTypedName() {
        Token nameTok = expect(TokenKind.IDENT);
        Type type = Type.SCALAR;
        SourceSpan span = nameTok.span();
        if (peek().kind() == TokenKind.COLON) {
            advance();
            Token typeTok = expect(TokenKind.IDENT);
            Type t = Type.fromSource(typeTok.text());
            if (t == null) {
                throw error("unknown type '" + typeTok.text() + "'", typeTok.span());
            }
            type = t;
            span = SourceSpan.join(nameTok.span(), typeTok.span());
        }
        return new TypedName(nameTok.text(), type, span);
    }

    private Assign parseAssign() {
        Token nameTok = peek();
        if (nameTok.kind() != TokenKind.IDENT) {
            throw error("expected a name to assign to (got " + nameTok.kind() + ")", nameTok.span());
        }
        advance();
        expect(TokenKind.EQUALS);
        Expr value = parseExpr();
        return new Assign(nameTok.text(), value,
                SourceSpan.join(nameTok.span(), value.span()));
    }

    // ─── Expressions ────────────────────────────────────────────────────────

    private Expr parseExpr() { return parseDot(); }

    /** {@code ·} — lowest precedence per dsl.md. */
    private Expr parseDot() {
        Expr lhs = parseAdditive();
        while (peek().kind() == TokenKind.MIDDOT) {
            advance();
            Expr rhs = parseAdditive();
            lhs = new Expr.Binary(BinOp.DOT, lhs, rhs, SourceSpan.join(lhs.span(), rhs.span()));
        }
        return lhs;
    }

    private Expr parseAdditive() {
        Expr lhs = parseMultiplicative();
        while (peek().kind() == TokenKind.PLUS || peek().kind() == TokenKind.MINUS) {
            BinOp op = peek().kind() == TokenKind.PLUS ? BinOp.ADD : BinOp.SUB;
            advance();
            Expr rhs = parseMultiplicative();
            lhs = new Expr.Binary(op, lhs, rhs, SourceSpan.join(lhs.span(), rhs.span()));
        }
        return lhs;
    }

    private Expr parseMultiplicative() {
        Expr lhs = parseUnary();
        while (peek().kind() == TokenKind.STAR || peek().kind() == TokenKind.SLASH) {
            BinOp op = peek().kind() == TokenKind.STAR ? BinOp.MUL : BinOp.DIV;
            advance();
            Expr rhs = parseUnary();
            lhs = new Expr.Binary(op, lhs, rhs, SourceSpan.join(lhs.span(), rhs.span()));
        }
        return lhs;
    }

    private Expr parseUnary() {
        if (peek().kind() == TokenKind.MINUS) {
            Token t = advance();
            Expr operand = parseUnary();   // chains so `--x` works (and we get to it via parseUnary not parsePower)
            return new Expr.Unary(UnaryOp.NEG, operand, SourceSpan.join(t.span(), operand.span()));
        }
        return parsePower();
    }

    private Expr parsePower() {
        Expr base = parsePrimary();
        if (peek().kind() == TokenKind.CARET) {
            advance();
            Expr exp = parseUnary();       // right-associative + tighter than unary minus on RHS
            return new Expr.Binary(BinOp.POW, base, exp, SourceSpan.join(base.span(), exp.span()));
        }
        return base;
    }

    private Expr parsePrimary() {
        Token t = peek();
        return switch (t.kind()) {
            case NUMBER -> { advance(); yield new Expr.Num(t.numValue(), t.span()); }
            case LPAREN -> {
                advance();
                Expr inner = parseExpr();
                expect(TokenKind.RPAREN);
                yield parseAccessChain(inner);
            }
            case IDENT -> {
                advance();
                Expr base;
                if (peek().kind() == TokenKind.LPAREN) {
                    advance();
                    List<Expr> args = new ArrayList<>();
                    if (peek().kind() != TokenKind.RPAREN) {
                        args.add(parseExpr());
                        while (peek().kind() == TokenKind.COMMA) {
                            advance();
                            args.add(parseExpr());
                        }
                    }
                    Token closing = expect(TokenKind.RPAREN);
                    base = new Expr.Call(t.text(), args, SourceSpan.join(t.span(), closing.span()));
                } else {
                    base = new Expr.VarRef(t.text(), t.span());
                }
                yield parseAccessChain(base);
            }
            default -> throw error("expected an expression (got " + t.kind() + ")", t.span());
        };
    }

    /** Trailing {@code .field} or {@code .[r, c]} accessors. */
    private Expr parseAccessChain(Expr lhs) {
        while (peek().kind() == TokenKind.DOT) {
            advance();
            Token next = peek();
            if (next.kind() == TokenKind.LBRACKET) {
                advance();
                int row = expectIntLiteral();
                expect(TokenKind.COMMA);
                int col = expectIntLiteral();
                Token close = expect(TokenKind.RBRACKET);
                lhs = new Expr.MatrixIndex(lhs, row, col, SourceSpan.join(lhs.span(), close.span()));
            } else if (next.kind() == TokenKind.IDENT) {
                advance();
                lhs = new Expr.FieldAccess(lhs, next.text(), SourceSpan.join(lhs.span(), next.span()));
            } else {
                throw error("expected a field name or '[' after '.'", next.span());
            }
        }
        return lhs;
    }

    private int expectIntLiteral() {
        Token t = expect(TokenKind.NUMBER);
        if (t.numValue() != Math.floor(t.numValue()) || Double.isInfinite(t.numValue())) {
            throw error("expected an integer literal (matrix index)", t.span());
        }
        return (int) t.numValue();
    }

    // ─── Token-stream helpers ───────────────────────────────────────────────

    private Token peek()    { return tokens.get(idx); }
    private Token advance() { return tokens.get(idx++); }

    private Token expect(TokenKind kind) {
        Token t = peek();
        if (t.kind() != kind) {
            throw error("expected " + kind + " but got " + t.kind() +
                    (t.text().isEmpty() ? "" : " ('" + t.text() + "')"), t.span());
        }
        idx++;
        return t;
    }

    private static ParseException error(String msg, SourceSpan span) {
        return new ParseException(msg, span);
    }
}
