package dev.eann.atlasvfx.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether an on-off phase - lightmap, overlay - is on. Its class is package-private. */
@Mixin(targets = "net.minecraft.client.render.RenderPhase$Toggleable")
public interface ToggleableAccessor {

    @Accessor("enabled")
    boolean atlasvfx$enabled();
}
