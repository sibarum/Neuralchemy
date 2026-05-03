package lab.ui.screens;

import com.raylib.Canvas;
import com.raylib.Color;
import com.raylib.Keys;
import com.raylib.MouseButtons;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lab.dsl.Type;
import lab.graph.BuiltinKinds;
import lab.graph.CollapseMode;
import lab.graph.ConnectionResult;
import lab.graph.DslKinds;
import lab.graph.Edge;
import lab.graph.KindRegistry;
import lab.graph.Network;
import lab.graph.Node;
import lab.graph.NodeKind;
import lab.graph.PortRef;
import lab.graph.PortSpec;
import lab.graph.Value;
import lab.graph.eval.DslResolver;
import lab.graph.eval.Evaluator;
import lab.ui.AppContext;
import lab.ui.RayGui;
import lab.ui.Screen;
import lab.ui.Theme;
import lab.ui.miniviz.Colormap;
import lab.ui.miniviz.MiniViz;
import lab.ui.miniviz.ProbeFieldSampler;
import lab.ui.miniviz.ScalarField;
import lab.ui.workspace.DslFile;

/**
 * Node-graph editor — backed by a real {@link Network} from {@code na-graph}. Renders nodes
 * with port circles, draws edges through {@link Network#edges()}, and offers two interactions:
 *
 * <ul>
 *   <li>Drag a node body to move it. Position lives on {@link Node} and survives across navigations.</li>
 *   <li>Drag from an output-port circle to an input-port circle to call {@link Network#connect}.
 *       The wire-in-progress is colored green or red based on the live {@link ConnectionResult}
 *       so the user gets type/cycle feedback before they release.</li>
 * </ul>
 *
 * <p>Demo network on first entry: 4 sliders feeding the workspace's {@code dsl.neuron.split_mix}
 * neuron, with two outputs hanging off it. The DSL kind is registered in
 * {@link lab.ui.workspace.Workspace#kinds}, which is shared with the DSL editor.
 */
public final class NetworkScreen implements Screen {

    private static final int   NODE_W           = 200;
    private static final int   PROBE_VIZ_H      = 160;
    private static final int   PROBE_RES        = 48;
    private static final int   INLINE_SLIDER_H  = 22;     // per-port extra height when inlined
    private static final int   CHEVRON_W        = 14;     // expand-back-out button width
    private static final float PORT_RADIUS      = 6f;
    private static final float PORT_HIT_R       = 10f;

    private Network net;
    private Evaluator evaluator;
    private Map<PortRef, Value> portValues = Map.of();
    private boolean demoBuilt;

    /** One MiniViz per {@code probe.field} node currently in the network. Texture-backed; closed on exit / removal. */
    private final Map<UUID, ProbeViz> probeVizes = new HashMap<>();

    /**
     * Per-frame collapse decision. Recomputed at the top of {@link #render}. Two views:
     * {@link #inlinedAtPort} maps a consumer's input-port reference to the leaf node that
     * should render inline at that port; {@link #inlinedNodes} is the set of leaf node ids
     * to skip during the standalone-node draw pass.
     */
    private Map<PortRef, Node> inlinedAtPort = Map.of();
    private Set<UUID>          inlinedNodes  = Set.of();

    /** Cached for layout helpers (port positions need font sizes; UI scale changes them). */
    private AppContext currentCtx;

    private NodeDrag draggingNode;
    private WireDrag draggingWire;

    @Override public String id()    { return "screen.network"; }
    @Override public String title() { return "Network"; }

    @Override
    public void onEnter(AppContext ctx) {
        if (net == null) {
            net = new Network(ctx.workspace.kinds);
        }
        if (!demoBuilt) {
            buildDemoIfPossible(ctx);
        }
    }

    @Override
    public void onExit(AppContext ctx) {
        // GL context still alive here — release every probe's texture.
        for (ProbeViz pv : probeVizes.values()) pv.viz.close();
        probeVizes.clear();
    }

    /** Drop a kind at the visible center of the canvas. Used by palette Insert actions. */
    public void insertAtCenter(AppContext ctx, String kindId) {
        ensureNet(ctx);
        NodeKind k = ctx.workspace.kinds.get(kindId);
        if (k == null) return;
        float cx = ctx.contentX + ctx.contentW / 2f - NODE_W / 2f;
        float cy = ctx.contentY + ctx.contentH / 2f - 60f;
        net.addNode(k, cx, cy);
    }

    /** The current in-memory network. Used by Project.save in App. {@code null} before first entry. */
    public Network currentNetwork() { return net; }

    /**
     * Replace the in-memory network (e.g. on Project: Open). Resets evaluator + drag state and
     * suppresses the demo-build-on-first-entry path so the loaded network isn't overwritten.
     */
    public void replaceNetwork(Network newNet) {
        closeAllProbes();
        this.net = newNet;
        this.evaluator = null;
        this.demoBuilt = true;
        this.draggingNode = null;
        this.draggingWire = null;
        this.portValues = java.util.Map.of();
    }

