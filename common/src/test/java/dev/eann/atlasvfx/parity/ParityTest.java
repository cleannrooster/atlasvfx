package dev.eann.atlasvfx.parity;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParityTest {

    private static final Identifier SKIN = Identifier.of("minecraft", "textures/entity/player/wide/steve.png");

    /** A phased entity layer: solid, lit, overlaid, textured, unless a test says otherwise. */
    private static LayerTraits entity(String name, String transparency, String texturing, String target) {
        return new LayerTraits(name, "entity", "quads", true, false, SKIN, transparency, texturing, target,
                true, true, false);
    }

    private static LayerClass classOf(LayerTraits traits) {
        return LayerClassifier.classify(traits).layerClass();
    }

    @Test
    @DisplayName("vanilla body layers read as surfaces, overlays as overlays")
    void classifiesVanillaLayers() {
        assertEquals(LayerClass.SURFACE,
                classOf(entity("entity_cutout_no_cull", "no_transparency", "default_texturing", "main_target")));
        assertEquals(LayerClass.SURFACE,
                classOf(entity("armor_cutout_no_cull", "no_transparency", "default_texturing", "main_target")));
        assertEquals(LayerClass.TRANSLUCENT_SURFACE,
                classOf(entity("entity_translucent", "translucent_transparency", "default_texturing", "main_target")));
        assertEquals(LayerClass.EFFECT_OVERLAY,
                classOf(entity("armor_entity_glint", "glint_transparency", "entity_glint_texturing", "main_target")));
        assertEquals(LayerClass.EFFECT_OVERLAY,
                classOf(entity("eyes", "additive_transparency", "default_texturing", "main_target")));
        assertEquals(LayerClass.EFFECT_OVERLAY,
                classOf(entity("energy_swirl", "additive_transparency", "offset_texturing", "main_target")));
        assertEquals(LayerClass.EFFECT_OVERLAY,
                classOf(entity("outline", "no_transparency", "default_texturing", "outline_target")));
    }

    @Test
    @DisplayName("a mod's layer is classified by what it does, not by its name")
    void classifiesModdedLayersByPhases() {
        assertEquals(LayerClass.SURFACE,
                classOf(entity("somemod:fancy_skin", "no_transparency", "default_texturing", "main_target")));
        assertEquals(LayerClass.EFFECT_OVERLAY,
                classOf(entity("somemod:aura_glow", "lightning_transparency", "default_texturing", "main_target")));
        assertEquals(LayerClass.UNKNOWN,
                classOf(entity("somemod:odd", "somemod_transparency", "default_texturing", "main_target")));
    }

    @Test
    @DisplayName("text, untextured and custom layers are unknown")
    void unknowns() {
        assertEquals(LayerClass.UNKNOWN, classOf(new LayerTraits("text", LayerTraits.TEXT_FORMAT, "quads", true,
                false, SKIN, "translucent_transparency", "default_texturing", "main_target", true, false, true)));
        assertEquals(LayerClass.UNKNOWN, classOf(new LayerTraits("lines", "lines", "lines", true, false, null,
                "translucent_transparency", "default_texturing", "main_target", false, false, true)));
        assertEquals(LayerClass.UNKNOWN, classOf(new LayerTraits("custom", "entity", "quads", false, false, null,
                "", "", "", false, false, false)));
    }

    @Test
    @DisplayName("an override wins over every rule")
    void overrideWins() {
        LayerTraits glinty = entity("parity_test:override_me", "glint_transparency", "glint_texturing", "main_target");
        assertEquals(LayerClass.EFFECT_OVERLAY, classOf(glinty));
        LayerClassifier.override("parity_test:override_me", LayerClass.SURFACE);
        assertEquals(LayerClass.SURFACE, classOf(glinty));
        assertEquals("override", LayerClassifier.classify(glinty).reason());
    }

    private static class NullConsumer implements VertexConsumer {
        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }
    }

    /** Counts what reaches each layer, the way a real buffer would receive it. */
    private static final class Sink {
        final List<String> asked = new ArrayList<>();
        final Map<String, int[]> vertices = new HashMap<>();

        VertexConsumer buffer(String layer) {
            asked.add(layer);
            int[] count = vertices.computeIfAbsent(layer, key -> new int[1]);
            return new NullConsumer() {
                @Override
                public VertexConsumer vertex(float x, float y, float z) {
                    count[0]++;
                    return this;
                }
            };
        }
    }

    @Test
    @DisplayName("recording passes every layer and vertex through, in order, and attributes them")
    void recordingPassesThrough() {
        Sink sink = new Sink();
        ParitySession session = new ParitySession();
        LayerSource model = new LayerSource.Model(PlayerRendererStandIn.class);
        LayerSource armour = new LayerSource.Feature(ArmorFeatureStandIn.class);

        quad(session.recordAny("body", null, model, sink.buffer("body")));
        quad(session.recordAny("body", "end_portal", armour, sink.buffer("body")));
        quad(session.recordAny("glint", null, armour, sink.buffer("glint")));
        quad(session.recordAny("shadow", null, new LayerSource.Dispatcher(), sink.buffer("shadow")));

        assertEquals(List.of("body", "body", "glint", "shadow"), sink.asked);
        assertEquals(8, sink.vertices.get("body")[0]);
        assertEquals(4, sink.vertices.get("glint")[0]);

        Map<Object, LayerTraits> traits = Map.of(
                "body", entity("parity_body", "no_transparency", "default_texturing", "main_target"),
                "glint", entity("parity_glint", "glint_transparency", "entity_glint_texturing", "main_target"),
                "shadow", entity("entity_shadow", "translucent_transparency", "default_texturing", "main_target"),
                "end_portal", entity("end_portal", "no_transparency", "default_texturing", "main_target"));
        List<LayerUse> uses = session.uses(traits::get);
        assertEquals(4, uses.size());
        assertEquals("model ParityTest$PlayerRendererStandIn", uses.get(0).source());
        assertEquals("feature ParityTest$ArmorFeatureStandIn", uses.get(1).source());
        assertEquals(LayerClass.SURFACE, uses.get(1).layerClass());
        assertEquals("end_portal", uses.get(1).drawnAs());
        assertEquals(null, uses.get(0).drawnAs());
        assertEquals(LayerClass.EFFECT_OVERLAY, uses.get(2).layerClass());
        assertEquals(4, uses.get(2).vertices());
        assertEquals("dispatcher", uses.get(3).source());
        assertEquals(0, session.size());
    }

    private static final class PlayerRendererStandIn {
    }

    private static final class ArmorFeatureStandIn {
    }

    @Test
    @DisplayName("each hidden layer gets a buffer of its own, so a glint can join two of them")
    void hiddenBuffersAreDistinct() {
        net.minecraft.client.render.VertexConsumer first = new Seam.Hidden();
        net.minecraft.client.render.VertexConsumer second = new Seam.Hidden();
        assertTrue(first != second);
        // What the game's armour glint does with the two it asks for.
        net.minecraft.client.render.VertexConsumers.union(first, second);
    }

    @Test
    @DisplayName("the draw scope nests and survives an unbalanced exit")
    void drawScope() {
        DrawScope.reset();
        assertTrue(!DrawScope.active());
        DrawScope.enter(new LayerSource.Dispatcher());
        DrawScope.enter(new LayerSource.Model(PlayerRendererStandIn.class));
        assertTrue(DrawScope.current() instanceof LayerSource.Model);
        DrawScope.exit();
        assertTrue(DrawScope.current() instanceof LayerSource.Dispatcher);
        DrawScope.exit();
        DrawScope.exit();
        assertTrue(!DrawScope.active());
    }

    private static void quad(VertexConsumer consumer) {
        for (int i = 0; i < 4; i++) {
            consumer.vertex(i, 0, 0);
        }
    }

    @Test
    @DisplayName("with nothing armed, the seam hands back the very provider it was given")
    void seamIsIdentityWhenIdle() {
        VertexConsumerProvider fake = layer -> new NullConsumer();
        assertSame(fake, ProviderSeam.wrap(null, fake));
        assertSame(fake, ProviderSeam.wrapArm(null, net.minecraft.util.Arm.RIGHT, fake));
        assertTrue(!ProviderSeam.scoped());
    }

    private static LayerUse use(int order, String source, String layer, LayerClass layerClass, int vertices) {
        return new LayerUse(order, layer, "entity", "quads", "minecraft:textures/x.png", source, layerClass, "test",
                vertices, null);
    }

    @Test
    @DisplayName("a report survives its file, and a diff names what appeared, went and moved")
    void reportRoundTripAndDiff() {
        ParityReport before = new ParityReport("minecraft:player", "entity", "fabric", false, false, false,
                "Immediate", List.of(
                use(0, "model P", "entity_cutout_no_cull", LayerClass.SURFACE, 288),
                use(1, "feature Armor", "armor_cutout_no_cull", LayerClass.SURFACE, 96),
                use(2, "feature Cape", "entity_solid", LayerClass.SURFACE, 24)));
        assertEquals(before, ParityReport.fromJson(before.toJson()));

        ParityReport after = new ParityReport("minecraft:player", "entity", "fabric", true, true, false,
                "FullyBufferedMultiBufferSource", List.of(
                use(0, "model P", "entity_cutout_no_cull", LayerClass.SURFACE, 288),
                use(1, "feature Armor", "armor_cutout_no_cull", LayerClass.TRANSLUCENT_SURFACE, 120),
                use(2, "feature Glint", "armor_entity_glint", LayerClass.EFFECT_OVERLAY, 96)));
        List<String> changes = after.diff(before);
        assertEquals(4, changes.size(), changes::toString);
        assertTrue(changes.stream().anyMatch(line -> line.startsWith("~") && line.contains("SURFACE -> TRANSLUCENT")));
        assertTrue(changes.stream().anyMatch(line -> line.startsWith("~") && line.contains("96 -> 120")));
        assertTrue(changes.stream().anyMatch(line -> line.startsWith("+") && line.contains("armor_entity_glint")));
        assertTrue(changes.stream().anyMatch(line -> line.startsWith("-") && line.contains("Cape")));
        assertTrue(before.diff(before).isEmpty());
    }
}
