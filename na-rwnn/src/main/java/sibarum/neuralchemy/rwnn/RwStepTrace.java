package sibarum.neuralchemy.rwnn;

/**
 * Diagnostic snapshot of one training step on a Ring-Weave network.
 *
 * <p>{@code tierValues[t]} is the bit pattern at tier {@code t}: tier 0 is input,
 * tier {@code nLayers} is the final output (post-step values for whatever the
 * forward produced this step).
 *
 * <p>{@code errProb[t][i]} is the probabilistic error at tier {@code t}, position
 * {@code i}, this step.
 *
 * <p>{@code tierErrAccum[t][i]} is the post-step value of the EMA accumulator
 * over {@code errProb} for that (tier, position): high values mean the position
 * has been persistently in error across recent steps.
 *
 * <p>{@code mutated[L][j]} is true iff gate {@code j} of layer {@code L} had its
 * truth-table changed this step.
 *
 * <p>{@code gateAccum[L][j][c]} is the post-step value of the signed accumulator
 * for gate {@code j} of layer {@code L} on input combo {@code c} (= {@code a*2+b}).
 */
public record RwStepTrace(
        byte[] input,
        byte[] target,
        byte[][] tierValues,
        double[][] errProb,
        double[][] tierErrAccum,
        boolean[][] mutated,
        double[][][] gateAccum) {
}
