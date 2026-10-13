package dev.eann.atlasvfx.parity;

/**
 * What a render layer is to a body being drawn: what substitution will later do with it.
 */
public enum LayerClass {
    /** Solid or cut-out skin, armour and modded body layers: what a substitution replaces. */
    SURFACE,
    /** Body layers drawn with ordinary blending - a slime's shell, a translucent skin. */
    TRANSLUCENT_SURFACE,
    /** Drawn over a surface rather than as one - glint, outlines, eyes, energy swirls: left alone. */
    EFFECT_OVERLAY,
    /** Nothing above can be read off the layer: a custom layer class, text, untextured lines. */
    UNKNOWN
}
