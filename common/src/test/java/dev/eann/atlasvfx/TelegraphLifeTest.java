package dev.eann.atlasvfx;

import dev.eann.atlasvfx.telegraph.Telegraph;
import dev.eann.atlasvfx.telegraph.TelegraphAnchor;
import dev.eann.atlasvfx.telegraph.TelegraphArea;
import dev.eann.atlasvfx.telegraph.TelegraphStyles;
import dev.eann.atlasvfx.telegraph.TelegraphTiming;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A telegraph's life as the world keeps it: landing on the world's clock, released early, locking. */
class TelegraphLifeTest {

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
    private long now;

    /** Ticks the world's clock and the effects once, the way a client tick does. */
    private void tick() {
        now++;
        world.advanceShapes();
    }

    @BeforeEach
    @AfterEach
    void empty() {
        world.clear();
        now = 1000L;
    }

    @Test
    @DisplayName("It lands on the world's tick and is gone a fade later, however late it was started")
    void landsOnTheClock() {
        TelegraphTiming timing = new TelegraphTiming(990L, 1010L, 5);
        Telegraph telegraph = Telegraph.withClock(new TelegraphArea.Circle(Vec3d.ZERO, 2.0f), timing,
                TelegraphStyles.DANGER, null, Long.MAX_VALUE, () -> now);
        FxHandle handle = world.add(telegraph, FULL);
        while (now < 1014L) {
            tick();
            assertTrue(handle.alive(), "gone early at " + now);
        }
        tick();
        assertFalse(handle.alive());
    }

    @Test
    @DisplayName("Released, it lands at once and fades from there")
    void releaseLandsNow() {
        TelegraphTiming timing = TelegraphTiming.starting(now, 100, 4);
        Telegraph telegraph = Telegraph.withClock(new TelegraphArea.Circle(Vec3d.ZERO, 2.0f), timing,
                TelegraphStyles.DANGER, null, Long.MAX_VALUE, () -> now);
        FxHandle handle = world.add(telegraph, FULL);
        tick();
        tick();
        handle.release();
        tick();
        assertEquals(now, telegraph.trigger());
        long landed = now;
        while (handle.alive()) {
            tick();
        }
        assertEquals(landed + 4, now);
    }

    @Test
    @DisplayName("It follows its anchor until it locks, then stays where it was")
    void followsThenLocks() {
        Vec3d[] where = {new Vec3d(0.0, 64.0, 0.0)};
        float[] facing = {0.0f};
        TelegraphAnchor anchor = new TelegraphAnchor() {
            @Override
            @Nullable
            public Vec3d position(float tickDelta) {
                return where[0];
            }

            @Override
            public float yaw(float tickDelta) {
                return facing[0];
            }
        };
        TelegraphTiming timing = TelegraphTiming.starting(now, 20, 4);
        Telegraph telegraph = Telegraph.withClock(new TelegraphArea.Cone(Vec3d.ZERO, 0.0f, 5.0f, 30.0f), timing,
                TelegraphStyles.DANGER, anchor, now + 10, () -> now);
        world.add(telegraph, FULL);

        where[0] = new Vec3d(5.0, 64.0, 5.0);
        facing[0] = 90.0f;
        tick();
        assertFalse(telegraph.locked());
        assertEquals(where[0], telegraph.area().origin());
        assertTrue(telegraph.area().containsFootprint(1.0, 5.0), "should face west now");

        while (now < 1010L) {
            tick();
        }
        assertTrue(telegraph.locked());
        Vec3d lockedAt = telegraph.area().origin();
        where[0] = new Vec3d(50.0, 64.0, 50.0);
        tick();
        assertEquals(lockedAt, telegraph.area().origin(), "moved after locking");
    }
}
