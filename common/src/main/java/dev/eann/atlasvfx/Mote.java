package dev.eann.atlasvfx;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * One small moving thing: a spark, a chip of stone, a curl of smoke.
 *
 * <p>Two kinds of position. A mote that gathers or orbits belongs to an {@link FxAnchor}: it is
 * kept as an offset from it and drawn wherever the anchor is that frame, so it follows a waving
 * arm without trailing behind it. A mote that has left - rising, falling, thrown - is on its own
 * in the world, and moves by its velocity like any particle.</p>
 *
 * <p>The anchored ones work out their offset from their age directly rather than stepping it,
 * so a spiral is a spiral however the frames fall.</p>
 */
public final class Mote {

    private final MoteRecipe recipe;
    /** What it belongs to; null for one on its own in the world. */
    private final @Nullable FxAnchor anchor;
    private final int pick;
    private final int life;
    private final float size;
    private final int tint;

    private int age;
    /** Anchored: offset from the anchor. Free: position in the world. */
    private double x;
    private double y;
    private double z;
    private double prevX;
    private double prevY;
    private double prevZ;
    private double vx;
    private double vy;
    private double vz;
    private float roll;
    private float prevRoll;
    private final float spin;

    // Anchored motion: where it started on the shell, what it turns about, and how fast.
    private final Vector3f start;
    private final Vector3f axis;
    private final float turnRate;
    private final float radius;
    private final float phase;

    private Mote(MoteRecipe recipe, @Nullable FxAnchor anchor, Random random, float sizeScale) {
        this.recipe = recipe;
        this.anchor = anchor;
        this.pick = random.nextInt(1 << 16);
        this.life = Math.max(2, Math.round(recipe.life() * (0.75f + random.nextFloat() * 0.5f)));
        this.size = recipe.size() * sizeScale * (0.7f + random.nextFloat() * 0.6f);
        this.tint = recipe.tint();
        this.spin = recipe.spin() * (random.nextBoolean() ? 1.0f : -1.0f) * (0.5f + random.nextFloat());
        this.roll = random.nextFloat() * MathHelper.TAU;
        this.prevRoll = roll;
        this.start = randomUnit(random);
        this.axis = randomUnit(random);
        this.turnRate = (recipe.motion() == Motion.ORBIT ? recipe.speed() : 0.25f)
                * (random.nextBoolean() ? 1.0f : -1.0f) * (0.7f + random.nextFloat() * 0.6f);
        this.radius = recipe.radius() * sizeScale * (0.75f + random.nextFloat() * 0.5f);
        this.phase = random.nextFloat() * MathHelper.TAU;
    }

    /** A mote that belongs to an anchor: gathering into it or circling it. */
    public static Mote anchored(MoteRecipe recipe, FxAnchor anchor, Random random, float sizeScale) {
        Mote mote = new Mote(recipe, anchor, random, sizeScale);
        mote.placeAnchored(0.0f);
        mote.prevX = mote.x;
        mote.prevY = mote.y;
        mote.prevZ = mote.z;
        return mote;
    }

    /**
     * A mote on its own in the world.
     *
     * @param from     where it starts, in the world; it is born up to its recipe's radius off it
     * @param velocity its velocity at birth, blocks per tick
     */
    public static Mote free(MoteRecipe recipe, Vec3d from, Vec3d velocity, Random random, float sizeScale) {
        Mote mote = new Mote(recipe, null, random, sizeScale);
        double spread = recipe.radius() * sizeScale;
        mote.x = from.x + (random.nextDouble() - 0.5) * 2.0 * spread;
        mote.y = from.y + (random.nextDouble() - 0.5) * 2.0 * spread;
        mote.z = from.z + (random.nextDouble() - 0.5) * 2.0 * spread;
        mote.prevX = mote.x;
        mote.prevY = mote.y;
        mote.prevZ = mote.z;
        mote.vx = velocity.x;
        mote.vy = velocity.y;
        mote.vz = velocity.z;
        return mote;
    }

    public MoteRecipe recipe() {
        return recipe;
    }

    @Nullable
    public FxAnchor anchor() {
        return anchor;
    }

    /** Whether it is drawn into the first-person view: only an anchored mote can be. */
    public boolean inView() {
        return anchor != null && anchor.inView();
    }

    /** Its own fixed number, for which frame or corner of a picture it shows. */
    public int pick() {
        return pick;
    }

    public int tint() {
        return tint;
    }

