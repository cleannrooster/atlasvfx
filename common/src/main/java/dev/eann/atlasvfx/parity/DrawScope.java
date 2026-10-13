package dev.eann.atlasvfx.parity;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Who is asking for layers right now, while the provider seam has an entity wrapped: the
 * dispatcher, the entity's model, one of its feature renderers, or a first-person arm. Read by a
 * capture to attribute each layer, and by substitution to tell which body part a layer is for.
 *
 * <p>Only kept while something is wrapped: outside that, the hooks that would push to it do not
 * build a source at all. Render thread only.</p>
 */
@ApiStatus.Internal
public final class DrawScope {

    private static final Deque<LayerSource> SOURCES = new ArrayDeque<>();

    private DrawScope() {
    }

    /** Whether an entity is wrapped, so sources are worth keeping. */
    public static boolean active() {
        return !SOURCES.isEmpty();
    }

    /** Layers asked for from here until {@link #exit} are {@code source}'s. */
    public static void enter(LayerSource source) {
        SOURCES.push(source);
    }

    /** Ends the latest {@link #enter}. An unbalanced exit is ignored rather than thrown mid-frame. */
    public static void exit() {
        SOURCES.poll();
    }

    /** Who is asking now; null when nothing is wrapped. */
    public static @Nullable LayerSource current() {
        return SOURCES.peek();
    }

    /** Forgets everything: for tests, and for recovering from a frame that threw. */
    static void reset() {
        SOURCES.clear();
    }
}
