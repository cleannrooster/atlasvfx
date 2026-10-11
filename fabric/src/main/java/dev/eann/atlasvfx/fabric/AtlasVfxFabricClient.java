package dev.eann.atlasvfx.fabric;

import dev.eann.atlasvfx.AtlasVfx;
import dev.eann.atlasvfx.WorldRenderHook;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

/**
 * Fabric's client entrypoint: adapts the one rendering hook Architectury does not bridge - see
 * {@link WorldRenderHook} - and starts the library.
 */
public final class AtlasVfxFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                WorldRenderHook.AFTER_TRANSLUCENT.invoker().render(new WorldRenderHook.Context(
                        context.matrixStack(),
                        context.consumers(),
                        context.camera(),
                        context.tickCounter().getTickDelta(false))));

        AtlasVfx.initClient();
    }
}
