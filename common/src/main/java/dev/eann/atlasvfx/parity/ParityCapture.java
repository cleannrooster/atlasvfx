package dev.eann.atlasvfx.parity;

import dev.architectury.platform.Platform;
import dev.eann.atlasvfx.WorldRenderHook;
import dev.eann.atlasvfx.compat.IrisCompat;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * One capture at a time: armed for an entity, it records the next time that entity - or its
 * first-person arm - is drawn in the main pass, then hands back a report at the end of that frame.
 * If the entity is not drawn within the frames it was given, it hands back a timeout instead.
 *
 * <p>Arming is the only thing that costs anything: until then the provider seam reads one field
 * and passes every provider on as it is.</p>
 */
@ApiStatus.Internal
public final class ParityCapture {

    public enum Mode {
        /** The entity as the world draws it: model, features, shadow, outline. */
        ENTITY,
        /** The local player's first-person arm, drawn when the hand is empty or holds a map. */
        ARM
    }

    /** What a capture ended with. */
    public sealed interface Outcome {
        record Captured(ParityReport report) implements Outcome {
        }

        record TimedOut(Mode mode, int frames) implements Outcome {
        }
    }

    private record Request(int entityId, Mode mode, int timeoutFrames, Consumer<Outcome> done) {
    }

    private static volatile @Nullable Request armed;
    private static int waited;
    private static @Nullable ParitySession recording;
    private static @Nullable String recordingEntity;
    private static @Nullable String recordingProvider;
    private static @Nullable Outcome pending;
    private static boolean installed;

    private ParityCapture() {
    }

    /** Hooks the frame counter in. Called once by the library's client start. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        WorldRenderHook.AFTER_WEATHER.register(context -> frame());
    }

    /**
     * Arms a capture of {@code entityId}, replacing any armed one. {@code done} is called on the
     * render thread at the end of the frame the capture finished or gave up in.
     */
    public static void arm(int entityId, Mode mode, int timeoutFrames, Consumer<Outcome> done) {
        waited = 0;
        armed = new Request(entityId, mode, Math.max(1, timeoutFrames), done);
    }

    static boolean armed() {
        return armed != null;
    }

    static @Nullable ParitySession recording() {
        return recording;
    }

    /**
     * Starts recording if {@code entity} is what is armed, in {@code mode}, and this is the main
     * pass: the shadow pass under a shader pack draws the same entity again, from the sun.
     */
    static @Nullable ParitySession begin(Entity entity, Mode mode, VertexConsumerProvider provider) {
        Request request = armed;
        if (request == null || recording != null || request.mode() != mode || request.entityId() != entity.getId()
                || IrisCompat.renderingShadowPass()) {
            return null;
        }
        recording = new ParitySession();
        recordingEntity = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        recordingProvider = provider.getClass().getName();
        return recording;
    }

    /** Ends {@code session}, if it is the one recording. The report waits for the end of the frame. */
    static void end(ParitySession session) {
        Request request = armed;
        if (session != recording || request == null) {
            return;
        }
        List<LayerUse> uses = session.uses(layer -> LayerTraits.of((net.minecraft.client.render.RenderLayer) layer));
        ParityReport report = new ParityReport(recordingEntity, request.mode().name().toLowerCase(java.util.Locale.ROOT),
                Platform.isNeoForge() ? "neoforge" : "fabric", IrisCompat.loaded(), IrisCompat.shaderPackInUse(),
                Platform.isModLoaded("sodium"), recordingProvider, uses);
        recording = null;
        recordingEntity = null;
        recordingProvider = null;
        pending = new Outcome.Captured(report);
    }

    /** Once a frame: delivers what finished, or gives up on what took too long. */
    static void frame() {
        Request request = armed;
        if (request == null) {
            return;
        }
        Outcome outcome = pending;
        if (outcome == null && recording == null && ++waited >= request.timeoutFrames()) {
            outcome = new Outcome.TimedOut(request.mode(), request.timeoutFrames());
        }
        if (outcome == null) {
            return;
        }
        pending = null;
        armed = null;
        request.done().accept(outcome);
    }
}
