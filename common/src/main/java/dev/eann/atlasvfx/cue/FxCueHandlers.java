package dev.eann.atlasvfx.cue;

import dev.architectury.networking.NetworkManager;
import dev.eann.atlasvfx.FxDensity;
import dev.eann.atlasvfx.FxShapes;
import dev.eann.atlasvfx.FxStyle;
import dev.eann.atlasvfx.FxWorld;
import dev.eann.atlasvfx.telegraph.Telegraph;
import dev.eann.atlasvfx.telegraph.TelegraphStyle;
import dev.eann.atlasvfx.trail.ArcSweep;
import dev.eann.atlasvfx.trail.SweepFrame;
import dev.eann.atlasvfx.trail.SweepFrameSource;
import dev.eann.atlasvfx.trail.Trail;
import dev.eann.atlasvfx.trail.TrailStyles;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's side of cues: turns the server's cue and telegraph packets into shapes. Client only.
 *
 * <p>The built-in cues in {@link FxCues} are registered here already. A mod with a bespoke look
 * registers its own name and handler, and its server sends that name with {@link FxEmitter} like
 * any built-in one - or registers over a built-in name to draw it its own way.</p>
 */
public final class FxCueHandlers {

    private static final Logger LOGGER = LoggerFactory.getLogger("atlasvfx");

    /** Draws one cue. Runs on the client thread. */
    @FunctionalInterface
    public interface Handler {
        void play(FxWorld world, FxCueArgs args);
    }

    /**
     * How much of a cue to make. Cues arrive from a server, not from one mod's own code, so they are
     * made at the game's particle setting alone.
     */
    public static final FxDensity DENSITY = FxDensity.FULL;

    /**
     * Copy-on-write: mods register during start-up, which on NeoForge runs on parallel threads,
     * so a plain map could lose a registration outright. Writers take the class lock and publish a
     * fresh map; a draw reads it with one volatile load and no locking.
     */
    private static volatile Map<Identifier, Handler> handlers = Map.of();
    private static boolean started;

    static {
        register(FxCues.PULSE, (world, a) -> world.play(FxShapes.pulse(style(a), a.at(), a.size(), a.tier()), DENSITY));
        register(FxCues.NOVA, (world, a) ->
                world.play(FxShapes.nova(style(a), a.at(), a.size(), a.tier(), a.seed()), DENSITY));
        register(FxCues.EXPLOSION, (world, a) ->
                world.play(FxShapes.explosion(style(a), a.at(), a.size(), a.tier(), a.seed()), DENSITY));
        register(FxCues.BLAST, (world, a) ->
                world.play(FxShapes.blast(style(a), a.at(), a.size(), a.tier(), a.seed(), false), DENSITY));
        register(FxCues.BLAST_FALLING, (world, a) ->
                world.play(FxShapes.blast(style(a), a.at(), a.size(), a.tier(), a.seed(), true), DENSITY));
        register(FxCues.BEAM, (world, a) -> world.play(FxShapes.beam(style(a), a.at(), a.to(), a.size()), DENSITY));
        register(FxCues.ARC, (world, a) ->
                world.play(FxShapes.arc(style(a), a.at(), a.to(), a.tier(), a.seed()), DENSITY));
        register(FxCues.CONE, (world, a) ->
                world.play(FxShapes.cone(style(a), a.at(), a.to(), a.angle(), a.size(), a.tier(), a.seed()), DENSITY));
        register(FxCues.BURST, (world, a) -> FxShapes.burst(world, style(a), a.at(), a.size(), 12, a.seed(), DENSITY));
        register(FxCues.WISP, (world, a) -> FxShapes.wisp(world, style(a), a.at(), a.size(), 16, DENSITY.effective()));
        register(FxCues.SMOKE, (world, a) -> FxShapes.smoke(world, a.at(), a.size(), 24, DENSITY.effective()));
        register(FxCues.RAIN, (world, a) -> FxShapes.rain(world, style(a), a.at(), a.size(), 16, DENSITY.effective()));
        register(FxCues.SLASH, (world, a) -> world.play(Trail.slash(
                SweepFrameSource.fixed(SweepFrame.of(a.at(), yawOf(a.to()), 0.0f)), a.size(), a.angle(),
                a.tier() == 1, ArcSweep.Plane.HORIZONTAL, 5, 6, TrailStyles.slash(style(a).glow()), a.seed()),
                DENSITY));
    }

    private FxCueHandlers() {
    }

    /** Names a cue and how to draw it, replacing whatever drew that name before. */
    public static synchronized void register(Identifier cue, Handler handler) {
        Map<Identifier, Handler> next = new HashMap<>(handlers);
        next.put(cue, handler);
        handlers = Map.copyOf(next);
    }

    /**
     * Declares the payloads and their receivers. Called once by the library's own client start,
     * during mod construction, which is when a loader accepts them.
     */
    public static void registerReceivers() {
        if (started) {
            return;
        }
        started = true;
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, FxCuePayload.ID, FxCuePayload.CODEC,
                (payload, context) -> context.queue(() -> play(payload.cue(), payload.args())));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, TelegraphPayload.ID, TelegraphPayload.CODEC,
                (payload, context) -> context.queue(() -> telegraph(payload)));
    }

    /**
     * Draws a cue as if the server had sent it: for a client effect that wants a built-in look, or a
     * single-player shortcut.
     *
     * @return whether anything drew that name
     */
    public static boolean play(Identifier cue, FxCueArgs args) {
        Handler handler = handlers.get(cue);
        if (handler == null) {
            LOGGER.debug("[atlasvfx] no client handler for cue {}", cue);
            return false;
        }
        handler.play(FxWorld.get(), args);
        return true;
    }

    /** Lays a telegraph as if the server had sent it. */
    public static void telegraph(TelegraphPayload payload) {
        TelegraphStyle style = payload.colour() != 0
                ? FxCueStyles.telegraph(payload.colour())
                : FxCueStyles.telegraph(payload.style());
        FxWorld.get().play(Telegraph.fixed(payload.area(), payload.timing(), style), DENSITY);
    }

    private static FxStyle style(FxCueArgs args) {
        return FxCueStyles.style(args.style());
    }

    /** The yaw a direction faces, as the game turns: 0 is south. */
    static float yawOf(Vec3d direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }
}
