package dev.eann.atlasvfx.cue;

import dev.architectury.networking.NetworkManager;
import dev.eann.atlasvfx.telegraph.TelegraphArea;
import dev.eann.atlasvfx.telegraph.TelegraphTiming;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * The server's side of cues: tells nearby players' clients to draw a cue or a telegraph. Safe on a
 * dedicated server - it touches only the telegraph geometry, never anything that draws.
 *
 * <p>A player whose client does not have AtlasVFX is simply not sent anything.</p>
 */
public final class FxEmitter {

    /** Players further than this from a cue never hear of it, in blocks. */
    public static final double RANGE = 96.0;
    /** How long a telegraph lingers after it lands, unless asked otherwise. */
    public static final int TELEGRAPH_FADE_TICKS = 8;

    private FxEmitter() {
    }

    /** Draws {@code cue} for every player within {@link #RANGE} of where it is. */
    public static void emit(ServerWorld world, Identifier cue, FxCueArgs args) {
        send(world, args.at(), RANGE, new FxCuePayload(cue, args));
    }

    /** Draws {@code cue} for every player within {@code range} of where it is. */
    public static void emit(ServerWorld world, Identifier cue, FxCueArgs args, double range) {
        send(world, args.at(), range, new FxCuePayload(cue, args));
    }

    /**
     * Warns of {@code area} - the area the server will hit-test, already placed and turned - landing
     * {@code windupTicks} from now, in the look named {@code style}.
     */
    public static void telegraph(ServerWorld world, TelegraphArea area, int windupTicks, Identifier style) {
        telegraph(world, area, TelegraphTiming.starting(world.getTime(), windupTicks, TELEGRAPH_FADE_TICKS), style, 0);
    }

    /**
     * Warns of {@code area} on its own timing, in the look named {@code style}, or in {@code colour}
     * ({@code 0xRRGGBB}) if that is not 0.
     */
    public static void telegraph(ServerWorld world, TelegraphArea area, TelegraphTiming timing, Identifier style,
                                 int colour) {
        send(world, area.origin(), RANGE, new TelegraphPayload(area, timing, style, colour));
    }

    private static void send(ServerWorld world, Vec3d at, double range, CustomPayload payload) {
        double rangeSq = range * range;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(at) <= rangeSq && NetworkManager.canPlayerReceive(player, payload.getId())) {
                NetworkManager.sendToPlayer(player, payload);
            }
        }
    }
}
