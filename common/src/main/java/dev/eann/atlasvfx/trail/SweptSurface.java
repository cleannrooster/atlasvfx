package dev.eann.atlasvfx.trail;

import org.joml.Vector3f;

/**
 * A surface a trail is drawn on, given as a function of two fractions: {@code s} along it, from
 * its start to its end, and {@code t} across it, from its inner edge to its outer one.
 *
 * <p>For a swing, {@code s} runs through the swing and {@code t} out along the blade, so a point
 * is where the blade's {@code t} was at the moment {@code s} of the way through. Where a swing's
 * damage is described by the same function, the trail is drawn exactly over what can be hit.</p>
 */
public interface SweptSurface {

    /** Works out where it is this frame; false when there is nothing to draw. */
    boolean prepare(float tickDelta);

    /** The point at {@code s} and {@code t}, both 0 to 1, into {@code out} as x, y, z in the world. */
    void point(float s, float t, double[] out);

    /** Its normal this frame, a unit vector: the way a slab drawn on it has thickness. */
    Vector3f normal();

    /** How far out its outer edge is from its inner origin, in blocks. */
    float reach();

    /** Whether its ends meet - a full circle - so it has no ends to shape. */
    boolean closed();

    /** Whether it is the viewer's own, seen from their own eyes. */
    boolean viewer();
}
