package dev.eann.atlasvfx.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link GeoAnimator} that needs no code: the clips a humanoid model plays standing, moving each
 * way, attacking and using an item, by name. What an appearance file's {@code clips} says.
 *
 * <p>Standing still loops {@code stance}. Moving loops {@code advance}, {@code backstep},
 * {@code lateral_left} or {@code lateral_right} by which way the body moves against where it faces,
 * faster the faster it goes; a clip left out falls back to {@code advance}. Each swing plays the
 * next of {@code swings}; starting to use an item plays {@code use}.</p>
 *
 * @param swingSpeed how fast swings play: 1 for their own pace
 */
public record ClipAnimator(@Nullable String stance, @Nullable String advance, @Nullable String backstep,
                           @Nullable String lateralLeft, @Nullable String lateralRight, List<String> swings,
                           @Nullable String use, float swingSpeed) implements GeoAnimator {

    /** Blocks a tick below which a body is standing. */
    private static final double STILL = 0.015;
    /** Blocks a tick a walking pace plays at full speed. */
    private static final double PACE = 0.2;

    public ClipAnimator {
        swings = List.copyOf(swings);
    }

    /**
     * Reads {@code clips} from an appearance file. Every name may be given whole, or after a
     * {@code prefix} - {@code "prefix": "animation.v2.trickster."} and {@code "stance": "stance"}.
     */
    public static ClipAnimator parse(JsonObject json) {
        String prefix = json.has("prefix") ? json.get("prefix").getAsString() : "";
        List<String> swings = new ArrayList<>();
        JsonElement swingJson = json.get("swing");
        if (swingJson != null && swingJson.isJsonArray()) {
            for (JsonElement element : (JsonArray) swingJson) {
                swings.add(prefix + element.getAsString());
            }
        } else if (swingJson != null) {
            swings.add(prefix + swingJson.getAsString());
        }
        return new ClipAnimator(clip(json, prefix, "stance"), clip(json, prefix, "advance"),
                clip(json, prefix, "backstep"), clip(json, prefix, "lateral_left"), clip(json, prefix, "lateral_right"),
                swings, clip(json, prefix, "use"),
                json.has("swing_speed") ? json.get("swing_speed").getAsFloat() : 1.0f);
    }

    private static @Nullable String clip(JsonObject json, String prefix, String key) {
        return json.has(key) ? prefix + json.get(key).getAsString() : null;
    }

    @Override
    public void tick(LivingEntity entity, GeoAnimationState state) {
        if (entity.handSwinging && entity.handSwingTicks == 0 && !swings.isEmpty()) {
            // Swings in turn, by when they come: a combo alternates without anything to remember.
            state.play(swings.get(Math.floorMod(entity.age / 8, swings.size())), swingSpeed);
        } else if (use != null && entity.isUsingItem() && entity.getItemUseTime() == 1) {
            state.play(use, 1.0f);
        }
        double dx = entity.getX() - entity.prevX;
        double dz = entity.getZ() - entity.prevZ;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (speed < STILL || advance == null) {
            if (stance != null) {
                state.loop(stance, 1.0f);
            }
            return;
        }
        float yaw = entity.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        // Facing (-sin, cos); its left is (cos, sin).
        double forward = dx * -MathHelper.sin(yaw) + dz * MathHelper.cos(yaw);
        double left = dx * MathHelper.cos(yaw) + dz * MathHelper.sin(yaw);
        float pace = (float) MathHelper.clamp(speed / PACE, 0.6, 1.8);
        String clip;
        if (Math.abs(forward) >= Math.abs(left)) {
            clip = forward > 0.0 || backstep == null ? advance : backstep;
        } else {
            String side = left > 0.0 ? lateralLeft : lateralRight;
            clip = side != null ? side : advance;
        }
        state.loop(clip, pace);
    }
}
