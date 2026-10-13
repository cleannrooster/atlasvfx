package dev.eann.atlasvfx.fabric;

import dev.eann.atlasvfx.character.OverlayFeatureRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.LivingEntityRenderer;

import dev.eann.atlasvfx.AtlasVfx;
import dev.eann.atlasvfx.WorldRenderHook;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Fabric's client entrypoint: adapts the one rendering hook Architectury does not bridge - see
 * {@link WorldRenderHook} - and starts the library.
 */
public final class AtlasVfxFabricClient implements ClientModInitializer {

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static OverlayFeatureRenderer overlays(LivingEntityRenderer renderer) {
        return new OverlayFeatureRenderer(renderer);
    }

    @Override
    public void onInitializeClient() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                WorldRenderHook.AFTER_TRANSLUCENT.invoker().render(new WorldRenderHook.Context(
                        context.matrixStack(),
                        context.consumers(),
                        context.camera(),
                        context.tickCounter().getTickDelta(false))));
        WorldRenderEvents.LAST.register(context ->
                WorldRenderHook.AFTER_WEATHER.invoker().render(new WorldRenderHook.Context(
                        context.matrixStack(),
                        context.consumers() != null ? context.consumers()
                                : MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                        context.camera(),
                        context.tickCounter().getTickDelta(false))));

        // Every living entity's renderer gets the overlay layer, through Fabric API's own hook.
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) ->
                helper.register(overlays(renderer)));

        AtlasVfx.initClient();
    }
}
