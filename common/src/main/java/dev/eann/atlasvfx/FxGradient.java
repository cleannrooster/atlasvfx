package dev.eann.atlasvfx;

import java.util.Arrays;

/**
 * Colours along a span, blended between stops: an ember cooling from white through red to grey, a
 * trail from its hot head to its dark tail.
 *
 * <p>Colours are {@code 0xRRGGBB}; the stops run from 0 to 1, strictly increasing. Read before the
 * first stop it is the first colour, after the last it is the last.</p>
 */
public final class FxGradient {

    private final int[] colours;
    private final float[] stops;

    private FxGradient(int[] colours, float[] stops) {
        this.colours = colours;
        this.stops = stops;
    }

    /** One colour all the way along. */
    public static FxGradient constant(int rgb) {
        return new FxGradient(new int[] {rgb}, new float[] {0.0f});
    }

    /** From one colour to another. */
    public static FxGradient between(int from, int to) {
        return new FxGradient(new int[] {from, to}, new float[] {0.0f, 1.0f});
    }

    /** Colours spread evenly: the first at 0, the last at 1. */
    public static FxGradient of(int... colours) {
        if (colours.length == 0) {
            throw new IllegalArgumentException("a gradient needs at least one colour");
        }
        float[] stops = new float[colours.length];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = colours.length == 1 ? 0.0f : i / (float) (colours.length - 1);
        }
        return new FxGradient(colours.clone(), stops);
    }

    /**
     * Colours at chosen places along the span.
     *
     * @param stops where each colour sits, from 0 to 1, strictly increasing, one per colour
     */
    public static FxGradient at(float[] stops, int[] colours) {
        if (colours.length == 0 || stops.length != colours.length) {
            throw new IllegalArgumentException("a gradient needs one stop per colour, and at least one");
        }
        for (int i = 0; i < stops.length; i++) {
            if (stops[i] < 0.0f || stops[i] > 1.0f || (i > 0 && stops[i] <= stops[i - 1])) {
                throw new IllegalArgumentException("stops must run from 0 to 1, strictly increasing: "
                        + Arrays.toString(stops));
            }
        }
        return new FxGradient(colours.clone(), stops.clone());
    }

    /** The colour at {@code t}, from 0 to 1. */
    public int at(float t) {
        if (t <= stops[0]) {
            return colours[0];
        }
        int last = stops.length - 1;
        if (t >= stops[last]) {
            return colours[last];
        }
        int i = 0;
        while (t > stops[i + 1]) {
            i++;
        }
        float f = (t - stops[i]) / (stops[i + 1] - stops[i]);
        return FxColor.mix(colours[i], colours[i + 1], f);
    }

    /** The same colours in the other order along the span. */
    public FxGradient reversed() {
        int n = colours.length;
        int[] flippedColours = new int[n];
        float[] flippedStops = new float[n];
        for (int i = 0; i < n; i++) {
            flippedColours[i] = colours[n - 1 - i];
            flippedStops[i] = 1.0f - stops[n - 1 - i];
        }
        return new FxGradient(flippedColours, flippedStops);
    }
}
