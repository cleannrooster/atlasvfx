package dev.eann.atlasvfx.example.fabric;

import dev.eann.atlasvfx.example.AtlasVfxExampleClient;
import net.fabricmc.api.ClientModInitializer;

/** Fabric's client entrypoint for the example. */
public final class ExampleFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AtlasVfxExampleClient.init();
    }
}
