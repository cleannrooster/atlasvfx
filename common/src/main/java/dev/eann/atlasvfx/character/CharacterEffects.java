package dev.eann.atlasvfx.character;

import dev.eann.atlasvfx.FxLight;
import dev.eann.atlasvfx.compat.IrisCompat;
import dev.eann.atlasvfx.model.ModelReplacement;
import dev.eann.atlasvfx.model.Replacements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who wears what, and drawing the overlay half of it.
 *
 * <p>Two ways to say it. A mod that keeps its own state - synced from its server the way it likes -
 * {@link #register registers} a {@link Source} that reads it, asked per body as it is drawn. Or
 * anything on the client {@link #set sets} an effect on an entity by id, kept until cleared or the
 * world changes. AtlasVFX keeps no other state of who wears what. A body nobody gave anything costs
 * one check.</p>
 *
 * <p>Whether a look swaps layers or is drawn over the body is the effect's own
 * {@link CharacterEffect.Mode mode}, unless the client {@link #forceMode forces} one - a host mod's
 * setting calls that - and a look that is not safe under shader packs is drawn over the body while
 * a pack is in use. Nothing is drawn differently in a shader pack's shadow pass.</p>
 */
public final class CharacterEffects {

    /** Says what a body wears now; null for nothing. Render thread. */
    @FunctionalInterface
    public interface Source {
        @Nullable CharacterEffect effectOf(Entity entity);
    }

    // Copy-on-write: sources are registered during NeoForge's parallel mod construction and read
    // on the render thread every frame.
    private static volatile List<Source> sources = List.of();
    private static volatile CharacterEffect.@Nullable Mode forced;
    // Client thread only, which on the client is also the render thread.
    private static final Map<Integer, CharacterEffect> BY_ENTITY = new HashMap<>();

    private CharacterEffects() {
    }

    public static synchronized void register(Source source) {
        List<Source> next = new ArrayList<>(sources);
        next.add(source);
        sources = List.copyOf(next);
    }

    /** Puts {@code effect} on the entity with this id, replacing any set before; null or empty clears it. */
    public static void set(int entityId, @Nullable CharacterEffect effect) {
        if (effect == null || effect.isEmpty()) {
            BY_ENTITY.remove(entityId);
        } else {
            BY_ENTITY.put(entityId, effect);
        }
    }

    /** What is set on the entity with this id; {@link CharacterEffect#NONE} if nothing. */
    public static CharacterEffect get(int entityId) {
        return BY_ENTITY.getOrDefault(entityId, CharacterEffect.NONE);
    }

    public static void clear(int entityId) {
        BY_ENTITY.remove(entityId);
    }

    /** Forgets everything set by id: ids mean nothing in the next world. */
    public static void clearAll() {
        BY_ENTITY.clear();
    }

    /**
     * Shows every look one way, whatever each effect says - for a host mod's setting; null goes back
     * to each effect's own.
     */
    public static void forceMode(CharacterEffect.@Nullable Mode mode) {
        forced = mode;
    }

    public static CharacterEffect.@Nullable Mode forcedMode() {
        return forced;
    }

    /** Whether any body could wear anything: the one check a body without anything costs. */
    public static boolean any() {
        return !BY_ENTITY.isEmpty() || !sources.isEmpty() || previewing != null;
    }

    private static @Nullable Entity previewing;
    private static CharacterEffect previewed = CharacterEffect.NONE;

    /**
     * Runs {@code draw} with {@code entity} wearing {@code effect} and nothing else - for a screen
     * that shows what something would look like before it is chosen. Nothing else sees it, and it is
     * gone when {@code draw} returns. Render thread.
     */
    public static void preview(Entity entity, CharacterEffect effect, Runnable draw) {
        Entity before = previewing;
        CharacterEffect beforeEffect = previewed;
        previewing = entity;
        previewed = effect;
        try {
            draw.run();
        } finally {
            previewing = before;
            previewed = beforeEffect;
        }
    }

    /** Everything {@code entity} wears now: what is set on it, then each source's. */
    public static CharacterEffect of(Entity entity) {
        if (entity == previewing) {
            return previewed;
        }
        CharacterEffect effect = BY_ENTITY.getOrDefault(entity.getId(), CharacterEffect.NONE);
        for (Source source : sources) {
            CharacterEffect more = source.effectOf(entity);
            if (more != null) {
                effect = effect.and(more);
            }
        }
        return effect;
    }

    /**
     * The look to swap {@code entity}'s layers by in this pass; null to draw it as asked - because it
     * has none, it is shown the overdraw way, or this is a shadow pass.
     */
    public static @Nullable Substitution substituting(Entity entity) {
        if (!any()) {
            return null;
        }
        CharacterEffect effect = of(entity);
        if (effect.substitution() == null || !swaps(effect) || IrisCompat.renderingShadowPass()) {
            return null;
        }
        return effect.substitution();
    }

    /**
     * {@code entity}'s replacement model, if it has one whose model loads - drawn whatever the pass,
     * since a replaced body's shadow should be the replacement's.
     */
    public static @Nullable ModelReplacement replacing(Entity entity) {
        Replacements.Active active = Replacements.active(entity);
        return active == null ? null : active.replacement();
    }

    /** What is drawn over {@code entity}: its overlays, and its look's when shown the overdraw way. */
    public static List<OverlayPass> overlaysOf(Entity entity) {
        CharacterEffect effect = of(entity);
        Substitution look = effect.substitution();
        if (look == null || swaps(effect) || look.overdraw().isEmpty()) {
            return effect.overlays();
        }
        List<OverlayPass> passes = new ArrayList<>(effect.overlays());
        passes.addAll(look.overdraw());
        return passes;
    }

    /** Whether an effect's look is shown by swapping layers now, rather than drawn over the body. */
    static boolean swaps(CharacterEffect effect) {
        CharacterEffect.Mode mode = forced != null ? forced : effect.mode();
        Substitution look = effect.substitution();
        return mode == CharacterEffect.Mode.SUBSTITUTE && look != null
                && (look.safeUnderShaders() || !IrisCompat.shaderPackInUse());
    }

    // ---- drawing overlays --------------------------------------------------------------------------------------

    /**
     * Whether a body's overlays are drawn now: not when the viewer cannot see the body, and not in
     * a shader pack's shadow pass, where they would cast shadows of their own.
     */
    static boolean drawable(Entity entity) {
        if (IrisCompat.renderingShadowPass()) {
            return false;
        }
        PlayerEntity viewer = MinecraftClient.getInstance().player;
        return viewer == null || !entity.isInvisibleTo(viewer);
    }

    /** Draws overlays over a replacement just drawn, posed as it is. */
    static void drawPart(List<OverlayPass> passes, ModelPart part, Identifier skin, MatrixStack matrices,
                         VertexConsumerProvider consumers, int light, float time) {
        for (OverlayPass pass : passes) {
            part.render(matrices, consumers.getBuffer(pass.layer(skin, time)),
                    pass.emissive() ? FxLight.FULL_BRIGHT : light, OverlayTexture.DEFAULT_UV, pass.color(time));
        }
    }

    /** Draws overlays over a model just drawn, posed as it is. */
    static void drawModel(List<OverlayPass> passes, EntityModel<?> model, Identifier skin, MatrixStack matrices,
                          VertexConsumerProvider consumers, int light, float time) {
        for (OverlayPass pass : passes) {
            model.render(matrices, consumers.getBuffer(pass.layer(skin, time)),
                    pass.emissive() ? FxLight.FULL_BRIGHT : light, OverlayTexture.DEFAULT_UV, pass.color(time));
        }
    }

    /**
     * Draws a player's overlays over a first-person arm just drawn: the arm and its sleeve, posed as
     * the arm renderer left them.
     */
    public static void drawArm(PlayerEntity player, ModelPart arm, @Nullable ModelPart sleeve, Identifier skin,
                               MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (!any() || !drawable(player)) {
            return;
        }
        List<OverlayPass> passes = overlaysOf(player);
        if (passes.isEmpty()) {
            return;
        }
        float time = player.age + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        for (OverlayPass pass : passes) {
            int partLight = pass.emissive() ? FxLight.FULL_BRIGHT : light;
            VertexConsumer buffer = consumers.getBuffer(pass.layer(skin, time));
            arm.render(matrices, buffer, partLight, OverlayTexture.DEFAULT_UV, pass.color(time));
            if (sleeve != null) {
                sleeve.render(matrices, buffer, partLight, OverlayTexture.DEFAULT_UV, pass.color(time));
            }
        }
    }
}
