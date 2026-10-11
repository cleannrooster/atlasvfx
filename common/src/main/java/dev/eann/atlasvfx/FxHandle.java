package dev.eann.atlasvfx;

/**
 * A playing shape, as whoever started it holds on to it.
 *
 * <p>Every call is safe on a shape that has already ended; it does nothing.</p>
 */
public interface FxHandle {

    /**
     * Lets go of it: a trail stops following and fades out along its length, a telegraph resolves
     * early. A shape with a fixed length that does not care simply plays out.
     */
    void release();

    /** Puts it away now, with no fade. */
    void cancel();

    /**
     * Confirms something it was showing - a hit landing: its colour snaps to its flash and drains
     * back. A shape that has no flash ignores it.
     */
    void flash();

    /** Whether it is still playing. */
    boolean alive();
}
