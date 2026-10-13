package dev.eann.atlasvfx.cue;

import dev.eann.atlasvfx.telegraph.TelegraphArea;
import dev.eann.atlasvfx.telegraph.TelegraphStyles;
import dev.eann.atlasvfx.telegraph.TelegraphTiming;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cues on the wire, and the client's registries of how to draw them. */
class FxCueTest {

    private static final Identifier SCHOOL = Identifier.of("testmod", "frost");

    private static RegistryByteBuf buffer() {
        return new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
    }

    @Test
    @DisplayName("A cue survives the trip to the client exactly")
    void cueRoundTrips() {
        FxCuePayload sent = new FxCuePayload(FxCues.CONE,
                FxCueArgs.cone(SCHOOL, new Vec3d(1.5, 64.25, -7.0), new Vec3d(0.0, 0.0, 1.0), 35.0f, 6.0f, 2,
                        123456789L).following(42));
        RegistryByteBuf buffer = buffer();
        FxCuePayload.CODEC.encode(buffer, sent);
        FxCuePayload received = FxCuePayload.CODEC.decode(buffer);
        assertEquals(sent, received);
        assertEquals(0, buffer.readableBytes(), "bytes left over");

        FxCuePayload unfollowed = new FxCuePayload(FxCues.PULSE, FxCueArgs.at(SCHOOL, Vec3d.ZERO, 1.0f, 0, 7L));
        buffer = buffer();
        FxCuePayload.CODEC.encode(buffer, unfollowed);
        assertEquals(-1, FxCuePayload.CODEC.decode(buffer).args().entityId());
    }

    @Test
    @DisplayName("Every shape of telegraph survives the trip, with its timing, style and colour")
    void telegraphsRoundTrip() {
        Vec3d at = new Vec3d(10.0, 70.0, -3.5);
        TelegraphTiming timing = new TelegraphTiming(5000L, 5040L, 8);
        List<TelegraphArea> areas = List.of(
                new TelegraphArea.Circle(at, 3.0f),
                new TelegraphArea.Ring(at, 2.0f, 5.0f),
                new TelegraphArea.Cone(at, 135.0f, 7.0f, 40.0f),
                new TelegraphArea.Line(at, -45.0f, 12.0f, 1.25f));
        for (TelegraphArea area : areas) {
            TelegraphPayload sent = new TelegraphPayload(area, timing, FxCues.WARNING, 0x40C0FF);
            RegistryByteBuf buffer = buffer();
            TelegraphPayload.CODEC.encode(buffer, sent);
            assertEquals(sent, TelegraphPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over for " + area);
        }
    }

    @Test
    @DisplayName("An unregistered style draws in the default look, a registered one in its own")
    void stylesFallBack() {
        assertSame(FxCueStyles.DEFAULT, FxCueStyles.style(Identifier.of("testmod", "nobody_registered_this")));
        assertSame(TelegraphStyles.DANGER, FxCueStyles.telegraph(Identifier.of("testmod", "nor_this")));
        assertSame(TelegraphStyles.DANGER, FxCueStyles.telegraph(FxCues.DANGER));

        FxCueStyles.register(SCHOOL, FxCueStyles.style(0x80D0FF));
        assertEquals(0x80D0FF, FxCueStyles.style(SCHOOL).glow());
        assertSame(FxCueStyles.telegraph(0x123456), FxCueStyles.telegraph(0x123456), "one style per colour");
    }

    @Test
    @DisplayName("A registered cue is drawn by its handler; an unknown one draws nothing")
    void handlersDispatch() {
        Identifier bespoke = Identifier.of("testmod", "bespoke");
        FxCueArgs[] seen = new FxCueArgs[1];
        FxCueHandlers.register(bespoke, (world, args) -> seen[0] = args);
        FxCueArgs args = FxCueArgs.at(SCHOOL, new Vec3d(1.0, 2.0, 3.0), 2.0f, 1, 99L);
        assertTrue(FxCueHandlers.play(bespoke, args));
        assertEquals(args, seen[0]);
        assertFalse(FxCueHandlers.play(Identifier.of("testmod", "unknown"), args));
    }

    @Test
    @DisplayName("Registrations from many threads at once are all kept, as on NeoForge's parallel start-up")
    void parallelRegistrationsAreKept() throws Exception {
        int threads = 8;
        int each = 250;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        List<java.util.concurrent.Future<?>> jobs = new java.util.ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            int t = thread;
            jobs.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < each; i++) {
                    Identifier name = Identifier.of("parallel", t + "_" + i);
                    FxCueHandlers.register(name, (world, args) -> { });
                    FxCueStyles.register(name, FxCueStyles.DEFAULT);
                }
                return null;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> job : jobs) {
            job.get();
        }
        pool.shutdown();
        FxCueArgs args = FxCueArgs.at(SCHOOL, Vec3d.ZERO, 1.0f, 0, 1L);
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < each; i++) {
                Identifier name = Identifier.of("parallel", t + "_" + i);
                assertTrue(FxCueHandlers.play(name, args), "lost the handler for " + name);
            }
        }
    }

    @Test
    @DisplayName("A direction's yaw is the game's: south 0, west 90, east -90")
    void yawMatchesTheGame() {
        assertEquals(0.0f, FxCueHandlers.yawOf(new Vec3d(0.0, 0.0, 1.0)), 1.0e-4f);
        assertEquals(90.0f, FxCueHandlers.yawOf(new Vec3d(-1.0, 0.0, 0.0)), 1.0e-4f);
        assertEquals(-90.0f, FxCueHandlers.yawOf(new Vec3d(1.0, 0.0, 0.0)), 1.0e-4f);
    }
}