    /** Discard the current network and rebuild the demo. Used by Project: New. */
    public void resetToDemo(AppContext ctx) {
        closeAllProbes();
        this.net = new Network(ctx.workspace.kinds);
        this.evaluator = null;
        this.demoBuilt = false;
        this.draggingNode = null;
        this.draggingWire = null;
        this.portValues = java.util.Map.of();
        buildDemoIfPossible(ctx);
    }

    private void closeAllProbes() {
        for (ProbeViz pv : probeVizes.values()) pv.viz.close();
        probeVizes.clear();
    }

    @Override
    public void render(AppContext ctx, Canvas c) {
        ensureNet(ctx);
        currentCtx = ctx;

        // One forward pass per frame — values flow from sliders through DSL kinds to outputs.
        portValues = evaluator.step();

        // Decide which slider leaves are inlined into their consumer nodes for this frame.
        recomputeInlinedSliders();

        // GC probe vizes whose nodes were removed.
        for (Iterator<Map.Entry<UUID, ProbeViz>> it = probeVizes.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, ProbeViz> e = it.next();
            if (net.node(e.getKey()) == null) {
                e.getValue().viz.close();
                it.remove();
            }
        }

        int x = ctx.contentX, y = ctx.contentY, w = ctx.contentW, h = ctx.contentH;
        c.fillRect(x, y, w, h, Theme.SURFACE);
        c.drawRect(x, y, w, h, Theme.FRAME);
        drawDotGrid(c, x, y, w, h);

        // Draw committed edges first (under nodes, over background). Skip wires whose source
        // is currently inlined — the leaf is rendered inside the consumer instead.
        for (Edge e : net.edges()) {
            if (inlinedNodes.contains(e.from().nodeId())) continue;
            drawEdge(c, e);
        }

        // Wire-in-progress (above edges, under nodes is fine since nodes are dense).
        if (draggingWire != null) {
            drawWireInProgress(ctx, c);
        }

        // Nodes (above edges). Inlined leaves are drawn inside their consumers — skip here.
        for (Node n : net.nodes()) {
            if (inlinedNodes.contains(n.id)) continue;
            drawNode(ctx, c, n);
        }

        // Input handling (after rendering — port positions are deterministic from node pos).
        if (!ctx.paletteOpen) handleInput(ctx);

        // Status strip.
        drawStatus(ctx, c, x, y, w, h);
    }

    // ─── Collapse decision ──────────────────────────────────────────────────

    /**
     * Per node-graph.md §"Single-port leaf collapse": a slider with one outgoing edge whose
     * collapse mode isn't {@link CollapseMode#EXPANDED} renders inline at its consumer's input
     * port. Recomputed each frame (cheap — linear in nodes + edges).
     *
     * <p>Future work: extend to other zero-input source kinds (constants, inputs) once we have
     * inline widgets for them. The current scope covers the slider-feeding-neuron case which is
     * 80% of the visual noise on the canvas today.
     */
    private void recomputeInlinedSliders() {
        Map<PortRef, Node> atPort = new HashMap<>();
        Set<UUID> hidden = new HashSet<>();

        for (Node n : net.nodes()) {
            if (!BuiltinKinds.SLIDER.equals(n.kind))    continue;
            if (n.collapse == CollapseMode.EXPANDED)    continue;
            // Slider has one output ("value") — find its outgoing edges.
            List<Edge> outgoing = net.outgoingFrom(n.id);
            if (outgoing.size() != 1) continue;     // 0 → orphan; ≥2 → inlining loses ambiguity
            Edge wire = outgoing.get(0);
            // Don't inline into another inlined leaf — keeps the rendering shallow for v0.
            if (hidden.contains(wire.to().nodeId())) continue;
            atPort.put(wire.to(), n);
            hidden.add(n.id);
        }

        this.inlinedAtPort = atPort;
        this.inlinedNodes  = hidden;
    }

    // ─── Demo network ──────────────────────────────────────────────────────

    private void ensureNet(AppContext ctx) {
        if (net == null) {
            net = new Network(ctx.workspace.kinds);
            evaluator = new Evaluator(net, makeDslResolver(ctx));
        }
        if (evaluator == null) {
            evaluator = new Evaluator(net, makeDslResolver(ctx));
        }
    }

    /** Resolver that walks the workspace's parsed artifacts looking for the requested kind id. */
    private static DslResolver makeDslResolver(AppContext ctx) {
        return id -> {
            for (DslFile f : ctx.workspace.files()) {
                if (f.artifact() != null && DslKinds.idFor(f.artifact()).equals(id)) {
                    return f.artifact();
                }
            }
            return null;
        };
    }

