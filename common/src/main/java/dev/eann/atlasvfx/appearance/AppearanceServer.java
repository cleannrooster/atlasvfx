package dev.eann.atlasvfx.appearance;

import dev.architectury.event.events.common.LifecycleEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.registry.ReloadListenerRegistry;
import dev.architectury.utils.Env;
import dev.eann.atlasvfx.AtlasVfxConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps which appearance each player chose, with the world, and tells every client. Safe on a
 * dedicated server: it names nothing that draws, and what an appearance looks like is the client's
 * business - the server keeps only its id.
 *
 * <p>While the setting is off, choices are refused and nobody is shown one; choices made before are
 * kept, for when it is turned on again.</p>
 *
 * <p>An appearance a data pack locks behind an advancement - see {@link AppearanceUnlocks} - may only
 * be chosen by a player who has done it: the server refuses it otherwise, and takes it off a player
 * who has lost the advancement since.</p>
 *
 * <p>A player joining for the first time is told to ask them to choose, if the setting says to; once
 * they close the screen or choose, they are never asked again here.</p>
 */
public final class AppearanceServer {

    private static final String SAVE = "atlasvfx_appearances";
    private static final PersistentState.Type<Saved> TYPE = new PersistentState.Type<>(Saved::new, Saved::read, null);

    /** Each player's choice, by UUID, and who has been asked to choose, kept in the overworld's data. */
    static final class Saved extends PersistentState {
        final Map<UUID, Identifier> choices = new HashMap<>();
        final Set<UUID> prompted = new HashSet<>();

        static Saved read(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            Saved saved = new Saved();
            NbtCompound choices = nbt.getCompound("choices");
            for (String key : choices.getKeys()) {
                try {
                    Identifier id = Identifier.tryParse(choices.getString(key));
                    if (id != null) {
                        saved.choices.put(UUID.fromString(key), id);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Not a UUID: skip it.
                }
            }
            NbtList prompted = nbt.getList("prompted", NbtElement.STRING_TYPE);
            for (int i = 0; i < prompted.size(); i++) {
                try {
                    saved.prompted.add(UUID.fromString(prompted.getString(i)));
                } catch (IllegalArgumentException ignored) {
                    // Not a UUID: skip it.
                }
            }
            return saved;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
            NbtCompound out = new NbtCompound();
            choices.forEach((player, id) -> out.putString(player.toString(), id.toString()));
            nbt.put("choices", out);
            NbtList prompted = new NbtList();
            this.prompted.forEach(player -> prompted.add(NbtString.of(player.toString())));
            nbt.put("prompted", prompted);
            return nbt;
        }
    }

    private AppearanceServer() {
    }

