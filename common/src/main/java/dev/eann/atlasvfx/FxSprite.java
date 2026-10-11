package dev.eann.atlasvfx;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A picture borrowed from one of the game's own atlases, by name.
 *
 * <p>Nothing here is a texture anyone ships. Every effect is composed at draw time from sprites
 * the game has already loaded - a flame, a bubble, a block of ice, a page of the enchanting
 * alphabet - tinted and layered, so the look follows whatever resource pack the player has on
 * and no copy of anyone else's artwork goes out with the jar. Block sprites that animate in the
 * world - fire, water, a portal - animate here too, because the atlas itself is what moves.</p>
 *
 * @param atlas  which atlas the frames live in
 * @param frames one name for a still picture, several for one that flips through them over
 *               a lifetime or is picked from at random
 */
public record FxSprite(AtlasRef atlas, List<Identifier> frames) {

    public FxSprite {
        frames = List.copyOf(frames);
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("a sprite needs at least one frame");
        }
    }

    /** One vanilla particle picture. */
    public static FxSprite particle(String name) {
        return new FxSprite(AtlasRef.PARTICLE, List.of(Identifier.ofVanilla(name)));
    }

    /** A run of vanilla particle pictures, {@code name_0} to {@code name_(count-1)}. */
    public static FxSprite particleRun(String name, int count) {
        List<Identifier> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(Identifier.ofVanilla(name + "_" + i));
        }
        return new FxSprite(AtlasRef.PARTICLE, ids);
    }

    /** Vanilla particle pictures picked from by name: {@code name_a}, {@code name_b}... */
    public static FxSprite particleSet(String... names) {
        List<Identifier> ids = new ArrayList<>(names.length);
        for (String name : names) {
            ids.add(Identifier.ofVanilla(name));
        }
        return new FxSprite(AtlasRef.PARTICLE, ids);
    }

    /** Vanilla block faces, {@code block/<name>}, picked from at random. */
    public static FxSprite blocks(String... names) {
        List<Identifier> ids = new ArrayList<>(names.length);
        for (String name : names) {
            ids.add(Identifier.ofVanilla("block/" + name));
        }
        return new FxSprite(AtlasRef.BLOCK, ids);
    }

    public int frameCount() {
        return frames.size();
    }

    /**
     * The frame to show.
     *
     * @param life  0 at birth to 1 at death, for a sprite that flips through its frames
     * @param pick  a fixed per-instance number, for one that is picked from instead
     * @param flips whether this use flips through the frames or keeps the picked one
     */
    @Nullable
    public Sprite frame(float life, int pick, boolean flips) {
        SpriteAtlasTexture loaded = atlas.loaded();
        if (loaded == null) {
            return null;
        }
        int index = flips ? frameIndex(life, frames.size()) : Math.floorMod(pick, frames.size());
        return loaded.getSprite(frames.get(index));
    }

    /** Which frame of {@code count} a life fraction lands on, the last one included. */
    public static int frameIndex(float life, int count) {
        float clamped = Math.max(0.0f, Math.min(0.9999f, life));
        return (int) (clamped * count);
    }
}
