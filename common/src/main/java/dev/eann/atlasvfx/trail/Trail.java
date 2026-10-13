package dev.eann.atlasvfx.trail;

import dev.eann.atlasvfx.FxAnchor;
import dev.eann.atlasvfx.FxBlend;
import dev.eann.atlasvfx.FxColor;
import dev.eann.atlasvfx.FxCurve;
import dev.eann.atlasvfx.FxLife;
import dev.eann.atlasvfx.FxLight;
import dev.eann.atlasvfx.FxPainter;
import dev.eann.atlasvfx.FxPass;
import dev.eann.atlasvfx.FxShape;
import dev.eann.atlasvfx.FxWorld;
import dev.eann.atlasvfx.SurfaceGrid;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * A trail on a swept surface: a slash through its arc, laid in bands, seen being cut.
 *
 * <p>Nothing about its shape is kept between frames. Each one, the surface says where it is, the
 * window says which part of it shows, and each band is laid over that part as a grid of points
 * and handed to the painter - twice, either side of the surface, when the trail has thickness, so
 * it reads as a solid slab from any side. The grids are kept and refilled, so it allocates nothing
 * while it plays.</p>
 *
 * <p>Everything that shapes it - the crescent, teeth, spikes, the clearance in front of the
 * viewer's own eyes - only ever takes surface away. A trail on a surface that matches an attack
 * can never be drawn over space that attack cannot reach.</p>
 */
public final class Trail implements FxShape {

    /** Layers with no thickness to stack in are this far apart, so they never fight over depth. */
    private static final float FLAT_SEPARATION = 0.004f;
    /** Never quite no width at the tail: a band with no area draws nothing worth having. */
    private static final float NARROWEST = 0.02f;

    private final SweptSurface surface;
    private final TrailStyle style;
    private final TrailWindow window;
    private final long seed;
    private final SurfaceGrid grid = new SurfaceGrid();
    private final double[] point = new double[3];
    /** Each band's outline, worked out the first time it is drawn: it never changes after. */
    private final @Nullable Outline[] outlines;

    /**
     * Where a band's teeth or spikes fall, fixed by the trail's seed.
     *
     * @param rowNotch    per column, how far it stops short of both ends; null if none do
     * @param columnNotch per column, how far it stops short of the tail; null if none do
     * @param valley      per row, how far the band's inner side is pulled outward; null if not
     */
    private record Outline(int rows, int columns, float @Nullable [] rowNotch, float @Nullable [] columnNotch,
                           float @Nullable [] valley) {
    }

    /** @param seed fixes its spikes and teeth, so they hold still from frame to frame */
    public Trail(SweptSurface surface, TrailStyle style, TrailWindow window, long seed) {
        this.surface = surface;
        this.style = style;
        this.window = window;
        this.seed = seed;
        this.outlines = new Outline[style.layers().size()];
    }

    /**
     * A slash through an arc: cut over {@code swingTicks}, the whole arc kept showing behind its
     * leading edge so the finished trail covers everything the swing passed through, then faded
     * over {@code fadeTicks}.
     *
     * @param reach     how far out the blade reaches, in blocks
     * @param halfAngle half the arc, in degrees off the frame's forward; 180 is a full circle
     */
    public static Trail slash(SweepFrameSource frame, float reach, float halfAngle, boolean reversed,
                              ArcSweep.Plane plane, int swingTicks, int fadeTicks, TrailStyle style, long seed) {
        return new Trail(new ArcSweep(frame, reach, halfAngle, reversed, plane), style,
                TrailWindow.swing(swingTicks, 1.0f, fadeTicks), seed);
    }

    /**
     * A ribbon behind something moving - a projectile, a dash, a wisp - facing the viewer and
     * narrowing toward its tail, kept {@code maxAge} ticks long. It lasts until its anchor lets go
     * or it is released, then runs off.
     *
     * @param width across, in blocks, at the head
     */
    public static Trail point(FxAnchor anchor, float width, int maxAge, TrailStyle style, long seed) {
        return new Trail(SampledSweep.point(anchor, width, FxCurve.easeOut(1.5f), maxAge), style,
                TrailWindow.follow(), seed);
    }

