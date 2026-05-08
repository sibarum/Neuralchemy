package sibarum.neuralchemy.rwnn;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Trainer for {@link RwNetwork}.
 *
 * <p><b>Forward</b>: each ring-weave layer evaluates 2-input gates against the
 * (j-1, j+1) wraparound neighbors of the previous tier.
 *
 * <p><b>Backprop (probability-based)</b>: at the output tier, each bit's error
 * probability is 1.0 if it disagrees with the target, 0.0 otherwise. For each gate
 * {@code j} with current inputs {@code (a, b)} and current output {@code y}:
 * <ul>
 *   <li>Compute {@code delta_a = (eval(type, ¬a, b) ≠ y) ? 1 : 0} — does flipping
 *       {@code a} flip the output?</li>
 *   <li>Same for {@code delta_b}.</li>
 *   <li>If exactly one delta is 1, that input gets all of {@code error_prob[j]}.</li>
 *   <li>If both are 1, split 50/50.</li>
 *   <li>If neither is 1 (both inputs must flip together), split 50/50.</li>
 * </ul>
 *
 * <p><b>Update</b>: per-(gate, input combo) signed accumulator with decay:
 * {@code accum += sign * error_prob}, where {@code sign = +1} if we want this
 * combo's output to be 1, {@code -1} otherwise. Each step, the gate type's bit at
 * combo {@code c} is set to {@code accum[c] > 0 ? 1 : 0}.
 *
 * <p>Hill-climb guard (revert on regression) is the caller's responsibility — the
 * trainer just mutates; outer code can snapshot and revert.
 */
public final class RwTrainer {

    public final RwNetwork network;
    public double flipRate;
    public double decay = 0.95;

    private final RandomGenerator rng;
    /** Per-layer per-gate per-combo signed accumulator. Shape [L][nOut][4]. */
    private final double[][][] gateAccum;
    /** Per-tier per-position error-probability EMA. Shape [nLayers+1][nOut]. */
    private final double[][] tierErrAccum;

    public RwTrainer(RwNetwork network, double flipRate, RandomGenerator rng) {
        this.network = network;
        this.flipRate = flipRate;
        this.rng = rng;
        int W = network.outputBits();
        for (RwLayer l : network.layers) {
            if (l.nIn != W || l.nOut != W) {
                throw new IllegalArgumentException(
                        "RwTrainer requires uniform layer width; got "
                                + l.nIn + "->" + l.nOut + " in a " + W + "-wide network");
            }
        }
        this.gateAccum = new double[network.layers.length][W][4];
        this.tierErrAccum = new double[network.layers.length + 1][W];
    }

    public void step(byte[] input, byte[] target) {
        stepTraced(input, target);
    }

    public RwStepTrace stepTraced(byte[] input, byte[] target) {
        int W = network.outputBits();
        int nLayers = network.layers.length;

        // Forward (capturing tier values).
        byte[][] tierValues = new byte[nLayers + 1][W];
        System.arraycopy(input, 0, tierValues[0], 0, W);
        for (int L = 0; L < nLayers; L++) {
            network.layers[L].forward(tierValues[L], tierValues[L + 1]);
        }

        // Initial output-tier error probabilities (binary per-step). The accumulator
        // is updated for visualization at the end of the step but does NOT feed back
        // into the backprop signal — empirically the per-step binary signal converges
        // faster than the smoothed accumulator on local-structure tasks.
        double[] errProb = new double[W];
        for (int i = 0; i < W; i++) {
            errProb[i] = ((tierValues[nLayers][i] ^ target[i]) & 1) == 1 ? 1.0 : 0.0;
        }

        double[][] errAtTier = new double[nLayers + 1][];
        errAtTier[nLayers] = errProb.clone();

        // Decay gate accumulators once per step.
        for (int L = 0; L < nLayers; L++) {
            for (int j = 0; j < W; j++) {
                for (int c = 0; c < 4; c++) {
                    gateAccum[L][j][c] *= decay;
                }
            }
        }

        // Backprop layer by layer.
        for (int L = nLayers - 1; L >= 0; L--) {
            RwLayer layer = network.layers[L];
            byte[] in = tierValues[L];
            byte[] out = tierValues[L + 1];
            double[] inErr = new double[W];

            for (int j = 0; j < W; j++) {
                double e = errProb[j];
                if (e <= 0) continue;

                int aPos = layer.leftSource(j);
                int bPos = layer.rightSource(j);
                int a = in[aPos] & 1;
                int b = in[bPos] & 1;
                int y = out[j] & 1;
                int combo = a * 2 + b;
                int targetBit = y ^ 1;

                // Update accumulator: we want this combo to produce targetBit.
                gateAccum[L][j][combo] += (targetBit == 1 ? +1.0 : -1.0) * e;

                // Distribute error to the two input positions per the probabilistic rule.
                int yFlipA = Gates.eval(layer.gateTypes[j], 1 - a, b);
                int yFlipB = Gates.eval(layer.gateTypes[j], a, 1 - b);
                int deltaA = (yFlipA != y) ? 1 : 0;
                int deltaB = (yFlipB != y) ? 1 : 0;
                int sum = deltaA + deltaB;
                double wA, wB;
                if (sum == 0) {
                    // Neither single flip helps; split equally.
                    wA = 0.5;
                    wB = 0.5;
                } else {
                    wA = (double) deltaA / sum;
                    wB = (double) deltaB / sum;
                }
                inErr[aPos] += e * wA;
                inErr[bPos] += e * wB;
            }

            errAtTier[L] = inErr.clone();
            errProb = inErr;
        }

        // Mutation: with prob flipRate per gate, snap each gate's truth-table bits to
        // the sign of its accumulator. Every step is a chance to update; gates that
        // weren't touched (no error) leave their accumulators decayed but unchanged in
        // sign, so no spurious flips.
        boolean[][] mutated = new boolean[nLayers][W];
        for (int L = 0; L < nLayers; L++) {
            RwLayer layer = network.layers[L];
            for (int j = 0; j < W; j++) {
                if (rng.nextDouble() >= flipRate) continue;
                byte oldType = layer.gateTypes[j];
                byte newType = oldType;
                for (int c = 0; c < 4; c++) {
                    int desired = gateAccum[L][j][c] > 0 ? 1 : (gateAccum[L][j][c] < 0 ? 0 : Gates.getBit(oldType, c));
                    newType = Gates.setBit(newType, c, desired);
                }
                if (newType != oldType) {
                    layer.gateTypes[j] = newType;
                    mutated[L][j] = true;
                }
            }
        }

        // Update per-tier error-probability accumulator (visualization only).
        for (int t = 0; t <= nLayers; t++) {
            double[] step = errAtTier[t];
            double[] acc = tierErrAccum[t];
            for (int i = 0; i < W; i++) {
                acc[i] = decay * acc[i] + step[i];
            }
        }

        // Snapshot accumulators for trace.
        double[][][] accumSnap = new double[nLayers][][];
        for (int L = 0; L < nLayers; L++) {
            accumSnap[L] = new double[W][];
            for (int j = 0; j < W; j++) accumSnap[L][j] = gateAccum[L][j].clone();
        }
        double[][] tierErrAccumSnap = new double[nLayers + 1][];
        for (int t = 0; t <= nLayers; t++) tierErrAccumSnap[t] = tierErrAccum[t].clone();

        return new RwStepTrace(
                Arrays.copyOf(input, W),
                Arrays.copyOf(target, W),
                tierValues,
                errAtTier,
                tierErrAccumSnap,
                mutated,
                accumSnap);
    }
}
