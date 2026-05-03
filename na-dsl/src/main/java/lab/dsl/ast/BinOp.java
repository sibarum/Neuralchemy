package lab.dsl.ast;

/** Binary operators in the order they appear in dsl.md's precedence table. */
public enum BinOp {
    POW("^"),
    MUL("*"),
    DIV("/"),
    ADD("+"),
    SUB("-"),
    DOT("·");

    public final String symbol;

    BinOp(String symbol) { this.symbol = symbol; }
}
