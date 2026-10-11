package dev.eann.atlasvfx;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The shapes any effect can be made of out in the world - pulses, novas, explosions, blasts,
 * beams and arcs - and the scattered kinds that are only motes: bursts, wisps, rain and smoke.
 *
 * <p>Each shape is a few lines of light in its style's colours that grow and fade over a handful
 * of ticks, plus motes thrown off it that then live on their own, so an impact looks like the
 * thing that caused it, only bigger. Start one with {@link FxWorld#play}, or with
 * {@link FxWorld#add} to leave out the debris it throws as it appears.</p>
 */
public final class FxShapes {

    /** Grey smoke or steam rising, belonging to nothing in particular. */
    public static final MoteRecipe SMOKE = new MoteRecipe(FxSprite.particleRun("big_smoke", 12), FxBlend.LIT,
            Motion.EMIT, 0x8A8A8A, 0.32f, 34, 1.0f, 0.25f, 0.04f, -0.006f, 0.04f, true, false);

    /** Turns a shape's own plane to lie flat on the ground. */
    public static final Quaternionf FLAT = new Quaternionf().rotationX((float) (Math.PI / 2.0));

    private FxShapes() {
    }

    // ---- making them ---------------------------------------------------------------------------------------

    /**
     * A blink of light and a ring running out across the view: a hit, a cue, a spark.
     *
     * @param tier 0 to 2, for how much it throws off
     */
    public static FxShape pulse(FxStyle style, Vec3d at, float size, int tier) {
        return new Pulse(style, at, size, tier);
    }

    /** A ring of light running out across the ground, throwing up motes as it goes. */
    public static FxShape nova(FxStyle style, Vec3d at, float size, int tier, long seed) {
        return new Nova(style, at, size, seed);
    }

    /** A bloom with a hot heart, blades of light, a shock ring and a spray of debris. */
    public static FxShape explosion(FxStyle style, Vec3d at, float size, int tier, long seed) {
        return new Explosion(style, at, size, tier, seed);
    }

    /**
     * A shaft of light standing on a point, with a ring at its foot.
     *
     * @param falling whether it comes down onto the point from the sky rather than up out of it
     */
    public static FxShape blast(FxStyle style, Vec3d at, float size, int tier, long seed, boolean falling) {
        return new Blast(style, at, size, falling);
    }

    /** A straight shaft of light between two points, sparkling along its length. */
    public static FxShape beam(FxStyle style, Vec3d from, Vec3d to, float width) {
        return new Beam(style, from, to, width);
    }

    /**
     * A jagged, flickering crack of light between two points.
     *
     * @param tier 0 to 2: how many forks branch off it
     */
    public static FxShape arc(FxStyle style, Vec3d from, Vec3d to, int tier, long seed) {
        return new Arc(style, from, to, tier, seed);
    }

    // ---- motes alone ---------------------------------------------------------------------------------------

    /**
     * A small spray of the style's debris from a point, with a faint blink.
     *
     * @param count how much debris at a full share
     */
    public static void burst(FxWorld world, FxStyle style, Vec3d at, float radius, int count, long seed,
                             FxDensity host) {
        float density = host.effective();
        world.add(pulse(style, at, Math.max(0.3f, radius * 0.6f), 0), host);
        world.spray(style.debris(), at, radius, Math.max(1, Math.round(count * density)));
        world.spray(style.accent(), at, radius, Math.max(1, Math.round(count * density * 0.4f)));
    }

    /** A few motes drifting off a point: something lingering, glowing, smouldering. */
    public static void wisp(FxWorld world, FxStyle style, Vec3d at, float radius, int count, float density) {
        world.scatter(style.wisp(), at, radius, Math.max(1, Math.round(count * density)), 0.02);
    }

    /** Smoke or steam rising over a point. */
    public static void smoke(FxWorld world, Vec3d at, float radius, int count, float density) {
        world.scatter(SMOKE, at, radius, Math.max(1, Math.round(count * density)), 0.03);
    }

    /** Things falling over a disc: the style's own falling mote, or its debris let drop. */
    public static void rain(FxWorld world, FxStyle style, Vec3d at, float radius, int count, float density) {
        MoteRecipe recipe = (style.falling() != null ? style.falling() : style.debris().drifting(0.02f))
                .lasting(1.6f);
        Random random = world.random();
        int total = Math.max(1, Math.round(count * density));
        for (int i = 0; i < total && world.hasRoom(); i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            Vec3d point = at.add(Math.cos(angle) * distance, random.nextDouble() * 0.5, Math.sin(angle) * distance);
            world.motes().add(Mote.free(recipe, point, new Vec3d(0.0, -0.15 - random.nextDouble() * 0.1, 0.0),
                    random, 1.3f));
        }
    }

    // ---- the shapes ----------------------------------------------------------------------------------------

    private static final class Pulse implements FxShape {
        private final FxStyle style;
        private final Vec3d at;
        private final float size;
        private final int tier;

        Pulse(FxStyle style, Vec3d at, float size, int tier) {
            this.style = style;
            this.at = at;
            this.size = size;
            this.tier = clampTier(tier);
        }

        @Override
        public int life() {
            return 8;
        }

        @Override
        public void open(FxWorld world, float density) {
            float more = (1.0f + 0.35f * tier) * density;
            world.spray(style.debris(), at, size * 0.6f, Math.round(5 * more));
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, int age, float tickDelta) {
            float t = FxShape.progress(age, tickDelta, life());
            float fade = (1.0f - t) * (1.0f - t);
            float grow = Math.min(1.0f, t / 0.2f);
            float r = size;
            painter.glow(at, r * 0.7f * (0.6f + 0.4f * grow), style.glow(), 0.75f * fade);
            painter.glow(at, r * 0.22f, style.hot(), fade);
            painter.ring(at, painter.facing(), r * (0.25f + 1.0f * t), r * 0.18f * (1.0f - t), style.hot(),
                    0.8f * (1.0f - t));
        }
    }

    private static final class Nova implements FxShape {
        private final FxStyle style;
        private final Vec3d at;
        private final float size;
        private final long seed;

        Nova(FxStyle style, Vec3d at, float size, long seed) {
            this.style = style;
            this.at = at;
            this.size = size;
            this.seed = seed;
        }

        @Override
        public int life() {
            return 14;
        }

        @Override
        public void tick(FxWorld world, int age, float density) {
            float t = age / (float) life();
            if (t > 0.6f) {
                return;
            }
            // Motes kicked up from the ring's edge as it passes.
            Random random = world.random();
            float ring = size * novaReach(t);
            int count = Math.round((3 + size * 2.5f) * density);
            for (int i = 0; i < count && world.hasRoom(); i++) {
                double angle = random.nextDouble() * Math.PI * 2.0;
                Vec3d out = new Vec3d(Math.cos(angle), 0.0, Math.sin(angle));
                Vec3d point = at.add(out.multiply(ring)).add(0.0, 0.1, 0.0);
                world.motes().add(Mote.free(style.debris().drifting(-0.004f), point,
                        out.multiply(0.04).add(0.0, 0.05 + random.nextDouble() * 0.06, 0.0), random, 1.0f));
            }
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, int age, float tickDelta) {
            float t = FxShape.progress(age, tickDelta, life());
            float fade = (1.0f - t) * (1.0f - t);
            float r = size;
            Vec3d ground = at.add(0.0, 0.08, 0.0);
            float reach = r * novaReach(t);
            float width = Math.max(0.1f, r * 0.12f) * (1.0f - 0.6f * t);
            painter.glow(ground, FLAT, reach * 1.05f, style.glow(), 0.2f * (1.0f - t));
            painter.ring(ground, FLAT, reach, width * 2.0f, style.glow(), 0.9f * (1.0f - t));
            painter.ring(ground, FLAT, reach * 0.85f, width * 0.7f, style.hot(), 0.75f * (1.0f - t));
            painter.glow(at.add(0.0, 0.4, 0.0), r * 0.35f, style.hot(), 0.9f * fade);
            flare(painter, style, at.add(0.0, 0.4, 0.0), r * 0.9f, t, seed);
        }
    }

    private static final class Explosion implements FxShape {
        private final FxStyle style;
        private final Vec3d at;
        private final float size;
        private final int tier;
        private final long seed;

        Explosion(FxStyle style, Vec3d at, float size, int tier, long seed) {
            this.style = style;
            this.at = at;
            this.size = size;
            this.tier = clampTier(tier);
            this.seed = seed;
        }

        @Override
        public int life() {
            return 11;
        }

        @Override
        public void open(FxWorld world, float density) {
            float r = size;
            float more = (1.0f + 0.35f * tier) * density;
            world.spray(style.debris(), at, r, Math.round((14 + r * 10) * more));
            world.spray(style.accent(), at, r, Math.round((6 + r * 5) * more));
            world.scatter(SMOKE, at, r * 0.5f, Math.round((2 + r * 2) * density), 0.04);
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, int age, float tickDelta) {
            float t = FxShape.progress(age, tickDelta, life());
            float fade = (1.0f - t) * (1.0f - t);
            float grow = Math.min(1.0f, t / 0.2f);
            float r = size;
            if (style.rift()) {
                float open = (float) Math.sin(Math.PI * Math.min(1.0f, t * 1.3f));
                painter.rift(at, r * 0.45f * open, t * 0.5f);
                painter.ring(at, painter.facing(), r * 0.47f * open, r * 0.07f, style.glow(), 0.9f * open);
            }
            painter.glow(at, r * (0.5f + 0.7f * grow), style.glow(), 0.9f * fade);
            painter.glow(at, r * 0.3f * (1.0f - 0.5f * t), style.hot(), fade);
            flare(painter, style, at, r * 2.2f * (0.6f + 0.6f * t), t, seed);
            blades(painter, at, style, r, grow, t, fade, 8 + tier * 3, seed);
            painter.ring(at, painter.facing(), r * (0.3f + 1.5f * t), r * 0.2f * (1.0f - t), style.hot(),
                    0.8f * (1.0f - t));
            painter.ring(at, FLAT, r * (0.4f + 1.8f * t), r * 0.25f * (1.0f - t), style.glow(),
                    0.6f * (1.0f - t));
        }
    }

    private static final class Blast implements FxShape {
        private final FxStyle style;
        private final Vec3d at;
        private final float size;
        private final boolean falling;

        Blast(FxStyle style, Vec3d at, float size, boolean falling) {
            this.style = style;
            this.at = at;
            this.size = size;
            this.falling = falling;
        }

        @Override
        public int life() {
            return 16;
        }

        private float height() {
            return MathHelper.clamp(2.5f + size * 1.5f, 3.0f, 10.0f);
        }

        @Override
        public void tick(FxWorld world, int age, float density) {
            float t = age / (float) life();
            if (t > 0.7f) {
                return;
            }
            Random random = world.random();
            float height = height();
            int count = Math.round((2 + size * 1.5f) * density);
            for (int i = 0; i < count && world.hasRoom(); i++) {
                Vector3f v = FxWorld.unit(random);
                Vec3d point = at.add(v.x * size * 0.3, random.nextDouble() * height, v.z * size * 0.3);
                double rise = falling ? -0.18 : 0.14;
                world.motes().add(Mote.free(style.debris().drifting(0.0f), point,
                        new Vec3d(v.x * 0.02, rise * (0.6 + random.nextDouble() * 0.6), v.z * 0.02), random, 1.0f));
            }
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, int age, float tickDelta) {
            float t = FxShape.progress(age, tickDelta, life());
            float fade = (1.0f - t) * (1.0f - t);
            float grow = Math.min(1.0f, t / 0.2f);
            float r = size;
            Vec3d top = at.add(0.0, height(), 0.0);
            // A falling shaft drops from the sky and lands; a rising one shoots up and thins.
            float reach = falling ? Math.min(1.0f, t * 3.0f) : grow;
            Vec3d start = falling ? top : at;
            Vec3d end = falling ? top.lerp(at, reach) : at.lerp(top, reach);
            float width = r * 0.55f * (1.0f - 0.7f * t);
            painter.beam(start, end, width, style.glow(), 0.85f * fade);
            painter.beam(start, end, width * 0.3f, style.hot(), fade);
            painter.glow(at.add(0.0, 0.2, 0.0), r * 0.8f, style.glow(), 0.8f * fade);
            painter.glow(end, r * 0.45f, style.hot(), 0.8f * fade);
            painter.ring(at.add(0.0, 0.08, 0.0), FLAT, r * (0.3f + 1.2f * t), r * 0.22f * (1.0f - t),
                    style.glow(), 0.8f * (1.0f - t));
        }
    }

    private static final class Beam implements FxShape {
        private final FxStyle style;
        private final Vec3d from;
        private final Vec3d to;
        private final float width;

        Beam(FxStyle style, Vec3d from, Vec3d to, float width) {
            this.style = style;
            this.from = from;
            this.to = to;
            this.width = width;
        }

        @Override
        public int life() {
            return 8;
        }

        @Override
        public void open(FxWorld world, float density) {
            sparkleAlong(world, style, from, to, density);
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, int age, float tickDelta) {
            float t = FxShape.progress(age, tickDelta, life());
            float fade = (1.0f - t) * (1.0f - t);
            float w = Math.max(0.05f, width) * (1.0f - 0.3f * t);
            painter.beam(from, to, w, style.glow(), 0.85f * fade);
            painter.beam(from, to, w * 0.35f, style.hot(), fade);
            painter.glow(from, w * 1.5f, style.hot(), 0.8f * fade);
            painter.glow(to, w * 2.0f, style.glow(), 0.9f * fade);
        }
    }

    /**
     * A crack of light: a line pushed about at random between its ends, redrawn on another
     * random path every tick so it flickers, with forks off it for a stronger one.
     */
    private static final class Arc implements FxShape {
        private final FxStyle style;
        private final Vec3d from;
        private final Vec3d to;
        private final int tier;
        private final long seed;

        Arc(FxStyle style, Vec3d from, Vec3d to, int tier, long seed) {
            this.style = style;
            this.from = from;
            this.to = to;
            this.tier = clampTier(tier);
            this.seed = seed;
        }

        @Override
        public int life() {
            return 5;
        }

        @Override
        public void open(FxWorld world, float density) {
            sparkleAlong(world, style, from, to, density);
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, int age, float tickDelta) {
            float t = FxShape.progress(age, tickDelta, life());
            float fade = (1.0f - t) * (1.0f - t);
            Random jag = new Random(seed + age * 31L);
            List<Vec3d> path = jagged(from, to, jag, 0.18f + tier * 0.05f);
            strokes(painter, path, style, fade, 1.0f);
            for (int fork = 0; fork < tier; fork++) {
                Vec3d start = path.get(1 + jag.nextInt(Math.max(1, path.size() - 2)));
                Vector3f v = FxWorld.unit(jag);
                double length = from.distanceTo(to) * 0.3;
                Vec3d end = start.add(v.x * length, v.y * length, v.z * length);
                strokes(painter, jagged(start, end, jag, 0.12f), style, fade, 0.6f);
            }
            painter.glow(from, 0.25f, style.hot(), 0.8f * fade);
            painter.glow(to, 0.35f, style.glow(), 0.9f * fade);
        }

        private static void strokes(FxPainter painter, List<Vec3d> path, FxStyle style, float fade, float scale) {
            for (int i = 1; i < path.size(); i++) {
                painter.beam(path.get(i - 1), path.get(i), 0.16f * scale, style.glow(), 0.9f * fade);
                painter.beam(path.get(i - 1), path.get(i), 0.05f * scale, style.hot(), fade);
            }
        }

        private static List<Vec3d> jagged(Vec3d from, Vec3d to, Random jag, float jitter) {
            Vec3d span = to.subtract(from);
            int steps = Math.max(3, (int) Math.ceil(span.length() * 2.5));
            List<Vec3d> path = new ArrayList<>(steps + 1);
            path.add(from);
            for (int i = 1; i < steps; i++) {
                Vector3f v = FxWorld.unit(jag);
                path.add(from.add(span.multiply(i / (double) steps)).add(v.x * jitter, v.y * jitter, v.z * jitter));
            }
            path.add(to);
            return path;
        }
    }

    // ---- pieces they share -------------------------------------------------------------------------------------

    /** Debris shaken loose along a line of light as it appears. */
    private static void sparkleAlong(FxWorld world, FxStyle style, Vec3d from, Vec3d to, float density) {
        Random random = world.random();
        Vec3d span = to.subtract(from);
        int count = Math.round((float) span.length() * 3.0f * density) + 1;
        for (int i = 0; i < count && world.hasRoom(); i++) {
            Vec3d point = from.add(span.multiply(random.nextDouble()));
            Vector3f v = FxWorld.unit(random);
            world.motes().add(Mote.free(style.debris(), point, new Vec3d(v.x, v.y, v.z).multiply(0.03), random, 0.7f));
        }
    }

    /** Blades of light thrown out round a point, facing the viewer. */
    public static void blades(FxPainter painter, Vec3d at, FxStyle style, float r, float grow, float t,
                              float fade, int count, long seed) {
        Quaternionf facing = painter.facing();
        Vector3f axisA = facing.transform(new Vector3f(1.0f, 0.0f, 0.0f));
        Vector3f axisB = facing.transform(new Vector3f(0.0f, 1.0f, 0.0f));
        Vector3f normal = facing.transform(new Vector3f(0.0f, 0.0f, 1.0f));
        Random blades = new Random(seed);
        float spin = blades.nextFloat() * MathHelper.TAU;
        for (int i = 0; i < count; i++) {
            float angle = spin + i * MathHelper.TAU / count + (blades.nextFloat() - 0.5f) * 0.4f;
            Vector3f along = new Vector3f(axisA).mul((float) Math.cos(angle))
                    .add(new Vector3f(axisB).mul((float) Math.sin(angle)));
            Vector3f across = new Vector3f(normal).cross(along).normalize();
            float length = r * (0.9f + 0.8f * blades.nextFloat()) * grow * (1.0f - 0.3f * t);
            painter.petal(at, along, across, length, r * 0.18f, style.hot(), style.glow(), 0.9f * fade);
        }
    }

    /** The style's own picture going off once over a flash, if it has one. */
    public static void flare(FxPainter painter, FxStyle style, Vec3d at, float size, float t, long seed) {
        if (style.flare() != null) {
            painter.sprite(FxBlend.EMISSIVE, style.flare(), t, (int) seed, true, at, size,
                    (seed % 628) / 100.0f, style.flareTint(), 0.9f * (1.0f - t), FxLight.FULL_BRIGHT, false);
        }
    }

    /** How far out a nova's ring has run: fast at first, easing as it reaches its edge. */
    public static float novaReach(float t) {
        float out = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        return 0.08f + 0.92f * out;
    }

    private static int clampTier(int tier) {
        return MathHelper.clamp(tier, 0, 2);
    }
}
