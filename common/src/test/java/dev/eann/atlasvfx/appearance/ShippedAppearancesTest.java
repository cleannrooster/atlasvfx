package dev.eann.atlasvfx.appearance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.eann.atlasvfx.model.BakedGeo;
import dev.eann.atlasvfx.model.GeoAnimation;
import dev.eann.atlasvfx.model.GeoBaker;
import dev.eann.atlasvfx.model.GeoModel;
import dev.eann.atlasvfx.model.GeoRig;
import dev.eann.atlasvfx.model.ModelReplacement;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every appearance AtlasVFX ships reads, bakes and finds its rig: a new model that would not show
 * fails here rather than in a game.
 */
class ShippedAppearancesTest {

    private static final String ASSETS = "/assets/atlasvfx/";

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = ShippedAppearancesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** {@code namespace:path} as a resource on the classpath. */
    private static String resource(Identifier id) {
        return "/assets/" + id.getNamespace() + "/" + id.getPath();
    }

    private static List<Path> appearanceFiles() throws IOException, URISyntaxException {
        URL folder = ShippedAppearancesTest.class.getResource(ASSETS + Appearances.FOLDER);
        assertNotNull(folder, "no appearances shipped");
        try (Stream<Path> files = Files.list(Path.of(folder.toURI()))) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test
    @DisplayName("every shipped appearance bakes, has its texture and clips, and finds a humanoid rig")
    void everyShippedAppearanceWorks() throws IOException, URISyntaxException {
        List<Path> files = appearanceFiles();
        assertTrue(files.size() >= 11, "found " + files.size());
        Map<String, Object> lang = new com.google.gson.Gson().fromJson(
                json(ASSETS + "lang/en_us.json"), Map.class);
        for (Path file : files) {
            String name = file.getFileName().toString().replace(".json", "");
            JsonObject appearanceJson;
            try (var reader = Files.newBufferedReader(file)) {
                appearanceJson = JsonParser.parseReader(reader).getAsJsonObject();
            }
            Appearance appearance = Appearances.parse(Identifier.of("atlasvfx", name), appearanceJson);
            ModelReplacement replacement = appearance.replacement();

            // Its name and description are translated.
            assertTrue(lang.containsKey(appearanceJson.get("name").getAsString()), name + ": untranslated name");

            GeoModel model = GeoModel.parse(json(resource(replacement.geo())));
            BakedGeo baked = GeoBaker.bake(model);
            assertFalse(baked.bones().isEmpty(), name + ": no bones");
            GeoRig rig = baked.rig();
            assertNotNull(rig.head(), name + ": no head");
            assertNotNull(rig.rightArm(), name + ": no right arm");
            assertNotNull(rig.leftArm(), name + ": no left arm");
            assertNotNull(rig.rightLeg(), name + ": no right leg");
            assertNotNull(rig.leftLeg(), name + ": no left leg");

            // The texture is there, the size the model was made for.
            try (InputStream png = ShippedAppearancesTest.class.getResourceAsStream(resource(replacement.texture()))) {
                assertNotNull(png, name + ": missing " + replacement.texture());
                byte[] header = png.readNBytes(24);
                int width = (header[16] & 0xFF) << 24 | (header[17] & 0xFF) << 16 | (header[18] & 0xFF) << 8
                        | (header[19] & 0xFF);
                int height = (header[20] & 0xFF) << 24 | (header[21] & 0xFF) << 16 | (header[22] & 0xFF) << 8
                        | (header[23] & 0xFF);
                assertEquals(model.textureWidth(), width, name + ": texture width");
                assertEquals(model.textureHeight(), height, name + ": texture height");
            }

            // Clips it names are in its animation file, and every value in them plays.
            if (replacement.animations() != null) {
                Map<String, GeoAnimation> clips = GeoAnimation.parseAll(json(resource(replacement.animations())));
                ModelReplacement animated = appearance.animated();
                assertNotNull(animated, name + ": animation file but no clips");
                dev.eann.atlasvfx.model.ClipAnimator animator =
                        (dev.eann.atlasvfx.model.ClipAnimator) animated.animator();
                for (String clip : Stream.concat(Stream.of(animator.stance(), animator.advance(), animator.use()),
                        animator.swings().stream()).filter(java.util.Objects::nonNull).toList()) {
                    assertTrue(clips.containsKey(clip), name + ": no clip " + clip);
                    assertEquals(0, clips.get(clip).unsupported(), name + ": " + clip + " has values that do not play");
                }
            }
        }
    }
}
