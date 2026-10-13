package dev.eann.atlasvfx.character;

import net.minecraft.client.render.VertexConsumer;

/**
 * Passes every vertex on with its colour multiplied by a tint, alpha included: how a look makes a
 * body see-through or coloured without touching its geometry. Only handed out for a tinted swap.
 */
final class TintingConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private final int argb;

    TintingConsumer(VertexConsumer delegate, int argb) {
        this.delegate = delegate;
        this.argb = argb;
    }

    /** {@code color}, ARGB, multiplied by {@code tint}, ARGB. */
    static int multiply(int color, int tint) {
        int a = (color >>> 24) * (tint >>> 24) / 255;
        int r = (color >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
        int g = (color >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
        int b = (color & 0xFF) * (tint & 0xFF) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                       float normalX, float normalY, float normalZ) {
        delegate.vertex(x, y, z, multiply(color, argb), u, v, overlay, light, normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        int tinted = multiply(alpha << 24 | red << 16 | green << 8 | blue, argb);
        delegate.color(tinted >> 16 & 0xFF, tinted >> 8 & 0xFF, tinted & 0xFF, tinted >>> 24);
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        delegate.texture(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        delegate.overlay(u, v);
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        delegate.light(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }
}