    /**
     * Build a demo network using the workspace's split_mix neuron if present. If the DSL kind
     * isn't available (workspace not loaded yet, or split_mix failed to parse), build a smaller
     * scalar add-and-output demo from built-ins so the screen always has something on it.
     */
    private void buildDemoIfPossible(AppContext ctx) {
        demoBuilt = true;
        KindRegistry kinds = ctx.workspace.kinds;
        NodeKind splitMix = kinds.get("dsl.neuron.split_mix");
        NodeKind slider   = kinds.get(BuiltinKinds.SLIDER);
        NodeKind output   = kinds.get(BuiltinKinds.OUTPUT_S);

        float left   = ctx.contentX + 80;
        float midX   = ctx.contentX + ctx.contentW / 2f - NODE_W / 2f;
        float rightX = ctx.contentX + ctx.contentW - NODE_W - 80;
        float top    = ctx.contentY + 80;

        if (splitMix == null) {
            // Fallback: slider + slider → add → output
            NodeKind add = kinds.get(BuiltinKinds.ADD_S);
            Node a = net.addNode(slider, left,   top);
            Node b = net.addNode(slider, left,   top + 100);
            Node addNode = net.addNode(add, midX, top + 30);
            Node out = net.addNode(output, rightX, top + 50);
            net.connect(new PortRef(a.id, "value"), new PortRef(addNode.id, "a"));
            net.connect(new PortRef(b.id, "value"), new PortRef(addNode.id, "b"));
            net.connect(new PortRef(addNode.id, "sum"), new PortRef(out.id, "value"));
            return;
        }

        // Full demo: 4 sliders → split_mix → 2 outputs + a probe sweeping (x, y) over mix.u.
        NodeKind probe = kinds.get(BuiltinKinds.PROBE_FIELD);
        Node sx = net.addNode(slider, left, top);
        Node sy = net.addNode(slider, left, top + 80);
        Node sa = net.addNode(slider, left, top + 160);
        Node sb = net.addNode(slider, left, top + 240);
        Node mix = net.addNode(splitMix, midX, top + 60);
        Node ou  = net.addNode(output, rightX, top + 60);
        Node ov  = net.addNode(output, rightX, top + 160);

        net.connect(new PortRef(sx.id, "value"), new PortRef(mix.id, "x"));
        net.connect(new PortRef(sy.id, "value"), new PortRef(mix.id, "y"));
        net.connect(new PortRef(sa.id, "value"), new PortRef(mix.id, "a"));
        net.connect(new PortRef(sb.id, "value"), new PortRef(mix.id, "b"));
        net.connect(new PortRef(mix.id, "u"), new PortRef(ou.id, "value"));
        net.connect(new PortRef(mix.id, "v"), new PortRef(ov.id, "value"));

        if (probe != null) {
            // Drop the probe under mix and sweep its 'u' output across (x, y) — the auto-axis
            // resolver picks up sx and sy as the first two source nodes upstream of mix.u.
            Node pu = net.addNode(probe, midX, top + 280);
            net.connect(new PortRef(mix.id, "u"), new PortRef(pu.id, "value"));
        }
    }

    // ─── Rendering ──────────────────────────────────────────────────────────

    private void drawNode(AppContext ctx, Canvas c, Node n) {
        NodeKind kind = net.kindOf(n);
        if (kind == null) return;
        int nx = (int) n.positionX;
        int ny = (int) n.positionY;
        int nw = NODE_W;
        int nh = nodeHeight(ctx, n);

        // Body + border.
        Color tint = tintFor(kind);
        c.fillRect(nx, ny, nw, nh, tint);
        c.drawRect(nx, ny, nw, nh, Theme.FRAME_HARD);

        // Header strip.
        int headerH = ctx.font.size + ctx.fontSmall.size + 10;
        c.drawLine(nx, ny + headerH, nx + nw, ny + headerH, Theme.FRAME);
        ctx.fontSmall.draw(kindShortLabel(kind), nx + 10, ny + 4, Theme.INK_DIM);
        ctx.font.draw(displayName(kind, n), nx + 10, ny + 4 + ctx.fontSmall.size + 2, Theme.INK);

        int portRowH = ctx.fontSmall.size + 8;
        int portTop  = ny + headerH + 6;

        // Inputs: variable-height rows when an inlined slider widget needs space below the label.
        int rowY = portTop;
        for (PortSpec p : kind.inputs()) {
            int rowH = inputRowHeight(n, p, portRowH);
            int py = rowY + portRowH / 2;       // port circle aligns with the LABEL line
            Edge incoming = incomingTo(n.id, p.name());
            Node leaf = inlinedAtPort.get(new PortRef(n.id, p.name()));
            boolean wired = incoming != null || leaf != null;

            drawPortDot(c, nx, py, p.type(), wired);

            String label = p.name();
            if (leaf != null) {
                // Inlined slider: show its current value, no need to look up portValues.
                Value v = leaf.state.get("value");
                if (v != null) label = p.name() + " = " + formatValue(v);
            } else if (incoming != null) {
                Value v = portValues.get(incoming.from());
                if (v != null) label = p.name() + " = " + formatValue(v);
            }
            ctx.fontSmall.draw(label, nx + 14, py - ctx.fontSmall.size / 2, Theme.INK);

            // The inlined slider widget + chevron sit on the row's second line.
            if (leaf != null) {
                drawInlinedSlider(ctx, c, leaf, nx, rowY + portRowH, nw);
                drawExpandChevron(ctx, c, nx + nw - CHEVRON_W - 6, rowY + 2);
            }

            rowY += rowH;
        }
        for (PortSpec p : kind.outputs()) {
            int py = portTop + p.index() * portRowH + portRowH / 2;
            drawPortDot(c, nx + nw, py, p.type(), false);
            String label = p.name();
            Value v = portValues.get(new PortRef(n.id, p.name()));
            if (v != null) label = p.name() + " = " + formatValue(v);
            int tw = ctx.fontSmall.measure(label);
            ctx.fontSmall.draw(label, nx + nw - 14 - tw, py - ctx.fontSmall.size / 2, Theme.INK);
        }

        // Inline slider widget for builtin.slider — drives state.value, picked up next forward pass.
        if (BuiltinKinds.SLIDER.equals(kind.id())) {
            drawInlineSlider(n, nx, ny, nw, nh);
        }

        // Embedded MiniViz for probe.field — sweeps the upstream subgraph as a scalar field.
        if (BuiltinKinds.PROBE_FIELD.equals(kind.id())) {
            drawProbeViz(ctx, c, n, nx, ny, nw, nh);
        }
    }

