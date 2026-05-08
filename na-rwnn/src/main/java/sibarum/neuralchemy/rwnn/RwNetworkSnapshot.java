package sibarum.neuralchemy.rwnn;

/**
 * A frozen copy of an {@link RwNetwork}'s gate-type bytes, for revert-on-regression
 * style hill-climbing.
 */
public record RwNetworkSnapshot(byte[][] gateTypes) {

    public static RwNetworkSnapshot of(RwNetwork net) {
        int n = net.layers.length;
        byte[][] g = new byte[n][];
        for (int i = 0; i < n; i++) g[i] = net.layers[i].gateTypes.clone();
        return new RwNetworkSnapshot(g);
    }

    public void restoreTo(RwNetwork net) {
        for (int i = 0; i < net.layers.length; i++) {
            System.arraycopy(gateTypes[i], 0, net.layers[i].gateTypes, 0, gateTypes[i].length);
        }
    }
}
