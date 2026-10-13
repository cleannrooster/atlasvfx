package dev.eann.atlasvfx;

import com.mojang.blaze3d.systems.RenderSystem;
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
 * are tested against depth, so a wall or a body in front still hides them, but never write it.</p>
 *
 * <p>The screen's layers draw over whatever the screen has drawn so far and never write depth,
 * so nothing drawn after them is hidden. They turn off culling, so a picture shows whichever way
 * round its corners went.</p>
 *
 * <p>Every layer is built the way the game builds its own - {@code RenderLayer.of} with its
 * standard phases - rather than as a layer that sets the state by hand. That matters to mods that
 * read layers rather than run them: a shader pack under Iris picks its program and its draw order
 * from these phases, and a layer whose state it cannot see is treated as opaque and drawn first,
 * underneath what it should sit on. {@code of} is closed in the game's own code; NeoForge's access
 * transformer opens it, and on Fabric Fabric API's transitive access wideners do, with this mod's
 * own widener declaring it too. Each layer is built once, the first time it is asked for.</p>
 *
 * <p>Pictures are bound without changing the atlas's filtering, as the game's particles leave it:
 * a texture phase that set it would change it for everything else drawn from the same atlas.</p>
 */
public final class FxLayers extends RenderLayer {

    private static final int ATLASES = AtlasRef.values().size();
    private static final int BUFFER_SIZE = 4096;

    private static final ShaderProgram COLOUR = new ShaderProgram(GameRenderer::getPositionColorProgram);
    private static final ShaderProgram PARTICLE = new ShaderProgram(GameRenderer::getParticleProgram);
    private static final ShaderProgram TEXTURE_COLOUR = new ShaderProgram(GameRenderer::getPositionTexColorProgram);

    private static final RenderLayer WORLD_GLOW = of("atlasvfx_world_glow", VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS, BUFFER_SIZE, false, false,
            MultiPhaseParameters.builder()
                    .program(COLOUR)
                    // Added, but only as much as the pixel is there: a soft edge stays soft.
                    .transparency(LIGHTNING_TRANSPARENCY)
                    .depthTest(LEQUAL_DEPTH_TEST)
                    .writeMaskState(COLOR_MASK)
                    .cull(DISABLE_CULLING)
                    .build(false));

    private static final RenderLayer SCREEN_GLOW = of("atlasvfx_screen_glow", VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS, BUFFER_SIZE, false, false,
            MultiPhaseParameters.builder()
                    .program(COLOUR)
                    .transparency(LIGHTNING_TRANSPARENCY)
                    .depthTest(ALWAYS_DEPTH_TEST)
                    .writeMaskState(COLOR_MASK)
                    .cull(DISABLE_CULLING)
                    .build(false));

    private static final RenderLayer[] WORLD_EMISSIVE = new RenderLayer[ATLASES];
    private static final RenderLayer[] SCREEN_EMISSIVE = new RenderLayer[ATLASES];
    private static final RenderLayer[] SCREEN_LIT = new RenderLayer[ATLASES];

    private FxLayers() {
        // Never made: extending RenderLayer is only for its phases.
        super("atlasvfx_layers", VertexFormats.POSITION, VertexFormat.DrawMode.QUADS, 0, false, false, () -> {
        }, () -> {
        });
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
    public static synchronized RenderLayer emissive(AtlasRef atlas) {
        RenderLayer layer = WORLD_EMISSIVE[atlas.index()];
        if (layer == null) {
            layer = of("atlasvfx_world_emissive_" + atlas.name(), VertexFormats.POSITION_TEXTURE_COLOR_LIGHT,
                    VertexFormat.DrawMode.QUADS, BUFFER_SIZE, false, false,
                    MultiPhaseParameters.builder()
                            // The particle program: the one the game draws its own sparks with, so
                            // fog and the lightmap behave the way they do on those.
                            .program(PARTICLE)
                            .texture(atlasTexture(atlas))
                            .transparency(LIGHTNING_TRANSPARENCY)
                            .depthTest(LEQUAL_DEPTH_TEST)
                            .writeMaskState(COLOR_MASK)
                            .cull(DISABLE_CULLING)
                            .lightmap(ENABLE_LIGHTMAP)
                            .build(false));
            WORLD_EMISSIVE[atlas.index()] = layer;
        }
        return layer;
    }

    /** Untextured light, out in the world. */
    public static RenderLayer glow() {
        return WORLD_GLOW;
    }

    private static synchronized RenderLayer screenEmissive(AtlasRef atlas) {
        RenderLayer layer = SCREEN_EMISSIVE[atlas.index()];
        if (layer == null) {
            layer = screenPicture("atlasvfx_screen_emissive_", atlas, LIGHTNING_TRANSPARENCY);
            SCREEN_EMISSIVE[atlas.index()] = layer;
        }
        return layer;
    }

    private static synchronized RenderLayer screenLit(AtlasRef atlas) {
        RenderLayer layer = SCREEN_LIT[atlas.index()];
        if (layer == null) {
            // Laid over as a solid picture rather than added as light.
            layer = screenPicture("atlasvfx_screen_lit_", atlas, TRANSLUCENT_TRANSPARENCY);
            SCREEN_LIT[atlas.index()] = layer;
        }
        return layer;
    }

    private static RenderLayer screenPicture(String name, AtlasRef atlas, Transparency transparency) {
        return of(name + atlas.name(), VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS,
                BUFFER_SIZE, false, false,
                MultiPhaseParameters.builder()
                        .program(TEXTURE_COLOUR)
                        .texture(atlasTexture(atlas))
                        .transparency(transparency)
                        .depthTest(ALWAYS_DEPTH_TEST)
                        .writeMaskState(COLOR_MASK)
                        .cull(DISABLE_CULLING)
                        .build(false));
    }

    /** Binds an atlas without touching its filtering. */
    private static TextureBase atlasTexture(AtlasRef atlas) {
        return new TextureBase(() -> RenderSystem.setShaderTexture(0, atlas.texture()), () -> {
        });
    }
}
