package lab.graph;

import lab.dsl.Type;

/**
 * Port metadata — part of a {@link NodeKind}, never of a {@link Node}. Saved networks reference
 * ports by {@link #name}, not by {@link #index}, so kinds can grow new ports between versions
 * without invalidating older saves.
 *
 * <p>{@link #optional} is the design-doc escape hatch for "default-zero bias input"-style
 * cases (node-graph.md §Constraints) — the network is still runnable when the port has no
 * incoming edge.
 */
public record PortSpec(String name, Type type, int index, boolean optional) {

    public PortSpec {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("PortSpec.name");
        if (type == null)                   throw new IllegalArgumentException("PortSpec.type");
        if (index < 0)                      throw new IllegalArgumentException("PortSpec.index");
    }

    public static PortSpec required(String name, Type type, int index) {
        return new PortSpec(name, type, index, false);
    }

    public static PortSpec optional(String name, Type type, int index) {
        return new PortSpec(name, type, index, true);
    }
}
