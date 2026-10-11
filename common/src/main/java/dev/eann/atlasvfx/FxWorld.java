package dev.eann.atlasvfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Effects out in the world, away from anything that draws itself: the shapes alive now, and the
 * motes thrown off them or left behind by anything else.
 *
 * <p>One for the whole client. It is ticked with the client, cleared with the world, and drawn
 * after the world's translucent geometry, through every {@link FxPass} in turn.</p>
 */
public final class FxWorld {

    /** Ceiling on its own motes alive at once, inside the shared {@link FxBudget}. */
    private static final int MAX_MOTES = 2400;
    private static final FxWorld INSTANCE = new FxWorld();

    /** One shape alive, with whose share of the effect it is made at; also its handle. */
    private static final class Live implements FxHandle {
        final FxShape shape;
        final FxDensity host;
        final FxLife life = new FxLife();
        boolean ended;

        Live(FxShape shape, FxDensity host) {
            this.shape = shape;
            this.host = host;
        }

        @Override
        public void release() {
            if (!ended) {
                life.release();
            }
        }

        @Override
        public void cancel() {
            if (!ended) {
                life.cancel();
            }
        }

        @Override
        public void flash() {
            if (!ended) {
                life.flash();
            }
        }

        @Override
        public boolean alive() {
            return !ended && !life.cancelled();
        }
    }

    private final List<Live> shapes = new ArrayList<>();
    private final MotePool motes = new MotePool(MAX_MOTES);
    private final Random random = new Random();

    private FxWorld() {
    }

    public static FxWorld get() {
        return INSTANCE;
    }

    // ---- shapes ----------------------------------------------------------------------------------------

    /**
     * Starts a shape, with what it throws off the moment it appears.
     *
     * @param host whose effect it is, for how much of it to make
     * @return a handle to let go of it, put it away or flash it
     */
    public FxHandle play(FxShape shape, FxDensity host) {
        Live live = new Live(shape, host);
        shapes.add(live);
        shape.open(this, host.effective());
        return live;
    }

    /**
     * Starts a shape on its own, without what it would throw off as it appears: for when
     * something else is already supplying the debris.
     *
     * @return a handle to let go of it, put it away or flash it
     */
    public FxHandle add(FxShape shape, FxDensity host) {
        Live live = new Live(shape, host);
        shapes.add(live);
        return live;
    }

    // ---- motes ---------------------------------------------------------------------------------------------

    public MotePool motes() {
        return motes;
    }

    public Random random() {
        return random;
    }

    /** Whether one more mote would be kept. */
    public boolean hasRoom() {
        return motes.hasRoom();
    }

    /** One mote, as it comes. */
    public void spawn(MoteRecipe recipe, Vec3d at, Vec3d velocity, float scale) {
        if (motes.hasRoom()) {
            motes.add(Mote.free(recipe, at, velocity, random, scale));
        }
    }

    /** Debris thrown out in every direction, far enough to reach about {@code reach} blocks. */
    public void spray(MoteRecipe recipe, Vec3d at, float reach, int count) {
        // Thrown debris slows by a seventh a tick, so it travels about seven times its speed.
        double speed = Math.max(0.05, reach / 7.0);
        for (int i = 0; i < count && motes.hasRoom(); i++) {
            Vector3f v = unit(random);
            double s = speed * (0.45 + random.nextDouble() * 0.85);
            motes.add(Mote.free(recipe, at, new Vec3d(v.x * s, v.y * s * 0.8 + s * 0.2, v.z * s),
                    random, Math.min(2.0f, 0.8f + reach * 0.15f)));
        }
    }

    /**
     * Motes thrown one way: each along a direction within {@code halfAngle} degrees of
     * {@code axis}, hard enough to come to rest about {@code reach} blocks out - a little short
     * or long of it, so the front is ragged. A gust, a jet, a torrent; called every tick from a
     * shape that lasts, it is a stream.
     *
     * @param axis the way the cone faces; need not be of unit length
     */
    public void jet(MoteRecipe recipe, Vec3d at, Vec3d axis, float halfAngle, float reach, int count) {
        Vector3f middle = new Vector3f((float) axis.x, (float) axis.y, (float) axis.z);
        double speed = FxCone.launchSpeed(recipe.motion(), reach);
        for (int i = 0; i < count && motes.hasRoom(); i++) {
            Vector3f v = FxCone.sample(middle, halfAngle, random);
            double s = speed * (0.55 + random.nextDouble() * 0.6);
            motes.add(Mote.free(recipe, at, new Vec3d(v.x * s, v.y * s, v.z * s), random, 1.0f));
        }
    }

    /** Motes born anywhere within {@code spread} of a point, drifting gently. */
    public void scatter(MoteRecipe recipe, Vec3d at, float spread, int count, double drift) {
        for (int i = 0; i < count && motes.hasRoom(); i++) {
            Vec3d point = at.add((random.nextDouble() - 0.5) * 2.0 * spread, (random.nextDouble() - 0.5) * spread,
                    (random.nextDouble() - 0.5) * 2.0 * spread);
            Vector3f v = unit(random);
            motes.add(Mote.free(recipe, point, new Vec3d(v.x * drift, Math.abs(v.y) * drift, v.z * drift),
                    random, 1.0f));
        }
    }

    // ---- ticking --------------------------------------------------------------------------------------------

    public void tick(MinecraftClient client) {
        if (client.world == null) {
            clear();
            return;
        }
        advanceShapes();
        motes.tick();
    }

    /** Ticks every shape once: the cancelled go at once, the rest go when they say they are done. */
    void advanceShapes() {
        Iterator<Live> iterator = shapes.iterator();
        while (iterator.hasNext()) {
            Live live = iterator.next();
            if (live.life.cancelled()) {
                live.ended = true;
                iterator.remove();
                continue;
            }
            live.shape.tick(this, live.life, live.host.effective());
            live.life.advance();
            if (live.shape.done(live.life)) {
                live.ended = true;
                iterator.remove();
            }
        }
    }

    public void clear() {
        for (Live live : shapes) {
            live.ended = true;
        }
        shapes.clear();
        motes.clear();
    }

    // ---- drawing --------------------------------------------------------------------------------------------

    public void render(WorldRenderHook.Context context) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || context.consumers() == null || context.matrices() == null
                || (shapes.isEmpty() && motes.isEmpty())) {
            return;
        }
        float tickDelta = context.tickDelta();
        FxPainter painter = FxPainter.world(context.consumers(), context.matrices().peek().getPositionMatrix(),
                new Quaternionf(context.camera().getRotation()), context.camera().getPos());
        for (FxPass pass : FxPass.ordered()) {
            painter.begin(pass);
            for (Live live : shapes) {
                if (!live.life.cancelled()) {
                    live.shape.draw(painter, world, live.life, tickDelta);
                }
            }
            motes.draw(painter, world, false, tickDelta, -1);
        }
        painter.finish();
    }

    /** A direction picked evenly from all round. */
    public static Vector3f unit(Random random) {
        Vector3f v = new Vector3f();
        do {
            v.set(random.nextFloat() * 2.0f - 1.0f, random.nextFloat() * 2.0f - 1.0f, random.nextFloat() * 2.0f - 1.0f);
        } while (v.lengthSquared() < 1.0e-3f || v.lengthSquared() > 1.0f);
        return v.normalize();
    }
}
