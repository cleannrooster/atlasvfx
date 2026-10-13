package dev.eann.atlasvfx.parity;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One capture while it records: every layer asked for, by whom, what it was drawn into if a
 * substitution swapped it, and a counter on what went into it. Holds the layers themselves only
 * until {@link #uses}, which turns them into plain values. Render thread only.
 */
final class ParitySession {

    // Layers are held as Objects only so the session can be tested without a game, where no
    // RenderLayer can be made; in play they always are.
    private record Request(Object layer, @Nullable Object drawnAs, LayerSource source, CountingConsumer counter) {
    }

    private final List<Request> requests = new ArrayList<>();

    /**
     * Notes a request for {@code layer} and hands back what to give the caller instead of
     * {@code buffer}.
     *
     * @param drawnAs what the layer was swapped for, or null if it was drawn as asked
     */
    VertexConsumer record(RenderLayer layer, @Nullable RenderLayer drawnAs, LayerSource source,
                          VertexConsumer buffer) {
        return recordAny(layer, drawnAs, source, buffer);
    }

    /** What a hidden layer reports it was drawn as. */
    static final String HIDDEN = "(hidden)";

    /** Notes a request for {@code layer} that a replacement left undrawn. */
    VertexConsumer recordHidden(RenderLayer layer, LayerSource source, VertexConsumer buffer) {
        return recordAny(layer, HIDDEN, source, buffer);
    }

    VertexConsumer recordAny(Object layer, @Nullable Object drawnAs, LayerSource source, VertexConsumer buffer) {
        CountingConsumer counter = new CountingConsumer(buffer);
        requests.add(new Request(layer, drawnAs, source, counter));
        return counter;
    }

    int size() {
        return requests.size();
    }

    /** The requests as plain values, classified. Forgets the layers. */
    List<LayerUse> uses(Function<Object, LayerTraits> read) {
        List<LayerUse> uses = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            Request request = requests.get(i);
            LayerTraits traits = read.apply(request.layer());
            LayerClassifier.Verdict verdict = LayerClassifier.classify(traits);
            String drawnAs = request.drawnAs() == null ? null
                    : request.drawnAs() instanceof String text ? text : read.apply(request.drawnAs()).name();
            uses.add(new LayerUse(i, traits.name(), traits.format(), traits.mode(),
                    traits.texture() == null ? null : traits.texture().toString(), request.source().label(),
                    verdict.layerClass(), verdict.reason(), request.counter().vertices(), drawnAs));
        }
        requests.clear();
        return uses;
    }
}
