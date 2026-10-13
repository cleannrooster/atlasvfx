package dev.eann.atlasvfx.appearance;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.eann.atlasvfx.character.BodyPart;
import dev.eann.atlasvfx.model.ClipAnimator;
import dev.eann.atlasvfx.model.ModelReplacement;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Every appearance there is to choose: those resource packs describe, and those mods register in
 * code. Client side.
 *
 * <p>A resource pack adds one as {@code assets/<namespace>/atlasvfx_appearances/<name>.json}, its id
 * {@code <namespace>:<name>}:</p>
 *
 * <pre>{@code
 * {
 *   "name": "Forsaken Rogue",                         // or a translation key
 *   "description": "A blade in the dark.",            // optional; also may be a key
 *   "geo": "rpg-minibosses:geo/thiefmob.json",
 *   "texture": "rpg-minibosses:textures/mob/thieftexture.png",
 *   "animations": "rpg-minibosses:animations/mobs_v2.animations.json",   // optional
 *   "posing": "follow",                               // the default: the body's own pose,
 *                                                     // or "animated": its own clips
 *   "hides": ["armor", "cape", "elytra", "head", "other"],               // optional
 *   "clips": {"prefix": "animation.v2.trickster.", "stance": "stance", "advance": "advance",
 *             "backstep": "backstep", "lateral_left": "lateral_left", "lateral_right": "lateral_right",
 *             "swing": ["cross_slash_r", "cross_slash_l"], "use": "throw_mobile", "swing_speed": 1.4}
 * }
 * }</pre>
 *
 * <p>A file that is broken is reported and left out; one registered in code with the same id wins.</p>
 */
public final class Appearances {

    private static final Logger LOGGER = LoggerFactory.getLogger("atlasvfx");
    static final String FOLDER = "atlasvfx_appearances";

    private static volatile Map<Identifier, Appearance> registered = Map.of();
    private static volatile Map<Identifier, Appearance> loaded = Map.of();

    private Appearances() {
    }

    /** Adds one in code, over any resource pack's with the same id. */
    public static synchronized void register(Appearance appearance) {
        Map<Identifier, Appearance> next = new HashMap<>(registered);
        next.put(appearance.id(), appearance);
        registered = Map.copyOf(next);
    }

    public static @Nullable Appearance get(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        Appearance appearance = registered.get(id);
        return appearance != null ? appearance : loaded.get(id);
    }

    /** Every appearance, by name. */
    public static List<Appearance> all() {
        Map<Identifier, Appearance> all = new HashMap<>(loaded);
        all.putAll(registered);
        List<Appearance> list = new ArrayList<>(all.values());
        list.sort(Comparator.comparing((Appearance appearance) -> appearance.name().getString())
                .thenComparing(appearance -> appearance.id().toString()));
        return list;
    }

    /** Reads every appearance resource packs describe. */
    public static void reload(ResourceManager manager) {
        Map<Identifier, Appearance> next = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : manager.findResources(FOLDER,
                path -> path.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            String path = file.getPath();
            Identifier id = Identifier.of(file.getNamespace(),
                    path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
            try (Reader reader = entry.getValue().getReader()) {
                next.put(id, parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                LOGGER.warn("AtlasVFX could not read appearance {}: {}", file, e.toString());
            }
        }
        loaded = Map.copyOf(next);
    }

    /** One appearance file's contents. */
    public static Appearance parse(Identifier id, JsonObject json) {
        Identifier geo = Identifier.of(json.get("geo").getAsString());
        Identifier texture = Identifier.of(json.get("texture").getAsString());
        Identifier animations = json.has("animations") ? Identifier.of(json.get("animations").getAsString()) : null;
        Set<BodyPart> hides = BodyPart.WORN;
        if (json.has("hides")) {
            Set<BodyPart> parts = EnumSet.noneOf(BodyPart.class);
            for (JsonElement element : json.getAsJsonArray("hides")) {
                parts.add(BodyPart.valueOf(element.getAsString().toUpperCase(Locale.ROOT)));
            }
            hides = parts;
        }
        boolean animated = json.has("posing") && json.get("posing").getAsString().equalsIgnoreCase("animated");
        ClipAnimator animator = json.has("clips") ? ClipAnimator.parse(json.getAsJsonObject("clips")) : null;
        // The clips are kept even when it follows the body, for whoever wants it animated after all.
        ModelReplacement replacement = new ModelReplacement(geo, texture, animations,
                animated && animator != null ? ModelReplacement.Posing.ANIMATED : ModelReplacement.Posing.FOLLOW_BODY,
                animator, hides, true);
        return new Appearance(id, text(json.get("name"), id.toString()),
                json.has("description") ? text(json.get("description"), "") : null, replacement);
    }

    /** A name or a translation key: shown as written when no translation has it. */
    private static Text text(@Nullable JsonElement json, String fallback) {
        String value = json == null ? fallback : json.getAsString();
        return Text.translatableWithFallback(value, value);
    }
}
