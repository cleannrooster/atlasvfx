package dev.eann.atlasvfx.trail;

import dev.eann.atlasvfx.FxAnchor;
import dev.eann.atlasvfx.FxCurve;
import dev.eann.atlasvfx.FxLife;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Supplier;

/**
 * A surface recorded from something moving - a projectile, a dash, a staff's tip, a blade - for
 * when its path is not known in advance.
 *
 * <p>It takes one sample a tick and draws a smooth curve through them - Catmull-Rom, which passes
 * through every sample - with the thing's position this very frame as its head, so the trail
 * looks the same at any frame rate and is never a tick behind what made it. {@code s} runs from
 * the oldest part still showing, 0, to that head, 1; the tail keeps {@code maxAge} ticks behind
 * the head, and once whatever it follows lets go, or its trail is released, it stops sampling
 * and the tail runs up to the last sample and the trail is over.</p>
 *
 * <p>Two kinds. A point trail follows one anchor and is a ribbon {@code width} wide, turned about
 * its own path to face the viewer, narrowing along {@code taper}; {@code t} runs across it. A
 * blade trail follows two - a hilt and a tip - and is the surface swept between them; {@code t}
 * runs from hilt to tip.</p>
 *
 * <p>A jump between samples longer than {@code jump} blocks - a teleport, a respawn - starts the
 * trail again rather than streaking across the world.</p>
 */
public final class SampledSweep implements SweptSurface {

    /** A tick's move longer than this, in blocks, starts the trail again. */
    public static final double DEFAULT_JUMP = 6.0;

    private final FxAnchor first;
    private final @Nullable FxAnchor second;
    private final int maxAge;
    private final float width;
    private final FxCurve taper;
    private final double jumpSquared;
    private final Supplier<Vec3d> eye;
    private final TrailBuffer hilt;
    private final @Nullable TrailBuffer tip;

    private boolean stopped;
    private int age;

    // Worked out once a frame: the control points the curve passes through, oldest first, ending
    // in the head; and the span of time that shows.
    private int controls;
    private final double[] times;
    private final double[] hiltPoints;
    private final double[] tipPoints;
    private double headTime;
    private double tailTime;
    private double eyeX;
    private double eyeY;
    private double eyeZ;
    private float reach;
    private final Vector3f normal = new Vector3f(0.0f, 1.0f, 0.0f);
    private final double[] scratch = new double[3];
    private final double[] scratchTip = new double[3];
    private final double[] tangent = new double[3];

    private SampledSweep(FxAnchor first, @Nullable FxAnchor second, int maxAge, float width, FxCurve taper,
                         double jump, Supplier<Vec3d> eye) {
        this.first = first;
        this.second = second;
        this.maxAge = Math.max(1, maxAge);
        this.width = Math.max(0.0f, width);
        this.taper = taper;
        this.jumpSquared = jump * jump;
        this.eye = eye;
        // A few more than it shows: the oldest showing needs a neighbour before it for the curve.
        int capacity = this.maxAge + 4;
        this.hilt = new TrailBuffer(capacity);
        this.tip = second != null ? new TrailBuffer(capacity) : null;
        this.times = new double[capacity + 1];
        this.hiltPoints = new double[(capacity + 1) * 3];
        this.tipPoints = second != null ? new double[(capacity + 1) * 3] : new double[0];
    }

    /**
     * A ribbon following one anchor.
     *
     * @param width  across, in blocks, at the head
     * @param taper  its width from the tail, 0, to the head, 1, as a share of {@code width}
     * @param maxAge how many ticks of path it keeps behind the head
     */
    public static SampledSweep point(FxAnchor anchor, float width, FxCurve taper, int maxAge) {
        return new SampledSweep(anchor, null, maxAge, width, taper, DEFAULT_JUMP, SampledSweep::cameraPosition);
    }

    /**
     * The surface swept between a hilt and a tip.
     *
     * @param maxAge how many ticks of path it keeps behind the head
     */
    public static SampledSweep blade(FxAnchor hilt, FxAnchor tip, int maxAge) {
        return new SampledSweep(hilt, tip, maxAge, 0.0f, FxCurve.ONE, DEFAULT_JUMP, SampledSweep::cameraPosition);
    }

    /** The same, starting again at a jump longer than {@code blocks} in a tick. */
    public SampledSweep withJump(double blocks) {
        return new SampledSweep(first, second, maxAge, width, taper, blocks, eye);
    }

