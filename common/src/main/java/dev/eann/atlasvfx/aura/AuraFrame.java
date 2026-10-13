package dev.eann.atlasvfx.aura;

import dev.eann.atlasvfx.FxPainter;
import dev.eann.atlasvfx.FxStyle;
import dev.eann.atlasvfx.SurfaceGrid;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Everything one layer of an aura draws from in one frame: where the body is and how big, how far
 * on its clock is, and how strongly the aura shows.
 *
 * @param feet        the bottom middle of the body this frame
 * @param height      the body's height, in blocks
 * @param width       the body's width, in blocks
 * @param scale       what sizes given in blocks are multiplied by on this body; see {@link Sizing}
 * @param time        ticks since the aura appeared, with the frame's fraction: what turns and pulses
 * @param strength    0 to 1: fading in, fading out, or the body dying
 * @param firstPerson whether the viewer is looking out of this body, when anything drawn round it
 *                    would fill the screen
 * @param seed        fixed for the aura's life, for anything picked at random
 * @param grid        a grid to fill and draw a surface from, shared by the aura's layers in turn
 */
public record AuraFrame(FxPainter painter, @Nullable ClientWorld world, FxStyle style, Vec3d feet, float height,
                        float width, float scale, float time, float strength, boolean firstPerson, long seed,
                        SurfaceGrid grid) {

    /** The middle of the body. */
    public Vec3d centre() {
        return feet.add(0.0, height * 0.5, 0.0);
    }
}
