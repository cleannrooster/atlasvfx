package dev.eann.atlasvfx.trail;

import dev.eann.atlasvfx.FxCurve;
import dev.eann.atlasvfx.FxLife;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Random;

/**
 * A thrust: a spearhead driven straight down its frame's forward direction, extending as the
 * thrust goes out.
 *
 * <p>{@code s} runs along it from the root, 0, to the point, 1, of however far it has extended;
 * {@code t} runs across it from one side to the other. Its outline is narrow at the root, widest a
 * little past halfway and closes to the point, with barbs along both sides raking back toward the
 * thrower, like streaks torn off the point.</p>
 *
 * <p>It is never wider than {@code halfWidth}, and never wider than the thrust's cone at that
 * distance when it has one: near the thrower a lane's corners lie outside what a cone-limited
 * attack can hit. The outline and the barbs only narrow it from there, so a lane drawn for an
 * attack with the same frame, reach, width and angle claims no space the attack cannot reach.</p>
 */
public final class LaneSweep implements SweptSurface {

    /** Where the spearhead is widest, as a share of the way from root to point. */
    private static final float WIDEST = 0.55f;
    /** How far along each barb its tip sits: early, so the steep side faces the point. */
    private static final float BARB_TIP = 0.15f;

    private final SweepFrameSource source;
    private final float reach;
    private final float halfWidth;
    private final float halfAngle;
    private final int extendTicks;
    private final float clearance;
    private final float[] barbDepths;

    private @Nullable SweepFrame frame;
    private int age;
    private float extension;
    private float root;
    private final Vector3f normal = new Vector3f(0.0f, 1.0f, 0.0f);

    /**
     * @param reach       how far it reaches once fully out, in blocks
     * @param halfWidth   half its widest width, in blocks
     * @param halfAngle   the thrust's cone, in degrees either side of forward; 0 for no cone
     * @param extendTicks how long it takes to reach full length
     * @param barbs       how many barbs along each side; 0 for a clean outline
     * @param barbDepth   how deep each cuts in, as a share of the width there, at most 0.85
     * @param clearance   on the viewer's own thrust, in their own eyes, how close to them it may
     *                    start, in blocks
     */
    public LaneSweep(SweepFrameSource source, float reach, float halfWidth, float halfAngle, int extendTicks,
                     int barbs, float barbDepth, float clearance, long seed) {
        this.source = source;
        this.reach = Math.max(0.0f, reach);
        this.halfWidth = Math.max(0.0f, halfWidth);
        this.halfAngle = MathHelper.clamp(halfAngle, 0.0f, 89.0f);
        this.extendTicks = Math.max(1, extendTicks);
        this.clearance = Math.max(0.0f, clearance);
        float depth = MathHelper.clamp(barbDepth, 0.0f, 0.85f);
        Random random = new Random(seed);
        this.barbDepths = new float[Math.max(0, barbs)];
        for (int i = 0; i < barbDepths.length; i++) {
            barbDepths[i] = depth * (0.5f + 0.5f * random.nextFloat());
        }
    }

    @Override
    public void tick(FxLife life) {
        age = life.age();
    }

    @Override
    public boolean prepare(float tickDelta) {
        SweepFrame now = source.frame(tickDelta);
        if (now != null) {
            frame = now;
        }
        extension = MathHelper.clamp((age + tickDelta) / extendTicks, 0.0f, 1.0f);
        if (frame == null || reach <= 0.0f || extension <= 1.0e-4f) {
            return false;
        }
        float length = reach * extension;
        root = source.viewer() && length > 0.0f ? Math.min(0.999f, clearance / length) : 0.0f;
        Vec3d up = frame.up();
        normal.set((float) up.x, (float) up.y, (float) up.z).normalize();
        return root < 0.999f;
    }

    @Override
    public void point(float s, float t, double[] out) {
        SweepFrame f = frame;
        float u = lerp(MathHelper.clamp(s, 0.0f, 1.0f), root, 1.0f);
        double distance = reach * extension * u;
        double allowed = halfWidth;
        if (halfAngle > 0.0f) {
            allowed = Math.min(allowed, distance * Math.tan(Math.toRadians(halfAngle)));
        }
        double width = allowed * outline(u) * barb(u);
        double x = (MathHelper.clamp(t, 0.0f, 1.0f) * 2.0 - 1.0) * width;
        Vec3d o = f.origin();
        Vec3d forward = f.forward();
        Vec3d right = f.right();
        out[0] = o.x + forward.x * distance + right.x * x;
        out[1] = o.y + forward.y * distance + right.y * x;
        out[2] = o.z + forward.z * distance + right.z * x;
    }

    /** The spearhead's width at {@code u} from root to point, as a share of its widest. */
    static float outline(float u) {
        if (u <= WIDEST) {
            return 0.3f + 0.7f * FxCurve.SMOOTH.at(u / WIDEST);
        }
        return MathHelper.clamp((1.0f - u) / (1.0f - WIDEST), 0.0f, 1.0f);
    }

    /** How much of the width the barbs leave at {@code u}: full at each barb's tip, least just before it. */
    float barb(float u) {
        int count = barbDepths.length;
        if (count == 0) {
            return 1.0f;
        }
        float along = MathHelper.clamp(u, 0.0f, 0.9999f) * count;
        int which = (int) along;
        float f = along - which;
        float depth = barbDepths[which];
        if (f < BARB_TIP) {
            return lerp(f / BARB_TIP, 1.0f - depth, 1.0f);
        }
        return 1.0f - depth * (f - BARB_TIP) / (1.0f - BARB_TIP);
    }

    @Override
    public Vector3f normal() {
        return normal;
    }

    @Override
    public float reach() {
        return reach * extension;
    }

    @Override
    public boolean closed() {
        return false;
    }

    @Override
    public boolean viewer() {
        // Clearance is taken off the root here, along the lane, not across it.
        return false;
    }

    private static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }
}
