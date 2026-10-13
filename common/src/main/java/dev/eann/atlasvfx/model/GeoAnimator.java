package dev.eann.atlasvfx.model;

import net.minecraft.entity.LivingEntity;

/**
 * Chooses what a replacement model plays, once a client tick, from what the body is doing: loop a
 * walk while it moves, play a swing as it attacks. The clips are named as in the animation file.
 *
 * <p>Asked on the render thread, for each tick since it was last asked, while the body is drawn.</p>
 */
@FunctionalInterface
public interface GeoAnimator {

    void tick(LivingEntity entity, GeoAnimationState state);
}
