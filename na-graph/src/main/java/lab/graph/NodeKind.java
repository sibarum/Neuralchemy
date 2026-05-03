package lab.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Class-level metadata for a node — what its ports are, what its persistent state looks like.
 * Stable across editor sessions for built-ins; rebuilt on workspace load for DSL-derived
 * kinds.
 *
 * <p>Per node-graph.md, nodes don't carry their port lists in the saved data — they carry
 * only their {@code kind} string, and the loader looks up the current {@link NodeKind} from
 * the {@link KindRegistry}. Adding a new port to a kind "just appears" in old saves; old
 * ports being renamed is a major-version bump.
 *
 * @param id            stable identifier ({@code "builtin.slider"}, {@code "dsl.neuron.split_mix"})
 * @param displayName   human-readable label for the palette / inspector
 * @param inputs        ordered input ports
 * @param outputs       ordered output ports
 * @param defaultState  initial value of each state key when a node is created
 */
public record NodeKind(
        String id,
        String displayName,
        List<PortSpec> inputs,
        List<PortSpec> outputs,
        Map<String, Value> defaultState
) {

    public NodeKind {
        if (id == null || id.isEmpty())                   throw new IllegalArgumentException("NodeKind.id");
        if (displayName == null)                          throw new IllegalArgumentException("NodeKind.displayName");
        if (inputs == null)                               throw new IllegalArgumentException("NodeKind.inputs");
        if (outputs == null)                              throw new IllegalArgumentException("NodeKind.outputs");
        if (defaultState == null)                         throw new IllegalArgumentException("NodeKind.defaultState");
        // Defensive copies — record fields are final but the lists/maps wouldn't be without these.
        inputs       = List.copyOf(inputs);
        outputs      = List.copyOf(outputs);
        defaultState = Map.copyOf(defaultState);

        // Indexes must be unique 0..n-1 within a port list.
        validateIndexes(inputs,  "input");
        validateIndexes(outputs, "output");
        // Names unique across both directions (so PortRef.portName is unambiguous).
        var seen = new LinkedHashMap<String, String>();
        for (PortSpec p : inputs)  if (seen.put(p.name(), "input")  != null) throw dup(id, p.name());
        for (PortSpec p : outputs) if (seen.put(p.name(), "output") != null) throw dup(id, p.name());
    }

    public PortSpec input(String name)  { return findPort(inputs,  name); }
    public PortSpec output(String name) { return findPort(outputs, name); }

    /** Look up a port by name across both directions; null if not found. */
    public PortSpec port(String name) {
        PortSpec p = findPort(inputs, name);
        return p != null ? p : findPort(outputs, name);
    }

    private static PortSpec findPort(List<PortSpec> ps, String name) {
        for (PortSpec p : ps) if (p.name().equals(name)) return p;
        return null;
    }

    private static void validateIndexes(List<PortSpec> ports, String dir) {
        boolean[] seen = new boolean[ports.size()];
        for (PortSpec p : ports) {
            if (p.index() >= ports.size() || seen[p.index()]) {
                throw new IllegalArgumentException("invalid " + dir + " port indexes: " + ports);
            }
            seen[p.index()] = true;
        }
    }

    private static IllegalArgumentException dup(String id, String name) {
        return new IllegalArgumentException("kind '" + id + "' has duplicate port name '" + name + "'");
    }
}
