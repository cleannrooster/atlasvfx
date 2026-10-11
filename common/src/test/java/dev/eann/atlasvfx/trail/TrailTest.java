package dev.eann.atlasvfx.trail;

import dev.eann.atlasvfx.FxColor;
import dev.eann.atlasvfx.SurfaceGrid;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arcs and the trails laid on them: geometry, and the promise never to draw past the arc. */
class TrailTest {

    private static final double EPSILON = 1.0e-4;
    private static final Vec3d ORIGIN = new Vec3d(10.0, 64.0, -3.0);
    /** Facing south, level: forward +Z, up +Y. */
    private static final SweepFrame FRAME = SweepFrame.of(ORIGIN, 0.0f, 0.0f);

    private static ArcSweep arc(float reach, float halfAngle, boolean reversed, ArcSweep.Plane plane) {
        ArcSweep sweep = new ArcSweep(SweepFrameSource.fixed(FRAME), reach, halfAngle, reversed, plane);
        assertTrue(sweep.prepare(0.0f));
        return sweep;
    }

    private static Vec3d point(ArcSweep sweep, float s, float t) {
        double[] out = new double[3];
        sweep.point(s, t, out);
        return new Vec3d(out[0], out[1], out[2]);
    }

    /** Degrees off forward of a point, signed by the plane's axis. */
    private static double angleOf(Vec3d p, ArcSweep.Plane plane) {
        Vec3d d = p.subtract(ORIGIN);
        Vec3d axis = plane == ArcSweep.Plane.VERTICAL ? FRAME.up() : FRAME.right();
        return Math.toDegrees(Math.atan2(d.dotProduct(axis), d.dotProduct(FRAME.forward())));
    }

    @Test
    @DisplayName("An arc runs from one side of forward to the other, its blade from the origin to its reach")
    void arcSpansItsAngleAndReach() {
        ArcSweep sweep = arc(3.0f, 60.0f, false, ArcSweep.Plane.HORIZONTAL);
        assertEquals(0.0, point(sweep, 0.3f, 0.0f).distanceTo(ORIGIN), EPSILON);
        assertEquals(3.0, point(sweep, 0.3f, 1.0f).distanceTo(ORIGIN), EPSILON);
        assertEquals(3.0, point(sweep, 0.3f, 2.0f).distanceTo(ORIGIN), EPSILON, "t is clamped to the reach");
        assertEquals(-60.0, angleOf(point(sweep, 0.0f, 1.0f), ArcSweep.Plane.HORIZONTAL), 1.0e-3);
        assertEquals(0.0, angleOf(point(sweep, 0.5f, 1.0f), ArcSweep.Plane.HORIZONTAL), 1.0e-3);
        assertEquals(60.0, angleOf(point(sweep, 1.0f, 1.0f), ArcSweep.Plane.HORIZONTAL), 1.0e-3);

        ArcSweep back = arc(3.0f, 60.0f, true, ArcSweep.Plane.HORIZONTAL);
        assertEquals(60.0, angleOf(point(back, 0.0f, 1.0f), ArcSweep.Plane.HORIZONTAL), 1.0e-3);
    }

    @Test
    @DisplayName("A horizontal arc stays level and a vertical one stays upright, each normal square to it")
    void planesHoldTheirOrientation() {
        ArcSweep flat = arc(2.0f, 80.0f, false, ArcSweep.Plane.HORIZONTAL);
        ArcSweep upright = arc(2.0f, 80.0f, false, ArcSweep.Plane.VERTICAL);
        for (float s = 0.0f; s <= 1.0f; s += 0.25f) {
            assertEquals(ORIGIN.y, point(flat, s, 1.0f).y, EPSILON);
            assertEquals(ORIGIN.x, point(upright, s, 1.0f).x, EPSILON);
        }
        assertEquals(1.0f, flat.normal().y, 1.0e-5f);
        assertEquals(1.0f, Math.abs(upright.normal().x), 1.0e-5f);
        assertTrue(arc(1.0f, 180.0f, false, ArcSweep.Plane.HORIZONTAL).closed());
    }

    @Test
    @DisplayName("Every point of every slash band lies inside the visible part of the arc and its reach")
    void slashNeverPassesTheArc() {
        float reach = 3.0f;
        float half = 70.0f;
        ArcSweep sweep = arc(reach, half, false, ArcSweep.Plane.HORIZONTAL);
        TrailStyle style = TrailStyles.slash(0x8899AA);
        Trail trail = new Trail(sweep, style, TrailWindow.swing(6, 1.0f, 4), 1234L);
        float tail = 0.2f;
        float head = 0.75f;
        double tailAngle = sweep.angle(tail);
        double headAngle = sweep.angle(head);
        List<TrailLayer> layers = style.layers();
        for (int i = 0; i < layers.size(); i++) {
            trail.build(layers.get(i), i, tail, head, style.inner(), 1.0f, 0.0f);
            SurfaceGrid grid = trail.grid();
            for (int r = 0; r <= grid.rows(); r++) {
                for (int c = 0; c <= grid.columns(); c++) {
                    Vec3d p = new Vec3d(grid.x(r, c), grid.y(r, c), grid.z(r, c));
                    double distance = p.distanceTo(ORIGIN);
                    assertTrue(distance <= reach + EPSILON, "layer " + i + " past the reach: " + distance);
                    assertTrue(distance >= style.inner() * reach - EPSILON,
                            "layer " + i + " inside the inner edge: " + distance);
                    double angle = angleOf(p, ArcSweep.Plane.HORIZONTAL);
                    assertTrue(angle >= tailAngle - 1.0e-3 && angle <= headAngle + 1.0e-3,
                            "layer " + i + " outside the visible arc: " + angle);
                    float alpha = grid.alpha(r, c);
                    assertTrue(alpha >= 0.0f && alpha <= 1.0f, "alpha out of range: " + alpha);
                }
            }
        }
    }

