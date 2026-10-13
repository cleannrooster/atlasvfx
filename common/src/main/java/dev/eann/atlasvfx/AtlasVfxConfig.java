package dev.eann.atlasvfx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AtlasVFX's own settings, in {@code config/atlasvfx.json}, read once at start-up on both sides.
 * Written with its defaults the first time; a broken file is reported and its defaults used.
 *
 * <pre>{@code
 * {
 *   "appearances": {
 *     "enabled": false,
 *     "prompt_on_first_join": true
 *   }
 * }
 * }</pre>
 *
 * <p>{@code appearances.enabled} turns on the appearance screen: players choose a model to be seen
 * as, kept with the world and shown to everyone. A server's setting is the one that counts; it tells
 * each client as it joins.</p>
 *
 * <p>{@code appearances.prompt_on_first_join} opens the appearance screen by itself the first time a
 * player joins a world or server - only while appearances are on, and only once: the world keeps who
 * has been asked. It waits for a quiet moment rather than a set time, so screens other mods show on
 * joining come first.</p>
 *
 * <p>A file missing a setting gets it added, with its default, as it is read.</p>
 */
public final class AtlasVfxConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("atlasvfx");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile boolean appearancesEnabled;
    private static volatile boolean promptOnFirstJoin = true;
    private static boolean loaded;

    private AtlasVfxConfig() {
    }

    /** Whether players may choose an appearance, on this side's own setting. */
    public static boolean appearancesEnabled() {
        return appearancesEnabled;
    }

    /** Whether a player joining for the first time is asked to choose: only while appearances are on. */
    public static boolean promptOnFirstJoin() {
        return appearancesEnabled && promptOnFirstJoin;
    }

    static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file;
        try {
            file = Platform.getConfigFolder().resolve("atlasvfx.json");
        } catch (Throwable e) {
            // No loader to ask, as in tests: defaults.
            return;
        }
        if (!Files.exists(file)) {
            write(file);
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject appearances = root.getAsJsonObject("appearances");
            appearancesEnabled = appearances != null && appearances.has("enabled")
                    && appearances.get("enabled").getAsBoolean();
            promptOnFirstJoin = appearances == null || !appearances.has("prompt_on_first_join")
                    || appearances.get("prompt_on_first_join").getAsBoolean();
            if (appearances == null || !appearances.has("enabled") || !appearances.has("prompt_on_first_join")) {
                // Written back with what it lacked, so the setting is there to find.
                write(file);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("AtlasVFX could not read {}, using its defaults: {}", file, e.toString());
        }
    }

    private static void write(Path file) {
        JsonObject appearances = new JsonObject();
        appearances.addProperty("enabled", appearancesEnabled);
        appearances.addProperty("prompt_on_first_join", promptOnFirstJoin);
        JsonObject root = new JsonObject();
        root.add("appearances", appearances);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("AtlasVFX could not write {}: {}", file, e.toString());
        }
    }
}
