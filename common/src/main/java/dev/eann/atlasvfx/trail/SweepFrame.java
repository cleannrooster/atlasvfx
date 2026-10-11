package dev.eann.atlasvfx.trail;

import net.minecraft.util.math.Vec3d;

/**
 * Where a swing is anchored and which way it faces, for one instant.
 *
 * @param origin  the point the swing radiates from
 * @param forward the way it faces: the middle of the arc
 * @param up      up, square to {@code forward}
 * @param right   to the right, square to both
 */
public record SweepFrame(Vec3d origin, Vec3d forward, Vec3d up, Vec3d right) {

    /**
     * A frame at a point facing a yaw and pitch, built the way the game turns a look into
     * directions - and the way Better Combat builds its attack hitboxes, so an arc drawn from this
     * frame points where such an attack's damage does.
     */
    public static SweepFrame of(Vec3d origin, float yaw, float pitch) {
        Vec3d forward = Vec3d.fromPolar(pitch, yaw).normalize();
        Vec3d up = Vec3d.fromPolar(pitch + 90.0f, yaw).negate().normalize();
        return new SweepFrame(origin, forward, up, forward.crossProduct(up));
    }
}
