package sibarum.neuralchemy.brnn;

import java.util.random.RandomGenerator;

/**
 * One BRN layer per the addendum: three sequential stages.
 *
 * <ol>
 *   <li><b>Static XOR</b> — {@code xor[i] = in[i] XOR in[(i+1) mod W]}. Wiring is
 *       fixed to neighbor pairs; never learned.</li>
 *   <li><b>Dynamic NOT</b> — per-output learnable {@code notFlags[i]}. If set, the
 *       XOR result is inverted.</li>
 *   <li><b>Dynamic Routing</b> — {@code route[]} is a learnable permutation; output
 *       {@code i} reads from gate position {@code route[i]} of the post-NOT stage.</li>
 * </ol>
 *
 * Width is uniform: {@code nIn == nOut}. Default state is identity routing with all
 * NOT flags clear; call {@link #randomInit(RandomGenerator)} to randomize both.
 */
public final class BrnLayer {

    public final int nIn;
    public final int nOut;
    public final int[] route;       // permutation of [0..nOut-1]
    public final byte[] notFlags;   // 0 or 1, per output

    private final byte[] postNotScratch;

    public BrnLayer(int nIn, int nOut) {
        if (nIn != nOut) throw new IllegalArgumentException("BrnLayer requires nIn == nOut");
        this.nIn = nIn;
        this.nOut = nOut;
        this.route = new int[nOut];
        this.notFlags = new byte[nOut];
        this.postNotScratch = new byte[nOut];
        for (int i = 0; i < nOut; i++) route[i] = i; // identity permutation by default
    }

    public void randomInit(RandomGenerator rng) {
        for (int i = 0; i < nOut; i++) route[i] = i;
        for (int i = nOut - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int tmp = route[i];
            route[i] = route[j];
            route[j] = tmp;
        }
        for (int i = 0; i < nOut; i++) {
            notFlags[i] = (byte) (rng.nextBoolean() ? 1 : 0);
        }
    }

    public void forward(byte[] in, byte[] out) {
        for (int i = 0; i < nOut; i++) {
            int xor = (in[i] ^ in[(i + 1) % nIn]) & 1;
            postNotScratch[i] = (byte) ((notFlags[i] != 0 ? (xor ^ 1) : xor) & 1);
        }
        for (int i = 0; i < nOut; i++) {
            out[i] = postNotScratch[route[i]];
        }
    }

    /** 2-way swap of routing entries — primary, monotone rewire when an
     *  opposite-value flagged pair exists. */
    public void swapTwo(int i, int j) {
        int tmp = route[i];
        route[i] = route[j];
        route[j] = tmp;
    }

    /** 3-cycle of routing entries per addendum step 3a:
     *  {@code R[a]←R[b], R[b]←R[c], R[c]←R[a]}. Preserves the permutation. */
    public void rotateThree(int a, int b, int c) {
        int oldA = route[a];
        route[a] = route[b];
        route[b] = route[c];
        route[c] = oldA;
    }

    public void flipNot(int i) {
        notFlags[i] = (byte) (notFlags[i] ^ 1);
    }
}