    /** The same, facing a viewer other than the camera: for testing, or a view of one's own. */
    public SampledSweep facing(Supplier<Vec3d> viewer) {
        return new SampledSweep(first, second, maxAge, width, taper, Math.sqrt(jumpSquared), viewer);
    }

    // ---- recording -----------------------------------------------------------------------------------------

    @Override
    public void tick(FxLife life) {
        age = life.age();
        if (stopped) {
            return;
        }
        if (life.released() || !first.held() || (second != null && !second.held())) {
            stopped = true;
            return;
        }
        Vec3d a = first.position(1.0f);
        Vec3d b = second != null ? second.position(1.0f) : null;
        if (a == null || (second != null && b == null)) {
            stopped = true;
            return;
        }
        if (!hilt.isEmpty() && hilt.newestDistanceSquared(a.x, a.y, a.z) > jumpSquared) {
            hilt.clear();
            if (tip != null) {
                tip.clear();
            }
        }
        // Taken at the end of tick {@code age}: where it is once the tick is over.
        long at = age + 1L;
        hilt.push(a.x, a.y, a.z, at);
        if (tip != null) {
            tip.push(b.x, b.y, b.z, at);
        }
    }

    @Override
    public boolean finished(FxLife life) {
        return stopped && (hilt.isEmpty() || life.age() - maxAge >= hilt.newestTime());
    }

    /** Whether it has stopped recording. */
    public boolean stopped() {
        return stopped;
    }

    // ---- drawing ---------------------------------------------------------------------------------------

    @Override
    public boolean prepare(float tickDelta) {
        double now = age + tickDelta;
        controls = 0;
        Vec3d liveHilt = null;
        Vec3d liveTip = null;
        if (!stopped) {
            liveHilt = first.position(tickDelta);
            liveTip = second != null ? second.position(tickDelta) : null;
            if (liveHilt == null || (second != null && liveTip == null)) {
                liveHilt = null;
            }
        }
        headTime = liveHilt != null ? now : (hilt.isEmpty() ? now : hilt.newestTime());
        for (int i = 0; i < hilt.size(); i++) {
            // With a live head, only the samples behind it: the newest is where it will be at the
            // end of this tick, ahead of where it is drawn.
            if (liveHilt != null && hilt.time(i) >= headTime) {
                break;
            }
            put(controls++, hilt.time(i), hilt.x(i), hilt.y(i), hilt.z(i),
                    tip != null ? tip.x(i) : 0.0, tip != null ? tip.y(i) : 0.0, tip != null ? tip.z(i) : 0.0);
        }
        if (liveHilt != null) {
            put(controls++, headTime, liveHilt.x, liveHilt.y, liveHilt.z,
                    liveTip != null ? liveTip.x : 0.0, liveTip != null ? liveTip.y : 0.0,
                    liveTip != null ? liveTip.z : 0.0);
        }
        if (controls < 2) {
            return false;
        }
        tailTime = Math.max(times[0], now - maxAge);
        if (headTime - tailTime < 1.0e-3) {
            return false;
        }

        int h = (controls - 1) * 3;
        int p = (controls - 2) * 3;
        if (tip != null) {
            double ax = tipPoints[h] - hiltPoints[h];
            double ay = tipPoints[h + 1] - hiltPoints[h + 1];
            double az = tipPoints[h + 2] - hiltPoints[h + 2];
            reach = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            // Square to the blade and to the way it is moving.
            Vector3f blade = new Vector3f((float) ax, (float) ay, (float) az);
            Vector3f moving = new Vector3f((float) (hiltPoints[h] - hiltPoints[p] + tipPoints[h] - tipPoints[p]),
                    (float) (hiltPoints[h + 1] - hiltPoints[p + 1] + tipPoints[h + 1] - tipPoints[p + 1]),
                    (float) (hiltPoints[h + 2] - hiltPoints[p + 2] + tipPoints[h + 2] - tipPoints[p + 2]));
            blade.cross(moving);
            if (blade.lengthSquared() > 1.0e-10f) {
                normal.set(blade.normalize());
            }
        } else {
            // Only a point trail turns to the viewer; a blade's surface is where the blade went.
            Vec3d viewer = eye.get();
            eyeX = viewer.x;
            eyeY = viewer.y;
            eyeZ = viewer.z;
            reach = width;
            Vector3f toEye = new Vector3f((float) (eyeX - hiltPoints[h]), (float) (eyeY - hiltPoints[h + 1]),
                    (float) (eyeZ - hiltPoints[h + 2]));
            if (toEye.lengthSquared() > 1.0e-10f) {
                normal.set(toEye.normalize());
            }
        }
        return true;
    }