    /**
     * Render the inlined-leaf slider widget at the second line of an input port row. Mutates
     * the source slider's {@code state.value} on drag — same path as the standalone widget.
     */
    private static void drawInlinedSlider(AppContext ctx, Canvas c, Node leaf,
                                          int nx, int rowSecondLineY, int nw) {
        Value vMin = leaf.state.get("min");
        Value vMax = leaf.state.get("max");
        Value vVal = leaf.state.get("value");
        if (!(vMin instanceof Value.Scalar minS)
                || !(vMax instanceof Value.Scalar maxS)
                || !(vVal instanceof Value.Scalar valS)) {
            return;
        }
        // Leave room for the chevron at the right edge of the row above; slider stretches
        // most of the body width.
        int sx = nx + 16;
        int sy = rowSecondLineY;
        int sw = nw - 32;
        int sh = INLINE_SLIDER_H - 4;
        float[] ref = { valS.v() };
        if (RayGui.slider(sx, sy, sw, sh, "", String.format("%+.2f", valS.v()),
                          ref, minS.v(), maxS.v())) {
            leaf.state.put("value", new Value.Scalar(ref[0]));
        }
    }

    /** Tiny "↗" chevron drawn as a triangle. Hit-tested in {@link #handleInput}. */
    private static void drawExpandChevron(AppContext ctx, Canvas c, int x, int y) {
        c.fillRect(x, y, CHEVRON_W, CHEVRON_W, Theme.PANEL);
        c.drawRect(x, y, CHEVRON_W, CHEVRON_W, Theme.FRAME);
        // Diagonal line from bottom-left to top-right + arrow at top-right.
        c.drawLine(x + 3,  y + CHEVRON_W - 3, x + CHEVRON_W - 3, y + 3, Theme.INK);
        c.drawLine(x + CHEVRON_W - 6, y + 3, x + CHEVRON_W - 3, y + 3, Theme.INK);
        c.drawLine(x + CHEVRON_W - 3, y + 3, x + CHEVRON_W - 3, y + 6, Theme.INK);
    }

    /**
     * Walk every consumer node that hosts an inlined leaf; if the cursor is over one of the
     * expand-chevron rects, pop the leaf back out and return {@code true} (consumes the click).
     */
    private boolean clickedExpandChevron(AppContext ctx) {
        if (currentCtx == null) return false;
        int portRowH = currentCtx.fontSmall.size + 8;
        int headerH  = currentCtx.font.size + currentCtx.fontSmall.size + 10;

        for (Map.Entry<PortRef, Node> e : inlinedAtPort.entrySet()) {
            UUID consumerId = e.getKey().nodeId();
            Node consumer = net.node(consumerId);
            if (consumer == null) continue;
            NodeKind k = net.kindOf(consumer);
            if (k == null) continue;

            int nx = (int) consumer.positionX;
            int portTop = (int) consumer.positionY + headerH + 6;
            int rowY = portTop;
            for (PortSpec p : k.inputs()) {
                if (p.name().equals(e.getKey().portName())) {
                    int cx = nx + NODE_W - CHEVRON_W - 6;
                    int cy = rowY + 2;
                    if (ctx.mouseX >= cx && ctx.mouseX <= cx + CHEVRON_W
                            && ctx.mouseY >= cy && ctx.mouseY <= cy + CHEVRON_W) {
                        e.getValue().collapse = CollapseMode.EXPANDED;
                        return true;
                    }
                    break;
                }
                rowY += inputRowHeight(consumer, p, portRowH);
            }
        }
        return false;
    }

