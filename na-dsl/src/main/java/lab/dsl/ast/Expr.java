package lab.dsl.ast;

import java.util.List;

import lab.dsl.SourceSpan;
import lab.dsl.Type;

/**
 * Expression AST. Sealed so the checker / future Truffle node generator can rely on
 * exhaustive switches.
 *
 * <p>One node kind per syntactic shape; semantic distinctions (e.g. {@code complex.zero} as a
 * type-level constant vs {@code y.x} as a component access) are resolved by the checker
 * looking at the {@link FieldAccess#target target} kind, not by minting separate AST shapes.
 */
public sealed interface Expr {

    SourceSpan span();

    /** Numeric literal — always parsed as {@code float} per dsl.md. */
    record Num(double value, SourceSpan span) implements Expr {}

    /** Reference to a name from {@code in:} / {@code param:} / a body local, or a type / function name. */
    record VarRef(String name, SourceSpan span) implements Expr {}

    /** {@code lhs op rhs}. */
    record Binary(BinOp op, Expr lhs, Expr rhs, SourceSpan span) implements Expr {}

    /** {@code op operand}. */
    record Unary(UnaryOp op, Expr operand, SourceSpan span) implements Expr {}

    /**
     * {@code name(args...)}. Resolves at check time to one of:
     * built-in scalar function, algebra op ({@code conj}/{@code norm}/{@code re}/{@code im}),
     * type constructor ({@code vec2}, {@code complex}, …), or workspace activation.
     */
    record Call(String name, List<Expr> args, SourceSpan span) implements Expr {}

    /**
     * {@code target.field}. {@code field} is either a component name ({@code x}/{@code y}/
     * {@code i}/{@code j}/{@code k}/{@code re}/{@code im}) or a type-level constant
     * ({@code zero}/{@code one}/{@code identity}) — disambiguated by the checker from
     * {@link #target}'s shape.
     */
    record FieldAccess(Expr target, String field, SourceSpan span) implements Expr {}

    /** {@code target.[row, col]}. v1 only allows zero-indexed integer literals (dsl.md). */
    record MatrixIndex(Expr target, int row, int col, SourceSpan span) implements Expr {}

    /**
     * Component-counted constructor: {@code vec2(1, 0)}, {@code complex(re, im)}, etc.
     *
     * <p>Parser also produces a {@link Call} with the same shape — the checker promotes it to
     * an {@code AlgebraLit} once it recognises the name. The dedicated record exists so later
     * passes (CSE, codegen) can match on it without re-doing the lookup.
     */
    record AlgebraLit(Type type, List<Expr> components, SourceSpan span) implements Expr {}
}
