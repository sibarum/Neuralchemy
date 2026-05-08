package sibarum.neuralchemy.brnn;

import org.junit.jupiter.api.Test;
import sibarum.neuralchemy.bits.Bits;

import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MVP convergence checks (design doc §9).
 *
 * <p>{@link #singleSampleMemorizes} is the strict assertion: a single-layer network on
 * one fixed (input, target) pair must drive Hamming error to zero. This validates the
 * rewire mechanism works at all.
 *
 * <p>{@link #parityProgressSmoke} runs the full multi-layer pipeline on the §9 toy task
 * (parity over 8 bits, 3 stacked layers) and prints the bit-error curve. It does not
 * assert convergence — whether the unmodified §3.3 rule learns parity is itself one
 * of the open questions in §8.
 */
class BrnConvergenceTest {

    @Test
    void singleSampleMemorizes() {
        final int width = 8;
        final RandomGenerator rng = RandomGenerator.of("L64X128MixRandom");

        BrnLayer layer = new BrnLayer(width, width);
        layer.routes.randomInit(width, rng);
        BrnNetwork net = new BrnNetwork(layer);
        BrnTrainer trainer = new BrnTrainer(net, 1.0, rng);

        byte[] in     = {1, 0, 1, 1, 0, 0, 1, 0};
        byte[] target = {0, 1, 0, 0, 1, 1, 0, 1};
        byte[] out    = new byte[width];

        for (int s = 0; s < 2_000; s++) trainer.step(in, target);

        net.forward(in, out);
        assertEquals(0, Bits.hammingDistance(out, target),
                "single-layer single-sample memorization should reach zero error");
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
