package dev.eann.atlasvfx.telegraph;

import dev.eann.atlasvfx.FxBlend;
import dev.eann.atlasvfx.FxLife;
import dev.eann.atlasvfx.FxLight;
import dev.eann.atlasvfx.FxPainter;
import dev.eann.atlasvfx.FxPass;
import dev.eann.atlasvfx.FxShape;
import dev.eann.atlasvfx.FxShapes;
import dev.eann.atlasvfx.FxWorld;
import dev.eann.atlasvfx.SurfaceGrid;
import dev.eann.atlasvfx.UvWindow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.LongSupplier;

/**
 * A warning on the ground of where an attack will land and when: a faint footprint from the start,
 * filling toward the moment it lands and beating faster as it nears, then a flash as it lands and
 * a short fade.
 *
 * <p>Its timing is the world's clock, not its own age, so it lands when the attack does however
 * late it arrived. Released through its handle, it lands now - an attack that went off early.</p>
 *
 * <p>It can follow something until it locks: a boss swinging round to face its target before
 * the cleave commits. Once locked - at the lock tick, or as it lands, whichever is first - it
 * stays where it is. A server deciding the hit should test the area as it stood at the same
 * tick.</p>
 *
 * <p>Drawn as light lying just above the ground, tested against depth so walls hide it, and never
 * hiding anything itself.</p>
 */
public final class Telegraph implements FxShape {

    /** How far above the ground the fill lies, and the outline above that, so neither flickers. */
    private static final double FILL_LIFT = 0.02;
    private static final double EDGE_LIFT = 0.03;
    /** How soft the filling front is, as a share of the area. */
    private static final float FRONT_SOFTNESS = 0.05f;
    private static final long NO_CLOCK = Long.MIN_VALUE;
    private static final Vector3f UP = new Vector3f(0.0f, 1.0f, 0.0f);

    private final TelegraphArea template;
    private final TelegraphTiming timing;
    private final TelegraphStyle style;
    private final @Nullable TelegraphAnchor anchor;
    private final long lockTick;
    private final LongSupplier clock;
    private final SurfaceGrid grid = new SurfaceGrid();
    private final double[] point = new double[3];

    private TelegraphArea area;
    private boolean locked;
    private long releasedAt = NO_CLOCK;

    private Telegraph(TelegraphArea area, TelegraphTiming timing, TelegraphStyle style,
                      @Nullable TelegraphAnchor anchor, long lockTick, LongSupplier clock) {
        this.template = area;
        this.area = area;
        this.timing = timing;
        this.style = style;
        this.anchor = anchor;
        this.lockTick = lockTick;
        this.clock = clock;
        this.locked = anchor == null;
    }

    /** A telegraph that stays where it is. */
    public static Telegraph fixed(TelegraphArea area, TelegraphTiming timing, TelegraphStyle style) {
        return new Telegraph(area, timing, style, null, Long.MAX_VALUE, Telegraph::worldTime);
    }

    /**
     * A telegraph that follows {@code anchor} - moving with it and turning to its facing - until
     * the world reaches {@code lockTick}, and stays where it was from then on.
     */
    public static Telegraph following(TelegraphArea area, TelegraphTiming timing, TelegraphStyle style,
                                      TelegraphAnchor anchor, long lockTick) {
        return new Telegraph(area, timing, style, anchor, lockTick, Telegraph::worldTime);
    }

    /** The same, on a clock of the caller's: for testing, or effects that keep their own time. */
    public static Telegraph withClock(TelegraphArea area, TelegraphTiming timing, TelegraphStyle style,
                               @Nullable TelegraphAnchor anchor, long lockTick, LongSupplier clock) {
        return new Telegraph(area, timing, style, anchor, lockTick, clock);
    }

    /** The area as it stands now: where it has followed its anchor to, or where it locked. */
    public TelegraphArea area() {
        return area;
    }

    /** Whether it has stopped following. */
    public boolean locked() {
        return locked;
    }

    /** The tick it lands: its own, or the moment it was released if that came first. */
    public long trigger() {
        return releasedAt == NO_CLOCK ? timing.triggerTick() : Math.min(timing.triggerTick(), releasedAt);
    }

