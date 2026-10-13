package dev.eann.atlasvfx.model;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a {@link GeoModel} into the game's own model parts, so it is drawn - and posed, and
 * reskinned, and handled by every mod that handles vanilla models - exactly as a vanilla model is.
 *
 * <p>The two formats share their axes but for height: a geometry file measures up from the feet,
 * a model part down from a point 24 pixels above them, with the same {@code x} and {@code z}, and
 * rotations carry over as they are. So the vanilla player's geometry, written as a geometry file,
 * bakes to exactly the vanilla player's parts.</p>
 *
 * <p>Box-unwrapped cubes come out as the game's own cuboids - those with a fractional size laid out
 * by its whole pixels instead, as GeckoLib and AzureLib lay them out, where the game's cuboid would
 * shift every face after a fractional edge. A cube that gives each face its own
 * texture area gets each face's area laid on the same face of such a cuboid, mirrored and stretched
 * as the file says - the faces named as GeckoLib names them: what a file calls east is the part's
 * west, and its top the part's {@code DOWN}, since a part's {@code y} runs down. A face's
 * {@code uv_rotation} is not applied.</p>
 */
public final class GeoBaker {

    /** Where a model part's {@code y} of zero is, measured up from the feet in pixels. */
    static final float ORIGIN_HEIGHT = 24.0f;
    private static final Set<Direction> ALL_FACES = EnumSet.allOf(Direction.class);

    private GeoBaker() {
    }

    public static BakedGeo bake(GeoModel model) {
        Map<String, GeoModel.Bone> byName = new LinkedHashMap<>();
        Map<String, List<GeoModel.Bone>> children = new HashMap<>();
        List<GeoModel.Bone> roots = new ArrayList<>();
        for (GeoModel.Bone bone : model.bones()) {
            byName.put(bone.name(), bone);
        }
        for (GeoModel.Bone bone : model.bones()) {
            if (bone.parent() != null && byName.containsKey(bone.parent())) {
                children.computeIfAbsent(bone.parent(), name -> new ArrayList<>()).add(bone);
            } else {
                roots.add(bone);
            }
        }
        Map<String, ModelPart> parts = new LinkedHashMap<>();
        Map<String, ModelPart> rootChildren = new LinkedHashMap<>();
        for (GeoModel.Bone bone : roots) {
            rootChildren.put(bone.name(), build(model, bone, null, children, parts));
        }
        ModelPart root = new ModelPart(List.of(), rootChildren);
        return new BakedGeo(model, root, parts, GeoRig.of(model));
    }

    private static ModelPart build(GeoModel model, GeoModel.Bone bone, float @Nullable [] parentPivot,
                                   Map<String, List<GeoModel.Bone>> children, Map<String, ModelPart> parts) {
        float[] pivot = bone.pivot();
        List<ModelPart.Cuboid> cuboids = new ArrayList<>();
        Map<String, ModelPart> childParts = new LinkedHashMap<>();
        int rotated = 0;
        for (GeoModel.Cube cube : bone.cubes()) {
            if (cube.rotation() != null && !zero(cube.rotation())) {
                // A part has one rotation; a cube with its own sits in a part of its own, turning
                // about its own pivot - or its middle, if the file names none.
                float[] cubePivot = cube.pivot() != null ? cube.pivot() : centre(cube);
                ModelPart holder = new ModelPart(cuboids(model, cube, cubePivot), Map.of());
                ModelTransform pose = transform(cubePivot, pivot, cube.rotation());
                holder.setTransform(pose);
                holder.setDefaultTransform(pose);
                childParts.put("#cube" + rotated++, holder);
            } else {
                cuboids.addAll(cuboids(model, cube, pivot));
            }
        }
        for (GeoModel.Bone child : children.getOrDefault(bone.name(), List.of())) {
            childParts.put(child.name(), build(model, child, pivot, children, parts));
        }
        ModelPart part = new ModelPart(cuboids, childParts);
        ModelTransform pose = transform(pivot, parentPivot, bone.rotation());
        part.setTransform(pose);
        part.setDefaultTransform(pose);
        parts.put(bone.name(), part);
        return part;
    }

    /** A part's pivot and rotation, as an offset from its parent's pivot - or from the origin, for a root. */
    static ModelTransform transform(float[] pivot, float @Nullable [] parentPivot, float[] rotationDegrees) {
        float x = pivot[0];
        float y = ORIGIN_HEIGHT - pivot[1];
        float z = pivot[2];
        if (parentPivot != null) {
            x -= parentPivot[0];
            y -= ORIGIN_HEIGHT - parentPivot[1];
            z -= parentPivot[2];
        }
        return ModelTransform.of(x, y, z, rotationDegrees[0] * MathHelper.RADIANS_PER_DEGREE,
                rotationDegrees[1] * MathHelper.RADIANS_PER_DEGREE, rotationDegrees[2] * MathHelper.RADIANS_PER_DEGREE);
    }

