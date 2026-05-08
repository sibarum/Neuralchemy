package sibarum.neuralchemy.visualizer;

import com.raylib.Canvas;
import com.raylib.Color;
import com.raylib.MouseButtons;
import com.raylib.Window;
import com.raylib.runtime.WindowsBootstrap;
import sibarum.neuralchemy.brnn.BrnLayer;
import sibarum.neuralchemy.brnn.BrnNetwork;
import sibarum.neuralchemy.brnn.BrnTrainer;
import sibarum.neuralchemy.brnn.NeighborXorDataset;
import sibarum.neuralchemy.brnn.NetworkSnapshot;
import sibarum.neuralchemy.brnn.StepTrace;

import java.util.random.RandomGenerator;

/**
 * Realtime BRN visualizer. Top bar shows step controls and global metrics; two control
 * rows expose hyperparameters; the network display shows tier values, routing, NOT
 * indicators, and per-layer mutation rate. Cell red-intensity reflects flagAccum.
 */
public final class BrnVisualizerMain {

    private static final int WINDOW_W = 1200;
    private static final int WINDOW_H = 860;

    private static final int CELL_SIZE   = 50;
    private static final int CELL_GAP    = 10;
    private static final int LEFT_MARGIN = 100;
    private static final int TOP_MARGIN  = 170;

    private static final int BAR_Y      = 14;
    private static final int BTN_H      = 32;
    private static final int BTN_W      = 80;
    private static final int BTN_STEP_X = 20;
    private static final int BTN_PLAY_X = 110;

    private static final int CTRL_Y_1   = 64;
    private static final int CTRL_Y_2   = 104;
    private static final int CTRL_H     = 28;
    private static final int CTRL_BTN_W = 22;
    private static final int CTRL_VALUE_W = 50;
    private static final int CTRL_GAP   = 14;

    private static final Color FLIP_BG  = new Color(170, 230, 255, 255);
    private static final Color NORMAL_BG = Color.WHITE;
    private static final Color LINE     = new Color(180, 180, 180, 255);
    private static final Color FLIP_LN  = new Color( 60, 140, 220, 255);
    private static final Color CTRL_BG  = new Color(230, 230, 230, 255);

    private RandomGenerator rng;
    private BrnNetwork network;
    private BrnTrainer trainer;
    private NeighborXorDataset dataset;
    private byte[] inBuf;
    private byte[] tgtBuf;

    // Config state encoded as ints for ±-button increments.
    private int flipRatePct   = 10;   // 1..100 → 0.01..1.00
    private int nLayers       = 2;     // 1..8
    private int autoplayHzX10 = 10;    // 1..100 → 0.1..10.0 Hz
    private int width         = 8;     // 4, 8, 16, 32
    private int datasetSize   = 64;    // 4..2048
    private int decayPct      = 95;    // 50..99 → 0.50..0.99
    private int swapBiasPct   = 50;    // 0..100 → 0.00..1.00

    private StepTrace lastTrace;
    private int stepCount;
    private boolean playing;
    private double playAccumulator;
    private double cachedErrorRate = -1;
    private boolean lastReverted;
    private int revertCount;

    private static final int N_CONTROLS = 7;
    private final Rect[] ctrlRects = new Rect[N_CONTROLS * 2];
    private record Rect(int x, int y, int w, int h) {
        boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    public static void main(String[] args) {
        WindowsBootstrap.init("na-nn-visualizer");
        new BrnVisualizerMain().run();
    }

    private BrnVisualizerMain() {
        rng = RandomGenerator.of("L64X128MixRandom");
        rebuildAll();
    }

    private void run() {
        try (Window w = Window.create(WINDOW_W, WINDOW_H, "BRN Visualizer")) {
            w.setTargetFPS(60);
            runStep();

            while (!w.shouldClose()) {
                handleInput(w);

                if (playing) {
                    playAccumulator += w.frameTime();
                    double interval = 1.0 / (autoplayHzX10 / 10.0);
                    if (playAccumulator >= interval) {
                        playAccumulator = 0;
                        runStep();
                    }
                }

                try (Canvas c = w.beginDraw()) {
                    c.clearBackground(Color.RAYWHITE);
                    drawTopBar(c);
                    drawControls(c);
                    drawNetwork(c);
                }
            }
        }
    }

    // ─── Rebuild ─────────────────────────────────────────────────────────────

