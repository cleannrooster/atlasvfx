package dev.eann.atlasvfx.character;

import dev.eann.atlasvfx.parity.LayerTraits;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/** Ready-made looks, each for whichever body parts it is given. */
public final class Substitutions {

    private Substitutions() {
    }

    /**
     * The End's starfield instead of the body: every part a window into it, as through an end
     * portal. The portal's layer has no transparency, so a player's outer skin layer shows as the
     * solid boxes it is built from. Shader packs draw end portals their own way, so under one it
     * is drawn over the body instead.
     */
    public static Substitution endSky(Set<BodyPart> parts) {
        Set<BodyPart> chosen = Set.copyOf(parts);
        return new Substitution() {
            @Override
            public @Nullable Swap swap(BodyPart part, LayerTraits requested) {
                return chosen.contains(part) ? Swap.of(RenderLayer.getEndPortal()) : null;
            }

            @Override
            public boolean safeUnderShaders() {
                return false;
            }

            @Override
            public List<OverlayPass> overdraw() {
                return List.of(new OverlayPass.EndSky());
            }
        };
    }

    /**
     * See-through: each part drawn translucent in its own textures, at {@code alpha}. Drawn into
     * the game's translucent entity layer, which shader packs sort with the rest.
     */
    public static Substitution ghost(float alpha, Set<BodyPart> parts) {
        Set<BodyPart> chosen = Set.copyOf(parts);
        int tint = argb(alpha, 0xFFFFFF);
        return (part, requested) -> chosen.contains(part) && requested.texture() != null
                ? new Swap(RenderLayer.getEntityTranslucent(requested.texture()), tint) : null;
    }

    /**
     * Lit from within: each part in its own textures at full brightness whatever light it stands
     * in, tinted {@code rgb}, a little see-through.
     */
    public static Substitution glow(int rgb, Set<BodyPart> parts) {
        Set<BodyPart> chosen = Set.copyOf(parts);
        int tint = argb(0.9f, rgb);
        return (part, requested) -> chosen.contains(part) && requested.texture() != null
                ? new Swap(RenderLayer.getEntityTranslucentEmissive(requested.texture()), tint) : null;
    }

    /**
     * Another texture on the same model: a skin swapped for another laid out the same way. Kept
     * translucent where the part asked for translucency, so outer skin layers stay see-through
     * where they are empty.
     */
    public static Substitution retexture(Identifier texture, Set<BodyPart> parts) {
        Set<BodyPart> chosen = Set.copyOf(parts);
        return (part, requested) -> {
            if (!chosen.contains(part)) {
                return null;
            }
            return Swap.of(requested.transparency().equals("translucent_transparency")
                    ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutoutNoCull(texture));
        };
    }

    static int argb(float alpha, int rgb) {
        int a = Math.round(MathHelper.clamp(alpha, 0.0f, 1.0f) * 255.0f);
        return a << 24 | (rgb & 0xFFFFFF);
    }
}
