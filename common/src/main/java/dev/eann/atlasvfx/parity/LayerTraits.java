package dev.eann.atlasvfx.parity;

import dev.eann.atlasvfx.mixin.MultiPhaseAccessor;
import dev.eann.atlasvfx.mixin.MultiPhaseParametersAccessor;
import dev.eann.atlasvfx.mixin.RenderPhaseAccessor;
import dev.eann.atlasvfx.mixin.TextureBaseInvoker;
import dev.eann.atlasvfx.mixin.ToggleableAccessor;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * What can be read off a render layer, as plain values: the classifier's whole input, so its
 * rules are tested without a game.
 *
 * <p>Read through accessor mixins, never by casting to the layer's own classes: those are opened
 * at compile time by this mod's widener, but at run time on NeoForge only as far as NeoForge's
 * own transformer opens them, and an interface the mixin adds is reachable everywhere.</p>
 *
 * @param phased       whether the layer is built from phases at all; a custom subclass is not,
 *                     and nothing past {@code mode} means anything for it
 * @param transparency the transparency phase's name, e.g. {@code translucent_transparency}
 * @param texturing    the texturing phase's name, e.g. {@code entity_glint_texturing}
 * @param target       the target phase's name, e.g. {@code outline_target}
 */
public record LayerTraits(String name, String format, String mode, boolean phased, boolean outline,
                          @Nullable Identifier texture, String transparency, String texturing, String target,
                          boolean lightmap, boolean overlay, boolean translucent) {

    private static final Map<VertexFormat, String> FORMATS = Map.ofEntries(
            Map.entry(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL, "block"),
            Map.entry(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, "entity"),
            Map.entry(VertexFormats.POSITION_TEXTURE_COLOR_LIGHT, "particle"),
            Map.entry(VertexFormats.POSITION, "position"),
            Map.entry(VertexFormats.POSITION_COLOR, "position_color"),
            Map.entry(VertexFormats.LINES, "lines"),
            Map.entry(VertexFormats.POSITION_COLOR_LIGHT, "position_color_light"),
            Map.entry(VertexFormats.POSITION_TEXTURE, "position_tex"),
            Map.entry(VertexFormats.POSITION_TEXTURE_COLOR, "position_tex_color"),
            Map.entry(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT, "text"),
            Map.entry(VertexFormats.POSITION_TEXTURE_LIGHT_COLOR, "position_tex_light_color"),
            Map.entry(VertexFormats.POSITION_TEXTURE_COLOR_NORMAL, "position_tex_color_normal"));

    /** The text format's name, which the classifier reads as "a name tag, not a body". */
    public static final String TEXT_FORMAT = "text";

    /** Reads a layer. Render thread. */
    public static LayerTraits of(RenderLayer layer) {
        String name = ((RenderPhaseAccessor) layer).atlasvfx$name();
        String format = formatName(layer.getVertexFormat());
        String mode = layer.getDrawMode().name().toLowerCase(java.util.Locale.ROOT);
        if (!(layer instanceof MultiPhaseAccessor multiPhase)) {
            return new LayerTraits(name, format, mode, false, layer.isOutline(), null, "", "", "", false, false,
                    layer.isTranslucent());
        }
        MultiPhaseParametersAccessor phases = (MultiPhaseParametersAccessor) (Object) multiPhase.atlasvfx$phases();
        RenderPhase.TextureBase texture = phases.atlasvfx$texture();
        Identifier id = ((TextureBaseInvoker) texture).atlasvfx$id().orElse(null);
        return new LayerTraits(name, format, mode, true, layer.isOutline(), id,
                phaseName(phases.atlasvfx$transparency()), phaseName(phases.atlasvfx$texturing()),
                phaseName(phases.atlasvfx$target()),
                ((ToggleableAccessor) phases.atlasvfx$lightmap()).atlasvfx$enabled(),
                ((ToggleableAccessor) phases.atlasvfx$overlay()).atlasvfx$enabled(),
                layer.isTranslucent());
    }

    static String formatName(VertexFormat format) {
        String known = FORMATS.get(format);
        return known != null ? known : format.toString();
    }

    private static String phaseName(RenderPhase phase) {
        return ((RenderPhaseAccessor) phase).atlasvfx$name();
    }
}
