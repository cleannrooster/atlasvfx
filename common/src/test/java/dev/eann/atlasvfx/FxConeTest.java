package dev.eann.atlasvfx;

import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Directions in a cone, and throws that land where they were aimed. */
class FxConeTest {

    private static final int SAMPLES = 4000;

    @Test
    @DisplayName("every direction is of unit length and inside the cone, whichever way it faces")
    void insideTheCone() {
        Random random = new Random(7);
        Vector3f[] axes = {new Vector3f(0, 0, 1), new Vector3f(0, 1, 0), new Vector3f(0, -1, 0),
                new Vector3f(1, 2, -3), new Vector3f(-0.2f, 0.05f, 0.9f)};
        for (Vector3f axis : axes) {
            Vector3f unit = new Vector3f(axis).normalize();
            float limit = (float) Math.cos(Math.toRadians(30.0));
            for (int i = 0; i < SAMPLES; i++) {
                Vector3f d = FxCone.sample(axis, 30.0f, random);
                assertEquals(1.0f, d.length(), 1.0e-4f);
                assertTrue(d.dot(unit) >= limit - 1.0e-4f, "outside the cone round " + axis + ": " + d);
            }
        }
    }

    @Test
    @DisplayName("the cone fills to its edge rather than bunching at the middle")
    void fillsToTheEdge() {
        Random random = new Random(11);
        Vector3f axis = new Vector3f(0, 0, 1);
        double outer = Math.cos(Math.toRadians(25.0));
        int nearEdge = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (FxCone.sample(axis, 30.0f, random).z < outer) {
                nearEdge++;
            }
        }
        // The band from 25 to 30 degrees is about 30% of the cap's area.
        assertTrue(nearEdge > SAMPLES * 0.2, "only " + nearEdge + " of " + SAMPLES + " near the edge");
    }

    @Test
    @DisplayName("a closed cone is its axis; a missing axis faces up rather than failing")
    void edgeCases() {
        Random random = new Random(3);
        Vector3f straight = FxCone.sample(new Vector3f(3, 0, 0), 0.0f, random);
        assertEquals(1.0f, straight.x, 1.0e-5f);

        Vector3f up = FxCone.sample(new Vector3f(), 0.0f, random);
        assertEquals(1.0f, up.y, 1.0e-5f);
    }

    @Test
    @DisplayName("thrown at the launch speed, a mote comes to rest about the reach away, for either motion")
    void launchSpeedReachesTheReach() {
        for (Motion motion : new Motion[]{Motion.BURST, Motion.EMIT}) {
            double speed = FxCone.launchSpeed(motion, 6.0f);
            double travelled = 0.0;
            for (int tick = 0; tick < 2000; tick++) {
                travelled += speed;
                speed *= Mote.drag(motion);
            }
            assertEquals(6.0, travelled, 0.01, motion.name());
        }
    }
}
