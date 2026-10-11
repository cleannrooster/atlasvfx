package dev.eann.atlasvfx.telegraph;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Something a telegraph follows until it locks: a boss turning to face its target before the
 * cleave commits.
 */
public interface TelegraphAnchor {

    /** Where the area's origin is this frame, or null when it cannot be found. */
    @Nullable
    Vec3d position(float tickDelta);

    /** Which way the area faces this frame, as the game turns: 0 is south. */
    float yaw(float tickDelta);

    /** An entity's feet, facing where its body faces. */
    static TelegraphAnchor feet(Entity entity) {
        return new TelegraphAnchor() {
            @Override
            @Nullable
            public Vec3d position(float tickDelta) {
                return entity.isRemoved() ? null : entity.getLerpedPos(tickDelta);
            }

            @Override
            public float yaw(float tickDelta) {
                return entity.getYaw(tickDelta);
            }
        };
    }
}
