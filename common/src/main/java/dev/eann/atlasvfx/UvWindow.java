package dev.eann.atlasvfx;

/**
 * The part of a picture a surface samples, as fractions of the picture: {@code u} across it,
 * {@code v} down it.
 *
 * <p>A surface stretched over a whole particle picture shows its transparent margins as holes. A
 * window over just the solid part of it gives the surface the picture's grain and nothing else,
 * so all of the shape comes from the geometry.</p>
 */
public record UvWindow(float u0, float v0, float u1, float v1) {

    /** The whole picture. */
    public static final UvWindow FULL = new UvWindow(0.0f, 0.0f, 1.0f, 1.0f);

    public UvWindow {
        u0 = clamp01(u0);
        v0 = clamp01(v0);
        u1 = clamp01(u1);
        v1 = clamp01(v1);
    }

    /** {@code u} at a fraction of the way across the window. */
    public float u(float fraction) {
        return u0 + (u1 - u0) * fraction;
    }

    /** {@code v} at a fraction of the way down the window. */
    public float v(float fraction) {
        return v0 + (v1 - v0) * fraction;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
