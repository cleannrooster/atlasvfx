package dev.eann.atlasvfx.parity;

import net.minecraft.client.render.RenderLayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Says what a render layer is to a body, from its phases: whether a substitution would replace it,
 * leave it alone, or cannot tell.
 *
 * <p>Read from what the layer does rather than from a list of the game's layers, so a mod's own
 * layer built the vanilla way is classified like the vanilla layer it resembles. A layer the rules
 * get wrong is named in an override, which always wins.</p>
 *
 * <p>Internal until substitution uses it: the rules may still move.</p>
 */
@ApiStatus.Internal
public final class LayerClassifier {

    /** A label and the rule that gave it. */
    public record Verdict(LayerClass layerClass, String reason) {
    }

    // Copy-on-write: overrides may be named during NeoForge's parallel mod construction, and the
    // render thread reads them every capture.
    private static volatile Map<String, LayerClass> overrides = Map.of();

    private LayerClassifier() {
    }

    /** Classifies every layer named {@code layerName}, whatever its texture, as {@code layerClass}. */
    public static synchronized void override(String layerName, LayerClass layerClass) {
        Map<String, LayerClass> next = new HashMap<>(overrides);
        next.put(layerName, layerClass);
        overrides = Map.copyOf(next);
        // Verdicts cached before this was named are dropped on the next read.
        LayerVerdicts.clearLater();
    }

    /** Render thread: reads the layer's phases. */
    public static Verdict classify(RenderLayer layer) {
        return classify(LayerTraits.of(layer));
    }

    /** The rules, in order; the first that applies decides. */
    public static Verdict classify(LayerTraits traits) {
        LayerClass forced = overrides.get(traits.name());
        if (forced != null) {
            return new Verdict(forced, "override");
        }
        if (!traits.phased()) {
            return new Verdict(LayerClass.UNKNOWN, "custom layer class, no phases to read");
        }
        if (traits.outline() || traits.target().equals("outline_target")) {
            return new Verdict(LayerClass.EFFECT_OVERLAY, "outline");
        }
        if (traits.texturing().equals("glint_texturing") || traits.texturing().equals("entity_glint_texturing")
                || traits.transparency().equals("glint_transparency")) {
            return new Verdict(LayerClass.EFFECT_OVERLAY, "glint");
        }
        if (traits.texturing().equals("offset_texturing")) {
            return new Verdict(LayerClass.EFFECT_OVERLAY, "scrolling texture (energy swirl)");
        }
        if (traits.transparency().equals("additive_transparency")
                || traits.transparency().equals("lightning_transparency")) {
            return new Verdict(LayerClass.EFFECT_OVERLAY, "additive (eyes, emissive)");
        }
        if (traits.transparency().equals("crumbling_transparency")) {
            return new Verdict(LayerClass.EFFECT_OVERLAY, "crumbling");
        }
        if (traits.format().equals(LayerTraits.TEXT_FORMAT)) {
            return new Verdict(LayerClass.UNKNOWN, "text");
        }
        if (traits.texture() == null) {
            return new Verdict(LayerClass.UNKNOWN, "no single texture");
        }
        if (traits.transparency().equals("translucent_transparency")) {
            return new Verdict(LayerClass.TRANSLUCENT_SURFACE, "translucent");
        }
        if (traits.transparency().equals("no_transparency")) {
            return new Verdict(LayerClass.SURFACE, "solid or cut-out");
        }
        return new Verdict(LayerClass.UNKNOWN, "unrecognised transparency " + traits.transparency());
    }
}
