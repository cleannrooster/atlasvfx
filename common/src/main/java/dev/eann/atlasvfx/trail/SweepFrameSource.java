package dev.eann.atlasvfx.trail;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Where a swing's frame is this frame: read again every time it is drawn, so an arc follows its
 * swinger turning.
 */
@FunctionalInterface
public interface SweepFrameSource {

    /** The frame now, or null when it cannot be found; the last one found is used then. */
    @Nullable
    SweepFrame frame(float tickDelta);

    /**
     * Whether this is the viewer's own swing seen from their own eyes, in which case the part of
     * the arc nearest them would smear across the whole screen and is left out.
     */
    default boolean viewer() {
        return false;
    }

    /** A frame that stays where it is. */
    static SweepFrameSource fixed(SweepFrame frame) {
        return tickDelta -> frame;
    }

    /**
     * An entity's eyes, dropped by a share of its height, facing where it looks - Better Combat's
     * attack origin with a drop of 0.15.
     *
     * @param drop how far below the eyes, as a share of the entity's height
     */
    static SweepFrameSource eyes(Entity entity, float drop) {
        return new SweepFrameSource() {
            @Override
            @Nullable
            public SweepFrame frame(float tickDelta) {
                if (entity.isRemoved()) {
                    return null;
                }
                Vec3d origin = entity.getCameraPosVec(tickDelta).subtract(0.0, entity.getHeight() * drop, 0.0);
                return SweepFrame.of(origin, entity.getYaw(tickDelta), entity.getPitch(tickDelta));
            }

            @Override
            public boolean viewer() {
                MinecraftClient client = MinecraftClient.getInstance();
                return entity == client.getCameraEntity() && client.options.getPerspective().isFirstPerson();
            }
        };
    }
}
