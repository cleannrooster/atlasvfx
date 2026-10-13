package dev.eann.atlasvfx.aura;

import dev.eann.atlasvfx.FxColor;
import dev.eann.atlasvfx.SurfaceGrid;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Lays a shell of light out on a grid: points over a ball, a dome or a column, each as bright as
 * the shell is edge-on to the viewer there.
 *
 * <p>That is what makes a shell read as a shell rather than a fog: where the viewer looks through
 * it square-on it is nearly clear, and where it curves away - its outline - the light piles up.
 * Drawn as added light with no culling, the near and far sides both show, and the far side's
 * outline lines up with the near one's.</p>
 */
public final class Shells {

    /** Rows round a shell, bottom to top. */
    public static final int ROWS = 12;
    /** Columns round a shell. */
    public static final int COLUMNS = 28;

    private Shells() {
    }

    /**
     * Fills {@code grid} with a shell.
     *
     * @param base     the bottom middle of the body it stands round
     * @param radius   how far out, in blocks
     * @param height   for a column, how tall
     * @param viewer   where it is seen from
     * @param rim      how strongly the outline shows, 0 to 1
     * @param face     how strongly the part seen square-on shows
     * @param shimmer  how strongly bands of light run up it, 0 for none
     * @param time     ticks, for the shimmer and the turn
     * @param strength multiplies everything: fading in and out
     */
    public static void fill(SurfaceGrid grid, ShellShape shape, Vec3d base, float radius, float height,
                            Vec3d viewer, int glow, int hot, float rim, float face, float shimmer, float time,
                            float strength) {
        grid.reset(ROWS, COLUMNS);
        float turn = time * 0.01f;
        for (int r = 0; r <= ROWS; r++) {
            float along = r / (float) ROWS;
            for (int c = 0; c <= COLUMNS; c++) {
                float angle = c / (float) COLUMNS * MathHelper.TAU + turn;
                float cos = MathHelper.cos(angle);
                float sin = MathHelper.sin(angle);
                double nx;
                double ny;
                double nz;
                double px;
                double py;
                double pz;
                float envelope = 1.0f;
                switch (shape) {
                    case SPHERE -> {
                        float lat = (along - 0.5f) * MathHelper.PI;
                        nx = MathHelper.cos(lat) * cos;
                        ny = MathHelper.sin(lat);
                        nz = MathHelper.cos(lat) * sin;
                        px = base.x + nx * radius;
                        py = base.y + radius + ny * radius;
                        pz = base.z + nz * radius;
                    }
                    case DOME -> {
                        float lat = along * MathHelper.HALF_PI;
                        nx = MathHelper.cos(lat) * cos;
                        ny = MathHelper.sin(lat);
                        nz = MathHelper.cos(lat) * sin;
                        px = base.x + nx * radius;
                        py = base.y + ny * radius;
                        pz = base.z + nz * radius;
                    }
                    default -> {
                        nx = cos;
                        ny = 0.0;
                        nz = sin;
                        px = base.x + nx * radius;
                        py = base.y + along * height;
                        pz = base.z + nz * radius;
                        // Standing on the ground and fading out upward.
                        envelope = Math.min(1.0f, along * 12.0f) * (1.0f - along);
                    }
                }
                float edge = edgeOn(nx, ny, nz, viewer.x - px, viewer.y - py, viewer.z - pz);
                float alpha = face + (rim - face) * edge * edge;
                if (shimmer > 0.0f) {
                    alpha *= 1.0f + shimmer * MathHelper.sin(along * MathHelper.TAU * 2.0f - time * 0.12f);
                }
                int rgb = FxColor.mix(glow, hot, edge * edge * edge * 0.6f);
                grid.set(r, c, px, py, pz, rgb, Math.max(0.0f, alpha * envelope * strength));
            }
        }
    }

    /**
     * How edge-on a surface with normal {@code n} is to a viewer in direction {@code toViewer}: 0
     * square-on, 1 edge-on.
     */
    static float edgeOn(double nx, double ny, double nz, double tx, double ty, double tz) {
        double length = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (length < 1.0e-6) {
            return 1.0f;
        }
        double dot = Math.abs((nx * tx + ny * ty + nz * tz) / length);
        return (float) (1.0 - Math.min(1.0, dot));
    }
}
