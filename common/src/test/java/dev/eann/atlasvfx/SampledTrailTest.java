package dev.eann.atlasvfx;

import dev.eann.atlasvfx.trail.LaneSweep;
import dev.eann.atlasvfx.trail.SampledSweep;
import dev.eann.atlasvfx.trail.SweepFrame;
import dev.eann.atlasvfx.trail.SweepFrameSource;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recorded trails - the curve through their samples, their breaks, their end - and the thrust lane. */
class SampledTrailTest {

    private static final double EPSILON = 1.0e-6;

    /** Moves like an entity: a position at the start and end of each tick, lerped between. */
    private static final class Mover implements FxAnchor {
        Vec3d previous;
        Vec3d current;
        boolean held = true;

        Mover(Vec3d at) {
            previous = at;
            current = at;
        }

        void moveTo(Vec3d to) {
            previous = current;
            current = to;
        }

        @Override
        public boolean held() {
            return held;
        }

        @Override
        public boolean inView() {
            return false;
        }

        @Override
        @Nullable
        public Vec3d position(float tickDelta) {
            return previous.lerp(current, tickDelta);
        }
    }

    /** A hilt and a tip a block above it, moving together. */
    private static final class Blade {
        final Mover hilt;
        final Mover tip;

        Blade(Vec3d at) {
            hilt = new Mover(at);
            tip = new Mover(at.add(0.0, 1.0, 0.0));
        }

        void moveTo(Vec3d to) {
            hilt.moveTo(to);
            tip.moveTo(to.add(0.0, 1.0, 0.0));
        }
    }

    /** One client tick: the thing moves, then the trail samples, then its age moves on. */
    private static void tick(SampledSweep sweep, FxLife life, Blade blade, Vec3d to) {
        blade.moveTo(to);
        sweep.tick(life);
        life.advance();
    }