    /**
     * The path a blade sweeps, from its hilt to its tip, kept {@code maxAge} ticks long: for a
     * swing whose path is not known in advance. Lasts until either anchor lets go or it is
     * released, then runs off.
     */
    public static Trail blade(FxAnchor hilt, FxAnchor tip, int maxAge, TrailStyle style, long seed) {
        return new Trail(SampledSweep.blade(hilt, tip, maxAge), style, TrailWindow.follow(), seed);
    }

    /**
     * A thrust down the frame's forward direction: a barbed spearhead extending to {@code reach}
     * over {@code extendTicks}, then fading over {@code fadeTicks}.
     *
     * @param halfWidth half its widest width, in blocks
     * @param halfAngle the thrust's cone, in degrees either side of forward; 0 for none
     */
    public static Trail thrust(SweepFrameSource frame, float reach, float halfWidth, float halfAngle,
                               int extendTicks, int fadeTicks, TrailStyle style, long seed) {
        return new Trail(new LaneSweep(frame, reach, halfWidth, halfAngle, extendTicks, 7, 0.35f,
                style.clearance(), seed), style, TrailWindow.hold(extendTicks, fadeTicks), seed);
    }

    @Override
    public void tick(FxWorld world, FxLife life, float density) {
        surface.tick(life);
    }

    @Override
    public boolean done(FxLife life) {
        return window.done(life) || surface.finished(life);
    }

    @Override
    public void draw(FxPainter painter, @Nullable ClientWorld world, FxLife life, float tickDelta) {
        FxPass pass = style.textured() ? FxPass.of(style.blend(), style.grain().atlas()) : FxPass.GLOW;
        if (!painter.drawing(pass)) {
            return;
        }
        float head = window.head(life, tickDelta);
        float tail = window.tail(life, tickDelta);
        float fade = window.fade(life, tickDelta);
        if (head - tail < 1.0e-4f || fade <= 0.01f || !surface.prepare(tickDelta)) {
            return;
        }
        float inner = style.inner();
        if (surface.viewer() && surface.reach() > 0.0f) {
            inner = Math.max(inner, style.clearance() / surface.reach());
        }
        if (inner >= 0.999f) {
            return;
        }

        int light = FxLight.FULL_BRIGHT;
        if (style.blend() == FxBlend.LIT) {
            surface.point(head, 0.0f, point);
            light = FxLight.withMinBlock(FxLight.at(world, new Vec3d(point[0], point[1], point[2])),
                    style.minBlockLight());
        }
        float flash = life.flash(tickDelta, style.flashTicks());
        Vector3f normal = surface.normal();
        List<TrailLayer> layers = style.layers();
        for (int i = 0; i < layers.size(); i++) {
            TrailLayer layer = layers.get(i);
            float opacity = layer.opacity() * fade;
            if (opacity <= 0.004f) {
                continue;
            }
            build(layer, i, tail, head, inner, opacity, flash);
            if (style.thickness() > 0.001f) {
                float offset = style.thickness() * layer.depth();
                painter.surface(grid, style.blend(), style.grain(), style.window(), (int) seed, light, normal, offset);
                painter.surface(grid, style.blend(), style.grain(), style.window(), (int) seed, light, normal, -offset);
            } else {
                painter.surface(grid, style.blend(), style.grain(), style.window(), (int) seed, light, normal,
                        FLAT_SEPARATION * i);
            }
        }
    }

