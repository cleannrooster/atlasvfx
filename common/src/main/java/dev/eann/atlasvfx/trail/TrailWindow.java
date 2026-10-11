package dev.eann.atlasvfx.trail;

import dev.eann.atlasvfx.FxLife;

/**
 * Which part of a trail's surface shows right now, how strongly, and when it is over.
 *
 * <p>The head is the leading end of what shows and the tail its trailing end, both as {@code s}
 * along the surface; moving the head through a swing is what animates it being cut rather than
 * popping a finished crescent into place.</p>
 */
public interface TrailWindow {

    /** The leading end of what shows, 0 to 1 along the surface. */
    float head(FxLife life, float tickDelta);

    /** The trailing end, at or behind the head. */
    float tail(FxLife life, float tickDelta);

    /** How strongly it shows overall, 1 down to 0 as it fades out. */
    float fade(FxLife life, float tickDelta);

    /** Whether it is over. */
    boolean done(FxLife life);

    /**
     * A swing: the head runs through the whole surface over the swing, keeping {@code trail} of it
     * showing behind, then the whole thing fades. Released early, it fades from then.
     *
     * @param swingTicks ticks from the start of the swing to the moment it lands
     * @param trail      how much of the surface shows behind the head; 1 keeps all of it, so the
     *                   finished trail covers everything the swing passed through
     * @param fadeTicks  how long it lingers and fades once the swing has landed
     */
    static TrailWindow swing(int swingTicks, float trail, int fadeTicks) {
        int swing = Math.max(1, swingTicks);
        int fade = Math.max(1, fadeTicks);
        float length = Math.max(0.0f, Math.min(1.0f, trail));
        return new TrailWindow() {
            @Override
            public float head(FxLife life, float tickDelta) {
                return life.progress(tickDelta, swing);
            }

            @Override
            public float tail(FxLife life, float tickDelta) {
                return Math.max(0.0f, head(life, tickDelta) - length);
            }

            @Override
            public float fade(FxLife life, float tickDelta) {
                float elapsed = Math.max(life.age() + tickDelta - swing, life.sinceRelease(tickDelta));
                if (!life.released() && elapsed <= 0.0f) {
                    return 1.0f;
                }
                return Math.max(0.0f, Math.min(1.0f, 1.0f - elapsed / fade));
            }

            @Override
            public boolean done(FxLife life) {
                return life.age() >= swing + fade || (life.released() && life.age() - life.releasedAt() >= fade);
            }
        };
    }
}
