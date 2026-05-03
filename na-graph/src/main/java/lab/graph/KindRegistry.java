package lab.graph;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Process-wide map of {@code kind id} → {@link NodeKind}. Workspace load registers DSL-derived
 * kinds here; the editor's palette / inspector / validator look kinds up by string id.
 *
 * <p>One canonical {@link #builtin()} instance carries the always-present built-in catalog.
 * Workspaces own their own {@link KindRegistry} that delegates to {@link #builtin()} for
 * lookups not found locally.
 */
public final class KindRegistry {

    private static final KindRegistry BUILTIN = new KindRegistry(null);

    static {
        BuiltinKinds.registerInto(BUILTIN);
    }

    public static KindRegistry builtin() { return BUILTIN; }

    private final KindRegistry parent;
    private final Map<String, NodeKind> kinds = new LinkedHashMap<>();

    public KindRegistry() { this(BUILTIN); }

    /** Constructor for the BUILTIN singleton — must not chain to itself. */
    private KindRegistry(KindRegistry parent) { this.parent = parent; }

    public void register(NodeKind k) {
        if (kinds.containsKey(k.id())) {
            throw new IllegalStateException("kind '" + k.id() + "' already registered");
        }
        kinds.put(k.id(), k);
    }

    /** Replace an existing entry (used when a DSL artifact is re-parsed). */
    public void replace(NodeKind k) { kinds.put(k.id(), k); }

    /** Remove a registered kind; returns the removed entry or {@code null}. */
    public NodeKind unregister(String id) { return kinds.remove(id); }

    /** Local-then-parent lookup. Returns {@code null} if neither has it. */
    public NodeKind get(String id) {
        NodeKind k = kinds.get(id);
        if (k != null) return k;
        return parent != null ? parent.get(id) : null;
    }

    /** Locally registered kinds only; for "what does this workspace add?" queries. */
    public Collection<NodeKind> local() { return kinds.values(); }
}
