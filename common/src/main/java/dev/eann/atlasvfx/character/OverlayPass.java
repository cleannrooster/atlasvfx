package dev.eann.atlasvfx.character;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * One pass drawn over a body: the same model, posed as it was just drawn, drawn again into another
 * of the game's own layers. The vanilla way of doing it - the enchantment glint on armour, a charged
 * creeper's swirl - so shader packs and other renderers already know these layers.
 *
 * <p>Drawn over the body only, not over armour or what it holds; a {@link Substitution} changes
 * those.</p>
 */
public sealed interface OverlayPass
        permits OverlayPass.Glint, OverlayPass.Glow, OverlayPass.Energy, OverlayPass.EndSky {

    /**
     * The layer to draw into.
     *
     * @param skin the body's own texture
     * @param time ticks, with the frame's fraction: what scrolls and pulses
     */
    RenderLayer layer(Identifier skin, float time);

    /** The colour to draw with, as ARGB. */
    int color(float time);

    /** Whether it is drawn at full brightness, whatever light the body stands in. */
    boolean emissive();

    /** The enchantment glint, over the body. */
    record Glint() implements OverlayPass {
        @Override
        public RenderLayer layer(Identifier skin, float time) {
            return RenderLayer.getEntityGlint();
        }

        @Override
        public int color(float time) {
            return 0xFFFFFFFF;
        }

        @Override
        public boolean emissive() {
            return false;
        }
    }

    /**
     * The body's own skin added on top as light, in a colour: a body lit from within. The same layer
     * a spider's and an enderman's eyes glow in.
     *
     * @param rgb      the colour of the light
     * @param strength how bright, 0 to 1
     * @param pulse    how much of it beats, 0 for a steady glow and 1 for one that goes out between beats
     */
    record Glow(int rgb, float strength, float pulse) implements OverlayPass {
        @Override
        public RenderLayer layer(Identifier skin, float time) {
            return RenderLayer.getEyes(skin);
        }

        @Override
        public int color(float time) {
            float beat = 0.5f + 0.5f * MathHelper.sin(time * 0.15f);
            float level = MathHelper.clamp(strength * (1.0f - pulse + pulse * beat), 0.0f, 1.0f);
            return 0xFF000000 | scaled(rgb, level);
        }

        @Override
        public boolean emissive() {
            return true;
        }
    }

    /**
     * A texture scrolling over the body as added light: a charged creeper's swirl, the wither's
     * armour.
     *
     * @param texture what scrolls; {@link #CREEPER} for the charged creeper's
     * @param rgb     its colour; {@code 0x808080} is the creeper's own
     * @param speed   how fast it scrolls; 1 is the creeper's pace
     */
    record Energy(Identifier texture, int rgb, float speed) implements OverlayPass {

        public static final Identifier CREEPER = Identifier.ofVanilla("textures/entity/creeper/creeper_armor.png");
        public static final Identifier WITHER = Identifier.ofVanilla("textures/entity/wither/wither_armor.png");

        @Override
        public RenderLayer layer(Identifier skin, float time) {
            float scroll = time * 0.01f * speed;
            return RenderLayer.getEnergySwirl(texture, scroll % 1.0f, scroll % 1.0f);
        }

        @Override
        public int color(float time) {
            return 0xFF000000 | rgb;
        }

        @Override
        public boolean emissive() {
            return true;
        }
    }

    /**
     * The End's starfield over the body, as through an end portal: the body becomes a window into
     * it. Drawn over the skin rather than instead of it - its outline, armour and held items stay.
     */
    record EndSky() implements OverlayPass {
        @Override
        public RenderLayer layer(Identifier skin, float time) {
            return RenderLayer.getEndPortal();
        }

        @Override
        public int color(float time) {
            return 0xFFFFFFFF;
        }

        @Override
        public boolean emissive() {
            return true;
        }
    }

    private static int scaled(int rgb, float level) {
        int r = Math.round((rgb >> 16 & 0xFF) * level);
        int g = Math.round((rgb >> 8 & 0xFF) * level);
        int b = Math.round((rgb & 0xFF) * level);
        return r << 16 | g << 8 | b;
    }
}