    private static Vec3d at(SampledSweep sweep, float s, float t) {
        double[] out = new double[3];
        sweep.point(s, t, out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    @Test
    @DisplayName("A straight run draws a straight trail, its head where the thing is this frame")
    void straightRunStaysStraight() {
        Blade blade = new Blade(Vec3d.ZERO);
        SampledSweep sweep = SampledSweep.blade(blade.hilt, blade.tip, 8);
        FxLife life = new FxLife();
        for (int i = 1; i <= 20; i++) {
            tick(sweep, life, blade, new Vec3d(i, 0.0, 0.0));
        }
        assertTrue(sweep.prepare(0.5f));
        assertEquals(blade.hilt.position(0.5f).x, at(sweep, 1.0f, 0.0f).x, EPSILON, "head is the live position");
        double previous = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 40; i++) {
            Vec3d p = at(sweep, i / 40.0f, 0.0f);
            assertEquals(0.0, p.y, EPSILON);
            assertEquals(0.0, p.z, EPSILON);
            assertTrue(p.x >= previous - EPSILON, "ran backwards at " + i);
            previous = p.x;
            assertEquals(1.0, at(sweep, i / 40.0f, 1.0f).y, EPSILON, "the tip side is the tip");
        }
        // The tail keeps maxAge ticks behind the head, at a block a tick.
        assertEquals(at(sweep, 1.0f, 0.0f).x - 8.0, at(sweep, 0.0f, 0.0f).x, 1.0e-3);
    }

    @Test
    @DisplayName("The curve passes through every sample it was given")
    void curveHitsItsSamples() {
        Blade blade = new Blade(Vec3d.ZERO);
        SampledSweep sweep = SampledSweep.blade(blade.hilt, blade.tip, 6);
        FxLife life = new FxLife();
        // A zigzag, so a curve that only came near its samples would show it.
        Vec3d[] path = new Vec3d[12];
        for (int i = 0; i < path.length; i++) {
            path[i] = new Vec3d(i, (i % 2) * 2.0, 0.0);
            tick(sweep, life, blade, path[i]);
        }
        assertTrue(sweep.prepare(0.0f));
        // With no time into the frame the head is where the thing was as the last tick began - where the
        // game draws it too - one sample behind the newest; s steps back a tick at a time from there.
        for (int k = 0; k <= 6; k++) {
            Vec3d expected = path[path.length - 8 + k];
            Vec3d drawn = at(sweep, k / 6.0f, 0.0f);
            assertEquals(expected.x, drawn.x, 1.0e-6, "x at sample " + k);
            assertEquals(expected.y, drawn.y, 1.0e-6, "y at sample " + k);
        }
    }

    @Test
    @DisplayName("A jump starts the trail again instead of streaking across the world")
    void jumpBreaksTheTrail() {
        Blade blade = new Blade(Vec3d.ZERO);
        SampledSweep sweep = SampledSweep.blade(blade.hilt, blade.tip, 10);
        FxLife life = new FxLife();
        for (int i = 1; i <= 10; i++) {
            tick(sweep, life, blade, new Vec3d(i * 0.5, 0.0, 0.0));
        }
        tick(sweep, life, blade, new Vec3d(500.0, 0.0, 0.0));
        tick(sweep, life, blade, new Vec3d(500.5, 0.0, 0.0));
        tick(sweep, life, blade, new Vec3d(501.0, 0.0, 0.0));
        assertTrue(sweep.prepare(0.5f));
        for (int i = 0; i <= 20; i++) {
            assertTrue(at(sweep, i / 20.0f, 0.0f).x > 499.0, "streaked back toward the old path at " + i);
        }
    }

    @Test
    @DisplayName("Let go of, it stops recording and is finished once its tail catches up")
    void releaseRunsOff() {
        Blade blade = new Blade(Vec3d.ZERO);
        SampledSweep sweep = SampledSweep.blade(blade.hilt, blade.tip, 5);
        FxLife life = new FxLife();
        for (int i = 1; i <= 10; i++) {
            tick(sweep, life, blade, new Vec3d(i, 0.0, 0.0));
        }
        blade.hilt.held = false;
        tick(sweep, life, blade, new Vec3d(11.0, 0.0, 0.0));
        assertTrue(sweep.stopped());
        assertFalse(sweep.finished(life));
        Vec3d last = new Vec3d(10.0, 0.0, 0.0);
        int ticks = 0;
        while (!sweep.finished(life) && ticks < 20) {
            tick(sweep, life, blade, new Vec3d(11.0 + ticks, 0.0, 0.0));
            if (sweep.prepare(0.0f)) {
                assertTrue(at(sweep, 1.0f, 0.0f).x <= last.x + EPSILON, "kept following after letting go");
            }
            ticks++;
        }
        assertTrue(sweep.finished(life));
        assertTrue(ticks <= 6, "took " + ticks + " ticks to run off a 5-tick trail");
    }

    @Test
    @DisplayName("A point trail is as wide as asked across its path, facing the viewer")
    void pointTrailHasItsWidth() {
        Mover mover = new Mover(Vec3d.ZERO);
        SampledSweep sweep = SampledSweep.point(mover, 0.4f, FxCurve.ONE, 8)
                .facing(() -> new Vec3d(0.0, 0.0, 50.0));
        FxLife life = new FxLife();
        for (int i = 1; i <= 12; i++) {
            mover.moveTo(new Vec3d(i, 0.0, 0.0));
            sweep.tick(life);
            life.advance();
        }
        assertTrue(sweep.prepare(0.3f));
        for (int i = 0; i <= 10; i++) {
            Vec3d one = at(sweep, i / 10.0f, 0.0f);
            Vec3d other = at(sweep, i / 10.0f, 1.0f);
            assertEquals(0.4, one.distanceTo(other), 1.0e-4, "width at " + i);
            // Moving along x and seen from +z, it is wide up and down.
            assertEquals(0.0, one.z - other.z, 1.0e-6);
        }
    }

    @Test
    @DisplayName("A thrust lane stays inside its width and reach, extends over time and closes to a point")
    void laneStaysInside() {
        SweepFrame frame = SweepFrame.of(new Vec3d(5.0, 70.0, 5.0), 30.0f, -10.0f);
        LaneSweep lane = new LaneSweep(SweepFrameSource.fixed(frame), 6.0f, 1.0f, 20.0f, 10, 7, 0.35f, 1.25f, 9L);
        FxLife life = new FxLife();
        for (int i = 0; i < 4; i++) {
            lane.tick(life);
            life.advance();
        }
        assertTrue(lane.prepare(0.0f));
        double extended = 6.0 * 3 / 10.0;
        double[] out = new double[3];
        for (int i = 0; i <= 30; i++) {
            for (int j = 0; j <= 10; j++) {
                lane.point(i / 30.0f, j / 10.0f, out);
                Vec3d d = new Vec3d(out[0], out[1], out[2]).subtract(frame.origin());
                double along = d.dotProduct(frame.forward());
                double across = Math.abs(d.dotProduct(frame.right()));
                assertTrue(along >= -EPSILON && along <= extended + EPSILON, "past the extended reach: " + along);
                assertTrue(across <= 1.0 + EPSILON, "wider than the lane: " + across);
                assertTrue(across <= along * Math.tan(Math.toRadians(20.0)) + EPSILON, "outside the cone");
            }
        }
        lane.point(1.0f, 0.0f, out);
        double[] other = new double[3];
        lane.point(1.0f, 1.0f, other);
        assertEquals(0.0, new Vec3d(out[0], out[1], out[2]).distanceTo(new Vec3d(other[0], other[1], other[2])),
                1.0e-6, "the point is a point");
    }
}