    private static void drawInlineSlider(Node n, int nx, int ny, int nw, int nh) {
        int sx = nx + 10;
        int sy = ny + nh - 26;
        int sw = nw - 20;
        int sh = 18;

        Value vMin = n.state.get("min");
        Value vMax = n.state.get("max");
        Value vVal = n.state.get("value");
        if (!(vMin instanceof Value.Scalar minS)
                || !(vMax instanceof Value.Scalar maxS)
                || !(vVal instanceof Value.Scalar valS)) {
            return;
        }
        float[] ref = { valS.v() };
        if (RayGui.slider(sx, sy, sw, sh, "", String.format("%+.2f", valS.v()),
                          ref, minS.v(), maxS.v())) {
            n.state.put("value", new Value.Scalar(ref[0]));
        }
    }

    private static void drawPortDot(Canvas c, int px, int py, Type type, boolean wired) {
        Color fill = wired ? Theme.ACCENT : portColor(type);
        c.fillCircle(px, py, PORT_RADIUS, fill);
        c.drawCircle(px, py, PORT_RADIUS, Theme.FRAME_HARD);
    }

    private void drawEdge(Canvas c, Edge e) {
        int[] from = portPosition(e.from(), false);
        int[] to   = portPosition(e.to(),   true);
        if (from == null || to == null) return;
        drawWirePath(c, from[0], from[1], to[0], to[1], Theme.FRAME_HARD);
    }

    private void drawWireInProgress(AppContext ctx, Canvas c) {
        WireDrag d = draggingWire;
        Color color = d.previewColor;
        drawWirePath(c, (int) d.startX, (int) d.startY, ctx.mouseX, ctx.mouseY, color);
    }

    /** Three-segment routing: horizontal, vertical, horizontal. Cheap and readable. */
    private static void drawWirePath(Canvas c, int sx, int sy, int dx, int dy, Color color) {
        int midX = (sx + dx) / 2;
        c.drawLine(sx,   sy, midX, sy, color);
        c.drawLine(midX, sy, midX, dy, color);
        c.drawLine(midX, dy, dx,   dy, color);
    }

    private void drawStatus(AppContext ctx, Canvas c, int x, int y, int w, int h) {
        int errors = net.validate().size();
        boolean runnable = errors == 0;
        String status = runnable
                ? net.nodeCount() + " nodes · " + net.edgeCount() + " wires · runnable"
                : net.nodeCount() + " nodes · " + net.edgeCount() + " wires · "
                        + errors + " unwired required port" + (errors == 1 ? "" : "s");
        ctx.fontSmall.draw(status, x + 12, y + h - 22, Theme.INK_DIM);

        int badgeW = 140;
        c.fillRect(x + w - badgeW - 12, y + h - 30, badgeW, 22,
                runnable ? Theme.ACCENT_SOFT : new Color(252, 232, 232, 255));
        ctx.fontSmall.draw(runnable ? "✓ runnable" : "! incomplete",
                x + w - badgeW - 4, y + h - 26,
                runnable ? Theme.OK : Theme.ERROR_RED);
    }

    private static void drawDotGrid(Canvas c, int x, int y, int w, int h) {
        int step = 20;
        for (int gy = y + step; gy < y + h; gy += step) {
            for (int gx = x + step; gx < x + w; gx += step) {
                c.drawPixel(gx, gy, Theme.FRAME);
            }
        }
    }

    // ─── Input handling ────────────────────────────────────────────────────