    private void rebuildAll() {
        BrnLayer[] layers = new BrnLayer[nLayers];
        for (int i = 0; i < nLayers; i++) {
            layers[i] = new BrnLayer(width, width);
            layers[i].randomInit(rng);
        }
        network = new BrnNetwork(layers);
        trainer = new BrnTrainer(network, flipRatePct / 100.0, rng);
        trainer.decay = decayPct / 100.0;
        trainer.swapBias = swapBiasPct / 100.0;
        dataset = new NeighborXorDataset(datasetSize, width, rng);
        inBuf = new byte[width];
        tgtBuf = new byte[width];
        lastTrace = null;
        stepCount = 0;
        cachedErrorRate = -1;
        lastReverted = false;
        revertCount = 0;
    }

    // ─── Stepping ────────────────────────────────────────────────────────────

    private void runStep() {
        dataset.sample(rng.nextInt(dataset.size()), inBuf, tgtBuf);

        NetworkSnapshot snap = NetworkSnapshot.of(network);
        double errBefore = cachedErrorRate >= 0 ? cachedErrorRate : computeMeanBitErrorRate();

        lastTrace = trainer.stepTraced(inBuf, tgtBuf);
        stepCount++;

        boolean mutated = hasMutations(lastTrace);
        if (mutated) {
            double errAfter = computeMeanBitErrorRate();
            if (errAfter > errBefore) {
                snap.restoreTo(network);
                lastReverted = true;
                revertCount++;
                cachedErrorRate = errBefore;
            } else {
                lastReverted = false;
                cachedErrorRate = errAfter;
            }
        } else {
            lastReverted = false;
        }
    }

    private static boolean hasMutations(StepTrace trace) {
        for (byte[] row : trace.flipped()) {
            for (byte b : row) if (b != 0) return true;
        }
        return false;
    }

    private double computeMeanBitErrorRate() {
        byte[] in = new byte[width];
        byte[] tgt = new byte[width];
        byte[] out = new byte[width];
        long total = 0;
        long wrong = 0;
        for (int i = 0; i < dataset.size(); i++) {
            dataset.sample(i, in, tgt);
            network.forward(in, out);
            for (int j = 0; j < width; j++) wrong += (out[j] ^ tgt[j]) & 1;
            total += width;
        }
        return wrong / (double) total;
    }

    // ─── Input ───────────────────────────────────────────────────────────────

    private void handleInput(Window w) {
        if (!w.isMouseButtonPressed(MouseButtons.LEFT)) return;
        int mx = w.mouseX();
        int my = w.mouseY();

        if (rectContains(BTN_STEP_X, BAR_Y, BTN_W, BTN_H, mx, my)) {
            runStep();
            return;
        }
        if (rectContains(BTN_PLAY_X, BAR_Y, BTN_W, BTN_H, mx, my)) {
            playing = !playing;
            playAccumulator = 0;
            return;
        }

        for (int i = 0; i < ctrlRects.length; i++) {
            Rect r = ctrlRects[i];
            if (r != null && r.contains(mx, my)) {
                int control = i / 2;
                int sign = (i % 2 == 0) ? -1 : +1;
                applyControlDelta(control, sign);
                return;
            }
        }
    }

    private void applyControlDelta(int control, int sign) {
        switch (control) {
            case 0 -> {
                flipRatePct = clamp(flipRatePct + sign, 1, 100);
                trainer.flipRate = flipRatePct / 100.0;
            }
            case 1 -> {
                nLayers = clamp(nLayers + sign, 1, 8);
                rebuildAll();
            }
            case 2 -> {
                autoplayHzX10 = clamp(autoplayHzX10 + sign, 1, 100);
            }
            case 3 -> {
                int newWidth = sign > 0 ? width * 2 : width / 2;
                width = clamp(newWidth, 4, 32);
                rebuildAll();
            }
            case 4 -> {
                int newSize = sign > 0 ? datasetSize * 2 : datasetSize / 2;
                datasetSize = clamp(newSize, 4, 2048);
                dataset = new NeighborXorDataset(datasetSize, width, rng);
                cachedErrorRate = computeMeanBitErrorRate();
            }
            case 5 -> {
                decayPct = clamp(decayPct + sign, 50, 99);
                trainer.decay = decayPct / 100.0;
            }
            case 6 -> {
                swapBiasPct = clamp(swapBiasPct + sign, 0, 100);
                trainer.swapBias = swapBiasPct / 100.0;
            }
        }
    }

    // ─── Drawing ─────────────────────────────────────────────────────────────