    @Override
    public void point(float s, float t, double[] out) {
        double when = tailTime + (headTime - tailTime) * Math.max(0.0f, Math.min(1.0f, s));
        int segment = segmentAt(when);
        double span = times[segment + 1] - times[segment];
        double u = span > 1.0e-9 ? (when - times[segment]) / span : 0.0;
        float across = Math.max(0.0f, Math.min(1.0f, t));
        if (tip != null) {
            curve(hiltPoints, segment, u, scratch);
            curve(tipPoints, segment, u, scratchTip);
            out[0] = scratch[0] + (scratchTip[0] - scratch[0]) * across;
            out[1] = scratch[1] + (scratchTip[1] - scratch[1]) * across;
            out[2] = scratch[2] + (scratchTip[2] - scratch[2]) * across;
            return;
        }
        curve(hiltPoints, segment, u, scratch);
        slope(hiltPoints, segment, u, tangent);
        // Across the path and across the line of sight to it, so it faces the viewer.
        double vx = eyeX - scratch[0];
        double vy = eyeY - scratch[1];
        double vz = eyeZ - scratch[2];
        double sx = tangent[1] * vz - tangent[2] * vy;
        double sy = tangent[2] * vx - tangent[0] * vz;
        double sz = tangent[0] * vy - tangent[1] * vx;
        double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
        double offset = (across - 0.5) * width * taper.at(s);
        if (length < 1.0e-9) {
            out[0] = scratch[0];
            out[1] = scratch[1] + offset;
            out[2] = scratch[2];
            return;
        }
        out[0] = scratch[0] + sx / length * offset;
        out[1] = scratch[1] + sy / length * offset;
        out[2] = scratch[2] + sz / length * offset;
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
        return false;
    }

    @Override
    public boolean viewer() {
        return false;
    }

    // ---- the curve ---------------------------------------------------------------------------------------

    private void put(int i, double when, double hx, double hy, double hz, double tx, double ty, double tz) {
        times[i] = when;
        hiltPoints[i * 3] = hx;
        hiltPoints[i * 3 + 1] = hy;
        hiltPoints[i * 3 + 2] = hz;
        if (tip != null) {
            tipPoints[i * 3] = tx;
            tipPoints[i * 3 + 1] = ty;
            tipPoints[i * 3 + 2] = tz;
        }
    }

    /** The control segment a moment falls in: between control {@code i} and {@code i + 1}. */
    private int segmentAt(double when) {
        int i = 0;
        while (i < controls - 2 && times[i + 1] < when) {
            i++;
        }
        return i;
    }

    /**
     * A point on the Catmull-Rom curve through the controls, {@code u} of the way along segment
     * {@code i}. The ends borrow a neighbour mirrored through them, so the curve stays straight
     * where there is nothing beyond to bend it.
     */
    private void curve(double[] points, int i, double u, double[] out) {
        double u2 = u * u;
        double u3 = u2 * u;
        for (int axis = 0; axis < 3; axis++) {
            double p1 = points[i * 3 + axis];
            double p2 = points[(i + 1) * 3 + axis];
            double p0 = i > 0 ? points[(i - 1) * 3 + axis] : 2.0 * p1 - p2;
            double p3 = i + 2 < controls ? points[(i + 2) * 3 + axis] : 2.0 * p2 - p1;
            out[axis] = 0.5 * (2.0 * p1 + (-p0 + p2) * u + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u2
                    + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u3);
        }
    }

    /** The curve's direction at the same place: its derivative, not normalised. */
    private void slope(double[] points, int i, double u, double[] out) {
        double u2 = u * u;
        for (int axis = 0; axis < 3; axis++) {
            double p1 = points[i * 3 + axis];
            double p2 = points[(i + 1) * 3 + axis];
            double p0 = i > 0 ? points[(i - 1) * 3 + axis] : 2.0 * p1 - p2;
            double p3 = i + 2 < controls ? points[(i + 2) * 3 + axis] : 2.0 * p2 - p1;
            out[axis] = 0.5 * ((-p0 + p2) + 2.0 * (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u
                    + 3.0 * (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u2);
        }
    }

    private static Vec3d cameraPosition() {
        return MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
    }
}
