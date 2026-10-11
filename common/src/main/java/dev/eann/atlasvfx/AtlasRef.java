package dev.eann.atlasvfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One of the game's own sprite atlases, by the texture it is registered under.
 *
 * <p>Only the game's atlases: nothing here ships a picture of its own. Two are worth borrowing
 * from today. The particle atlas holds everything under {@code textures/particle}, named without
 * the folder ({@code minecraft:flame}); the block atlas holds block faces, named with it
 * ({@code minecraft:block/ice}). Another of the game's atlases is one more constant here; every
 * pass and layer is built from {@link #values()}, so nothing else needs to learn of it.</p>
 */
public final class AtlasRef {

    // The atlas ids are marked deprecated in favour of passing atlases around, which a lookup by
    // name cannot do; they are still what the game itself registers the atlases under.
    @SuppressWarnings("deprecation")
    public static final AtlasRef PARTICLE = new AtlasRef(0, "particle", SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
    @SuppressWarnings("deprecation")
    public static final AtlasRef BLOCK = new AtlasRef(1, "block", SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);

    private static final List<AtlasRef> VALUES = List.of(PARTICLE, BLOCK);

    private final int index;
    private final String name;
    private final Identifier texture;

    private AtlasRef(int index, String name, Identifier texture) {
        this.index = index;
        this.name = name;
        this.texture = texture;
    }

    /** Every atlas there is, in the order their passes are drawn. */
    public static List<AtlasRef> values() {
        return VALUES;
    }

    /** Its place in {@link #values()}. */
    public int index() {
        return index;
    }

    /** A short name, for the layers drawn from it. */
    public String name() {
        return name;
    }

    public Identifier texture() {
        return texture;
    }

    /**
     * The atlas as loaded right now.
     *
     * <p>Looked up each time rather than kept, because a resource reload replaces it and a kept
     * one would hand out sprites from a texture that no longer exists.</p>
     */
    @Nullable
    public SpriteAtlasTexture loaded() {
        AbstractTexture loaded = MinecraftClient.getInstance().getTextureManager().getTexture(texture);
        return loaded instanceof SpriteAtlasTexture atlas ? atlas : null;
    }

    @Override
    public String toString() {
        return "AtlasRef[" + name + "]";
    }
}