    /** Both sides, at mod construction. */
    public static void init() {
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(AppearancePayloads.Sync.ID, AppearancePayloads.Sync.CODEC);
            NetworkManager.registerS2CPayloadType(AppearancePayloads.Unlocks.ID, AppearancePayloads.Unlocks.CODEC);
        }
        LifecycleEvent.SERVER_STARTED.register(started -> server = started);
        LifecycleEvent.SERVER_STOPPED.register(stopped -> server = null);
        // What locks what is data, read on start and on /reload; a reload tells everyone again.
        ReloadListenerRegistry.register(ResourceType.SERVER_DATA, (SynchronousResourceReloader) manager -> {
            AppearanceUnlocks.reload(manager);
            MinecraftServer running = server;
            if (running != null) {
                running.execute(() -> running.getPlayerManager().getPlayerList().forEach(player -> {
                    sendUnlocks(player);
                    dropIfLocked(player);
                }));
            }
        });
        // Earning an advancement may unlock something: say so at once.
        PlayerEvent.PLAYER_ADVANCEMENT.register((player, advancement) -> {
            if (AppearanceUnlocks.unlocksAnything(advancement.id())) {
                sendUnlocks(player);
            }
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, AppearancePayloads.Choose.ID,
                AppearancePayloads.Choose.CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayerEntity player) {
                        choose(player, payload.appearance().orElse(null));
                    }
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, AppearancePayloads.Prompted.ID,
                AppearancePayloads.Prompted.CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayerEntity player && player.getServer() != null) {
                        Saved saved = saved(player.getServer());
                        if (saved.prompted.add(player.getUuid())) {
                            saved.markDirty();
                        }
                    }
                }));
        PlayerEvent.PLAYER_JOIN.register(AppearanceServer::joined);
    }

    private static volatile @Nullable MinecraftServer server;

    private static Saved saved(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, SAVE);
    }

    /** Everyone's choices to the player joining, and theirs to everyone else. */
    private static void joined(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        boolean enabled = AtlasVfxConfig.appearancesEnabled();
        if (enabled) {
            sendUnlocks(player);
            // Earned once and since taken away - by a command, a data pack change - it comes off.
            dropIfLocked(player);
        }
        Map<UUID, Optional<Identifier>> all = new LinkedHashMap<>();
        if (enabled) {
            saved(server).choices.forEach((uuid, id) -> all.put(uuid, Optional.of(id)));
        }
        Saved saved = saved(server);
        boolean prompt = AtlasVfxConfig.promptOnFirstJoin() && !saved.prompted.contains(player.getUuid())
                && !saved.choices.containsKey(player.getUuid());
        send(player, new AppearancePayloads.Sync(enabled, true, prompt, all));
        Identifier own = enabled ? saved(server).choices.get(player.getUuid()) : null;
        if (own != null) {
            broadcast(server, player, Optional.of(own));
        }
    }

    /** A player's choice: kept, and shown to everyone - or refused, while the setting is off. */
    static void choose(ServerPlayerEntity player, @Nullable Identifier appearance) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (!AtlasVfxConfig.appearancesEnabled()) {
            // Puts the client straight: nothing is shown here.
            send(player, new AppearancePayloads.Sync(false, true, false, Map.of()));
            return;
        }
        if (appearance != null && !AppearanceUnlocks.unlocked(player, appearance)) {
            // Not earned: refused, and the client told again what it has unlocked.
            sendUnlocks(player);
            return;
        }
        Saved saved = saved(server);
        if (appearance == null) {
            saved.choices.remove(player.getUuid());
        } else {
            saved.choices.put(player.getUuid(), appearance);
        }
        // Having chosen, there is nothing left to ask.
        saved.prompted.add(player.getUuid());
        saved.markDirty();
        broadcast(server, player, Optional.ofNullable(appearance));
    }

    /** Tells {@code player} what is locked and what they have unlocked. */
    static void sendUnlocks(ServerPlayerEntity player) {
        if (!AtlasVfxConfig.appearancesEnabled()
                || !NetworkManager.canPlayerReceive(player, AppearancePayloads.Unlocks.ID)) {
            return;
        }
        NetworkManager.sendToPlayer(player, new AppearancePayloads.Unlocks(AppearanceUnlocks.requirements(),
                AppearanceUnlocks.unlockedBy(player)));
    }

    /** Takes off what {@code player} wears if it is locked to them now, and tells everyone. */
    private static void dropIfLocked(ServerPlayerEntity player) {
        MinecraftServer running = player.getServer();
        if (running == null || !AtlasVfxConfig.appearancesEnabled()) {
            return;
        }
        Saved saved = saved(running);
        Identifier wearing = saved.choices.get(player.getUuid());
        if (wearing != null && !AppearanceUnlocks.unlocked(player, wearing)) {
            saved.choices.remove(player.getUuid());
            saved.markDirty();
            broadcast(running, player, Optional.empty());
        }
    }

    private static void broadcast(MinecraftServer server, ServerPlayerEntity chooser, Optional<Identifier> appearance) {
        AppearancePayloads.Sync sync = new AppearancePayloads.Sync(true, false, false,
                Map.of(chooser.getUuid(), appearance));
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            send(player, sync);
        }
    }

    private static void send(ServerPlayerEntity player, AppearancePayloads.Sync sync) {
        if (NetworkManager.canPlayerReceive(player, AppearancePayloads.Sync.ID)) {
            NetworkManager.sendToPlayer(player, sync);
        }
    }
}
