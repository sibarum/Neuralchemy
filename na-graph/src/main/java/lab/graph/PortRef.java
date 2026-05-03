package lab.graph;

import java.util.UUID;

/**
 * A {@code (node, port)} address — used by {@link Edge} endpoints. Port lookup goes through
 * the node's {@link NodeKind}, so this carries only the name, not the index or type.
 */
public record PortRef(UUID nodeId, String portName) {

    public PortRef {
        if (nodeId == null)                         throw new IllegalArgumentException("PortRef.nodeId");
        if (portName == null || portName.isEmpty()) throw new IllegalArgumentException("PortRef.portName");
    }

    @Override
    public String toString() {
        // Truncated UUID keeps logs scannable; full UUID is still in the field for codecs.
        String short8 = nodeId.toString().substring(0, 8);
        return short8 + ":" + portName;
    }
}
