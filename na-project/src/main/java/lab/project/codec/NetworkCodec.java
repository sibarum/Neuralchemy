package lab.project.codec;

import java.util.Map;
import java.util.UUID;

import lab.graph.CollapseMode;
import lab.graph.Edge;
import lab.graph.KindRegistry;
import lab.graph.Network;
import lab.graph.Node;
import lab.graph.NodeKind;
import lab.graph.PortRef;
import lab.graph.Value;
import lab.project.json.JsonReader;
import lab.project.json.JsonWriter;

/**
 * JSON codec for {@link Network}. Reads in two passes per the
 * "load then resolve" pattern (since a {@link Node} needs to know its kind via the
 * {@link KindRegistry}, which the loader injects at the call site).
 *
 * <pre>{@code
 * {
 *   "id":       "uuid",
 *   "viewport": { "x": 0, "y": 0, "zoom": 1.0 },
 *   "nodes":    [ ... node objects ... ],
 *   "edges":    [ ... edge objects ... ]
 * }
 * }</pre>
 *
 * <p>Each node is:
 * <pre>{@code
 * { "id": "uuid", "kind": "builtin.slider", "x": 100, "y": 200,
 *   "collapse": "AUTO",
 *   "state": { "value": <value-object>, "min": ..., "max": ... } }
 * }</pre>
 *
 * <p>Each edge is:
 * <pre>{@code
 * { "from": { "node": "uuid", "port": "value" },
 *   "to":   { "node": "uuid", "port": "a" } }
 * }</pre>
 */
public final class NetworkCodec {

    private NetworkCodec() {}

    public static void write(Network net, JsonWriter w) {
        w.beginObject();

        w.name("id").value(net.id.toString());

        w.name("viewport").beginObject()
                .name("x").value(net.viewport.x)
                .name("y").value(net.viewport.y)
                .name("zoom").value(net.viewport.zoom)
                .endObject();

        w.name("nodes").beginArray();
        for (Node n : net.nodes()) writeNode(n, w);
        w.endArray();

        w.name("edges").beginArray();
        for (Edge e : net.edges()) writeEdge(e, w);
        w.endArray();

        w.endObject();
    }

    /**
     * Read a network from JSON, resolving kind ids via {@code kinds}. Nodes whose
     * {@code kind} is unknown to the registry are dropped — the loader logs the issue but
     * keeps going, matching the design-doc rule that a partially-incompatible archive opens
     * read-only rather than refusing entirely.
     */
    public static Network read(JsonReader r, KindRegistry kinds) {
        UUID id = null;
        float vx = 0, vy = 0, vzoom = 1f;
        // Build a "pre-network" — collect nodes + edges as raw structs first, then assemble.
        java.util.List<RawNode> rawNodes = new java.util.ArrayList<>();
        java.util.List<RawEdge> rawEdges = new java.util.ArrayList<>();

        r.beginObject();
        while (r.hasNext()) {
            String key = r.nextName();
            switch (key) {
                case "id" -> {
                    try { id = UUID.fromString(r.nextString()); }
                    catch (IllegalArgumentException ex) { /* leave id null → fresh UUID below */ }
                }
                case "viewport" -> {
                    r.beginObject();
                    while (r.hasNext()) {
                        switch (r.nextName()) {
                            case "x"    -> vx    = (float) r.nextDouble();
                            case "y"    -> vy    = (float) r.nextDouble();
                            case "zoom" -> vzoom = (float) r.nextDouble();
                            default     -> r.skipValue();
                        }
                    }
                    r.endObject();
                }
                case "nodes" -> {
                    r.beginArray();
                    while (r.hasNext()) rawNodes.add(readRawNode(r));
                    r.endArray();
                }
                case "edges" -> {
                    r.beginArray();
                    while (r.hasNext()) rawEdges.add(readRawEdge(r));
                    r.endArray();
                }
                default -> r.skipValue();
            }
        }
        r.endObject();

        Network net = new Network(id != null ? id : UUID.randomUUID(), kinds);
        net.viewport.set(vx, vy, vzoom);

        // Populate nodes preserving their original UUIDs so edges resolve. Skip any whose
        // kind isn't registered — this lets a workspace open a file that references kinds it
        // doesn't currently know about (the user can re-add them later).
        for (RawNode rn : rawNodes) {
            NodeKind k = kinds.get(rn.kind);
            if (k == null) continue;
            Node node = net.addNodeWithId(k, rn.id, rn.x, rn.y);
            node.collapse = rn.collapse;
            node.state.clear();
            node.state.putAll(rn.state);
        }
        // Populate edges — skip any whose endpoints are missing.
        for (RawEdge re : rawEdges) {
            if (net.node(re.fromNode) == null || net.node(re.toNode) == null) continue;
            net.connect(new PortRef(re.fromNode, re.fromPort),
                        new PortRef(re.toNode,   re.toPort));
        }
        return net;
    }