    /** The box of a cube in a part whose pivot is {@code pivot}: its lowest corner in the part's own terms. */
    static float[] box(GeoModel.Cube cube, float[] pivot) {
        float[] o = cube.origin();
        float[] s = cube.size();
        return new float[]{o[0] - pivot[0], pivot[1] - o[1] - s[1], o[2] - pivot[2]};
    }

    private static List<ModelPart.Cuboid> cuboids(GeoModel model, GeoModel.Cube cube, float[] pivot) {
        float[] at = box(cube, pivot);
        float[] s = cube.size();
        float inflate = cube.inflate();
        float width = model.textureWidth();
        float height = model.textureHeight();
        if (cube.boxUv() != null && whole(s) && whole(cube.boxUv())) {
            return List.of(new ModelPart.Cuboid(Math.round(cube.boxUv()[0]), Math.round(cube.boxUv()[1]),
                    at[0], at[1], at[2], s[0], s[1], s[2], inflate, inflate, inflate, cube.mirror(), width, height,
                    ALL_FACES));
        }
        if (cube.boxUv() != null) {
            // A box unwrap of a cube with a fractional size, or at a fractional corner: the game's
            // cuboid would lay it out by the exact size, shifting every face after the first
            // fractional edge. GeckoLib and AzureLib - what such files are made for - lay it out by
            // the whole pixels, so it is laid out face by face their way.
            return List.of(FaceCuboid.of(at[0], at[1], at[2], s[0], s[1], s[2], inflate, cube.mirror(), width, height,
                    boxFaces(cube.boxUv(), s)));
        }
        Map<Direction, GeoModel.FaceUv> byFace = new EnumMap<>(Direction.class);
        for (Map.Entry<GeoModel.Face, GeoModel.FaceUv> entry : cube.faces().entrySet()) {
            byFace.put(direction(entry.getKey()), entry.getValue());
        }
        if (byFace.isEmpty()) {
            return List.of();
        }
        return List.of(FaceCuboid.of(at[0], at[1], at[2], s[0], s[1], s[2], inflate, cube.mirror(), width, height,
                byFace));
    }

    /**
     * The face a geometry file names, as the model part's own - as GeckoLib reads them, where a box
     * unwrap puts the file's east face where the game's box puts its west one, and the file's top
     * on the part's {@code DOWN}, since a part's {@code y} runs down.
     */
    static Direction direction(GeoModel.Face face) {
        return switch (face) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            case UP -> Direction.DOWN;
            case DOWN -> Direction.UP;
        };
    }

    /**
     * A box unwrap at {@code uv} as each face's own area, the way GeckoLib lays it out: by the
     * size's whole pixels, the file's east face first.
     */
    static Map<Direction, GeoModel.FaceUv> boxFaces(float[] uv, float[] size) {
        float u = uv[0];
        float v = uv[1];
        float x = (float) Math.floor(size[0]);
        float y = (float) Math.floor(size[1]);
        float z = (float) Math.floor(size[2]);
        Map<Direction, GeoModel.FaceUv> faces = new EnumMap<>(Direction.class);
        faces.put(direction(GeoModel.Face.EAST), new GeoModel.FaceUv(u, v + z, z, y));
        faces.put(direction(GeoModel.Face.NORTH), new GeoModel.FaceUv(u + z, v + z, x, y));
        faces.put(direction(GeoModel.Face.WEST), new GeoModel.FaceUv(u + z + x, v + z, z, y));
        faces.put(direction(GeoModel.Face.SOUTH), new GeoModel.FaceUv(u + z + x + z, v + z, x, y));
        faces.put(direction(GeoModel.Face.UP), new GeoModel.FaceUv(u + z, v, x, z));
        faces.put(direction(GeoModel.Face.DOWN), new GeoModel.FaceUv(u + z + x, v + z, x, -z));
        return faces;
    }

    private static boolean whole(float[] values) {
        for (float value : values) {
            if (value != Math.floor(value)) {
                return false;
            }
        }
        return true;
    }

    private static float[] centre(GeoModel.Cube cube) {
        float[] o = cube.origin();
        float[] s = cube.size();
        return new float[]{o[0] + s[0] * 0.5f, o[1] + s[1] * 0.5f, o[2] + s[2] * 0.5f};
    }

    private static boolean zero(float[] rotation) {
        return rotation[0] == 0.0f && rotation[1] == 0.0f && rotation[2] == 0.0f;
    }
}
