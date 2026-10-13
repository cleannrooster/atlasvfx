package dev.eann.atlasvfx.neoforge;

import dev.eann.atlasvfx.character.OverlayFeatureRenderer;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import dev.eann.atlasvfx.AtlasVfx;
import dev.eann.atlasvfx.AtlasVfxCommon;
import dev.architectury.event.Event;
import dev.eann.atlasvfx.WorldRenderHook;
import net.minecraft.client.MinecraftClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge's entrypoint. On a server it only declares the cue payloads the server sends; the
 * drawing is all on the client.
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
    public AtlasVfxNeoForge(IEventBus modBus) {
        AtlasVfxCommon.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            Client.init(modBus);
        }
    }

    /** Kept apart so the server never loads a class that names client-only types. */
    private static final class Client {
        static void init(IEventBus modBus) {
            // Every living entity's renderer, and each player model's, gets the overlay layer.
            modBus.addListener((EntityRenderersEvent.AddLayers event) -> {
                for (EntityType<?> type : event.getEntityTypes()) {
                    addOverlays(event.getRenderer(type));
                }
                for (SkinTextures.Model skin : event.getSkins()) {
                    addOverlays(event.getSkin(skin));
                }
            });
            NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent event) -> {
                RenderLevelStageEvent.Stage stage = event.getStage();
                Event<WorldRenderHook> hook = stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                        ? WorldRenderHook.AFTER_TRANSLUCENT
                        : stage == RenderLevelStageEvent.Stage.AFTER_WEATHER ? WorldRenderHook.AFTER_WEATHER : null;
                if (hook == null) {
                    return;
                }
                hook.invoker().render(new WorldRenderHook.Context(
                        event.getPoseStack(),
                        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                        event.getCamera(),
                        event.getPartialTick().getTickDelta(false)));
            });
            AtlasVfx.initClient();
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void addOverlays(EntityRenderer<?> renderer) {
            if (renderer instanceof LivingEntityRenderer living) {
                living.addFeature(new OverlayFeatureRenderer(living));
            }
        }
    }
}
