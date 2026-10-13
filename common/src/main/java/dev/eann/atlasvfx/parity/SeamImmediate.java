package dev.eann.atlasvfx.parity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;

import java.util.LinkedHashMap;

/**
 * Stands in for the game's batching provider while the seam has an entity wrapped, and is one
 * itself: renderers that check for {@link VertexConsumerProvider.Immediate} to flush a layer early
 * still find one, and every flush goes to the real provider.
 *
 * <p>The buffers the superclass is built with are never used - every method that would touch them
 * is passed on - so they share one small allocation for the life of the game.</p>
 */
final class SeamImmediate extends VertexConsumerProvider.Immediate implements Seam.Holder {

    private static BufferAllocator unused;

    private final VertexConsumerProvider.Immediate delegate;
    private final Seam seam;

    SeamImmediate(VertexConsumerProvider.Immediate delegate, Seam seam) {
        super(unusedAllocator(), new LinkedHashMap<>());
        this.delegate = delegate;
        this.seam = seam;
    }

    private static BufferAllocator unusedAllocator() {
        if (unused == null) {
            unused = new BufferAllocator(256);
        }
        return unused;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return seam.getBuffer(layer);
    }

    @Override
    public void drawCurrentLayer() {
        delegate.drawCurrentLayer();
    }

    @Override
    public void draw() {
        delegate.draw();
    }

    @Override
    public void draw(RenderLayer layer) {
        delegate.draw(layer);
    }

    @Override
    public Seam seam() {
        return seam;
    }
}
