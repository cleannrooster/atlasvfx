package dev.eann.atlasvfx.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Bedrock geometry file - the {@code .geo.json} Blockbench, GeckoLib and AzureLib models are
 * saved as - read into plain values. Nothing here needs GeckoLib: the format is plain JSON, and
 * {@link GeoBaker} turns it into the game's own model parts.
 *
 * <p>Units are pixels, {@code y} up from the feet, as in the file.</p>
 *
 * @param bones in file order, which puts a parent before its children in files Blockbench writes
 */
public record GeoModel(String identifier, int textureWidth, int textureHeight, List<Bone> bones) {

    public GeoModel {
        bones = List.copyOf(bones);
    }

    /**
     * @param parent   the parent bone's name; null for a root
     * @param pivot    what it turns about
     * @param rotation degrees about x, y and z, applied z first, then y, then x
     */
    public record Bone(String name, @Nullable String parent, float[] pivot, float[] rotation, List<Cube> cubes) {
        public Bone {
            cubes = List.copyOf(cubes);
        }
    }

    /**
     * @param origin   the cube's lowest corner
     * @param inflate  how far each face is pushed out, in pixels
     * @param boxUv    the corner of its box-unwrapped texture, or null if it gives each face its own
     * @param faces    each face's own texture area, when {@code boxUv} is null
     * @param pivot    what its own rotation turns about; null for none
     * @param rotation its own rotation, degrees; null for none
     */
    public record Cube(float[] origin, float[] size, float inflate, boolean mirror, float @Nullable [] boxUv,
                       Map<Face, FaceUv> faces, float @Nullable [] pivot, float @Nullable [] rotation) {
        public Cube {
            faces = Map.copyOf(faces);
        }
    }

    public enum Face { NORTH, SOUTH, EAST, WEST, UP, DOWN }

    /** One face's texture area: a corner and a size, either of which may run backwards. */
    public record FaceUv(float u, float v, float width, float height) {
    }

    /** Reads a geometry file's first model. */
    public static GeoModel parse(JsonObject root) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            throw new JsonParseException("no minecraft:geometry");
        }
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        String identifier = description != null && description.has("identifier")
                ? description.get("identifier").getAsString() : "geometry.unknown";
        int width = description != null && description.has("texture_width")
                ? description.get("texture_width").getAsInt() : 64;
        int height = description != null && description.has("texture_height")
                ? description.get("texture_height").getAsInt() : 64;
        List<Bone> bones = new ArrayList<>();
        JsonArray boneArray = geometry.getAsJsonArray("bones");
        if (boneArray != null) {
            for (JsonElement element : boneArray) {
                bones.add(bone(element.getAsJsonObject()));
            }
        }
        return new GeoModel(identifier, width, height, bones);
    }

    private static Bone bone(JsonObject json) {
        List<Cube> cubes = new ArrayList<>();
        boolean boneMirror = json.has("mirror") && json.get("mirror").getAsBoolean();
        float boneInflate = json.has("inflate") ? json.get("inflate").getAsFloat() : 0.0f;
        JsonArray cubeArray = json.getAsJsonArray("cubes");
        if (cubeArray != null) {
            for (JsonElement element : cubeArray) {
                cubes.add(cube(element.getAsJsonObject(), boneMirror, boneInflate));
            }
        }
        return new Bone(json.get("name").getAsString(),
                json.has("parent") ? json.get("parent").getAsString() : null,
                vec(json, "pivot", 0.0f), vec(json, "rotation", 0.0f), cubes);
    }

    private static Cube cube(JsonObject json, boolean boneMirror, float boneInflate) {
        float[] boxUv = null;
        Map<Face, FaceUv> faces = new EnumMap<>(Face.class);
        JsonElement uv = json.get("uv");
        if (uv == null) {
            boxUv = new float[]{0.0f, 0.0f};
        } else if (uv.isJsonArray()) {
            boxUv = new float[]{uv.getAsJsonArray().get(0).getAsFloat(), uv.getAsJsonArray().get(1).getAsFloat()};
        } else {
            for (Map.Entry<String, JsonElement> entry : uv.getAsJsonObject().entrySet()) {
                Face face = Face.valueOf(entry.getKey().toUpperCase(Locale.ROOT));
                JsonObject area = entry.getValue().getAsJsonObject();
                float[] corner = floats(area.getAsJsonArray("uv"));
                float[] size = area.has("uv_size") ? floats(area.getAsJsonArray("uv_size")) : new float[]{0.0f, 0.0f};
                faces.put(face, new FaceUv(corner[0], corner[1], size[0], size[1]));
            }
        }
        return new Cube(vec(json, "origin", 0.0f), vec(json, "size", 0.0f),
                json.has("inflate") ? json.get("inflate").getAsFloat() : boneInflate,
                json.has("mirror") ? json.get("mirror").getAsBoolean() : boneMirror, boxUv, faces,
                json.has("pivot") ? vec(json, "pivot", 0.0f) : null,
                json.has("rotation") ? vec(json, "rotation", 0.0f) : null);
    }

    private static float[] vec(JsonObject json, String key, float fallback) {
        JsonArray array = json.getAsJsonArray(key);
        return array == null ? new float[]{fallback, fallback, fallback} : floats(array);
    }

    private static float[] floats(JsonArray array) {
        float[] out = new float[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }
}
