package dev.eann.atlasvfx.example;

import dev.eann.atlasvfx.FxBlend;
import dev.eann.atlasvfx.FxDensity;
import dev.eann.atlasvfx.FxLife;
import dev.eann.atlasvfx.FxLight;
import dev.eann.atlasvfx.FxPainter;
import dev.eann.atlasvfx.FxShape;
import dev.eann.atlasvfx.FxShapes;
import dev.eann.atlasvfx.FxSprite;
import dev.eann.atlasvfx.FxStyle;
import dev.eann.atlasvfx.cue.FxCueHandlers;
import dev.eann.atlasvfx.cue.FxCueStyles;
import dev.eann.atlasvfx.telegraph.TelegraphStyles;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The example's client side: names the look its server sends, and draws a cue of its own with a
 * shape of its own - what a content mod does to get a bespoke effect onto every nearby screen.
 *
 * <p>Registered at mod construction, which on NeoForge runs alongside other mods' construction on
 * parallel threads: the registries it writes to are built for that.</p>
 */
public final class AtlasVfxExampleClient {

    private static final int VIOLET = 0x9B5CFF;
    /** This mod's share of each effect: all of it. A real mod would read its own setting here. */
    private static final FxDensity DENSITY = FxDensity.FULL;

    private AtlasVfxExampleClient() {
    }

    public static void init() {
        FxCueStyles.register(AtlasVfxExample.STYLE, FxCueStyles.style(VIOLET), TelegraphStyles.of(VIOLET));
        FxCueHandlers.register(AtlasVfxExample.SIGIL, (world, args) -> {
            FxStyle style = FxCueStyles.style(args.style());
            world.play(new Sigil(style, args.at(), args.size()), DENSITY);
            world.play(FxShapes.nova(style, args.at(), args.size() * 1.4f, args.tier(), args.seed()), DENSITY);
        });
        Adventurers.registerCommand();
    }

    /**
     * A ring of the enchanting table's glyphs lying on the ground, turning, swelling in and fading
     * out: a shape the library does not have, built on its painter.
     */
    private record Sigil(FxStyle style, Vec3d at, float radius) implements FxShape.Timed {

        private static final FxSprite GLYPHS = FxSprite.particleSet("sga_a", "sga_c", "sga_e", "sga_h", "sga_k",
                "sga_m", "sga_o", "sga_r", "sga_u", "sga_x");
        private static final int COUNT = 12;

        @Override
        public int ticks() {
            return 40;
        }

        @Override
        public void draw(FxPainter painter, @Nullable ClientWorld world, FxLife life, float tickDelta) {
            float t = life.progress(tickDelta, ticks());
            float strength = Math.min(1.0f, t / 0.15f) * Math.min(1.0f, (1.0f - t) / 0.3f);
            float r = radius * (0.6f + 0.4f * Math.min(1.0f, t / 0.2f));
            float turn = (life.age() + tickDelta) * 0.04f;
            painter.ring(at, FxShapes.FLAT, r, 0.08f, style.glow(), 0.8f * strength);
            painter.ring(at, FxShapes.FLAT, r * 0.72f, 0.05f, style.hot(), 0.6f * strength);
            for (int i = 0; i < COUNT; i++) {
                float angle = turn + i * MathHelper.TAU / COUNT;
                Vec3d spot = at.add(Math.cos(angle) * r * 0.86, 0.01, Math.sin(angle) * r * 0.86);
                painter.sprite(FxBlend.EMISSIVE, GLYPHS, 0.0f, i, false, spot, FxShapes.FLAT, 0.35f, -angle,
                        style.hot(), strength, FxLight.FULL_BRIGHT, false);
            }
        }
    }
}
