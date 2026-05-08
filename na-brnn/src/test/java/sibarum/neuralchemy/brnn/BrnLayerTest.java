package sibarum.neuralchemy.brnn;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class BrnLayerTest {

    @Test
    void forwardComputesXorPerEntry() {
        BrnLayer layer = new BrnLayer(4, 2);
        layer.routes.setEntry(0, 0, 1);
        layer.routes.setEntry(1, 2, 3);

        byte[] in  = {1, 0, 1, 1};
        byte[] out = new byte[2];
        layer.forward(in, out);

        assertEquals(1, out[0]);
        assertEquals(0, out[1]);
    }

    @Test
    void rewireBitFlipsOutputWhenInputProvidesACandidate() {
        BrnLayer layer = new BrnLayer(4, 1);
        layer.routes.setEntry(0, 0, 1);

        byte[] in  = {1, 1, 0, 0};
        byte[] out = new byte[1];
        layer.forward(in, out);
        assertEquals(0, out[0]);

        byte result = layer.rewireBit(0, (byte) 1, 64, RandomGenerator.of("L64X128MixRandom"));
        assertEquals(1, result);
        assertEquals(1, (in[layer.routes.a[0]] ^ in[layer.routes.b[0]]) & 1);
    }

    @Test
    void rewireBitNoOpWhenAlreadyCorrect() {
        BrnLayer layer = new BrnLayer(4, 1);
        layer.routes.setEntry(0, 0, 1);

        byte[] in  = {1, 0, 0, 0};
        byte[] out = new byte[1];
        layer.forward(in, out);
        assertEquals(1, out[0]);

        int oldA = layer.routes.a[0];
        int oldB = layer.routes.b[0];
        byte result = layer.rewireBit(0, (byte) 1, 64, RandomGenerator.of("L64X128MixRandom"));
        assertEquals(1, result);
        assertEquals(oldA, layer.routes.a[0]);
        assertEquals(oldB, layer.routes.b[0]);
    }

    @Test
    void rewireBitGivesUpWhenNoCandidateExists() {
        // All inputs are equal → every gate yields 0; flipping to 1 is impossible.
        BrnLayer layer = new BrnLayer(4, 1);
        layer.routes.setEntry(0, 0, 1);

        byte[] in  = {0, 0, 0, 0};
        byte[] out = new byte[1];
        layer.forward(in, out);

        byte result = layer.rewireBit(0, (byte) 1, 64, RandomGenerator.of("L64X128MixRandom"));
        assertEquals(0, result);
    }
}
