package dev.eann.atlasvfx.mixin;

import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/** The texture a phase binds: empty for none, or for several at once. */
@Mixin(RenderPhase.TextureBase.class)
public interface TextureBaseInvoker {

    @Invoker("getId")
    Optional<Identifier> atlasvfx$id();
}
