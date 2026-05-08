package sibarum.neuralchemy.brnn;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class BrnLayerTest {

    @Test
    void forwardComputes3InputXor() {
        BrnLayer layer = new BrnLayer(4, 4);
        // Default state: route = identity, notRef[i] = i (self-ref → cancels neighbor i)
        // Override notRef so that gate truly is a 3-input XOR over distinct positions.
        layer.notRef[0] = 2; // gate 0 = in[0] XOR in[1] XOR in[2]
        layer.notRef[1] = 3; // gate 1 = in[1] XOR in[2] XOR in[3]

        byte[] in  = {1, 0, 1, 1};
        byte[] out = new byte[4];
        layer.forward(in, out);

        assertEquals(0, out[0]); // 1 ^ 0 ^ 1 = 0
        assertEquals(0, out[1]); // 0 ^ 1 ^ 1 = 0
        // Gate 2 has notRef[2] = 2 by default (self-cancel) → in[2] ^ in[3] ^ in[2] = in[3] = 1
        assertEquals(1, out[2]);
        // Gate 3 has notRef[3] = 3 → in[3] ^ in[0] ^ in[3] = in[0] = 1
        assertEquals(1, out[3]);
    }

    @Test
    void notRefDefaultIsSelfReferenceCancellingNeighbor() {
        // Default notRef[i] = i means gate computes in[i] ^ in[(i+1)%W] ^ in[i] = in[(i+1)%W].
        BrnLayer layer = new BrnLayer(4, 4);
        byte[] in  = {1, 0, 1, 1};
        byte[] out = new byte[4];
        layer.forward(in, out);
        // out[i] = in[(i+1) % 4]
        assertArrayEquals(new byte[]{0, 1, 1, 1}, out);
    }

    @Test
    void routingPermutesOutputs() {
        BrnLayer layer = new BrnLayer(4, 4);
        layer.route[0] = 3;
        layer.route[1] = 2;
        layer.route[2] = 1;
        layer.route[3] = 0;
        // Default notRef[i] = i → gate output = in[(i+1) % 4]
        byte[] in  = {1, 0, 1, 1};
        byte[] out = new byte[4];
        layer.forward(in, out);
        // pre-routing values = {0, 1, 1, 1}
        // After reverse routing: out[0]=pre[3]=1, out[1]=pre[2]=1, out[2]=pre[1]=1, out[3]=pre[0]=0
        assertArrayEquals(new byte[]{1, 1, 1, 0}, out);
    }

    @Test
    void swapTwoExchangesRouteEntries() {
        BrnLayer layer = new BrnLayer(4, 4);
        layer.swapTwo(0, 2);
        assertEquals(2, layer.route[0]);
        assertEquals(0, layer.route[2]);
        assertEquals(1, layer.route[1]);
        assertEquals(3, layer.route[3]);
    }

    @Test
    void redirectNotPicksOppositeValueBit() {
        BrnLayer layer = new BrnLayer(4, 4);
        layer.notRef[0] = 0; // currently references bit 0

        byte[] layerInput = {1, 0, 1, 0};
        // bit 0 is 1; redirect should pick a bit holding 0 (either index 1 or 3).
        boolean ok = layer.redirectNot(0, layerInput, RandomGenerator.of("L64X128MixRandom"));
        assertTrue(ok);
        assertTrue(layer.notRef[0] == 1 || layer.notRef[0] == 3);
        assertEquals(0, layerInput[layer.notRef[0]]);
    }

    @Test
    void redirectNotFailsOnUniformInput() {
        BrnLayer layer = new BrnLayer(4, 4);
        layer.notRef[0] = 0;

        byte[] layerInput = {1, 1, 1, 1};
        boolean ok = layer.redirectNot(0, layerInput, RandomGenerator.of("L64X128MixRandom"));
        assertFalse(ok);
        assertEquals(0, layer.notRef[0]); // unchanged
    }
}
