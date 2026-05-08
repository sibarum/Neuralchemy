package sibarum.neuralchemy.brnn;

import sibarum.neuralchemy.nn.Network;

/**
 * Sequence of {@link BrnLayer}s with pre-allocated activation buffers between them.
 * {@code activations[0]} holds a copy of the input, and {@code activations[L+1]}
 * holds the output of layer {@code L}.
 */
public final class BrnNetwork implements Network {

    public final BrnLayer[] layers;
    private final byte[][] activations;

    public BrnNetwork(BrnLayer... layers) {
        if (layers.length == 0) throw new IllegalArgumentException("at least one layer required");
        for (int i = 1; i < layers.length; i++) {
            if (layers[i].nIn != layers[i - 1].nOut) {
                throw new IllegalArgumentException(
                        "layer " + i + " input width " + layers[i].nIn
                                + " does not match layer " + (i - 1) + " output width " + layers[i - 1].nOut);
            }
        }
        this.layers = layers;
        this.activations = new byte[layers.length + 1][];
        this.activations[0] = new byte[layers[0].nIn];
        for (int i = 0; i < layers.length; i++) {
            this.activations[i + 1] = new byte[layers[i].nOut];
        }
    }

    @Override public int inputBits()  { return layers[0].nIn; }
    @Override public int outputBits() { return layers[layers.length - 1].nOut; }

    @Override
    public void forward(byte[] input, byte[] output) {
        System.arraycopy(input, 0, activations[0], 0, input.length);
        for (int i = 0; i < layers.length; i++) {
            layers[i].forward(activations[i], activations[i + 1]);
        }
        System.arraycopy(activations[layers.length], 0, output, 0, output.length);
    }

    public byte[] inputAt(int layerIdx)  { return activations[layerIdx]; }
    public byte[] outputAt(int layerIdx) { return activations[layerIdx + 1]; }
}
