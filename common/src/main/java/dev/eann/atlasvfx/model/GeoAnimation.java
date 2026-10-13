package dev.eann.atlasvfx.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One clip from a Bedrock animation file - what Blockbench, GeckoLib and AzureLib save - read into
 * keyframe tracks and played without GeckoLib.
 *
 * <p>Values may be numbers or sums of numbers ({@code "28+720"}); anything that needs the game to
 * evaluate - {@code query.anim_time}, {@code math.sin} - is read as 0 and counted in
 * {@link #unsupported()}. Keyframes ease by the name the next one gives (GeckoLib's
 * {@code easing}), or by Catmull-Rom where the file says {@code lerp_mode: catmullrom}.</p>
 *
 * <p>Sampled values are in the model part's terms: rotations in radians and positions in pixels,
 * already turned the way {@link GeoBaker} turns the geometry.</p>
 *
 * @param length seconds
 */
public record GeoAnimation(String name, float length, Loop loop, Map<String, BoneTracks> bones, int unsupported) {

    public enum Loop {
        /** Plays once and lets go. */
        ONCE,
        /** Plays over and over. */
        LOOP,
        /** Plays once and holds its last frame. */
        HOLD
    }

    /** One bone's tracks; any may be null. */
    public record BoneTracks(@Nullable Track rotation, @Nullable Track position, @Nullable Track scale) {
    }

    /** One channel's keyframes, sorted by time. */
    public record Track(float[] times, float[][] pre, float[][] post, Easing[] easings, float[] easingArgs,
                        boolean[] smooth) {

        /** The value at {@code seconds}, into {@code out}. */
        public float[] sample(float seconds, float[] out) {
            int n = times.length;
            if (n == 1 || seconds <= times[0]) {
                return copy(n == 1 ? post[0] : pre[0], out);
            }
            if (seconds >= times[n - 1]) {
                return copy(post[n - 1], out);
            }
            int i = 0;
            while (i < n - 2 && seconds >= times[i + 1]) {
                i++;
            }
            float span = times[i + 1] - times[i];
            float t = span <= 0.0f ? 1.0f : (seconds - times[i]) / span;
            float eased = easings[i + 1].apply(t, easingArgs[i + 1]);
            if (smooth[i] || smooth[i + 1]) {
                float[] p0 = post[Math.max(0, i - 1)];
                float[] p3 = pre[Math.min(n - 1, i + 2)];
                for (int c = 0; c < 3; c++) {
                    out[c] = catmullRom(p0[c], post[i][c], pre[i + 1][c], p3[c], t);
                }
                return out;
            }
            for (int c = 0; c < 3; c++) {
                out[c] = post[i][c] + (pre[i + 1][c] - post[i][c]) * eased;
            }
            return out;
        }

        private static float[] copy(float[] from, float[] out) {
            out[0] = from[0];
            out[1] = from[1];
            out[2] = from[2];
            return out;
        }

        private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
            float t2 = t * t;
            float t3 = t2 * t;
            return 0.5f * (2.0f * p1 + (p2 - p0) * t + (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2
                    + (3.0f * p1 - p0 - 3.0f * p2 + p3) * t3);
        }
    }

