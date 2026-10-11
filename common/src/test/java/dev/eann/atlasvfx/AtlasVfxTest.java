package dev.eann.atlasvfx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arithmetic and bookkeeping under the effects: frames, easing, pass order and the budget. */
class AtlasVfxTest {

    private static final float EPSILON = 1.0e-3f;

    @Test
    @DisplayName("A picture flips through every frame and ends on its last")
    void framesCoverTheRun() {
        assertEquals(0, FxSprite.frameIndex(0.0f, 8));
        assertEquals(7, FxSprite.frameIndex(1.0f, 8));
        assertEquals(3, FxSprite.frameIndex(0.45f, 8));
        assertEquals(0, FxSprite.frameIndex(-1.0f, 8));
        assertEquals(0, FxSprite.frameIndex(0.7f, 1));
    }

    @Test
    @DisplayName("A nova's ring starts near the middle, only ever runs outward, and reaches its edge")
    void novaRunsOutToItsEdge() {
        assertTrue(FxShapes.novaReach(0.0f) < 0.1f);
        assertEquals(1.0f, FxShapes.novaReach(1.0f), EPSILON);
        float previous = 0.0f;
        for (int i = 0; i <= 20; i++) {
            float now = FxShapes.novaReach(i / 20.0f);
            assertTrue(now >= previous, "the ring ran back in at " + i);
            previous = now;
        }
        assertTrue(FxShapes.novaReach(0.5f) > 0.8f);
    }

    @Test
    @DisplayName("Passes run lit pictures, then the End, then light, with glow last")
    void passesAreOrdered() {
        List<FxPass> passes = FxPass.ordered();
        int atlases = AtlasRef.values().size();
        assertEquals(2 * atlases + 2, passes.size());
        for (int i = 0; i < atlases; i++) {
            assertSame(FxPass.Kind.LIT, passes.get(i).kind());
        }
        assertSame(FxPass.RIFT, passes.get(atlases));
        for (int i = 0; i < atlases; i++) {
            assertSame(FxPass.Kind.EMISSIVE, passes.get(atlases + 1 + i).kind());
        }
        assertSame(FxPass.GLOW, passes.get(passes.size() - 1));
    }

    @Test
    @DisplayName("Each blend and atlas has exactly one pass, so passes compare by identity")
    void passesAreCanonical() {
        for (AtlasRef atlas : AtlasRef.values()) {
            assertSame(FxPass.lit(atlas), FxPass.of(FxBlend.LIT, atlas));
            assertSame(FxPass.emissive(atlas), FxPass.of(FxBlend.EMISSIVE, atlas));
            assertSame(FxPass.GLOW, FxPass.of(FxBlend.GLOW, atlas));
            assertSame(atlas, FxPass.lit(atlas).atlas());
        }
    }

    @Test
    @DisplayName("The budget fills to its ceiling, refuses past it, and frees what is given back")
    void budgetIsBounded() {
        int taken = 0;
        while (FxBudget.tryReserve()) {
            taken++;
        }
        try {
            assertEquals(FxBudget.MAX_MOTES, FxBudget.live());
            assertFalse(FxBudget.hasRoom());
            FxBudget.release(1);
            assertTrue(FxBudget.hasRoom());
            assertTrue(FxBudget.tryReserve());
        } finally {
            FxBudget.release(FxBudget.live());
        }
        assertEquals(FxBudget.MAX_MOTES, taken);
        assertEquals(0, FxBudget.live());
    }
}