    private void drawTopBar(Canvas c) {
        c.fillRect(BTN_STEP_X, BAR_Y, BTN_W, BTN_H, Color.SKYBLUE);
        c.drawRect(BTN_STEP_X, BAR_Y, BTN_W, BTN_H, Color.DARKBLUE);
        c.drawText("Step", BTN_STEP_X + 24, BAR_Y + 8, 18, Color.BLACK);

        Color playColor = playing ? Color.ORANGE : Color.GREEN;
        c.fillRect(BTN_PLAY_X, BAR_Y, BTN_W, BTN_H, playColor);
        c.drawRect(BTN_PLAY_X, BAR_Y, BTN_W, BTN_H, Color.DARKGRAY);
        c.drawText(playing ? "Pause" : "Play", BTN_PLAY_X + 18, BAR_Y + 8, 18, Color.BLACK);

        c.drawText("Step: " + stepCount, 220, BAR_Y + 8, 18, Color.DARKGRAY);

        if (cachedErrorRate >= 0) {
            String txt = String.format("Mean bit-err: %.3f", cachedErrorRate);
            c.drawText(txt, 360, BAR_Y + 8, 18, Color.DARKGRAY);
        }

        c.drawText("Reverts: " + revertCount, 580, BAR_Y + 8, 18, Color.DARKGRAY);
        if (lastReverted) {
            c.drawText("REVERTED", 720, BAR_Y + 8, 18, Color.RED);
        }
    }

    private void drawControls(Canvas c) {
        // Row 1
        int x = 20;
        x = drawControl(c, x, CTRL_Y_1, "Flip",   String.format("%.2f", flipRatePct / 100.0), 0);
        x = drawControl(c, x, CTRL_Y_1, "Layers", Integer.toString(nLayers),                 1);
        x = drawControl(c, x, CTRL_Y_1, "Speed",  String.format("%.1fHz", autoplayHzX10 / 10.0), 2);
        x = drawControl(c, x, CTRL_Y_1, "Width",  Integer.toString(width),                   3);
        x = drawControl(c, x, CTRL_Y_1, "Data N", Integer.toString(datasetSize),             4);

        // Row 2
        x = 20;
        x = drawControl(c, x, CTRL_Y_2, "Decay",     String.format("%.2f", decayPct / 100.0), 5);
        x = drawControl(c, x, CTRL_Y_2, "SwapBias",  String.format("%.2f", swapBiasPct / 100.0), 6);
    }

    private int drawControl(Canvas c, int x, int y, String label, String value, int idx) {
        int labelW = 70;
        c.drawText(label, x, y + 8, 14, Color.DARKGRAY);
        x += labelW;

        c.fillRect(x, y, CTRL_BTN_W, CTRL_H, CTRL_BG);
        c.drawRect(x, y, CTRL_BTN_W, CTRL_H, Color.DARKGRAY);
        c.drawText("-", x + 8, y + 6, 18, Color.BLACK);
        ctrlRects[idx * 2] = new Rect(x, y, CTRL_BTN_W, CTRL_H);
        x += CTRL_BTN_W;

        c.fillRect(x, y, CTRL_VALUE_W, CTRL_H, Color.WHITE);
        c.drawRect(x, y, CTRL_VALUE_W, CTRL_H, Color.DARKGRAY);
        int vw = c.measureText(value, 14);
        c.drawText(value, x + (CTRL_VALUE_W - vw) / 2, y + 8, 14, Color.BLACK);
        x += CTRL_VALUE_W;

        c.fillRect(x, y, CTRL_BTN_W, CTRL_H, CTRL_BG);
        c.drawRect(x, y, CTRL_BTN_W, CTRL_H, Color.DARKGRAY);
        c.drawText("+", x + 6, y + 6, 18, Color.BLACK);
        ctrlRects[idx * 2 + 1] = new Rect(x, y, CTRL_BTN_W, CTRL_H);
        x += CTRL_BTN_W;

        x += CTRL_GAP;
        return x;
    }

    private void drawNetwork(Canvas c) {
        if (lastTrace == null) return;

        int nl = network.layers.length;
        int spacing = tierSpacing(nl);
        int[] tierY = new int[nl + 1];
        for (int t = 0; t <= nl; t++) tierY[t] = TOP_MARGIN + t * spacing;
        int targetY = tierY[nl] + spacing;

        for (int L = 0; L < nl; L++) {
            drawRoutingLines(c, L, tierY[L], tierY[L + 1]);
        }

        drawTierLabel(c, "Input", null, tierY[0]);
        drawBitRow(c, lastTrace.tierValues()[0], null, null, null, tierY[0]);

        double saturation = 1.0 / Math.max(1e-9, 1.0 - decayPct / 100.0);
        double oneMinusDecay = 1.0 - decayPct / 100.0;

        for (int L = 0; L < nl; L++) {
            double perStepRate = lastTrace.mutationRate()[L] * oneMinusDecay;
            String layerLabel = String.format("Layer %d  μ=%.2f", L, perStepRate);
            drawTierLabel(c, layerLabel, null, tierY[L + 1]);
            drawBitRow(c, lastTrace.tierValues()[L + 1],
                    lastTrace.flagged()[L], lastTrace.flipped()[L],
                    new AccumCtx(lastTrace.flagAccum()[L], saturation),
                    tierY[L + 1]);
            drawNotIndicators(c, network.layers[L], lastTrace.tierValues()[L], tierY[L + 1]);
        }

        drawTierLabel(c, "Target", null, targetY);
        drawBitRow(c, lastTrace.target(), null, null, null, targetY);
    }

