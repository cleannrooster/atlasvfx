package dev.eann.atlasvfx.character;

import dev.eann.atlasvfx.parity.LayerTraits;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A look that changes what a body is drawn as: for each layer one of its parts asks for, what to
 * draw into instead, if anything. The geometry is the game's own - posed by whatever posed it -
 * so only the layer changes.
 *
 * <p>Asked only for layers that are a body's surface: glint, outlines, eyes and other effect
 * overlays, and layers that cannot be read, are always drawn as asked.</p>
 */
@FunctionalInterface
public interface Substitution {

    /**
     * What to draw a layer asked for by {@code part} into instead; null to draw it as asked.
     *
     * @param requested what the layer is: its name, texture and blending
     */
    @Nullable Swap swap(BodyPart part, LayerTraits requested);

    /**
     * Whether it looks right under a shader pack. One that does not is drawn the
     * {@link CharacterEffect.Mode#OVERDRAW overdraw} way instead while a pack is in use.
     */
    default boolean safeUnderShaders() {
        return true;
    }

    /** What to draw over the body instead, when it is drawn the overdraw way. */
    default List<OverlayPass> overdraw() {
        return List.of();
    }
}
