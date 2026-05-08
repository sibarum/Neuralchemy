package sibarum.neuralchemy.brnn;

/**
 * A frozen copy of a {@link BrnNetwork}'s learnable parameters: per-layer routing
 * permutation and NOT-ref indices.
 */
public record NetworkSnapshot(int[][] routes, int[][] notRefs) {

    public static NetworkSnapshot of(BrnNetwork net) {
        int n = net.layers.length;
        int[][] r = new int[n][];
        int[][] f = new int[n][];
        for (int i = 0; i < n; i++) {
            r[i] = net.layers[i].route.clone();
            f[i] = net.layers[i].notRef.clone();
        }
        return new NetworkSnapshot(r, f);
    }

    public void restoreTo(BrnNetwork net) {
        for (int i = 0; i < net.layers.length; i++) {
            System.arraycopy(routes[i], 0, net.layers[i].route, 0, routes[i].length);
            System.arraycopy(notRefs[i], 0, net.layers[i].notRef, 0, notRefs[i].length);
        }
    }
}
