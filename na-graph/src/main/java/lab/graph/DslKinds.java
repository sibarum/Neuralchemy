package lab.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lab.dsl.Type;
import lab.dsl.ast.Artifact;
import lab.dsl.ast.TypedName;

/**
 * Bridge from {@link Artifact}s parsed by {@code na-dsl} to graph {@link NodeKind}s. One DSL
 * neuron becomes one kind; activations and losses too (they appear as {@code dsl.activation.*}
 * and {@code dsl.loss.*} so the catalog stays unambiguous).
 *
 * <p>Per node-graph.md §"One uniform model": a neuron's parameters become regular input
 * ports — there's no separate "param" slot in the graph data model. Concrete shape:
 * <ul>
 *   <li>{@code dsl.neuron.<name>}  inputs = neuron.in: ++ neuron.param:, outputs = neuron.out:</li>
 *   <li>{@code dsl.activation.<name>}  inputs = activation args (typed), outputs = single scalar {@code value}</li>
 *   <li>{@code dsl.loss.<name>}  inputs = (pred, target), outputs = single scalar {@code loss}</li>
 * </ul>
 */
public final class DslKinds {

    private DslKinds() {}

    /** Build a kind id from an artifact: {@code dsl.<kind>.<name>}. */
    public static String idFor(Artifact a) {
        return switch (a) {
            case Artifact.Neuron     n -> "dsl.neuron."     + n.name();
            case Artifact.Activation v -> "dsl.activation." + v.name();
            case Artifact.Loss       l -> "dsl.loss."       + l.name();
        };
    }

    /** Convert a parsed {@link Artifact} into a {@link NodeKind}. */
    public static NodeKind kindOf(Artifact a) {
        return switch (a) {
            case Artifact.Neuron     n -> neuronKind(n);
            case Artifact.Activation v -> activationKind(v);
            case Artifact.Loss       l -> lossKind(l);
        };
    }

    private static NodeKind neuronKind(Artifact.Neuron n) {
        // inputs ++ params become input ports (parameters are "just typed input ports" per
        // node-graph.md §"One uniform model"). Index space is shared: ins first, then params.
        List<PortSpec> inputs = new ArrayList<>(n.inputs().size() + n.params().size());
        int idx = 0;
        for (TypedName tn : n.inputs())  inputs.add(PortSpec.required(tn.name(), tn.type(), idx++));
        for (TypedName tn : n.params())  inputs.add(PortSpec.required(tn.name(), tn.type(), idx++));

        List<PortSpec> outputs = new ArrayList<>(n.outputs().size());
        idx = 0;
        for (TypedName tn : n.outputs()) outputs.add(PortSpec.required(tn.name(), tn.type(), idx++));

        return new NodeKind("dsl.neuron." + n.name(), n.name(),
                inputs, outputs, Map.of());
    }

    private static NodeKind activationKind(Artifact.Activation v) {
        List<PortSpec> inputs = new ArrayList<>(v.args().size());
        int idx = 0;
        for (TypedName tn : v.args()) inputs.add(PortSpec.required(tn.name(), tn.type(), idx++));
        // Activations return their args' shape — for the common scalar-in / scalar-out case
        // that's scalar; multi-arg activations like softmax2 return a tuple, but until the
        // DSL gets explicit return-type syntax we'll assume scalar. The checker enforces this
        // is valid; refine when the DSL grows return annotations.
        List<PortSpec> outputs = List.of(PortSpec.required("value", Type.SCALAR, 0));
        return new NodeKind("dsl.activation." + v.name(), v.name(),
                inputs, outputs, Map.of());
    }

    private static NodeKind lossKind(Artifact.Loss l) {
        List<PortSpec> inputs = new ArrayList<>(l.args().size());
        int idx = 0;
        for (TypedName tn : l.args()) inputs.add(PortSpec.required(tn.name(), tn.type(), idx++));
        // Loss bodies always produce a scalar (checker rule).
        List<PortSpec> outputs = List.of(PortSpec.required("loss", Type.SCALAR, 0));
        return new NodeKind("dsl.loss." + l.name(), l.name(),
                inputs, outputs, Map.of());
    }

    /**
     * Drop everything currently in {@code registry} whose id starts with the DSL prefix and
     * re-register based on {@code artifacts}. Used when the workspace re-parses one or more
     * files and wants the registry to track the latest shape.
     *
     * <p>Returns the list of kind ids that disappeared (e.g. an artifact was deleted) so the
     * caller can invalidate any nodes referencing them.
     */
    public static List<String> sync(KindRegistry registry, List<Artifact> artifacts) {
        Map<String, NodeKind> next = new HashMap<>();
        for (Artifact a : artifacts) {
            NodeKind k = kindOf(a);
            next.put(k.id(), k);
        }
        List<String> dropped = new ArrayList<>();
        for (NodeKind existing : new ArrayList<>(registry.local())) {
            if (existing.id().startsWith("dsl.") && !next.containsKey(existing.id())) {
                registry.unregister(existing.id());
                dropped.add(existing.id());
            }
        }
        for (NodeKind k : next.values()) {
            registry.replace(k);
        }
        return dropped;
    }
}