    /**
     * Lays one band over the visible part of the surface, into the grid.
     *
     * @param tail    where the visible part starts along the surface
     * @param head    where it ends
     * @param inner   where the trail starts across the surface at its head
     * @param opacity the band's opacity, faded
     * @param flash   how strongly a flash shows, 0 to 1
     */
    void build(TrailLayer layer, int index, float tail, float head, float inner, float opacity, float flash) {
        Outline outline = outline(layer, index);
        int rows = outline.rows();
        int columns = outline.columns();
        float[] rowNotch = outline.rowNotch();
        float[] columnNotch = outline.columnNotch();
        float[] valley = outline.valley();

        grid.reset(rows, columns);
        boolean shaped = !surface.closed() && style.tailWidth() < 1.0f;
        int flashColour = style.flashColour();
        for (int r = 0; r <= rows; r++) {
            float rowFraction = r / (float) rows;
            for (int c = 0; c <= columns; c++) {
                float columnFraction = c / (float) columns;
                // Along: teeth stop short at both ends, tail spikes only at the tail.
                float rf;
                if (rowNotch != null) {
                    rf = lerp(rowFraction, rowNotch[c], 1.0f - rowNotch[c]);
                } else if (columnNotch != null) {
                    rf = lerp(rowFraction, columnNotch[c], 1.0f);
                } else {
                    rf = rowFraction;
                }
                // Across: columns crowd toward the outside as asked, and inner spikes pull the
                // band's inner side outward.
                float packed = layer.packing() > 1.0f
                        ? 1.0f - (float) Math.pow(1.0f - columnFraction, layer.packing())
                        : columnFraction;
                float start = valley != null ? lerp(valley[r], layer.bandStart(), layer.bandEnd()) : layer.bandStart();
                float band = lerp(packed, start, layer.bandEnd());
                float local = (band - layer.bandStart()) / (layer.bandEnd() - layer.bandStart());

                float s = lerp(rf, tail, head);
                float t = lerp(band, shaped ? crescentInner(inner, rf) : inner, 1.0f);
                surface.point(s, t, point);

                float alpha = layer.alongAlpha().at(rf) * layer.acrossAlpha().at(local);
                // A flash lifts every point toward full, so the confirmed trail shows itself whole.
                alpha = opacity * lerp(flash, alpha, 1.0f);
                int colour = layer.colour().at(rf);
                if (flash > 0.0f) {
                    colour = FxColor.mix(colour, flashColour, flash);
                }
                grid.set(r, c, point[0], point[1], point[2], colour, alpha);
            }
        }
    }

    /** A band's outline, from the cache or worked out now. */
    private Outline outline(TrailLayer layer, int index) {
        Outline outline = outlines[index];
        if (outline == null) {
            outline = outlineOf(layer, index);
            outlines[index] = outline;
        }
        return outline;
    }

    private Outline outlineOf(TrailLayer layer, int index) {
        TrailEdging edging = layer.edging();
        boolean closed = surface.closed();
        if (edging instanceof TrailEdging.Serrated serrated) {
            int columns = serrated.teeth() * 2;
            // Alternate columns stop short of both ends; the quads between are the teeth. The
            // outermost column runs the full length, so the outer edge is never notched. A full
            // circle has no ends to cut.
            float[] notch = new float[columns + 1];
            for (int c = 1; c < columns; c += 2) {
                notch[c] = closed ? 0.0f : serrated.depth();
            }
            return new Outline(style.alongSegments(), columns, notch, null, null);
        }
        if (edging instanceof TrailEdging.Spiked spiked) {
            // Each inner spike spans four rows - tip, slope, valley, slope - so its tip lands on a
            // point and stays sharp; each tail spike is a pair of columns.
            int rows = spiked.count() * 4;
            int columns = spiked.count() * 2;
            Random random = new Random(seed ^ (index * 0x9E3779B97F4A7C15L));
            float[] valley = new float[rows + 1];
            for (int k = 0; k < spiked.count(); k++) {
                float length = spiked.maxLength() * (0.4f + 0.6f * random.nextFloat());
                valley[k * 4 + 1] = length * 0.5f;
                valley[k * 4 + 2] = length;
                valley[k * 4 + 3] = length * 0.5f;
            }
            float[] notch = new float[columns + 1];
            if (!closed) {
                for (int c = 1; c < columns; c += 2) {
                    notch[c] = spiked.maxLength() * (0.4f + 0.6f * random.nextFloat());
                }
            }
            return new Outline(rows, columns, null, notch, valley);
        }
        return new Outline(style.alongSegments(), layer.columns(), null, null, null);
    }

    /**
     * Where the trail starts across the surface at {@code rf} along it: at {@code inner} at the
     * head, sliding outward toward the tail as the trail thins into a crescent. Never inside
     * {@code inner}, so it only ever takes surface away.
     */
    private float crescentInner(float inner, float rf) {
        float width = lerp(FxCurve.SMOOTH.at(rf), style.tailWidth(), 1.0f);
        return 1.0f - (1.0f - inner) * Math.max(width, NARROWEST);
    }

    /** The grid as last built, for checking what a band covers. */
    SurfaceGrid grid() {
        return grid;
    }

    private static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }
}
