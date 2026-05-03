package lab.ui.miniviz;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lab.graph.Edge;
import lab.graph.Network;
import lab.graph.Node;
import lab.graph.NodeKind;
import lab.graph.PortRef;
import lab.graph.Value;
import lab.graph.eval.DslResolver;
import lab.graph.eval.Evaluator;

/**
 * Sweeps a 2D scalar field by re-evaluating the upstream subgraph of a {@code probe.field}
 * node for every {@code (wx, wy)} the {@link ScalarField} layer requests.
 *
 * <h3>How the sweep works</h3>
 * The probe has one {@code value} input port. Tracing back from its incoming edge gives the
 * subgraph that produces the probe's value. Two of that subgraph's source nodes (zero-input
 * scalars — sliders / constants / inputs) become the X and Y axes: their {@code state.value}
 * is overridden per pixel, the network is re-evaluated on a private {@link Evaluator}, and
 * the value at the probe's input port is read back.
 *
 * <p>Auto-resolution: the first two source nodes encountered in a BFS upstream from the probe
 * become the X and Y axis. "First" is order-deterministic (insertion order in
 * {@link Network#nodes()}). Manual override is a future feature.
 *
 * <p>Isolation: the sampler holds its own {@link Evaluator}, so sweeping doesn't disturb the
 * port values the network screen reads for live labels.
 */
public final class ProbeFieldSampler implements ScalarField.Sampler {

    private final Network net;
    private final Evaluator eval;
    private final UUID probeNodeId;

    public ProbeFieldSampler(Network net, UUID probeNodeId, DslResolver dslResolver) {
        if (net == null)         throw new IllegalArgumentException("net");
        if (probeNodeId == null) throw new IllegalArgumentException("probeNodeId");
        if (dslResolver == null) dslResolver = DslResolver.NONE;
        this.net = net;
        this.probeNodeId = probeNodeId;
        this.eval = new Evaluator(net, dslResolver);
    }

    /**
     * Returns {@code true} when the probe is wired and has at least two upstream source nodes.
     * Callers can use this to fall back to a "probe not configured" placeholder.
     */
    public boolean canSweep() {
        return findIncomingEdge() != null && findAxes().length == 2;
    }

    @Override
    public float sample(float wx, float wy) {
        Edge incoming = findIncomingEdge();
        if (incoming == null) return 0f;

        UUID[] axes = findAxes();
        if (axes.length < 2) return 0f;
        Node xn = net.node(axes[0]);
        Node yn = net.node(axes[1]);
        if (xn == null || yn == null) return 0f;

        // Override and run.
        Value oldX = xn.state.get("value");
        Value oldY = yn.state.get("value");
        try {
            xn.state.put("value", new Value.Scalar(wx));
            yn.state.put("value", new Value.Scalar(wy));
            Map<PortRef, Value> values = eval.step();
            Value v = values.get(incoming.from());
            return v instanceof Value.Scalar s ? s.v() : 0f;
        } finally {
            // Restore unconditionally — exceptions in eval shouldn't corrupt slider state.
            xn.state.put("value", oldX);
            yn.state.put("value", oldY);
        }
    }

    /** Names of the two axis nodes (or {@code null}s when unresolved). For status / titles. */
    public String[] axisLabels() {
        UUID[] axes = findAxes();
        String[] out = { null, null };
        for (int i = 0; i < Math.min(2, axes.length); i++) {
            Node n = net.node(axes[i]);
            if (n == null) continue;
            Value name = n.state.get("name");
            if (name instanceof Value.Str s) out[i] = s.s();
            else out[i] = "n" + n.id.toString().substring(0, 4);
        }
        return out;
    }

    // ─── Topology helpers ───────────────────────────────────────────────────

    private Edge findIncomingEdge() {
        Node probe = net.node(probeNodeId);
        if (probe == null) return null;
        for (Edge e : net.edges()) {
            if (e.to().nodeId().equals(probeNodeId)) return e;
        }
        return null;
    }

    /** BFS upstream from the probe; collect source nodes (zero-input kinds) in encounter order. */
    private UUID[] findAxes() {
        Edge in = findIncomingEdge();
        if (in == null) return new UUID[0];

        Set<UUID> visited = new HashSet<>();
        Deque<UUID> frontier = new ArrayDeque<>();
        frontier.add(in.from().nodeId());

        UUID[] picked = new UUID[2];
        int filled = 0;
        while (!frontier.isEmpty() && filled < 2) {
            UUID cur = frontier.poll();
            if (!visited.add(cur)) continue;

            Node n = net.node(cur);
            if (n == null) continue;
            NodeKind k = net.kindOf(n);
            if (k == null) continue;

            if (k.inputs().isEmpty()) {
                picked[filled++] = cur;
                continue;       // don't recurse past a source
            }
            // Walk further upstream — enqueue every incoming edge's source.
            List<Edge> incoming = net.incomingTo(cur);
            for (Edge e : incoming) frontier.add(e.from().nodeId());
        }

        if (filled < 2) return new UUID[0];
        return new UUID[] { picked[0], picked[1] };
    }
}
