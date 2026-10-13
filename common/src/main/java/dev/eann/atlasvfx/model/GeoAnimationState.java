package dev.eann.atlasvfx.model;

import net.minecraft.client.model.ModelPart;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What one body's replacement model is playing: a looping base clip - standing, walking - that
 * crossfades when it changes, and an action clip played once over it - a swing, a roll - that
 * takes over the bones it moves and hands them back when it ends.
 *
 * <p>A {@link GeoAnimator} drives it once a tick; the replacement model samples it every frame,
 * between ticks, so it is smooth at any frame rate. Render thread only.</p>
 */
public final class GeoAnimationState {

    /** Ticks a change of base clip blends over. */
    static final float BASE_FADE = 4.0f;
    /** Ticks an action blends in over, and out over once it ends. */
    static final float ACTION_IN = 2.0f;
    static final float ACTION_OUT = 3.0f;

    private record Playing(GeoAnimation clip, float start, float speed) {
        float seconds(float age) {
            float seconds = Math.max(0.0f, (age - start) / 20.0f * speed);
            return clip.loop() == GeoAnimation.Loop.LOOP ? seconds % clip.length() : Math.min(seconds, clip.length());
        }

        /** Ticks since it ended, or negative while it still plays; a looping clip never ends. */
        float sinceEnd(float age) {
            if (clip.loop() != GeoAnimation.Loop.ONCE) {
                return -1.0f;
            }
            return (age - start) - clip.length() * 20.0f / Math.max(1.0e-3f, speed);
        }
    }

    private Map<String, GeoAnimation> clips = Map.of();
    private @Nullable Playing base;
    private @Nullable Playing fading;
    private float fadeStart;
    private @Nullable Playing action;
    private float now;
    int lastTick = Integer.MIN_VALUE;

    private final float[] scratchA = new float[3];
    private final float[] scratchB = new float[3];
    private final float[] rotation = new float[3];
    private final float[] position = new float[3];
    private final float[] scale = new float[3];

    void bind(Map<String, GeoAnimation> clips) {
        if (this.clips != clips) {
            this.clips = clips;
            base = null;
            fading = null;
            action = null;
        }
    }

    void at(float age) {
        now = age;
    }

    /**
     * Loops {@code clip} as the base, blending from whatever looped before; if it is already the
     * base, only its speed changes. An unknown clip leaves the base as it is.
     *
     * @param speed 1 for the clip's own pace
     */
    public void loop(String clip, float speed) {
        GeoAnimation animation = clips.get(clip);
        if (animation == null) {
            return;
        }
        if (base != null && base.clip() == animation) {
            if (base.speed() != speed) {
                // Keep the clip where it is in its cycle at the new pace.
                float seconds = base.seconds(now);
                base = new Playing(animation, now - seconds * 20.0f / Math.max(1.0e-3f, speed), speed);
            }
            return;
        }
        fading = base;
        fadeStart = now;
        base = new Playing(animation, now, speed);
    }

    /** Plays {@code clip} once over the base, from its start, replacing any action playing. */
    public void play(String clip, float speed) {
        GeoAnimation animation = clips.get(clip);
        if (animation != null) {
            action = new Playing(animation, now, speed);
        }
    }

    /** Whether an action is still playing, or holding its last frame. */
    public boolean acting() {
        return action != null && action.sinceEnd(now) < 0.0f;
    }

    /** The base clip's name; null before one is looped. */
    public @Nullable String base() {
        return base == null ? null : base.clip().name();
    }

    /** Whether the animation files bound to it have {@code clip}. */
    public boolean has(String clip) {
        return clips.containsKey(clip);
    }

    /** Moves {@code model}'s bones, already at rest, by what is playing at {@code age}. */
    void apply(BakedGeo model, float age) {
        now = age;
        if (action != null && action.sinceEnd(age) >= ACTION_OUT) {
            action = null;
        }
        float fade = fading == null ? 1.0f : Math.min(1.0f, (age - fadeStart) / BASE_FADE);
        if (fade >= 1.0f) {
            fading = null;
        }
        float actionWeight = 0.0f;
        if (action != null) {
            float in = Math.min(1.0f, (age - action.start()) / ACTION_IN);
            float since = action.sinceEnd(age);
            float out = since <= 0.0f ? 1.0f : Math.max(0.0f, 1.0f - since / ACTION_OUT);
            actionWeight = in * out;
        }
        Set<String> bones = new HashSet<>();
        for (Playing playing : new Playing[]{base, fading, action}) {
            if (playing != null) {
                bones.addAll(playing.clip().bones().keySet());
            }
        }
        for (String name : bones) {
            ModelPart part = model.bones().get(name);
            if (part == null) {
                continue;
            }
            blend(name, age, fade, actionWeight, Channel.ROTATION, rotation);
            blend(name, age, fade, actionWeight, Channel.POSITION, position);
            blend(name, age, fade, actionWeight, Channel.SCALE, scale);
            part.pitch += rotation[0];
            part.yaw += rotation[1];
            part.roll += rotation[2];
            part.pivotX += position[0];
            part.pivotY += position[1];
            part.pivotZ += position[2];
            part.xScale *= scale[0];
            part.yScale *= scale[1];
            part.zScale *= scale[2];
        }
    }

    private enum Channel { ROTATION, POSITION, SCALE }

    private void blend(String bone, float age, float fade, float actionWeight, Channel channel, float[] out) {
        float rest = channel == Channel.SCALE ? 1.0f : 0.0f;
        sample(base, bone, age, channel, rest, out);
        if (fading != null && fade < 1.0f) {
            sample(fading, bone, age, channel, rest, scratchA);
            for (int c = 0; c < 3; c++) {
                out[c] = scratchA[c] + (out[c] - scratchA[c]) * fade;
            }
        }
        if (action != null && actionWeight > 0.0f && track(action, bone, channel) != null) {
            sample(action, bone, age, channel, rest, scratchB);
            for (int c = 0; c < 3; c++) {
                out[c] = out[c] + (scratchB[c] - out[c]) * actionWeight;
            }
        }
    }

    private static void sample(@Nullable Playing playing, String bone, float age, Channel channel, float rest,
                               float[] out) {
        GeoAnimation.Track track = playing == null ? null : track(playing, bone, channel);
        if (track == null) {
            out[0] = rest;
            out[1] = rest;
            out[2] = rest;
            return;
        }
        track.sample(playing.seconds(age), out);
    }

    private static GeoAnimation.@Nullable Track track(Playing playing, String bone, Channel channel) {
        GeoAnimation.BoneTracks tracks = playing.clip().bones().get(bone);
        if (tracks == null) {
            return null;
        }
        return switch (channel) {
            case ROTATION -> tracks.rotation();
            case POSITION -> tracks.position();
            case SCALE -> tracks.scale();
        };
    }
}
