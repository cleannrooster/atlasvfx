package dev.eann.atlasvfx.appearance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which appearances are earned, and by what: an advancement a player has to have done before they
 * may choose it. Server side, and safe on a dedicated server.
 *
 * <p>A data pack locks one as {@code data/<namespace>/atlasvfx_unlocks/<name>.json}, for the
 * appearance {@code <namespace>:<name>}:</p>
 *
 * <pre>{@code
 * { "advancement": "minecraft:story/mine_diamond" }
 * }</pre>
 *
 * <p>An appearance nothing locks is open to everyone. In data, not with the appearance's look in a
 * resource pack: the server decides who may wear what, and a dedicated server reads no resource
 * packs. An advancement that does not exist - its mod not installed - locks nothing, rather than
 * locking it for good; it is reported once.</p>
 */
public final class AppearanceUnlocks {

    private static final Logger LOGGER = LoggerFactory.getLogger("atlasvfx");
    static final String FOLDER = "atlasvfx_unlocks";

    private static volatile Map<Identifier, Identifier> requirements = Map.of();
    private static final Set<Identifier> REPORTED = new HashSet<>();

    private AppearanceUnlocks() {
    }

    /** Each locked appearance, with the advancement that unlocks it. */
    public static Map<Identifier, Identifier> requirements() {
        return requirements;
    }

    /** What unlocks {@code appearance}; null if nothing locks it. */
    public static @Nullable Identifier requirement(Identifier appearance) {
        return requirements.get(appearance);
    }

    /** Whether {@code player} may wear {@code appearance}. */
    public static boolean unlocked(ServerPlayerEntity player, Identifier appearance) {
        Identifier advancement = requirements.get(appearance);
        if (advancement == null) {
            return true;
        }
        MinecraftServer server = player.getServer();
        AdvancementEntry entry = server == null ? null : server.getAdvancementLoader().get(advancement);
        if (entry == null) {
            synchronized (REPORTED) {
                if (REPORTED.add(advancement)) {
                    LOGGER.warn("AtlasVFX: appearance {} is unlocked by {}, which does not exist; it is left unlocked",
                            appearance, advancement);
                }
            }
            return true;
        }
        return player.getAdvancementTracker().getProgress(entry).isDone();
    }

    /** Every locked appearance {@code player} has unlocked. */
    public static Set<Identifier> unlockedBy(ServerPlayerEntity player) {
        Set<Identifier> unlocked = new HashSet<>();
        for (Identifier appearance : requirements.keySet()) {
            if (unlocked(player, appearance)) {
                unlocked.add(appearance);
            }
        }
        return unlocked;
    }

    /** Whether earning {@code advancement} unlocks anything. */
    static boolean unlocksAnything(Identifier advancement) {
        return requirements.containsValue(advancement);
    }

    /** Reads every unlock data packs give. */
    public static void reload(ResourceManager manager) {
        Map<Identifier, Identifier> next = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : manager.findResources(FOLDER,
                path -> path.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            String path = file.getPath();
            Identifier appearance = Identifier.of(file.getNamespace(),
                    path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
            try (Reader reader = entry.getValue().getReader()) {
                next.put(appearance, parse(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                LOGGER.warn("AtlasVFX could not read appearance unlock {}: {}", file, e.toString());
            }
        }
        requirements = Map.copyOf(next);
        synchronized (REPORTED) {
            REPORTED.clear();
        }
    }

    /** One unlock file's advancement. */
    static Identifier parse(JsonObject json) {
        return Identifier.of(json.get("advancement").getAsString());
    }
}
