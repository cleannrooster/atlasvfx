package dev.eann.atlasvfx;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Puts the pieces of an effect down: pictures turned to face the viewer, soft spots of light,
 * the petals and rings a flash is made of, beams, and a hole into the End.
 *
 * <p>Two ways of being walked. In the world, a frame is drawn in passes, one per way of drawing
 * - see {@link FxPass} - and the caller walks everything once per pass after
 * {@link #begin(FxPass) beginning} it; every method here draws only when the pass is its own
 * and returns at once otherwise. A painter that has not begun a pass draws everything as it
 * comes, switching buffers as the pieces need, which keeps the order they were asked for in:
 * that is how the screen is drawn, where everything is added light and nothing hides anything.</p>
 *
 * <p>Three places to draw. In the world, positions are absolute and the viewer is the camera; in
 * the first-person view, positions are already relative to the eye and nothing needs turning;
 * the difference is only the origin and the facing the painter is made with. On the screen,
 * positions are pixels, with {@code y} running down, and every picture lies flat on it.</p>
 */
public final class FxPainter {

    private static final int RIFT_SIDES = 18;
    private static final int WORLD_GLOW_SIDES = 14;
    private static final int WORLD_RING_SIDES = 32;
    // Light on the screen is drawn far larger, against a still background, where a coarse
    // circle would show its corners.
    private static final int SCREEN_GLOW_SIDES = 48;
    private static final int SCREEN_RING_SIDES = 48;
    /** Turns a picture's own plane, {@code y} up, onto the screen's, {@code y} down. */
    private static final Quaternionf SCREEN_FACING = new Quaternionf().rotationX((float) Math.PI);
    private static final Vector3f OUT_OF_SCREEN = new Vector3f(0.0f, 0.0f, 1.0f);

    private final FxSpace space;
    private final VertexConsumerProvider consumers;
    private final Matrix4f matrix;
    private final Quaternionf facing;
    private final Vec3d origin;
    /**
     * Where the eye is, in the same frame as {@link #local}. The camera is at the origin of the
     * space the matrix draws into, so this is that origin brought back through the matrix: zero
     * when drawing the world from the camera, and the camera's offset from the entity when
     * drawing in an entity's own frame.
     */
    private final Vector3f eye;
    private final int glowSides;
    private final int ringSides;
    /** The pass being drawn; null while drawing everything as it comes. */
    private @Nullable FxPass pass;
    private @Nullable RenderLayer layer;
    private @Nullable VertexConsumer buffer;

    private FxPainter(FxSpace space, VertexConsumerProvider consumers, Matrix4f matrix, Quaternionf facing,
                      Vec3d origin) {
        this.space = space;
        this.consumers = consumers;
        this.matrix = matrix;
        this.facing = facing;
        this.origin = origin;
        this.eye = space == FxSpace.WORLD ? new Matrix4f(matrix).invert().transformPosition(new Vector3f())
                : new Vector3f();
        this.glowSides = space == FxSpace.WORLD ? WORLD_GLOW_SIDES : SCREEN_GLOW_SIDES;
        this.ringSides = space == FxSpace.WORLD ? WORLD_RING_SIDES : SCREEN_RING_SIDES;
    }

    /**
     * A painter for the world, or for the first-person view.
     *
     * @param matrix the position matrix to draw through
     * @param facing turns a picture's own plane to face the viewer
     * @param origin subtracted from every position: the camera in the world, zero in the view
     */
    public static FxPainter world(VertexConsumerProvider consumers, Matrix4f matrix, Quaternionf facing,
                                  Vec3d origin) {
        return new FxPainter(FxSpace.WORLD, consumers, matrix, facing, origin);
    }

    /**
     * A painter for the screen, over everything it has drawn so far: that is drawn first, so
     * this lands on top of it. {@link #finish() Finish} it before the screen draws anything
     * that should go over it.
     */
    public static FxPainter screen(DrawContext context) {
        context.draw();
        return new FxPainter(FxSpace.SCREEN, context.getVertexConsumers(),
                context.getMatrices().peek().getPositionMatrix(), SCREEN_FACING, Vec3d.ZERO);
    }

    /** From here on, draws only what goes in this pass. */
    public void begin(FxPass pass) {
        finish();
        this.pass = pass;
    }

    /**
     * Draws what has been put down, now.
     *
     * <p>The buffers are shared, and a layer left in one is drawn whenever something next empties
     * it - which in first person is the pass that draws the hands, through the hands' own
     * projection and not the world's, so the effect lands in the wrong place and seems to swim
     * as the view moves. Every pass is drawn before the next starts, and the last before the
     * painter is put away, while the world's projection is still the one in force.</p>
     */
    public void finish() {
        if (layer != null) {
            drawNow(consumers, layer);
        }
        layer = null;
        buffer = null;
    }

    /** Draws one layer of a shared buffer now, if the buffer can be told to. */
    public static void drawNow(VertexConsumerProvider consumers, RenderLayer layer) {
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }

    public FxSpace space() {
        return space;
    }

    /** The pass being drawn; null while drawing everything as it comes. */
    @Nullable
    public FxPass pass() {
        return pass;
    }

    /** Turns a picture's own plane to face the viewer. */
    public Quaternionf facing() {
        return facing;
    }

    /** Whether something going in this pass is drawn now. */
    public boolean drawing(FxPass of) {
        return pass == null || pass == of;
    }

    /**
     * The buffer for a pass, or null if this painter is not drawing it now or cannot draw it at
     * all here. Switches layer when drawing everything as it comes: the one being left is drawn
     * first, so nothing is ever drawn out of the order it was put down in.
     */
    @Nullable
    private VertexConsumer buffer(FxPass of) {
        if (!drawing(of)) {
            return null;
        }
        RenderLayer wanted = FxLayers.of(space, of);
        if (wanted == null) {
            return null;
        }
        if (wanted != layer) {
            finish();
            layer = wanted;
        }
        if (buffer == null) {
            buffer = consumers.getBuffer(wanted);
        }
        return buffer;
    }

    // ---- pictures -------------------------------------------------------------------------------------

    /**
     * A picture facing the viewer.
     *
     * @param life  0 to 1, for a picture that flips through its frames
     * @param pick  a fixed number, for one that keeps a frame
     * @param size  across, in blocks
     * @param roll  turn in its own plane, in radians
     * @param crumb whether to show only a quarter of it, chosen by {@code pick}
     */
    public void sprite(FxBlend blend, FxSprite sprite, float life, int pick, boolean flips, Vec3d at,
                       float size, float roll, int rgb, float alpha, int light, boolean crumb) {
        sprite(blend, sprite, life, pick, flips, at, facing, size, roll, rgb, alpha, light, crumb);
    }

    /**
     * A picture lying in a plane rather than facing the viewer: a crack in the ground, a stain
     * on a floor.
     *
     * @param plane turns the picture's own plane - x and y - to where it should lie
     */
    public void sprite(FxBlend blend, FxSprite sprite, float life, int pick, boolean flips, Vec3d at,
                       Quaternionf plane, float size, float roll, int rgb, float alpha, int light, boolean crumb) {
        if (blend == FxBlend.GLOW) {
            glow(at, plane, size * 0.5f, rgb, alpha);
            return;
        }
        if (alpha <= 0.004f || size <= 0.0f || !drawing(FxPass.of(blend, sprite.atlas()))) {
            return;
        }
        Sprite picture = sprite.frame(life, pick, flips);
        if (picture == null) {
            return;
        }
        VertexConsumer out = buffer(FxPass.of(blend, sprite.atlas()));
        if (out == null) {
            return;
        }
        float u0;
        float u1;
        float v0;
        float v1;
        if (crumb) {
            // A quarter of the face, at one of nine places, the way broken-block dust picks one.
            float cu = Math.floorMod(pick, 3) / 4.0f;
            float cv = Math.floorMod(pick / 3, 3) / 4.0f;
            u0 = picture.getFrameU(cu);
            u1 = picture.getFrameU(cu + 0.25f);
            v0 = picture.getFrameV(cv);
            v1 = picture.getFrameV(cv + 0.25f);
        } else {
            u0 = picture.getMinU();
            u1 = picture.getMaxU();
            v0 = picture.getMinV();
            v1 = picture.getMaxV();
        }

        // Turned the other way on the screen: its plane is flipped over to bring y down, which
        // turns a roll round with it.
        float turn = space == FxSpace.SCREEN ? -roll : roll;
        float half = size * 0.5f;
        float cos = (float) Math.cos(turn) * half;
        float sin = (float) Math.sin(turn) * half;
        float x = (float) (at.x - origin.x);
        float y = (float) (at.y - origin.y);
        float z = (float) (at.z - origin.z);
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        boolean lit = blend == FxBlend.LIT;
        // The order the game's own particles use, so the face is the one turned to the viewer.
        corner(out, plane, x, y, z, cos - -sin, sin + -cos, u1, v1, r, g, b, alpha, light, lit);
        corner(out, plane, x, y, z, cos - sin, sin + cos, u1, v0, r, g, b, alpha, light, lit);
        corner(out, plane, x, y, z, -cos - sin, -sin + cos, u0, v0, r, g, b, alpha, light, lit);
        corner(out, plane, x, y, z, -cos - -sin, -sin + -cos, u0, v1, r, g, b, alpha, light, lit);
    }

    private void corner(VertexConsumer out, Quaternionf plane, float x, float y, float z, float lx, float ly,
                        float u, float v, float r, float g, float b, float a, int light, boolean lit) {
        Vector3f p = plane.transform(new Vector3f(lx, ly, 0.0f)).add(x, y, z);
        if (space == FxSpace.SCREEN) {
            out.vertex(matrix, p.x, p.y, p.z).texture(u, v).color(r, g, b, a);
        } else if (lit) {
            out.vertex(matrix, p.x, p.y, p.z).color(r, g, b, a).texture(u, v)
                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0.0f, 1.0f, 0.0f);
        } else {
            out.vertex(matrix, p.x, p.y, p.z).texture(u, v).color(r, g, b, a).light(light);
        }
    }

    // ---- light ------------------------------------------------------------------------------------------

    /** A soft round spot of light facing the viewer: full at the middle, nothing at the edge. */
    public void glow(Vec3d at, float radius, int rgb, float alpha) {
        glow(at, facing, radius, rgb, alpha);
    }

    /**
     * A soft round spot of light lying in a plane: a scorch of light on the ground under a nova,
     * say, rather than one turned to the eye.
     */
    public void glow(Vec3d at, Quaternionf plane, float radius, int rgb, float alpha) {
        if (alpha <= 0.004f || radius <= 0.0f) {
            return;
        }
        VertexConsumer out = buffer(FxPass.GLOW);
        if (out == null) {
            return;
        }
        Vector3f centre = local(at);
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        Vector3f previous = rim(centre, plane, radius, 0);
        for (int i = 1; i <= glowSides; i++) {
            Vector3f next = rim(centre, plane, radius, i / (float) glowSides);
            // A wedge, as a quad with its point doubled.
            put(out, centre, r, g, b, alpha);
            put(out, previous, r, g, b, 0.0f);
            put(out, next, r, g, b, 0.0f);
            put(out, centre, r, g, b, alpha);
            previous = next;
        }
    }

    /**
     * A thin ring of light lying in a plane: brightest along its middle line, fading to either
     * edge.
     *
     * @param plane turns the ring's own flat plane - x and y - to where it should lie
     */
    public void ring(Vec3d at, Quaternionf plane, float radius, float width, int rgb, float alpha) {
        if (alpha <= 0.004f || radius <= 0.0f || width <= 0.0f) {
            return;
        }
        VertexConsumer out = buffer(FxPass.GLOW);
        if (out == null) {
            return;
        }
        Vector3f centre = local(at);
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float inner = Math.max(0.0f, radius - width * 0.5f);
        float outer = radius + width * 0.5f;
        for (int i = 0; i < ringSides; i++) {
            float t0 = i / (float) ringSides;
            float t1 = (i + 1) / (float) ringSides;
            Vector3f in0 = rim(centre, plane, inner, t0);
            Vector3f in1 = rim(centre, plane, inner, t1);
            Vector3f mid0 = rim(centre, plane, radius, t0);
            Vector3f mid1 = rim(centre, plane, radius, t1);
            Vector3f out0 = rim(centre, plane, outer, t0);
            Vector3f out1 = rim(centre, plane, outer, t1);
            put(out, in0, r, g, b, 0.0f);
            put(out, mid0, r, g, b, alpha);
            put(out, mid1, r, g, b, alpha);
            put(out, in1, r, g, b, 0.0f);
            put(out, mid0, r, g, b, alpha);
            put(out, out0, r, g, b, 0.0f);
            put(out, out1, r, g, b, 0.0f);
            put(out, mid1, r, g, b, alpha);
        }
    }

    /**
     * One blade of a flash: hot at its root, its colour along its sides, nothing at its tip.
     *
     * @param along  the way the blade points, a unit vector, already in this painter's space
     * @param across the way it is wide, a unit vector at right angles to {@code along}
     */
    public void petal(Vec3d at, Vector3f along, Vector3f across, float length, float width,
                      int hot, int rgb, float alpha) {
        if (alpha <= 0.004f || length <= 0.0f) {
            return;
        }
        VertexConsumer out = buffer(FxPass.GLOW);
        if (out == null) {
            return;
        }
        Vector3f root = local(at);
        Vector3f shoulder = new Vector3f(along).mul(length * 0.3f).add(root);
        Vector3f left = new Vector3f(across).mul(width * 0.5f).add(shoulder);
        Vector3f right = new Vector3f(across).mul(-width * 0.5f).add(shoulder);
        Vector3f tip = new Vector3f(along).mul(length).add(root);
        float hr = (hot >> 16 & 0xFF) / 255.0f;
        float hg = (hot >> 8 & 0xFF) / 255.0f;
        float hb = (hot & 0xFF) / 255.0f;
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        put(out, root, hr, hg, hb, alpha);
        put(out, left, r, g, b, alpha * 0.6f);
        put(out, tip, r, g, b, 0.0f);
        put(out, right, r, g, b, alpha * 0.6f);
    }

    /**
     * A straight band of light from one point to another, turned about its own length to face
     * the viewer, so it reads as a solid shaft from any side: brightest down its middle, fading
     * to nothing at either edge. Beams, lasers and the segments of an arc are all this.
     */
    public void beam(Vec3d from, Vec3d to, float width, int rgb, float alpha) {
        if (alpha <= 0.004f || width <= 0.0f) {
            return;
        }
        Vector3f a = local(from);
        Vector3f b = local(to);
        Vector3f along = new Vector3f(b).sub(a);
        if (along.lengthSquared() < 1.0e-8f) {
            return;
        }
        VertexConsumer out = buffer(FxPass.GLOW);
        if (out == null) {
            return;
        }
        // Across the beam and across the line of sight to it. The screen is looked at straight on.
        Vector3f sight;
        if (space == FxSpace.SCREEN) {
            sight = new Vector3f(OUT_OF_SCREEN);
        } else {
            sight = new Vector3f(a).add(b).mul(0.5f).sub(eye);
            if (sight.lengthSquared() < 1.0e-6f) {
                sight = facing.transform(new Vector3f(0.0f, 0.0f, 1.0f));
            }
        }
        Vector3f side = new Vector3f(along).cross(sight);
        if (side.lengthSquared() < 1.0e-8f) {
            side = perpendicularTo(along);
        }
        side.normalize().mul(width * 0.5f);
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float bl = (rgb & 0xFF) / 255.0f;
        put(out, new Vector3f(a).sub(side), r, g, bl, 0.0f);
        put(out, a, r, g, bl, alpha);
        put(out, b, r, g, bl, alpha);
        put(out, new Vector3f(b).sub(side), r, g, bl, 0.0f);
        put(out, a, r, g, bl, alpha);
        put(out, new Vector3f(a).add(side), r, g, bl, 0.0f);
        put(out, new Vector3f(b).add(side), r, g, bl, 0.0f);
        put(out, b, r, g, bl, alpha);
    }

    private static Vector3f perpendicularTo(Vector3f v) {
        Vector3f helper = Math.abs(v.y) < 0.9f ? new Vector3f(0.0f, 1.0f, 0.0f) : new Vector3f(1.0f, 0.0f, 0.0f);
        return helper.cross(v);
    }

    // ---- the End ------------------------------------------------------------------------------------------

    /**
     * A round hole facing the viewer, through which the End's starfield shows, exactly as it does
     * through an end portal: the sky is drawn in screen space, so it does not move with the hole.
     * Only in the world; the screen has no End to look into.
     */
    public void rift(Vec3d at, float radius, float roll) {
        if (radius <= 0.002f) {
            return;
        }
        VertexConsumer out = buffer(FxPass.RIFT);
        if (out == null) {
            return;
        }
        Vector3f centre = local(at);
        Vector3f previous = rim(centre, facing, radius, roll);
        for (int i = 1; i <= RIFT_SIDES; i++) {
            // A little ragged, so it reads as torn rather than cut.
            float wobble = 1.0f + 0.12f * (float) Math.sin(i * 2.7f + roll * 9.0f);
            Vector3f next = rim(centre, facing, radius * wobble, roll + i / (float) RIFT_SIDES);
            // Both windings: this layer culls, and the hole should show from either side.
            out.vertex(matrix, centre.x, centre.y, centre.z);
            out.vertex(matrix, previous.x, previous.y, previous.z);
            out.vertex(matrix, next.x, next.y, next.z);
            out.vertex(matrix, centre.x, centre.y, centre.z);
            out.vertex(matrix, centre.x, centre.y, centre.z);
            out.vertex(matrix, next.x, next.y, next.z);
            out.vertex(matrix, previous.x, previous.y, previous.z);
            out.vertex(matrix, centre.x, centre.y, centre.z);
            previous = next;
        }
    }

    // ---- on the screen ---------------------------------------------------------------------------------------

    /** A soft round spot of light on the screen. */
    public void glow(float x, float y, float radius, int rgb, float alpha) {
        glow(new Vec3d(x, y, 0.0), facing, radius, rgb, alpha);
    }

    /** A thin ring of light on the screen. */
    public void ring(float x, float y, float radius, float width, int rgb, float alpha) {
        ring(new Vec3d(x, y, 0.0), facing, radius, width, rgb, alpha);
    }

    /** A soft straight line on the screen: brightest down its middle, fading to either side. */
    public void beam(float x0, float y0, float x1, float y1, float width, int rgb, float alpha) {
        beam(new Vec3d(x0, y0, 0.0), new Vec3d(x1, y1, 0.0), width, rgb, alpha);
    }

    /** A picture on the screen, as light. */
    public void sprite(FxSprite sprite, float life, int pick, boolean flips, float x, float y, float size,
                       float roll, int rgb, float alpha) {
        sprite(FxBlend.EMISSIVE, sprite, life, pick, flips, x, y, size, roll, rgb, alpha);
    }

    /** A picture on the screen: added as light, or laid over as a solid thing. */
    public void sprite(FxBlend blend, FxSprite sprite, float life, int pick, boolean flips, float x, float y,
                       float size, float roll, int rgb, float alpha) {
        sprite(blend, sprite, life, pick, flips, new Vec3d(x, y, 0.0), facing, size, roll, rgb, alpha,
                FxLight.FULL_BRIGHT, false);
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    /** Where a position is relative to this painter's origin. */
    public Vector3f local(Vec3d at) {
        return new Vector3f((float) (at.x - origin.x), (float) (at.y - origin.y), (float) (at.z - origin.z));
    }

    /** A point on a circle in a turned plane; {@code turn} is a fraction of a full circle. */
    private static Vector3f rim(Vector3f centre, Quaternionf plane, float radius, float turn) {
        double angle = turn * Math.PI * 2.0;
        return plane.transform(new Vector3f((float) Math.cos(angle) * radius, (float) Math.sin(angle) * radius, 0.0f))
                .add(centre);
    }

    private void put(VertexConsumer out, Vector3f p, float r, float g, float b, float a) {
        out.vertex(matrix, p.x, p.y, p.z).color(r, g, b, a);
    }
}
