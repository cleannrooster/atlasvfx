package dev.eann.atlasvfx;

import net.minecraft.util.math.MathHelper;

/** Colours as {@code 0xRRGGBB}. */
public final class FxColor {

    public static final int WHITE = 0xFFFFFF;

    private FxColor() {
    }

    /** Part way from one colour to another, channel by channel. */
    public static int mix(int from, int to, float t) {
        int r = (int) MathHelper.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) MathHelper.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) MathHelper.lerp(t, from & 0xFF, to & 0xFF);
        return r << 16 | g << 8 | b;
    }
}
