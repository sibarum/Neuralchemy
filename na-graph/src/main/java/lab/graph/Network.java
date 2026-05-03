package lab.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lab.dsl.Type;

/**
 * The central data structure: a set of {@link Node}s plus directed {@link Edge}s between
 * their ports, against a {@link KindRegistry} that defines what each node's ports look like.
 *
 * <p>All structural mutations go through this class so invariants are preserved:
 * <ul>
 *   <li>Every edge connects an existing output port to an existing input port.</li>
 *   <li>Every input port has at most one incoming edge ({@link #connect} rejects duplicates).</li>
 *   <li>The edge graph is acyclic ({@link #connect} rejects cycle-forming wires).</li>
 *   <li>Edge endpoint types are compatible (exact match or scalar broadcast onto an algebra).</li>
 * </ul>
 *
 * <p>Mutability deliberately contained to this class. Direct field access on {@link Node} is
 * fine for non-structural changes (drag, slider drag) — those don't change the topology so
 * the invariants above are unaffected.
 */
public final class Network {

    public final UUID id;
    public final Viewport viewport = new Viewport();

    private final KindRegistry kinds;
    private final Map<UUID, Node> nodes = new LinkedHashMap<>();
    private final List<Edge>      edges = new ArrayList<>();

    public Network() {
        this(KindRegistry.builtin());
    }

    public Network(KindRegistry kinds) {
        this(UUID.randomUUID(), kinds);
    }

    public Network(UUID id, KindRegistry kinds) {
        if (id == null)    throw new IllegalArgumentException("Network.id");
        if (kinds == null) throw new IllegalArgumentException("Network.kinds");
        this.id = id;
        this.kinds = kinds;
    }

    public KindRegistry kinds() { return kinds; }

    // ─── Node operations ────────────────────────────────────────────────────

    /** Place a new node of {@code kind} at {@code (x, y)}; initial state from kind defaults. */
    public Node addNode(NodeKind kind, float x, float y) {
        return addNodeWithId(kind, UUID.randomUUID(), x, y);
    }

    /**
     * Restore a node with a preserved id. Used by codecs that need edge endpoints to resolve
     * against the original UUIDs from the saved file. {@code IllegalStateException} on a
     * duplicate id or unregistered kind.
     */
    public Node addNodeWithId(NodeKind kind, UUID id, float x, float y) {
        if (kind == null) throw new IllegalArgumentException("kind");
        if (id == null)   throw new IllegalArgumentException("id");
        if (kinds.get(kind.id()) == null) {
            throw new IllegalStateException("kind '" + kind.id() + "' is not registered");
        }
        if (nodes.containsKey(id)) {
            throw new IllegalStateException("duplicate node id: " + id);
        }
        Node n = new Node(id, kind.id(), x, y, kind.defaultState());
        nodes.put(id, n);
        return n;
    }

    /**
     * Reorder so {@code nodeId} is the last entry in the node map — i.e. it renders on top of
     * everything else. No effect if the node is missing or already on top. Returns whether the
     * order actually changed.
     */
    public boolean bringToFront(UUID nodeId) {
        Node n = nodes.get(nodeId);
        if (n == null) return false;
        // Already on top? LinkedHashMap insertion order has it last.
        UUID last = null;
        for (UUID id : nodes.keySet()) last = id;
        if (nodeId.equals(last)) return false;
        nodes.remove(nodeId);
        nodes.put(nodeId, n);
        return true;
    }

    /** Remove a node and any edges touching it. Returns true if the node existed. */
    public boolean removeNode(UUID nodeId) {
        if (!nodes.containsKey(nodeId)) return false;
        edges.removeIf(e -> e.from().nodeId().equals(nodeId) || e.to().nodeId().equals(nodeId));
        nodes.remove(nodeId);
        return true;
    }

    public Node node(UUID nodeId)         { return nodes.get(nodeId); }
    public NodeKind kindOf(Node n)        { return kinds.get(n.kind); }
    public List<Node> nodes()             { return List.copyOf(nodes.values()); }
    public List<Edge> edges()             { return List.copyOf(edges); }
    public int  nodeCount()               { return nodes.size(); }
    public int  edgeCount()               { return edges.size(); }

    // ─── Edge operations ────────────────────────────────────────────────────

    /** Dry-run connection check — never mutates the graph. */
    public ConnectionResult canConnect(PortRef from, PortRef to) {
        Node srcNode = nodes.get(from.nodeId());
        Node dstNode = nodes.get(to.nodeId());
        if (srcNode == null) return ConnectionResult.fail("source node not in network");
        if (dstNode == null) return ConnectionResult.fail("destination node not in network");
        if (srcNode == dstNode) {
            return ConnectionResult.fail("self-loops are not allowed");
        }

        NodeKind srcKind = kinds.get(srcNode.kind);
        NodeKind dstKind = kinds.get(dstNode.kind);
        if (srcKind == null) return ConnectionResult.fail("source kind '" + srcNode.kind + "' not registered");
        if (dstKind == null) return ConnectionResult.fail("destination kind '" + dstNode.kind + "' not registered");

        PortSpec srcPort = srcKind.output(from.portName());
        PortSpec dstPort = dstKind.input(to.portName());
        if (srcPort == null) return ConnectionResult.fail("'" + from.portName() + "' is not an output of " + srcKind.id());
        if (dstPort == null) return ConnectionResult.fail("'" + to.portName()   + "' is not an input of "  + dstKind.id());

        if (!typesCompatible(srcPort.type(), dstPort.type())) {
            return ConnectionResult.fail("type mismatch: " + srcPort.type().sourceName()
                    + " → " + dstPort.type().sourceName());
        }

        // One incoming edge per input port (per node-graph.md).
        for (Edge e : edges) {
            if (e.to().equals(to)) {
                return ConnectionResult.fail("input '" + to.portName()
                        + "' is already wired (use a builtin.add to combine sources)");
            }
        }

        // Cycle detection — a path already exists from `to.node` back to `from.node`?
        if (reaches(to.nodeId(), from.nodeId())) {
            return ConnectionResult.fail("would form a cycle");
        }

        return ConnectionResult.ok(new Edge(from, to));
    }

