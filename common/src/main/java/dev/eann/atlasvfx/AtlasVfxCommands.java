package dev.eann.atlasvfx;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import dev.eann.atlasvfx.aura.Aura;
import dev.eann.atlasvfx.aura.Auras;
import dev.eann.atlasvfx.character.BodyPart;
import dev.eann.atlasvfx.character.CharacterEffect;
import dev.eann.atlasvfx.character.CharacterEffects;
import dev.eann.atlasvfx.character.OverlayPass;
import dev.eann.atlasvfx.character.Substitution;
import dev.eann.atlasvfx.character.Substitutions;
import dev.eann.atlasvfx.cue.FxCueStyles;
import dev.eann.atlasvfx.parity.ParityCommand;
import dev.eann.atlasvfx.telegraph.Telegraph;
import dev.eann.atlasvfx.telegraph.TelegraphAnchor;
import dev.eann.atlasvfx.telegraph.TelegraphArea;
import dev.eann.atlasvfx.telegraph.TelegraphStyle;
import dev.eann.atlasvfx.telegraph.TelegraphStyles;
import dev.eann.atlasvfx.telegraph.TelegraphTiming;
import dev.eann.atlasvfx.trail.ArcSweep;
import dev.eann.atlasvfx.trail.SweepFrameSource;
import dev.eann.atlasvfx.trail.Trail;
import dev.eann.atlasvfx.trail.TrailAnchors;
import dev.eann.atlasvfx.trail.TrailStyle;
import dev.eann.atlasvfx.trail.TrailStyles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
 *
 * <p>{@code /atlasvfx trail [blade] [glow] [ticks] [#RRGGBB]} puts a recorded trail on the player
 * for a while; {@code /atlasvfx thrust [glow] [far] [#RRGGBB]} drives a thrust out from their
 * eyes.</p>
 *
 * <p>{@code /atlasvfx aura [ward|embers|orbit|helix|sigil|sphere|off] [target] [scaled] [#RRGGBB]} puts an
 * aura on the player, or with {@code target} on the entity under the crosshair; another replaces
 * it, and {@code off} fades it out.</p>
 *
 * <p>{@code /atlasvfx overlay [glint|glow|pulse|energy|wither|end|off] [target] [#RRGGBB]} draws
 * passes over the player's body, or the targeted entity's: several words stack several passes.</p>
 *
 * <p>{@code /atlasvfx look [endsky|ghost|glow|skin|off] [target] [held] [skinonly] [overdraw]
 * [#RRGGBB]} changes what the player's body is drawn as.</p>
 *
 * <p>{@code /atlasvfx parity [self|target|arm] [diff <name|last>]} lists the render layers one
 * entity is drawn with for a frame, and saves them; see {@link ParityCommand}.</p>
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
    /** The aura this command put on each entity, by id: one each, so the next replaces it. */
    private static final Map<Integer, FxAttachment> AURAS = new HashMap<>();

    private AtlasVfxCommands() {
    }

    static void register() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandRegistrationEvent.literal(AtlasVfx.MOD_ID)
                        .then(withOptions("slash", AtlasVfxCommands::slash))
                        .then(withOptions("telegraph", AtlasVfxCommands::telegraph))
                        .then(withOptions("cone", AtlasVfxCommands::cone))
                        .then(withOptions("trail", AtlasVfxCommands::trail))
                        .then(withOptions("thrust", AtlasVfxCommands::thrust))
                        .then(withOptions("aura", AtlasVfxCommands::aura))
                        .then(withOptions("overlay", AtlasVfxCommands::overlay))
                        .then(withOptions("look", AtlasVfxCommands::look))
                        .then(withOptions("parity", (context, options) -> ParityCommand.run(context.getSource(), options)))));
    }

    /**
     * An aura kept on the player, or on what is under the crosshair: {@code ward}, {@code embers},
     * {@code orbit}, {@code helix}, {@code sigil} or {@code sphere}, a colour as {@code #RRGGBB},
     * {@code scaled} to size it to the body rather than in blocks; {@code off} fades it out. It stays across the entity leaving view and coming back.
     */
    private static int aura(CommandContext<ClientCommandSourceStack> context, String options) {
        ClientCommandSourceStack source = context.getSource();
        ClientPlayerEntity player = source.arch$getPlayer();
        if (player == null || source.arch$getLevel() == null) {
            return 0;
        }
        String kind = "ward";
        boolean target = false;
        boolean scaled = false;
        int colour = 0x7FD4FF;
        for (String word : options.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (word) {
                case "" -> {
                }
                case "ward", "embers", "orbit", "helix", "sigil", "sphere", "off" -> kind = word;
                case "target" -> target = true;
                case "scaled" -> scaled = true;
                default -> {
                    Integer parsed = colour(word);
                    if (parsed == null) {
                        source.arch$sendFailure(Text.literal("Unknown option: " + word
                                + " (ward, embers, orbit, helix, sigil, sphere, off, target, scaled, #RRGGBB)"));
                        return 0;
                    }
                    colour = parsed;
                }
            }
        }
        Entity entity = target ? MinecraftClient.getInstance().targetedEntity : player;
        if (entity == null) {
            source.arch$sendFailure(Text.literal("Nothing under the crosshair"));
            return 0;
        }
        FxAttachment previous = AURAS.remove(entity.getId());
        if (previous != null) {
            previous.detach();
        }
        if (kind.equals("off")) {
            return 1;
        }
        FxStyle style = FxCueStyles.style(colour);
        Aura aura = switch (kind) {
            case "embers" -> Auras.embers(style);
            case "orbit" -> Auras.orbit(style);
            case "helix" -> Auras.helix(style);
            case "sigil" -> Auras.sigil(style);
            case "sphere" -> Auras.sphere(style);
            default -> Auras.ward(style);
        };
        Aura sized = scaled ? aura.scaledToBody() : aura;
        long seed = RANDOM.nextLong();
        AURAS.put(entity.getId(), FxAttachments.get().attach(entity, on -> sized.on(on, seed), FxDensity.FULL));
        return 1;
    }

    /**
     * A look that changes what the player's body - or with {@code target} the entity under the
     * crosshair - is drawn as: {@code endsky} (the End's starfield), {@code ghost} (see-through),
     * {@code glow} (lit from within, in a colour as {@code #RRGGBB}) or {@code skin} (a zombie's
     * skin on the same model); {@code off} takes it off. {@code held} includes what it holds,
     * {@code skinonly} only the skin and arms, and {@code overdraw} draws the look over the body
     * instead of swapping layers. Overlays set by {@code /atlasvfx overlay} stay.
     */
    private static int look(CommandContext<ClientCommandSourceStack> context, String options) {
        ClientCommandSourceStack source = context.getSource();
        ClientPlayerEntity player = source.arch$getPlayer();
        if (player == null || source.arch$getLevel() == null) {
            return 0;
        }
        String kind = "endsky";
        boolean target = false;
        boolean held = false;
        boolean skinOnly = false;
        boolean overdraw = false;
        int colour = 0x7FD4FF;
        for (String word : options.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (word) {
                case "" -> {
                }
                case "endsky", "ghost", "glow", "skin", "off" -> kind = word;
                case "target" -> target = true;
                case "held" -> held = true;
                case "skinonly" -> skinOnly = true;
                case "overdraw" -> overdraw = true;
                default -> {
                    Integer parsed = colour(word);
                    if (parsed == null) {
                        source.arch$sendFailure(Text.literal("Unknown option: " + word
                                + " (endsky, ghost, glow, skin, off, target, held, skinonly, overdraw, #RRGGBB)"));
                        return 0;
                    }
                    colour = parsed;
                }
            }
        }
        Entity entity = target ? MinecraftClient.getInstance().targetedEntity : player;
        if (entity == null) {
            source.arch$sendFailure(Text.literal("Nothing under the crosshair"));
            return 0;
        }
        Set<BodyPart> parts = EnumSet.copyOf(skinOnly ? BodyPart.SKIN_ONLY : BodyPart.WORN);
        if (held) {
            parts.add(BodyPart.HELD);
        }
        Substitution look = switch (kind) {
            case "ghost" -> Substitutions.ghost(0.4f, parts);
            case "glow" -> Substitutions.glow(colour, parts);
            case "skin" -> Substitutions.retexture(
                    Identifier.ofVanilla("textures/entity/zombie/zombie.png"),
                    EnumSet.of(BodyPart.SKIN, BodyPart.ARM));
            case "off" -> null;
            default -> Substitutions.endSky(parts);
        };
        CharacterEffect effect = CharacterEffects.get(entity.getId()).withSubstitution(look)
                .withMode(overdraw ? CharacterEffect.Mode.OVERDRAW : CharacterEffect.Mode.SUBSTITUTE);
        CharacterEffects.set(entity.getId(), effect);
        return 1;
    }

    /**
     * Overlays drawn over the player's body, or with {@code target} the entity under the crosshair:
     * {@code glint}, {@code glow} (lit from within), {@code pulse} (a glow that beats),
     * {@code energy} (the charged creeper's swirl), {@code wither} (the wither's armour) and
     * {@code end} (the End's starfield), stacking; a colour as {@code #RRGGBB} for the glow and
     * swirls; {@code off} clears them.
     */
    private static int overlay(CommandContext<ClientCommandSourceStack> context, String options) {
        ClientCommandSourceStack source = context.getSource();
        ClientPlayerEntity player = source.arch$getPlayer();
        if (player == null || source.arch$getLevel() == null) {
            return 0;
        }
        List<String> kinds = new ArrayList<>();
        boolean target = false;
        int colour = -1;
        for (String word : options.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (word) {
                case "" -> {
                }
                case "glint", "glow", "pulse", "energy", "wither", "end", "off" -> kinds.add(word);
                case "target" -> target = true;
                default -> {
                    Integer parsed = colour(word);
                    if (parsed == null) {
                        source.arch$sendFailure(Text.literal("Unknown option: " + word
                                + " (glint, glow, pulse, energy, wither, end, off, target, #RRGGBB)"));
                        return 0;
                    }
                    colour = parsed;
                }
            }
        }
        Entity entity = target ? MinecraftClient.getInstance().targetedEntity : player;
        if (entity == null) {
            source.arch$sendFailure(Text.literal("Nothing under the crosshair"));
            return 0;
        }
        if (kinds.isEmpty()) {
            kinds.add("glow");
        }
        List<OverlayPass> passes = new ArrayList<>();
        for (String kind : kinds) {
            switch (kind) {
                case "glint" -> passes.add(new OverlayPass.Glint());
                case "glow" -> passes.add(new OverlayPass.Glow(colour >= 0 ? colour : 0x7FD4FF, 0.6f, 0.0f));
                case "pulse" -> passes.add(new OverlayPass.Glow(colour >= 0 ? colour : 0xFF5040, 0.8f, 1.0f));
                case "energy" -> passes.add(new OverlayPass.Energy(OverlayPass.Energy.CREEPER,
                        colour >= 0 ? colour : 0x808080, 1.0f));
                case "wither" -> passes.add(new OverlayPass.Energy(OverlayPass.Energy.WITHER,
                        colour >= 0 ? colour : 0x808080, 1.0f));
                case "end" -> passes.add(new OverlayPass.EndSky());
                default -> passes.clear();
            }
        }
        CharacterEffects.set(entity.getId(), CharacterEffects.get(entity.getId()).withOverlays(passes));
        return 1;
    }

    /**
     * A recorded trail on the player for a while: a streak held out by their right hand, or with
     * {@code blade} the path of a blade held straight out - turn to sweep it. Options: {@code blade},
     * {@code glow}, a number of ticks it lasts (100 by default), a colour as {@code #RRGGBB}.
     */
    private static int trail(CommandContext<ClientCommandSourceStack> context, String options) {
        ClientCommandSourceStack source = context.getSource();
        ClientPlayerEntity player = source.arch$getPlayer();
        if (player == null || source.arch$getLevel() == null) {
            return 0;
        }
        boolean blade = false;
        boolean glow = false;
        int ticks = 100;
        int colour = -1;
        for (String word : options.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (word) {
                case "" -> {
                }
                case "blade" -> blade = true;
                case "glow" -> glow = true;
                default -> {
                    Integer parsed = colour(word);
                    if (parsed != null) {
                        colour = parsed;
                    } else if (word.chars().allMatch(Character::isDigit) && word.length() <= 4) {
                        ticks = Math.max(1, Integer.parseInt(word));
                    } else {
                        source.arch$sendFailure(Text.literal("Unknown option: " + word
                                + " (blade, glow, <ticks>, #RRGGBB)"));
                        return 0;
                    }
                }
            }
        }
        Trail trail;
        if (blade) {
            TrailStyle style = TrailStyles.slash(colour >= 0 ? colour : DEFAULT_COLOUR);
            if (glow) {
                style = style.withBlend(FxBlend.EMISSIVE);
            }
            trail = Trail.blade(TrailAnchors.look(player, 0.35, 0.5, 0.35), TrailAnchors.look(player, 0.35, 2.2, 0.35),
                    6, style, RANDOM.nextLong());
        } else {
            trail = Trail.point(TrailAnchors.look(player, 0.45, 0.7, 0.45), 0.35f, 14,
                    TrailStyles.streak(colour >= 0 ? colour : 0x60C8FF), RANDOM.nextLong());
        }
        FxHandle handle = FxWorld.get().play(trail, FxDensity.FULL);
        FxWorld.get().add(new At(ticks, handle::release), FxDensity.FULL);
        return 1;
    }

    /**
     * A thrust from the player's eyes: a barbed spearhead driven out down where they look.
     * Options: {@code glow}, {@code far} for a longer reach, a colour as {@code #RRGGBB}.
     */
    private static int thrust(CommandContext<ClientCommandSourceStack> context, String options) {
        ClientCommandSourceStack source = context.getSource();
        ClientPlayerEntity player = source.arch$getPlayer();
        if (player == null || source.arch$getLevel() == null) {
            return 0;
        }
        boolean glow = false;
        float reach = 4.0f;
        int colour = DEFAULT_COLOUR;
        for (String word : options.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
            switch (word) {
                case "" -> {
                }
                case "glow" -> glow = true;
                case "far" -> reach = 8.0f;
                default -> {
                    Integer parsed = colour(word);
                    if (parsed == null) {
                        source.arch$sendFailure(Text.literal("Unknown option: " + word + " (glow, far, #RRGGBB)"));
                        return 0;
                    }
                    colour = parsed;
                }
            }
        }
        TrailStyle style = TrailStyles.thrust(colour);
        if (glow) {
            style = style.withBlend(FxBlend.EMISSIVE);
        }
        FxWorld.get().play(Trail.thrust(SweepFrameSource.eyes(player, EYE_DROP), reach, 0.75f, 0.0f, 5, 4, style,
                RANDOM.nextLong()), FxDensity.FULL);
        return 1;
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
