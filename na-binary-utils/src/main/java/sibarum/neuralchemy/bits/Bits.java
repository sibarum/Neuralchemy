package sibarum.neuralchemy.bits;

import java.util.Arrays;
import java.util.random.RandomGenerator;

/**
 * Allocation-free helpers over {@code byte[]} bit vectors. Each cell holds 0 or 1.
 * Higher bits of any byte are ignored on read and not preserved on write.
 */
public final class Bits {

    private Bits() {}

    public static void xor(byte[] a, byte[] b, byte[] out) {
        for (int i = 0; i < out.length; i++) out[i] = (byte) ((a[i] ^ b[i]) & 1);
    }

    public static void xorInto(byte[] dst, byte[] src) {
        for (int i = 0; i < dst.length; i++) dst[i] = (byte) ((dst[i] ^ src[i]) & 1);
    }

    public static int hammingWeight(byte[] v) {
        int n = 0;
        for (byte b : v) n += b & 1;
        return n;
    }

    public static int hammingDistance(byte[] a, byte[] b) {
        int n = 0;
        for (int i = 0; i < a.length; i++) n += (a[i] ^ b[i]) & 1;
        return n;
    }

    public static void randomFill(byte[] dst, RandomGenerator rng) {
        for (int i = 0; i < dst.length; i++) dst[i] = (byte) (rng.nextBoolean() ? 1 : 0);
    }

    public static void copy(byte[] src, byte[] dst) {
        System.arraycopy(src, 0, dst, 0, src.length);
    }

    public static void fill(byte[] dst, int value) {
        Arrays.fill(dst, (byte) (value & 1));
    }
}
