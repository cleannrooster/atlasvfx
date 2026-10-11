package dev.eann.atlasvfx;

/** How a piece of an effect is put down. */
public enum FxBlend {
    /** A soft spot of light, added on top. Needs no picture: the falloff is drawn. */
    GLOW,
    /** A picture that lights itself, added over what is behind it. */
    EMISSIVE,
    /** A picture lit by the world like anything else: stone, earth, ice. */
    LIT
}
