package dev.eann.atlasvfx.aura;

import net.minecraft.entity.Entity;

/**
 * How big an aura is drawn on a given body: a factor every size in it given in blocks is
 * multiplied by - a sigil's radius, how far out sparks circle and how big they are, a helix's
 * reach and thickness, how fast motes rise.
 *
 * <p>Shells are the exception: they are sized against the body they surround whatever this says,
 * since a shell that does not clear the body is not a shell.</p>
 */
@FunctionalInterface
public interface Sizing {

    /** A player's width and height: the body sizes in blocks are meant for. */
    float PLAYER_WIDTH = 0.6f;
    float PLAYER_HEIGHT = 1.8f;

    /** Sizes as given, in blocks, on any body. */
    Sizing FIXED = entity -> 1.0f;

    /**
     * Sizes as given on a player, and in proportion on anything else: half again as big on a body
     * half again as tall or wide, half as big on a baby. Follows the scale attribute, which sizes
     * the body itself.
     */
    Sizing BODY = entity -> body(entity.getWidth(), entity.getHeight());

    /** The factor for {@code entity}. */
    float scale(Entity entity);

    /** The same, then multiplied by {@code factor}. */
    default Sizing times(float factor) {
        return entity -> scale(entity) * factor;
    }

    /** Always {@code factor}, whatever the body. */
    static Sizing of(float factor) {
        return entity -> factor;
    }

    /**
     * A body's size against a player's: by whichever of its width and height is the larger
     * share, so a wide, low ravager gets a circle that clears its feet and a tall, thin enderman
     * one that matches its height.
     */
    static float body(float width, float height) {
        return Math.max(Math.max(width / PLAYER_WIDTH, height / PLAYER_HEIGHT), 0.05f);
    }
}
