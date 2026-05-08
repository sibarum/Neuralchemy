package sibarum.neuralchemy.brnn;

import sibarum.neuralchemy.bits.Bits;
import sibarum.neuralchemy.nn.Dataset;

import java.util.random.RandomGenerator;

/**
 * Width-W dataset where {@code target[i] = input[i] XOR input[(i+1) mod W]}.
 * Native 1-layer task under the addendum architecture (identity route + zero
 * notFlags solves it exactly).
 */
public final class NeighborXorDataset implements Dataset {

    private final byte[][] inputs;
    private final byte[][] targets;
    public final int width;

    public NeighborXorDataset(int size, int width, RandomGenerator rng) {
        this.width = width;
        this.inputs = new byte[size][width];
        this.targets = new byte[size][width];
        for (int i = 0; i < size; i++) {
            Bits.randomFill(inputs[i], rng);
            for (int j = 0; j < width; j++) {
                targets[i][j] = (byte) ((inputs[i][j] ^ inputs[i][(j + 1) % width]) & 1);
            }
        }
    }

    @Override public int size()       { return inputs.length; }
    @Override public int inputBits()  { return width; }
    @Override public int outputBits() { return width; }

    @Override
    public void sample(int idx, byte[] inputOut, byte[] targetOut) {
        System.arraycopy(inputs[idx], 0, inputOut, 0, width);
        System.arraycopy(targets[idx], 0, targetOut, 0, width);
    }
}