    // ─── Per-element write/read helpers ────────────────────────────────────

    private static void writeNode(Node n, JsonWriter w) {
        w.beginObject()
                .name("id").value(n.id.toString())
                .name("kind").value(n.kind)
                .name("x").value(n.positionX)
                .name("y").value(n.positionY)
                .name("collapse").value(n.collapse.name());

        w.name("state").beginObject();
        for (Map.Entry<String, Value> e : n.state.entrySet()) {
            w.name(e.getKey());
            ValueCodec.write(e.getValue(), w);
        }
        w.endObject();

        w.endObject();
    }

    private static void writeEdge(Edge e, JsonWriter w) {
        w.beginObject()
                .name("from").beginObject()
                    .name("node").value(e.from().nodeId().toString())
                    .name("port").value(e.from().portName())
                .endObject()
                .name("to").beginObject()
                    .name("node").value(e.to().nodeId().toString())
                    .name("port").value(e.to().portName())
                .endObject()
                .endObject();
    }

    private static RawNode readRawNode(JsonReader r) {
        UUID id = null;
        String kind = "";
        float x = 0, y = 0;
        CollapseMode collapse = CollapseMode.AUTO;
        Map<String, Value> state = new java.util.LinkedHashMap<>();

        r.beginObject();
        while (r.hasNext()) {
            switch (r.nextName()) {
                case "id"   -> id   = parseUUID(r.nextString());
                case "kind" -> kind = r.nextString();
                case "x"    -> x    = (float) r.nextDouble();
                case "y"    -> y    = (float) r.nextDouble();
                case "collapse" -> {
                    String s = r.nextString();
                    try { collapse = CollapseMode.valueOf(s); }
                    catch (IllegalArgumentException ex) { collapse = CollapseMode.AUTO; }
                }
                case "state" -> {
                    r.beginObject();
                    while (r.hasNext()) {
                        String k = r.nextName();
                        state.put(k, ValueCodec.read(r));
                    }
                    r.endObject();
                }
                default -> r.skipValue();
            }
        }
        r.endObject();
        return new RawNode(id != null ? id : UUID.randomUUID(), kind, x, y, collapse, state);
    }

    private static RawEdge readRawEdge(JsonReader r) {
        UUID fromNode = null;
        String fromPort = "";
        UUID toNode = null;
        String toPort = "";

        r.beginObject();
        while (r.hasNext()) {
            switch (r.nextName()) {
                case "from" -> {
                    r.beginObject();
                    while (r.hasNext()) {
                        switch (r.nextName()) {
                            case "node" -> fromNode = parseUUID(r.nextString());
                            case "port" -> fromPort = r.nextString();
                            default     -> r.skipValue();
                        }
                    }
                    r.endObject();
                }
                case "to" -> {
                    r.beginObject();
                    while (r.hasNext()) {
                        switch (r.nextName()) {
                            case "node" -> toNode = parseUUID(r.nextString());
                            case "port" -> toPort = r.nextString();
                            default     -> r.skipValue();
                        }
                    }
                    r.endObject();
                }
                default -> r.skipValue();
            }
        }
        r.endObject();
        return new RawEdge(fromNode, fromPort, toNode, toPort);
    }

    private static UUID parseUUID(String s) {
        try { return UUID.fromString(s); }
        catch (IllegalArgumentException ex) { return UUID.randomUUID(); }
    }

    // ─── Internal raw shapes ───────────────────────────────────────────────

    private static final class RawNode {
        final UUID id;
        final String kind;
        final float x, y;
        final CollapseMode collapse;
        final Map<String, Value> state;
        RawNode(UUID id, String kind, float x, float y, CollapseMode c, Map<String, Value> s) {
            this.id = id; this.kind = kind; this.x = x; this.y = y;
            this.collapse = c; this.state = s;
        }
    }

    private static final class RawEdge {
        final UUID fromNode;
        final String fromPort;
        final UUID toNode;
        final String toPort;
        RawEdge(UUID fromNode, String fromPort, UUID toNode, String toPort) {
            this.fromNode = fromNode; this.fromPort = fromPort;
            this.toNode = toNode; this.toPort = toPort;
        }
    }
}
