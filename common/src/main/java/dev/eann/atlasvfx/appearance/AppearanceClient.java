package dev.eann.atlasvfx.appearance;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.eann.atlasvfx.character.CharacterEffect;
import dev.eann.atlasvfx.character.CharacterEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The client's side of appearances: what the server says each player chose, drawn on them; and
 * the key that opens the appearance screen.
 *
 * <p>Only the server's word counts for whether appearances are on: it says so as the client joins.
 * Client thread only.</p>
 */
public final class AppearanceClient {

    public static final KeyBinding OPEN = new KeyBinding("key.atlasvfx.appearance", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O, "key.categories.atlasvfx");

    /**
     * Ticks in a row the game must be quiet - in a world, alive, no screen open - before the first-join
     * prompt opens: long enough for a screen another mod shows on joining to have opened first, and to
     * have the prompt wait behind it.
     */
    static final int QUIET_TICKS = 40;

    private static final Map<UUID, Identifier> CHOICES = new HashMap<>();
    private static boolean enabled;
    private static boolean promptPending;
    private static int quiet;

    private AppearanceClient() {
    }

    public static void init() {
        KeyMappingRegistry.register(OPEN);
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, AppearancePayloads.Sync.ID,
                AppearancePayloads.Sync.CODEC, (payload, context) -> context.queue(() -> sync(payload)));
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register(player -> {
            CHOICES.clear();
            enabled = false;
            promptPending = false;
            quiet = 0;
        });
        ClientTickEvent.CLIENT_POST.register(AppearanceClient::tick);
        // Drawn on every player who chose one, while appearances are on.
        CharacterEffects.register(entity -> entity instanceof PlayerEntity player ? effectOf(player) : null);
    }

    /** Whether the server lets players choose an appearance. */
    public static boolean enabled() {
        return enabled;
    }

    /** What {@code player} chose; null for their own skin. */
    public static @Nullable Identifier choiceOf(PlayerEntity player) {
        return CHOICES.get(player.getUuid());
    }

    /** Chooses for the player playing: the server keeps it and shows everyone. */
    public static void choose(@Nullable Identifier appearance) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !enabled) {
            return;
        }
        if (NetworkManager.canServerReceive(AppearancePayloads.Choose.ID)) {
            NetworkManager.sendToServer(AppearancePayloads.Choose.of(appearance));
        }
    }

    private static @Nullable CharacterEffect effectOf(PlayerEntity player) {
        if (!enabled || CHOICES.isEmpty()) {
            return null;
        }
        Appearance appearance = Appearances.get(CHOICES.get(player.getUuid()));
        return appearance == null ? null : appearance.effect();
    }

    /** Tells the server the first-join prompt has been seen, so it is not shown again. */
    static void prompted() {
        if (NetworkManager.canServerReceive(AppearancePayloads.Prompted.ID)) {
            NetworkManager.sendToServer(new AppearancePayloads.Prompted());
        }
    }

    private static void sync(AppearancePayloads.Sync payload) {
        if (payload.full()) {
            CHOICES.clear();
            promptPending = payload.prompt() && payload.enabled();
            quiet = 0;
        }
        enabled = payload.enabled();
        for (Map.Entry<UUID, Optional<Identifier>> entry : payload.choices().entrySet()) {
            if (entry.getValue().isPresent()) {
                CHOICES.put(entry.getKey(), entry.getValue().get());
            } else {
                CHOICES.remove(entry.getKey());
            }
        }
    }

    private static void tick(MinecraftClient client) {
        if (promptPending) {
            waitToPrompt(client);
        }
        while (OPEN.wasPressed()) {
            if (client.player == null || client.currentScreen != null) {
                continue;
            }
            if (!enabled) {
                client.player.sendMessage(Text.translatable("atlasvfx.appearance.disabled")
                        .formatted(Formatting.GRAY), true);
                continue;
            }
            client.setScreen(new AppearanceScreen(null, false));
        }
    }

    /**
     * Opens the first-join prompt once the game has been quiet long enough: every tick another mod's
     * screen, a loading screen, or death keeps it waiting starts the count again.
     */
    private static void waitToPrompt(MinecraftClient client) {
        if (!enabled) {
            promptPending = false;
            return;
        }
        boolean quietNow = client.player != null && client.world != null && client.currentScreen == null
                && client.player.isAlive() && client.getOverlay() == null;
        quiet = quietNow ? quiet + 1 : 0;
        if (quiet >= QUIET_TICKS) {
            promptPending = false;
            quiet = 0;
            client.setScreen(new AppearanceScreen(null, true));
        }
    }
}
