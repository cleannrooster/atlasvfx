package dev.eann.atlasvfx.model;

import dev.eann.atlasvfx.character.CharacterEffects;
import dev.eann.atlasvfx.parity.LayerSource;
import dev.eann.atlasvfx.parity.ProviderSeam;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws replacement models where the bodies they replace are drawn: posed and drawn right after
 * the vanilla model's own draw - which drew nothing, its layers hidden by the provider seam - in
 * the same matrices, light, hurt tint and colour; and over the first-person arm.
 *
 * <p>Called from this mod's mixins and overlay layer, on the render thread.</p>
 */
@ApiStatus.Internal
public final class Replacements {

    /** A body's replacement, with its model loaded. */
    public record Active(ModelReplacement replacement, BakedGeo model) {
    }

    /** What a replaced first-person arm was drawn as, for overlays to be drawn over. */
    public record PosedArm(ModelPart bone, Identifier texture) {
    }

    // One state per body per replacement: a screen previewing several on the same player must not
    // have them restart each other's clips.
    private static final Map<Entity, Map<ModelReplacement, GeoAnimationState>> STATES = new WeakHashMap<>();

    private Replacements() {
    }

    static void clearStates() {
        STATES.clear();
    }

    /** {@code entity}'s replacement now, if it has one and its model loads. */
    public static @Nullable Active active(Entity entity) {
        if (!CharacterEffects.any()) {
            return null;
        }
        ModelReplacement replacement = CharacterEffects.of(entity).replacement();
        if (replacement == null) {
            return null;
        }
        BakedGeo model = GeoAssets.model(replacement.geo());
        return model == null ? null : new Active(replacement, model);
    }

    /** After the vanilla model's draw: poses and draws {@code entity}'s replacement, if it has one. */
    public static void drawBody(LivingEntity entity, EntityModel<?> vanilla, MatrixStack matrices,
                                VertexConsumerProvider consumers, int light, int overlay, int color, float tickDelta) {
        Active active = active(entity);
        if (active == null) {
            return;
        }
        pose(entity, active, vanilla, tickDelta);
        RenderLayer layer = layer(active.replacement().texture(), color);
        draw(() -> active.model().root().render(matrices, consumers.getBuffer(layer), light, overlay, color));
    }

