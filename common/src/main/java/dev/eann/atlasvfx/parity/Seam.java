package dev.eann.atlasvfx.parity;

import dev.eann.atlasvfx.character.BodyPart;
import dev.eann.atlasvfx.character.BodyParts;
import dev.eann.atlasvfx.character.Substitution;
import dev.eann.atlasvfx.character.Swap;
import dev.eann.atlasvfx.model.ModelReplacement;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * What a seam provider does with each request for a layer while it stands in for an entity's: swap
 * it if the entity wears a look and the layer is part of its body, then record it if a capture is
 * running. Shared by the two providers that can stand in, the batching one and any other.
 */
final class Seam {

    final Entity owner;
    final VertexConsumerProvider delegate;
    final @Nullable ParitySession session;
    final @Nullable Substitution look;
    final @Nullable ModelReplacement replacement;

    Seam(Entity owner, VertexConsumerProvider delegate, @Nullable ParitySession session,
         @Nullable Substitution look, @Nullable ModelReplacement replacement) {
        this.owner = owner;
        this.delegate = delegate;
        this.session = session;
        this.look = look;
        this.replacement = replacement;
    }

    VertexConsumer getBuffer(RenderLayer layer) {
        LayerSource source = DrawScope.current();
        if (replacement != null) {
            BodyPart part = BodyParts.of(source);
            if (part != null && replacement.hides(part)) {
                // A part the replacement stands in for: every layer of it, glint and all, goes
                // unseen. Its geometry is still built by its own renderer, and dropped here. A new
                // one each time: the game joins two buffers for a glint, and refuses to join one
                // buffer with itself.
                VertexConsumer hidden = new Hidden();
                return session != null && source != null ? session.recordHidden(layer, source, hidden) : hidden;
            }
        }
        Swap swap = look == null ? null : swapFor(layer, source);
        VertexConsumer out;
        RenderLayer drawn = null;
        if (swap == null) {
            out = delegate.getBuffer(layer);
        } else {
            drawn = swap.layer();
            out = swap.apply(delegate.getBuffer(drawn));
            // A glowing body is drawn through the outline provider, which outlines a layer by what
            // the layer says its outline is. The swapped layer may have none; the outline the body
            // asked for is kept by drawing into both, the way the outline provider itself does.
            if (delegate instanceof OutlineVertexConsumerProvider) {
                Optional<RenderLayer> outline = layer.getAffectedOutline();
                if (outline.isPresent()) {
                    out = VertexConsumers.union(delegate.getBuffer(outline.get()), out);
                }
            }
        }
        if (session != null && source != null) {
            out = session.record(layer, drawn, source, out);
        }
        return out;
    }

    private @Nullable Swap swapFor(RenderLayer layer, @Nullable LayerSource source) {
        BodyPart part = BodyParts.of(source);
        if (part == null) {
            return null;
        }
        LayerVerdicts.Entry entry = LayerVerdicts.of(layer);
        LayerClass layerClass = entry.verdict().layerClass();
        if (layerClass != LayerClass.SURFACE && layerClass != LayerClass.TRANSLUCENT_SURFACE) {
            return null;
        }
        return look.swap(part, entry.traits());
    }

    /** Takes vertices and keeps none. */
    static final class Hidden implements VertexConsumer {

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                           float normalX, float normalY, float normalZ) {
        }
    }

    /** A provider standing in for an entity's while the seam has it wrapped. */
    interface Holder {
        Seam seam();
    }

    /** Stands in for anything but the game's batching provider - the outline provider, a mod's own. */
    static final class Plain implements VertexConsumerProvider, Holder {
        private final Seam seam;

        Plain(Seam seam) {
            this.seam = seam;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            return seam.getBuffer(layer);
        }

        @Override
        public Seam seam() {
            return seam;
        }
    }
}