    /** Add an edge if {@link #canConnect} succeeds; returns the same {@link ConnectionResult}. */
    public ConnectionResult connect(PortRef from, PortRef to) {
        ConnectionResult r = canConnect(from, to);
        if (r.ok()) edges.add(r.edge());
        return r;
    }

    /** Remove an edge. Returns true if the edge existed. */
    public boolean disconnect(Edge e) {
        return edges.remove(e);
    }

    /** Edges feeding into a node. */
    public List<Edge> incomingTo(UUID nodeId) {
        List<Edge> out = new ArrayList<>();
        for (Edge e : edges) if (e.to().nodeId().equals(nodeId)) out.add(e);
        return out;
    }

    /** Edges fanning out from a node. */
    public List<Edge> outgoingFrom(UUID nodeId) {
        List<Edge> out = new ArrayList<>();
        for (Edge e : edges) if (e.from().nodeId().equals(nodeId)) out.add(e);
        return out;
    }

    // ─── Topology / validation ──────────────────────────────────────────────

    /**
     * Kahn's algorithm. Throws {@link IllegalStateException} if a cycle is somehow present —
     * it shouldn't be, since {@link #connect} rejects cycle-forming edges, but the check is
     * cheap and a useful invariant audit during eval.
     */
    public List<UUID> topologicalOrder() {
        Map<UUID, Integer> indeg = new HashMap<>();
        for (UUID n : nodes.keySet()) indeg.put(n, 0);
        for (Edge e : edges) indeg.merge(e.to().nodeId(), 1, Integer::sum);

        Deque<UUID> queue = new ArrayDeque<>();
        for (var entry : indeg.entrySet()) if (entry.getValue() == 0) queue.add(entry.getKey());

        List<UUID> order = new ArrayList<>(nodes.size());
        while (!queue.isEmpty()) {
            UUID n = queue.poll();
            order.add(n);
            for (Edge e : outgoingFrom(n)) {
                int d = indeg.merge(e.to().nodeId(), -1, Integer::sum);
                if (d == 0) queue.add(e.to().nodeId());
            }
        }
        if (order.size() != nodes.size()) {
            throw new IllegalStateException("cycle detected during topological sort");
        }
        return order;
    }

    /**
     * "Runnable" check per node-graph.md §Constraints — every required input port across all
     * nodes has an incoming edge. Returns the list of unwired required ports as
     * {@code "<kind>.<port>"} strings (empty list = network is runnable).
     */
    public List<String> validate() {
        Set<PortRef> wiredInputs = new HashSet<>();
        for (Edge e : edges) wiredInputs.add(e.to());

        List<String> issues = new ArrayList<>();
        for (Node n : nodes.values()) {
            NodeKind k = kinds.get(n.kind);
            if (k == null) {
                issues.add("node " + n.id.toString().substring(0, 8) + " has unknown kind '" + n.kind + "'");
                continue;
            }
            for (PortSpec p : k.inputs()) {
                if (p.optional()) continue;
                if (!wiredInputs.contains(new PortRef(n.id, p.name()))) {
                    issues.add(k.id() + "." + p.name() + " not wired");
                }
            }
        }
        return issues;
    }

    public boolean isRunnable() { return validate().isEmpty(); }

    // ─── Internals ──────────────────────────────────────────────────────────

    /** True iff a directed edge path exists from {@code start} to {@code target}. */
    private boolean reaches(UUID start, UUID target) {
        if (start.equals(target)) return true;
        Set<UUID> seen = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            if (!seen.add(cur)) continue;
            if (cur.equals(target)) return true;
            for (Edge e : edges) {
                if (e.from().nodeId().equals(cur)) stack.push(e.to().nodeId());
            }
        }
        return false;
    }

    /**
     * Source-port type {@code src} can feed an input port of type {@code dst} when:
     * <ul>
     *   <li>they're the exact same type, or</li>
     *   <li>{@code src} is scalar and {@code dst} is one of the algebra types — corresponds
     *       to dsl.md's "scalar × T = scalar broadcast" rule. Wires don't multiply, but a
     *       scalar feeding into an algebra-typed port is valid for downstream operators
     *       that broadcast (e.g. {@code builtin.mul}).</li>
     * </ul>
     *
     * <p>The reverse — algebra-typed source into a scalar input — is not allowed; it would
     * silently drop information.
     */
    private static boolean typesCompatible(Type src, Type dst) {
        if (src == dst) return true;
        if (src == Type.SCALAR && dst != null) return true;
        return false;
    }
}
