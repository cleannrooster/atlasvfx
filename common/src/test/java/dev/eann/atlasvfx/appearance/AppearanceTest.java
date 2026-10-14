package dev.eann.atlasvfx.appearance;

import com.google.gson.JsonParser;
import dev.eann.atlasvfx.character.BodyPart;
import dev.eann.atlasvfx.model.BakedGeo;
import dev.eann.atlasvfx.model.ClipAnimator;
import dev.eann.atlasvfx.model.GeoBaker;
import dev.eann.atlasvfx.model.GeoModel;
import dev.eann.atlasvfx.model.ModelReplacement;
import dev.eann.atlasvfx.model.ReplacementsAccess;
import io.netty.buffer.Unpooled;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppearanceTest {

    private static final String ROGUE = """
            {"name": "Forsaken Rogue", "description": "Daggers.",
             "geo": "atlasvfx_example:geo/rogue.geo.json", "texture": "atlasvfx_example:textures/entity/rogue.png",
             "animations": "atlasvfx_example:animations/adventurers.animations.json", "posing": "animated",
             "hides": ["armor", "cape"],
             "clips": {"prefix": "animation.v2.trickster.", "stance": "stance", "advance": "advance",
                       "swing": ["cross_slash_r", "cross_slash_l"], "use": "throw_mobile", "swing_speed": 1.4}}""";

    @Test
    @DisplayName("an appearance file names its model, look, posing, hidden parts and clips")
    void parsesAppearance() {
        Appearance rogue = Appearances.parse(Identifier.of("atlasvfx_example", "rogue"),
                JsonParser.parseString(ROGUE).getAsJsonObject());
        assertEquals("Forsaken Rogue", rogue.name().getString());
        assertEquals("Daggers.", rogue.description().getString());
        ModelReplacement replacement = rogue.replacement();
        assertEquals(ModelReplacement.Posing.ANIMATED, replacement.posing());
        // Following is the default, the clips kept for whoever wants them.
        Appearance following = Appearances.parse(Identifier.of("atlasvfx_example", "rogue"),
                JsonParser.parseString(ROGUE.replace("\"posing\": \"animated\",", "")).getAsJsonObject());
        assertEquals(ModelReplacement.Posing.FOLLOW_BODY, following.replacement().posing());
        assertEquals(ModelReplacement.Posing.ANIMATED, following.animated().posing());
        // What the file lists, and the skin and first-person arm it stands in for.
        assertEquals(Set.of(BodyPart.SKIN, BodyPart.ARM, BodyPart.ARMOR, BodyPart.CAPE), replacement.hides());
        assertTrue(replacement.hides(BodyPart.SKIN));
        ClipAnimator clips = (ClipAnimator) replacement.animator();
        assertEquals("animation.v2.trickster.stance", clips.stance());
        assertEquals(List.of("animation.v2.trickster.cross_slash_r", "animation.v2.trickster.cross_slash_l"),
                clips.swings());
        assertNull(clips.backstep());
        assertEquals(1.4f, clips.swingSpeed(), 1.0e-6f);
        // One effect for every body wearing it.
        assertTrue(rogue.effect() == rogue.effect());
        assertTrue(rogue.effect().replacement() == replacement);
    }

    @Test
    @DisplayName("an appearance kept over the skin hides armour but not the skin or first-person arm")
    void keepSkin() {
        Appearance reverent = Appearances.parse(Identifier.of("atlasvfx", "reverent"), JsonParser.parseString("""
                {"name": "Reverent", "geo": "atlasvfx:geo/adventurers/reverent.geo.json",
                 "texture": "atlasvfx:textures/entity/adventurers/reverent.png", "keep_skin": true}""")
                .getAsJsonObject());
        ModelReplacement replacement = reverent.replacement();
        assertFalse(replacement.hides(BodyPart.SKIN));
        assertFalse(replacement.hides(BodyPart.ARM));
        assertTrue(replacement.hides(BodyPart.ARMOR));
        assertTrue(replacement.hides(BodyPart.OTHER));
        assertFalse(replacement.hides(BodyPart.HELD));
    }

    @Test
    @DisplayName("a waist turns with the body about the body's pivot, as if it hung from it")
    void waistFollowsBody() {
        BakedGeo geo = GeoBaker.bake(GeoModel.parse(JsonParser.parseString("""
                {"minecraft:geometry": [{"description": {"texture_width": 64, "texture_height": 64}, "bones": [
                  {"name": "bipedBody", "pivot": [0, 24, 0]},
                  {"name": "bipedRightArm", "pivot": [-5, 22, 0]},
                  {"name": "bipedLeftArm", "pivot": [5, 22, 0]},
                  {"name": "bipedRightLeg", "pivot": [-1.9, 12, 0]},
                  {"name": "bipedLeftLeg", "pivot": [1.9, 12, 0]},
                  {"name": "bipedHead", "pivot": [0, 24, 0]},
                  {"name": "bipedWaist", "pivot": [0, 12, 0],
                   "cubes": [{"origin": [-4, 10, -2], "size": [8, 3, 4], "uv": [0, 0]}]}
                ]}]}""").getAsJsonObject()));
        assertEquals("bipedWaist", geo.rig().waist());
        assertEquals("bipedBody", geo.rig().body());
        BipedEntityModel<LivingEntity> biped = vanilla();
        // Sneaking: the body leans forward about the neck.
        biped.body.pitch = 0.5f;
        geo.reset();
        ReplacementsAccess.follow(geo, "bipedWaist", biped.body);
        ModelPart waist = geo.bone("bipedWaist");
        assertEquals(0.5f, waist.pitch, 1.0e-6f);
        // 12 pixels below the neck, swung forward and up with it.
        assertEquals((float) (12 * Math.cos(0.5)), waist.pivotY, 1.0e-4f);
        assertEquals((float) (12 * Math.sin(0.5)), waist.pivotZ, 1.0e-4f);
    }

    @Test
    @DisplayName("an appearance with no clips follows the body")
    void noClipsFollows() {
        Appearance plain = Appearances.parse(Identifier.of("x", "y"), JsonParser.parseString("""
                {"name": "x.y.name", "geo": "x:geo/y.geo.json", "texture": "x:textures/y.png"}""")
                .getAsJsonObject());
        assertEquals(ModelReplacement.Posing.FOLLOW_BODY, plain.replacement().posing());
        assertEquals(BodyPart.WORN, plain.replacement().hides());
        // An untranslated key shows as written.
        assertEquals("x.y.name", plain.name().getString());
    }

    @Test
    @DisplayName("the server's word on appearances survives the wire")
    void syncRoundTrip() {
        Map<UUID, Optional<Identifier>> choices = new LinkedHashMap<>();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        choices.put(first, Optional.of(Identifier.of("atlasvfx_example", "templar")));
        choices.put(second, Optional.empty());
        AppearancePayloads.Sync sync = new AppearancePayloads.Sync(true, true, true, choices);
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        AppearancePayloads.Sync.CODEC.encode(buffer, sync);
        AppearancePayloads.Sync read = AppearancePayloads.Sync.CODEC.decode(buffer);
        assertTrue(read.enabled());
        assertTrue(read.full());
        assertTrue(read.prompt());
        assertEquals(choices, read.choices());

        RegistryByteBuf unlocks = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        AppearancePayloads.Unlocks sent = new AppearancePayloads.Unlocks(
                Map.of(Identifier.of("atlasvfx", "rogue"), Identifier.of("rpg-minibosses", "slay_rogue")),
                Set.of(Identifier.of("atlasvfx", "rogue")));
        AppearancePayloads.Unlocks.CODEC.encode(unlocks, sent);
        assertEquals(sent, AppearancePayloads.Unlocks.CODEC.decode(unlocks));
        assertEquals(Identifier.of("minecraft", "story/mine_diamond"), AppearanceUnlocks.parse(JsonParser.parseString(
                "{\"advancement\": \"minecraft:story/mine_diamond\"}").getAsJsonObject()));

        RegistryByteBuf choose = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
        AppearancePayloads.Choose.CODEC.encode(choose, AppearancePayloads.Choose.of(null));
        assertEquals(Optional.empty(), AppearancePayloads.Choose.CODEC.decode(choose).appearance());
    }

    private static final String RIG = """
            {"minecraft:geometry": [{"description": {"texture_width": 64, "texture_height": 64}, "bones": [
              {"name": "body", "pivot": [0, 12, 0]},
              {"name": "torso", "parent": "body", "pivot": [0, 24, 0],
               "cubes": [{"origin": [-4, 12, -2], "size": [8, 12, 4], "uv": [16, 16]}]},
              {"name": "cape", "parent": "torso", "pivot": [0, 24, 2],
               "cubes": [{"origin": [-5, 8, 2], "size": [10, 16, 1], "uv": [0, 0]}]},
              {"name": "rightArm", "parent": "body", "pivot": [-5, 22, 0],
               "cubes": [{"origin": [-8, 12, -2], "size": [4, 12, 4], "uv": [40, 16]}]},
              {"name": "leftArm", "parent": "body", "pivot": [5, 22, 0],
               "cubes": [{"origin": [4, 12, -2], "size": [4, 12, 4], "uv": [32, 48]}]},
              {"name": "rightLeg", "parent": "body", "pivot": [-2, 12, 0]},
              {"name": "leftLeg", "parent": "body", "pivot": [2, 12, 0]},
              {"name": "head", "parent": "body", "pivot": [0, 24, 0],
               "cubes": [{"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [0, 0]}]},
              {"name": "waist", "parent": "body", "pivot": [0, 12, 0],
               "cubes": [{"origin": [-4, 10, -3], "size": [8, 3, 6], "uv": [0, 32]}]}
            ]}]}""";

    private static BipedEntityModel<LivingEntity> vanilla() {
        ModelPart root = TexturedModelData.of(BipedEntityModel.getModelData(Dilation.NONE, 0.0f), 64, 64)
                .createModel();
        return new BipedEntityModel<>(root);
    }

    @Test
    @DisplayName("in a first-person pass that shows only the arms, only the replacement's arms show")
    void firstPersonShowsArmsOnly() {
        BakedGeo geo = GeoBaker.bake(GeoModel.parse(JsonParser.parseString(RIG).getAsJsonObject()));
        BipedEntityModel<LivingEntity> biped = vanilla();
        geo.reset();
        assertFalse(ReplacementsAccess.partlyHidden(biped));

        // As PlayerAnimator leaves the vanilla model for a first-person swing.
        biped.head.visible = false;
        biped.hat.visible = false;
        biped.body.visible = false;
        biped.rightLeg.visible = false;
        biped.leftLeg.visible = false;
        biped.rightArm.pitch = -1.2f;
        assertTrue(ReplacementsAccess.partlyHidden(biped));
        ReplacementsAccess.showAsVanilla(geo, biped);

        assertTrue(geo.bone("rightArm").visible && !geo.bone("rightArm").hidden);
        assertTrue(geo.bone("leftArm").visible && !geo.bone("leftArm").hidden);
        assertFalse(geo.bone("head").visible);
        assertFalse(geo.bone("torso").visible);
        assertFalse(geo.bone("cape").visible);
        // Hangs from nothing the rig names: it goes with the vanilla body.
        assertFalse(geo.bone("waist").visible);
        // The root carries the arms, its own cubes - none here - hidden.
        assertTrue(geo.bone("body").visible && geo.bone("body").hidden);
        // The arm follows the vanilla arm's swing.
        assertEquals(-1.2f, geo.bone("rightArm").pitch, 1.0e-6f);

        // Back at rest, everything shows again.
        geo.reset();
        assertTrue(geo.bone("head").visible && !geo.bone("body").hidden);
    }
}
