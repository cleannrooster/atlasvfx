package dev.eann.atlasvfx.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The phases the classifier reads: what is drawn, how it blends, and where to. */
@Mixin(RenderLayer.MultiPhaseParameters.class)
public interface MultiPhaseParametersAccessor {

    @Accessor("texture")
    RenderPhase.TextureBase atlasvfx$texture();

    @Accessor("transparency")
    RenderPhase.Transparency atlasvfx$transparency();

    @Accessor("texturing")
    RenderPhase.Texturing atlasvfx$texturing();

    @Accessor("target")
    RenderPhase.Target atlasvfx$target();

    @Accessor("lightmap")
    RenderPhase.Lightmap atlasvfx$lightmap();

    @Accessor("overlay")
    RenderPhase.Overlay atlasvfx$overlay();
}
