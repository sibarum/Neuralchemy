package sibarum.neuralchemy.brnn;

/**
 * Diagnostic record produced by {@link BrnTrainer#stepTraced} for visualization.
 *
 * <p>{@code tierValues[t]} is the bit pattern at tier {@code t}: tier 0 is the
 * network input, tier {@code nLayers} is the final output. These values reflect
 * the forward pass at the start of the step (before any rewire).
 *
 * <p>{@code flagged[L][i]} is 1 iff bit {@code i} of layer {@code L}'s output tier
 * was flagged in the error mask reaching that layer (before rewire was applied).
 *
 * <p>{@code flipped[L][i]} is 1 iff bit {@code i} of layer {@code L}'s output was
 * "addressed" during the step — either its routing entry was rotated (3-way swap)
 * or its source gate's NOT was toggled.
 *
 * <p>{@code flagAccum[L][i]} is the post-step value of the EMA flag accumulator
 * for that bit — high values mean the bit has been persistently flagged across
 * recent steps. Saturation ≈ {@code 1 / (1 - decay)}.
 *
 * <p>{@code mutationRate[L]} is the post-step EMA of mutations per step at that
 * layer (rotations + NOT flips). Per-step rate ≈ {@code mutationRate[L] * (1 - decay)}.
 */
public record StepTrace(
        byte[] input,
        byte[] target,
        byte[][] tierValues,
        byte[][] flagged,
        byte[][] flipped,
        double[][] flagAccum,
        double[] mutationRate) {
}
