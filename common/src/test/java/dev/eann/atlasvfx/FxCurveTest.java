package dev.eann.atlasvfx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Curves and gradients: ends, clamping, and what composing them does. */
class FxCurveTest {

    private static final float EPSILON = 1.0e-5f;

    @Test
    @DisplayName("The stock curves start and end where they say, and clamp outside the span")
    void stockCurvesHoldTheirEnds() {
        for (FxCurve curve : new FxCurve[] {FxCurve.LINEAR, FxCurve.SMOOTH, FxCurve.easeIn(2.0f),
                FxCurve.easeOut(3.0f)}) {
            assertEquals(0.0f, curve.at(0.0f), EPSILON);
            assertEquals(1.0f, curve.at(1.0f), EPSILON);
            assertEquals(0.0f, curve.at(-0.5f), EPSILON);
            assertEquals(1.0f, curve.at(1.5f), EPSILON);
        }
        assertEquals(0.0f, FxCurve.HUMP.at(0.0f), EPSILON);
        assertEquals(1.0f, FxCurve.HUMP.at(0.5f), EPSILON);
        assertEquals(0.0f, FxCurve.HUMP.at(1.0f), EPSILON);
    }

    @Test
    @DisplayName("Easing in starts slow and easing out starts fast")
    void easingLeansTheRightWay() {
        assertTrue(FxCurve.easeIn(2.0f).at(0.5f) < 0.5f);
        assertTrue(FxCurve.easeOut(2.0f).at(0.5f) > 0.5f);
    }

    @Test
    @DisplayName("Points are joined by straight lines, evenly spaced from 0 to 1")
    void pointsInterpolate() {
        FxCurve curve = FxCurve.points(1.0f, 0.5f, 0.0f);
        assertEquals(1.0f, curve.at(0.0f), EPSILON);
        assertEquals(0.75f, curve.at(0.25f), EPSILON);
        assertEquals(0.5f, curve.at(0.5f), EPSILON);
        assertEquals(0.0f, curve.at(1.0f), EPSILON);
        assertEquals(0.3f, FxCurve.points(0.3f).at(0.8f), EPSILON);
        assertThrows(IllegalArgumentException.class, FxCurve::points);
    }

    @Test
    @DisplayName("Composed curves reverse, scale, multiply, remap and window as they say")
    void compositionBehaves() {
        assertEquals(0.75f, FxCurve.LINEAR.reversed().at(0.25f), EPSILON);
        assertEquals(1.0f, FxCurve.LINEAR.scaled(2.0f).at(0.5f), EPSILON);
        assertEquals(0.25f, FxCurve.LINEAR.times(FxCurve.LINEAR).at(0.5f), EPSILON);
        assertEquals(0.6f, FxCurve.LINEAR.between(0.2f, 1.0f).at(0.5f), EPSILON);
        FxCurve window = FxCurve.LINEAR.within(0.5f, 1.0f);
        assertEquals(0.0f, window.at(0.25f), EPSILON);
        assertEquals(0.5f, window.at(0.75f), EPSILON);
        assertEquals(1.0f, window.at(1.0f), EPSILON);
    }

    @Test
    @DisplayName("A gradient holds its end colours past its stops and blends between them")
    void gradientBlends() {
        FxGradient gradient = FxGradient.of(0xFF0000, 0x0000FF);
        assertEquals(0xFF0000, gradient.at(-1.0f));
        assertEquals(0x0000FF, gradient.at(2.0f));
        assertEquals(FxColor.mix(0xFF0000, 0x0000FF, 0.5f), gradient.at(0.5f));
        assertEquals(0x123456, FxGradient.constant(0x123456).at(0.7f));

        FxGradient stopped = FxGradient.at(new float[] {0.0f, 0.8f, 1.0f}, new int[] {0xFFFFFF, 0xFF0000, 0x000000});
        assertEquals(0xFF0000, stopped.at(0.8f));
        assertEquals(FxColor.mix(0xFFFFFF, 0xFF0000, 0.5f), stopped.at(0.4f));
        assertEquals(0x000000, stopped.reversed().at(0.0f));
        assertEquals(0xFF0000, stopped.reversed().at(0.2f));
    }

    @Test
    @DisplayName("A gradient refuses stops that do not run from 0 to 1 in order")
    void gradientValidates() {
        assertThrows(IllegalArgumentException.class, FxGradient::of);
        assertThrows(IllegalArgumentException.class,
                () -> FxGradient.at(new float[] {0.0f, 0.5f}, new int[] {0, 1, 2}));
        assertThrows(IllegalArgumentException.class,
                () -> FxGradient.at(new float[] {0.5f, 0.5f}, new int[] {0, 1}));
        assertThrows(IllegalArgumentException.class,
                () -> FxGradient.at(new float[] {0.0f, 1.5f}, new int[] {0, 1}));
    }
}
