package dev.eann.atlasvfx.mixin;

import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A phase's or layer's name - {@code translucent_transparency}, {@code armor_cutout_no_cull}. */
@Mixin(RenderPhase.class)
public interface RenderPhaseAccessor {

    @Accessor("name")
    String atlasvfx$name();
}
