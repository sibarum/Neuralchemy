package lab.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One vertex in the {@link Network}. Owns its position (mutable, dragged by the editor),
 * its persistent state (also mutable — slider values, learnable weights), and a collapse
 * hint. Port lists are <em>not</em> stored here; they're looked up from the {@link NodeKind}
 * at runtime.
 */
public final class Node {

    public final UUID   id;
    public final String kind;

    public float positionX, positionY;
    public final Map<String, Value> state;
    public CollapseMode collapse = CollapseMode.AUTO;

    /** Construct a node with the kind's default state. Most callers should use {@link Network#addNode}. */
    public Node(UUID id, String kind, float x, float y, Map<String, Value> initialState) {
        if (id == null)                       throw new IllegalArgumentException("Node.id");
        if (kind == null || kind.isEmpty())   throw new IllegalArgumentException("Node.kind");
        this.id = id;
        this.kind = kind;
        this.positionX = x;
        this.positionY = y;
        this.state = new HashMap<>(initialState == null ? Map.of() : initialState);
    }

    @Override
    public String toString() {
        return "Node(" + id.toString().substring(0, 8) + " kind=" + kind
                + " pos=(" + positionX + "," + positionY + "))";
    }
}
