package sibarum.neuralchemy.brnn;

import org.junit.jupiter.api.Test;
import sibarum.neuralchemy.bits.Bits;
import sibarum.neuralchemy.nn.Dataset;
import sibarum.neuralchemy.nn.datasets.AdderDataset;
import sibarum.neuralchemy.nn.datasets.MultiplexerDataset;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Convergence smoke tests for the addendum architecture (static neighbor-XOR →
 * dynamic NOT → permutation routing) and the addendum's structural backprop
 * (3-way route swap, NOT flip, 50/50 XOR backtrace).
 *
 * <p>None of these assert specific error rates — they print bit-error curves so the
 * algorithm's behaviour can be observed across different toy tasks. The only hard
 * assertion is that the rate stays in [0, 1].
 */
class BrnConvergenceTest {

    @Test
    void singleSampleMemorizeSmoke() {
        // Single-layer single-sample memorization. Under the addendum architecture,
        // a 1-layer network has W! * 2^W configurations (permutation route + per-output
        // NOT), so any specific 8-bit target is reachable for any specific input —
        // memorization is in-capacity. Smoke just records whether the trainer
        // finds the matching configuration.
        final int width = 8;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        BrnLayer layer = new BrnLayer(width, width);
        layer.randomInit(rng);
        BrnNetwork net = new BrnNetwork(layer);
        BrnTrainer trainer = new BrnTrainer(net, 1.0, rng);

        byte[] in     = {1, 0, 1, 1, 0, 0, 1, 0};
        byte[] target = {0, 1, 0, 0, 1, 1, 0, 1};
        byte[] out    = new byte[width];

        net.forward(in, out);
        int initial = Bits.hammingDistance(out, target);

        for (int s = 0; s < 2_000; s++) trainer.step(in, target);

        net.forward(in, out);
        int finalErr = Bits.hammingDistance(out, target);
        System.out.printf("[memorize smoke] hamming initial=%d final=%d (width=%d)%n",
                initial, finalErr, width);
        assertTrue(finalErr <= width, "hamming must be in [0, width]");
    }

    @Test
    void parityProgressSmoke() {
        final int width = 8;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        BrnLayer l1 = new BrnLayer(width, width);
        BrnLayer l2 = new BrnLayer(width, width);
        BrnLayer l3 = new BrnLayer(width, width);
//        l1.routes.randomInit(width, rng);
//        l2.routes.randomInit(width, rng);
//        l3.routes.randomInit(width, rng);

        BrnNetwork net = new BrnNetwork(l1, l2);//, l3);

        ParityDataset train = new ParityDataset(2048, width, rng);
        ParityDataset eval  = new ParityDataset(512,  width, rng);

        double initial = meanBitErrorRate(net, eval);
        System.out.printf("[parity smoke] initial bit-error %.3f%n", initial);

        BrnTrainer trainer = new BrnTrainer(net, 0.1, rng);
        byte[] in  = new byte[width];
        byte[] tgt = new byte[width];

        final int steps = 50_000;
        final int reportEvery = 10_000;
        for (int s = 0; s < steps; s++) {
            train.sample(rng.nextInt(train.size()), in, tgt);
            trainer.step(in, tgt);
            if ((s + 1) % reportEvery == 0) {
                System.out.printf("[parity smoke] step %d: bit-error %.3f%n",
                        s + 1, meanBitErrorRate(net, eval));
            }
        }

        // Smoke only: confirm the run produced a finite, sensible rate. Convergence
        // on parity under the unmodified §3.3 rule is an open question per §8 and is
        // not asserted here.
        double finalErr = meanBitErrorRate(net, eval);
        assertTrue(finalErr >= 0.0 && finalErr <= 1.0, "bit-error rate must be in [0,1]");
    }

