package lab.graph;

/**
 * Editor camera state per network. Mutable so drag/zoom can update fields directly without
 * rebuilding the {@link Network} record. Persisted as part of the network file but not used
 * during evaluation.
 */
public final class Viewport {

    public float x, y;
    public float zoom = 1.0f;

    public Viewport() {}

    public Viewport(float x, float y, float zoom) {
        this.x = x;
        this.y = y;
        this.zoom = zoom;
    }

    public Viewport set(float x, float y, float zoom) {
        this.x = x;
        this.y = y;
        this.zoom = zoom;
        return this;
    }
}
