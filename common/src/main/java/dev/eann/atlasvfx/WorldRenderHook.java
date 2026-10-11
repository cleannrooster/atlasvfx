package dev.eann.atlasvfx;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

/**
 * "The world has finished drawing its translucent geometry" - the one rendering hook effects
 * drawn in the world need and Architectury does not bridge.
 *
 * <p>Fabric has {@code WorldRenderEvents.AFTER_TRANSLUCENT} and NeoForge has
 * {@code RenderLevelStageEvent}; the two carry the same information in different shapes, so
 * each loader module adapts its own into this event and everything drawn from it stays common.</p>
 */
@FunctionalInterface
public interface WorldRenderHook {

    Event<WorldRenderHook> AFTER_TRANSLUCENT = EventFactory.createLoop();

    void render(Context context);

    /**
     * Everything the circle and ground renderers read out of a frame. Deliberately the
     * smallest set that satisfies both of them: widening it would mean finding a NeoForge
     * equivalent for each addition.
     */
    record Context(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera,
                   float tickDelta) {
    }
}
