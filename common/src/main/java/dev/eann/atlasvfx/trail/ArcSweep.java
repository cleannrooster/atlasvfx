package dev.eann.atlasvfx.trail;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A swing through a known arc: a blade of some reach sweeping from one side of its frame's
 * forward direction to the other.
 *
 * <p>Nothing is recorded. The surface is worked out from the frame each time it is drawn, so it
 * follows its swinger turning and costs the same however long it lasts. A point at {@code s} and
 * {@code t} is the blade's {@code t} of its reach out, {@code s} of the way through the arc: the
 * same crescent an attack that reaches that far and covers that angle can hit.</p>
 */
public final class ArcSweep implements SweptSurface {

    /** Which plane of its frame a swing sweeps through. */
    public enum Plane {
        /** Sideways: through forward and right. */
        HORIZONTAL,
        /** Overhead: through forward and up. */
        VERTICAL
    }

    private final SweepFrameSource source;
    private final float reach;
    private final float halfAngle;
    private final boolean reversed;
    private final Plane plane;
    private @Nullable SweepFrame frame;
    private final Vector3f normal = new Vector3f();

    /**
     * @param reach     how far out the blade reaches, in blocks
     * @param halfAngle half the arc, in degrees off forward; 180 is a full circle
     * @param reversed  whether it runs from the right (or top) to the left (or bottom) instead
     */
    public ArcSweep(SweepFrameSource source, float reach, float halfAngle, boolean reversed, Plane plane) {
        this.source = source;
        this.reach = Math.max(0.0f, reach);
        this.halfAngle = MathHelper.clamp(halfAngle, 0.0f, 180.0f);
        this.reversed = reversed;
        this.plane = plane;
    }

    @Override
    public boolean prepare(float tickDelta) {
        SweepFrame now = source.frame(tickDelta);
        // Kept when its source is lost, so a swing finishes its fade rather than snapping away.
        if (now != null) {
            frame = now;
        }
        if (frame == null || reach <= 0.0f) {
            return false;
        }
        Vec3d n = plane == Plane.VERTICAL ? frame.right() : frame.up();
        normal.set((float) n.x, (float) n.y, (float) n.z).normalize();
        return true;
    }

    @Override
    public void point(float s, float t, double[] out) {
        SweepFrame f = frame;
        double angle = Math.toRadians(angle(s));
        Vec3d axis = plane == Plane.VERTICAL ? f.up() : f.right();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double distance = reach * MathHelper.clamp(t, 0.0f, 1.0f);
        out[0] = f.origin().x + (f.forward().x * cos + axis.x * sin) * distance;
        out[1] = f.origin().y + (f.forward().y * cos + axis.y * sin) * distance;
        out[2] = f.origin().z + (f.forward().z * cos + axis.z * sin) * distance;
    }

    /** The blade's angle at {@code s} through the swing, in degrees off forward. */
    public float angle(float s) {
        float start = reversed ? halfAngle : -halfAngle;
        return MathHelper.lerp(MathHelper.clamp(s, 0.0f, 1.0f), start, -start);
    }

    @Override
    public Vector3f normal() {
        return normal;
    }

    @Override
    public float reach() {
        return reach;
    }

    @Override
    public boolean closed() {
        return halfAngle >= 180.0f;
    }

    @Override
    public boolean viewer() {
        return source.viewer();
    }

    /**
     * A reach to draw for a real one: the same up to {@code knee}, growing ever more slowly past
     * it, and never longer than the real reach - so a very long weapon still reads as long without
     * painting the whole screen, and the drawing never claims space the attack cannot reach.
     *
     * @param softness how quickly growth slows past the knee; smaller is slower
     */
    public static float softReach(float real, float knee, float softness) {
        if (real <= knee) {
            return real;
        }
        float soft = Math.max(1.0e-3f, softness);
        return knee + soft * (float) Math.log1p((real - knee) / soft);
    }
}
