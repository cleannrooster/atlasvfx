package dev.eann.atlasvfx.aura;

import dev.eann.atlasvfx.FxBlend;
import dev.eann.atlasvfx.FxLight;
import dev.eann.atlasvfx.FxShapes;
import dev.eann.atlasvfx.FxSprite;
import dev.eann.atlasvfx.FxStyle;
import dev.eann.atlasvfx.FxWorld;
import dev.eann.atlasvfx.MoteRecipe;
import dev.eann.atlasvfx.UvWindow;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Random;

/**
 * One part of an aura: what is drawn at a body's feet, round it, or off it. An aura stacks several.
 *
 * <p>Sizes are given against the body, so one aura fits a player, a wolf and a ravager alike.
 * Everything that moves is driven by the aura's clock, never by chance per frame, so it looks the
 * same at any frame rate; only {@link Rise} throws motes, and those count against the shared mote
 * budget and the density the aura was played at.</p>
 */
public sealed interface AuraLayer
        permits AuraLayer.GroundSigil, AuraLayer.Shell, AuraLayer.Orbit, AuraLayer.Rise, AuraLayer.Helix {

    /**
     * Whether it would fill the screen of someone looking out of the body it surrounds: those are
     * not drawn in first person.
     */
    boolean fillsView();

    /** Puts it down for this frame. */
    void draw(AuraFrame frame);

    /**
     * What it throws off this tick.
     *
     * @param feet    the bottom middle of the body
     * @param scale   what sizes given in blocks are multiplied by on this body
     * @param density how much to make, as a share
     */
    default void tick(FxWorld world, FxStyle style, Vec3d feet, float height, float width, float scale,
                      float density) {
    }

    /**
     * A circle lying at the feet, turning: rings of light and, if it has any, the enchanting
     * table's glyphs set round it.
     *
     * @param radius blocks out from the middle of the body
     * @param spin   radians a tick; negative turns the other way
     * @param glyphs how many glyphs; 0 for rings alone
     */
    record GroundSigil(float radius, float spin, int glyphs) implements AuraLayer {

        private static final FxSprite GLYPHS = FxSprite.particleSet("sga_a", "sga_c", "sga_e", "sga_h", "sga_k",
                "sga_m", "sga_o", "sga_r", "sga_u", "sga_x");

        @Override
        public boolean fillsView() {
            return false;
        }

        @Override
        public void draw(AuraFrame frame) {
            float s = frame.strength();
            float radius = this.radius * frame.scale();
            // Just off the ground, so it does not flicker with it.
            Vec3d at = frame.feet().add(0.0, 0.02, 0.0);
            FxStyle style = frame.style();
            float breathe = 0.85f + 0.15f * MathHelper.sin(frame.time() * 0.08f);
            frame.painter().glow(at, FxShapes.FLAT, radius * 1.15f, style.glow(), 0.16f * s * breathe);
            frame.painter().ring(at, FxShapes.FLAT, radius, radius * 0.06f, style.glow(), 0.75f * s);
            frame.painter().ring(at, FxShapes.FLAT, radius * 0.74f, radius * 0.035f, style.hot(), 0.55f * s * breathe);
            if (glyphs <= 0) {
                return;
            }
            float turn = frame.time() * spin;
            float size = radius * 0.24f;
            for (int i = 0; i < glyphs; i++) {
                float angle = turn + i * MathHelper.TAU / glyphs;
                Vec3d spot = at.add(MathHelper.cos(angle) * radius * 0.87f, 0.005,
                        MathHelper.sin(angle) * radius * 0.87f);
                float flicker = 0.65f + 0.35f * MathHelper.sin(frame.time() * 0.15f + i * 1.7f);
                frame.painter().sprite(FxBlend.EMISSIVE, GLYPHS, 0.0f, (int) (frame.seed() + i), false, spot,
                        FxShapes.FLAT, size, -angle, style.hot(), s * flicker, FxLight.FULL_BRIGHT, false);
            }
        }
    }

    /**
     * A shell of light round the body, brightest along its outline.
     *
     * @param scale   its size against the body's: 1 just clears it
     * @param rim     how strongly the outline shows, 0 to 1
     * @param face    how strongly the part seen square-on shows
     * @param shimmer how strongly bands of light run up it, 0 for none
     */
    record Shell(ShellShape shape, float scale, float rim, float face, float shimmer) implements AuraLayer {

        @Override
        public boolean fillsView() {
            return true;
        }

        @Override
        public void draw(AuraFrame frame) {
            float radius;
            float height = frame.height() * 1.1f * scale;
            switch (shape) {
                case SPHERE -> radius = Math.max(frame.height(), frame.width()) * 0.55f * scale;
                case DOME -> radius = Math.max(frame.height() * 1.05f, frame.width()) * scale;
                default -> radius = frame.width() * 0.9f * scale;
            }
            Vec3d base = shape == ShellShape.SPHERE
                    ? frame.feet().add(0.0, frame.height() * 0.5 - radius, 0.0)
                    : frame.feet();
            Shells.fill(frame.grid(), shape, base, radius, height, frame.painter().viewer(), frame.style().glow(),
                    frame.style().hot(), rim, face, shimmer, frame.time(), frame.strength());
            frame.painter().surface(frame.grid(), FxBlend.GLOW, null, UvWindow.FULL, 0, FxLight.FULL_BRIGHT,
                    new Vector3f(0.0f, 1.0f, 0.0f), 0.0f);
        }
    }

    /**
     * Sparks of light circling the body, each with a short tail.
     *
     * @param count  how many
     * @param reach  blocks out from the body's side
     * @param level  how high round the body, as a share of its height
     * @param speed  radians a tick
     * @param size   how big each one is, in blocks
     */
    record Orbit(int count, float reach, float level, float speed, float size) implements AuraLayer {

        private static final int TAIL = 5;

        @Override
        public boolean fillsView() {
            return true;
        }

        @Override
        public void draw(AuraFrame frame) {
            float s = frame.strength();
            float radius = frame.width() * 0.5f + reach * frame.scale();
            float size = this.size * frame.scale();
            FxStyle style = frame.style();
            for (int i = 0; i < count; i++) {
                float phase = i * MathHelper.TAU / count;
                // Each on its own slight tilt, so together they read as a swarm rather than a wheel.
                float tilt = 0.25f * MathHelper.sin(phase * 3.0f + frame.seed() % 7);
                Vec3d head = point(frame, radius, phase, tilt, frame.time());
                frame.painter().glow(head, size, style.glow(), 0.85f * s);
                frame.painter().glow(head, size * 0.4f, style.hot(), s);
                Vec3d previous = head;
                for (int k = 1; k <= TAIL; k++) {
                    Vec3d next = point(frame, radius, phase, tilt, frame.time() - k * 1.2f);
                    frame.painter().beam(previous, next, size * 0.5f * (1.0f - k / (float) (TAIL + 1)), style.glow(),
                            0.5f * s * (1.0f - k / (float) (TAIL + 1)));
                    previous = next;
                }
            }
        }

        private Vec3d point(AuraFrame frame, float radius, float phase, float tilt, float time) {
            float angle = phase + time * speed;
            double y = frame.height() * level + MathHelper.sin(angle) * tilt * radius
                    + 0.08 * MathHelper.sin(time * 0.1f + phase);
            return frame.feet().add(MathHelper.cos(angle) * radius, y, MathHelper.sin(angle) * radius);
        }
    }

    /**
     * Motes drifting up off the ground round the body: embers, wisps, motes of light.
     *
     * @param mote   what rises; null for the aura's own wisps
     * @param rate   how many a tick at full density; a fraction gives one now and then
     * @param spread how far out they start, as a share of the body's width
     * @param speed  blocks a tick upward at birth
     */
    record Rise(@Nullable MoteRecipe mote, float rate, float spread, float speed) implements AuraLayer {

        @Override
        public boolean fillsView() {
            return false;
        }

        @Override
        public void draw(AuraFrame frame) {
            // The motes draw themselves.
        }

        @Override
        public void tick(FxWorld world, FxStyle style, Vec3d feet, float height, float width, float scale,
                         float density) {
            float speed = this.speed * scale;
            Random random = world.random();
            float wanted = rate * density;
            int count = (int) wanted + (random.nextFloat() < wanted - (int) wanted ? 1 : 0);
            MoteRecipe recipe = mote != null ? mote : style.wisp();
            for (int i = 0; i < count && world.hasRoom(); i++) {
                float angle = random.nextFloat() * MathHelper.TAU;
                float out = (float) Math.sqrt(random.nextFloat()) * width * spread;
                Vec3d at = feet.add(MathHelper.cos(angle) * out, 0.1 + random.nextFloat() * height * 0.3,
                        MathHelper.sin(angle) * out);
                world.spawn(recipe, at, new Vec3d((random.nextFloat() - 0.5f) * 0.01, speed * (0.7f
                        + random.nextFloat() * 0.6f), (random.nextFloat() - 0.5f) * 0.01), 1.0f);
            }
        }
    }

    /**
     * Strands of light winding up round the body.
     *
     * @param strands how many, evenly round
     * @param reach   blocks out from the body's side
     * @param turns   how many times each winds round over the body's height
     * @param speed   radians a tick
     * @param width   how wide each strand is, in blocks
     */
    record Helix(int strands, float reach, float turns, float speed, float width) implements AuraLayer {

        private static final int SEGMENTS = 24;

        @Override
        public boolean fillsView() {
            return true;
        }

        @Override
        public void draw(AuraFrame frame) {
            float s = frame.strength();
            float radius = frame.width() * 0.5f + reach * frame.scale();
            float width = this.width * frame.scale();
            float height = frame.height() * 1.05f;
            for (int strand = 0; strand < strands; strand++) {
                float phase = strand * MathHelper.TAU / strands + frame.time() * speed;
                Vec3d previous = null;
                for (int i = 0; i <= SEGMENTS; i++) {
                    float t = i / (float) SEGMENTS;
                    float angle = phase + turns * MathHelper.TAU * t;
                    Vec3d point = frame.feet().add(MathHelper.cos(angle) * radius, t * height,
                            MathHelper.sin(angle) * radius);
                    if (previous != null) {
                        // Thickest and brightest at the middle of the body, gone at either end.
                        float envelope = MathHelper.sin(MathHelper.PI * (t - 0.5f / SEGMENTS));
                        frame.painter().beam(previous, point, width * (0.4f + 0.6f * envelope), frame.style().glow(),
                                0.7f * s * envelope);
                    }
                    previous = point;
                }
            }
        }
    }
}
