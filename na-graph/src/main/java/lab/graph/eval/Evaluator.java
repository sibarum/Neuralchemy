package lab.graph.eval;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * Forward-pass evaluator. {@link #step()} runs one tick: topo-sort the network, evaluate
 * each node's kind given its inputs and state, cache outputs in the per-port value map.
 *
 * <p>Per-frame use is fine — networks here are tens of nodes, not thousands. Hot-path
 * batching, caching, and zero-alloc rewriting come when the simulator is doing real
 * training. For now the eval shape is the v1 reference per node-graph.md §Evaluation.
 *
 * <p>Built-ins are dispatched inline by kind id; DSL kinds resolve through a
 * {@link DslResolver} so {@code na-graph} doesn't depend on any UI / workspace layer.
 */
public final class Evaluator {

    private final Network net;
    private final DslResolver dslResolver;
    private final Map<PortRef, Value> portValues = new LinkedHashMap<>();

    public Evaluator(Network net) {
        this(net, DslResolver.NONE);
    }

    public Evaluator(Network net, DslResolver dslResolver) {
        if (net == null)         throw new IllegalArgumentException("Evaluator.net");
        if (dslResolver == null) throw new IllegalArgumentException("Evaluator.dslResolver");
        this.net = net;
        this.dslResolver = dslResolver;
    }

    /**
     * Run one forward pass. Returns the per-port value map (immutable view). Empty when the
     * network isn't runnable (unwired required ports) — partial-evaluation might be useful
     * for the editor but we keep the contract simple here.
     */
    public Map<PortRef, Value> step() {
        portValues.clear();
        if (!net.isRunnable()) return Map.of();
        for (UUID id : net.topologicalOrder()) {
            evalNode(id);
        }
        return Collections.unmodifiableMap(portValues);
    }

    public Value valueAt(PortRef ref)              { return portValues.get(ref); }
    public Value valueAt(UUID nodeId, String port) { return portValues.get(new PortRef(nodeId, port)); }

    public Network    network()      { return net; }
    public DslResolver dslResolver() { return dslResolver; }

    private void evalNode(UUID nodeId) {
        Node node = net.node(nodeId);
        NodeKind kind = net.kindOf(node);
        if (kind == null) return;
        Map<String, Value> inputs = readInputs(nodeId, kind);
        Map<String, Value> outputs = compute(node, kind, inputs);
        for (PortSpec p : kind.outputs()) {
            Value v = outputs.get(p.name());
            if (v != null) portValues.put(new PortRef(nodeId, p.name()), v);
        }
    }

    private Map<String, Value> readInputs(UUID nodeId, NodeKind kind) {
        Map<String, Value> inputs = new HashMap<>();
        for (Edge e : net.incomingTo(nodeId)) {
            Value v = portValues.get(e.from());
            if (v != null) inputs.put(e.to().portName(), v);
        }
        // Optional input ports may legitimately be missing.
        for (PortSpec p : kind.inputs()) {
            if (!inputs.containsKey(p.name()) && !p.optional()) {
                // isRunnable() should have caught this; defensive only.
                inputs.put(p.name(), Value.zero(p.type()));
            }
        }
        return inputs;
    }

    private Map<String, Value> compute(Node node, NodeKind kind, Map<String, Value> inputs) {
        // Built-ins first — switch on the canonical id.
        Map<String, Value> r = computeBuiltin(node, kind, inputs);
        if (r != null) return r;

        // DSL — dispatch by id prefix.
        String id = kind.id();
        if (id.startsWith("dsl.neuron.")) {
            Artifact a = dslResolver.resolve(id);
            if (a instanceof Artifact.Neuron n) {
                return DslInterpreter.evalNeuron(n, inputs);
            }
            return Map.of();
        }
        if (id.startsWith("dsl.activation.")) {
            Artifact a = dslResolver.resolve(id);
            if (a instanceof Artifact.Activation v) {
                return Map.of("value", DslInterpreter.evalActivation(v, inputs));
            }
            return Map.of();
        }
        if (id.startsWith("dsl.loss.")) {
            Artifact a = dslResolver.resolve(id);
            if (a instanceof Artifact.Loss l) {
                return Map.of("loss", DslInterpreter.evalLoss(l, inputs));
            }
            return Map.of();
        }
        return Map.of();
    }

    /** Returns {@code null} if the kind isn't a built-in we know about. */
    private Map<String, Value> computeBuiltin(Node node, NodeKind kind, Map<String, Value> inputs) {
        return switch (kind.id()) {
            case BuiltinKinds.SLIDER, BuiltinKinds.CONSTANT_S, BuiltinKinds.INPUT_S,
                 BuiltinKinds.LEARNABLE_S ->
                    Map.of("value", node.state.getOrDefault("value", new Value.Scalar(0f)));
            case BuiltinKinds.OUTPUT_S, BuiltinKinds.PROBE_FIELD, BuiltinKinds.PROBE_METRIC_S ->
                    Map.of();
            case BuiltinKinds.ADD_S ->
                    Map.of("sum", Algebra.add(inputs.get("a"), inputs.get("b")));
            case BuiltinKinds.SUB_S ->
                    Map.of("diff", Algebra.sub(inputs.get("a"), inputs.get("b")));
            case BuiltinKinds.MUL_S ->
                    Map.of("product", Algebra.mul(inputs.get("a"), inputs.get("b")));
            case BuiltinKinds.LOSS_MSE_S -> {
                Value pred = inputs.get("pred"), target = inputs.get("target");
                if (pred instanceof Value.Scalar p && target instanceof Value.Scalar t) {
                    float d = p.v() - t.v();
                    yield Map.of("loss", new Value.Scalar(d * d));
                }
                yield Map.of();
            }
            case BuiltinKinds.SPLIT -> {
                Value pair = inputs.get("pair");
                if (pair instanceof Value.Vec2 v) {
                    yield Map.of("a", new Value.Scalar(v.x()),
                                 "b", new Value.Scalar(v.y()));
                }
                yield Map.of();
            }
            case BuiltinKinds.JOIN -> {
                Value a = inputs.get("a"), b = inputs.get("b");
                if (a instanceof Value.Scalar sa && b instanceof Value.Scalar sb) {
                    yield Map.of("pair", new Value.Vec2(sa.v(), sb.v()));
                }
                yield Map.of();
            }
            default -> null;
        };
    }
}
