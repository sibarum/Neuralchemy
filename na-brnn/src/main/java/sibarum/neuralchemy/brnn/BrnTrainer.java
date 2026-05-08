package sibarum.neuralchemy.brnn;

import sibarum.neuralchemy.bits.Bits;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Stochastic-rewire trainer with per-(layer, bit) decaying flag accumulators and a
 * primary 2-way swap operation (monotone) plus a 3-way fallback (addendum spec).
 *
 * <p>Per step, for each layer L from last to first, with mask at L's post-routing tier:
 * <ol type="a">
 *   <li>Update {@code flagAccum[L][i] = decay * flagAccum[L][i] + (mask[i] ? 1 : 0)}.</li>
 *   <li><b>2-way swap</b> (primary, monotone): if any opposite-value flagged pair
 *       exists, with prob {@code flipRate * swapBias} pick the pair with the highest
 *       accumulator sum, swap their routes, and exchange their cached output values.
 *       Each swap fixes 2 flagged bits with no effect on other bits at this layer.</li>
 *   <li><b>3-way swap</b> (fallback, when no 2-way pair exists and {@code M >= 3}):
 *       fire with prob {@code M * flipRate * swapBias}, pick top-3 by accumulator,
 *       rotate their routes.</li>
 *   <li><b>If any swap fired, reset all accumulators across all layers</b> — wait
 *       for new evidence before the next mutation.</li>
 *   <li>Translate remaining flags through routing.</li>
 *   <li><b>NOT flip</b>: per pre-routing flag at gate {@code j}, with prob
 *       {@code flipRate * (1 - swapBias) * accum/saturation} flip the NOT and reset
 *       that output bit's count.</li>
 *   <li>Trace remaining flags through XOR (50/50 to {@code j} or {@code (j+1) mod W}).</li>
 * </ol>
 */
public final class BrnTrainer {

    public final BrnNetwork network;
    public double flipRate;
    public double decay    = 0.95;   // EMA decay for flagAccum and mutationRate
    public double swapBias = 0.5;    // 0 = NOT-only, 1 = swap-only

    private final RandomGenerator rng;
    private final byte[] outputScratch;
    private byte[] currentMask;
    private byte[] nextMask;
    private final double[][] flagAccum;
    private final double[] mutationRate;

    public BrnTrainer(BrnNetwork network, double flipRate, RandomGenerator rng) {
        int w = network.outputBits();
        for (BrnLayer l : network.layers) {
            if (l.nIn != w || l.nOut != w) {
                throw new IllegalArgumentException(
                        "BrnTrainer requires uniform layer width; got "
                                + l.nIn + "->" + l.nOut + " in a " + w + "-wide network");
            }
        }
        this.network = network;
        this.flipRate = flipRate;
        this.rng = rng;
        this.outputScratch = new byte[w];
        this.currentMask = new byte[w];
        this.nextMask = new byte[w];
        this.flagAccum = new double[network.layers.length][w];
        this.mutationRate = new double[network.layers.length];
    }

    public void step(byte[] input, byte[] target) {
        stepTraced(input, target);
    }

