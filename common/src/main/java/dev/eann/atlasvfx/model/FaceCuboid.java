package dev.eann.atlasvfx.model;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * A cuboid whose faces each take their own area of the texture: what a geometry file's per-face UV
 * describes, and the game's cuboids cannot - they are built from box unwraps only.
 *
 * <p>Built from the game's own cuboid for the same box: drawn once into a recorder, it says where
 * each face's corners are and which corner of the box unwrap each one takes. Each corner then takes
 * the same corner of the file's area for that face instead - so the area lies on the face exactly as
 * a box unwrap's would, mirroring included, and a negative size in the file flips it as it says.</p>
 */
public final class FaceCuboid extends ModelPart.Cuboid {

    private final float[] positions;
    private final float[] uvs;
    private final float[] normals;
    private final int faces;

    private FaceCuboid(float x, float y, float z, float sizeX, float sizeY, float sizeZ, float inflate,
                       boolean mirror, float textureWidth, float textureHeight, float[] positions, float[] uvs,
                       float[] normals) {
        // No faces of its own: it draws its own instead.
        super(0, 0, x, y, z, sizeX, sizeY, sizeZ, inflate, inflate, inflate, mirror, textureWidth, textureHeight,
                EnumSet.noneOf(Direction.class));
        this.positions = positions;
        this.uvs = uvs;
        this.normals = normals;
        this.faces = normals.length / 3;
    }

    /**
     * A cuboid with each face's area as the file gives it.
     *
     * @param faces the part's own faces, with the file's area for each
     */
    public static FaceCuboid of(float x, float y, float z, float sizeX, float sizeY, float sizeZ, float inflate,
                                boolean mirror, float textureWidth, float textureHeight,
                                Map<Direction, GeoModel.FaceUv> faces) {
        ModelPart.Cuboid box = new ModelPart.Cuboid(0, 0, x, y, z, sizeX, sizeY, sizeZ, inflate, inflate, inflate,
                mirror, textureWidth, textureHeight, EnumSet.allOf(Direction.class));
        Recorder recorder = new Recorder();
        box.renderCuboid(new MatrixStack().peek(), recorder, 0, 0, -1);
        List<float[]> kept = new ArrayList<>();
        for (int quad = 0; quad < recorder.count / 4; quad++) {
            Region region = Region.of(recorder, quad, sizeX, sizeY, sizeZ, textureWidth, textureHeight);
            GeoModel.FaceUv area = region == null ? null : faces.get(region.face);
            if (area == null) {
                continue;
            }
            for (int corner = 0; corner < 4; corner++) {
                int at = quad * 4 + corner;
                float u = recorder.u[at] * textureWidth;
                float v = recorder.v[at] * textureHeight;
                boolean firstU = Math.abs(u - region.u1) <= Math.abs(u - region.u2);
                boolean firstV = Math.abs(v - region.v1) <= Math.abs(v - region.v2);
                kept.add(new float[]{recorder.x[at], recorder.y[at], recorder.z[at],
                        (firstU ? area.u() : area.u() + area.width()) / textureWidth,
                        (firstV ? area.v() : area.v() + area.height()) / textureHeight,
                        recorder.nx[at], recorder.ny[at], recorder.nz[at]});
            }
        }
        int corners = kept.size();
        float[] positions = new float[corners * 3];
        float[] uvs = new float[corners * 2];
        float[] normals = new float[corners / 4 * 3];
        for (int i = 0; i < corners; i++) {
            float[] c = kept.get(i);
            positions[i * 3] = c[0];
            positions[i * 3 + 1] = c[1];
            positions[i * 3 + 2] = c[2];
            uvs[i * 2] = c[3];
            uvs[i * 2 + 1] = c[4];
            if (i % 4 == 0) {
                normals[i / 4 * 3] = c[5];
                normals[i / 4 * 3 + 1] = c[6];
                normals[i / 4 * 3 + 2] = c[7];
            }
        }
        return new FaceCuboid(x, y, z, sizeX, sizeY, sizeZ, inflate, mirror, textureWidth, textureHeight, positions,
                uvs, normals);
    }

    /** How many faces it draws. */
    public int faceCount() {
        return faces;
    }

