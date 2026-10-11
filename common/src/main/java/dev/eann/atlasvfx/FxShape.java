package dev.eann.atlasvfx;

import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;

/**
 * A shape of light out in the world: a pulse, a nova, a beam, a crack in the ground, a trail that
 * lasts as long as what it follows.
 *
 * <p>Give one to {@link FxWorld}, which keeps it, ticks it and draws it until it says it is
 * {@link #done done}. A shape keeps no clock of its own; the world hands it its {@link FxLife}.
 * It draws only through the painter, which walks it once per {@link FxPass}, so it should put
 * down everything it is every time it is asked and let the painter keep what belongs to the
 * pass.</p>
 *
 * <p>Most shapes last a fixed number of ticks; those implement {@link Timed}.</p>
 */
public interface FxShape {

    /** Whether it has finished, checked once a tick after its age moves on. */
    boolean done(FxLife life);

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
     * @param density how much to make, as a share
     */
    default void tick(FxWorld world, FxLife life, float density) {
    }

    /**
     * Puts it down for this frame.
     *
     * @param world the world, for the light lit pieces are drawn in
     */
    void draw(FxPainter painter, @Nullable ClientWorld world, FxLife life, float tickDelta);

    /** A shape that lasts a fixed number of ticks, whatever happens to it. */
    interface Timed extends FxShape {

        /** How many ticks it lives. */
        int ticks();

        @Override
        default boolean done(FxLife life) {
            return life.age() >= ticks();
        }
    }
}
