package dev.eann.atlasvfx.example;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.eann.atlasvfx.cue.FxCueArgs;
import dev.eann.atlasvfx.cue.FxCues;
import dev.eann.atlasvfx.cue.FxEmitter;
import dev.eann.atlasvfx.telegraph.TelegraphArea;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * A dev-only mod that uses AtlasVFX the way a content mod would, to try it in a dev client.
 *
 * <p>The server side lives here: {@code /atlasvfx_example} sends cues and telegraphs to every
 * nearby client through {@link FxEmitter} - the whole server-to-client path, from a real server
 * command. The client side, in {@link AtlasVfxExampleClient}, registers the style this mod names
 * and a cue of its own.</p>
 *
 * <ul>
 *   <li>{@code /atlasvfx_example cue <name>} - a built-in cue three blocks in front of you, in this
 *       mod's style: pulse, nova, explosion, blast, blast_falling, beam, arc, cone, burst, wisp,
 *       smoke, rain, slash.</li>
 *   <li>{@code /atlasvfx_example telegraph <circle|ring|cone|line> [ticks]} - a telegraph at your
 *       feet, facing where you face, in this mod's look.</li>
 *   <li>{@code /atlasvfx_example sigil} - this mod's own cue, drawn by a shape it defines.</li>
 * </ul>
 */
public final class AtlasVfxExample {

    public static final String MOD_ID = "atlasvfx_example";

    /** The look this mod's cues and telegraphs are drawn in: a violet, registered on the client. */
    public static final Identifier STYLE = Identifier.of(MOD_ID, "violet");
    /** This mod's own cue: a turning ring of glyphs under a nova. */
    public static final Identifier SIGIL = Identifier.of(MOD_ID, "sigil");

    private AtlasVfxExample() {
    }

    /** Both sides, at mod construction. */
    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, selection) -> dispatcher.register(
                CommandManager.literal(MOD_ID)
                        .then(CommandManager.literal("cue")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(AtlasVfxExample::cue)))
                        .then(CommandManager.literal("telegraph")
                                .then(CommandManager.argument("shape", StringArgumentType.word())
                                        .executes(context -> telegraph(context, 40))
                                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(1, 400))
                                                .executes(context -> telegraph(context,
                                                        IntegerArgumentType.getInteger(context, "ticks"))))))
                        .then(CommandManager.literal("sigil").executes(AtlasVfxExample::sigil))));
    }

    private static int cue(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        // The built-in cues all live in the library's namespace.
        Identifier cue = Identifier.of(FxCues.PULSE.getNamespace(), name);
        ServerWorld world = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d eye = player.getEyePos();
        Vec3d front = eye.add(look.multiply(3.0));
        long seed = world.random.nextLong();
        FxCueArgs args = switch (name) {
            case "beam", "arc" -> FxCueArgs.between(STYLE, eye.add(look), eye.add(look.multiply(8.0)), 0.4f, 1, seed);
            case "cone" -> FxCueArgs.cone(STYLE, eye.add(look), look, 35.0f, 6.0f, 1, seed);
            case "slash" -> new FxCueArgs(STYLE, eye.subtract(0.0, 0.3, 0.0), look, 3.0f, 70.0f, 0, seed, -1);
            default -> FxCueArgs.at(STYLE, front, 2.0f, 1, seed);
        };
        FxEmitter.emit(world, cue, args);
        context.getSource().sendFeedback(() -> Text.literal("Sent " + cue), false);
        return 1;
    }

    private static int telegraph(CommandContext<ServerCommandSource> context, int ticks) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        Vec3d feet = player.getPos();
        float yaw = player.getYaw();
        TelegraphArea area = switch (StringArgumentType.getString(context, "shape")) {
            case "ring" -> new TelegraphArea.Ring(feet, 2.0f, 5.0f);
            case "cone" -> new TelegraphArea.Cone(feet, yaw, 7.0f, 40.0f);
            case "line" -> new TelegraphArea.Line(feet, yaw, 10.0f, 1.25f);
            default -> new TelegraphArea.Circle(feet, 3.5f);
        };
        FxEmitter.telegraph(player.getServerWorld(), area, ticks, STYLE);
        context.getSource().sendFeedback(() -> Text.literal("Telegraph lands in " + ticks + " ticks"), false);
        return 1;
    }

    private static int sigil(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        ServerWorld world = player.getServerWorld();
        FxEmitter.emit(world, SIGIL, FxCueArgs.at(STYLE, player.getPos().add(0.0, 0.05, 0.0), 2.5f, 1,
                world.random.nextLong()));
        return 1;
    }
}