    @Test
    void parityProgressSmokeMultiLayer() {
        final int width = 8;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        BrnLayer l1 = new BrnLayer(width, width);
        BrnLayer l2 = new BrnLayer(width, width);
        BrnLayer l3 = new BrnLayer(width, width);
        l1.randomInit(rng);
        l2.randomInit(rng);
        l3.randomInit(rng);

        BrnNetwork net = new BrnNetwork(l1, l2, l3);

        ParityDataset train = new ParityDataset(4, width, rng);
        ParityDataset eval  = train;

        double initial = meanBitErrorRate(net, eval);
        System.out.printf("[multi-layer parity] initial bit-error %.3f%n", initial);

        BrnTrainer trainer = new BrnTrainer(net, 0.1, rng);
        byte[] in  = new byte[width];
        byte[] tgt = new byte[width];

        final int steps = 50_000;
        final int reportEvery = 10_000;
        for (int s = 0; s < steps; s++) {
            train.sample(rng.nextInt(train.size()), in, tgt);
            trainer.step(in, tgt);
            if ((s + 1) % reportEvery == 0) {
                System.out.printf("[multi-layer parity] step %d: bit-error %.3f%n",
                        s + 1, meanBitErrorRate(net, eval));
            }
        }

        double finalErr = meanBitErrorRate(net, eval);
        assertTrue(finalErr >= 0.0 && finalErr <= 1.0, "bit-error rate must be in [0,1]");
    }

    @Test
    void parityFixedSmallSmoke() {
        // Same data for train and eval, small fixed N samples. Tests whether the
        // algorithm can fit a known finite task — separates "can the rewire memorize
        // a multi-sample function" from "does it drift on a stream of random samples."
        final int width = 8;
        final int nSamples = 4;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        BrnLayer l1 = new BrnLayer(width, width);
        BrnLayer l2 = new BrnLayer(width, width);
        BrnLayer l3 = new BrnLayer(width, width);
        l1.randomInit(rng);
        l2.randomInit(rng);
        l3.randomInit(rng);

        BrnNetwork net = new BrnNetwork(l1, l2, l3);

        // train == eval: a small fixed set of (input, parity-replicated) pairs.
        ParityDataset data = new ParityDataset(nSamples, width, rng);

        double initial = meanBitErrorRate(net, data);
        System.out.printf("[parity fixed N=%d] initial bit-error %.3f%n", nSamples, initial);

        BrnTrainer trainer = new BrnTrainer(net, 0.05, rng);
        byte[] in  = new byte[width];
        byte[] tgt = new byte[width];

        final int steps = 50_000;
        final int reportEvery = 10_000;
        for (int s = 0; s < steps; s++) {
            data.sample(rng.nextInt(data.size()), in, tgt);
            trainer.step(in, tgt);
            if ((s + 1) % reportEvery == 0) {
                System.out.printf("[parity fixed N=%d] step %d: bit-error %.3f%n",
                        nSamples, s + 1, meanBitErrorRate(net, data));
            }
        }

        double finalErr = meanBitErrorRate(net, data);
        assertTrue(finalErr >= 0.0 && finalErr <= 1.0, "bit-error rate must be in [0,1]");
    }

    @Test
    void neighborXorSmoke() {
        // target[i] = input[i] ^ input[(i+1) mod W]. Under the addendum architecture
        // this is a *native* 1-layer task: identity route + zero notFlags solves it
        // exactly. Random init starts the algorithm somewhere in the W! * 2^W state
        // space and has to find that solution.
        final int width = 8;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        BrnLayer l1 = new BrnLayer(width, width);
        l1.randomInit(rng);

        BrnNetwork net = new BrnNetwork(l1);

        NeighborXorDataset train = new NeighborXorDataset(2048, width, rng);
        NeighborXorDataset eval  = train;

        double initial = meanBitErrorRate(net, eval);
        System.out.printf("[neighbor-xor smoke] initial bit-error %.3f%n", initial);

        BrnTrainer trainer = new BrnTrainer(net, 0.1, rng);
        byte[] in  = new byte[width];
        byte[] tgt = new byte[width];

        final int steps = 50_000;
        final int reportEvery = 10_000;
        for (int s = 0; s < steps; s++) {
            train.sample(rng.nextInt(train.size()), in, tgt);
            trainer.step(in, tgt);
            if ((s + 1) % reportEvery == 0) {
                System.out.printf("[neighbor-xor smoke] step %d: bit-error %.3f%n",
                        s + 1, meanBitErrorRate(net, eval));
            }
        }

        double finalErr = meanBitErrorRate(net, eval);
        assertTrue(finalErr >= 0.0 && finalErr <= 1.0, "bit-error rate must be in [0,1]");
    }

