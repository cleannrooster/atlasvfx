package dev.eann.atlasvfx.parity;

import org.jetbrains.annotations.Nullable;

/**
 * One request for a layer while a capture recorded: in the order asked, by whom, what it was
 * taken for and how many vertices went into it.
 *
 * @param texture the layer's texture id, or null for a layer with none or several
 * @param reason  the rule that gave {@code layerClass}, for reading a report
 * @param drawnAs the layer a substitution drew it into instead, or null if it was drawn as asked
 */
public record LayerUse(int order, String layerName, String format, String mode, @Nullable String texture,
                       String source, LayerClass layerClass, String reason, int vertices,
                       @Nullable String drawnAs) {

    /** What identifies "the same use" across two captures: order and counts may differ. */
    public String key() {
        return source + " | " + layerName + " | " + (texture == null ? "-" : texture);
    }
}