    private record AccumCtx(double[] accum, double saturation) {}

    private void drawRoutingLines(Canvas c, int layerIdx, int yIn, int yOut) {
        BrnLayer layer = network.layers[layerIdx];
        byte[] flipped = lastTrace.flipped()[layerIdx];

        int rowY1 = yIn + cellSize();
        int rowY2 = yOut;

        for (int i = 0; i < width; i++) {
            int srcGate = layer.route[i];
            int xOut = cellX(i) + cellSize() / 2;
            int xA = cellX(srcGate) + cellSize() / 2;
            int xB = cellX((srcGate + 1) % width) + cellSize() / 2;

            Color lc = flipped[i] != 0 ? FLIP_LN : LINE;
            c.drawLine(xA, rowY1, xOut, rowY2, lc);
            c.drawLine(xB, rowY1, xOut, rowY2, lc);
        }
    }

    private void drawBitRow(Canvas c, byte[] values, byte[] flagged, byte[] flipped,
                            AccumCtx accumCtx, int y) {
        int cs = cellSize();
        int fontSize = cs > 30 ? 24 : 16;
        for (int i = 0; i < width; i++) {
            int x = cellX(i);
            Color bg;
            if (flipped != null && flipped[i] != 0) {
                bg = FLIP_BG;
            } else if (accumCtx != null) {
                double t = accumCtx.accum[i] / accumCtx.saturation;
                bg = accumColor(t);
            } else {
                bg = NORMAL_BG;
            }
            c.fillRect(x, y, cs, cs, bg);
            // Border: red highlight if flagged this step
            Color border = (flagged != null && flagged[i] != 0) ? Color.RED : Color.BLACK;
            c.drawRect(x, y, cs, cs, border);

            String txt = String.valueOf(values[i] & 1);
            int txtW = c.measureText(txt, fontSize);
            c.drawText(txt, x + (cs - txtW) / 2, y + (cs - fontSize) / 2, fontSize, Color.BLACK);
        }
    }

    /** Signed accumulator → background tint.
     *  t > 0 (consistently wrong): white → red.
     *  t < 0 (consistently right): white → green.
     *  t ≈ 0: white. */
    private static Color accumColor(double t) {
        double clamped = Math.max(-1.0, Math.min(1.0, t));
        if (clamped > 0) {
            int g = (int) Math.round(255 * (1.0 - clamped * 0.7));
            return new Color(255, g, g, 255);
        } else if (clamped < 0) {
            int rb = (int) Math.round(255 * (1.0 - (-clamped) * 0.5));
            return new Color(rb, 255, rb, 255);
        } else {
            return NORMAL_BG;
        }
    }

    private void drawTierLabel(Canvas c, String label, Object unused, int y) {
        c.drawText(label, 14, y + (cellSize() - 14) / 2, 14, Color.DARKGRAY);
    }

    private void drawNotIndicators(Canvas c, BrnLayer layer, byte[] layerInput, int y) {
        // Active NOT = the gate's notRef points at a layer-input bit currently holding 1
        // (because the third XOR operand inverts when it is 1).
        int cs = cellSize();
        for (int i = 0; i < width; i++) {
            int j = layer.route[i];
            int refIdx = layer.notRef[j];
            if ((layerInput[refIdx] & 1) == 1) {
                int x = cellX(i);
                c.fillCircle(x + cs - 8, y + 8, 5, Color.PURPLE);
            }
        }
    }

    // ─── Geometry ────────────────────────────────────────────────────────────

    private int cellSize() {
        if (width <= 8) return CELL_SIZE;
        if (width <= 16) return 28;
        return 16;
    }

    private int cellGap() {
        return width <= 8 ? CELL_GAP : 4;
    }

    private int cellX(int i) {
        return LEFT_MARGIN + i * (cellSize() + cellGap());
    }

    private int tierSpacing(int nl) {
        int nTiers = nl + 2;
        int available = WINDOW_H - TOP_MARGIN - cellSize() - 20;
        int desired = available / Math.max(1, nTiers - 1);
        return Math.max(cellSize() + 25, Math.min(120, desired));
    }

    private static boolean rectContains(int rx, int ry, int rw, int rh, int x, int y) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