    @Test
    void parityFixedSmallWithRevertSmoke() {
        // Parallel of RWNN's parity test: same task and reversion policy. BRN's 3-input
        // XOR architecture has receptive field 3^L, so 2 layers covers width 8.
        final int width = 8;
        final int nLayers = 2;
        final int nSamples = 4;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        BrnLayer[] layers = new BrnLayer[nLayers];
        for (int i = 0; i < nLayers; i++) {
            layers[i] = new BrnLayer(width, width);
            layers[i].randomInit(rng);
        }
        BrnNetwork net = new BrnNetwork(layers);
        BrnTrainer trainer = new BrnTrainer(net, 0.1, rng);

        ParityDataset data = new ParityDataset(nSamples, width, rng);

        double prevErr = meanBitErrorRate(net, data);
        System.out.printf("[brnn parity fixed N=%d L=%d w/revert] initial %.3f%n",
                nSamples, nLayers, prevErr);

        byte[] in = new byte[width];
        byte[] tgt = new byte[width];
        int reverts = 0;
        for (int s = 0; s < 50_000; s++) {
            data.sample(rng.nextInt(data.size()), in, tgt);

            NetworkSnapshot snap = NetworkSnapshot.of(net);
            trainer.step(in, tgt);
            double err = meanBitErrorRate(net, data);
            if (err > prevErr) {
                snap.restoreTo(net);
                reverts++;
            } else {
                prevErr = err;
            }

            if ((s + 1) % 10_000 == 0) {
                System.out.printf("[brnn parity fixed w/revert] step %d: %.3f (reverts=%d)%n",
                        s + 1, prevErr, reverts);
            }
        }
        assertTrue(prevErr >= 0 && prevErr <= 1);
    }

    @Test
    void multiplexerSmoke() {
        runHillClimb("brnn mux", new MultiplexerDataset(), 3, 100_000);
    }

    @Test
    void adderSmoke() {
        runHillClimb("brnn add", new AdderDataset(), 3, 100_000);
    }

    private static void runHillClimb(String label, Dataset data, int nLayers, int steps) {
        int width = data.inputBits();
        RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        BrnLayer[] layers = new BrnLayer[nLayers];
        for (int i = 0; i < nLayers; i++) {
            layers[i] = new BrnLayer(width, width);
            layers[i].randomInit(rng);
        }
        BrnNetwork net = new BrnNetwork(layers);
        BrnTrainer trainer = new BrnTrainer(net, 0.1, rng);

        double prevErr = meanBitErrorRate(net, data);
        System.out.printf("[%s] initial %.3f (N=%d L=%d)%n", label, prevErr, data.size(), nLayers);

        byte[] in = new byte[width];
        byte[] tgt = new byte[width];
        int reverts = 0;
        int reportEvery = steps / 5;
        for (int s = 0; s < steps; s++) {
            data.sample(rng.nextInt(data.size()), in, tgt);
            NetworkSnapshot snap = NetworkSnapshot.of(net);
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

    private static double meanBitErrorRate(BrnNetwork net, Dataset ds) {
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

    private static double meanBitErrorRate(BrnNetwork net, NeighborXorDataset ds) {
        int width = ds.inputBits();
        byte[] in  = new byte[width];
        byte[] tgt = new byte[width];
        byte[] out = new byte[width];
        long bits = 0;
        long wrong = 0;
        for (int i = 0; i < ds.size(); i++) {
            ds.sample(i, in, tgt);
            net.forward(in, out);
            wrong += Bits.hammingDistance(out, tgt);
            bits  += width;
        }
        return wrong / (double) bits;
    }

    private static double meanBitErrorRate(BrnNetwork net, ParityDataset ds) {
        int width = ds.inputBits();
        byte[] in  = new byte[width];
        byte[] tgt = new byte[width];
        byte[] out = new byte[width];
        long bits = 0;
        long wrong = 0;
        for (int i = 0; i < ds.size(); i++) {
            ds.sample(i, in, tgt);
            net.forward(in, out);
            wrong += Bits.hammingDistance(out, tgt);
            bits  += width;
        }
        return wrong / (double) bits;
    }
}
