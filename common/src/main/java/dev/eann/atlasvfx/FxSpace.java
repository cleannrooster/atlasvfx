package dev.eann.atlasvfx;

/** Where a painter draws. */
public enum FxSpace {
    /**
     * Out in the world, or into the first-person view: tested against depth, lit by the world,
     * pictures turned to face the viewer.
     */
    WORLD,
    /** Flat on the screen, in its own pixels, over whatever the screen has drawn so far. */
    SCREEN
}
