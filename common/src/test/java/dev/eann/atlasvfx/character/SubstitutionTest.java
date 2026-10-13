package dev.eann.atlasvfx.character;

import dev.eann.atlasvfx.parity.LayerSource;
import dev.eann.atlasvfx.parity.LayerTraits;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.feature.PlayerHeldItemFeatureRenderer;
import net.minecraft.client.render.entity.feature.StuckArrowsFeatureRenderer;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubstitutionTest {

    @Test
    @DisplayName("each source is the part of the body it draws, subclasses included")
    void bodyParts() {
        assertEquals(BodyPart.SKIN, BodyParts.of(new LayerSource.Model(Object.class)));
        assertEquals(BodyPart.ARM, BodyParts.of(new LayerSource.FirstPersonArm(Arm.RIGHT)));
        assertNull(BodyParts.of(new LayerSource.Dispatcher()));
        assertNull(BodyParts.of((LayerSource) null));
        assertEquals(BodyPart.ARMOR, BodyParts.of(ArmorFeatureRenderer.class));
        assertEquals(BodyPart.HELD, BodyParts.of(HeldItemFeatureRenderer.class));
        // The player's held-item renderer is a subclass of the general one.
        assertEquals(BodyPart.HELD, BodyParts.of(PlayerHeldItemFeatureRenderer.class));
        // Stuck arrows are a subclass of the stuck-objects renderer.
        assertEquals(BodyPart.ATTACHMENT, BodyParts.of(StuckArrowsFeatureRenderer.class));
        assertEquals(BodyPart.ATTACHMENT, BodyParts.of(OverlayFeatureRenderer.class));
        // Anything unknown - another mod's layer - is OTHER, and takes a look by default.
        assertEquals(BodyPart.OTHER, BodyParts.of(UnknownFeature.class));
        assertTrue(BodyPart.WORN.contains(BodyPart.OTHER));
        assertTrue(!BodyPart.WORN.contains(BodyPart.HELD) && !BodyPart.WORN.contains(BodyPart.ATTACHMENT));
    }

    private static final class UnknownFeature {
    }

    private static class ModdedFeature {
    }

    private static final class ModdedArmour extends ModdedFeature {
    }

    @Test
    @DisplayName("a mod names its own feature's part, and its subclasses follow, even after a lookup")
    void registeredParts() {
        assertEquals(BodyPart.OTHER, BodyParts.of(ModdedArmour.class));
        BodyParts.register(ModdedFeature.class, BodyPart.ARMOR);
        assertEquals(BodyPart.ARMOR, BodyParts.of(ModdedFeature.class));
        assertEquals(BodyPart.ARMOR, BodyParts.of(ModdedArmour.class));
    }

    @Test
    @DisplayName("a tint multiplies every channel, alpha included; white leaves colours alone")
    void tint() {
        assertEquals(0x80FF8040, TintingConsumer.multiply(0xFFFF8040, 0x80FFFFFF));
        assertEquals(0xFF800000, TintingConsumer.multiply(0xFFFF0000, 0xFF808080));
        assertEquals(0x12345678, TintingConsumer.multiply(0x12345678, Swap.NO_TINT));
    }

    @Test
    @DisplayName("a look leaves alone the parts it was not given")
    void lookParts() {
        LayerTraits armour = new LayerTraits("armor_cutout_no_cull", "entity", "quads", true, false,
                Identifier.ofVanilla("textures/models/armor/diamond_layer_1.png"), "no_transparency",
                "default_texturing", "main_target", true, true, false);
        Substitution ghost = Substitutions.ghost(0.4f, BodyPart.SKIN_ONLY);
        assertNull(ghost.swap(BodyPart.ARMOR, armour));
        assertNull(ghost.swap(BodyPart.HELD, armour));
        Substitution sky = Substitutions.endSky(EnumSet.of(BodyPart.SKIN));
        assertNull(sky.swap(BodyPart.CAPE, armour));
        assertTrue(!sky.safeUnderShaders());
        assertEquals(1, sky.overdraw().size());
    }

    @Test
    @DisplayName("two effects on one body keep both sets of overlays and the first look")
    void combining() {
        Substitution first = (part, requested) -> null;
        Substitution second = (part, requested) -> null;
        CharacterEffect a = new CharacterEffect(List.of(new OverlayPass.Glint()), first,
                CharacterEffect.Mode.OVERDRAW);
        CharacterEffect b = new CharacterEffect(List.of(new OverlayPass.EndSky()), second,
                CharacterEffect.Mode.SUBSTITUTE);
        CharacterEffect both = a.and(b);
        assertEquals(2, both.overlays().size());
        assertSame(first, both.substitution());
        assertEquals(CharacterEffect.Mode.OVERDRAW, both.mode());
        CharacterEffect lookless = CharacterEffect.overlays(new OverlayPass.Glint()).and(b);
        assertSame(second, lookless.substitution());
        assertSame(a, a.and(CharacterEffect.NONE));
    }

    @Test
    @DisplayName("forcing a mode overrides each effect's own, and lifting it restores them")
    void forcedMode() {
        Substitution look = (part, requested) -> null;
        CharacterEffect swapping = CharacterEffect.substitute(look);
        assertTrue(CharacterEffects.swaps(swapping));
        CharacterEffects.forceMode(CharacterEffect.Mode.OVERDRAW);
        try {
            assertTrue(!CharacterEffects.swaps(swapping));
        } finally {
            CharacterEffects.forceMode(null);
        }
        assertTrue(CharacterEffects.swaps(swapping));
        assertTrue(!CharacterEffects.swaps(swapping.withMode(CharacterEffect.Mode.OVERDRAW)));
    }
}
