package dev.eann.atlasvfx.telegraph;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The areas' geometry - the part a server tests damage with - and their timing. */
class TelegraphAreaTest {

    private static final Vec3d O = new Vec3d(100.0, 64.0, -20.0);

    private static Vec3d offset(double dx, double dz) {
        return O.add(dx, 0.0, dz);
    }

    @Test
    @DisplayName("A circle holds what stands within its radius and nothing past it")
    void circleContains() {
        TelegraphArea area = new TelegraphArea.Circle(O, 3.0f);
        assertTrue(area.contains(offset(0.0, 0.0), 2.0));
        assertTrue(area.contains(offset(2.9, 0.0), 2.0));
        assertFalse(area.contains(offset(2.2, 2.2), 2.0));
        assertFalse(area.contains(offset(0.0, 0.0).add(0.0, 2.5, 0.0), 2.0), "above its height");
        assertFalse(area.contains(offset(0.0, 0.0).add(0.0, -0.6, 0.0), 2.0), "too far below it");
    }

    @Test
    @DisplayName("A ring is safe in the middle and dangerous between its edges")
    void ringContains() {
        TelegraphArea area = new TelegraphArea.Ring(O, 2.0f, 4.0f);
        assertFalse(area.containsFootprint(O.x, O.z));
        assertFalse(area.containsFootprint(O.x + 1.9, O.z));
        assertTrue(area.containsFootprint(O.x + 3.0, O.z));
        assertFalse(area.containsFootprint(O.x + 4.1, O.z));
    }

    @Test
    @DisplayName("A cone faces its yaw: south at 0, west at 90, and holds only its spread")
    void coneFacesItsYaw() {
        TelegraphArea south = new TelegraphArea.Cone(O, 0.0f, 5.0f, 30.0f);
        assertTrue(south.containsFootprint(O.x, O.z + 4.0));
        assertFalse(south.containsFootprint(O.x, O.z - 4.0), "behind it");
        assertTrue(south.containsFootprint(O.x + 2.0, O.z + 4.0), "26.6 degrees off, inside 30");
        assertFalse(south.containsFootprint(O.x + 3.0, O.z + 4.0), "36.9 degrees off, outside 30");
        assertFalse(south.containsFootprint(O.x, O.z + 5.1), "past its range");

        TelegraphArea west = south.at(O, 90.0f);
        assertTrue(west.containsFootprint(O.x - 4.0, O.z));
        assertFalse(west.containsFootprint(O.x, O.z + 4.0));
    }

    @Test
    @DisplayName("A line runs forward from its start, as wide as it says")
    void lineContains() {
        TelegraphArea area = new TelegraphArea.Line(O, 0.0f, 8.0f, 1.0f);
        assertTrue(area.containsFootprint(O.x, O.z + 7.9));
        assertTrue(area.containsFootprint(O.x + 0.9, O.z + 4.0));
        assertFalse(area.containsFootprint(O.x + 1.1, O.z + 4.0));
        assertFalse(area.containsFootprint(O.x, O.z - 0.1), "behind its start");
        assertFalse(area.containsFootprint(O.x, O.z + 8.1), "past its end");
        TelegraphArea east = area.at(O, -90.0f);
        assertTrue(east.containsFootprint(O.x + 7.0, O.z));
    }

    @Test
    @DisplayName("Every point a drawing walks lies inside the area it is drawing")
    void drawingStaysInside() {
        List<TelegraphArea> areas = List.of(
                new TelegraphArea.Circle(O, 3.0f),
                new TelegraphArea.Ring(O, 1.5f, 3.5f),
                new TelegraphArea.Cone(O, 37.0f, 6.0f, 45.0f),
                new TelegraphArea.Cone(O, -120.0f, 4.0f, 180.0f),
                new TelegraphArea.Line(O, 200.0f, 10.0f, 1.5f));
        double[] p = new double[3];
        for (TelegraphArea area : areas) {
            for (int i = 0; i <= 20; i++) {
                for (int j = 0; j <= 20; j++) {
                    // Pulled in a hair from every edge, where floating point decides either way.
                    area.point(0.0005f + i / 20.0f * 0.999f, 0.0005f + j / 20.0f * 0.999f, p);
                    assertEquals(O.y, p[1], 1.0e-9);
                    assertTrue(area.containsFootprint(p[0], p[2]),
                            area + " walked outside itself at u=" + i / 20.0f + " v=" + j / 20.0f);
                }
            }
        }
    }

    @Test
    @DisplayName("An area's bounds hold all of it")
    void boundsHoldTheArea() {
        TelegraphArea area = new TelegraphArea.Line(O, 45.0f, 6.0f, 2.0f);
        Box box = area.bounds(3.0);
        double[] p = new double[3];
        for (int i = 0; i <= 10; i++) {
            for (int j = 0; j <= 10; j++) {
                area.point(i / 10.0f, j / 10.0f, p);
                assertTrue(box.contains(p[0], p[1], p[2]));
            }
        }
    }

    @Test
    @DisplayName("Timing runs from start to trigger, and a late arrival starts part way through")
    void timingFollowsTheClock() {
        TelegraphTiming timing = TelegraphTiming.starting(1000L, 40, 6);
        assertEquals(1040L, timing.triggerTick());
        assertEquals(0.0f, timing.progress(990.0), 1.0e-6f);
        assertEquals(0.5f, timing.progress(1020.0), 1.0e-6f);
        assertEquals(1.0f, timing.progress(1050.0), 1.0e-6f);
        assertEquals(0.5f, timing.afterTrigger(1043.0, 1040L), 1.0e-6f);
        assertEquals(0.0f, timing.afterTrigger(1030.0, 1040L), 1.0e-6f);
    }

    @Test
    @DisplayName("A growing fill covers out from the origin, a sweeping one across, a pulse all at once")
    void fillModesCover() {
        TelegraphArea area = new TelegraphArea.Circle(O, 2.0f);
        TelegraphTiming timing = TelegraphTiming.starting(0L, 20, 4);
        Telegraph grow = Telegraph.withClock(area, timing, TelegraphStyles.DANGER, null, Long.MAX_VALUE, () -> 0L);
        assertEquals(1.0f, grow.covered(0.3f, 0.9f, 0.5f), 1.0e-6f);
        assertEquals(0.0f, grow.covered(0.9f, 0.1f, 0.5f), 1.0e-6f);
        float previous = 1.0f;
        for (float u = 0.0f; u <= 1.0f; u += 0.01f) {
            float now = grow.covered(u, 0.0f, 0.4f);
            assertTrue(now <= previous + 1.0e-6f, "the fill came back further out at " + u);
            previous = now;
        }
        Telegraph sweep = Telegraph.withClock(area, timing,
                TelegraphStyles.DANGER.withMode(TelegraphStyle.FillMode.SWEEP), null, Long.MAX_VALUE, () -> 0L);
        assertEquals(1.0f, sweep.covered(0.9f, 0.3f, 0.5f), 1.0e-6f);
        assertEquals(0.0f, sweep.covered(0.1f, 0.9f, 0.5f), 1.0e-6f);
        Telegraph pulse = Telegraph.withClock(area, timing,
                TelegraphStyles.DANGER.withMode(TelegraphStyle.FillMode.PULSE), null, Long.MAX_VALUE, () -> 0L);
        assertEquals(0.5f, pulse.covered(0.9f, 0.9f, 0.5f), 1.0e-6f);
    }
}
