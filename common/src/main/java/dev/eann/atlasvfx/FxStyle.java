package dev.eann.atlasvfx;

import org.jetbrains.annotations.Nullable;

/**
 * What an effect is made of, by colour and by debris: everything a shape needs to know to look
 * like the thing that made it.
 *
 * @param glow      the soft light every layer sits in
 * @param hot       the colour at the very middle of the light: paler, nearly white
 * @param debris    what is thrown off it
 * @param accent    a second kind of debris; the first again when there is only one
 * @param wisp      what drifts off it while it lingers
 * @param falling   what it lets fall, for rain over it; null to let its debris drop instead
 * @param flare     a picture laid over its flashes, flipped through once; null for none
 * @param flareTint the flare's tint; white keeps the picture's own colours
 * @param rift      whether its flashes tear a hole into the End's starfield
 */
public record FxStyle(int glow, int hot, MoteRecipe debris, MoteRecipe accent, MoteRecipe wisp,
                      @Nullable MoteRecipe falling, @Nullable FxSprite flare, int flareTint, boolean rift) {
}
