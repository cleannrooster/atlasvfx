package dev.eann.atlasvfx.parity;

import dev.eann.atlasvfx.character.CharacterEffects;
import dev.eann.atlasvfx.character.Substitution;
import dev.eann.atlasvfx.model.ModelReplacement;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The one place that decides whether the provider an entity is drawn into is swapped for another:
 * when a parity capture wants it, when it wears a look that swaps its layers, or when another
 * model is drawn in its place.
 *
 * <p>When nothing wants the entity, {@link #wrap} returns the provider it was given, the same
 * object: a renderer that checks its type, or a fast path that unwraps it, sees exactly what it
 * would have without this mod. One provider stands in for both reasons at once, swapping first and
 * recording what was asked for and what it was drawn as.</p>
 *
 * <p>An entity drawn from inside another's drawing - into the provider standing in for the other -
 * gets the real provider back, not the other's look.</p>
 *
 * <p>Called from this mod's mixins on the render thread. Not for other mods.</p>
 */
@ApiStatus.Internal
public final class ProviderSeam {

    private ProviderSeam() {
    }

    /** Around {@code EntityRenderDispatcher.render}: the provider to draw {@code entity} into. */
    public static VertexConsumerProvider wrap(Entity entity, VertexConsumerProvider provider) {
        return open(entity, provider, ParityCapture.Mode.ENTITY, new LayerSource.Dispatcher());
    }

    /** Around a first-person arm: the provider to draw {@code side} of {@code player} into. */
    public static VertexConsumerProvider wrapArm(PlayerEntity player, Arm side, VertexConsumerProvider provider) {
        return open(player, provider, ParityCapture.Mode.ARM, new LayerSource.FirstPersonArm(side));
    }

    private static VertexConsumerProvider open(Entity entity, VertexConsumerProvider provider,
                                               ParityCapture.Mode mode, LayerSource initial) {
        boolean armed = ParityCapture.armed();
        if (!armed && !CharacterEffects.any()) {
            return provider;
        }
        VertexConsumerProvider base = provider;
        if (provider instanceof Seam.Holder holder) {
            if (holder.seam().owner == entity) {
                return provider;
            }
            base = holder.seam().delegate;
        }
        Substitution look = CharacterEffects.substituting(entity);
        ModelReplacement replacement = CharacterEffects.replacing(entity);
        ParitySession session = armed ? ParityCapture.begin(entity, mode, base) : null;
        if (look == null && replacement == null && session == null) {
            return base;
        }
        DrawScope.enter(initial);
        Seam seam = new Seam(entity, base, session, look, replacement);
        return base instanceof VertexConsumerProvider.Immediate immediate
                ? new SeamImmediate(immediate, seam)
                : new Seam.Plain(seam);
    }

    /** After the call {@link #wrap} or {@link #wrapArm} served: ends what it started, if anything. */
    public static void finish(VertexConsumerProvider wrapped) {
        if (wrapped instanceof Seam.Holder holder) {
            DrawScope.exit();
            ParitySession session = holder.seam().session;
            if (session != null) {
                ParityCapture.end(session);
            }
        }
    }

    /** Whether an entity is wrapped - so callers build a source only when one is wanted. */
    public static boolean scoped() {
        return DrawScope.active();
    }

    /** Layers asked for from here until {@link #exit} are {@code source}'s. Only while scoped. */
    public static void enter(LayerSource source) {
        DrawScope.enter(source);
    }

    public static void exit() {
        DrawScope.exit();
    }

    /** The source asking now, for tests and diagnostics; null when nothing is wrapped. */
    static @Nullable LayerSource current() {
        return DrawScope.current();
    }
}