    @Override
    public void tick(FxWorld world, FxLife life, float density) {
        long now = clock.getAsLong();
        if (now == NO_CLOCK) {
            return;
        }
        if (life.released() && releasedAt == NO_CLOCK) {
            releasedAt = now;
        }
        if (!locked) {
            follow(1.0f);
            if (now >= Math.min(lockTick, trigger())) {
                locked = true;
            }
        }
    }

    @Override
    public boolean done(FxLife life) {
        long now = clock.getAsLong();
        return now == NO_CLOCK || now >= trigger() + timing.fadeTicks();
    }

    @Override
    public void draw(FxPainter painter, @Nullable ClientWorld world, FxLife life, float tickDelta) {
        long tick = clock.getAsLong();
        if (tick == NO_CLOCK || !painter.drawing(FxPass.GLOW)) {
            return;
        }
        if (!locked) {
            follow(tickDelta);
        }
        double now = tick + tickDelta;
        long trigger = trigger();
        boolean landed = now >= trigger;
        float progress = landed ? 1.0f : timing.progress(now);
        float after = timing.afterTrigger(now, trigger);
        float beat = 1.0f;
        if (!landed) {
            // Faster as it nears: the warning's own heartbeat.
            float rate = 0.25f + 0.9f * style.urgency().at(progress);
            beat = 0.75f + 0.25f * (float) Math.sin((now - timing.startTick()) * rate);
        }
        float flash = (1.0f - after) * (1.0f - after);

        fill(painter, progress, landed, beat, flash);
        float edgeAlpha = style.edgeAlpha() * (landed ? 1.0f - after : beat);
        int edgeColour = landed ? style.hot() : style.edge();
        outline(painter, edgeColour, edgeAlpha, style.edgeWidth() * (landed ? 1.0f + after : 1.0f));
        if (!landed && style.mode() != TelegraphStyle.FillMode.PULSE) {
            front(painter, progress, style.edgeAlpha() * beat);
        }
    }

    /** Moves the area to its anchor, unless the anchor cannot be found, when it stays put. */
    private void follow(float tickDelta) {
        Vec3d at = anchor.position(tickDelta);
        if (at != null) {
            area = template.at(at, anchor.yaw(tickDelta));
        }
    }

    // ---- the fill --------------------------------------------------------------------------------------

    private void fill(FxPainter painter, float progress, boolean landed, float beat, float flash) {
        int rows;
        int columns;
        switch (area) {
            case TelegraphArea.Circle c -> {
                rows = 6;
                columns = 48;
            }
            case TelegraphArea.Ring r -> {
                rows = 3;
                columns = 48;
            }
            case TelegraphArea.Cone c -> {
                rows = 6;
                columns = Math.max(2, (int) Math.ceil(c.halfAngle() / 6.0f));
            }
            case TelegraphArea.Line l -> {
                rows = 10;
                columns = 2;
            }
        }
        grid.reset(rows, columns);
        int colour = landed ? style.hot() : style.fill();
        for (int r = 0; r <= rows; r++) {
            float u = r / (float) rows;
            for (int c = 0; c <= columns; c++) {
                float v = c / (float) columns;
                float alpha = landed
                        ? (style.fillAlpha() + 0.3f) * flash
                        : (style.baseAlpha() + style.fillAlpha() * covered(u, v, progress)) * beat;
                area.point(u, v, point);
                grid.set(r, c, point[0], point[1] + FILL_LIFT, point[2], colour, alpha);
            }
        }
        painter.surface(grid, FxBlend.GLOW, null, UvWindow.FULL, 0, FxLight.FULL_BRIGHT, UP, 0.0f);
    }

    /** How much of the fill shows at {@code u} out and {@code v} across, {@code progress} through. */
    float covered(float u, float v, float progress) {
        return switch (style.mode()) {
            case GROW -> front(u, progress);
            case SWEEP -> front(v, progress);
            case PULSE -> progress;
        };
    }

    /** Full behind the front, falling off just past it. */
    private static float front(float position, float progress) {
        if (position <= progress) {
            return 1.0f;
        }
        return Math.max(0.0f, 1.0f - (position - progress) / FRONT_SOFTNESS);
    }

    // ---- the outline and the front ---------------------------------------------------------------------

