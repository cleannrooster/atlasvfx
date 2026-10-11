package dev.eann.atlasvfx.neoforge;

import dev.eann.atlasvfx.AtlasVfx;
import dev.eann.atlasvfx.WorldRenderHook;
import net.minecraft.client.MinecraftClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge's entrypoint. Nothing here runs on a server: the library only draws.
 *
 * <p>On the client it adapts NeoForge's render stage into {@link WorldRenderHook} and starts the
 * library. {@code AFTER_TRANSLUCENT_BLOCKS} is the stage that lines up with Fabric's
 * {@code AFTER_TRANSLUCENT}: translucent terrain is down, particles and weather are not yet.
 * The event carries no buffer source, so the entity consumers are taken from the client the
 * same way the vanilla world renderer takes them; they are flushed at the end of the frame with
 * everything else drawn into them.</p>
 */
@Mod(AtlasVfx.MOD_ID)
public final class AtlasVfxNeoForge {
    public AtlasVfxNeoForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.init();
        }
    }

    /** Kept apart so the server never loads a class that names client-only types. */
    private static final class Client {
        static void init() {
            NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
                if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                    return;
                }
                WorldRenderHook.AFTER_TRANSLUCENT.invoker().render(new WorldRenderHook.Context(
                        event.getPoseStack(),
                        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                        event.getCamera(),
                        event.getPartialTick().getTickDelta(false)));
            });
            AtlasVfx.initClient();
        }
    }
}
