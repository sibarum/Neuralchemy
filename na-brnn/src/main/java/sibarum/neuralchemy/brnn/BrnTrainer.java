package sibarum.neuralchemy.brnn;

import sibarum.neuralchemy.bits.Bits;

import java.util.random.RandomGenerator;

/**
 * Stochastic-rewire trainer per BRN design doc §3.3.
 *
 * <p>Algorithm per step:
 * <ol>
 *   <li>Forward the input.
 *   <li>Compute output error mask {@code error = output XOR target}.
 *   <li>For each layer (last to first), for each flagged bit, with probability {@code flipRate}
 *       attempt a rewire that flips that bit. The same error mask is applied at every layer
 *       (no credit assignment, per §3.2). MVP assumes uniform layer width.
 * </ol>
 */
public final class BrnTrainer {

    public final BrnNetwork network;
    public double flipRate;
    public int rewireTrials = 8;

    private final RandomGenerator rng;
    private final byte[] outputScratch;
    private final byte[] errorScratch;

    public BrnTrainer(BrnNetwork network, double flipRate, RandomGenerator rng) {
        int w = network.outputBits();
        for (BrnLayer l : network.layers) {
            if (l.nIn != w || l.nOut != w) {
                throw new IllegalArgumentException(
                        "BrnTrainer MVP assumes uniform layer width; got "
                                + l.nIn + "->" + l.nOut + " in a " + w + "-wide network");
            }
        }
        this.network = network;
        this.flipRate = flipRate;
        this.rng = rng;
        this.outputScratch = new byte[w];
        this.errorScratch = new byte[w];
    }

    public void step(byte[] input, byte[] target) {
        network.forward(input, outputScratch);
        Bits.xor(outputScratch, target, errorScratch);

        for (int L = network.layers.length - 1; L >= 0; L--) {
            BrnLayer layer = network.layers[L];
            byte[] layerOut = network.outputAt(L);
            for (int i = 0; i < layer.nOut; i++) {
                if (errorScratch[i] != 0 && rng.nextDouble() < flipRate) {
                    byte desired = (byte) (layerOut[i] ^ 1);
                    layer.rewireBit(i, desired, rewireTrials, rng);
                }
            }
        }
    }
}
