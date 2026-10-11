package dev.eann.atlasvfx;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import dev.eann.atlasvfx.telegraph.Telegraph;
import dev.eann.atlasvfx.telegraph.TelegraphAnchor;
import dev.eann.atlasvfx.telegraph.TelegraphArea;
import dev.eann.atlasvfx.telegraph.TelegraphStyle;
import dev.eann.atlasvfx.telegraph.TelegraphStyles;
import dev.eann.atlasvfx.telegraph.TelegraphTiming;
import dev.eann.atlasvfx.trail.ArcSweep;
import dev.eann.atlasvfx.trail.SweepFrameSource;
import dev.eann.atlasvfx.trail.Trail;
import dev.eann.atlasvfx.trail.TrailStyle;
import dev.eann.atlasvfx.trail.TrailStyles;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Random;

/**
 * Client commands for looking at effects without a mod that plays them.
 *
 * <p>{@code /atlasvfx slash [options]} swings a slash from the player's own eyes. Options, in any
 * order: {@code vertical} for an overhead chop, {@code glow} for one that lights itself,
 * {@code flash} to flash it as it lands, {@code wide} for a full circle, and a colour as
 * {@code #RRGGBB}. Successive slashes alternate direction, like a combo. Easiest seen in third
 * person, where the part nearest the eyes is not left out.</p>
 *
 * <p>{@code /atlasvfx telegraph [options]} lays a telegraph at the player's feet, facing where
 * they face. Options: a shape - {@code circle} (the default), {@code ring}, {@code cone} or
 * {@code line}; a number of ticks until it lands (40 by default); {@code grow}, {@code sweep} or
 * {@code pulse} for how it fills; {@code follow} to have it follow the player until three
 * quarters of the way in; and a colour as {@code #RRGGBB}.</p>
 *
 * <p>{@code /atlasvfx cone [options]} blasts a cone from the player's eyes, the way they look.
 * Options: {@code narrow} or {@code wide} for its opening (35 degrees each side by default),
 * {@code far} to throw it twice as far, {@code heavy} for the most it throws, and a colour as
 * {@code #RRGGBB}.</p>
 */
final class AtlasVfxCommands {

    private static final int DEFAULT_COLOUR = 0xB8C4D0;
    private static final float REACH = 3.0f;
    private static final float HALF_ANGLE = 70.0f;
    private static final int SWING_TICKS = 6;
    private static final int FADE_TICKS = 4;
    /** Better Combat's attack origin: the eyes, dropped by 15% of the height. */
    private static final float EYE_DROP = 0.15f;

    private static final Random RANDOM = new Random();
    private static boolean backhand;

    private AtlasVfxCommands() {
    }