    private void handleInput(AppContext ctx) {
        boolean leftPressed  = ctx.window.isMouseButtonPressed(MouseButtons.LEFT);
        boolean leftDown     = ctx.window.isMouseButtonDown(MouseButtons.LEFT);
        boolean leftReleased = ctx.window.isMouseButtonReleased(MouseButtons.LEFT);

        // Press: pick port first, then node body. In either case bring the node under the
        // cursor (if any) to the top of the render order so subsequent frames draw it last.
        if (leftPressed && draggingWire == null && draggingNode == null) {
            // Inlined-leaf chevron takes priority — clicking it pops the leaf back to a
            // standalone canvas node and consumes the click.
            if (clickedExpandChevron(ctx)) return;

            PortHit ph = portUnderCursor(ctx, /*outputsOnly=*/true);
            if (ph != null) {
                Node n = net.node(ph.nodeId);
                net.bringToFront(n.id);
                int[] pos = portPosition(new PortRef(ph.nodeId, ph.portName), false);
                draggingWire = new WireDrag(n.id, ph.portName, pos[0], pos[1], Theme.ACCENT);
            } else {
                Node n = nodeUnderCursor(ctx);
                if (n != null) {
                    net.bringToFront(n.id);
                    if (!isOnInlineWidget(ctx, n, net.kindOf(n))) {
                        draggingNode = new NodeDrag(n, ctx.mouseX - n.positionX, ctx.mouseY - n.positionY);
                    }
                }
            }
        }

        // Hold: update active drag.
        if (leftDown) {
            if (draggingNode != null) {
                draggingNode.node.positionX = ctx.mouseX - draggingNode.offsetX;
                draggingNode.node.positionY = ctx.mouseY - draggingNode.offsetY;
            }
            if (draggingWire != null) {
                // Refresh preview color based on what's under the cursor.
                PortHit ph = portUnderCursor(ctx, /*outputsOnly=*/false);
                if (ph != null && net.kindOf(net.node(ph.nodeId)).input(ph.portName) != null) {
                    ConnectionResult r = net.canConnect(
                            new PortRef(draggingWire.sourceNodeId, draggingWire.sourcePortName),
                            new PortRef(ph.nodeId, ph.portName));
                    draggingWire.previewColor = r.ok() ? Theme.OK : Theme.ERROR_RED;
                } else {
                    draggingWire.previewColor = Theme.ACCENT;
                }
            }
        }

        // Release: commit wire if hovering a compatible input port.
        if (leftReleased) {
            if (draggingWire != null) {
                PortHit ph = portUnderCursor(ctx, /*outputsOnly=*/false);
                if (ph != null) {
                    NodeKind k = net.kindOf(net.node(ph.nodeId));
                    if (k != null && k.input(ph.portName) != null) {
                        net.connect(
                                new PortRef(draggingWire.sourceNodeId, draggingWire.sourcePortName),
                                new PortRef(ph.nodeId, ph.portName));
                    }
                }
                draggingWire = null;
            }
            draggingNode = null;
        }

        // Delete pressed: remove the node currently under the cursor (if any).
        if (ctx.window.isKeyPressed(Keys.DELETE) && draggingNode == null && draggingWire == null) {
            Node n = nodeUnderCursor(ctx);
            if (n != null) net.removeNode(n.id);
        }
    }

    // ─── Hit-testing helpers ────────────────────────────────────────────────

