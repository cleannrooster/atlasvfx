package dev.eann.atlasvfx.telegraph;

/**
 * When a telegraphed attack lands, in world ticks.
 *
 * <p>Pinned to the world's clock rather than counted from when a client heard of it, so a warning
 * that arrives late still lands with the attack it warns of: it simply starts further along.
 * Safe to use on a server, which is where these numbers should come from.</p>
 *
 * @param startTick   the world tick the warning starts
 * @param triggerTick the world tick the attack lands
 * @param fadeTicks   how long the warning lingers after it lands
 */
public record TelegraphTiming(long startTick, long triggerTick, int fadeTicks) {

    public TelegraphTiming {
        triggerTick = Math.max(triggerTick, startTick + 1);
        fadeTicks = Math.max(1, fadeTicks);
    }

    /** A warning starting {@code now} and landing {@code windupTicks} later. */
    public static TelegraphTiming starting(long now, int windupTicks, int fadeTicks) {
        return new TelegraphTiming(now, now + Math.max(1, windupTicks), fadeTicks);
    }

    /** How far through the wind-up {@code now} is, 0 to 1. */
    public float progress(double now) {
        double span = triggerTick - startTick;
        return (float) Math.max(0.0, Math.min(1.0, (now - startTick) / span));
    }

    /** How far through the fade after landing {@code now} is, 0 to 1; 0 before it lands. */
    public float afterTrigger(double now, long trigger) {
        return (float) Math.max(0.0, Math.min(1.0, (now - trigger) / fadeTicks));
    }
}