    static void register() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandRegistrationEvent.literal(AtlasVfx.MOD_ID)
                        .then(withOptions("slash", AtlasVfxCommands::slash))
                        .then(withOptions("telegraph", AtlasVfxCommands::telegraph))
                        .then(withOptions("cone", AtlasVfxCommands::cone))));
    }

    /** A subcommand taking its options as the rest of the line, or none. */
    private static LiteralArgumentBuilder<ClientCommandSourceStack> withOptions(String name, Subcommand run) {
        return ClientCommandRegistrationEvent.literal(name)
                .executes(context -> run.run(context, ""))
                .then(ClientCommandRegistrationEvent.argument("options", StringArgumentType.greedyString())
                        .executes(context -> run.run(context, StringArgumentType.getString(context, "options"))));
    }

    @FunctionalInterface
    private interface Subcommand {
        int run(CommandContext<ClientCommandSourceStack> context, String options);
    }

    private static int telegraph(CommandContext<ClientCommandSourceStack> context, String options) {
        ClientCommandSourceStack source = context.getSource();
        ClientPlayerEntity player = source.arch$getPlayer();
        ClientWorld world = source.arch$getLevel();
        if (player == null || world == null) {
            return 0;
        }
        String shape = "circle";
        int windup = 40;
        TelegraphStyle.FillMode mode = TelegraphStyle.FillMode.GROW;
        boolean follow = false;
        int colour = -1;
        for (String word : options.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (word) {
                case "" -> {
                }
                case "circle", "ring", "cone", "line" -> shape = word;
                case "grow" -> mode = TelegraphStyle.FillMode.GROW;
                case "sweep" -> mode = TelegraphStyle.FillMode.SWEEP;
                case "pulse" -> mode = TelegraphStyle.FillMode.PULSE;
                case "follow" -> follow = true;
                default -> {
                    Integer parsed = colour(word);
                    if (parsed != null) {
                        colour = parsed;
                    } else if (word.chars().allMatch(Character::isDigit) && word.length() <= 4) {
                        windup = Math.max(1, Integer.parseInt(word));
                    } else {
                        source.arch$sendFailure(Text.literal("Unknown option: " + word
                                + " (circle, ring, cone, line, <ticks>, grow, sweep, pulse, follow, #RRGGBB)"));
                        return 0;
                    }
                }
            }
        }
        Vec3d feet = player.getPos();
        float yaw = player.getYaw();
        TelegraphArea area = switch (shape) {
            case "ring" -> new TelegraphArea.Ring(feet, 2.0f, 5.0f);
            case "cone" -> new TelegraphArea.Cone(feet, yaw, 7.0f, 40.0f);
            case "line" -> new TelegraphArea.Line(feet, yaw, 10.0f, 1.25f);
            default -> new TelegraphArea.Circle(feet, 3.5f);
        };
        TelegraphStyle style = (colour >= 0 ? TelegraphStyles.of(colour) : TelegraphStyles.DANGER).withMode(mode);
        long now = world.getTime();
        TelegraphTiming timing = TelegraphTiming.starting(now, windup, 6);
        Telegraph telegraph = follow
                ? Telegraph.following(area, timing, style, TelegraphAnchor.feet(player), now + windup * 3L / 4L)
                : Telegraph.fixed(area, timing, style);
        FxWorld.get().play(telegraph, FxDensity.FULL);
        return 1;
    }

    private static int slash(CommandContext<ClientCommandSourceStack> context, String options) {
        ClientCommandSourceStack source = context.getSource();
        ClientPlayerEntity player = source.arch$getPlayer();
        ClientWorld world = source.arch$getLevel();
        if (player == null || world == null) {
            return 0;
        }
        ArcSweep.Plane plane = ArcSweep.Plane.HORIZONTAL;
        boolean glow = false;
        boolean flash = false;
        float halfAngle = HALF_ANGLE;
        int colour = DEFAULT_COLOUR;
        for (String word : options.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (word) {
                case "" -> {
                }
                case "vertical" -> plane = ArcSweep.Plane.VERTICAL;
                case "horizontal" -> plane = ArcSweep.Plane.HORIZONTAL;
                case "glow" -> glow = true;
                case "flash" -> flash = true;
                case "wide" -> halfAngle = 180.0f;
                default -> {
                    Integer parsed = colour(word);
                    if (parsed == null) {
                        source.arch$sendFailure(Text.literal("Unknown option: " + word
                                + " (vertical, horizontal, glow, flash, wide, #RRGGBB)"));
                        return 0;
                    }
                    colour = parsed;
                }
            }
        }
        TrailStyle style = TrailStyles.slash(colour);
        if (glow) {
            style = style.withBlend(FxBlend.EMISSIVE);
        }
        backhand = !backhand;
        Trail trail = Trail.slash(SweepFrameSource.eyes(player, EYE_DROP), REACH, halfAngle, backhand, plane,
                SWING_TICKS, FADE_TICKS, style, RANDOM.nextLong());
        FxHandle handle = FxWorld.get().play(trail, FxDensity.FULL);
        if (flash) {
            FxWorld.get().add(new At(SWING_TICKS - 1, handle::flash), FxDensity.FULL);
        }
        return 1;
    }

    private static int cone(CommandContext<ClientCommandSourceStack> context, String options) {
        ClientCommandSourceStack source = context.getSource();
        ClientPlayerEntity player = source.arch$getPlayer();
        if (player == null) {
            return 0;
        }
        float halfAngle = 35.0f;
        float reach = 6.0f;
        int tier = 1;
        int colour = DEFAULT_COLOUR;
        for (String word : options.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (word) {
                case "" -> {
                }
                case "narrow" -> halfAngle = 15.0f;
                case "wide" -> halfAngle = 60.0f;
                case "far" -> reach = 12.0f;
                case "heavy" -> tier = 2;
                default -> {
                    Integer parsed = colour(word);
                    if (parsed == null) {
                        source.arch$sendFailure(Text.literal("Unknown option: " + word
                                + " (narrow, wide, far, heavy, #RRGGBB)"));
                        return 0;
                    }
                    colour = parsed;
                }
            }
        }
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d mouth = player.getEyePos().subtract(0.0, player.getHeight() * EYE_DROP, 0.0).add(look.multiply(0.6));
        FxWorld.get().play(FxShapes.cone(coneStyle(colour), mouth, look, halfAngle, reach, tier, RANDOM.nextLong()),
                FxDensity.FULL);
        return 1;
    }

    /** A plain style from one colour, for looking at shapes with nothing of a mod's own behind them. */
    private static FxStyle coneStyle(int colour) {
        int hot = FxColor.mix(colour, FxColor.WHITE, 0.7f);
        MoteRecipe spark = new MoteRecipe(FxSprite.particleRun("generic", 8), FxBlend.EMISSIVE, Motion.BURST,
                colour, 0.14f, 14, 0.0f, 0.05f, 0.0f, 0.0f, 0.1f, true, false);
        return new FxStyle(colour, hot, spark, spark.tinted(hot), spark, null, null, colour, false);
    }

    @Nullable
    private static Integer colour(String word) {
        String hex = word.startsWith("#") ? word.substring(1) : word;
        if (hex.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Does something once, a number of ticks from now, and draws nothing. */
    private record At(int tick, Runnable action) implements FxShape.Timed {
        @Override
        public int ticks() {
            return tick + 1;
        }

        @Override
        public void tick(FxWorld world, FxLife life, float density) {
            if (life.age() == tick) {
                action.run();
            }
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, FxLife life, float tickDelta) {
        }
    }
}
