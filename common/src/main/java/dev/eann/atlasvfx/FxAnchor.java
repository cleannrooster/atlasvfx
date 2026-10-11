package dev.eann.atlasvfx;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Something a mote belongs to and is drawn round, wherever it is that frame: a hand, a staff's
 * tip, a point on a moving body.
 *
 * <p>An anchored mote is kept as an offset from its anchor and drawn wherever the anchor is when
 * the frame is drawn, so it follows a waving arm without trailing behind it. Anchors that are the
 * same thing should be equal, so a frame works out where each one is only once however many
 * motes ask.</p>
 */
public interface FxAnchor {

    /**
     * Whether it still holds its motes. Once it lets go they fade out quickly rather than
     * vanishing.
     */
    boolean held();

    /**
     * Whether it is drawn into the first-person view rather than out in the world. Its motes are
     * drawn only by a painter for the same one.
     */
    boolean inView();

    /**
     * Where it is this frame, in the space its painter draws in: the world, or relative to the
     * eye in the first-person view. Null when it cannot be found, and its motes are not drawn.
     */
    @Nullable
    Vec3d position(float tickDelta);
}
