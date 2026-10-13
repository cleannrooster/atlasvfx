package dev.eann.atlasvfx.mixin;

import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A phased layer's phases, for reading what kind of layer it is. */
@Mixin(RenderLayer.MultiPhase.class)
public interface MultiPhaseAccessor {

    @Accessor("phases")
    RenderLayer.MultiPhaseParameters atlasvfx$phases();
}
