package sibarum.neuralchemy.rwnn;

import org.junit.jupiter.api.Test;
import sibarum.neuralchemy.bits.Bits;
import sibarum.neuralchemy.nn.Dataset;
import sibarum.neuralchemy.nn.datasets.AdderDataset;
import sibarum.neuralchemy.nn.datasets.MultiplexerDataset;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Convergence smokes for the Ring-Weave Neural Network. None assert specific error
 * rates — they print bit-error curves so the algorithm's behavior can be observed.
 */
class RwConvergenceTest {

    @Test
    void singleSampleMemorizeSmoke() {
        // 1 layer, 1 fixed sample. With ring-weave 2-input gates, a single layer can
        // express any function from (in[i-1], in[i+1]) → out[i] for each output i.
        // Whether random target is reachable depends on whether each output's required
        // bit matches some valid truth-table entry given the static neighbor inputs.
        final int width = 8;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        RwLayer layer = new RwLayer(width, width);
        layer.randomInit(rng);
        RwNetwork net = new RwNetwork(layer);
        RwTrainer trainer = new RwTrainer(net, 1.0, rng);

        byte[] in     = {1, 0, 1, 1, 0, 0, 1, 0};
        byte[] target = {0, 1, 0, 0, 1, 1, 0, 1};
        byte[] out    = new byte[width];

        net.forward(in, out);
        int initial = Bits.hammingDistance(out, target);
        for (int s = 0; s < 2_000; s++) trainer.step(in, target);
        net.forward(in, out);
        int finalErr = Bits.hammingDistance(out, target);
        System.out.printf("[rwnn memorize] hamming initial=%d final=%d (width=%d)%n",
                initial, finalErr, width);
        assertTrue(finalErr <= width);
    }

    @Test
    void neighborXorSmoke() {
        // target[i] = input[(i-1+W) mod W] XOR input[(i+1) mod W] — directly matches
        // the ring-weave's static input pattern, so a single layer of all-XOR gates
        // is the exact solution.
        final int width = 8;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        RwLayer layer = new RwLayer(width, width);
        layer.randomInit(rng);
        RwNetwork net = new RwNetwork(layer);
        RwTrainer trainer = new RwTrainer(net, 0.1, rng);

        // Build dataset: random inputs, target = ring-XOR of neighbors.
        int N = 64;
        byte[][] inputs = new byte[N][width];
        byte[][] targets = new byte[N][width];
        for (int s = 0; s < N; s++) {
            Bits.randomFill(inputs[s], rng);
            for (int j = 0; j < width; j++) {
                int aIdx = (j - 1 + width) % width;
                int bIdx = (j + 1) % width;
                targets[s][j] = (byte) ((inputs[s][aIdx] ^ inputs[s][bIdx]) & 1);
            }
        }

        double initial = meanBitErrorRate(net, inputs, targets);
        System.out.printf("[rwnn neighbor-xor] initial bit-error %.3f%n", initial);

        byte[] in  = new byte[width];
        byte[] tgt = new byte[width];
        for (int s = 0; s < 50_000; s++) {
            int idx = rng.nextInt(N);
            System.arraycopy(inputs[idx], 0, in, 0, width);
            System.arraycopy(targets[idx], 0, tgt, 0, width);
            trainer.step(in, tgt);
            if ((s + 1) % 10_000 == 0) {
                System.out.printf("[rwnn neighbor-xor] step %d: bit-error %.3f%n",
                        s + 1, meanBitErrorRate(net, inputs, targets));
            }
        }
        double finalErr = meanBitErrorRate(net, inputs, targets);
        assertTrue(finalErr >= 0.0 && finalErr <= 1.0);
    }

