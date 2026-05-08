package sibarum.neuralchemy.brnn;

import java.util.random.RandomGenerator;

/**
 * One BRN layer. Forward is {@code out[i] = in[a[i]] ^ in[b[i]]}. The layer caches
 * the last input it saw so {@link #rewireBit} can evaluate candidate sources without
 * the trainer having to thread the input through.
 */
public final class BrnLayer {

    public final int nIn;
    public final int nOut;
    public final RoutingMatrix routes;

    private byte[] lastInput;

    public BrnLayer(int nIn, int nOut) {
        this.nIn = nIn;
        this.nOut = nOut;
        this.routes = new RoutingMatrix(nOut);
    }

    public void forward(byte[] in, byte[] out) {
        this.lastInput = in;
        for (int i = 0; i < nOut; i++) {
            out[i] = (byte) ((in[routes.a[i]] ^ in[routes.b[i]]) & 1);
        }
    }

    /**
     * Try to rewire output bit {@code i} so its gate yields {@code desired} on the
     * input most recently seen by {@link #forward}. Picks a random slot (A or B) and
     * a random new source for it; accepts the first candidate whose XOR matches
     * {@code desired}. Up to {@code maxTrials} attempts. Returns the post-rewire
     * output bit (== {@code desired} on success, the original output otherwise).
     */
    public byte rewireBit(int i, byte desired, int maxTrials, RandomGenerator rng) {
        byte aVal = lastInput[routes.a[i]];
        byte bVal = lastInput[routes.b[i]];
        byte current = (byte) ((aVal ^ bVal) & 1);
        if (current == desired) return current;

        for (int t = 0; t < maxTrials; t++) {
            boolean replaceA = rng.nextBoolean();
            int otherSlot = replaceA ? routes.b[i] : routes.a[i];
            int newSrc;
            do { newSrc = rng.nextInt(nIn); } while (newSrc == otherSlot);

            byte newVal = (byte) ((lastInput[newSrc] ^ (replaceA ? bVal : aVal)) & 1);
            if (newVal == desired) {
                if (replaceA) routes.a[i] = newSrc;
                else routes.b[i] = newSrc;
                return desired;
            }
        }
        return current;
    }
}