    /** Every clip in an animation file, by name. */
    public static Map<String, GeoAnimation> parseAll(JsonObject root) {
        Map<String, GeoAnimation> clips = new HashMap<>();
        JsonObject animations = root.getAsJsonObject("animations");
        if (animations == null) {
            return clips;
        }
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            clips.put(entry.getKey(), parse(entry.getKey(), entry.getValue().getAsJsonObject()));
        }
        return clips;
    }

    static GeoAnimation parse(String name, JsonObject json) {
        Loop loop = Loop.ONCE;
        JsonElement loopJson = json.get("loop");
        if (loopJson != null && loopJson.isJsonPrimitive()) {
            JsonPrimitive primitive = loopJson.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                loop = primitive.getAsBoolean() ? Loop.LOOP : Loop.ONCE;
            } else if (primitive.isString() && primitive.getAsString().equals("hold_on_last_frame")) {
                loop = Loop.HOLD;
            }
        }
        Counter unsupported = new Counter();
        Map<String, BoneTracks> bones = new HashMap<>();
        float last = 0.0f;
        JsonObject bonesJson = json.getAsJsonObject("bones");
        if (bonesJson != null) {
            for (Map.Entry<String, JsonElement> entry : bonesJson.entrySet()) {
                JsonObject bone = entry.getValue().getAsJsonObject();
                Track rotation = track(bone.get("rotation"), Channel.ROTATION, unsupported);
                Track position = track(bone.get("position"), Channel.POSITION, unsupported);
                Track scale = track(bone.get("scale"), Channel.SCALE, unsupported);
                for (Track track : new Track[]{rotation, position, scale}) {
                    if (track != null) {
                        last = Math.max(last, track.times()[track.times().length - 1]);
                    }
                }
                bones.put(entry.getKey(), new BoneTracks(rotation, position, scale));
            }
        }
        float length = json.has("animation_length") ? json.get("animation_length").getAsFloat() : last;
        return new GeoAnimation(name, Math.max(length, 1.0e-3f), loop, Map.copyOf(bones), unsupported.count);
    }

    private enum Channel { ROTATION, POSITION, SCALE }

    private static final class Counter {
        int count;
    }

    private record Key(float time, float[] pre, float[] post, Easing easing, float arg, boolean smooth) {
    }

    private static @Nullable Track track(@Nullable JsonElement json, Channel channel, Counter unsupported) {
        if (json == null) {
            return null;
        }
        List<Key> keys = new ArrayList<>();
        if (json.isJsonObject() && !json.getAsJsonObject().has("vector")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                float time;
                try {
                    time = Float.parseFloat(entry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                keys.add(key(time, entry.getValue(), channel, unsupported));
            }
        } else {
            keys.add(key(0.0f, json, channel, unsupported));
        }
        if (keys.isEmpty()) {
            return null;
        }
        keys.sort(Comparator.comparingDouble(Key::time));
        int n = keys.size();
        float[] times = new float[n];
        float[][] pre = new float[n][];
        float[][] post = new float[n][];
        Easing[] easings = new Easing[n];
        float[] args = new float[n];
        boolean[] smooth = new boolean[n];
        for (int i = 0; i < n; i++) {
            Key key = keys.get(i);
            times[i] = key.time();
            pre[i] = key.pre();
            post[i] = key.post();
            easings[i] = key.easing();
            args[i] = key.arg();
            smooth[i] = key.smooth();
        }
        return new Track(times, pre, post, easings, args, smooth);
    }

    private static Key key(float time, JsonElement json, Channel channel, Counter unsupported) {
        Easing easing = Easing.LINEAR;
        float arg = Float.NaN;
        boolean smooth = false;
        float[] pre;
        float[] post;
        if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("easing")) {
                easing = Easing.named(object.get("easing").getAsString());
            }
            if (object.has("easingArgs") && object.get("easingArgs").isJsonArray()
                    && !object.getAsJsonArray("easingArgs").isEmpty()) {
                arg = object.getAsJsonArray("easingArgs").get(0).getAsFloat();
            }
            if (object.has("lerp_mode")) {
                smooth = object.get("lerp_mode").getAsString().equals("catmullrom");
            }
            if (object.has("vector")) {
                pre = vector(object.get("vector"), channel, unsupported);
                post = pre;
            } else {
                post = object.has("post") ? vector(object.get("post"), channel, unsupported) : null;
                pre = object.has("pre") ? vector(object.get("pre"), channel, unsupported) : post;
                if (post == null) {
                    post = pre != null ? pre : identity(channel);
                }
                if (pre == null) {
                    pre = post;
                }
            }
        } else {
            pre = vector(json, channel, unsupported);
            post = pre;
        }
        return new Key(time, pre, post, easing, arg, smooth);
    }

    /** A value in the file - a number, a sum, or three - turned into the model part's terms. */
    private static float[] vector(JsonElement json, Channel channel, Counter unsupported) {
        float[] v = new float[3];
        if (json.isJsonArray()) {
            JsonArray array = json.getAsJsonArray();
            for (int i = 0; i < 3; i++) {
                v[i] = i < array.size() ? number(array.get(i), unsupported) : v[Math.max(0, i - 1)];
            }
        } else {
            float single = number(json, unsupported);
            v[0] = single;
            v[1] = single;
            v[2] = single;
        }
        return switch (channel) {
            case ROTATION -> new float[]{(float) Math.toRadians(v[0]), (float) Math.toRadians(v[1]),
                    (float) Math.toRadians(v[2])};
            // Up in the file is down in a model part.
            case POSITION -> new float[]{v[0], -v[1], v[2]};
            case SCALE -> v;
        };
    }

    private static float[] identity(Channel channel) {
        return channel == Channel.SCALE ? new float[]{1.0f, 1.0f, 1.0f} : new float[3];
    }

    private static float number(JsonElement json, Counter unsupported) {
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            return json.getAsFloat();
        }
        Float value = Constants.evaluate(json.getAsString());
        if (value == null) {
            unsupported.count++;
            return 0.0f;
        }
        return value;
    }

    /** Arithmetic on numbers alone: {@code +}, {@code -}, {@code *}, {@code /} and brackets. */
    static final class Constants {
        private final String text;
        private int at;

        private Constants(String text) {
            this.text = text;
        }

        /** The value of {@code text}, or null if it is anything but arithmetic on numbers. */
        static @Nullable Float evaluate(String text) {
            Constants parser = new Constants(text.replace(" ", ""));
            try {
                double value = parser.sum();
                return parser.at == parser.text.length() && Double.isFinite(value) ? (float) value : null;
            } catch (RuntimeException e) {
                return null;
            }
        }

        private double sum() {
            double value = product();
            while (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
                char op = text.charAt(at++);
                double next = product();
                value = op == '+' ? value + next : value - next;
            }
            return value;
        }

        private double product() {
            double value = unary();
            while (at < text.length() && (text.charAt(at) == '*' || text.charAt(at) == '/')) {
                char op = text.charAt(at++);
                double next = unary();
                value = op == '*' ? value * next : value / next;
            }
            return value;
        }

        private double unary() {
            if (at < text.length() && text.charAt(at) == '-') {
                at++;
                return -unary();
            }
            if (at < text.length() && text.charAt(at) == '+') {
                at++;
                return unary();
            }
            if (at < text.length() && text.charAt(at) == '(') {
                at++;
                double value = sum();
                if (at >= text.length() || text.charAt(at) != ')') {
                    throw new IllegalArgumentException("unclosed bracket");
                }
                at++;
                return value;
            }
            int start = at;
            while (at < text.length() && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.')) {
                at++;
            }
            if (start == at) {
                throw new IllegalArgumentException("not a number");
            }
            return Double.parseDouble(text.substring(start, at));
        }
    }
}
