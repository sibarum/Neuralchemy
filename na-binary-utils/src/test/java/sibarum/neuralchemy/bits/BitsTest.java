package sibarum.neuralchemy.bits;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class BitsTest {

    @Test
    void xorMatchesElementwise() {
        byte[] a   = {1, 0, 1, 1, 0};
        byte[] b   = {1, 1, 1, 0, 0};
        byte[] out = new byte[5];
        Bits.xor(a, b, out);
        assertArrayEquals(new byte[]{0, 1, 0, 1, 0}, out);
    }

    @Test
    void xorIntoMutatesDst() {
        byte[] dst = {1, 0, 1, 1};
        byte[] src = {0, 1, 1, 0};
        Bits.xorInto(dst, src);
        assertArrayEquals(new byte[]{1, 1, 0, 1}, dst);
    }

    @Test
    void hammingWeightCounts1s() {
        assertEquals(3, Bits.hammingWeight(new byte[]{1, 0, 1, 0, 1}));
        assertEquals(0, Bits.hammingWeight(new byte[]{0, 0, 0}));
    }

    @Test
    void hammingDistanceCountsDiffs() {
        assertEquals(2, Bits.hammingDistance(new byte[]{1, 0, 1, 1}, new byte[]{1, 1, 1, 0}));
        assertEquals(0, Bits.hammingDistance(new byte[]{1, 0, 1}, new byte[]{1, 0, 1}));
    }

    @Test
    void randomFillStaysWithin01() {
        byte[] v = new byte[1024];
        Bits.randomFill(v, RandomGenerator.of("L64X128MixRandom"));
        for (byte b : v) assertTrue(b == 0 || b == 1, "bit value out of range: " + b);
    }

    @Test
    void fillAcceptsAnyIntButOnlyKeepsLowBit() {
        byte[] v = new byte[4];
        Bits.fill(v, 7);
        assertArrayEquals(new byte[]{1, 1, 1, 1}, v);
        Bits.fill(v, 4);
        assertArrayEquals(new byte[]{0, 0, 0, 0}, v);
    }

    @Test
    void copyDuplicatesContent() {
        byte[] src = {1, 0, 1, 1};
        byte[] dst = new byte[4];
        Bits.copy(src, dst);
        assertArrayEquals(src, dst);
    }
}
