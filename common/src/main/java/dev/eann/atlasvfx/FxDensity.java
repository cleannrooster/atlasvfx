package dev.eann.atlasvfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;

/**
 * How much of an effect to make, as a share of the whole of it.
 *
 * <p>Two parts multiplied. The game's own particle setting is the base - it is where a player
 * already says "fewer particles", and it holds for every mod drawn this way alike. Each mod then
 * scales its own effects by a share it supplies, from its own settings if it has any; nothing
 * here keeps a setting of its own. What all of them make together is bounded by
 * {@link FxBudget}.</p>
 */
@FunctionalInterface
public interface FxDensity {

    /** A mod's own effects at their full amount. */
    FxDensity FULL = () -> 1.0f;

    /** This mod's own share: 1 for everything it would make, less for less. */
    float share();

    /** The share to make now: the game's setting times this mod's own. */
    default float effective() {
        return vanilla() * Math.max(0.0f, share());
    }

    /** The game's particle setting, as a share of the full effect. */
    static float vanilla() {
        ParticlesMode mode = MinecraftClient.getInstance().options.getParticles().getValue();
        return switch (mode) {
            case ALL -> 1.0f;
            case DECREASED -> 0.5f;
            case MINIMAL -> 0.2f;
        };
    }
}
