package dev.eann.atlasvfx.example.fabric;

import dev.eann.atlasvfx.example.AtlasVfxExample;
import net.fabricmc.api.ModInitializer;

/** Fabric's common entrypoint for the example. */
public final class ExampleFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AtlasVfxExample.init();
    }
}
