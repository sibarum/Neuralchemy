package lab.graph;

/**
 * Outcome of {@link Network#canConnect} / {@link Network#connect}. Method-call style rather
 * than exception so the editor can preview connectability while the user is dragging a wire
 * without paying for stack-trace construction on every frame.
 */
public record ConnectionResult(boolean ok, String reason, Edge edge) {

    public static ConnectionResult ok(Edge e) {
        return new ConnectionResult(true, "", e);
    }

    public static ConnectionResult fail(String reason) {
        return new ConnectionResult(false, reason, null);
    }
}
