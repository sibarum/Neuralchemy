package sibarum.neuralchemy.nn.datasets;

import sibarum.neuralchemy.nn.Dataset;

/**
 * Multi-multiplexer dataset in a width-8 frame, designed so every output bit carries
 * meaningful signal (no constant-output padding).
 *
 * <p>Input layout (8 bits): bits 0–1 are a 2-bit selector S, bits 2–7 are 6 data bits.
 *
 * <p>Output layout (8 bits, 8 different functions of inputs):
 * <ul>
 *   <li>bit 0: standard 6-bit mux — data[S] (i.e., bit (2 + S) of the input)</li>
 *   <li>bit 1: data[(S+1) mod 4]</li>
 *   <li>bit 2: data[(S+2) mod 4]</li>
 *   <li>bit 3: data[(S+3) mod 4]</li>
 *   <li>bit 4: data[4] when S=0, data[5] when S=1, data[4] when S=2, data[5] when S=3</li>
 *   <li>bit 5: parity of the 4 selector-addressable data bits</li>
 *   <li>bit 6: NOT (data[S]) — inverted mux</li>
 *   <li>bit 7: data[S] AND data[(S+1) mod 4]</li>
 * </ul>
 *
 * <p>Enumerates all 256 possible 8-bit inputs.
 */
public final class MultiplexerDataset implements Dataset {

    public static final int WIDTH = 8;
    private static final int N = 256;

    private final byte[][] inputs = new byte[N][WIDTH];
    private final byte[][] targets = new byte[N][WIDTH];

    public MultiplexerDataset() {
        for (int i = 0; i < N; i++) {
            byte[] in = inputs[i];
            for (int b = 0; b < WIDTH; b++) in[b] = (byte) ((i >> b) & 1);
            int s = (in[0] & 1) | ((in[1] & 1) << 1);
            int d0 = in[2 + (s % 4)] & 1;
            int d1 = in[2 + ((s + 1) % 4)] & 1;
            int d2 = in[2 + ((s + 2) % 4)] & 1;
            int d3 = in[2 + ((s + 3) % 4)] & 1;
            int dExtra = in[(s % 2 == 0) ? 6 : 7] & 1;
            int parity = d0 ^ d1 ^ d2 ^ d3;

            byte[] tgt = targets[i];
            tgt[0] = (byte) d0;
            tgt[1] = (byte) d1;
            tgt[2] = (byte) d2;
            tgt[3] = (byte) d3;
            tgt[4] = (byte) dExtra;
            tgt[5] = (byte) parity;
            tgt[6] = (byte) (d0 ^ 1);
            tgt[7] = (byte) (d0 & d1);
        }
    }

    @Override public int size()       { return N; }
    @Override public int inputBits()  { return WIDTH; }
    @Override public int outputBits() { return WIDTH; }

    @Override
    public void sample(int idx, byte[] inputOut, byte[] targetOut) {
        System.arraycopy(inputs[idx], 0, inputOut, 0, WIDTH);
        System.arraycopy(targets[idx], 0, targetOut, 0, WIDTH);
    }
}
