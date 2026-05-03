package lab.ui.screens;

import com.raylib.Canvas;

import lab.graph.BuiltinKinds;
import lab.graph.KindRegistry;
import lab.graph.Network;
import lab.graph.Node;
import lab.graph.PortRef;
import lab.graph.Value;
import lab.graph.eval.Trainer;
import lab.ui.AppContext;
import lab.ui.RayGui;
import lab.ui.Screen;
import lab.ui.Theme;
import lab.ui.miniviz.Colormap;
import lab.ui.miniviz.LineStrip;
import lab.ui.miniviz.MiniViz;
import lab.ui.miniviz.PointSet;
import lab.ui.miniviz.ScalarField;

/**
 * Real linear-regression trainer wired to four mini-vizes.
 *
 * <p>Network (built in {@link #onEnter}):
 * <pre>{@code
 *   x1, x2 (Constant)               target (Constant)
 *           \                              |
 *            w1*x1 + w2*x2 + b   →   loss = (pred - target)^2
 *               (Learnables: w1, w2, b)
 * }</pre>
 *
 * <p>Per running tick, we cycle through a tiny dataset {@code y = TRUE_W1·x1 + TRUE_W2·x2 +
 * TRUE_B} of ~10 hand-picked samples. {@link Trainer#step(float)} runs forward+backward+SGD;
 * the returned loss is appended to the loss history; learnable values are read for the
 * weights scatter.
 *
 * <p>Mini-viz samplers compute closed-form (we have the analytic linear function) rather than
 * running a per-pixel forward eval — much cheaper, same result.
 */
public final class SimulatorScreen implements Screen {

    private static final int   MAX_EPOCHS = 1024;
    private static final int   FIELD_RES  = 96;

    /** True coefficients we're trying to learn. */
    private static final float TRUE_W1 = 0.70f;
    private static final float TRUE_W2 = -0.30f;
    private static final float TRUE_B  = 0.20f;

    /** Tiny mock dataset — (x1, x2) pairs; targets are computed from the true function. */
    private static final float[][] DATASET = {
            { 0.0f,  0.0f}, { 1.0f,  0.0f}, {0.0f,  1.0f},
            { 1.0f,  1.0f}, {-1.0f,  0.5f}, {0.5f, -0.5f},
            { 1.5f,  1.5f}, {-1.0f, -1.0f}, {2.0f,  0.0f},
            { 0.0f, -2.0f}
    };

    private boolean running;
    private int   epoch;
    private float currentLoss;
    private float bestLoss = Float.POSITIVE_INFINITY;
    private final float[] learningRate = { 0.05f };

    private Network net;
    private Trainer trainer;
    private Node x1Node, x2Node, targetNode;
    private Node w1Node, w2Node, biasNode;
    private int datasetCursor;

    /** Loss history packed as (x, y) — index along x, loss along y. */
    private final float[] lossHistory = new float[MAX_EPOCHS * 2];
    private int historyCount;

    /** Two points: current (w1, w2) and target (TRUE_W1, TRUE_W2). */
    private final float[] weightPoints = new float[4];

    private MiniViz outputViz, lossViz, residualViz, weightsViz;
    private LineStrip lossLine;
    private PointSet weightDots;

    @Override public String id()    { return "screen.simulator"; }
    @Override public String title() { return "Simulator"; }

    @Override
    public void onEnter(AppContext ctx) {
        buildNetwork(ctx.workspace.kinds);
        buildVizes();
        refreshWeightPoints();
    }

    @Override
    public void onExit(AppContext ctx) {
        if (outputViz   != null) outputViz.close();
        if (lossViz     != null) lossViz.close();
        if (residualViz != null) residualViz.close();
        if (weightsViz  != null) weightsViz.close();
        outputViz = lossViz = residualViz = weightsViz = null;
    }

