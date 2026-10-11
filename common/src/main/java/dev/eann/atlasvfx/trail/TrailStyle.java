package dev.eann.atlasvfx.trail;

import dev.eann.atlasvfx.FxBlend;
import dev.eann.atlasvfx.FxSprite;
import dev.eann.atlasvfx.UvWindow;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a trail looks like, whatever it follows.
 *
 * @param layers        its bands, drawn in order
 * @param grain         a picture whose grain the surface takes; null for untextured light
 * @param window        the part of the picture sampled - its solid middle, so transparent texels
 *                      never eat the edge
 * @param blend         lit by the world, lighting itself, or untextured glow
 * @param minBlockLight for a lit trail, the least block light it is drawn in, 0 to 15, so it dims
 *                      in the dark without vanishing
 * @param thickness     half the thickness of the slab it is drawn as, in blocks; 0 draws one sheet
 * @param inner         where it starts across, as a share of the surface's width - a slash reads
 *                      better with the part nearest the shoulder left empty
 * @param tailWidth     its width at the tail as a share of its width at the head: below 1 it
 *                      thins toward where it has been, into a crescent
 * @param alongSegments segments along it, for layers that do not set their own
 * @param flashColour   the colour a {@link dev.eann.atlasvfx.FxHandle#flash() flash} snaps it to
 * @param flashTicks    how long a flash takes to drain back
 * @param clearance     on the viewer's own trail, in their own eyes, how close to them in blocks it
 *                      may come
 */
public record TrailStyle(List<TrailLayer> layers, @Nullable FxSprite grain, UvWindow window, FxBlend blend,
                         int minBlockLight, float thickness, float inner, float tailWidth, int alongSegments,
                         int flashColour, float flashTicks, float clearance) {

    public TrailStyle {
        layers = List.copyOf(layers);
        minBlockLight = Math.max(0, Math.min(15, minBlockLight));
        thickness = Math.max(0.0f, thickness);
        inner = Math.max(0.0f, Math.min(0.99f, inner));
        tailWidth = Math.max(0.0f, Math.min(1.0f, tailWidth));
        alongSegments = Math.max(2, alongSegments);
        clearance = Math.max(0.0f, clearance);
    }

    /** Whether a picture's grain is drawn, rather than untextured light. */
    public boolean textured() {
        return grain != null && blend != FxBlend.GLOW;
    }

    /** The same look, blended another way: lighting itself instead of lit by the world, say. */
    public TrailStyle withBlend(FxBlend newBlend) {
        return new TrailStyle(layers, grain, window, newBlend, minBlockLight, thickness, inner, tailWidth,
                alongSegments, flashColour, flashTicks, clearance);
    }

    /** The same look as a slab this thick: half its thickness, in blocks. */
    public TrailStyle withThickness(float halfThickness) {
        return new TrailStyle(layers, grain, window, blend, minBlockLight, halfThickness, inner, tailWidth,
                alongSegments, flashColour, flashTicks, clearance);
    }

    /** The same look with other bands. */
    public TrailStyle withLayers(List<TrailLayer> newLayers) {
        return new TrailStyle(newLayers, grain, window, blend, minBlockLight, thickness, inner, tailWidth,
                alongSegments, flashColour, flashTicks, clearance);
    }

    /** The same look with another picture's grain. */
    public TrailStyle withGrain(@Nullable FxSprite newGrain, UvWindow newWindow) {
        return new TrailStyle(layers, newGrain, newWindow, blend, minBlockLight, thickness, inner, tailWidth,
                alongSegments, flashColour, flashTicks, clearance);
    }
}
