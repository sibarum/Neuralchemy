package sibarum.neuralchemy.brnn;

import sibarum.neuralchemy.bits.Bits;
import sibarum.neuralchemy.nn.Dataset;

import java.util.random.RandomGenerator;

/**
 * Width-W dataset where each target is the XOR-parity of the input replicated W times.
 * Replication lets the network use any of its W output bits to carry the prediction,
 * which keeps the convergence test forgiving without changing the underlying task.
 */
final class ParityDataset implements Dataset {

    private final byte[][] inputs;
    private final byte[][] targets;
    public final int width;

    ParityDataset(int size, int width, RandomGenerator rng) {
        this.width = width;
        this.inputs = new byte[size][width];
        this.targets = new byte[size][width];
        for (int i = 0; i < size; i++) {
            Bits.randomFill(inputs[i], rng);
            byte parity = 0;
            for (int j = 0; j < width; j++) parity ^= inputs[i][j];
            for (int j = 0; j < width; j++) targets[i][j] = parity;
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
