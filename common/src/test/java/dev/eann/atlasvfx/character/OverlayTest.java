package dev.eann.atlasvfx.character;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayTest {

    @Test
    @DisplayName("a steady glow is its colour at its strength, opaque in alpha")
    void steadyGlow() {
        OverlayPass.Glow glow = new OverlayPass.Glow(0xFF8000, 0.5f, 0.0f);
        assertEquals(0xFF804000, glow.color(0.0f));
        assertEquals(glow.color(0.0f), glow.color(37.5f));
    }

    @Test
    @DisplayName("a full pulse goes out between beats and peaks at full strength")
    void pulsingGlow() {
        OverlayPass.Glow pulse = new OverlayPass.Glow(0xFFFFFF, 1.0f, 1.0f);
        int brightest = 0;
        int darkest = 255;
        for (int t = 0; t < 120; t++) {
            int level = pulse.color(t) & 0xFF;
            brightest = Math.max(brightest, level);
            darkest = Math.min(darkest, level);
            assertEquals(0xFF, pulse.color(t) >>> 24);
        }
        assertTrue(brightest >= 250, "brightest " + brightest);
        assertTrue(darkest <= 5, "darkest " + darkest);
    }

    @Test
    @DisplayName("effects set by id are kept until cleared, and clearing them all empties the registry")
    void setAndClear() {
        CharacterEffects.clearAll();
        assertTrue(CharacterEffects.get(42).isEmpty());
        CharacterEffects.set(42, CharacterEffect.overlays(new OverlayPass.Glint(), new OverlayPass.EndSky()));
        assertEquals(2, CharacterEffects.get(42).overlays().size());
        assertTrue(CharacterEffects.any());
        CharacterEffects.set(42, CharacterEffect.NONE);
        assertTrue(CharacterEffects.get(42).isEmpty());
        CharacterEffects.set(7, CharacterEffect.overlays(new OverlayPass.Glint()));
        CharacterEffects.clearAll();
        assertFalse(CharacterEffects.any());
    }
}
