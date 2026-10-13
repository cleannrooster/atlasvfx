package dev.eann.atlasvfx.appearance;

import dev.eann.atlasvfx.character.CharacterEffect;
import dev.eann.atlasvfx.model.ModelReplacement;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Something a player can choose to be seen as on the appearance screen: a name, a line about it,
 * and the model drawn in their place.
 */
public final class Appearance {

    private final Identifier id;
    private final Text name;
    private final @Nullable Text description;
    private final ModelReplacement replacement;
    private final CharacterEffect effect;

    public Appearance(Identifier id, Text name, @Nullable Text description, ModelReplacement replacement) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.replacement = replacement;
        // One effect object for every body wearing it, every frame.
        this.effect = CharacterEffect.replace(replacement);
    }

    public Identifier id() {
        return id;
    }

    public Text name() {
        return name;
    }

    public @Nullable Text description() {
        return description;
    }

    public ModelReplacement replacement() {
        return replacement;
    }

    /**
     * The same model playing its own clips instead of following the body; null if its file names
     * none. What a chosen appearance looks like is {@link #replacement()}.
     */
    public @Nullable ModelReplacement animated() {
        if (replacement.animator() == null) {
            return null;
        }
        return new ModelReplacement(replacement.geo(), replacement.texture(), replacement.animations(),
                ModelReplacement.Posing.ANIMATED, replacement.animator(), replacement.hides(), replacement.followLook());
    }

    /** What a body wearing it is drawn with. */
    public CharacterEffect effect() {
        return effect;
    }
}
