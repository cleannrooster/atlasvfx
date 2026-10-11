package dev.eann.atlasvfx;

/**
 * A number that changes over a span: how wide a trail is from head to tail, how a fill eases in,
 * how bright something is over its life.
 *
 * <p>Read at {@code t} from 0 to 1. Values outside that are clamped to its ends by every curve
 * made here, so a caller may pass a fraction that strays a little without asking for the shape
 * past either end.</p>
 */
@FunctionalInterface
public interface FxCurve {

    /** 0 to 1 in a straight line. */
    FxCurve LINEAR = t -> clamp01(t);
    /** Always 1. */
    FxCurve ONE = t -> 1.0f;
    /** Always 0. */
    FxCurve ZERO = t -> 0.0f;
    /** 0 to 1, easing in and out: flat at both ends. */
    FxCurve SMOOTH = t -> {
        float x = clamp01(t);
        return x * x * (3.0f - 2.0f * x);
    };
    /** 0 up to 1 at the middle and back down: a pulse that starts and ends at nothing. */
    FxCurve HUMP = t -> (float) Math.sin(Math.PI * clamp01(t));

    /** The value at {@code t}, from 0 to 1. */
    float at(float t);

    /** Always {@code value}. */
    static FxCurve constant(float value) {
        return t -> value;
    }

    /** 0 to 1, starting slowly: {@code t^power}. */
    static FxCurve easeIn(float power) {
        return t -> (float) Math.pow(clamp01(t), power);
    }

    /** 0 to 1, starting quickly and easing into its end: {@code 1 - (1 - t)^power}. */
    static FxCurve easeOut(float power) {
        return t -> 1.0f - (float) Math.pow(1.0f - clamp01(t), power);
    }

    /**
     * Straight lines through evenly spaced values: the first at 0, the last at 1. One value is a
     * constant; none is an error.
     */
    static FxCurve points(float... values) {
        if (values.length == 0) {
            throw new IllegalArgumentException("a curve needs at least one point");
        }
        float[] copy = values.clone();
        if (copy.length == 1) {
            return constant(copy[0]);
        }
        int last = copy.length - 1;
        return t -> {
            float x = clamp01(t) * last;
            int i = Math.min((int) x, last - 1);
            float f = x - i;
            return copy[i] + (copy[i + 1] - copy[i]) * f;
        };
    }

    /** The same curve run backwards: its end at 0 and its start at 1. */
    default FxCurve reversed() {
        return t -> at(1.0f - clamp01(t));
    }

    /** This curve times a constant. */
    default FxCurve scaled(float factor) {
        return t -> at(t) * factor;
    }

    /** This curve times another, point by point: a width profile faded by an alpha profile, say. */
    default FxCurve times(FxCurve other) {
        return t -> at(t) * other.at(t);
    }

    /** This curve's 0 to 1 stretched to run from {@code from} to {@code to}. */
    default FxCurve between(float from, float to) {
        return t -> from + (to - from) * at(t);
    }

    /** This curve read only over part of the span: 0 before {@code start}, its end after {@code end}. */
    default FxCurve within(float start, float end) {
        float span = Math.max(1.0e-6f, end - start);
        return t -> at((t - start) / span);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
