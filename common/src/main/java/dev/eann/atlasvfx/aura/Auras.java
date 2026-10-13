package dev.eann.atlasvfx.aura;

import dev.eann.atlasvfx.FxStyle;

/**
 * Ready-made auras, each in whatever style it is given: starting points to wear as they are or
 * to take apart for their layers.
 */
public final class Auras {

    private Auras() {
    }

    /** A dome of light over the body on a turning circle of glyphs: a ward. */
    public static Aura ward(FxStyle style) {
        return new Aura(style,
                new AuraLayer.GroundSigil(1.4f, 0.02f, 12),
                new AuraLayer.Shell(ShellShape.DOME, 1.0f, 0.55f, 0.03f, 0.25f));
    }

    /** Embers drifting up off a faint circle of light. */
    public static Aura embers(FxStyle style) {
        return new Aura(style,
                new AuraLayer.GroundSigil(0.9f, 0.0f, 0),
                new AuraLayer.Rise(style.debris().drifting(-0.004f).lasting(1.5f), 0.8f, 1.2f, 0.03f));
    }

    /** Sparks circling the body at chest height, shedding wisps. */
    public static Aura orbit(FxStyle style) {
        return new Aura(style,
                new AuraLayer.Orbit(3, 0.45f, 0.6f, 0.12f, 0.16f),
                new AuraLayer.Rise(null, 0.25f, 1.0f, 0.02f));
    }

    /** Two strands of light winding up round the body. */
    public static Aura helix(FxStyle style) {
        return new Aura(style,
                new AuraLayer.GroundSigil(0.8f, 0.0f, 0),
                new AuraLayer.Helix(2, 0.3f, 1.5f, 0.07f, 0.12f));
    }

    /** A circle of glyphs at the feet, turning, and nothing else. */
    public static Aura sigil(FxStyle style) {
        return new Aura(style, new AuraLayer.GroundSigil(1.2f, -0.025f, 10));
    }

    /** A ball of light round the body, edged bright, with a column of rising light inside. */
    public static Aura sphere(FxStyle style) {
        return new Aura(style,
                new AuraLayer.Shell(ShellShape.SPHERE, 1.0f, 0.5f, 0.02f, 0.0f),
                new AuraLayer.Shell(ShellShape.CYLINDER, 0.9f, 0.35f, 0.08f, 0.4f));
    }
}
