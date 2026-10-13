package dev.eann.atlasvfx.aura;

import dev.eann.atlasvfx.SurfaceGrid;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuraTest {

    private static final int GLOW = 0x7FD4FF;
    private static final int HOT = 0xEAF8FF;

    @Test
    @DisplayName("edge-on is 0 square-on and 1 side-on, whichever side is seen")
    void edgeOn() {
        assertEquals(0.0f, Shells.edgeOn(1, 0, 0, 5, 0, 0), 1.0e-6f);
        assertEquals(0.0f, Shells.edgeOn(1, 0, 0, -5, 0, 0), 1.0e-6f);
        assertEquals(1.0f, Shells.edgeOn(1, 0, 0, 0, 3, 0), 1.0e-6f);
        assertTrue(Shells.edgeOn(1, 0, 0, 1, 1, 0) > 0.2f);
    }

    /** The point of a filled grid nearest {@code to}. */
    private static int[] nearest(SurfaceGrid grid, Vec3d to) {
        int[] best = {0, 0};
        double bestDistance = Double.MAX_VALUE;
        for (int r = 0; r <= grid.rows(); r++) {
            for (int c = 0; c <= grid.columns(); c++) {
                double dx = grid.x(r, c) - to.x;
                double dy = grid.y(r, c) - to.y;
                double dz = grid.z(r, c) - to.z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{r, c};
                }
            }
        }
        return best;
    }

    @Test
    @DisplayName("a sphere is clear where it faces the viewer and bright along its outline")
    void sphereRim() {
        SurfaceGrid grid = new SurfaceGrid();
        Vec3d base = new Vec3d(0, 64, 0);
        float radius = 1.0f;
        // Far off along +x, level with the middle.
        Vec3d viewer = new Vec3d(40, 65, 0);
        Shells.fill(grid, ShellShape.SPHERE, base, radius, 0, viewer, GLOW, HOT, 0.6f, 0.02f, 0, 0, 1.0f);
        int[] facing = nearest(grid, new Vec3d(1, 65, 0));
        int[] outline = nearest(grid, new Vec3d(0, 65, 1));
        float facingAlpha = grid.alpha(facing[0], facing[1]);
        float outlineAlpha = grid.alpha(outline[0], outline[1]);
        assertTrue(facingAlpha < 0.06f, "facing " + facingAlpha);
        assertTrue(outlineAlpha > 0.45f, "outline " + outlineAlpha);
        // Every point lies on the sphere.
        for (int r = 0; r <= grid.rows(); r++) {
            for (int c = 0; c <= grid.columns(); c++) {
                double dx = grid.x(r, c);
                double dy = grid.y(r, c) - 65;
                double dz = grid.z(r, c);
                assertEquals(radius, Math.sqrt(dx * dx + dy * dy + dz * dz), 1.0e-4);
            }
        }
    }

    @Test
    @DisplayName("a dome stands on the ground; a column fades out at its top")
    void domeAndColumn() {
        SurfaceGrid grid = new SurfaceGrid();
        Vec3d base = new Vec3d(10, 70, -4);
        Vec3d viewer = new Vec3d(10, 71, 30);
        Shells.fill(grid, ShellShape.DOME, base, 2.0f, 0, viewer, GLOW, HOT, 0.6f, 0.05f, 0, 0, 1.0f);
        for (int c = 0; c <= grid.columns(); c++) {
            assertEquals(70.0, grid.y(0, c), 1.0e-6);
            assertTrue(grid.y(grid.rows(), c) > 71.99);
        }
        Shells.fill(grid, ShellShape.CYLINDER, base, 0.5f, 2.0f, viewer, GLOW, HOT, 0.6f, 0.3f, 0, 0, 1.0f);
        for (int c = 0; c <= grid.columns(); c++) {
            assertEquals(0.0f, grid.alpha(grid.rows(), c), 1.0e-6f);
            assertEquals(0.0f, grid.alpha(0, c), 1.0e-6f);
            assertTrue(grid.alpha(2, c) > 0.0f);
        }
    }

    @Test
    @DisplayName("strength scales a whole shell, and nothing goes negative under a shimmer")
    void strengthAndShimmer() {
        SurfaceGrid grid = new SurfaceGrid();
        Vec3d base = new Vec3d(0, 0, 0);
        Vec3d viewer = new Vec3d(0, 1, 20);
        Shells.fill(grid, ShellShape.DOME, base, 2.0f, 0, viewer, GLOW, HOT, 0.6f, 0.1f, 1.5f, 17.0f, 0.0f);
        for (int r = 0; r <= grid.rows(); r++) {
            for (int c = 0; c <= grid.columns(); c++) {
                assertEquals(0.0f, grid.alpha(r, c), 1.0e-6f);
            }
        }
        Shells.fill(grid, ShellShape.DOME, base, 2.0f, 0, viewer, GLOW, HOT, 0.6f, 0.1f, 1.5f, 17.0f, 1.0f);
        for (int r = 0; r <= grid.rows(); r++) {
            for (int c = 0; c <= grid.columns(); c++) {
                assertTrue(grid.alpha(r, c) >= 0.0f);
            }
        }
    }

    @Test
    @DisplayName("an aura comes up, fades after release, and fades with a dying body")
    void strengthOverLife() {
        assertEquals(0.0f, Aura.strength(0, false, 0, 10, 12, 0), 1.0e-6f);
        assertEquals(0.5f, Aura.strength(5, false, 0, 10, 12, 0), 1.0e-6f);
        assertEquals(1.0f, Aura.strength(400, false, 0, 10, 12, 0), 1.0e-6f);
        assertEquals(0.5f, Aura.strength(400, true, 6, 10, 12, 0), 1.0e-6f);
        assertEquals(0.0f, Aura.strength(400, true, 12, 10, 12, 0), 1.0e-6f);
        assertEquals(0.0f, Aura.strength(400, true, 30, 10, 12, 0), 1.0e-6f);
        assertEquals(0.5f, Aura.strength(400, false, 0, 10, 12, 5), 1.0e-6f);
        assertEquals(0.0f, Aura.strength(400, false, 0, 10, 12, 15), 1.0e-6f);
    }

    @Test
    @DisplayName("only what surrounds the body is left out in first person")
    void firstPersonLayers() {
        assertTrue(!new AuraLayer.GroundSigil(1, 0, 0).fillsView());
        assertTrue(!new AuraLayer.Rise(null, 1, 1, 0.02f).fillsView());
        assertTrue(new AuraLayer.Shell(ShellShape.DOME, 1, 0.5f, 0, 0).fillsView());
        assertTrue(new AuraLayer.Orbit(3, 0.4f, 0.6f, 0.1f, 0.1f).fillsView());
        assertTrue(new AuraLayer.Helix(2, 0.3f, 1.5f, 0.07f, 0.1f).fillsView());
    }

    @Test
    @DisplayName("body sizing is 1 on a player and follows the larger share of width or height")
    void bodySizing() {
        assertEquals(1.0f, Sizing.body(0.6f, 1.8f), 1.0e-6f);
        // A baby zombie: half a player each way.
        assertEquals(0.5f, Sizing.body(0.3f, 0.9f), 1.0e-6f);
        // A ravager is wide rather than tall; an enderman tall rather than wide.
        assertEquals(1.95f / 0.6f, Sizing.body(1.95f, 2.2f), 1.0e-5f);
        assertEquals(2.9f / 1.8f, Sizing.body(0.6f, 2.9f), 1.0e-5f);
        // Never vanishes, however small the body.
        assertTrue(Sizing.body(0.0f, 0.0f) > 0.0f);
        assertEquals(1.0f, Sizing.FIXED.scale(null), 0.0f);
        assertEquals(3.0f, Sizing.of(1.5f).times(2.0f).scale(null), 1.0e-6f);
    }
}
