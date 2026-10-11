package dev.eann.atlasvfx;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;

/**
 * The library's own start: what keeps {@link FxWorld} going without any mod that uses it having
 * to.
 *
 * <p>Each loader's client entrypoint calls {@link #initClient()} once, after adapting its own
 * render event into {@link WorldRenderHook}. From then on the world's effects tick with the
 * client, are forgotten when a world is joined or left, and are drawn after the world's
 * translucent geometry. A mod using this only plays shapes and spawns motes. It also registers
 * the {@code /atlasvfx} client commands, for looking at effects without such a mod.</p>
 */
public final class AtlasVfx {

    public static final String MOD_ID = "atlasvfx";

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
        ClientTickEvent.CLIENT_POST.register(world::tick);
        // Entity ids and positions mean nothing in the next world.
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> world.clear());
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> world.clear());
        WorldRenderHook.AFTER_TRANSLUCENT.register(world::render);
        AtlasVfxCommands.register();
    }
}