    /**
     * Its anchor has let go: it fades out over the next few ticks rather than vanishing, under
     * whatever has replaced what it belonged to.
     */
    void letGo() {
        age = Math.max(age, life - 4);
    }

    /** Advances one tick; false once it is spent. */
    boolean tick() {
        prevX = x;
        prevY = y;
        prevZ = z;
        prevRoll = roll;
        roll += spin;
        age++;
        if (age >= life) {
            return false;
        }
        if (anchor != null) {
            placeAnchored(age / (float) life);
            return true;
        }
        x += vx;
        y += vy;
        z += vz;
        Motion motion = recipe.motion();
        // Thrown things slow quickly; drifting things hardly at all.
        double drag = motion == Motion.BURST ? 0.86 : 0.96;
        vx *= drag;
        vy = vy * drag - recipe.gravity();
        vz *= drag;
        return true;
    }

    /**
     * The offset from the anchor at this point in life.
     *
     * <p>Gathering: from a point on a shell around the anchor, round the turning axis and in,
     * faster as it nears, so the last stretch is a rush into it. Orbiting: round the axis at a
     * steady distance, swelling out over the first moments and bobbing along it.</p>
     */
    private void placeAnchored(float t) {
        Vector3f offset;
        if (recipe.motion() == Motion.GATHER) {
            float inward = 1.0f - t;
            float distance = radius * inward * inward;
            float turned = turnRate * age * 1.6f + t * t * 3.0f * Math.signum(turnRate);
            offset = new Vector3f(start).mul(distance).rotate(new Quaternionf().rotationAxis(turned, axis));
        } else {
            float swell = Math.min(1.0f, t * 5.0f);
            Vector3f across = new Vector3f(start).cross(axis);
            if (across.lengthSquared() < 1.0e-4f) {
                across.set(1.0f, 0.0f, 0.0f).cross(axis);
            }
            across.normalize();
            Vector3f other = new Vector3f(axis).cross(across).normalize();
            float angle = phase + turnRate * age;
            float bob = (float) Math.sin(age * 0.35f + phase) * radius * 0.25f;
            offset = across.mul((float) Math.cos(angle) * radius * swell)
                    .add(other.mul((float) Math.sin(angle) * radius * swell))
                    .add(new Vector3f(axis).mul(bob));
        }
        x = offset.x;
        y = offset.y;
        z = offset.z;
    }

    /** How far through its life it is, with the frame's fraction. */
    public float lifeFraction(float tickDelta) {
        return MathHelper.clamp((age + tickDelta) / life, 0.0f, 1.0f);
    }

    /** How strongly it shows: rising in, holding, and fading out. */
    public float alpha(float tickDelta) {
        float t = lifeFraction(tickDelta);
        float fadeIn = recipe.motion() == Motion.GATHER ? 0.35f : 0.12f;
        float in = Math.min(1.0f, t / fadeIn);
        float out = t < 0.6f ? 1.0f : 1.0f - (t - 0.6f) / 0.4f;
        return MathHelper.clamp(in * out, 0.0f, 1.0f);
    }

    /** How big it is drawn: shrinking into its anchor, or swelling as smoke does. */
    public float drawnSize(float tickDelta) {
        float t = lifeFraction(tickDelta);
        return switch (recipe.motion()) {
            case GATHER -> size * (1.0f - 0.55f * t);
            case EMIT -> recipe.flips() ? size * (1.0f + 1.4f * t) : size * (1.0f - 0.4f * t);
            case BURST -> recipe.flips() ? size * (1.0f + 0.8f * t) : size * (1.0f - 0.5f * t);
            default -> size;
        };
    }

    /** Its turn in its own plane this frame. */
    public float roll(float tickDelta) {
        return MathHelper.lerp(tickDelta, prevRoll, roll);
    }

    /** Anchored: its offset from the anchor. Free: where it is in the world. */
    public Vec3d offsetOrPosition(float tickDelta) {
        return new Vec3d(MathHelper.lerp(tickDelta, prevX, x), MathHelper.lerp(tickDelta, prevY, y),
                MathHelper.lerp(tickDelta, prevZ, z));
    }

    private static Vector3f randomUnit(Random random) {
        Vector3f v = new Vector3f();
        do {
            v.set(random.nextFloat() * 2.0f - 1.0f, random.nextFloat() * 2.0f - 1.0f, random.nextFloat() * 2.0f - 1.0f);
        } while (v.lengthSquared() < 1.0e-3f || v.lengthSquared() > 1.0f);
        return v.normalize();
    }
}
