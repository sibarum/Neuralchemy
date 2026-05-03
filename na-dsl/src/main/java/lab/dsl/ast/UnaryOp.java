package lab.dsl.ast;

/**
 * Unary operators per dsl.md. {@code !} is reserved for v2 (no booleans yet), so v1 only emits
 * {@link #NEG} from the parser.
 */
public enum UnaryOp {
    NEG("-");

    public final String symbol;

    UnaryOp(String symbol) { this.symbol = symbol; }
}