    private void outline(FxPainter painter, int colour, float alpha, float width) {
        switch (area) {
            case TelegraphArea.Circle c -> painter.ring(lifted(c.origin()), FxShapes.FLAT, c.radius(), width, colour,
                    alpha);
            case TelegraphArea.Ring r -> {
                painter.ring(lifted(r.origin()), FxShapes.FLAT, r.inner(), width, colour, alpha);
                painter.ring(lifted(r.origin()), FxShapes.FLAT, r.outer(), width, colour, alpha);
            }
            case TelegraphArea.Cone c -> {
                if (c.halfAngle() >= 180.0f) {
                    painter.ring(lifted(c.origin()), FxShapes.FLAT, c.range(), width, colour, alpha);
                    return;
                }
                painter.arc(lifted(c.origin()), FxShapes.FLAT, c.range(), width, coneStart(c),
                        (float) Math.toRadians(c.halfAngle() * 2.0f), colour, alpha);
                painter.band(lifted(c.origin()), at(0.999f, 0.0f), UP, width, colour, alpha);
                painter.band(lifted(c.origin()), at(0.999f, 1.0f), UP, width, colour, alpha);
            }
            case TelegraphArea.Line l -> {
                Vec3d a = at(0.0f, 0.0f);
                Vec3d b = at(1.0f, 0.0f);
                Vec3d d = at(1.0f, 1.0f);
                Vec3d e = at(0.0f, 1.0f);
                painter.band(a, b, UP, width, colour, alpha);
                painter.band(b, d, UP, width, colour, alpha);
                painter.band(d, e, UP, width, colour, alpha);
                painter.band(e, a, UP, width, colour, alpha);
            }
        }
    }

    /** The filling front: a bright line where the fill has got to. */
    private void front(FxPainter painter, float progress, float alpha) {
        if (progress <= 0.001f) {
            return;
        }
        int colour = style.hot();
        float width = style.edgeWidth() * 1.4f;
        boolean grow = style.mode() == TelegraphStyle.FillMode.GROW;
        switch (area) {
            case TelegraphArea.Circle c -> {
                if (grow) {
                    painter.ring(lifted(c.origin()), FxShapes.FLAT, c.radius() * progress, width, colour, alpha);
                } else {
                    painter.band(lifted(c.origin()), at(1.0f, progress), UP, width, colour, alpha);
                }
            }
            case TelegraphArea.Ring r -> {
                if (grow) {
                    painter.ring(lifted(r.origin()), FxShapes.FLAT, r.inner() + (r.outer() - r.inner()) * progress,
                            width, colour, alpha);
                } else {
                    painter.band(at(0.0f, progress), at(1.0f, progress), UP, width, colour, alpha);
                }
            }
            case TelegraphArea.Cone c -> {
                if (grow) {
                    painter.arc(lifted(c.origin()), FxShapes.FLAT, c.range() * progress, width, coneStart(c),
                            (float) Math.toRadians(c.halfAngle() * 2.0f), colour, alpha);
                } else {
                    painter.band(lifted(c.origin()), at(1.0f, progress), UP, width, colour, alpha);
                }
            }
            case TelegraphArea.Line l -> {
                if (grow) {
                    painter.band(at(progress, 0.0f), at(progress, 1.0f), UP, width, colour, alpha);
                } else {
                    painter.band(at(0.0f, progress), at(1.0f, progress), UP, width, colour, alpha);
                }
            }
        }
    }

    /**
     * Where a cone's arc starts, in the flat plane's own angles: 0 along east, a quarter turn
     * along south - a quarter turn on from the game's yaw, which starts at south.
     */
    private static float coneStart(TelegraphArea.Cone cone) {
        return (float) (Math.toRadians(cone.yaw() - cone.halfAngle()) + Math.PI / 2.0);
    }

    /** The area's point at {@code u} and {@code v}, lifted to where outlines are drawn. */
    private Vec3d at(float u, float v) {
        area.point(u, v, point);
        return new Vec3d(point[0], point[1] + EDGE_LIFT, point[2]);
    }

    private static Vec3d lifted(Vec3d origin) {
        return origin.add(0.0, EDGE_LIFT, 0.0);
    }

    private static long worldTime() {
        ClientWorld world = MinecraftClient.getInstance().world;
        return world == null ? NO_CLOCK : world.getTime();
    }
}