    @Override
    public void render(AppContext ctx, Canvas c) {
        int x = ctx.contentX, y = ctx.contentY, w = ctx.contentW, h = ctx.contentH;

        // ─── Top control bar ────────────────────────────────────────────────
        int barH = 56;
        c.fillRect(x, y, w, barH, Theme.PANEL);
        c.drawRect(x, y, w, barH, Theme.FRAME);

        if (RayGui.button(x + 12, y + 14, 80, 28, running ? "Pause" : "Run")) {
            running = !running;
        }
        if (RayGui.button(x + 100, y + 14, 80, 28, "Step")) {
            stepOnce();
        }
        if (RayGui.button(x + 188, y + 14, 80, 28, "Reset")) {
            reset();
        }

        RayGui.label(x + 290, y + 14, 60, 28, "lr");
        RayGui.slider(x + 320, y + 14, 200, 28, fmt(learningRate[0]), null,
                      learningRate, 1e-4f, 1f);

        if (running) stepOnce();

        String status = String.format("epoch %4d   loss %.4f   best %.4f   %s",
                epoch, currentLoss, bestLoss, running ? "running" : "paused");
        ctx.fontSmall.draw(status, x + w - 360, y + 22, Theme.INK_DIM);

        // ─── Panel grid ─────────────────────────────────────────────────────
        int gridY = y + barH + 8;
        int gridH = h - barH - 8;
        int colW  = (w - 16) / 2;
        int rowH  = (gridH - 8) / 2;

        outputViz  .rect(x,            gridY,            colW,     rowH)    .render(ctx, c);
        lossViz    .rect(x + colW + 8, gridY,            colW - 8, rowH)    .render(ctx, c);
        residualViz.rect(x,            gridY + rowH + 8, colW,     rowH - 8).render(ctx, c);
        weightsViz .rect(x + colW + 8, gridY + rowH + 8, colW - 8, rowH - 8).render(ctx, c);
    }

    // ─── Setup ──────────────────────────────────────────────────────────────

    private void buildNetwork(KindRegistry K) {
        net = new Network(K);
        x1Node     = net.addNode(K.get(BuiltinKinds.CONSTANT_S),  0, 0);
        x2Node     = net.addNode(K.get(BuiltinKinds.CONSTANT_S),  0, 0);
        targetNode = net.addNode(K.get(BuiltinKinds.CONSTANT_S),  0, 0);
        w1Node     = net.addNode(K.get(BuiltinKinds.LEARNABLE_S), 0, 0);
        w2Node     = net.addNode(K.get(BuiltinKinds.LEARNABLE_S), 0, 0);
        biasNode   = net.addNode(K.get(BuiltinKinds.LEARNABLE_S), 0, 0);
        Node mul1  = net.addNode(K.get(BuiltinKinds.MUL_S),       0, 0);
        Node mul2  = net.addNode(K.get(BuiltinKinds.MUL_S),       0, 0);
        Node add1  = net.addNode(K.get(BuiltinKinds.ADD_S),       0, 0);
        Node add2  = net.addNode(K.get(BuiltinKinds.ADD_S),       0, 0);
        Node loss  = net.addNode(K.get(BuiltinKinds.LOSS_MSE_S),  0, 0);

        // pred = w1*x1 + w2*x2 + b
        net.connect(new PortRef(x1Node.id, "value"),  new PortRef(mul1.id, "a"));
        net.connect(new PortRef(w1Node.id, "value"),  new PortRef(mul1.id, "b"));
        net.connect(new PortRef(x2Node.id, "value"),  new PortRef(mul2.id, "a"));
        net.connect(new PortRef(w2Node.id, "value"),  new PortRef(mul2.id, "b"));
        net.connect(new PortRef(mul1.id, "product"),  new PortRef(add1.id, "a"));
        net.connect(new PortRef(mul2.id, "product"),  new PortRef(add1.id, "b"));
        net.connect(new PortRef(add1.id, "sum"),      new PortRef(add2.id, "a"));
        net.connect(new PortRef(biasNode.id, "value"), new PortRef(add2.id, "b"));
        net.connect(new PortRef(add2.id, "sum"),      new PortRef(loss.id, "pred"));
        net.connect(new PortRef(targetNode.id, "value"), new PortRef(loss.id, "target"));

        trainer = new Trainer(net, new PortRef(loss.id, "loss"));
    }

