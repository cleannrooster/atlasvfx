package dev.eann.atlasvfx.parity;

import net.minecraft.client.render.VertexConsumer;

/**
 * Passes every vertex on unchanged and counts them. Only ever handed out during a capture: it
 * hides the buffer behind it, so a renderer's fast path to that buffer (Sodium's, say) falls back
 * to the ordinary calls for the one frame recorded.
 */
final class CountingConsumer implements VertexConsumer {

    private final VertexConsumer delegate;
    private int vertices;

    CountingConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    int vertices() {
        return vertices;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        vertices++;
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                       float normalX, float normalY, float normalZ) {
        vertices++;
        delegate.vertex(x, y, z, color, u, v, overlay, light, normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, alpha);
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
