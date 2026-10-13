package dev.eann.atlasvfx;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.eann.atlasvfx.appearance.AppearanceServer;
import dev.eann.atlasvfx.cue.FxCuePayload;
import dev.eann.atlasvfx.cue.TelegraphPayload;

/**
 * The library's start on both sides. Safe on a dedicated server: it names nothing that draws.
 *
 * <p>Each loader's common entrypoint calls {@link #init()} once, during mod construction. On a
 * dedicated server it declares the cue payloads so {@link dev.eann.atlasvfx.cue.FxEmitter} can send
 * them; a client declares them with their receivers instead, in {@link AtlasVfx#initClient()}.</p>
 */
public final class AtlasVfxCommon {

    public static final String MOD_ID = "atlasvfx";

    private static boolean started;

    private AtlasVfxCommon() {
    }

    public static void init() {
        if (started) {
            return;
        }
        started = true;
        AtlasVfxConfig.load();
        AppearanceServer.init();
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(FxCuePayload.ID, FxCuePayload.CODEC);
            NetworkManager.registerS2CPayloadType(TelegraphPayload.ID, TelegraphPayload.CODEC);
        }
    }
}
