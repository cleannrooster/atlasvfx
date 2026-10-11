package dev.eann.atlasvfx;

/**
 * The one ceiling on motes alive at once, shared by every pool.
 *
 * <p>Each pool may keep a ceiling of its own as well, but only this one bounds them all
 * together: two pools each minding their own would let the sum run as high as they liked. Past
 * it, new motes are simply not made; nothing alive is cut short.</p>
 */
public final class FxBudget {

    /** Motes alive at once, across everything. */
    public static final int MAX_MOTES = 4000;

    private static int live;

    private FxBudget() {
    }

    /** Takes room for one more mote if there is any. */
    static boolean tryReserve() {
        if (live >= MAX_MOTES) {
            return false;
        }
        live++;
        return true;
    }

    /** Gives back room for motes that are gone. */
    static void release(int count) {
        live = Math.max(0, live - count);
    }

    /** Whether one more mote would fit. */
    public static boolean hasRoom() {
        return live < MAX_MOTES;
    }

    /** Motes alive now, across every pool. */
    public static int live() {
        return live;
    }
}
