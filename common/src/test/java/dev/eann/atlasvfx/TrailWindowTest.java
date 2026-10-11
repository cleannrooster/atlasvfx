package dev.eann.atlasvfx;

import dev.eann.atlasvfx.trail.TrailWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A swing's window: cut through, held, faded, and cut short by a release. */
class TrailWindowTest {

    private static final float EPSILON = 1.0e-5f;

    private static FxLife aged(int ticks) {
        FxLife life = new FxLife();
        for (int i = 0; i < ticks; i++) {
            life.advance();
        }
        return life;
    }

    @Test
    @DisplayName("The head runs through the swing with the whole arc kept behind it, then it fades")
    void swingCutsThenFades() {
        TrailWindow window = TrailWindow.swing(8, 1.0f, 4);
        assertEquals(0.0f, window.head(aged(0), 0.0f), EPSILON);
        assertEquals(0.5f, window.head(aged(4), 0.0f), EPSILON);
        assertEquals(1.0f, window.head(aged(8), 0.0f), EPSILON);
        assertEquals(0.0f, window.tail(aged(6), 0.0f), EPSILON);
        assertEquals(1.0f, window.fade(aged(7), 0.5f), EPSILON);
        assertEquals(0.5f, window.fade(aged(10), 0.0f), EPSILON);
        assertFalse(window.done(aged(11)));
        assertTrue(window.done(aged(12)));
    }

    @Test
    @DisplayName("A short trail keeps only its length behind the head")
    void shortTrailFollows() {
        TrailWindow window = TrailWindow.swing(10, 0.3f, 2);
        assertEquals(0.0f, window.tail(aged(2), 0.0f), EPSILON);
        assertEquals(0.5f, window.tail(aged(8), 0.0f), EPSILON);
    }

    @Test
    @DisplayName("Released mid-swing, it fades from then and is over a fade later")
    void releaseCutsItShort() {
        TrailWindow window = TrailWindow.swing(20, 1.0f, 4);
        FxLife life = aged(5);
        life.release();
        life.advance();
        assertEquals(1.0f, window.fade(life, 0.0f), EPSILON);
        life.advance();
        life.advance();
        assertEquals(0.5f, window.fade(life, 0.0f), EPSILON);
        assertFalse(window.done(life));
        life.advance();
        life.advance();
        assertTrue(window.done(life));
    }
}