    private Node nodeUnderCursor(AppContext ctx) {
        // Iterate in reverse render order so topmost-drawn node wins. Skip inlined leaves —
        // they don't have their own canvas region.
        List<Node> nodes = net.nodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            if (inlinedNodes.contains(n.id)) continue;
            NodeKind k = net.kindOf(n);
            if (k == null) continue;
            int nh = nodeHeight(ctx, n);
            if (ctx.mouseX >= n.positionX && ctx.mouseX <= n.positionX + NODE_W
                    && ctx.mouseY >= n.positionY && ctx.mouseY <= n.positionY + nh) {
                return n;
            }
        }
        return null;
    }

    /** Find the port whose hit circle the cursor is inside. */
    private PortHit portUnderCursor(AppContext ctx, boolean outputsOnly) {
        List<Node> nodes = net.nodes();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node n = nodes.get(i);
            if (inlinedNodes.contains(n.id)) continue;
            NodeKind k = net.kindOf(n);
            if (k == null) continue;

            if (!outputsOnly) {
                for (PortSpec p : k.inputs()) {
                    int[] pos = portPosition(new PortRef(n.id, p.name()), true);
                    if (within(ctx.mouseX, ctx.mouseY, pos[0], pos[1], PORT_HIT_R)) {
                        return new PortHit(n.id, p.name());
                    }
                }
            }
            for (PortSpec p : k.outputs()) {
                int[] pos = portPosition(new PortRef(n.id, p.name()), false);
                if (within(ctx.mouseX, ctx.mouseY, pos[0], pos[1], PORT_HIT_R)) {
                    return new PortHit(n.id, p.name());
                }
            }
        }
        return null;
    }

    /**
     * Resolve a {@link PortRef} to its on-screen position. Uses {@link #currentCtx} for the
     * font sizes that drive header / port-row heights, so port positions stay aligned with
     * what {@link #drawNode} draws under any UI scale.
     */
    private int[] portPosition(PortRef ref, boolean isInput) {
        Node n = net.node(ref.nodeId());
        if (n == null) return null;
        NodeKind k = net.kindOf(n);
        if (k == null) return null;
        PortSpec p = isInput ? k.input(ref.portName()) : k.output(ref.portName());
        if (p == null) return null;

        int fontSize      = currentCtx != null ? currentCtx.font.size      : 18;
        int fontSmallSize = currentCtx != null ? currentCtx.fontSmall.size : 14;
        int headerH = fontSize + fontSmallSize + 10;
        int portRowH = fontSmallSize + 8;
        int portTop = (int) n.positionY + headerH + 6;

        int py;
        if (isInput) {
            // Inputs may have variable row heights when an inlined slider is below the label.
            int rowY = portTop;
            for (PortSpec ip : k.inputs()) {
                if (ip.name().equals(p.name())) break;
                rowY += inputRowHeight(n, ip, portRowH);
            }
            py = rowY + portRowH / 2;
        } else {
            py = portTop + p.index() * portRowH + portRowH / 2;
        }
        int px = isInput ? (int) n.positionX : (int) n.positionX + NODE_W;
        return new int[] { px, py };
    }

    private Edge incomingTo(UUID nodeId, String portName) {
        for (Edge e : net.edges()) {
            if (e.to().nodeId().equals(nodeId) && e.to().portName().equals(portName)) return e;
        }
        return null;
    }

    private static boolean within(int mx, int my, int px, int py, float radius) {
        float dx = mx - px;
        float dy = my - py;
        return dx * dx + dy * dy <= radius * radius;
    }

    /** Height including any inlined-leaf widgets for this specific node. */
    private int nodeHeight(AppContext ctx, Node n) {
        NodeKind k = net.kindOf(n);
        if (k == null) return 0;
        int headerH = ctx.font.size + ctx.fontSmall.size + 10;
        int portRowH = ctx.fontSmall.size + 8;
        int inputsH  = totalInputRowsHeight(n, k, portRowH);
        int outputsH = k.outputs().size() * portRowH;
        int rowsH = Math.max(inputsH, outputsH);
        if (rowsH == 0) rowsH = portRowH;       // header-only nodes still need a body strip
        int base = headerH + rowsH + 12;
        if (BuiltinKinds.SLIDER.equals(k.id()))      base += 30;     // inline raygui slider
        if (BuiltinKinds.PROBE_FIELD.equals(k.id())) base += PROBE_VIZ_H + 8;
        return base;
    }

    /** Sum of per-input row heights, accounting for inlined-slider expansion. */
    private int totalInputRowsHeight(Node n, NodeKind k, int portRowH) {
        int total = 0;
        for (PortSpec p : k.inputs()) total += inputRowHeight(n, p, portRowH);
        return total;
    }

    private int inputRowHeight(Node n, PortSpec p, int portRowH) {
        if (n != null && inlinedAtPort.containsKey(new PortRef(n.id, p.name()))) {
            return portRowH + INLINE_SLIDER_H;
        }
        return portRowH;
    }

    /**
     * Render the embedded mini-viz inside a probe.field node. Lazily builds the {@link MiniViz}
     * (allocates a GPU texture on first render) and re-positions it at {@code (nx, ny, nw, nh)}
     * each frame so it tracks the node as it's dragged.
     *
     * <p>If the probe isn't wired or its upstream subgraph has fewer than two source nodes, the
     * mini-viz still renders — the {@link ProbeFieldSampler} returns 0 for every sample so the
     * field shows as a neutral mid-tone, and the title is annotated.
     */
    private void drawProbeViz(AppContext ctx, com.raylib.Canvas c, Node probe,
                              int nx, int ny, int nw, int nh) {
        ProbeViz panel = probeVizes.get(probe.id);
        if (panel == null) {
            ProbeFieldSampler sampler = new ProbeFieldSampler(net, probe.id, evaluator.dslResolver());
            ScalarField field = new ScalarField(sampler, Colormap.MAGENTA_CYAN, PROBE_RES, PROBE_RES)
                    .range(-1f, 1f);
            MiniViz viz = new MiniViz(0, 0, 0, 0, "probe field")
                    .worldRect(-2.5f, -2.5f, 2.5f, 2.5f)
                    .add(field);
            viz.showTitle  = false;     // host node already has its own title bar
            viz.showStatus = false;     // saves vertical space
            panel = new ProbeViz(viz, sampler);
            probeVizes.put(probe.id, panel);
        }

        // Inset under the port row + below the (single) input port label.
        int headerH  = ctx.font.size + ctx.fontSmall.size + 10;
        int portRowH = ctx.fontSmall.size + 8;
        int vizX = nx + 8;
        int vizY = ny + headerH + portRowH + 2;
        int vizW = nw - 16;
        int vizH = nh - (vizY - ny) - 8;
        panel.viz.rect(vizX, vizY, vizW, vizH).render(ctx, c);

        // If the probe isn't sweep-able, write a hint over the canvas.
        if (!panel.sampler.canSweep()) {
            String hint = "wire input + need ≥2 source nodes";
            int tw = ctx.fontSmall.measure(hint);
            ctx.fontSmall.draw(hint, vizX + (vizW - tw) / 2, vizY + vizH / 2 - 8, Theme.INK_DIM);
        }
    }

    /** True when the cursor is inside an inline widget (e.g. a slider) of {@code n}'s render area. */
    private boolean isOnInlineWidget(AppContext ctx, Node n, NodeKind k) {
        // Standalone slider node: the bottom-row widget swallows clicks.
        if (BuiltinKinds.SLIDER.equals(k.id())) {
            int nh = nodeHeight(ctx, n);
            int sx = (int) n.positionX + 10;
            int sy = (int) n.positionY + nh - 26;
            int sw = NODE_W - 20;
            int sh = 18;
            if (ctx.mouseX >= sx && ctx.mouseX <= sx + sw
                    && ctx.mouseY >= sy && ctx.mouseY <= sy + sh) {
                return true;
            }
        }

        // Consumer node hosting an inlined leaf — clicks on the inlined slider widget should
        // drag the slider, not the consumer.
        int headerH  = ctx.font.size + ctx.fontSmall.size + 10;
        int portRowH = ctx.fontSmall.size + 8;
        int rowY = (int) n.positionY + headerH + 6;
        for (PortSpec p : k.inputs()) {
            if (inlinedAtPort.containsKey(new PortRef(n.id, p.name()))) {
                int sx = (int) n.positionX + 16;
                int sy = rowY + portRowH;
                int sw = NODE_W - 32;
                int sh = INLINE_SLIDER_H - 4;
                if (ctx.mouseX >= sx && ctx.mouseX <= sx + sw
                        && ctx.mouseY >= sy && ctx.mouseY <= sy + sh) {
                    return true;
                }
            }
            rowY += inputRowHeight(n, p, portRowH);
        }
        return false;
    }

    /** Compact value formatter used for live port labels. */
    private static String formatValue(Value v) {
        return switch (v) {
            case Value.Scalar s       -> String.format("%.2f", s.v());
            case Value.Vec2 vv        -> String.format("(%.2f, %.2f)", vv.x(), vv.y());
            case Value.Complex c      -> String.format("%.2f%+.2fi", c.re(), c.im());
            case Value.SplitComplex c -> String.format("%.2f%+.2fj", c.re(), c.im());
            case Value.Quaternion q   -> String.format("(%.2f,%.2f,%.2f,%.2f)", q.w(), q.x(), q.y(), q.z());
            case Value.Coquat q       -> String.format("(%.2f,%.2f,%.2f,%.2f)", q.a(), q.b(), q.c(), q.d());
            case Value.Mat2 m         -> String.format("[%.2f,%.2f;%.2f,%.2f]", m.a00(), m.a01(), m.a10(), m.a11());
            case Value.Str s          -> "'" + s.s() + "'";
            case Value.Int i          -> String.valueOf(i.n());
        };
    }

    private static String kindShortLabel(NodeKind k) {
        // Drop the "builtin." or "dsl.neuron." prefix for the inline label.
        String id = k.id();
        int dot = id.lastIndexOf('.');
        if (dot >= 0) return id.substring(0, dot);
        return id;
    }

    private static String displayName(NodeKind k, Node n) {
        // For nodes that hold a user-meaningful "name" string in state (input/output), prefer it.
        var nameVal = n.state.get("name");
        if (nameVal instanceof lab.graph.Value.Str s) return s.s();
        return k.displayName();
    }

    private static Color tintFor(NodeKind k) {
        String id = k.id();
        if (id.startsWith("builtin.slider"))   return new Color(245, 235, 215, 255);
        if (id.startsWith("builtin.constant")) return new Color(245, 235, 215, 255);
        if (id.startsWith("builtin.input"))    return new Color(245, 235, 215, 255);
        if (id.startsWith("builtin.output"))   return new Color(232, 224, 244, 255);
        if (id.startsWith("builtin.probe"))    return new Color(232, 224, 244, 255);
        if (id.startsWith("dsl.neuron"))       return new Color(225, 240, 230, 255);
        return Theme.SURFACE;
    }

    private static Color portColor(Type t) {
        return switch (t) {
            case SCALAR       -> new Color(160, 160, 160, 255);
            case VEC2         -> new Color( 80, 130, 200, 255);
            case COMPLEX      -> new Color(160,  80, 200, 255);
            case SPLITCOMPLEX -> new Color(200, 130,  80, 255);
            case QUATERNION   -> new Color( 80, 180, 130, 255);
            case COQUAT       -> new Color(180, 180,  80, 255);
            case MAT2         -> new Color(120, 120, 200, 255);
        };
    }

    // ─── Drag state records ────────────────────────────────────────────────

    private static final class NodeDrag {
        final Node node;
        final float offsetX, offsetY;
        NodeDrag(Node node, float offsetX, float offsetY) {
            this.node = node; this.offsetX = offsetX; this.offsetY = offsetY;
        }
    }

    private static final class WireDrag {
        final UUID sourceNodeId;
        final String sourcePortName;
        final float startX, startY;
        Color previewColor;
        WireDrag(UUID id, String port, float sx, float sy, Color preview) {
            this.sourceNodeId = id; this.sourcePortName = port;
            this.startX = sx; this.startY = sy; this.previewColor = preview;
        }
    }

    private static final class PortHit {
        final UUID nodeId;
        final String portName;
        PortHit(UUID nodeId, String portName) { this.nodeId = nodeId; this.portName = portName; }
    }

    /** Per-probe rendering state — the embedded MiniViz plus the sampler driving it. */
    private static final class ProbeViz {
        final MiniViz viz;
        final ProbeFieldSampler sampler;
        ProbeViz(MiniViz viz, ProbeFieldSampler sampler) {
            this.viz = viz; this.sampler = sampler;
        }
    }
}
