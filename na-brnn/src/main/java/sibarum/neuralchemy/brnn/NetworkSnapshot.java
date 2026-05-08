package sibarum.neuralchemy.brnn;

/**
 * A frozen copy of a {@link BrnNetwork}'s learnable parameters (routing tables and
 * NOT flags). Use to checkpoint before a step and restore on regression.
 */
public record NetworkSnapshot(int[][] routes, byte[][] notFlags) {

    public static NetworkSnapshot of(BrnNetwork net) {
        int n = net.layers.length;
        int[][] r = new int[n][];
        byte[][] f = new byte[n][];
        for (int i = 0; i < n; i++) {
            r[i] = net.layers[i].route.clone();
            f[i] = net.layers[i].notFlags.clone();
        }
        return new NetworkSnapshot(r, f);
    }

    public void restoreTo(BrnNetwork net) {
        for (int i = 0; i < net.layers.length; i++) {
            System.arraycopy(routes[i], 0, net.layers[i].route, 0, routes[i].length);
            System.arraycopy(notFlags[i], 0, net.layers[i].notFlags, 0, notFlags[i].length);
        }
    }
}
