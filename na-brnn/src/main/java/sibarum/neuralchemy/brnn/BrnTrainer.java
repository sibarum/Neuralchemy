package sibarum.neuralchemy.brnn;

import sibarum.neuralchemy.bits.Bits;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Stochastic-rewire trainer for the 3-input-XOR variant: per-layer 2-way route swap,
 * 3-source XOR backtrace, and accumulator-gated NOT-ref redirection.
 *
 * <p>Per step, for each layer L from last to first, with mask at L's post-routing tier:
 * <ol type="a">
 *   <li>Update {@code flagAccum[L][i] = decay * flagAccum[L][i] + (mask[i] ? 1 : 0)}.</li>
 *   <li><b>2-way swap</b> (primary, monotone): if any opposite-value flagged pair
 *       exists, with prob {@code flipRate * swapBias} pick the pair with highest
 *       accumulator sum, swap routes, exchange cached values.</li>
 *   <li>If a swap fired, reset every accumulator across every layer.</li>
 *   <li>Translate remaining flags through routing.</li>
 *   <li><b>NOT-ref redirect</b>: per pre-routing flag at gate {@code j}, with prob
 *       {@code flipRate * (1 - swapBias) * accum/saturation}, redirect
 *       {@code notRef[j]} to a layer-input bit holding the opposite value. If the
 *       redirect succeeds, reset that output bit's accumulator and unflag.</li>
 *   <li>Trace remaining flags through the gate's three inputs at 33/33/33.</li>
 * </ol>
 */
public final class BrnTrainer {

    public final BrnNetwork network;
    public double flipRate;
    public double decay    = 0.95;
    public double swapBias = 0.5;

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
            byte[] layerIn = tierValues[L];

            System.arraycopy(currentMask, 0, flagged[L], 0, W);

            // Signed update: +1 if flagged (this bit was wrong), -1 if not (it was right).
            // Equilibrium ranges over [-saturation, +saturation]; positive means "consistently
            // wrong recently," negative means "consistently right." NOT redirect prob is
            // accum/saturation, so negative accum naturally suppresses mutations.
            for (int i = 0; i < W; i++) {
                flagAccum[L][i] = decay * flagAccum[L][i] + (currentMask[i] != 0 ? 1.0 : -1.0);
            }

            int mutations = 0;
            boolean swapFired = false;

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

            if (swapFired) {
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
                    boolean redirected = layer.redirectNot(j, layerIn, rng);
                    if (redirected) {
                        currentMask[j] = 0;
                        flipped[L][outBit] = 1;
                        flagAccum[L][outBit] = 0;
                        mutations++;
                    }
                }
            }

            mutationRate[L] = decay * mutationRate[L] + mutations;

            if (L > 0) {
                traceBackThreeSource(layer, currentMask, nextMask, W);
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
        // Require the pair's combined accumulator to be net positive — otherwise the bits
        // are usually correct and the current-step flag is transient noise; a swap on
        // them is unlikely to help across the dataset and likely to be reverted.
        int bestI = -1, bestJ = -1;
        double bestScore = 0;
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

    /** 33/33/33 trace through the gate's 3 inputs: positions {@code j}, {@code (j+1) mod W},
     *  and {@code notRef[j]}. */
    private void traceBackThreeSource(BrnLayer layer, byte[] mask, byte[] outMask, int W) {
        Arrays.fill(outMask, 0, W, (byte) 0);
        for (int j = 0; j < W; j++) {
            if (mask[j] == 0) continue;
            double r = rng.nextDouble();
            int dst;
            if (r < 1.0 / 3.0) dst = j;
            else if (r < 2.0 / 3.0) dst = (j + 1) % W;
            else dst = layer.notRef[j];
            outMask[dst] = 1;
        }
    }
}
