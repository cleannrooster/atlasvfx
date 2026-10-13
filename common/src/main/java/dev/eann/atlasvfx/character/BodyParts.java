package dev.eann.atlasvfx.character;

import dev.eann.atlasvfx.parity.LayerSource;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.feature.Deadmau5FeatureRenderer;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.feature.HorseArmorFeatureRenderer;
import net.minecraft.client.render.entity.feature.LlamaDecorFeatureRenderer;
import net.minecraft.client.render.entity.feature.SaddleFeatureRenderer;
import net.minecraft.client.render.entity.feature.ShoulderParrotFeatureRenderer;
import net.minecraft.client.render.entity.feature.StuckObjectsFeatureRenderer;
import net.minecraft.client.render.entity.feature.TridentRiptideFeatureRenderer;
import net.minecraft.client.render.entity.feature.WolfArmorFeatureRenderer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which body part each feature renderer draws: the game's own are known, a mod names its own with
 * {@link #register}, and anything else is {@link BodyPart#OTHER} - modded layers take a look by
 * default.
 *
 * <p>A renderer counts as what its nearest registered superclass is, so a mod's subclass of the
 * armour renderer is armour without saying so.</p>
 */
public final class BodyParts {

    // Copy-on-write: registered during NeoForge's parallel mod construction, read every frame.
    private static volatile Map<Class<?>, BodyPart> registered = defaults();
    // What each renderer class resolved to; dropped whenever a registration could change it.
    private static final Map<Class<?>, BodyPart> RESOLVED = new ConcurrentHashMap<>();

    private BodyParts() {
    }

    private static Map<Class<?>, BodyPart> defaults() {
        Map<Class<?>, BodyPart> map = new HashMap<>();
        map.put(ArmorFeatureRenderer.class, BodyPart.ARMOR);
        map.put(HorseArmorFeatureRenderer.class, BodyPart.ARMOR);
        map.put(WolfArmorFeatureRenderer.class, BodyPart.ARMOR);
        map.put(LlamaDecorFeatureRenderer.class, BodyPart.ARMOR);
        map.put(SaddleFeatureRenderer.class, BodyPart.ARMOR);
        map.put(CapeFeatureRenderer.class, BodyPart.CAPE);
        map.put(ElytraFeatureRenderer.class, BodyPart.ELYTRA);
        map.put(HeadFeatureRenderer.class, BodyPart.HEAD);
        map.put(HeldItemFeatureRenderer.class, BodyPart.HELD);
        map.put(StuckObjectsFeatureRenderer.class, BodyPart.ATTACHMENT);
        map.put(ShoulderParrotFeatureRenderer.class, BodyPart.ATTACHMENT);
        map.put(TridentRiptideFeatureRenderer.class, BodyPart.ATTACHMENT);
        map.put(Deadmau5FeatureRenderer.class, BodyPart.SKIN);
        // This library's own overlays are drawn over a body, never swapped.
        map.put(OverlayFeatureRenderer.class, BodyPart.ATTACHMENT);
        return Map.copyOf(map);
    }

    /** Says that {@code featureRenderer}, and its subclasses, draw {@code part}. */
    public static synchronized void register(Class<?> featureRenderer, BodyPart part) {
        Map<Class<?>, BodyPart> next = new HashMap<>(registered);
        next.put(featureRenderer, part);
        registered = Map.copyOf(next);
        RESOLVED.clear();
    }

    /**
     * The part a layer asked for by {@code source} is drawn for; null for what is no part of the
     * body at all - the dispatcher's shadow and fire.
     */
    public static @Nullable BodyPart of(@Nullable LayerSource source) {
        if (source instanceof LayerSource.Model) {
            return BodyPart.SKIN;
        }
        if (source instanceof LayerSource.FirstPersonArm) {
            return BodyPart.ARM;
        }
        if (source instanceof LayerSource.Feature feature) {
            return of(feature.renderer());
        }
        return null;
    }

    /** The part a feature renderer of this class draws. */
    public static BodyPart of(Class<?> featureRenderer) {
        return RESOLVED.computeIfAbsent(featureRenderer, BodyParts::resolve);
    }

    private static BodyPart resolve(Class<?> type) {
        Map<Class<?>, BodyPart> map = registered;
        for (Class<?> at = type; at != null; at = at.getSuperclass()) {
            BodyPart part = map.get(at);
            if (part != null) {
                return part;
            }
        }
        return BodyPart.OTHER;
    }
}
