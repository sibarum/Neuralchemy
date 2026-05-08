package sibarum.neuralchemy.visualizer;

import com.raylib.Canvas;
import com.raylib.Color;
import com.raylib.MouseButtons;
import com.raylib.Window;
import com.raylib.runtime.WindowsBootstrap;
import sibarum.neuralchemy.nn.Dataset;
import sibarum.neuralchemy.nn.datasets.AdderDataset;
import sibarum.neuralchemy.rwnn.Gates;
import sibarum.neuralchemy.rwnn.RwLayer;
import sibarum.neuralchemy.rwnn.RwNetwork;
import sibarum.neuralchemy.rwnn.RwNetworkSnapshot;
import sibarum.neuralchemy.rwnn.RwStepTrace;
import sibarum.neuralchemy.rwnn.RwTrainer;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Realtime visualizer for the Ring-Weave Neural Network. Mirrors the BRN visualizer
 * in spirit: top bar (Step/Play/Add Layer/metrics), control bar (flip rate, decay,
 * autoplay), and a layered network display showing tier values, per-gate truth tables,
 * connections, and per-gate error/mutation status.
 *
 * <p>"Add Layer" inserts an identity-preserving pair of layers (gates all-A then all-B)
 * at the end of the network so training can continue from the same effective function.
 */
public final class RwVisualizerMain {

    private static final int WINDOW_W = 1200;
    private static final int WINDOW_H = 860;

    private static final int CELL_SIZE   = 44;
    private static final int CELL_GAP    = 10;
    private static final int LEFT_MARGIN = 110;
    private static final int TOP_MARGIN  = 170;

    private static final int BAR_Y      = 14;
    private static final int BTN_H      = 32;
    private static final int BTN_W      = 80;
    private static final int BTN_STEP_X = 20;
    private static final int BTN_PLAY_X = 110;
    private static final int BTN_ADD_X  = 200;
    private static final int BTN_ADD_W  = 110;

    private static final int CTRL_Y     = 64;
    private static final int CTRL_H     = 28;
    private static final int CTRL_BTN_W = 22;
    private static final int CTRL_VALUE_W = 50;
    private static final int CTRL_GAP   = 14;

    private static final Color FLIP_BG  = new Color(170, 230, 255, 255);
    private static final Color NORMAL_BG = Color.WHITE;
    private static final Color LINE     = new Color(180, 180, 180, 255);
    private static final Color FLIP_LN  = new Color( 60, 140, 220, 255);
    private static final Color CTRL_BG  = new Color(230, 230, 230, 255);
    private static final Color GATE_BORDER = new Color(80, 80, 80, 255);

    private static final String[] GATE_LABELS = {
            "F", "NOR", "!a&b", "!A", "a&!b", "!B", "XOR", "NAND",
            "AND", "XNOR", "B", "!a|b", "A", "a|!b", "OR", "T"
    };

    private RandomGenerator rng;
    private RwNetwork network;
    private RwTrainer trainer;
    private Dataset dataset;
    private byte[] inBuf;
    private byte[] tgtBuf;

    private int flipRatePct = 10;
    private int autoplayHzX10 = 10;
    private int decayPct = 95;

    private RwStepTrace lastTrace;
    private int stepCount;
    private boolean playing;
    private double playAccumulator;
    private double cachedErrorRate = -1;
    private boolean lastReverted;
    private int revertCount;

