package dev.eann.atlasvfx.trail;

import dev.eann.atlasvfx.FxCurve;
import dev.eann.atlasvfx.FxGradient;

/**
 * One band of a trail: a slice of its width with its own colour, opacity and outline. A slash is
 * a deep, rough wake at the inside, a saturated body over most of it, and a pale razor edge along
 * the outside, drawn in that order.
 *
 * @param bandStart  where the band starts across the trail, 0 at its inner edge to 1 at its outer
 * @param bandEnd    where it ends, past {@code bandStart}
 * @param columns    segments across it; teeth and spikes set their own
 * @param packing    how its columns crowd toward its outer side: 1 evenly, 2 and up more tightly,
 *                   for an edge whose opacity climbs steeply to a hard line
 * @param alongAlpha opacity from the trail's tail, 0, to its head, 1
 * @param acrossAlpha opacity from the band's inner side, 0, to its outer, 1
 * @param colour     colour from the trail's tail to its head
 * @param opacity    how strongly the whole band shows
 * @param depth      where it sits in the trail's thickness, as a share of the half-thickness:
 *                   layers drawn later should sit further out, so they lie on top seen from
 *                   either side
 */
public record TrailLayer(float bandStart, float bandEnd, int columns, float packing, FxCurve alongAlpha,
                         FxCurve acrossAlpha, FxGradient colour, float opacity, float depth, TrailEdging edging) {

    public TrailLayer {
        bandStart = Math.max(0.0f, Math.min(1.0f, bandStart));
        bandEnd = Math.max(bandStart + 1.0e-3f, Math.min(1.0f, bandEnd));
        columns = Math.max(1, columns);
        packing = Math.max(1.0f, packing);
    }

    /** The same band in another colour. */
    public TrailLayer withColour(FxGradient newColour) {
        return new TrailLayer(bandStart, bandEnd, columns, packing, alongAlpha, acrossAlpha, newColour, opacity,
                depth, edging);
    }

    /** The same band, showing this strongly. */
    public TrailLayer withOpacity(float newOpacity) {
        return new TrailLayer(bandStart, bandEnd, columns, packing, alongAlpha, acrossAlpha, colour, newOpacity,
                depth, edging);
    }
}
