package dev.eann.atlasvfx.trail;

import dev.eann.atlasvfx.FxAnchor;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** Anchors for recorded trails to follow. */
public final class TrailAnchors {

    private TrailAnchors() {
    }

    /**
     * A point held out from an entity's eyes along where it looks: a staff's tip, the point of a
     * held blade. Held while the entity is.
     *
     * @param drop    how far below the eyes the offset starts, in blocks
     * @param forward how far out along the look, in blocks
     * @param right   how far to the right of the look, in blocks
     */
    public static FxAnchor look(Entity entity, double drop, double forward, double right) {
        return new FxAnchor() {
            @Override
            public boolean held() {
                return !entity.isRemoved();
            }

            @Override
            public boolean inView() {
                return false;
            }

            @Override
            @Nullable
            public Vec3d position(float tickDelta) {
                if (entity.isRemoved()) {
                    return null;
                }
                SweepFrame frame = SweepFrame.of(entity.getCameraPosVec(tickDelta).subtract(0.0, drop, 0.0),
                        entity.getYaw(tickDelta), entity.getPitch(tickDelta));
                return frame.origin().add(frame.forward().multiply(forward)).add(frame.right().multiply(right));
            }
        };
    }

    /** The middle of an entity's body, held while the entity is: for something it trails. */
    public static FxAnchor body(Entity entity) {
        return new FxAnchor() {
            @Override
            public boolean held() {
                return !entity.isRemoved();
            }

            @Override
            public boolean inView() {
                return false;
            }

            @Override
            @Nullable
            public Vec3d position(float tickDelta) {
                return entity.isRemoved() ? null
                        : entity.getLerpedPos(tickDelta).add(0.0, entity.getHeight() * 0.5, 0.0);
            }
        };
    }
}
