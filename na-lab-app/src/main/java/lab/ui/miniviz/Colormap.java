package lab.ui.miniviz;

/**
 * Maps a normalized float value in {@code [0, 1]} to a packed RGB int ({@code 0x00RRGGBB}).
 * Implementations stay branch-light — they're called once per texture pixel.
 *
 * <p>Standard presets are static factories on this interface.
 */
@FunctionalInterface
public interface Colormap {

    /** {@code t} clamped to {@code [0, 1]} by callers. Returns {@code 0x00RRGGBB}. */
    int sample(float t);

    /** Magenta (-) → grey (0) → cyan (+); the parameter-mixer demo's palette, signed-input variant. */
    Colormap MAGENTA_CYAN = t -> {
        // t in [0,1] maps to a diverging palette around 0.5.
        float s = (t - 0.5f) * 2f;     // [-1, 1]
        if (s < -1f) s = -1f; else if (s > 1f) s = 1f;
        final int N = 96;
        int trxR, trxG, trxB;
        if (s >= 0) { trxR = 0;   trxG = 255; trxB = 255; }   // cyan tail
        else        { trxR = 255; trxG = 0;   trxB = 255; }   // magenta tail
        float a = Math.abs(s);
        int r = N + (int) ((trxR - N) * a);
        int g = N + (int) ((trxG - N) * a);
        int b = N + (int) ((trxB - N) * a);
        return clamp8(r) << 16 | clamp8(g) << 8 | clamp8(b);
    };

    /** Black → orange → white; one-sided heatmap good for magnitudes / losses. */
    Colormap MAGMA = t -> {
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
        // Three-stop ramp: (10,8,40) → (220,90,30) → (250,250,210)
        float a, b;
        int r0, g0, b0, r1, g1, b1;
        if (t < 0.55f) {
            a = t / 0.55f; b = 1 - a;
            r0 = 10;  g0 = 8;   b0 = 40;
            r1 = 220; g1 = 90;  b1 = 30;
        } else {
            a = (t - 0.55f) / 0.45f; b = 1 - a;
            r0 = 220; g0 = 90;  b0 = 30;
            r1 = 250; g1 = 250; b1 = 210;
        }
        int r = (int) (r0 * b + r1 * a);
        int g = (int) (g0 * b + g1 * a);
        int bl = (int) (b0 * b + b1 * a);
        return clamp8(r) << 16 | clamp8(g) << 8 | clamp8(bl);
    };

    /** Blue → white → red; standard divergent palette. */
    Colormap BLUE_RED = t -> {
        if (t < 0f) t = 0f; else if (t > 1f) t = 1f;
        if (t < 0.5f) {
            float a = t * 2f;
            int r = (int) (40  + (255 - 40)  * a);
            int g = (int) (80  + (255 - 80)  * a);
            int b = (int) (180 + (255 - 180) * a);
            return clamp8(r) << 16 | clamp8(g) << 8 | clamp8(b);
        } else {
            float a = (t - 0.5f) * 2f;
            int r = (int) (255 + (220 - 255) * a);
            int g = (int) (255 + (60  - 255) * a);
            int b = (int) (255 + (60  - 255) * a);
            return clamp8(r) << 16 | clamp8(g) << 8 | clamp8(b);
        }
    };

    private static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
