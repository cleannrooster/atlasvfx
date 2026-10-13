package dev.eann.atlasvfx.example.neoforge;

import dev.eann.atlasvfx.example.AtlasVfxExample;
import dev.eann.atlasvfx.example.AtlasVfxExampleClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/** NeoForge's entrypoint for the example: the server side always, the client side on a client. */
@Mod(AtlasVfxExample.MOD_ID)
public final class ExampleNeoForge {
    public ExampleNeoForge() {
        AtlasVfxExample.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            AtlasVfxExampleClient.init();
        }
    }
}
