package dev.eann.atlasvfx;

import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Directions within a cone, and how hard to throw a mote along one to reach a distance.
 *
 * <p>The geometry under {@link FxWorld#jet} and {@link FxShapes#cone}: a spray aimed one way
 * rather than in every direction - a gust, a jet of water, a torrent of light. Pure arithmetic,
 * so it can be tested without a game.</p>
 */
public final class FxCone {

    private FxCone() {
    }

    /**
     * A direction picked evenly from within {@code halfAngle} of {@code axis}: evenly over the
     * cone's cap, not bunched at its middle, so a wide cone fills to its edges.
     *
     * @param axis      the middle of the cone; need not be of unit length
     * @param halfAngle half the cone's opening, in degrees, 0 to 180
     */
    public static Vector3f sample(Vector3f axis, float halfAngle, Random random) {
        Vector3f w = new Vector3f(axis);
        if (w.lengthSquared() < 1.0e-8f) {
            w.set(0.0f, 1.0f, 0.0f);
        }
        w.normalize();
        // Two directions square to the axis and to each other.
        Vector3f helper = Math.abs(w.y) < 0.9f ? new Vector3f(0.0f, 1.0f, 0.0f) : new Vector3f(1.0f, 0.0f, 0.0f);
        Vector3f u = new Vector3f(helper).cross(w).normalize();
        Vector3f v = new Vector3f(w).cross(u);

        float limit = (float) Math.cos(Math.toRadians(MathHelper.clamp(halfAngle, 0.0f, 180.0f)));
        // Evenly in cos theta is evenly over the area of the cap.
        float cos = MathHelper.lerp(random.nextFloat(), limit, 1.0f);
        float sin = (float) Math.sqrt(Math.max(0.0f, 1.0f - cos * cos));
        float turn = random.nextFloat() * MathHelper.TAU;
        float a = sin * MathHelper.cos(turn);
        float b = sin * MathHelper.sin(turn);
        return new Vector3f(
                w.x * cos + u.x * a + v.x * b,
                w.y * cos + u.y * a + v.y * b,
                w.z * cos + u.z * a + v.z * b).normalize();
    }

    /**
     * The speed, in blocks per tick, at which a mote moving this way comes to rest about
     * {@code reach} blocks out. A thing that loses a share {@code 1 - drag} of its speed every tick
     * travels its starting speed divided by that share before it stops.
     */
    public static double launchSpeed(Motion motion, float reach) {
        return Math.max(0.0f, reach) * (1.0 - Mote.drag(motion));
    }
}
