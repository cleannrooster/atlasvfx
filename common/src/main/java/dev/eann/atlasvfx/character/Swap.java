package dev.eann.atlasvfx.character;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;

/**
 * What a substituted layer is drawn into instead, and in what colour.
 *
 * @param argbTint multiplies every vertex's colour, alpha included; {@link #NO_TINT} leaves the
 *                 colours as the model gave them, and is the only tint that keeps the buffer's own
 *                 fast path
 */
public record Swap(RenderLayer layer, int argbTint) {

    public static final int NO_TINT = 0xFFFFFFFF;

    public static Swap of(RenderLayer layer) {
        return new Swap(layer, NO_TINT);
    }

    public boolean tinted() {
        return argbTint != NO_TINT;
    }

    /** What to hand a renderer for this swap's buffer: the buffer, or the buffer tinted. */
    public VertexConsumer apply(VertexConsumer buffer) {
        return tinted() ? new TintingConsumer(buffer, argbTint) : buffer;
    }
}
