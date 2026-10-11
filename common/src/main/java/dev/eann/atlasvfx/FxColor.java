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

    /** Hue, saturation and lightness, each 0 to 1. */
    public static float[] toHsl(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255.0f;
        float g = (rgb >> 8 & 0xFF) / 255.0f;
        float b = (rgb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float lightness = (max + min) * 0.5f;
        float chroma = max - min;
        if (chroma < 1.0e-6f) {
            return new float[] {0.0f, 0.0f, lightness};
        }
        float saturation = chroma / (1.0f - Math.abs(2.0f * lightness - 1.0f));
        float hue;
        if (max == r) {
            hue = ((g - b) / chroma) % 6.0f;
        } else if (max == g) {
            hue = (b - r) / chroma + 2.0f;
        } else {
            hue = (r - g) / chroma + 4.0f;
        }
        hue /= 6.0f;
        if (hue < 0.0f) {
            hue += 1.0f;
        }
        return new float[] {hue, MathHelper.clamp(saturation, 0.0f, 1.0f), lightness};
    }

    /** A colour from hue, saturation and lightness, each 0 to 1. */
    public static int fromHsl(float hue, float saturation, float lightness) {
        float s = MathHelper.clamp(saturation, 0.0f, 1.0f);
        float l = MathHelper.clamp(lightness, 0.0f, 1.0f);
        float h = hue - (float) Math.floor(hue);
        return channel(hslChannel(0, h, s, l)) << 16 | channel(hslChannel(8, h, s, l)) << 8
                | channel(hslChannel(4, h, s, l));
    }

    /** One channel of an HSL colour; {@code n} is 0 for red, 8 for green, 4 for blue. */
    private static float hslChannel(int n, float hue, float saturation, float lightness) {
        float k = (n + hue * 12.0f) % 12.0f;
        float a = saturation * Math.min(lightness, 1.0f - lightness);
        return lightness - a * Math.max(-1.0f, Math.min(Math.min(k - 3.0f, 9.0f - k), 1.0f));
    }

    private static int channel(float value) {
        return Math.round(MathHelper.clamp(value, 0.0f, 1.0f) * 255.0f);
    }
}
