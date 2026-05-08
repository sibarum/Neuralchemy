package sibarum.neuralchemy.nn;

/**
 * A discrete network that maps an {@code inputBits()}-bit input vector to an
 * {@code outputBits()}-bit output vector. Implementations should be allocation-free
 * in {@link #forward}; the caller owns both buffers.
 */
public interface Network {

    int inputBits();

    int outputBits();

    void forward(byte[] input, byte[] output);
}
