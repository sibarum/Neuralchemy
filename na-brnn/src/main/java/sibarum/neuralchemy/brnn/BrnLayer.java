package sibarum.neuralchemy.brnn;

import java.util.random.RandomGenerator;

/**
 * One BRN layer: gate computes XOR of three input bits.
 *
 * <ol>
 *   <li><b>Static neighbor pair</b> — bits at positions {@code i} and {@code (i+1) mod W}.</li>
 *   <li><b>Dynamic NOT-ref</b> — {@code notRef[i]} is a learnable index into the layer's
 *       input. The bit at that position becomes the third XOR operand. Setting it to a
 *       bit currently holding 1 makes the gate effectively invert; setting it to a bit
 *       currently holding 0 makes the gate effectively pass through.</li>
 *   <li><b>Dynamic Routing</b> — {@code route[]} is a learnable permutation; output
 *       {@code i} reads from gate position {@code route[i]} of the post-XOR stage.</li>
 * </ol>
 *
 * <p>The gate output before routing is {@code in[i] XOR in[(i+1) mod W] XOR in[notRef[i]]} —
 * a 3-input XOR. Cancellation when {@code notRef[i]} equals {@code i} or {@code (i+1) mod W}
 * lets the gate degenerate to a 1-input passthrough; otherwise it is a true 3-input XOR
 * spanning whichever input bit notRef references.
 *
 * <p>Width is uniform: {@code nIn == nOut}.
 */
public final class BrnLayer {

    public final int nIn;
    public final int nOut;
    public final int[] route;     // permutation of [0..nOut-1]
    public final int[] notRef;    // notRef[gateIdx] ∈ [0..nIn-1] — index of third XOR operand

    private final byte[] postGateScratch;

    public BrnLayer(int nIn, int nOut) {
        if (nIn != nOut) throw new IllegalArgumentException("BrnLayer requires nIn == nOut");
        this.nIn = nIn;
        this.nOut = nOut;
        this.route = new int[nOut];
        this.notRef = new int[nOut];
        this.postGateScratch = new byte[nOut];
        for (int i = 0; i < nOut; i++) {
            route[i] = i;       // identity permutation
            notRef[i] = i;      // self-reference → gate = in[(i+1) mod W] (cancels neighbor XOR)
        }
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
            notRef[i] = rng.nextInt(nIn);
        }
    }

    public void forward(byte[] in, byte[] out) {
        for (int i = 0; i < nOut; i++) {
            int v = (in[i] ^ in[(i + 1) % nIn] ^ in[notRef[i]]) & 1;
            postGateScratch[i] = (byte) v;
        }
        for (int i = 0; i < nOut; i++) {
            out[i] = postGateScratch[route[i]];
        }
    }

    /** 2-way swap of routing entries — primary, monotone rewire when an
     *  opposite-value flagged pair exists. */
    public void swapTwo(int i, int j) {
        int tmp = route[i];
        route[i] = route[j];
        route[j] = tmp;
    }

    /** 3-cycle of routing entries (legacy fallback). */
    public void rotateThree(int a, int b, int c) {
        int oldA = route[a];
        route[a] = route[b];
        route[b] = route[c];
        route[c] = oldA;
    }

    /**
     * Redirect the NOT-ref of gate {@code gateIdx} to a layer-input bit whose value is
     * the opposite of the current notRef target. This deterministically flips the gate's
     * output for the current sample (since the third XOR operand toggles). Returns true
     * on success; false if the layer input is uniform (no opposite-value bit available).
     */
    public boolean redirectNot(int gateIdx, byte[] layerInput, RandomGenerator rng) {
        byte currentVal = (byte) (layerInput[notRef[gateIdx]] & 1);
        byte targetVal = (byte) (currentVal ^ 1);
        int start = rng.nextInt(nIn);
        for (int t = 0; t < nIn; t++) {
            int candidate = (start + t) % nIn;
            if (candidate == notRef[gateIdx]) continue;
            if ((layerInput[candidate] & 1) == targetVal) {
                notRef[gateIdx] = candidate;
                return true;
            }
        }
        return false;
    }
}
