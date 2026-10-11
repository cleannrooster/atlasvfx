package dev.eann.atlasvfx;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/** The light a piece of an effect is drawn in. */
public final class FxLight {

    /** As bright as anything gets: for light, and for pictures that light themselves. */
    public static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private FxLight() {
    }

    /** The world's light at a point; full bright where there is no world to ask. */
    public static int at(@Nullable ClientWorld world, Vec3d at) {
        return world == null ? FULL_BRIGHT : WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(at));
    }
}
