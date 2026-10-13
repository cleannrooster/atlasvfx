package dev.eann.atlasvfx.parity;

import net.minecraft.client.render.RenderLayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * What each render layer is, worked out once per layer: substitution asks for every layer a body
 * wearing a look requests, every frame, and reading a layer's phases each time would be waste.
 *
 * <p>Keyed weakly by the layer itself - layers compare by identity - so the layers some renderers
 * make afresh each frame, a charged creeper's swirl among them, are let go of with the frame that
 * made them. Render thread only.</p>
 */
@ApiStatus.Internal
public final class LayerVerdicts {

    /** A layer's traits and what the classifier made of them. */
    public record Entry(LayerTraits traits, LayerClassifier.Verdict verdict) {
    }

    private static final Map<RenderLayer, Entry> CACHE = new WeakHashMap<>();

    private LayerVerdicts() {
    }

    private static volatile boolean stale;

    public static Entry of(RenderLayer layer) {
        if (stale) {
            stale = false;
            CACHE.clear();
        }
        Entry entry = CACHE.get(layer);
        if (entry == null) {
            LayerTraits traits = LayerTraits.of(layer);
            entry = new Entry(traits, LayerClassifier.classify(traits));
            CACHE.put(layer, entry);
        }
        return entry;
    }

    /**
     * Forgets every verdict before the next is read: an override was named, so earlier ones may be
     * wrong. Safe from any thread; the cache itself is only touched on the render thread.
     */
    static void clearLater() {
        stale = true;
    }
}
