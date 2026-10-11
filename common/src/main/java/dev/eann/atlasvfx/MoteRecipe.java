package dev.eann.atlasvfx;

import org.jetbrains.annotations.Nullable;

/**
 * One kind of mote.
 *
 * @param rate     how many are given off per tick at full strength; for a burst, a count
 * @param radius   for gathering and orbiting, how far out from the anchor; for the rest, how far
 *                 from where they start they may be born
 * @param speed    blocks per tick at birth; for orbiting, radians per tick round the anchor
 * @param gravity  blocks per tick per tick, downward; negative rises
 * @param flips    whether the picture flips through its frames over the mote's life, rather
 *                 than keeping one picked at birth
 * @param crumb    whether to show only a corner of the picture, the way broken-block dust does,
 *                 so a whole block face reads as a fragment of one
 */
public record MoteRecipe(@Nullable FxSprite sprite, FxBlend blend, Motion motion, int tint,
                         float size, int life, float rate, float radius, float speed,
                         float gravity, float spin, boolean flips, boolean crumb) {

    /** The same mote showing a broken-off corner of its picture. */
    public MoteRecipe asCrumb() {
        return new MoteRecipe(sprite, blend, motion, tint, size, life, rate, radius, speed,
                gravity, spin, flips, true);
    }

    /**
     * The same mote drifting freely instead - slowing gently rather than hard, the way a thing
     * floats rather than flies - with its own pull up or down.
     */
    public MoteRecipe drifting(float gravity) {
        return new MoteRecipe(sprite, blend, Motion.EMIT, tint, size, life, rate, radius, speed,
                gravity, spin, flips, crumb);
    }

    /** The same mote in another colour. */
    public MoteRecipe tinted(int rgb) {
        return new MoteRecipe(sprite, blend, motion, rgb, size, life, rate, radius, speed, gravity, spin,
                flips, crumb);
    }

    /** The same mote drawn as a solid thing lit by the world, for smoke and dust. */
    public MoteRecipe lit() {
        return new MoteRecipe(sprite, FxBlend.LIT, motion, tint, size, life, rate, radius, speed,
                gravity, spin, flips, crumb);
    }

    /** The same mote, living this many times as long. */
    public MoteRecipe lasting(float times) {
        return new MoteRecipe(sprite, blend, motion, tint, size, Math.max(2, Math.round(life * times)),
                rate, radius, speed, gravity, spin, flips, crumb);
    }
}
