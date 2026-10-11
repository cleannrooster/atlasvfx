package dev.eann.atlasvfx;

import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shapes' lifetimes as the world keeps them, and what their handles do to them. */
class FxHandleTest {

    private static final float EPSILON = 1.0e-5f;

    /** Every effect at full, without asking a game that is not running for its particle setting. */
    private static final FxDensity FULL = new FxDensity() {
        @Override
        public float share() {
            return 1.0f;
        }

        @Override
        public float effective() {
            return 1.0f;
        }
    };

    private final FxWorld world = FxWorld.get();

    /** Lasts until let go of, then a few ticks more; remembers what it saw. */
    private static final class Lingering implements FxShape {
        static final int FADE = 3;
        int ticks;
        @Nullable FxLife seen;

        @Override
        public boolean done(FxLife life) {
            return life.released() && life.age() - life.releasedAt() >= FADE;
        }

        @Override
        public void tick(FxWorld world, FxLife life, float density) {
            ticks++;
            seen = life;
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, FxLife life, float tickDelta) {
        }
    }

    /** Lasts a fixed number of ticks. */
    private record Brief(int ticks) implements FxShape.Timed {
        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, FxLife life, float tickDelta) {
        }
    }

    @BeforeEach
    @AfterEach
    void empty() {
        world.clear();
    }

    @Test
    @DisplayName("A timed shape ends after exactly its ticks, and its handle says so")
    void timedShapeEndsOnTime() {
        FxHandle handle = world.add(new Brief(3), FULL);
        for (int i = 0; i < 2; i++) {
            world.advanceShapes();
            assertTrue(handle.alive(), "ended early at tick " + (i + 1));
        }
        world.advanceShapes();
        assertFalse(handle.alive());
    }

    @Test
    @DisplayName("An open-ended shape lives until released, then fades out over its own ticks")
    void releaseEndsAnOpenShape() {
        Lingering shape = new Lingering();
        FxHandle handle = world.add(shape, FULL);
        for (int i = 0; i < 50; i++) {
            world.advanceShapes();
        }
        assertTrue(handle.alive(), "nothing told it to end");

        handle.release();
        // The tick the release lands in, then the whole fade after it - so a fade drawn from
        // sinceRelease reaches its end on the shape's last tick, not one short of it.
        for (int i = 0; i <= Lingering.FADE; i++) {
            assertTrue(handle.alive(), "cut short at fade tick " + i);
            world.advanceShapes();
        }
        assertFalse(handle.alive());
        assertEquals(50 + Lingering.FADE + 1, shape.ticks);
    }

    @Test
    @DisplayName("Cancelling ends a shape at once, without another tick")
    void cancelIsImmediate() {
        Lingering shape = new Lingering();
        FxHandle handle = world.add(shape, FULL);
        world.advanceShapes();
        handle.cancel();
        assertFalse(handle.alive());
        world.advanceShapes();
        assertEquals(1, shape.ticks, "a cancelled shape was ticked again");
    }

    @Test
    @DisplayName("A release or flash during a tick reads as just happened on the next frame")
    void stampsLeadByATick() {
        Lingering shape = new Lingering();
        FxHandle handle = world.add(shape, FULL);
        world.advanceShapes();
        handle.release();
        handle.flash();
        world.advanceShapes();
        FxLife life = shape.seen;
        assertTrue(life != null && life.released());
        // The world has aged it past the stamp: the first frame sees nothing elapsed, then it counts.
        assertEquals(0.0f, life.sinceRelease(0.0f), EPSILON);
        assertEquals(0.5f, life.sinceRelease(0.5f), EPSILON);
        assertEquals(1.0f, life.flash(0.0f, 4.0f), EPSILON);
        assertEquals(0.75f, life.flash(1.0f, 4.0f), EPSILON);
    }

    @Test
    @DisplayName("Handles to shapes that have ended, or were cleared away, do nothing")
    void endedHandlesAreInert() {
        FxHandle handle = world.add(new Brief(1), FULL);
        world.advanceShapes();
        assertFalse(handle.alive());
        handle.release();
        handle.flash();
        handle.cancel();
        assertFalse(handle.alive());

        FxHandle cleared = world.add(new Lingering(), FULL);
        world.clear();
        assertFalse(cleared.alive());
    }

    @Test
    @DisplayName("A shape that was never flashed shows no flash")
    void noFlashUnlessAsked() {
        Lingering shape = new Lingering();
        world.add(shape, FULL);
        world.advanceShapes();
        world.advanceShapes();
        assertEquals(0.0f, shape.seen.flash(0.5f, 4.0f), EPSILON);
        assertFalse(shape.seen.released());
        assertEquals(-1, shape.seen.releasedAt());
    }
}