    @Test
    @DisplayName("Only the edge band reaches the full reach, along its whole length")
    void edgeMarksTheReach() {
        ArcSweep sweep = arc(2.0f, 60.0f, false, ArcSweep.Plane.HORIZONTAL);
        TrailStyle style = TrailStyles.slash(0xFFFFFF);
        Trail trail = new Trail(sweep, style, TrailWindow.swing(6, 1.0f, 4), 7L);
        trail.build(style.layers().get(2), 2, 0.0f, 1.0f, style.inner(), 1.0f, 0.0f);
        SurfaceGrid grid = trail.grid();
        int outer = grid.columns();
        for (int r = 0; r <= grid.rows(); r++) {
            Vec3d p = new Vec3d(grid.x(r, outer), grid.y(r, outer), grid.z(r, outer));
            assertEquals(2.0, p.distanceTo(ORIGIN), EPSILON);
        }
    }

    @Test
    @DisplayName("A trail's spikes come from its seed: the same seed draws the same wake")
    void spikesHoldStill() {
        TrailStyle style = TrailStyles.slash(0xC04020);
        double[] first = wakeInner(style, 99L);
        double[] again = wakeInner(style, 99L);
        double[] other = wakeInner(style, 100L);
        boolean differs = false;
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], again[i], 0.0);
            differs |= Math.abs(first[i] - other[i]) > 1.0e-9;
        }
        assertTrue(differs, "a different seed drew the same spikes");
    }

    private static double[] wakeInner(TrailStyle style, long seed) {
        ArcSweep sweep = arc(3.0f, 60.0f, false, ArcSweep.Plane.HORIZONTAL);
        Trail trail = new Trail(sweep, style, TrailWindow.swing(6, 1.0f, 4), seed);
        trail.build(style.layers().get(0), 0, 0.0f, 1.0f, style.inner(), 1.0f, 0.0f);
        SurfaceGrid grid = trail.grid();
        double[] inner = new double[grid.rows() + 1];
        for (int r = 0; r <= grid.rows(); r++) {
            inner[r] = new Vec3d(grid.x(r, 0), grid.y(r, 0), grid.z(r, 0)).distanceTo(ORIGIN);
        }
        return inner;
    }

    @Test
    @DisplayName("A flash lifts every point toward full and snaps the colour to the flash")
    void flashLifts() {
        ArcSweep sweep = arc(2.0f, 60.0f, false, ArcSweep.Plane.HORIZONTAL);
        TrailStyle style = TrailStyles.slash(0x3060C0);
        Trail trail = new Trail(sweep, style, TrailWindow.swing(6, 1.0f, 4), 3L);
        TrailLayer body = style.layers().get(1);
        trail.build(body, 1, 0.0f, 1.0f, style.inner(), 0.5f, 1.0f);
        SurfaceGrid grid = trail.grid();
        for (int r = 0; r <= grid.rows(); r++) {
            for (int c = 0; c <= grid.columns(); c++) {
                assertEquals(0.5f, grid.alpha(r, c), 1.0e-5f);
                assertEquals(style.flashColour(), grid.rgb(r, c));
            }
        }
    }

    @Test
    @DisplayName("A slash palette keeps one hue: a pale edge, a deeper body, a deeper wake")
    void paletteKeepsItsHue() {
        TrailStyles.Palette palette = TrailStyles.Palette.of(0x2050D0);
        float edge = FxColor.toHsl(palette.edge())[2];
        float body = FxColor.toHsl(palette.body())[2];
        float wake = FxColor.toHsl(palette.wake())[2];
        assertTrue(edge > body && body >= wake, "lightness should fall edge > body >= wake");
        float hue = FxColor.toHsl(0x2050D0)[0];
        assertEquals(hue, FxColor.toHsl(palette.body())[0], 0.02f);
        assertEquals(hue, FxColor.toHsl(palette.wake())[0], 0.02f);
    }

    @Test
    @DisplayName("HSL round-trips a colour within a step of each channel")
    void hslRoundTrips() {
        for (int rgb : new int[] {0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x8899AA, 0xC04020, 0x123456}) {
            float[] hsl = FxColor.toHsl(rgb);
            int back = FxColor.fromHsl(hsl[0], hsl[1], hsl[2]);
            for (int shift = 0; shift <= 16; shift += 8) {
                assertTrue(Math.abs((rgb >> shift & 0xFF) - (back >> shift & 0xFF)) <= 1,
                        String.format("%06X came back as %06X", rgb, back));
            }
        }
    }

    @Test
    @DisplayName("A softened reach is the real one up to the knee and never longer past it")
    void softReachUnderstates() {
        assertEquals(2.0f, ArcSweep.softReach(2.0f, 3.0f, 1.5f), 1.0e-6f);
        float previous = 3.0f;
        for (float real = 3.5f; real <= 20.0f; real += 0.5f) {
            float drawn = ArcSweep.softReach(real, 3.0f, 1.5f);
            assertTrue(drawn <= real && drawn >= previous, "at " + real + " drew " + drawn);
            previous = drawn;
        }
    }
}
