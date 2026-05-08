package sibarum.neuralchemy.nn;

/**
 * Indexed (input, target) pair source. {@link #sample} writes into caller-owned
 * buffers so iteration is allocation-free.
 */
public interface Dataset {

    int size();

    int inputBits();

    int outputBits();

    void sample(int idx, byte[] inputOut, byte[] targetOut);
}
