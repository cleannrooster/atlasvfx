package dev.eann.atlasvfx;

/**
 * One shape's life as the world keeps it: how old it is, and whether it has been let go of or
 * flashed through its {@link FxHandle}.
 *
 * <p>Shapes read this; only the world and the handle change it. A shape keeps no clock of its
 * own, so the same shape object could in principle be played twice.</p>
 *
 * <p>Releases and flashes are stamped one tick ahead of the current age. Whatever calls the handle
 * usually does so during the client tick - a hit landing, an attack resolving - and the world
 * ages its shapes at the end of that same tick, so the first frame drawn afterwards should see
 * nothing elapsed yet. One called from somewhere else, after the world has ticked, simply holds
 * for a tick before it starts to count.</p>
 */
public final class FxLife {

    private static final int NEVER = Integer.MIN_VALUE;

    private int age;
    private int releasedAt = NEVER;
    private int flashedAt = NEVER;
    private boolean cancelled;

    FxLife() {
    }

    /** Ticks since it appeared. */
    public int age() {
        return age;
    }

    /** How far through {@code ticks} it is, 0 to 1, with the frame's fraction. */
    public float progress(float tickDelta, int ticks) {
        return clamp01((age + tickDelta) / Math.max(1, ticks));
    }

    /** Whether it has been let go of: a trail detached, a telegraph resolved early. */
    public boolean released() {
        return releasedAt != NEVER;
    }

    /** The age it was let go of at, or -1 if it has not been. */
    public int releasedAt() {
        return released() ? releasedAt : -1;
    }

    /** Ticks since it was let go of, with the frame's fraction; 0 until then. */
    public float sinceRelease(float tickDelta) {
        return released() ? Math.max(0.0f, age + tickDelta - releasedAt) : 0.0f;
    }

    /**
     * How strongly its flash shows: 1 the moment it was flashed, draining to 0 over {@code ticks}.
     * 0 if it never was.
     */
    public float flash(float tickDelta, float ticks) {
        if (flashedAt == NEVER) {
            return 0.0f;
        }
        float elapsed = Math.max(0.0f, age + tickDelta - flashedAt);
        return clamp01(1.0f - elapsed / Math.max(1.0e-3f, ticks));
    }

    /** Whether it was put away at once, with no fade. */
    public boolean cancelled() {
        return cancelled;
    }

    void advance() {
        age++;
    }

    void release() {
        if (!released()) {
            releasedAt = age + 1;
        }
    }

    void flash() {
        flashedAt = age + 1;
    }

    void cancel() {
        cancelled = true;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
