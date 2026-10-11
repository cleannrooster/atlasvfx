package dev.eann.atlasvfx;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.jetbrains.annotations.Nullable;

/**
 * The layer every pass draws into, in the world and on the screen.
 *
 * <p>Most of them the game does not offer. Light has to be added on top of what is behind it,
 * weighted by its own transparency, and without hiding anything drawn after it. The game's
 * additive layers either ignore transparency - so a soft picture comes out as a square - or
 * write depth, so a glow hides the sparks behind it. The world's light layers do neither: they
 * are tested against depth, so a wall or a body in front still hides them, but never write it.
 * The test has to be asked for, as every one of the game's own layers asks for it: a layer that
 * leaves it alone draws with whatever the last one left behind, which is often nothing, and
 * then shows through everything.</p>
 *
 * <p>The screen's layers leave depth as they find it, as the screen's own drawing does, and set
 * only how colour is blended. They do turn off culling, so a picture shows whichever way round
 * its corners went.</p>
 *
 * <p>All are plain {@link RenderLayer}s built through its public constructor, with their state
 * set and put back by hand, rather than through the factory the game builds its own with: that
 * one is closed, and opening it with an access widener does not reach the NeoForge jar. Each is
 * built once, the first time it is asked for.</p>
 */
public final class FxLayers {

    private static final int ATLASES = AtlasRef.values().size();

    private static final RenderLayer WORLD_GLOW = new WorldLayer("atlasvfx_world_glow", VertexFormats.POSITION_COLOR,
            () -> RenderSystem.setShader(GameRenderer::getPositionColorProgram),
            () -> {
            });
    private static final RenderLayer SCREEN_GLOW = new ScreenLayer("atlasvfx_screen_glow",
            VertexFormats.POSITION_COLOR, true,
            () -> RenderSystem.setShader(GameRenderer::getPositionColorProgram));

    private static final RenderLayer[] WORLD_EMISSIVE = new RenderLayer[ATLASES];
    private static final RenderLayer[] SCREEN_EMISSIVE = new RenderLayer[ATLASES];
    private static final RenderLayer[] SCREEN_LIT = new RenderLayer[ATLASES];

    private FxLayers() {
    }

    /**
     * The layer a pass draws into in this space; null for one that cannot be drawn there - the
     * End's starfield is a thing of the world.
     */
    @Nullable
    public static RenderLayer of(FxSpace space, FxPass pass) {
        AtlasRef atlas = pass.atlas();
        return switch (pass.kind()) {
            case GLOW -> space == FxSpace.WORLD ? WORLD_GLOW : SCREEN_GLOW;
            case RIFT -> space == FxSpace.WORLD ? RenderLayer.getEndPortal() : null;
            case LIT -> space == FxSpace.WORLD
                    ? RenderLayer.getEntityTranslucent(atlas.texture())
                    : screenLit(atlas);
            case EMISSIVE -> space == FxSpace.WORLD ? emissive(atlas) : screenEmissive(atlas);
        };
    }

    /** A picture from this atlas as light, out in the world. */
    public static RenderLayer emissive(AtlasRef atlas) {
        RenderLayer layer = WORLD_EMISSIVE[atlas.index()];
        if (layer == null) {
            layer = new WorldLayer("atlasvfx_world_emissive_" + atlas.name(),
                    VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
                    () -> {
                        // The particle program: the same one the game draws its own sparks with,
                        // so fog and the lightmap behave the way they do on those.
                        RenderSystem.setShader(GameRenderer::getParticleProgram);
                        RenderSystem.setShaderTexture(0, atlas.texture());
                        MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().enable();
                    },
                    () -> MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().disable());
            WORLD_EMISSIVE[atlas.index()] = layer;
        }
        return layer;
    }

    /** Untextured light, out in the world. */
    public static RenderLayer glow() {
        return WORLD_GLOW;
    }

    private static RenderLayer screenEmissive(AtlasRef atlas) {
        RenderLayer layer = SCREEN_EMISSIVE[atlas.index()];
        if (layer == null) {
            layer = new ScreenLayer("atlasvfx_screen_emissive_" + atlas.name(), VertexFormats.POSITION_TEXTURE_COLOR,
                    true, () -> {
                        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                        RenderSystem.setShaderTexture(0, atlas.texture());
                    });
            SCREEN_EMISSIVE[atlas.index()] = layer;
        }
        return layer;
    }

    private static RenderLayer screenLit(AtlasRef atlas) {
        RenderLayer layer = SCREEN_LIT[atlas.index()];
        if (layer == null) {
            layer = new ScreenLayer("atlasvfx_screen_lit_" + atlas.name(), VertexFormats.POSITION_TEXTURE_COLOR,
                    false, () -> {
                        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
                        RenderSystem.setShaderTexture(0, atlas.texture());
                    });
            SCREEN_LIT[atlas.index()] = layer;
        }
        return layer;
    }

    private static final class WorldLayer extends RenderLayer {
        WorldLayer(String name, VertexFormat format, Runnable setup, Runnable clear) {
            super(name, format, VertexFormat.DrawMode.QUADS, 4096, false, false,
                    () -> {
                        setup.run();
                        RenderSystem.enableBlend();
                        // Added, but only as much as the pixel is there: a soft edge stays soft.
                        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
                        RenderSystem.enableDepthTest();
                        RenderSystem.depthFunc(GlConst.GL_LEQUAL);
                        RenderSystem.depthMask(false);
                        RenderSystem.disableCull();
                    },
                    () -> {
                        RenderSystem.enableCull();
                        RenderSystem.depthMask(true);
                        // Put back the way the game's own depth phase leaves it.
                        RenderSystem.disableDepthTest();
                        RenderSystem.depthFunc(GlConst.GL_LEQUAL);
                        RenderSystem.defaultBlendFunc();
                        RenderSystem.disableBlend();
                        clear.run();
                    });
        }
    }

    private static final class ScreenLayer extends RenderLayer {
        /**
         * @param additive whether it is added on top, as light, or laid over, as a solid picture
         */
        ScreenLayer(String name, VertexFormat format, boolean additive, Runnable setup) {
            super(name, format, VertexFormat.DrawMode.QUADS, 4096, false, false,
                    () -> {
                        setup.run();
                        RenderSystem.enableBlend();
                        if (additive) {
                            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
                        } else {
                            RenderSystem.defaultBlendFunc();
                        }
                        RenderSystem.disableCull();
                    },
                    () -> {
                        RenderSystem.enableCull();
                        RenderSystem.defaultBlendFunc();
                        RenderSystem.disableBlend();
                    });
        }
    }
}