    private void buildVizes() {
        // ─── Output field — pred(x1, x2) over the input plane ────────────────
        ScalarField outputField = new ScalarField(
                (wx, wy) -> w1() * wx + w2() * wy + bias(),
                Colormap.MAGENTA_CYAN, FIELD_RES, FIELD_RES)
                .range(-2.5f, 2.5f);
        outputViz = new MiniViz(0, 0, 0, 0, "network output  pred = w1·x1 + w2·x2 + b")
                .worldRect(-2.5f, -2.5f, 2.5f, 2.5f)
                .add(outputField);

        // ─── Loss curve ──────────────────────────────────────────────────────
        lossLine = new LineStrip(lossHistory, 0, Theme.ACCENT);
        lossViz = new MiniViz(0, 0, 0, 0, "loss over steps")
                .worldRect(0, 0, MAX_EPOCHS, 1.5f)
                .add(lossLine);
        lossViz.hoverFormat = (wx, wy) ->
                String.format("step %.0f   loss %+.3f", wx, wy);

        // ─── Residual: pred - true(x1, x2) ──────────────────────────────────
        ScalarField residualField = new ScalarField(
                (wx, wy) -> {
                    float pred = w1() * wx + w2() * wy + bias();
                    float truth = TRUE_W1 * wx + TRUE_W2 * wy + TRUE_B;
                    return pred - truth;
                },
                Colormap.BLUE_RED, FIELD_RES, FIELD_RES)
                .range(-1f, 1f);
        residualViz = new MiniViz(0, 0, 0, 0, "residual  pred − true")
                .worldRect(-2.5f, -2.5f, 2.5f, 2.5f)
                .add(residualField);

        // ─── Learnable weights (current vs target) ──────────────────────────
        weightDots = new PointSet(weightPoints, 2, Theme.ACCENT).size(7f);
        weightsViz = new MiniViz(0, 0, 0, 0, "learnable weights  (w1, w2)")
                .worldRect(-1.5f, -1.5f, 1.5f, 1.5f)
                .add(weightDots);
    }

    // ─── Training step ──────────────────────────────────────────────────────

    private void stepOnce() {
        if (trainer == null) return;

        // Pick the next sample, set inputs and target.
        float[] sample = DATASET[datasetCursor];
        datasetCursor = (datasetCursor + 1) % DATASET.length;
        float x1 = sample[0], x2 = sample[1];
        float target = TRUE_W1 * x1 + TRUE_W2 * x2 + TRUE_B;
        x1Node.state.put("value",     new Value.Scalar(x1));
        x2Node.state.put("value",     new Value.Scalar(x2));
        targetNode.state.put("value", new Value.Scalar(target));

        // Train.
        float loss = trainer.step(learningRate[0]);
        if (Float.isNaN(loss)) return;

        epoch++;
        currentLoss = loss;
        if (loss < bestLoss) bestLoss = loss;

        if (historyCount < MAX_EPOCHS) {
            int idx = historyCount * 2;
            lossHistory[idx]     = epoch;
            lossHistory[idx + 1] = loss;
            historyCount++;
            lossLine.count = historyCount;
        }
        refreshWeightPoints();
    }

    private void refreshWeightPoints() {
        weightPoints[0] = w1();
        weightPoints[1] = w2();
        weightPoints[2] = TRUE_W1;
        weightPoints[3] = TRUE_W2;
    }

    private void reset() {
        running = false;
        epoch = 0;
        currentLoss = 0f;
        bestLoss = Float.POSITIVE_INFINITY;
        historyCount = 0;
        if (lossLine != null) lossLine.count = 0;
        datasetCursor = 0;
        if (w1Node   != null) w1Node.state.put("value",   new Value.Scalar(0f));
        if (w2Node   != null) w2Node.state.put("value",   new Value.Scalar(0f));
        if (biasNode != null) biasNode.state.put("value", new Value.Scalar(0f));
        refreshWeightPoints();
    }

    // ─── Read accessors for sampler closures ───────────────────────────────

    private float w1()   { return scalarOf(w1Node.state.get("value")); }
    private float w2()   { return scalarOf(w2Node.state.get("value")); }
    private float bias() { return scalarOf(biasNode.state.get("value")); }

    private static float scalarOf(Value v) {
        return v instanceof Value.Scalar s ? s.v() : 0f;
    }

    private static String fmt(float v) { return String.format("%.4f", v); }
}
