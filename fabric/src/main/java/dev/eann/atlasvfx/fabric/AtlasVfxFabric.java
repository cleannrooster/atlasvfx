package dev.eann.atlasvfx.fabric;

import dev.eann.atlasvfx.AtlasVfxCommon;
import net.fabricmc.api.ModInitializer;

/** Fabric's common entrypoint, on both sides: declares the cue payloads a server sends. */
public final class AtlasVfxFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        AtlasVfxCommon.init();
    }
}
