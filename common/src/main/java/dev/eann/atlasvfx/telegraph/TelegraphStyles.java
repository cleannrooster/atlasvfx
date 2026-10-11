package dev.eann.atlasvfx.telegraph;

import dev.eann.atlasvfx.FxColor;
import dev.eann.atlasvfx.FxCurve;

/** Ready-made telegraph looks. */
public final class TelegraphStyles {

    /** Red: this will hurt. */
    public static final TelegraphStyle DANGER = of(0xFF3A28);
    /** Amber: something is coming. */
    public static final TelegraphStyle WARNING = of(0xFFAA30);

    private TelegraphStyles() {
    }

    /**
     * A telegraph in one colour: a faint footprint from the start, a front filling it toward the
     * moment it lands in a paler shade, and a crisp outline, beating faster as it nears.
     */
    public static TelegraphStyle of(int rgb) {
        return new TelegraphStyle(FxColor.mix(rgb, FxColor.WHITE, 0.2f), rgb, FxColor.mix(rgb, FxColor.WHITE, 0.6f),
                TelegraphStyle.FillMode.GROW, FxCurve.easeIn(2.0f), 0.14f, 0.1f, 0.3f, 0.85f);
    }
}
