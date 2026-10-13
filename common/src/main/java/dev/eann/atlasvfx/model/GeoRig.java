package dev.eann.atlasvfx.model;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which of a model's bones are a humanoid's head, body, arms and legs, read from their names: what
 * lets the model follow the body it replaces, and turn its head where that body looks.
 *
 * <p>A head is found if any bone is plainly named one - {@code head}, {@code Head}, {@code bipedHead},
 * {@code head_bone} - or, failing that, the outermost bone with "head" in its name. Armour bones
 * ({@code armorHead}) and bones inside another head candidate are never taken for the head itself;
 * they turn with it as its children.</p>
 *
 * <p>The body is only taken if it holds none of the limbs: in many rigs the bone called "body" is the
 * root everything hangs from, and turning it would swing the legs with the torso. A {@code torso} or
 * {@code chest} under it is taken instead when there is one.</p>
 *
 * @param head     null if no bone is plainly a head
 * @param body     null if no bone is a torso that holds no limbs
 */
public record GeoRig(@Nullable String head, @Nullable String body, @Nullable String rightArm,
                     @Nullable String leftArm, @Nullable String rightLeg, @Nullable String leftLeg) {

    private static final List<String> HEADS = List.of("head", "headbone", "bipedhead", "mainhead");
    private static final List<String> BODIES = List.of("torso", "chest", "upperbody", "bipedbody", "body");
    private static final List<String> RIGHT_ARMS = List.of("rightarm", "armright", "bipedrightarm", "rarm", "armr");
    private static final List<String> LEFT_ARMS = List.of("leftarm", "armleft", "bipedleftarm", "larm", "arml");
    private static final List<String> RIGHT_LEGS = List.of("rightleg", "legright", "bipedrightleg", "rleg", "legr");
    private static final List<String> LEFT_LEGS = List.of("leftleg", "legleft", "bipedleftleg", "lleg", "legl");

    /** Reads the rig from a model's bones. */
    public static GeoRig of(GeoModel model) {
        Map<String, String> byKey = new HashMap<>();
        Map<String, String> parents = new HashMap<>();
        Map<String, float[]> pivots = new HashMap<>();
        for (GeoModel.Bone bone : model.bones()) {
            byKey.putIfAbsent(key(bone.name()), bone.name());
            if (bone.parent() != null) {
                parents.put(bone.name(), bone.parent());
            }
            pivots.put(bone.name(), bone.pivot());
        }
        String head = first(byKey, HEADS);
        if (head == null) {
            head = looseHead(model, parents, pivots);
        }
        String rightArm = first(byKey, RIGHT_ARMS);
        String leftArm = first(byKey, LEFT_ARMS);
        String rightLeg = first(byKey, RIGHT_LEGS);
        String leftLeg = first(byKey, LEFT_LEGS);
        String body = null;
        for (String candidate : BODIES) {
            String name = byKey.get(candidate);
            if (name != null && !holdsAny(name, parents, rightArm, leftArm, rightLeg, leftLeg)) {
                body = name;
                break;
            }
        }
        return new GeoRig(head, body, rightArm, leftArm, rightLeg, leftLeg);
    }

    /** A bone name with case and separators dropped: {@code Right_Arm} and {@code rightArm} alike. */
    static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static @Nullable String first(Map<String, String> byKey, List<String> keys) {
        for (String key : keys) {
            String name = byKey.get(key);
            if (name != null) {
                return name;
            }
        }
        return null;
    }

    /** The outermost bone with "head" in its name, not armour; the highest, if several. */
    private static @Nullable String looseHead(GeoModel model, Map<String, String> parents,
                                              Map<String, float[]> pivots) {
        String best = null;
        for (GeoModel.Bone bone : model.bones()) {
            String key = key(bone.name());
            if (!key.contains("head") || key.startsWith("armor")) {
                continue;
            }
            boolean inner = false;
            for (String at = parents.get(bone.name()); at != null; at = parents.get(at)) {
                String parentKey = key(at);
                if (parentKey.contains("head") && !parentKey.startsWith("armor")) {
                    inner = true;
                    break;
                }
            }
            if (!inner && (best == null || pivots.get(bone.name())[1] > pivots.get(best)[1])) {
                best = bone.name();
            }
        }
        return best;
    }

    /** Whether any of {@code limbs} hangs, however deep, from {@code bone}. */
    private static boolean holdsAny(String bone, Map<String, String> parents, @Nullable String... limbs) {
        for (String limb : limbs) {
            for (String at = limb == null ? null : parents.get(limb); at != null; at = parents.get(at)) {
                if (at.equals(bone)) {
                    return true;
                }
            }
        }
        return false;
    }
}
