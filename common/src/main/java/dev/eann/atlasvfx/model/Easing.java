package dev.eann.atlasvfx.model;

import java.util.Locale;

/**
 * How a keyframe is eased into: the names GeckoLib and AzureLib animation files use, so the same
 * files play the same here. An unknown name plays linear.
 */
public enum Easing {
    LINEAR, STEP,
    EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
    EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
    EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
    EASE_IN_QUINT, EASE_OUT_QUINT, EASE_IN_OUT_QUINT,
    EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
    EASE_IN_CIRC, EASE_OUT_CIRC, EASE_IN_OUT_CIRC,
    EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
    EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
    EASE_IN_BOUNCE, EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE;

    /** {@code easeInOutSine}, {@code linear}, {@code step}; anything else is linear. */
    public static Easing named(String name) {
        String key = name.trim().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return LINEAR;
        }
    }

    /**
     * Eases {@code t}, 0 to 1.
     *
     * @param arg the file's {@code easingArgs} first value, if any: a back's overshoot, an elastic's
     *            bounciness, a bounce's bounciness; NaN for the default
     */
    public float apply(float t, float arg) {
        return switch (this) {
            case LINEAR -> t;
            case STEP -> t < 1.0f ? 0.0f : 1.0f;
            case EASE_IN_SINE -> 1.0f - (float) Math.cos(t * Math.PI / 2.0);
            case EASE_OUT_SINE -> (float) Math.sin(t * Math.PI / 2.0);
            case EASE_IN_OUT_SINE -> (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
            case EASE_IN_QUAD -> in(t, 2);
            case EASE_OUT_QUAD -> out(t, 2);
            case EASE_IN_OUT_QUAD -> inOut(t, 2);
            case EASE_IN_CUBIC -> in(t, 3);
            case EASE_OUT_CUBIC -> out(t, 3);
            case EASE_IN_OUT_CUBIC -> inOut(t, 3);
            case EASE_IN_QUART -> in(t, 4);
            case EASE_OUT_QUART -> out(t, 4);
            case EASE_IN_OUT_QUART -> inOut(t, 4);
            case EASE_IN_QUINT -> in(t, 5);
            case EASE_OUT_QUINT -> out(t, 5);
            case EASE_IN_OUT_QUINT -> inOut(t, 5);
            case EASE_IN_EXPO -> t <= 0.0f ? 0.0f : (float) Math.pow(2.0, 10.0 * t - 10.0);
            case EASE_OUT_EXPO -> t >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
            case EASE_IN_OUT_EXPO -> t <= 0.0f ? 0.0f : t >= 1.0f ? 1.0f : t < 0.5f
                    ? (float) Math.pow(2.0, 20.0 * t - 10.0) / 2.0f
                    : (2.0f - (float) Math.pow(2.0, -20.0 * t + 10.0)) / 2.0f;
            case EASE_IN_CIRC -> 1.0f - (float) Math.sqrt(1.0 - t * t);
            case EASE_OUT_CIRC -> (float) Math.sqrt(1.0 - (t - 1.0) * (t - 1.0));
            case EASE_IN_OUT_CIRC -> t < 0.5f
                    ? (1.0f - (float) Math.sqrt(1.0 - 4.0 * t * t)) / 2.0f
                    : ((float) Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2.0)) + 1.0f) / 2.0f;
            case EASE_IN_BACK -> back(t, arg);
            case EASE_OUT_BACK -> 1.0f - back(1.0f - t, arg);
            case EASE_IN_OUT_BACK -> t < 0.5f ? back(2.0f * t, arg) / 2.0f
                    : 1.0f - back(2.0f - 2.0f * t, arg) / 2.0f;
            case EASE_IN_ELASTIC -> 1.0f - elastic(1.0f - t, arg);
            case EASE_OUT_ELASTIC -> elastic(t, arg);
            case EASE_IN_OUT_ELASTIC -> t < 0.5f ? (1.0f - elastic(1.0f - 2.0f * t, arg)) / 2.0f
                    : (1.0f + elastic(2.0f * t - 1.0f, arg)) / 2.0f;
            case EASE_IN_BOUNCE -> 1.0f - bounce(1.0f - t);
            case EASE_OUT_BOUNCE -> bounce(t);
            case EASE_IN_OUT_BOUNCE -> t < 0.5f ? (1.0f - bounce(1.0f - 2.0f * t)) / 2.0f
                    : (1.0f + bounce(2.0f * t - 1.0f)) / 2.0f;
        };
    }

    private static float in(float t, int power) {
        return (float) Math.pow(t, power);
    }

    private static float out(float t, int power) {
        return 1.0f - (float) Math.pow(1.0f - t, power);
    }

    private static float inOut(float t, int power) {
        return t < 0.5f ? (float) (Math.pow(2.0, power - 1) * Math.pow(t, power))
                : 1.0f - (float) Math.pow(-2.0 * t + 2.0, power) / 2.0f;
    }

    private static float back(float t, float arg) {
        float overshoot = Float.isNaN(arg) ? 1.70158f : arg;
        return t * t * ((overshoot + 1.0f) * t - overshoot);
    }

    private static float elastic(float t, float arg) {
        if (t <= 0.0f || t >= 1.0f) {
            return t <= 0.0f ? 0.0f : 1.0f;
        }
        float bounciness = Float.isNaN(arg) ? 1.0f : Math.max(0.1f, arg);
        double period = 0.3 / bounciness;
        return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t - period / 4.0) * (2.0 * Math.PI) / period) + 1.0);
    }

    private static float bounce(float t) {
        float n = 7.5625f;
        float d = 2.75f;
        if (t < 1.0f / d) {
            return n * t * t;
        } else if (t < 2.0f / d) {
            t -= 1.5f / d;
            return n * t * t + 0.75f;
        } else if (t < 2.5f / d) {
            t -= 2.25f / d;
            return n * t * t + 0.9375f;
        }
        t -= 2.625f / d;
        return n * t * t + 0.984375f;
    }
}
