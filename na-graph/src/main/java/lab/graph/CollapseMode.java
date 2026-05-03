package lab.graph;

/**
 * Editor display hint per node-graph.md §"Display: collapse rules". Has no effect on
 * evaluation; the network behaves identically across all three states.
 *
 * <p>{@link #AUTO} applies the rules in the doc (single-port leaf → inline, pass-through
 * with same in/out type → dot). {@link #EXPANDED} and {@link #COLLAPSED} are user pins.
 */
public enum CollapseMode {
    AUTO,
    EXPANDED,
    COLLAPSED
}
