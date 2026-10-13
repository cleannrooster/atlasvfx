package dev.eann.atlasvfx.cue;

import dev.eann.atlasvfx.AtlasVfxCommon;
import net.minecraft.util.Identifier;

/**
 * The names of the built-in cues - one per generic shape, drawn in whatever style the server names
 * - and of the built-in looks. Safe on a server.
 *
 * <p>A mod with something bespoke registers its own cue name with {@link FxCueHandlers} on the
 * client and sends it with {@link FxEmitter} exactly like these.</p>
 */
public final class FxCues {

    /** A blink and a ring: a hit, a cast going off. {@code at}, {@code size}. */
    public static final Identifier PULSE = id("pulse");
    /** A ring running out across the ground. {@code at}, {@code size} is its radius. */
    public static final Identifier NOVA = id("nova");
    /** A bloom with debris. {@code at}, {@code size}. */
    public static final Identifier EXPLOSION = id("explosion");
    /** A shaft of light rising from {@code at}. */
    public static final Identifier BLAST = id("blast");
    /** A shaft of light falling onto {@code at}: a meteor landing. */
    public static final Identifier BLAST_FALLING = id("blast_falling");
    /** A straight beam from {@code at} to {@code to}; {@code size} is its width. */
    public static final Identifier BEAM = id("beam");
    /** A jagged arc from {@code at} to {@code to}. */
    public static final Identifier ARC = id("arc");
    /** A blast one way: {@code at}, along {@code to}, {@code angle} either side, {@code size} far. */
    public static final Identifier CONE = id("cone");
    /** Debris thrown out from {@code at} within {@code size}. */
    public static final Identifier BURST = id("burst");
    /** Motes drifting within {@code size} of {@code at}: something lingering. */
    public static final Identifier WISP = id("wisp");
    /** Smoke rising over {@code size} around {@code at}; the style is ignored. */
    public static final Identifier SMOKE = id("smoke");
    /** The style's falling motes over {@code size} around {@code at}. */
    public static final Identifier RAIN = id("rain");
    /**
     * A level blade trail from {@code at}, facing along {@code to}, reaching {@code size} blocks
     * through {@code angle} degrees either side; {@code tier} 1 sweeps from the right.
     */
    public static final Identifier SLASH = id("slash");

    /** The look of something that hurts: red. For cues, the default style. */
    public static final Identifier DANGER = id("danger");
    /** The look of something coming: amber. */
    public static final Identifier WARNING = id("warning");
    /** The look of something that helps: green. */
    public static final Identifier HELPFUL = id("helpful");

    private FxCues() {
    }

    private static Identifier id(String name) {
        return Identifier.of(AtlasVfxCommon.MOD_ID, name);
    }
}