    @Override
    public void renderCuboid(MatrixStack.Entry entry, VertexConsumer consumer, int light, int overlay, int color) {
        Matrix4f matrix = entry.getPositionMatrix();
        Vector3f normal = new Vector3f();
        Vector4f point = new Vector4f();
        for (int face = 0; face < faces; face++) {
            entry.transformNormal(normals[face * 3], normals[face * 3 + 1], normals[face * 3 + 2], normal);
            for (int corner = 0; corner < 4; corner++) {
                int at = face * 4 + corner;
                // Positions are in pixels, as the game's own cuboids keep them.
                matrix.transform(positions[at * 3] / 16.0f, positions[at * 3 + 1] / 16.0f,
                        positions[at * 3 + 2] / 16.0f, 1.0f, point);
                consumer.vertex(point.x(), point.y(), point.z(), color, uvs[at * 2], uvs[at * 2 + 1], overlay, light,
                        normal.x(), normal.y(), normal.z());
            }
        }
    }

    /** One face of a box unwrap at the origin: which face it is and the corners the game gave it. */
    private record Region(Direction face, float u1, float v1, float u2, float v2) {

        /**
         * Which face {@code quad} is, by the area of the box unwrap it took - the game's own label
         * for that area, whichever way a mirrored cuboid turned it.
         */
        static Region of(Recorder recorder, int quad, float sx, float sy, float sz, float width, float height) {
            float minU = Float.MAX_VALUE;
            float minV = Float.MAX_VALUE;
            float maxV = -Float.MAX_VALUE;
            for (int corner = 0; corner < 4; corner++) {
                minU = Math.min(minU, recorder.u[quad * 4 + corner] * width);
                minV = Math.min(minV, recorder.v[quad * 4 + corner] * height);
                maxV = Math.max(maxV, recorder.v[quad * 4 + corner] * height);
            }
            Region[] regions = {
                    new Region(Direction.DOWN, sz, 0, sz + sx, sz),
                    new Region(Direction.UP, sz + sx, sz, sz + 2 * sx, 0),
                    new Region(Direction.WEST, 0, sz, sz, sz + sy),
                    new Region(Direction.NORTH, sz, sz, sz + sx, sz + sy),
                    new Region(Direction.EAST, sz + sx, sz, 2 * sz + sx, sz + sy),
                    new Region(Direction.SOUTH, 2 * sz + sx, sz, 2 * sz + 2 * sx, sz + sy),
            };
            Region best = null;
            float bestError = Float.MAX_VALUE;
            for (Region region : regions) {
                float error = Math.abs(Math.min(region.u1, region.u2) - minU)
                        + Math.abs(Math.min(region.v1, region.v2) - minV)
                        + Math.abs(Math.max(region.v1, region.v2) - maxV);
                if (error < bestError) {
                    bestError = error;
                    best = region;
                }
            }
            return best;
        }
    }

    /** Keeps what a cuboid draws: each corner's place, texture corner and normal. */
    private static final class Recorder implements VertexConsumer {
        float[] x = new float[24];
        float[] y = new float[24];
        float[] z = new float[24];
        float[] u = new float[24];
        float[] v = new float[24];
        float[] nx = new float[24];
        float[] ny = new float[24];
        float[] nz = new float[24];
        int count;

        @Override
        public void vertex(float px, float py, float pz, int color, float pu, float pv, int overlay, int light,
                           float normalX, float normalY, float normalZ) {
            if (count >= x.length) {
                return;
            }
            // Back to pixels: the game's cuboid drew them in blocks.
            x[count] = px * 16.0f;
            y[count] = py * 16.0f;
            z[count] = pz * 16.0f;
            u[count] = pu;
            v[count] = pv;
            nx[count] = normalX;
            ny[count] = normalY;
            nz[count] = normalZ;
            count++;
        }

        @Override
        public VertexConsumer vertex(float px, float py, float pz) {
            throw new UnsupportedOperationException("cuboids draw whole vertices");
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float pu, float pv) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int pu, int pv) {
            return this;
        }

        @Override
        public VertexConsumer light(int pu, int pv) {
            return this;
        }

        @Override
        public VertexConsumer normal(float px, float py, float pz) {
            return this;
        }
    }
}
