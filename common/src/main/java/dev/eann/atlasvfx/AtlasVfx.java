package dev.eann.atlasvfx;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.eann.atlasvfx.character.CharacterEffects;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.eann.atlasvfx.appearance.AppearanceClient;
import dev.eann.atlasvfx.appearance.Appearances;
import dev.eann.atlasvfx.cue.FxCueHandlers;
import dev.eann.atlasvfx.model.GeoAssets;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import dev.eann.atlasvfx.parity.ParityCapture;

/**
 * The library's own start: what keeps {@link FxWorld} going without any mod that uses it having
 * to.
 *
 * <p>Each loader's client entrypoint calls {@link #initClient()} once, after adapting its own
 * render event into {@link WorldRenderHook}. From then on the world's effects tick with the
 * client, are forgotten when a world is joined or left, and are drawn after the world's
 * translucent geometry, and cues and telegraphs a server sends are drawn. A mod using this only
 * plays shapes and spawns motes. It also registers the {@code /atlasvfx} client commands, for
 * looking at effects without such a mod.</p>
 */
public final class AtlasVfx {

    public static final String MOD_ID = AtlasVfxCommon.MOD_ID;

    private static boolean started;

    private AtlasVfx() {
    }

    /** Starts the client side. Calling it again does nothing. */
    public static void initClient() {
        if (started) {
            return;
        }
        started = true;
        FxWorld world = FxWorld.get();
        FxAttachments attachments = FxAttachments.get();
        ClientTickEvent.CLIENT_POST.register(world::tick);
        ClientTickEvent.CLIENT_POST.register(attachments::tick);
        // Entity ids and positions mean nothing in the next world.
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            CharacterEffects.clearAll();
            attachments.clear();
            world.clear();
        });
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            CharacterEffects.clearAll();
            attachments.clear();
            world.clear();
        });
        WorldRenderHook.AFTER_TRANSLUCENT.register(world::render);
        FxCueHandlers.registerReceivers();
        ParityCapture.install();
        // Geometry and animation files are read from resource packs; a reload may change them.
        ReloadListenerRegistry.register(ResourceType.CLIENT_RESOURCES, (SynchronousResourceReloader) manager -> {
            GeoAssets.clear();
            Appearances.reload(manager);
        });
        AppearanceClient.init();
        AtlasVfxCommands.register();
    }
}
