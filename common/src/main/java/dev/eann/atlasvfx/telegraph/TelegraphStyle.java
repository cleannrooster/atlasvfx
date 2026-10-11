package dev.eann.atlasvfx.telegraph;

import dev.eann.atlasvfx.FxCurve;

/**
 * What a telegraph looks like.
 *
 * @param edge      the outline's colour
 * @param fill      the area's colour
 * @param hot       the colour of the filling front, and of the flash as it lands
 * @param mode      how the area fills over the wind-up
 * @param urgency   how fast it beats over the wind-up, 0 to 1 in, 0 (slow) to 1 (fast) out
 * @param edgeWidth the outline's width, in blocks
 * @param baseAlpha how strongly the whole area shows from the start, so its footprint reads
 * @param fillAlpha how strongly the filled part shows
 * @param edgeAlpha how strongly the outline shows
 */
public record TelegraphStyle(int edge, int fill, int hot, FillMode mode, FxCurve urgency, float edgeWidth,
                             float baseAlpha, float fillAlpha, float edgeAlpha) {

    /** How the area fills as the attack nears. */
    public enum FillMode {
        /** Out from the origin to the rim, the far end, or the outer edge: a front closing in. */
        GROW,
        /** Across, from one side to the other. */
        SWEEP,
        /** All of it at once, brightening and beating harder. */
        PULSE
    }

    /** The same look, filling another way. */
    public TelegraphStyle withMode(FillMode newMode) {
        return new TelegraphStyle(edge, fill, hot, newMode, urgency, edgeWidth, baseAlpha, fillAlpha, edgeAlpha);
    }
}
