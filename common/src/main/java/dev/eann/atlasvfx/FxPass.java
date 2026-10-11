package dev.eann.atlasvfx;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One way of drawing, and the buffer it goes into.
 *
 * <p>A frame in the world is drawn in several passes, one per way of drawing, so each kind of
 * geometry goes into its buffer in one run instead of switching back and forth a piece at a
 * time: what blocks the view first, light last so it lands on top. Pictures need a pass per
 * atlas, because a buffer is bound to one texture; light and the End need none.</p>
 *
 * <p>Every pass there is exists once, so passes compare by identity.</p>
 */
public final class FxPass {

    public enum Kind {
        /** Pictures lit by the world, blended over what is behind them. */
        LIT,
        /** Holes into the End's starfield. */
        RIFT,
        /** Pictures as light, added on top. */
        EMISSIVE,
        /** Untextured light: soft spots, rings, blades and beams. */
        GLOW
    }

    public static final FxPass RIFT = new FxPass(Kind.RIFT, null);
    public static final FxPass GLOW = new FxPass(Kind.GLOW, null);

    private static final FxPass[] LIT = new FxPass[AtlasRef.values().size()];
    private static final FxPass[] EMISSIVE = new FxPass[AtlasRef.values().size()];
    private static final List<FxPass> ORDERED;

    static {
        List<FxPass> ordered = new ArrayList<>();
        for (AtlasRef atlas : AtlasRef.values()) {
            LIT[atlas.index()] = new FxPass(Kind.LIT, atlas);
            ordered.add(LIT[atlas.index()]);
        }
        ordered.add(RIFT);
        for (AtlasRef atlas : AtlasRef.values()) {
            EMISSIVE[atlas.index()] = new FxPass(Kind.EMISSIVE, atlas);
            ordered.add(EMISSIVE[atlas.index()]);
        }
        ordered.add(GLOW);
        ORDERED = List.copyOf(ordered);
    }

    private final Kind kind;
    private final @Nullable AtlasRef atlas;

    private FxPass(Kind kind, @Nullable AtlasRef atlas) {
        this.kind = kind;
        this.atlas = atlas;
    }

    /** Every pass, in the order a frame draws them. */
    public static List<FxPass> ordered() {
        return ORDERED;
    }

    public static FxPass lit(AtlasRef atlas) {
        return LIT[atlas.index()];
    }

    public static FxPass emissive(AtlasRef atlas) {
        return EMISSIVE[atlas.index()];
    }

    /** The pass a picture from this atlas goes in, blended this way. */
    public static FxPass of(FxBlend blend, AtlasRef atlas) {
        return switch (blend) {
            case LIT -> lit(atlas);
            case EMISSIVE -> emissive(atlas);
            case GLOW -> GLOW;
        };
    }

    public Kind kind() {
        return kind;
    }

    /** The atlas a picture pass draws from; null for light and the End. */
    @Nullable
    public AtlasRef atlas() {
        return atlas;
    }

    @Override
    public String toString() {
        return atlas == null ? "FxPass[" + kind + "]" : "FxPass[" + kind + ", " + atlas.name() + "]";
    }
}
