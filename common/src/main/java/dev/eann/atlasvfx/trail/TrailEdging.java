package dev.eann.atlasvfx.trail;

/**
 * How a layer's outline is broken up. Every kind only ever takes surface away, so a layer never
 * reaches past where it would have without it.
 */
public sealed interface TrailEdging {

    /** A clean outline. */
    TrailEdging NONE = new None();

    /** A clean outline. */
    record None() implements TrailEdging {
    }

    /**
     * Teeth across both ends of the trail, its outer edge left clean.
     *
     * @param teeth how many
     * @param depth how far each notch cuts in from either end, as a share of the visible trail, at
     *              most 0.45
     */
    record Serrated(int teeth, float depth) implements TrailEdging {
        public Serrated {
            teeth = Math.max(1, teeth);
            depth = Math.max(0.0f, Math.min(0.45f, depth));
        }
    }

    /**
     * Spikes along the inner edge and torn across the tail end: a rough, trailing wake.
     *
     * @param count     how many spikes along each side
     * @param maxLength the longest spike, as a share of the layer's width or of the visible trail,
     *                  at most 0.6
     */
    record Spiked(int count, float maxLength) implements TrailEdging {
        public Spiked {
            count = Math.max(1, count);
            maxLength = Math.max(0.0f, Math.min(0.6f, maxLength));
        }
    }
}
