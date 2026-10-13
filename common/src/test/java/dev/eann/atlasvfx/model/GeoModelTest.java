package dev.eann.atlasvfx.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.model.ModelPart;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoModelTest {

    /** The Rogue's rig, cut down: a root "body" holding a torso, arms, legs and a head with armour. */
    private static final String ROGUE = """
            {"format_version": "1.12.0", "minecraft:geometry": [{
              "description": {"identifier": "geometry.assassin", "texture_width": 128, "texture_height": 128},
              "bones": [
                {"name": "body", "pivot": [0, 12, 0]},
                {"name": "torso", "parent": "body", "pivot": [0, 24, 0], "cubes": [
                  {"origin": [-4, 14, -2], "size": [8, 9, 4], "inflate": 0.6, "uv": [32, 17]},
                  {"origin": [-7, 17, -2], "size": [3, 9, 4], "pivot": [0, 24, 0], "rotation": [0, 0, -42.5], "uv": [14, 58]}]},
                {"name": "rightArm", "parent": "body", "pivot": [-5, 22, 0]},
                {"name": "armorRightArm", "parent": "rightArm", "pivot": [-5, 22, 0], "cubes": [
                  {"origin": [-8, 12, -2], "size": [4, 12, 4], "inflate": 0.51, "uv": [32, 46]}]},
                {"name": "leftArm", "parent": "body", "pivot": [4, 22, 0]},
                {"name": "rightLeg", "parent": "body", "pivot": [-2, 12, 0]},
                {"name": "leftLeg", "parent": "body", "pivot": [2, 12, 0]},
                {"name": "head", "parent": "body", "pivot": [0, 24, 0], "cubes": [
                  {"origin": [-4, 24, -4], "size": [8, 8, 8], "uv": [28, 79]}]},
                {"name": "armorHead", "parent": "head", "pivot": [0, 24, 0]}
              ]}]}
            """;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    @DisplayName("a geometry file bakes to the vanilla player's parts where it describes them")
    void bakesLikeVanilla() {
        BakedGeo geo = GeoBaker.bake(GeoModel.parse(json(ROGUE)));
        ModelPart body = geo.bone("body");
        ModelPart head = geo.bone("head");
        ModelPart arm = geo.bone("rightArm");
        assertNotNull(body);
        // A root's pivot is measured from 24 pixels up; a child's from its parent's.
        assertEquals(12.0f, body.pivotY, 1.0e-5f);
        assertEquals(-12.0f, head.pivotY, 1.0e-5f);
        assertEquals(-5.0f, arm.pivotX, 1.0e-5f);
        assertEquals(-10.0f, arm.pivotY, 1.0e-5f);
        // The head's cube is the vanilla head's box.
        ModelPart.Cuboid cube = headCuboid(head);
        assertEquals(-4.0f, cube.minX, 1.0e-5f);
        assertEquals(-8.0f, cube.minY, 1.0e-5f);
        assertEquals(-4.0f, cube.minZ, 1.0e-5f);
        assertEquals(4.0f, cube.maxX, 1.0e-5f);
        assertEquals(0.0f, cube.maxY, 1.0e-5f);
        // Rest is kept, to pose from every frame.
        head.pitch = 1.0f;
        head.xScale = 2.0f;
        geo.reset();
        assertEquals(0.0f, head.pitch, 0.0f);
        assertEquals(1.0f, head.xScale, 0.0f);
    }

    private static ModelPart.Cuboid headCuboid(ModelPart head) {
        ModelPart.Cuboid[] found = new ModelPart.Cuboid[1];
        head.forEachCuboid(new net.minecraft.client.util.math.MatrixStack(), (entry, path, index, cuboid) -> {
            if (path.isEmpty() && found[0] == null) {
                found[0] = cuboid;
            }
        });
        return found[0];
    }

    @Test
    @DisplayName("a cube with its own rotation sits in a part of its own, turning about its pivot")
    void rotatedCube() {
        BakedGeo geo = GeoBaker.bake(GeoModel.parse(json(ROGUE)));
        ModelPart torso = geo.bone("torso");
        assertTrue(torso.hasChild("#cube0"));
        ModelPart holder = torso.getChild("#cube0");
        assertEquals((float) Math.toRadians(-42.5), holder.roll, 1.0e-5f);
        assertEquals(0.0f, holder.pivotX, 1.0e-5f);
        assertEquals(0.0f, holder.pivotY, 1.0e-5f);
    }

    @Test
    @DisplayName("the rig finds the head, arms and legs, and a torso rather than a body that holds the limbs")
    void rig() {
        GeoRig rig = GeoRig.of(GeoModel.parse(json(ROGUE)));
        assertEquals("head", rig.head());
        assertEquals("torso", rig.body());
        assertEquals("rightArm", rig.rightArm());
        assertEquals("leftArm", rig.leftArm());
        assertEquals("rightLeg", rig.rightLeg());
        assertEquals("leftLeg", rig.leftLeg());
    }

    @Test
    @DisplayName("a head named loosely is found as the outermost head bone, never armour or a bone inside it")
    void looseHead() {
        GeoRig rig = GeoRig.of(GeoModel.parse(json("""
                {"minecraft:geometry": [{"description": {}, "bones": [
                  {"name": "root", "pivot": [0, 0, 0]},
                  {"name": "armorHeadPiece", "parent": "root", "pivot": [0, 30, 0]},
                  {"name": "Head_Main", "parent": "root", "pivot": [0, 26, 0]},
                  {"name": "head_feather", "parent": "Head_Main", "pivot": [0, 34, 0]}
                ]}]}""")));
        assertEquals("Head_Main", rig.head());
        assertNull(rig.body());
        GeoRig none = GeoRig.of(GeoModel.parse(json("""
                {"minecraft:geometry": [{"description": {}, "bones": [{"name": "root", "pivot": [0, 0, 0]}]}]}""")));
        assertNull(none.head());
    }

    @Test
    @DisplayName("values may be sums of numbers; anything needing the game is not read")
    void constants() {
        assertEquals(748.0f, GeoAnimation.Constants.evaluate("28+720 "), 1.0e-4f);
        assertEquals(-45.8734f, GeoAnimation.Constants.evaluate("-405.8734066292+360"), 1.0e-3f);
        assertEquals(9.0f, GeoAnimation.Constants.evaluate("(1+2)*3"), 1.0e-6f);
        assertEquals(-2.0f, GeoAnimation.Constants.evaluate("-(4/2)"), 1.0e-6f);
        assertNull(GeoAnimation.Constants.evaluate("math.sin(query.anim_time*90)"));
        assertNull(GeoAnimation.Constants.evaluate("1+"));
    }

    private static final String CLIPS = """
            {"format_version": "1.8.0", "animations": {
              "stance": {"loop": true, "animation_length": 1.6, "bones": {
                "rightArm": {"rotation": {
                  "0.0": {"vector": [-18, 44, 22.6], "easing": "easeInOutSine"},
                  "0.8": {"vector": [-24, 46, 22.6], "easing": "easeInOutSine"},
                  "1.6": {"vector": [-18, 44, 22.6], "easing": "easeInOutSine"}}},
                "head": {"position": [0, 2, 0]}}},
              "spin": {"animation_length": 1.0, "bones": {
                "rightArm": {"rotation": {"0.0": [0, 0, 0], "1.0": ["0+90", 0, 0]}}}}
            }}
            """;

    @Test
    @DisplayName("keyframes ease by the next key's easing, in radians and with height flipped")
    void sampling() {
        Map<String, GeoAnimation> clips = GeoAnimation.parseAll(json(CLIPS));
        GeoAnimation stance = clips.get("stance");
        assertEquals(GeoAnimation.Loop.LOOP, stance.loop());
        assertEquals(1.6f, stance.length(), 1.0e-6f);
        float[] out = new float[3];
        stance.bones().get("rightArm").rotation().sample(0.0f, out);
        assertEquals((float) Math.toRadians(-18), out[0], 1.0e-5f);
        stance.bones().get("rightArm").rotation().sample(0.4f, out);
        // Halfway, eased in-out sine is exactly halfway too.
        assertEquals((float) Math.toRadians(-21), out[0], 1.0e-4f);
        stance.bones().get("head").position().sample(0.3f, out);
        assertEquals(-2.0f, out[1], 1.0e-6f);
        GeoAnimation spin = clips.get("spin");
        assertEquals(GeoAnimation.Loop.ONCE, spin.loop());
        spin.bones().get("rightArm").rotation().sample(0.25f, out);
        assertEquals((float) Math.toRadians(22.5), out[0], 1.0e-5f);
        assertEquals(0, stance.unsupported() + spin.unsupported());
    }

    @Test
    @DisplayName("named easings read as GeckoLib names them; unknown ones play linear")
    void easings() {
        assertEquals(Easing.EASE_IN_OUT_SINE, Easing.named("easeInOutSine"));
        assertEquals(Easing.EASE_IN_ELASTIC, Easing.named("easeInElastic"));
        assertEquals(Easing.LINEAR, Easing.named("linear"));
        assertEquals(Easing.LINEAR, Easing.named("somethingElse"));
        for (Easing easing : Easing.values()) {
            assertEquals(0.0f, easing.apply(0.0f, Float.NaN), 1.0e-4f, easing.name());
            assertEquals(1.0f, easing.apply(1.0f, Float.NaN), 1.0e-4f, easing.name());
        }
    }

    @Test
    @DisplayName("a playing state loops its base, blends a change in, and hands bones back after an action")
    void playback() {
        BakedGeo geo = GeoBaker.bake(GeoModel.parse(json(ROGUE)));
        GeoAnimationState state = new GeoAnimationState();
        state.bind(GeoAnimation.parseAll(json(CLIPS)));
        state.at(0.0f);
        state.loop("stance", 1.0f);
        assertEquals("stance", state.base());
        ModelPart arm = geo.bone("rightArm");

        geo.reset();
        state.apply(geo, 0.0f);
        assertEquals((float) Math.toRadians(-18), arm.pitch, 1.0e-4f);
        // Looping: 1.6 s on is the start again.
        geo.reset();
        state.apply(geo, 32.0f);
        assertEquals((float) Math.toRadians(-18), arm.pitch, 1.0e-4f);

        // An action takes over the bones it moves, blending in over two ticks.
        state.at(40.0f);
        state.play("spin", 1.0f);
        assertTrue(state.acting());
        geo.reset();
        state.apply(geo, 50.0f);
        assertEquals((float) Math.toRadians(45), arm.pitch, 1.0e-4f);
        // Once it has ended and faded, the base has the arm back.
        geo.reset();
        state.apply(geo, 40.0f + 20.0f + GeoAnimationState.ACTION_OUT + 1.0f);
        assertTrue(!state.acting());
        float base = arm.pitch;
        geo.reset();
        state.apply(geo, 40.0f + 20.0f + GeoAnimationState.ACTION_OUT + 1.0f);
        assertEquals(base, arm.pitch, 1.0e-6f);
        assertTrue(Math.abs(base - (float) Math.toRadians(45)) > 0.1f);

        // An unknown clip changes nothing.
        state.loop("nope", 1.0f);
        assertEquals("stance", state.base());
    }

    /** Every corner a part draws, as "x y z u v", rounded, sorted: what it looks like, in any order. */
    private static java.util.List<String> drawn(ModelPart part) {
        java.util.List<String> corners = new java.util.ArrayList<>();
        part.render(new net.minecraft.client.util.math.MatrixStack(), new net.minecraft.client.render.VertexConsumer() {
            @Override
            public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                               float nx, float ny, float nz) {
                corners.add(String.format(java.util.Locale.ROOT, "%.4f %.4f %.4f %.4f %.4f", x, y, z, u, v));
            }

            @Override
            public net.minecraft.client.render.VertexConsumer vertex(float x, float y, float z) {
                throw new UnsupportedOperationException();
            }

            @Override
            public net.minecraft.client.render.VertexConsumer color(int r, int g, int b, int a) {
                return this;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer texture(float u, float v) {
                return this;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer overlay(int u, int v) {
                return this;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer light(int u, int v) {
                return this;
            }

            @Override
            public net.minecraft.client.render.VertexConsumer normal(float x, float y, float z) {
                return this;
            }
        }, 0, 0);
        java.util.Collections.sort(corners);
        return corners;
    }

    private static String cubeFile(String uv, boolean mirror) {
        return """
                {"minecraft:geometry": [{"description": {"texture_width": 64, "texture_height": 64}, "bones": [
                  {"name": "b", "pivot": [0, 0, 0], "cubes": [
                    {"origin": [-1, 2, -3], "size": [3, 5, 2], "mirror": %s, "uv": %s}]}]}]}
                """.formatted(mirror, uv);
    }

    @Test
    @DisplayName("a per-face cube that describes a box unwrap draws exactly as the box cube, mirrored or not")
    void perFaceMatchesBox() {
        // A box unwrap at (10, 20) of a 3 x 5 x 2 cube, written face by face as Blockbench writes it.
        String faces = """
                {"east": {"uv": [10, 22], "uv_size": [2, 5]}, "west": {"uv": [15, 22], "uv_size": [2, 5]},
                 "north": {"uv": [12, 22], "uv_size": [3, 5]}, "south": {"uv": [17, 22], "uv_size": [3, 5]},
                 "up": {"uv": [12, 20], "uv_size": [3, 2]}, "down": {"uv": [15, 22], "uv_size": [3, -2]}}""";
        for (boolean mirror : new boolean[]{false, true}) {
            ModelPart box = GeoBaker.bake(GeoModel.parse(json(cubeFile("[10, 20]", mirror)))).bone("b");
            ModelPart perFace = GeoBaker.bake(GeoModel.parse(json(cubeFile(faces, mirror)))).bone("b");
            java.util.List<String> expected = drawn(box);
            assertEquals(24, expected.size());
            assertEquals(expected, drawn(perFace), "mirror " + mirror);
        }
    }

    @Test
    @DisplayName("GeckoLib's box layout, written face by face, is the game's own for whole sizes")
    void boxFacesMatchVanilla() {
        for (boolean mirror : new boolean[]{false, true}) {
            ModelPart box = GeoBaker.bake(GeoModel.parse(json(cubeFile("[10, 20]", mirror)))).bone("b");
            ModelPart faces = new ModelPart(java.util.List.of(FaceCuboid.of(-1, -7, -3, 3, 5, 2, 0, mirror, 64, 64,
                    GeoBaker.boxFaces(new float[]{10, 20}, new float[]{3, 5, 2}))), Map.of());
            faces.setTransform(box.getTransform());
            assertEquals(drawn(box), drawn(faces), "mirror " + mirror);
        }
    }

    @Test
    @DisplayName("a box unwrap of a fractional size is laid out by its whole pixels, as GeckoLib does")
    void fractionalBoxUsesWholePixels() {
        // The Templar's chest plate: 8 x 6 x 5.1 at (0, 42).
        ModelPart part = GeoBaker.bake(GeoModel.parse(json("""
                {"minecraft:geometry": [{"description": {"texture_width": 128, "texture_height": 128}, "bones": [
                  {"name": "b", "pivot": [0, 0, 0], "cubes": [{"origin": [0, 0, 0], "size": [8, 6, 5.1], "uv": [0, 42]}]}]}]}
                """))).bone("b");
        java.util.List<String> corners = drawn(part);
        assertEquals(24, corners.size());
        java.util.Set<Float> us = new java.util.TreeSet<>();
        for (String corner : corners) {
            us.add(Math.round(Float.parseFloat(corner.split(" ")[3]) * 128 * 100) / 100.0f);
        }
        // Edges at 0, 5, 13, 18, 26 and 21: whole pixels, where the exact size would give 5.1, 13.1, ...
        assertEquals(java.util.Set.of(0.0f, 5.0f, 13.0f, 18.0f, 21.0f, 26.0f), us);
    }

    @Test
    @DisplayName("a face the file leaves out is not drawn, and a negative size flips the area")
    void perFaceFlipsAndOmits() {
        String faces = """
                {"north": {"uv": [16, 104], "uv_size": [-4, 2]}, "south": {"uv": [12, 104], "uv_size": [4, 2]}}""";
        ModelPart part = GeoBaker.bake(GeoModel.parse(json("""
                {"minecraft:geometry": [{"description": {"texture_width": 128, "texture_height": 128}, "bones": [
                  {"name": "b", "pivot": [0, 0, 0], "cubes": [{"origin": [0, 0, 0], "size": [4, 2, 1], "uv": %s}]}]}]}
                """.formatted(faces)))).bone("b");
        java.util.List<String> corners = drawn(part);
        assertEquals(8, corners.size());
        // Both faces use the same 4 x 2 area, one of them mirrored: the same set of texture corners.
        java.util.Set<String> uvs = new java.util.TreeSet<>();
        for (String corner : corners) {
            String[] parts = corner.split(" ");
            uvs.add(parts[3] + " " + parts[4]);
        }
        assertEquals(4, uvs.size());
    }
}
