package sibarum.neuralchemy.brnn;

import java.util.random.RandomGenerator;

/**
 * Two parallel int arrays: for each output bit {@code i}, {@code a[i]} and {@code b[i]}
 * are the source indices in the layer's input. Distinct sources are enforced — XOR(x,x)=0
 * is degenerate and would lock the bit to 0.
 *
 * <p>Mutable, joml-style: public fields, in-place mutation, allocation-free.
 */
public final class RoutingMatrix {

    public final int nOut;
    public final int[] a;
    public final int[] b;

    public RoutingMatrix(int nOut) {
        this.nOut = nOut;
        this.a = new int[nOut];
        this.b = new int[nOut];
    }

    public void randomInit(int nIn, RandomGenerator rng) {
        if (nIn < 2) throw new IllegalArgumentException("need nIn >= 2 for distinct sources");
        for (int i = 0; i < nOut; i++) {
            a[i] = rng.nextInt(nIn);
            int x;
            do { x = rng.nextInt(nIn); } while (x == a[i]);
            b[i] = x;
        }
    }

    public void setEntry(int i, int srcA, int srcB) {
        a[i] = srcA;
        b[i] = srcB;
    }
}
