package sibarum.neuralchemy.rwnn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RwLayerTest {

    @Test
    void forwardComputesXorWithRingNeighborsWhenAllGatesAreXor() {
        RwLayer layer = new RwLayer(4, 4);
        for (int i = 0; i < 4; i++) layer.gateTypes[i] = Gates.XOR;

        // input  : 1 0 1 1
        // out[0] = in[3] XOR in[1] = 1 XOR 0 = 1
        // out[1] = in[0] XOR in[2] = 1 XOR 1 = 0
        // out[2] = in[1] XOR in[3] = 0 XOR 1 = 1
        // out[3] = in[2] XOR in[0] = 1 XOR 1 = 0
        byte[] in  = {1, 0, 1, 1};
        byte[] out = new byte[4];
        layer.forward(in, out);
        assertEquals(1, out[0]);
        assertEquals(0, out[1]);
        assertEquals(1, out[2]);
        assertEquals(0, out[3]);
    }

    @Test
    void mixedGateTypesProduceMixedOutputs() {
        RwLayer layer = new RwLayer(4, 4);
        layer.gateTypes[0] = Gates.AND;
        layer.gateTypes[1] = Gates.OR;
        layer.gateTypes[2] = Gates.XOR;
        layer.gateTypes[3] = Gates.NAND;

        byte[] in  = {1, 0, 1, 1};
        byte[] out = new byte[4];
        layer.forward(in, out);
        // out[0] = AND(in[3]=1, in[1]=0) = 0
        // out[1] = OR(in[0]=1, in[2]=1) = 1
        // out[2] = XOR(in[1]=0, in[3]=1) = 1
        // out[3] = NAND(in[2]=1, in[0]=1) = 0
        assertEquals(0, out[0]);
        assertEquals(1, out[1]);
        assertEquals(1, out[2]);
        assertEquals(0, out[3]);
    }
}