    /**
     * After a first-person arm's draw: draws the replacement's arm bone in its place, posed as the
     * vanilla arm was; null if the body has no replacement or its model no arm on that side.
     */
    public static @Nullable PosedArm drawArm(LivingEntity player, Arm side, PlayerEntityModel<?> vanilla,
                                             MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        Active active = active(player);
        if (active == null) {
            return null;
        }
        BakedGeo model = active.model();
        String name = side == Arm.RIGHT ? model.rig().rightArm() : model.rig().leftArm();
        ModelPart bone = model.bone(name);
        if (bone == null) {
            return null;
        }
        model.reset();
        ModelPart arm = side == Arm.RIGHT ? vanilla.rightArm : vanilla.leftArm;
        ModelTransform armRest = arm.getDefaultTransform();
        // Drawn on its own, the bone's pivot is where it is in the whole model, not under its parents.
        float[] rest = restPivot(model, name);
        bone.pivotX = rest[0] + arm.pivotX - armRest.pivotX;
        bone.pivotY = rest[1] + arm.pivotY - armRest.pivotY;
        bone.pivotZ = rest[2] + arm.pivotZ - armRest.pivotZ;
        bone.pitch += arm.pitch - armRest.pitch;
        bone.yaw += arm.yaw - armRest.yaw;
        bone.roll += arm.roll - armRest.roll;
        Identifier texture = active.replacement().texture();
        draw(() -> bone.render(matrices, consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)), light,
                net.minecraft.client.render.OverlayTexture.DEFAULT_UV));
        return new PosedArm(bone, texture);
    }

    /** The layer a replacement is drawn in: see-through where the body is drawn see-through. */
    static RenderLayer layer(Identifier texture, int color) {
        return (color >>> 24) < 255 ? RenderLayer.getEntityTranslucent(texture)
                : RenderLayer.getEntityCutoutNoCull(texture);
    }

    /** Draws as the replacement: the provider seam leaves these layers alone. */
    private static void draw(Runnable drawing) {
        boolean scoped = ProviderSeam.scoped();
        if (scoped) {
            ProviderSeam.enter(new LayerSource.Replacement());
        }
        try {
            drawing.run();
        } finally {
            if (scoped) {
                ProviderSeam.exit();
            }
        }
    }

    /** Puts a replacement's bones where they are this frame. */
    static void pose(Entity entity, Active active, EntityModel<?> vanilla, float tickDelta) {
        BakedGeo model = active.model();
        ModelReplacement replacement = active.replacement();
        model.reset();
        if (replacement.posing() == ModelReplacement.Posing.ANIMATED) {
            if (replacement.animator() != null && entity instanceof LivingEntity living) {
                GeoAnimationState state = STATES.computeIfAbsent(entity, key -> new IdentityHashMap<>())
                        .computeIfAbsent(replacement, key -> new GeoAnimationState());
                state.bind(GeoAssets.animations(replacement.animations()));
                int age = entity.age;
                if (state.lastTick == Integer.MIN_VALUE || age - state.lastTick > 20 || age < state.lastTick) {
                    state.lastTick = age - 1;
                }
                while (state.lastTick < age) {
                    state.lastTick++;
                    state.at(state.lastTick);
                    replacement.animator().tick(living, state);
                }
                state.apply(model, age + tickDelta);
            }
            if (replacement.followLook()) {
                look(model, entity, vanilla, tickDelta);
            }
        } else if (vanilla instanceof BipedEntityModel<?> biped) {
            GeoRig rig = model.rig();
            if (replacement.followLook()) {
                follow(model.bone(rig.head()), biped.head);
            }
            follow(model.bone(rig.body()), biped.body);
            follow(model.bone(rig.rightArm()), biped.rightArm);
            follow(model.bone(rig.leftArm()), biped.leftArm);
            follow(model.bone(rig.rightLeg()), biped.rightLeg);
            follow(model.bone(rig.leftLeg()), biped.leftLeg);
        }
        if (vanilla instanceof BipedEntityModel<?> biped && partlyHidden(biped)) {
            showAsVanilla(model, biped);
        }
    }

    /**
     * Whether some of the vanilla body is hidden this draw - by a first-person pass, as
     * PlayerAnimator's for Better Combat's swings draws only the arms, or by a spectator's view.
     */
    static boolean partlyHidden(BipedEntityModel<?> biped) {
        return !(biped.head.visible && biped.body.visible && biped.rightArm.visible && biped.leftArm.visible
                && biped.rightLeg.visible && biped.leftLeg.visible);
    }

    /**
     * Shows only the replacement's bones whose vanilla part is showing, and has them follow it: a
     * first-person pass that draws only the arms gets only the replacement's arms, where the vanilla
     * arms are, and nothing else to block the view.
     *
     * <p>A bone the rig does not name shows if what it hangs from does, or with the vanilla body if it
     * hangs from nothing named. A bone that holds a showing bone but does not show itself still
     * carries it, its own cubes hidden.</p>
     */
    static void showAsVanilla(BakedGeo model, BipedEntityModel<?> biped) {
        GeoRig rig = model.rig();
        Map<String, ModelPart> vanilla = new HashMap<>();
        putIfNamed(vanilla, rig.head(), biped.head);
        putIfNamed(vanilla, rig.body(), biped.body);
        putIfNamed(vanilla, rig.rightArm(), biped.rightArm);
        putIfNamed(vanilla, rig.leftArm(), biped.leftArm);
        putIfNamed(vanilla, rig.rightLeg(), biped.rightLeg);
        putIfNamed(vanilla, rig.leftLeg(), biped.leftLeg);
        Map<String, String> parents = new HashMap<>();
        for (GeoModel.Bone bone : model.source().bones()) {
            if (bone.parent() != null) {
                parents.put(bone.name(), bone.parent());
            }
        }
        Map<String, Boolean> shown = new HashMap<>();
        for (GeoModel.Bone bone : model.source().bones()) {
            shown(bone.name(), vanilla, parents, biped.body.visible, shown);
        }
        // Whatever holds a showing bone has to be drawn for it to be.
        Map<String, Boolean> carries = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : shown.entrySet()) {
            if (entry.getValue()) {
                for (String at = parents.get(entry.getKey()); at != null; at = parents.get(at)) {
                    carries.put(at, true);
                }
            }
        }
        for (Map.Entry<String, Boolean> entry : shown.entrySet()) {
            ModelPart part = model.bone(entry.getKey());
            if (part == null) {
                continue;
            }
            boolean show = entry.getValue();
            boolean carry = carries.getOrDefault(entry.getKey(), false);
            part.visible = show || carry;
            part.hidden = !show;
            if (!show) {
                // Its rotated cubes sit in parts of their own: hide those with it.
                for (int i = 0; part.hasChild("#cube" + i); i++) {
                    part.getChild("#cube" + i).visible = false;
                }
            }
        }
        // Showing arms follow the vanilla ones, wherever an animation had put them.
        followShown(model, rig.rightArm(), biped.rightArm);
        followShown(model, rig.leftArm(), biped.leftArm);
    }

    private static void followShown(BakedGeo model, @Nullable String name, ModelPart vanilla) {
        ModelPart bone = model.bone(name);
        if (bone != null && vanilla.visible) {
            bone.resetTransform();
            follow(bone, vanilla);
        }
    }

    private static void putIfNamed(Map<String, ModelPart> map, @Nullable String name, ModelPart part) {
        if (name != null) {
            map.put(name, part);
        }
    }

    private static boolean shown(String bone, Map<String, ModelPart> vanilla, Map<String, String> parents,
                                 boolean bodyShown, Map<String, Boolean> memo) {
        Boolean known = memo.get(bone);
        if (known != null) {
            return known;
        }
        ModelPart part = vanilla.get(bone);
        String parent = parents.get(bone);
        boolean show = part != null ? part.visible
                : parent != null ? shown(parent, vanilla, parents, bodyShown, memo) : bodyShown;
        memo.put(bone, show);
        return show;
    }

    /** Moves a bone by however far the vanilla part it stands for has moved from its own rest. */
    static void follow(@Nullable ModelPart bone, ModelPart part) {
        if (bone == null) {
            return;
        }
        ModelTransform rest = part.getDefaultTransform();
        bone.pivotX += part.pivotX - rest.pivotX;
        bone.pivotY += part.pivotY - rest.pivotY;
        bone.pivotZ += part.pivotZ - rest.pivotZ;
        bone.pitch += part.pitch - rest.pitch;
        bone.yaw += part.yaw - rest.yaw;
        bone.roll += part.roll - rest.roll;
    }

    /** Turns the head bone, if there is one, where the body looks, over whatever it is playing. */
    static void look(BakedGeo model, Entity entity, EntityModel<?> vanilla, float tickDelta) {
        ModelPart head = model.bone(model.rig().head());
        if (head == null) {
            return;
        }
        if (vanilla instanceof BipedEntityModel<?> biped) {
            // The vanilla head already looks where the body does, and where animation mods turn it.
            ModelTransform rest = biped.head.getDefaultTransform();
            head.yaw += biped.head.yaw - rest.yaw;
            head.pitch += biped.head.pitch - rest.pitch;
            return;
        }
        if (entity instanceof LivingEntity living) {
            float yaw = MathHelper.wrapDegrees(MathHelper.lerpAngleDegrees(tickDelta, living.prevHeadYaw, living.headYaw)
                    - MathHelper.lerpAngleDegrees(tickDelta, living.prevBodyYaw, living.bodyYaw));
            head.yaw += MathHelper.clamp(yaw, -85.0f, 85.0f) * MathHelper.RADIANS_PER_DEGREE;
            head.pitch += entity.getPitch(tickDelta) * MathHelper.RADIANS_PER_DEGREE;
        }
    }

    /** Where a bone's pivot is at rest in the whole model: its own offset and its parents'. */
    static float[] restPivot(BakedGeo model, String name) {
        Map<String, String> parents = new HashMap<>();
        for (GeoModel.Bone bone : model.source().bones()) {
            if (bone.parent() != null) {
                parents.put(bone.name(), bone.parent());
            }
        }
        float[] pivot = new float[3];
        for (String at = name; at != null; at = parents.get(at)) {
            ModelPart part = model.bone(at);
            if (part == null) {
                break;
            }
            ModelTransform rest = part.getDefaultTransform();
            pivot[0] += rest.pivotX;
            pivot[1] += rest.pivotY;
            pivot[2] += rest.pivotZ;
        }
        return pivot;
    }
}
