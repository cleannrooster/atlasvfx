package dev.eann.atlasvfx.model;

import dev.eann.atlasvfx.character.BodyPart;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A body drawn as another model: a geometry file in place of the skin - and, as chosen, of armour,
 * cape and the rest - posed by the body it replaces or by its own animations.
 *
 * <p>The vanilla body is not cancelled: every renderer and every mod's hook on it still runs, and
 * only the layers of the parts in {@link #hides} are left undrawn. If the model cannot be loaded the
 * body is drawn as it always was.</p>
 *
 * @param geo        a geometry file, e.g. {@code mymod:geo/rogue.geo.json}
 * @param texture    the texture it is drawn in
 * @param animations an animation file for {@link Posing#ANIMATED}; null for none
 * @param animator   what it plays when, for {@link Posing#ANIMATED}; null to hold its rest pose
 * @param hides      the body's own parts left undrawn; the skin and first-person arm always are
 * @param followLook whether its head - if it has a plainly named one - turns where the body looks
 */
public record ModelReplacement(Identifier geo, Identifier texture, @Nullable Identifier animations, Posing posing,
                               @Nullable GeoAnimator animator, Set<BodyPart> hides, boolean followLook) {

    public enum Posing {
        /**
         * Head, body, arms and legs follow the vanilla body's pose: walking, sneaking, swinging,
         * and every pose an animation mod gives the vanilla model.
         */
        FOLLOW_BODY,
        /** Its own animations pose it; only the head still follows where the body looks. */
        ANIMATED
    }

    public ModelReplacement {
        hides = Set.copyOf(hides);
    }

    /** A model that follows the body, hiding everything it is seen as but what it holds. */
    public static ModelReplacement following(Identifier geo, Identifier texture) {
        return new ModelReplacement(geo, texture, null, Posing.FOLLOW_BODY, null, BodyPart.WORN, true);
    }

    /** A model that plays its own animations, chosen by {@code animator}. */
    public static ModelReplacement animated(Identifier geo, Identifier texture, Identifier animations,
                                            GeoAnimator animator) {
        return new ModelReplacement(geo, texture, animations, Posing.ANIMATED, animator, BodyPart.WORN, true);
    }

    /** Whether the body's own layers for {@code part} are left undrawn. */
    public boolean hides(BodyPart part) {
        return part == BodyPart.SKIN || part == BodyPart.ARM || hides.contains(part);
    }

    public ModelReplacement hiding(Set<BodyPart> parts) {
        return new ModelReplacement(geo, texture, animations, posing, animator, parts, followLook);
    }
}
