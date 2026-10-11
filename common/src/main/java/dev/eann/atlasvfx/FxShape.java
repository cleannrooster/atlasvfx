package dev.eann.atlasvfx;

import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;

/**
 * A shape of light out in the world that lives a handful of ticks: a pulse, a nova, a beam, a
 * crack in the ground.
 *
 * <p>Give one to {@link FxWorld}, which keeps it, ticks it and draws it until its life is up.
 * A shape keeps no age of its own; the world hands it one. It draws only through the painter,
 * which walks it once per {@link FxPass}, so it should put down everything it is every time it
 * is asked and let the painter keep what belongs to the pass.</p>
 */
public interface FxShape {

    /** How many ticks it lives. */
    int life();

    /**
     * What it throws off the moment it appears, when {@link FxWorld#play played} rather than
     * merely {@link FxWorld#add added}.
     *
     * @param density how much to make, as a share
     */
    default void open(FxWorld world, float density) {
    }

    /**
     * What it keeps throwing off while it lives; once a tick, before its age moves on.
     *
     * @param age     ticks since it appeared
     * @param density how much to make, as a share
     */
    default void tick(FxWorld world, int age, float density) {
    }

    /**
     * Puts it down for this frame.
     *
     * @param world the world, for the light lit pieces are drawn in
     * @param age   ticks since it appeared; the frame's fraction is {@code tickDelta}
     */
    void draw(FxPainter painter, @Nullable ClientWorld world, int age, float tickDelta);

    /** How far through a life of {@code life} ticks it is, 0 to 1, with the frame's fraction. */
    static float progress(int age, float tickDelta, int life) {
        return Math.max(0.0f, Math.min(1.0f, (age + tickDelta) / life));
    }
}
