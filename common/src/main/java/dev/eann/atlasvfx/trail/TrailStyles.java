package dev.eann.atlasvfx.trail;

import dev.eann.atlasvfx.FxBlend;
import dev.eann.atlasvfx.FxColor;
import dev.eann.atlasvfx.FxCurve;
import dev.eann.atlasvfx.FxGradient;
import dev.eann.atlasvfx.FxSprite;
import dev.eann.atlasvfx.UvWindow;

import java.util.List;

/** Ready-made trail looks. */
public final class TrailStyles {

    /** The grain a slash takes: the game's own sweep, sampled only across its solid middle. */
    public static final FxSprite SWEEP = FxSprite.particle("sweep_3");
    public static final UvWindow SWEEP_WINDOW = new UvWindow(0.19f, 0.41f, 0.81f, 0.46f);

    /** The red a slash snaps to when its hit lands. */
    private static final int HIT_RED = 0xFF1F14;

    private TrailStyles() {
    }

    /**
     * A slash in one colour - a weapon's material, a spell's element - as three bands derived
     * from it: a deep, spiked wake at the inside, a saturated, serrated body over most of the
     * width, and a near-white razor edge along the outside, the line its reach is read from.
     *
     * <p>Lit by the world, so a slash in a dark cave is not a flash of light;
     * {@link TrailStyle#withBlend withBlend(EMISSIVE)} makes it glow. Thinning toward its tail into
     * a crescent, and a little thick, so it reads as a solid blade-path from the side.</p>
     */
    public static TrailStyle slash(int baseRgb) {
        Palette palette = Palette.of(baseRgb);
        TrailLayer wake = new TrailLayer(0.0f, 0.7f, 1, 1.0f,
                FxCurve.easeIn(0.6f),
                // Present at its inner reach, densest mid-band, gone where the body takes over.
                FxCurve.SMOOTH.within(0.0f, 0.5f).between(0.35f, 1.0f)
                        .times(FxCurve.SMOOTH.within(0.7f, 1.0f).between(1.0f, 0.0f)),
                FxGradient.constant(palette.wake()), 0.45f, 0.6f, new TrailEdging.Spiked(7, 0.3f));
        TrailLayer body = new TrailLayer(0.15f, 1.0f, 1, 1.0f,
                FxCurve.SMOOTH,
                // Fades in from the inside, full by about halfway across and out to the edge.
                FxCurve.SMOOTH.within(0.0f, 0.53f),
                FxGradient.constant(palette.body()), 0.65f, 0.8f, new TrailEdging.Serrated(5, 0.08f));
        TrailLayer edge = new TrailLayer(0.8f, 1.0f, 5, 2.0f,
                // Strongest at the blade, still there down the tail so the outline holds.
                FxCurve.easeIn(1.5f).between(0.15f, 1.0f),
                FxCurve.easeIn(3.0f),
                FxGradient.constant(palette.edge()), 0.8f, 1.0f, TrailEdging.NONE);
        return new TrailStyle(List.of(wake, body, edge), SWEEP, SWEEP_WINDOW, FxBlend.LIT, 7, 0.08f, 0.14f, 0.15f,
                32, HIT_RED, 3.0f, 1.25f);
    }

    /**
     * The three colours of a slash, from one.
     *
     * @param edge pale, for the razor edge
     * @param body saturated, for the mass of it
     * @param wake deeper and more saturated still, for the rough wake - never dark
     */
    public record Palette(int edge, int body, int wake) {

        private static final float MIN_LIGHTNESS = 0.55f;
        private static final float EDGE_WHITENESS = 0.55f;
        /** The body keeps enough depth to show its hue rather than washing out to the edge's white. */
        private static final float BODY_MAX_LIGHTNESS = 0.72f;
        private static final float WAKE_MAX_LIGHTNESS = 0.62f;

        public static Palette of(int baseRgb) {
            float[] hsl = FxColor.toHsl(baseRgb);
            float lifted = Math.max(hsl[2], MIN_LIGHTNESS);
            int edge = FxColor.mix(FxColor.fromHsl(hsl[0], hsl[1], lifted), FxColor.WHITE, EDGE_WHITENESS);
            int body = FxColor.fromHsl(hsl[0], Math.min(1.0f, hsl[1] * 1.15f + 0.05f),
                    clamp(lifted, BODY_MAX_LIGHTNESS));
            int wake = FxColor.fromHsl(hsl[0], Math.min(1.0f, hsl[1] * 1.3f + 0.12f),
                    clamp(lifted - 0.12f, WAKE_MAX_LIGHTNESS));
            return new Palette(edge, body, wake);
        }

        private static float clamp(float lightness, float ceiling) {
            return Math.max(MIN_LIGHTNESS, Math.min(lightness, ceiling));
        }
    }
}