    public StepTrace stepTraced(byte[] input, byte[] target) {
        int W = network.outputBits();
        int nLayers = network.layers.length;
        double saturation = 1.0 / Math.max(1e-9, 1.0 - decay);

        byte[][] tierValues = new byte[nLayers + 1][W];
        System.arraycopy(input, 0, tierValues[0], 0, W);
        for (int i = 0; i < nLayers; i++) {
            network.layers[i].forward(tierValues[i], tierValues[i + 1]);
        }

        byte[][] flagged = new byte[nLayers][W];
        byte[][] flipped = new byte[nLayers][W];

        Bits.xor(tierValues[nLayers], target, currentMask);

        for (int L = nLayers - 1; L >= 0; L--) {
            BrnLayer layer = network.layers[L];
            byte[] layerOut = tierValues[L + 1];

            System.arraycopy(currentMask, 0, flagged[L], 0, W);

            for (int i = 0; i < W; i++) {
                flagAccum[L][i] = decay * flagAccum[L][i] + (currentMask[i] != 0 ? 1.0 : 0.0);
            }

            int mutations = 0;
            boolean swapFired = false;

            // 2-way swap primary: opposite-value flagged pair, prob flipRate * swapBias
            int[] pair = findBest2WayPair(currentMask, layerOut, flagAccum[L], W);
            if (pair != null && rng.nextDouble() < flipRate * swapBias) {
                int i = pair[0], j = pair[1];
                layer.swapTwo(i, j);
                byte tmp = layerOut[i];
                layerOut[i] = layerOut[j];
                layerOut[j] = tmp;
                currentMask[i] = currentMask[j] = 0;
                flipped[L][i] = flipped[L][j] = 1;
                mutations += 2;
                swapFired = true;
            }

            // 3-way fallback disabled — 2-way (monotone) + NOT flip only.

            if (swapFired) {
                // Reset every accumulator in every layer — wait for new evidence.
                for (int l = 0; l < flagAccum.length; l++) {
                    Arrays.fill(flagAccum[l], 0);
                }
            }

            translateThroughRouting(layer, currentMask, nextMask);
            byte[] tmp = currentMask;
            currentMask = nextMask;
            nextMask = tmp;

            double notRate = flipRate * (1.0 - swapBias);
            for (int j = 0; j < W; j++) {
                if (currentMask[j] == 0) continue;
                int outBit = inverseRoute(layer, j);
                double prob = notRate * (flagAccum[L][outBit] / saturation);
                if (rng.nextDouble() <= prob) {
                    layer.flipNot(j);
                    currentMask[j] = 0;
                    flipped[L][outBit] = 1;
                    flagAccum[L][outBit] = 0;
                    mutations++;
                }
            }

            mutationRate[L] = decay * mutationRate[L] + mutations;

            if (L > 0) {
                traceBackXor(currentMask, nextMask, W);
                byte[] tmp2 = currentMask;
                currentMask = nextMask;
                nextMask = tmp2;
            }
        }

        double[][] flagAccumCopy = new double[nLayers][];
        for (int L = 0; L < nLayers; L++) flagAccumCopy[L] = flagAccum[L].clone();

        return new StepTrace(
                Arrays.copyOf(input, W),
                Arrays.copyOf(target, W),
                tierValues,
                flagged,
                flipped,
                flagAccumCopy,
                mutationRate.clone());
    }

    private static int[] findBest2WayPair(byte[] mask, byte[] layerOut, double[] accum, int W) {
        int bestI = -1, bestJ = -1;
        double bestScore = -1;
        for (int i = 0; i < W; i++) {
            if (mask[i] == 0) continue;
            for (int j = i + 1; j < W; j++) {
                if (mask[j] == 0) continue;
                if (layerOut[i] == layerOut[j]) continue;
                double score = accum[i] + accum[j];
                if (score > bestScore) {
                    bestScore = score;
                    bestI = i;
                    bestJ = j;
                }
            }
        }
        return bestJ < 0 ? null : new int[]{bestI, bestJ};
    }

    private static int countFlagged(byte[] mask, int W) {
        int n = 0;
        for (int i = 0; i < W; i++) if (mask[i] != 0) n++;
        return n;
    }

    private static int[] pickTop3ByAccum(byte[] mask, double[] accum, int W) {
        int b1 = -1, b2 = -1, b3 = -1;
        double v1 = -1, v2 = -1, v3 = -1;
        for (int i = 0; i < W; i++) {
            if (mask[i] == 0) continue;
            double a = accum[i];
            if (a > v1) {
                v3 = v2; b3 = b2;
                v2 = v1; b2 = b1;
                v1 = a;  b1 = i;
            } else if (a > v2) {
                v3 = v2; b3 = b2;
                v2 = a;  b2 = i;
            } else if (a > v3) {
                v3 = a;  b3 = i;
            }
        }
        return b3 < 0 ? null : new int[]{b1, b2, b3};
    }

    private static int inverseRoute(BrnLayer layer, int gateIdx) {
        for (int i = 0; i < layer.nOut; i++) {
            if (layer.route[i] == gateIdx) return i;
        }
        return -1;
    }

    private static void translateThroughRouting(BrnLayer layer, byte[] postRouting, byte[] preRouting) {
        Arrays.fill(preRouting, 0, layer.nOut, (byte) 0);
        for (int i = 0; i < layer.nOut; i++) {
            if (postRouting[i] != 0) {
                preRouting[layer.route[i]] = 1;
            }
        }
    }

    private void traceBackXor(byte[] mask, byte[] outMask, int W) {
        Arrays.fill(outMask, 0, W, (byte) 0);
        for (int j = 0; j < W; j++) {
            if (mask[j] != 0) {
                int dst = rng.nextDouble() > 0.5 ? (j + 1) % W : j;
                outMask[dst] = 1;
            }
        }
    }
}
