package sibarum.neuralchemy.brnn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BrnLayerTest {

    @Test
    void forwardComputesNeighborXorWithIdentityRouting() {
        // Default state: identity route, all NOT flags clear.
        BrnLayer layer = new BrnLayer(4, 4);

        byte[] in  = {1, 0, 1, 1};
        byte[] out = new byte[4];
        layer.forward(in, out);

        // out[i] = in[i] XOR in[(i+1) % 4]
        assertEquals(1, out[0]); // 1 ^ 0
        assertEquals(1, out[1]); // 0 ^ 1
        assertEquals(0, out[2]); // 1 ^ 1
        assertEquals(0, out[3]); // 1 ^ 1
    }

    @Test
    void notFlagInvertsOutputAtThatPosition() {
        BrnLayer layer = new BrnLayer(4, 4);
        layer.notFlags[1] = 1;

        byte[] in  = {1, 0, 1, 1};
        byte[] out = new byte[4];
        layer.forward(in, out);

        assertEquals(1, out[0]);
        assertEquals(0, out[1]); // (0 ^ 1) inverted
        assertEquals(0, out[2]);
        assertEquals(0, out[3]);
    }

    @Test
    void routingPermutesOutputs() {
        BrnLayer layer = new BrnLayer(4, 4);
        // Reverse permutation: out[i] gets value from gate position 3-i
        layer.route[0] = 3;
        layer.route[1] = 2;
        layer.route[2] = 1;
        layer.route[3] = 0;

        byte[] in  = {1, 0, 1, 1};
        byte[] out = new byte[4];
        layer.forward(in, out);

        // Pre-routing values are still {1, 1, 0, 0} (the neighbor XORs).
        // After reverse routing: out[0] = pre[3]=0, out[1] = pre[2]=0, out[2] = pre[1]=1, out[3] = pre[0]=1.
        assertArrayEquals(new byte[]{0, 0, 1, 1}, out);
    }

    @Test
    void rotateThreeCyclesRouteEntries() {
        BrnLayer layer = new BrnLayer(4, 4);
        // Initial identity: route = {0, 1, 2, 3}
        layer.rotateThree(0, 1, 2);
        // R[0]=R[1]=1, R[1]=R[2]=2, R[2]=R[0]=0
        assertEquals(1, layer.route[0]);
        assertEquals(2, layer.route[1]);
        assertEquals(0, layer.route[2]);
        assertEquals(3, layer.route[3]);
    }

    @Test
    void flipNotToggles() {
        BrnLayer layer = new BrnLayer(4, 4);
        assertEquals(0, layer.notFlags[2]);
        layer.flipNot(2);
        assertEquals(1, layer.notFlags[2]);
        layer.flipNot(2);
        assertEquals(0, layer.notFlags[2]);
    }
}
