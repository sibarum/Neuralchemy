package sibarum.neuralchemy.nn.datasets;

import sibarum.neuralchemy.nn.Dataset;

/**
 * 4-bit unsigned adder + auxiliary functions in a width-8 frame, with all 8 output
 * bits carrying meaningful (non-constant) signal so neither network's architecture
 * is unfairly penalized by constant-zero padding bits.
 *
 * <p>Input layout (8 bits): bits 0–3 are operand A (LSB first), bits 4–7 are operand B.
 *
 * <p>Output layout (8 bits):
 * <ul>
 *   <li>bits 0–3: 4-bit sum (low nibble of A+B), LSB first</li>
 *   <li>bit 4: carry-out (the 5th bit of the sum)</li>
 *   <li>bit 5: parity of A (XOR of A's 4 bits)</li>
 *   <li>bit 6: parity of B</li>
 *   <li>bit 7: A &gt; B (1 iff A is strictly greater)</li>
 * </ul>
 *
 * <p>Enumerates all 256 inputs.
 */
public final class AdderDataset implements Dataset {

    public static final int WIDTH = 8;
    private static final int N = 256;

    private final byte[][] inputs = new byte[N][WIDTH];
    private final byte[][] targets = new byte[N][WIDTH];

    public AdderDataset() {
        for (int i = 0; i < N; i++) {
            byte[] in = inputs[i];
            for (int b = 0; b < WIDTH; b++) in[b] = (byte) ((i >> b) & 1);
            int a = i & 0xF;
            int b = (i >> 4) & 0xF;
            int sum = a + b;
            byte[] tgt = targets[i];
            for (int j = 0; j < 5; j++) tgt[j] = (byte) ((sum >> j) & 1);
            tgt[5] = (byte) parity(a);
            tgt[6] = (byte) parity(b);
            tgt[7] = (byte) (a > b ? 1 : 0);
        }
    }

    private static int parity(int x) {
        x ^= x >> 2;
        x ^= x >> 1;
        return x & 1;
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
