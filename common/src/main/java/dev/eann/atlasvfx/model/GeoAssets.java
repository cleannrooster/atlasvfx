package dev.eann.atlasvfx.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Geometry and animation files from resource packs, read and baked the first time they are asked
 * for and kept until resources reload. A file that is missing or cannot be read is reported once
 * and reads as absent - the body it would have replaced is drawn as it always was.
 *
 * <p>Render thread only.</p>
 */
public final class GeoAssets {

    private static final Logger LOGGER = LoggerFactory.getLogger("atlasvfx");
    private static final Map<Identifier, Optional<BakedGeo>> MODELS = new HashMap<>();
    private static final Map<Identifier, Map<String, GeoAnimation>> ANIMATIONS = new HashMap<>();

    private GeoAssets() {
    }

    /** The baked model in {@code file}; null if it is missing or broken. */
    public static @Nullable BakedGeo model(Identifier file) {
        return MODELS.computeIfAbsent(file, GeoAssets::loadModel).orElse(null);
    }

    /** Every clip in {@code file}, by name; empty if it is missing, broken, or null. */
    public static Map<String, GeoAnimation> animations(@Nullable Identifier file) {
        return file == null ? Map.of() : ANIMATIONS.computeIfAbsent(file, GeoAssets::loadAnimations);
    }

    /** Forgets everything read: resources have reloaded. */
    public static void clear() {
        MODELS.clear();
        ANIMATIONS.clear();
        Replacements.clearStates();
    }

    private static Optional<BakedGeo> loadModel(Identifier file) {
        JsonObject json = read(file);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(GeoBaker.bake(GeoModel.parse(json)));
        } catch (RuntimeException e) {
            LOGGER.warn("AtlasVFX could not bake geometry {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    private static Map<String, GeoAnimation> loadAnimations(Identifier file) {
        JsonObject json = read(file);
        if (json == null) {
            return Map.of();
        }
        try {
            Map<String, GeoAnimation> clips = Map.copyOf(GeoAnimation.parseAll(json));
            int unsupported = clips.values().stream().mapToInt(GeoAnimation::unsupported).sum();
            if (unsupported > 0) {
                LOGGER.warn("AtlasVFX read {} value(s) in {} as 0: only numbers and arithmetic on them are played",
                        unsupported, file);
            }
            return clips;
        } catch (RuntimeException e) {
            LOGGER.warn("AtlasVFX could not read animations {}: {}", file, e.toString());
            return Map.of();
        }
    }

    private static @Nullable JsonObject read(Identifier file) {
        Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            LOGGER.warn("AtlasVFX found no {}", file);
            return null;
        }
        try (Reader reader = resource.get().getReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            LOGGER.warn("AtlasVFX could not read {}: {}", file, e.toString());
            return null;
        }
    }
}
