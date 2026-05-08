package sibarum.neuralchemy.rwnn;

import java.util.random.RandomGenerator;

/**
 * One Ring-Weave layer. Each gate at position {@code j} reads inputs from positions
 * {@code (j-1+W) mod W} and {@code (j+1) mod W} of the previous tier — a wraparound
 * "ring" pattern. Each gate's behavior is fully specified by its 4-bit truth table,
 * stored in {@code gateTypes[j]} (low 4 bits used; high 4 bits ignored).
 *
 * <p>Forward: {@code out[j] = Gates.eval(gateTypes[j], in[(j-1+W) mod W], in[(j+1) mod W])}.
 */
public final class RwLayer {

    public final int nIn;
    public final int nOut;
    public final byte[] gateTypes;

    public RwLayer(int nIn, int nOut) {
        if (nIn != nOut) throw new IllegalArgumentException("RwLayer requires nIn == nOut");
        this.nIn = nIn;
        this.nOut = nOut;
        this.gateTypes = new byte[nOut];
    }

    public void randomInit(RandomGenerator rng) {
        for (int i = 0; i < nOut; i++) {
            gateTypes[i] = (byte) (rng.nextInt(16));
        }
    }

    public void forward(byte[] in, byte[] out) {
        int W = nIn;
        for (int j = 0; j < nOut; j++) {
            int a = in[(j - 1 + W) % W] & 1;
            int b = in[(j + 1) % W] & 1;
            out[j] = (byte) Gates.eval(gateTypes[j], a, b);
        }
    }

    /** Position of the "left" input source (wrap-around) for gate {@code j}. */
    public int leftSource(int j) {
        return (j - 1 + nIn) % nIn;
    }

    /** Position of the "right" input source (wrap-around) for gate {@code j}. */
    public int rightSource(int j) {
        return (j + 1) % nIn;
    }
}
