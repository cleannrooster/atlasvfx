package dev.eann.atlasvfx.character;

import dev.eann.atlasvfx.model.ModelReplacement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything done to how one body is drawn: passes drawn over it, a look that changes what it is
 * drawn as, and another model drawn in its place.
 *
 * @param overlays     drawn over the body - or over its replacement - whatever the mode
 * @param substitution what its parts are drawn as instead; null to leave them
 * @param mode         how the substitution is shown: by swapping layers, or by drawing its
 *                     {@link Substitution#overdraw() overdraw} passes over the body
 * @param replacement  another model drawn in the body's place; null for none
 */
public record CharacterEffect(List<OverlayPass> overlays, @Nullable Substitution substitution, Mode mode,
                              @Nullable ModelReplacement replacement) {

    public enum Mode {
        /** The parts are drawn as the look says, in place of what they would have been. */
        SUBSTITUTE,
        /** The parts are drawn as they were, with the look's overdraw passes on top. */
        OVERDRAW
    }

    public static final CharacterEffect NONE = new CharacterEffect(List.of(), null, Mode.SUBSTITUTE, null);

    public CharacterEffect {
        overlays = List.copyOf(overlays);
        mode = mode == null ? Mode.SUBSTITUTE : mode;
    }

    public CharacterEffect(List<OverlayPass> overlays, @Nullable Substitution substitution, Mode mode) {
        this(overlays, substitution, mode, null);
    }

    public static CharacterEffect overlays(OverlayPass... passes) {
        return new CharacterEffect(List.of(passes), null, Mode.SUBSTITUTE, null);
    }

    public static CharacterEffect substitute(Substitution substitution) {
        return new CharacterEffect(List.of(), substitution, Mode.SUBSTITUTE, null);
    }

    public static CharacterEffect replace(ModelReplacement replacement) {
        return new CharacterEffect(List.of(), null, Mode.SUBSTITUTE, replacement);
    }

    public CharacterEffect withOverlays(List<OverlayPass> passes) {
        return new CharacterEffect(passes, substitution, mode, replacement);
    }

    public CharacterEffect withSubstitution(@Nullable Substitution look) {
        return new CharacterEffect(overlays, look, mode, replacement);
    }

    public CharacterEffect withMode(Mode other) {
        return new CharacterEffect(overlays, substitution, other, replacement);
    }

    public CharacterEffect withReplacement(@Nullable ModelReplacement other) {
        return new CharacterEffect(overlays, substitution, mode, other);
    }

    public boolean isEmpty() {
        return overlays.isEmpty() && substitution == null && replacement == null;
    }

    /**
     * Two effects on one body as one: both sets of overlays, and this one's look and replacement
     * unless it has none.
     */
    public CharacterEffect and(CharacterEffect other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        List<OverlayPass> passes = new ArrayList<>(overlays);
        passes.addAll(other.overlays);
        ModelReplacement model = replacement != null ? replacement : other.replacement;
        return substitution != null ? new CharacterEffect(passes, substitution, mode, model)
                : new CharacterEffect(passes, other.substitution, other.mode, model);
    }
}
