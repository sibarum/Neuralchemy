package lab.dsl.ast;

import lab.dsl.SourceSpan;

/**
 * One {@code <name> = <expr>} statement in a neuron body. Single-assignment is enforced by
 * the checker, not the parser — duplicates only become a problem once the symbol table is
 * built.
 */
public record Assign(String name, Expr value, SourceSpan span) {}