    @Test
    void parityFixedSmallSmoke() {
        // Parity-of-8 stress test for ring-weave: receptive field ±1 per layer,
        // so 4 layers on a width-8 ring fully covers all 8 input positions.
        // N=4 fixed samples, train==eval, revert-on-regression.
        final int width = 8;
        final int nLayers = 4;
        final int nSamples = 4;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        RwLayer[] layers = new RwLayer[nLayers];
        for (int i = 0; i < nLayers; i++) {
            layers[i] = new RwLayer(width, width);
            layers[i].randomInit(rng);
        }
        RwNetwork net = new RwNetwork(layers);
        RwTrainer trainer = new RwTrainer(net, 0.1, rng);

        byte[][] inputs = new byte[nSamples][width];
        byte[][] targets = new byte[nSamples][width];
        for (int s = 0; s < nSamples; s++) {
            Bits.randomFill(inputs[s], rng);
            byte parity = 0;
            for (int j = 0; j < width; j++) parity ^= inputs[s][j];
            for (int j = 0; j < width; j++) targets[s][j] = parity;
        }

        double prevErr = meanBitErrorRate(net, inputs, targets);
        System.out.printf("[rwnn parity fixed N=%d L=%d] initial %.3f%n", nSamples, nLayers, prevErr);

        byte[] in = new byte[width];
        byte[] tgt = new byte[width];
        int reverts = 0;
        for (int s = 0; s < 50_000; s++) {
            int idx = rng.nextInt(nSamples);
            System.arraycopy(inputs[idx], 0, in, 0, width);
            System.arraycopy(targets[idx], 0, tgt, 0, width);

            RwNetworkSnapshot snap = RwNetworkSnapshot.of(net);
            trainer.step(in, tgt);
            double err = meanBitErrorRate(net, inputs, targets);
            if (err > prevErr) {
                snap.restoreTo(net);
                reverts++;
            } else {
                prevErr = err;
            }

            if ((s + 1) % 10_000 == 0) {
                System.out.printf("[rwnn parity fixed] step %d: %.3f (reverts=%d)%n",
                        s + 1, prevErr, reverts);
            }
        }

        assertTrue(prevErr >= 0 && prevErr <= 1);
    }

    @Test
    void multiplexerSmoke() {
        runHillClimb("rwnn mux", new MultiplexerDataset(), 4, 100_000);
    }

    @Test
    void adderSmoke() {
        runHillClimb("rwnn add", new AdderDataset(), 4, 100_000);
    }

    @Test
    void adderDeepLongSmoke() {
        runHillClimb("rwnn add deep", new AdderDataset(), 6, 300_000);
    }

    private static void runHillClimb(String label, Dataset data, int nLayers, int steps) {
        int width = data.inputBits();
        RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        RwLayer[] layers = new RwLayer[nLayers];
        for (int i = 0; i < nLayers; i++) {
            layers[i] = new RwLayer(width, width);
            layers[i].randomInit(rng);
        }
        RwNetwork net = new RwNetwork(layers);
        RwTrainer trainer = new RwTrainer(net, 0.1, rng);

        double prevErr = meanBitErrorRate(net, data);
        System.out.printf("[%s] initial %.3f (N=%d L=%d)%n", label, prevErr, data.size(), nLayers);

        byte[] in = new byte[width];
        byte[] tgt = new byte[width];
        int reverts = 0;
        int reportEvery = steps / 5;
        for (int s = 0; s < steps; s++) {
            data.sample(rng.nextInt(data.size()), in, tgt);
            RwNetworkSnapshot snap = RwNetworkSnapshot.of(net);
            trainer.step(in, tgt);
            double err = meanBitErrorRate(net, data);
            if (err > prevErr) {
                snap.restoreTo(net);
                reverts++;
            } else {
                prevErr = err;
            }
            if ((s + 1) % reportEvery == 0) {
                System.out.printf("[%s] step %d: %.3f (reverts=%d)%n",
                        label, s + 1, prevErr, reverts);
            }
        }
        assertTrue(prevErr >= 0 && prevErr <= 1);
    }

    private static double meanBitErrorRate(RwNetwork net, Dataset ds) {
        int width = ds.inputBits();
        byte[] in = new byte[width];
        byte[] tgt = new byte[width];
        byte[] out = new byte[width];
        long total = 0, wrong = 0;
        for (int i = 0; i < ds.size(); i++) {
            ds.sample(i, in, tgt);
            net.forward(in, out);
            for (int j = 0; j < width; j++) wrong += (out[j] ^ tgt[j]) & 1;
            total += width;
        }
        return wrong / (double) total;
    }

    private static double meanBitErrorRate(RwNetwork net, byte[][] inputs, byte[][] targets) {
        int W = inputs[0].length;
        byte[] out = new byte[W];
        long total = 0, wrong = 0;
        for (int s = 0; s < inputs.length; s++) {
            net.forward(inputs[s], out);
            for (int j = 0; j < W; j++) wrong += (out[j] ^ targets[s][j]) & 1;
            total += W;
        }
        return wrong / (double) total;
    }
}
