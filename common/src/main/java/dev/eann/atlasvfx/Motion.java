package dev.eann.atlasvfx;

/** How a mote moves. */
public enum Motion {
    /** Born on a shell around its anchor and drawn into it on a tightening spiral. */
    GATHER,
    /** Circles its anchor at a distance, bobbing, carried along with it. */
    ORBIT,
    /** Leaves where it was born and drifts off on its own, rising or sinking. */
    EMIT,
    /** Hangs for a moment, then falls. */
    FALL,
    /** Thrown out, slowing as it goes. */
    BURST;

    /** Whether a mote moving this way belongs to an anchor rather than being on its own. */
    public boolean anchored() {
        return this == GATHER || this == ORBIT;
    }
}
