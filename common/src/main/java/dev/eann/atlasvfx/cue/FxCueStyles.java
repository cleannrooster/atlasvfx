package dev.eann.atlasvfx.cue;

import dev.eann.atlasvfx.FxBlend;
import dev.eann.atlasvfx.FxColor;
import dev.eann.atlasvfx.FxSprite;
import dev.eann.atlasvfx.FxStyle;
import dev.eann.atlasvfx.MoteRecipe;
import dev.eann.atlasvfx.Motion;
import dev.eann.atlasvfx.telegraph.TelegraphStyle;
import dev.eann.atlasvfx.telegraph.TelegraphStyles;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The looks cues and telegraphs are drawn in, by the names a server sends. Client only.
 *
 * <p>A mod registers its own - a school of magic, an element, a boss's colours - and its server
 * names them in {@link FxCueArgs#style()} and {@link TelegraphPayload#style()}. A name nobody
 * registered draws in the default look rather than nothing, so a missing registration shows as a
 * wrong colour instead of a silent hole.</p>
 */
public final class FxCueStyles {

    private static final FxSprite GLITTER = FxSprite.particleRun("glitter", 8);
    private static final FxSprite SPARK = FxSprite.particleRun("spark", 8);

    /** A cool, pale look for cues that name no style anyone registered. */
    public static final FxStyle DEFAULT = style(0xB8C4D0);

    // Copy-on-write, for the same reason as the cue handlers: registrations can arrive from
    // NeoForge's parallel start-up threads. Reads are one volatile load.
    private static volatile Map<Identifier, FxStyle> styles = Map.of();
    private static volatile Map<Identifier, TelegraphStyle> telegraphs = Map.of();
    private static final Map<Integer, TelegraphStyle> BY_COLOUR = new ConcurrentHashMap<>();

    static {
        register(FxCues.DANGER, style(0xFF3A28), TelegraphStyles.DANGER);
        register(FxCues.WARNING, style(0xFFAA30), TelegraphStyles.WARNING);
        register(FxCues.HELPFUL, style(0x66FF66), TelegraphStyles.of(0x66FF66));
    }

    private FxCueStyles() {
    }

    /** Names the look a cue is drawn in. */
    public static synchronized void register(Identifier name, FxStyle style) {
        Map<Identifier, FxStyle> next = new HashMap<>(styles);
        next.put(name, style);
        styles = Map.copyOf(next);
    }

    /** Names the look a telegraph is drawn in. */
    public static synchronized void register(Identifier name, TelegraphStyle style) {
        Map<Identifier, TelegraphStyle> next = new HashMap<>(telegraphs);
        next.put(name, style);
        telegraphs = Map.copyOf(next);
    }

    /** Names both looks at once. */
    public static synchronized void register(Identifier name, FxStyle style, TelegraphStyle telegraph) {
        register(name, style);
        register(name, telegraph);
    }

    /** The cue look by this name, or {@link #DEFAULT}. */
    public static FxStyle style(Identifier name) {
        return styles.getOrDefault(name, DEFAULT);
    }

    /** The telegraph look by this name, or the danger look. */
    public static TelegraphStyle telegraph(Identifier name) {
        return telegraphs.getOrDefault(name, TelegraphStyles.DANGER);
    }

    /** A telegraph look in one colour, built once per colour. */
    public static TelegraphStyle telegraph(int colour) {
        return BY_COLOUR.computeIfAbsent(colour, TelegraphStyles::of);
    }

    /**
     * A plain look in one colour: glinting sparks for debris, glitter for accents and lingering
     * motes, and a hot, near-white heart.
     */
    public static FxStyle style(int rgb) {
        MoteRecipe debris = new MoteRecipe(SPARK, FxBlend.EMISSIVE, Motion.BURST, rgb, 0.18f, 14, 1.0f, 0.05f, 0.1f,
                0.0f, 0.2f, true, false);
        MoteRecipe accent = new MoteRecipe(GLITTER, FxBlend.EMISSIVE, Motion.BURST, rgb, 0.14f, 18, 1.0f, 0.05f, 0.08f,
                0.0f, 0.1f, true, false);
        MoteRecipe wisp = accent.drifting(-0.004f).lasting(1.5f);
        int hot = FxColor.mix(rgb, FxColor.WHITE, 0.6f);
        return new FxStyle(rgb, hot, debris, accent, wisp, null, null, rgb, false);
    }
}