    private static final int N_CONTROLS = 3;
    private final Rect[] ctrlRects = new Rect[N_CONTROLS * 2];
    private record Rect(int x, int y, int w, int h) {
        boolean contains(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    public static void main(String[] args) {
        WindowsBootstrap.init("na-nn-visualizer");
        new RwVisualizerMain().run();
    }

    private RwVisualizerMain() {
        rng = RandomGenerator.of("L64X128MixRandom");
        dataset = new AdderDataset();
        rebuildNetwork(2);
    }

    private void rebuildNetwork(int nLayers) {
        int width = dataset.inputBits();
        RwLayer[] layers = new RwLayer[nLayers];
        for (int i = 0; i < nLayers; i++) {
            layers[i] = new RwLayer(width, width);
            layers[i].randomInit(rng);
        }
        network = new RwNetwork(layers);
        trainer = new RwTrainer(network, flipRatePct / 100.0, rng);
        trainer.decay = decayPct / 100.0;
        inBuf = new byte[width];
        tgtBuf = new byte[width];
        lastTrace = null;
        stepCount = 0;
        cachedErrorRate = -1;
        lastReverted = false;
        revertCount = 0;
    }

    private void addIdentityLayerPair() {
        int width = dataset.inputBits();
        RwLayer layerA = new RwLayer(width, width);
        RwLayer layerB = new RwLayer(width, width);
        Arrays.fill(layerA.gateTypes, Gates.A);
        Arrays.fill(layerB.gateTypes, Gates.B);

        RwLayer[] oldLayers = network.layers;
        RwLayer[] newLayers = Arrays.copyOf(oldLayers, oldLayers.length + 2);
        newLayers[oldLayers.length] = layerA;
        newLayers[oldLayers.length + 1] = layerB;

        network = new RwNetwork(newLayers);
        trainer = new RwTrainer(network, flipRatePct / 100.0, rng);
        trainer.decay = decayPct / 100.0;
        // Re-evaluate cached error rate (should be unchanged since identity-pair preserves output).
        cachedErrorRate = computeMeanBitErrorRate();
        lastTrace = null;
    }

    private void run() {
        try (Window w = Window.create(WINDOW_W, WINDOW_H, "RWNN Visualizer")) {
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

    private void runStep() {
        dataset.sample(rng.nextInt(dataset.size()), inBuf, tgtBuf);

        RwNetworkSnapshot snap = RwNetworkSnapshot.of(network);
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

    private static boolean hasMutations(RwStepTrace trace) {
        for (boolean[] row : trace.mutated()) {
            for (boolean m : row) if (m) return true;
        }
        return false;
    }

    private double computeMeanBitErrorRate() {
        int width = dataset.inputBits();
        byte[] in = new byte[width];
        byte[] tgt = new byte[width];
        byte[] out = new byte[width];
        long total = 0, wrong = 0;
        for (int i = 0; i < dataset.size(); i++) {
            dataset.sample(i, in, tgt);
            network.forward(in, out);
            for (int j = 0; j < width; j++) wrong += (out[j] ^ tgt[j]) & 1;
            total += width;
        }
        return wrong / (double) total;
    }

    private void handleInput(Window w) {
        if (!w.isMouseButtonPressed(MouseButtons.LEFT)) return;
        int mx = w.mouseX();
        int my = w.mouseY();

        if (rectContains(BTN_STEP_X, BAR_Y, BTN_W, BTN_H, mx, my)) { runStep(); return; }
        if (rectContains(BTN_PLAY_X, BAR_Y, BTN_W, BTN_H, mx, my)) {
            playing = !playing;
            playAccumulator = 0;
            return;
        }
        if (rectContains(BTN_ADD_X, BAR_Y, BTN_ADD_W, BTN_H, mx, my)) {
            addIdentityLayerPair();
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
            case 1 -> autoplayHzX10 = clamp(autoplayHzX10 + sign, 1, 100);
            case 2 -> {
                decayPct = clamp(decayPct + sign, 50, 99);
                trainer.decay = decayPct / 100.0;
            }
        }
    }

    private void drawTopBar(Canvas c) {
        c.fillRect(BTN_STEP_X, BAR_Y, BTN_W, BTN_H, Color.SKYBLUE);
        c.drawRect(BTN_STEP_X, BAR_Y, BTN_W, BTN_H, Color.DARKBLUE);
        c.drawText("Step", BTN_STEP_X + 24, BAR_Y + 8, 18, Color.BLACK);

        Color playColor = playing ? Color.ORANGE : Color.GREEN;
        c.fillRect(BTN_PLAY_X, BAR_Y, BTN_W, BTN_H, playColor);
        c.drawRect(BTN_PLAY_X, BAR_Y, BTN_W, BTN_H, Color.DARKGRAY);
        c.drawText(playing ? "Pause" : "Play", BTN_PLAY_X + 18, BAR_Y + 8, 18, Color.BLACK);

        c.fillRect(BTN_ADD_X, BAR_Y, BTN_ADD_W, BTN_H, Color.LIME);
        c.drawRect(BTN_ADD_X, BAR_Y, BTN_ADD_W, BTN_H, Color.DARKGREEN);
        c.drawText("Add Layer", BTN_ADD_X + 16, BAR_Y + 8, 16, Color.BLACK);

        c.drawText("Step: " + stepCount, 330, BAR_Y + 8, 16, Color.DARKGRAY);
        if (cachedErrorRate >= 0) {
            c.drawText(String.format("Err: %.3f", cachedErrorRate), 440, BAR_Y + 8, 16, Color.DARKGRAY);
        }
        c.drawText("Layers: " + network.layers.length, 560, BAR_Y + 8, 16, Color.DARKGRAY);
        c.drawText("Reverts: " + revertCount, 670, BAR_Y + 8, 16, Color.DARKGRAY);
        if (lastReverted) c.drawText("REVERTED", 800, BAR_Y + 8, 16, Color.RED);
    }

    private void drawControls(Canvas c) {
        int x = 20;
        x = drawControl(c, x, "Flip",  String.format("%.2f", flipRatePct / 100.0), 0);
        x = drawControl(c, x, "Speed", String.format("%.1fHz", autoplayHzX10 / 10.0), 1);
        x = drawControl(c, x, "Decay", String.format("%.2f", decayPct / 100.0), 2);
    }

    private int drawControl(Canvas c, int x, String label, String value, int idx) {
        int labelW = 70;
        c.drawText(label, x, CTRL_Y + 8, 14, Color.DARKGRAY);
        x += labelW;

        c.fillRect(x, CTRL_Y, CTRL_BTN_W, CTRL_H, CTRL_BG);
        c.drawRect(x, CTRL_Y, CTRL_BTN_W, CTRL_H, Color.DARKGRAY);
        c.drawText("-", x + 8, CTRL_Y + 6, 18, Color.BLACK);
        ctrlRects[idx * 2] = new Rect(x, CTRL_Y, CTRL_BTN_W, CTRL_H);
        x += CTRL_BTN_W;

        c.fillRect(x, CTRL_Y, CTRL_VALUE_W, CTRL_H, Color.WHITE);
        c.drawRect(x, CTRL_Y, CTRL_VALUE_W, CTRL_H, Color.DARKGRAY);
        int vw = c.measureText(value, 14);
        c.drawText(value, x + (CTRL_VALUE_W - vw) / 2, CTRL_Y + 8, 14, Color.BLACK);
        x += CTRL_VALUE_W;

        c.fillRect(x, CTRL_Y, CTRL_BTN_W, CTRL_H, CTRL_BG);
        c.drawRect(x, CTRL_Y, CTRL_BTN_W, CTRL_H, Color.DARKGRAY);
        c.drawText("+", x + 6, CTRL_Y + 6, 18, Color.BLACK);
        ctrlRects[idx * 2 + 1] = new Rect(x, CTRL_Y, CTRL_BTN_W, CTRL_H);
        x += CTRL_BTN_W;

        x += CTRL_GAP;
        return x;
    }

    private void drawNetwork(Canvas c) {
        if (lastTrace == null) return;

        int width = dataset.inputBits();
        int nLayers = network.layers.length;
        int spacing = tierSpacing(nLayers);

        int[] tierY = new int[nLayers + 1];
        for (int t = 0; t <= nLayers; t++) tierY[t] = TOP_MARGIN + t * spacing;
        int targetY = tierY[nLayers] + spacing;

        // Draw each layer's connections + gates first (so cells overlay)
        for (int L = 0; L < nLayers; L++) {
            drawLayerConnections(c, L, tierY[L], tierY[L + 1]);
            drawGateLabels(c, L, tierY[L], tierY[L + 1]);
        }

        double saturation = 1.0 / Math.max(1e-9, 1.0 - decayPct / 100.0);

        drawTierLabel(c, "Input", tierY[0]);
        drawBitRow(c, lastTrace.tierValues()[0],
                lastTrace.tierErrAccum()[0], saturation, width, tierY[0]);

        for (int L = 0; L < nLayers; L++) {
            drawTierLabel(c, "Layer " + L, tierY[L + 1]);
            drawBitRow(c, lastTrace.tierValues()[L + 1],
                    lastTrace.tierErrAccum()[L + 1], saturation, width, tierY[L + 1]);
        }

        drawTierLabel(c, "Target", targetY);
        drawBitRow(c, lastTrace.target(), null, 0, width, targetY);
    }

    private void drawLayerConnections(Canvas c, int layerIdx, int yIn, int yOut) {
        int width = dataset.inputBits();
        int rowY1 = yIn + cellSize();
        int rowY2 = yOut;
        for (int j = 0; j < width; j++) {
            int xOut = cellX(j) + cellSize() / 2;
            int xLeft = cellX((j - 1 + width) % width) + cellSize() / 2;
            int xRight = cellX((j + 1) % width) + cellSize() / 2;
            Color lc = (lastTrace.mutated()[layerIdx][j]) ? FLIP_LN : LINE;
            c.drawLine(xLeft, rowY1, xOut, rowY2, lc);
            c.drawLine(xRight, rowY1, xOut, rowY2, lc);
        }
    }

    private void drawGateLabels(Canvas c, int layerIdx, int yIn, int yOut) {
        int width = dataset.inputBits();
        int midY = (yIn + cellSize() + yOut) / 2 - 8;
        for (int j = 0; j < width; j++) {
            int gateType = network.layers[layerIdx].gateTypes[j] & 0x0F;
            String label = GATE_LABELS[gateType];
            int cx = cellX(j) + cellSize() / 2;
            int tw = c.measureText(label, 11);
            int boxW = tw + 8;
            int boxH = 16;
            Color bg = lastTrace.mutated()[layerIdx][j] ? FLIP_BG : Color.WHITE;
            c.fillRect(cx - boxW / 2, midY, boxW, boxH, bg);
            c.drawRect(cx - boxW / 2, midY, boxW, boxH, GATE_BORDER);
            c.drawText(label, cx - tw / 2, midY + 2, 11, Color.BLACK);
        }
    }

    private void drawBitRow(Canvas c, byte[] values, double[] errAccum, double saturation,
                            int width, int y) {
        int cs = cellSize();
        int fontSize = cs > 30 ? 22 : 16;
        for (int i = 0; i < width; i++) {
            int x = cellX(i);
            Color bg = NORMAL_BG;
            if (errAccum != null && saturation > 0) {
                double t = Math.min(1.0, errAccum[i] / saturation);
                if (t > 0) {
                    int g = (int) Math.round(255 * (1.0 - t * 0.7));
                    bg = new Color(255, g, g, 255);
                }
            }
            c.fillRect(x, y, cs, cs, bg);
            c.drawRect(x, y, cs, cs, Color.BLACK);

            String txt = String.valueOf(values[i] & 1);
            int txtW = c.measureText(txt, fontSize);
            c.drawText(txt, x + (cs - txtW) / 2, y + (cs - fontSize) / 2, fontSize, Color.BLACK);
        }
    }

    private void drawTierLabel(Canvas c, String label, int y) {
        c.drawText(label, 14, y + (cellSize() - 14) / 2, 14, Color.DARKGRAY);
    }

    private int cellSize() { return CELL_SIZE; }
    private int cellGap()  { return CELL_GAP; }
    private int cellX(int i) { return LEFT_MARGIN + i * (cellSize() + cellGap()); }

    private int tierSpacing(int nl) {
        int nTiers = nl + 2;
        int available = WINDOW_H - TOP_MARGIN - cellSize() - 20;
        int desired = available / Math.max(1, nTiers - 1);
        return Math.max(cellSize() + 35, Math.min(110, desired));
    }

    private static boolean rectContains(int rx, int ry, int rw, int rh, int x, int y) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
