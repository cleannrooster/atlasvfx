package dev.eann.atlasvfx.telegraph;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Where an attack is going to land, as a shape on the ground: a circle, a ring, a cone, a line.
 *
 * <p>Pure geometry, and safe to use on a server: nothing here touches the client. The point is to
 * describe an attack's area once and use it for both the damage and the warning - the server
 * asks {@link #contains} of the same area the client draws, so what players see is what hits
 * them.</p>
 *
 * <p>Every area lies flat at its origin's height and is tested by its footprint: a point is in it
 * if it stands over the shape and within {@code height} above it.</p>
 *
 * <p>Areas are also described by two fractions, as a drawing walks them: {@code u} out along the
 * area's length - from the middle of a circle to its rim, from a cone's point to its arc, from a
 * line's start to its end - and {@code v} across it. {@link #point} turns those into a place on
 * the ground, always inside the area.</p>
 */
public sealed interface TelegraphArea {

    /** Its origin: the middle of a circle or ring, the point of a cone, the start of a line. */
    Vec3d origin();

    /** Whether a point on the ground plane, by its x and z, is inside the shape. */
    boolean containsFootprint(double x, double z);

    /** The same shape moved to {@code origin}, facing {@code yaw} where it has a facing. */
    TelegraphArea at(Vec3d origin, float yaw);

    /** Out along it by {@code u} and across it by {@code v}, both 0 to 1, into x, y, z. */
    void point(float u, float v, double[] out);

    /** How far its footprint reaches from its origin, in blocks. */
    double extent();

    /**
     * Whether a point is in it: over its footprint, no more than half a block below it - for
     * standing on slabs and in shallow dips - and no more than {@code height} above it.
     */
    default boolean contains(Vec3d point, double height) {
        double y = origin().y;
        return point.y >= y - 0.5 && point.y <= y + height && containsFootprint(point.x, point.z);
    }

    /** A box holding everything it could contain up to {@code height}: for finding who to test. */
    default Box bounds(double height) {
        Vec3d o = origin();
        double e = extent();
        return new Box(o.x - e, o.y - 0.5, o.z - e, o.x + e, o.y + height, o.z + e);
    }

    /** The way a facing points along the ground: yaw 0 is south, as the game turns. */
    static Vec3d forward(float yaw) {
        double radians = Math.toRadians(yaw);
        return new Vec3d(-Math.sin(radians), 0.0, Math.cos(radians));
    }

    /** A full circle. */
    record Circle(Vec3d origin, float radius) implements TelegraphArea {
        public Circle {
            radius = Math.max(0.0f, radius);
        }

        @Override
        public boolean containsFootprint(double x, double z) {
            double dx = x - origin.x;
            double dz = z - origin.z;
            return dx * dx + dz * dz <= (double) radius * radius;
        }

        @Override
        public TelegraphArea at(Vec3d to, float yaw) {
            return new Circle(to, radius);
        }

        @Override
        public void point(float u, float v, double[] out) {
            double angle = MathHelper.clamp(v, 0.0f, 1.0f) * Math.PI * 2.0;
            double distance = radius * MathHelper.clamp(u, 0.0f, 1.0f);
            out[0] = origin.x + Math.cos(angle) * distance;
            out[1] = origin.y;
            out[2] = origin.z + Math.sin(angle) * distance;
        }

        @Override
        public double extent() {
            return radius;
        }
    }

    /** A ring: safe in the middle, dangerous between {@code inner} and {@code outer}. */
    record Ring(Vec3d origin, float inner, float outer) implements TelegraphArea {
        public Ring {
            inner = Math.max(0.0f, inner);
            outer = Math.max(inner, outer);
        }

        @Override
        public boolean containsFootprint(double x, double z) {
            double dx = x - origin.x;
            double dz = z - origin.z;
            double squared = dx * dx + dz * dz;
            return squared >= (double) inner * inner && squared <= (double) outer * outer;
        }

        @Override
        public TelegraphArea at(Vec3d to, float yaw) {
            return new Ring(to, inner, outer);
        }

        @Override
        public void point(float u, float v, double[] out) {
            double angle = MathHelper.clamp(v, 0.0f, 1.0f) * Math.PI * 2.0;
            double distance = MathHelper.lerp(MathHelper.clamp(u, 0.0f, 1.0f), inner, outer);
            out[0] = origin.x + Math.cos(angle) * distance;
            out[1] = origin.y;
            out[2] = origin.z + Math.sin(angle) * distance;
        }

        @Override
        public double extent() {
            return outer;
        }
    }

    /**
     * A cone, from a point out to an arc: a breath, a cleave, a sweep in front of a boss.
     *
     * @param yaw       the way it faces, as the game turns: 0 is south
     * @param halfAngle half its spread, in degrees either side of {@code yaw}, up to 180
     */
    record Cone(Vec3d origin, float yaw, float range, float halfAngle) implements TelegraphArea {
        public Cone {
            range = Math.max(0.0f, range);
            halfAngle = MathHelper.clamp(halfAngle, 0.0f, 180.0f);
        }

        @Override
        public boolean containsFootprint(double x, double z) {
            double dx = x - origin.x;
            double dz = z - origin.z;
            double squared = dx * dx + dz * dz;
            if (squared > (double) range * range) {
                return false;
            }
            if (squared < 1.0e-12 || halfAngle >= 180.0f) {
                return true;
            }
            Vec3d facing = forward(yaw);
            double cos = (dx * facing.x + dz * facing.z) / Math.sqrt(squared);
            return cos >= Math.cos(Math.toRadians(halfAngle)) - 1.0e-9;
        }

        @Override
        public TelegraphArea at(Vec3d to, float newYaw) {
            return new Cone(to, newYaw, range, halfAngle);
        }

        @Override
        public void point(float u, float v, double[] out) {
            double angle = Math.toRadians(MathHelper.lerp(MathHelper.clamp(v, 0.0f, 1.0f), -halfAngle, halfAngle));
            double heading = Math.toRadians(yaw) + angle;
            double distance = range * MathHelper.clamp(u, 0.0f, 1.0f);
            out[0] = origin.x - Math.sin(heading) * distance;
            out[1] = origin.y;
            out[2] = origin.z + Math.cos(heading) * distance;
        }

        @Override
        public double extent() {
            return range;
        }
    }

    /**
     * A straight lane from its origin forward: a charge, a beam, a shockwave down a corridor.
     *
     * @param yaw       the way it runs, as the game turns: 0 is south
     * @param halfWidth half its width, in blocks
     */
    record Line(Vec3d origin, float yaw, float length, float halfWidth) implements TelegraphArea {
        public Line {
            length = Math.max(0.0f, length);
            halfWidth = Math.max(0.0f, halfWidth);
        }

        @Override
        public boolean containsFootprint(double x, double z) {
            Vec3d facing = forward(yaw);
            double dx = x - origin.x;
            double dz = z - origin.z;
            double along = dx * facing.x + dz * facing.z;
            double across = dx * facing.z - dz * facing.x;
            return along >= -1.0e-9 && along <= length + 1.0e-9 && Math.abs(across) <= halfWidth + 1.0e-9;
        }

        @Override
        public TelegraphArea at(Vec3d to, float newYaw) {
            return new Line(to, newYaw, length, halfWidth);
        }

        @Override
        public void point(float u, float v, double[] out) {
            Vec3d facing = forward(yaw);
            double along = length * MathHelper.clamp(u, 0.0f, 1.0f);
            double across = halfWidth * (MathHelper.clamp(v, 0.0f, 1.0f) * 2.0 - 1.0);
            out[0] = origin.x + facing.x * along + facing.z * across;
            out[1] = origin.y;
            out[2] = origin.z + facing.z * along - facing.x * across;
        }

        @Override
        public double extent() {
            return Math.sqrt((double) length * length + (double) halfWidth * halfWidth);
        }
    }
}
