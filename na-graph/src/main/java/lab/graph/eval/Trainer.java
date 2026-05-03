package lab.graph.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lab.dsl.ast.Artifact;
import lab.graph.BuiltinKinds;
import lab.graph.Edge;
import lab.graph.Network;
import lab.graph.Node;
import lab.graph.NodeKind;
import lab.graph.PortRef;
import lab.graph.PortSpec;
import lab.graph.Value;

/**
 * One-shot forward + backward + SGD trainer for scalar networks. Wraps a forward
 * {@link Evaluator}, then walks reverse topo accumulating gradients, then applies
 * {@code value -= lr · grad} to every {@link BuiltinKinds#LEARNABLE_S} node.
 *
 * <h3>Limitations (v0)</h3>
 * <ul>
 *   <li>Built-ins only. DSL neuron backward derivation isn't implemented yet — networks that
 *       go through {@code dsl.neuron.*} kinds will simply skip those backward steps, which
 *       silently zeros the gradient through them. Use built-in arithmetic (add/sub/mul) for
 *       trainable subgraphs until DSL differentiation lands.</li>
 *   <li>Scalar gradients only. {@link Algebra} arithmetic on richer types isn't yet
 *       backward-aware.</li>
 *   <li>Plain SGD; no momentum, weight decay, or per-parameter optimizer state.</li>
 * </ul>
 */
public final class Trainer {

    private final Network net;
    private final Evaluator evaluator;
    private final DslResolver dslResolver;
    private final PortRef lossPort;
    /** Memoized backwards by kind id — built lazily on first encounter. */
    private final Map<String, Backward> backwardCache = new HashMap<>();

    public Trainer(Network net, DslResolver dslResolver, PortRef lossPort) {
        if (net == null)      throw new IllegalArgumentException("net");
        if (lossPort == null) throw new IllegalArgumentException("lossPort");
        this.net = net;
        this.dslResolver = dslResolver == null ? DslResolver.NONE : dslResolver;
        this.evaluator = new Evaluator(net, this.dslResolver);
        this.lossPort = lossPort;
    }

    /** Convenience for callers that don't have a DSL resolver. */
    public Trainer(Network net, PortRef lossPort) { this(net, DslResolver.NONE, lossPort); }

    public Network network() { return net; }
    public PortRef lossPort() { return lossPort; }

    /**
     * Run one training step. Returns the post-step loss value (i.e. the loss as evaluated on
     * the parameters that were in effect during this step's forward pass — not the post-update
     * parameters). Returns NaN when the network isn't runnable or the loss port is missing.
     */
    public float step(float lr) {
        // 1. Forward.
        Map<PortRef, Value> portValues = evaluator.step();
        if (portValues.isEmpty()) return Float.NaN;
        Value lossV = portValues.get(lossPort);
        if (!(lossV instanceof Value.Scalar lossScalar)) return Float.NaN;

        // 2. Backward.
        Map<PortRef, Value> portGrads = backward(portValues);

        // 3. SGD on every Learnable node.
        for (Node n : net.nodes()) {
            if (!BuiltinKinds.LEARNABLE_S.equals(n.kind)) continue;

            Backward bw = BuiltinBackwards.backwardOf(n.kind);
            if (bw == null) continue;

            Map<String, Value> outGrads = readNodeOutGrads(n, portGrads);
            Map<String, Value> inputs   = readForwardInputs(n, portValues);
            Backward.Result r = bw.apply(inputs, outGrads, n.state);

            Value gV = r.stateGrads().get("value");
            if (!(gV instanceof Value.Scalar gs)) continue;
            Value cur = n.state.get("value");
            if (!(cur instanceof Value.Scalar cs)) continue;
            n.state.put("value", new Value.Scalar(cs.v() - lr * gs.v()));
        }

        return lossScalar.v();
    }

    // ─── Backward pass ──────────────────────────────────────────────────────

    /** Reverse-topo walk; returns gradient of loss w.r.t. each port in the network. */
    private Map<PortRef, Value> backward(Map<PortRef, Value> portValues) {
        Map<PortRef, Value> grads = new HashMap<>();
        // Seed: ∂loss/∂loss = 1.
        grads.put(lossPort, new Value.Scalar(1f));

        List<UUID> topo = net.topologicalOrder();
        List<UUID> reverse = new ArrayList<>(topo);
        Collections.reverse(reverse);

        for (UUID nodeId : reverse) {
            Node node = net.node(nodeId);
            if (node == null) continue;
            NodeKind kind = net.kindOf(node);
            if (kind == null) continue;
            Backward bw = backwardFor(kind);
            if (bw == null) continue;

            Map<String, Value> outGrads = readNodeOutGrads(node, grads);
            // If every output gradient is zero (default), nothing to propagate.
            if (allZero(outGrads)) continue;

            Map<String, Value> inputs = readForwardInputs(node, portValues);
            Backward.Result r = bw.apply(inputs, outGrads, node.state);

            // Distribute input grads upstream — for each input port of this node, find the
            // upstream output port feeding it and accumulate.
            for (Map.Entry<String, Value> e : r.inputGrads().entrySet()) {
                Edge incoming = incomingEdge(node.id, e.getKey());
                if (incoming == null) continue;
                accumulate(grads, incoming.from(), e.getValue());
            }
        }
        return grads;
    }

    // ─── Backward dispatch ──────────────────────────────────────────────────

    /**
     * Return a {@link Backward} for {@code kind}: built-in if registered, otherwise DSL via the
     * resolver, otherwise {@code null} (no gradients flow through this node). Memoized — the
     * DSL path runs symbolic differentiation at first encounter, and we don't want to redo it
     * every backward step.
     */
    private Backward backwardFor(NodeKind kind) {
        Backward cached = backwardCache.get(kind.id());
        if (cached != null) return cached;

        Backward bw = BuiltinBackwards.backwardOf(kind.id());
        if (bw == null && kind.id().startsWith("dsl.")) {
            Artifact a = dslResolver.resolve(kind.id());
            if (a != null) bw = DslBackwards.forArtifact(a);
        }
        if (bw != null) backwardCache.put(kind.id(), bw);
        return bw;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Map<String, Value> readNodeOutGrads(Node node, Map<PortRef, Value> grads) {
        NodeKind kind = net.kindOf(node);
        if (kind == null) return Map.of();
        Map<String, Value> out = new HashMap<>();
        for (PortSpec p : kind.outputs()) {
            Value g = grads.get(new PortRef(node.id, p.name()));
            out.put(p.name(), g != null ? g : new Value.Scalar(0f));
        }
        return out;
    }

    private Map<String, Value> readForwardInputs(Node node, Map<PortRef, Value> portValues) {
        Map<String, Value> in = new HashMap<>();
        for (Edge e : net.incomingTo(node.id)) {
            Value v = portValues.get(e.from());
            if (v != null) in.put(e.to().portName(), v);
        }
        return in;
    }

    private Edge incomingEdge(UUID nodeId, String portName) {
        for (Edge e : net.incomingTo(nodeId)) {
            if (e.to().portName().equals(portName)) return e;
        }
        return null;
    }

    private static void accumulate(Map<PortRef, Value> grads, PortRef ref, Value delta) {
        if (!(delta instanceof Value.Scalar ds)) return;
        Value cur = grads.get(ref);
        float curF = cur instanceof Value.Scalar cs ? cs.v() : 0f;
        grads.put(ref, new Value.Scalar(curF + ds.v()));
    }

    private static boolean allZero(Map<String, Value> grads) {
        for (Value v : grads.values()) {
            if (v instanceof Value.Scalar s && s.v() != 0f) return false;
        }
        return true;
    }
}
