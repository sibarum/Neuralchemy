package lab.graph;

/**
 * One directed connection from an output port to an input port. Records are immutable —
 * editing means {@code disconnect(old)} + {@code connect(...)} on the {@link Network}.
 */
public record Edge(PortRef from, PortRef to) {

    public Edge {
        if (from == null) throw new IllegalArgumentException("Edge.from");
        if (to == null)   throw new IllegalArgumentException("Edge.to");
    }

    @Override
    public String toString() { return from + " → " + to; }
}
