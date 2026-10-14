package dev.eann.atlasvfx.appearance;

import dev.eann.atlasvfx.AtlasVfxCommon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** What client and server say about appearances. Safe on a dedicated server. */
public final class AppearancePayloads {

    private AppearancePayloads() {
    }

    /** "I want to be seen as this; empty for my own skin." Client to server. */
    public record Choose(Optional<Identifier> appearance) implements CustomPayload {

        public static final CustomPayload.Id<Choose> ID =
                new CustomPayload.Id<>(Identifier.of(AtlasVfxCommon.MOD_ID, "choose_appearance"));

        public static final PacketCodec<RegistryByteBuf, Choose> CODEC = PacketCodec.of(Choose::write, Choose::read);

        public static Choose of(@Nullable Identifier appearance) {
            return new Choose(Optional.ofNullable(appearance));
        }

        private void write(RegistryByteBuf buffer) {
            buffer.writeBoolean(appearance.isPresent());
            appearance.ifPresent(buffer::writeIdentifier);
        }

        private static Choose read(RegistryByteBuf buffer) {
            return new Choose(buffer.readBoolean() ? Optional.of(buffer.readIdentifier()) : Optional.empty());
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * "These appearances are locked, by these advancements, and you have unlocked these." Server to
     * client, to the player it is about: as they join, as they earn one, and as data reloads.
     */
    public record Unlocks(Map<Identifier, Identifier> requirements, Set<Identifier> unlocked)
            implements CustomPayload {

        public static final CustomPayload.Id<Unlocks> ID =
                new CustomPayload.Id<>(Identifier.of(AtlasVfxCommon.MOD_ID, "appearance_unlocks"));

        public static final PacketCodec<RegistryByteBuf, Unlocks> CODEC = PacketCodec.of(Unlocks::write, Unlocks::read);

        public Unlocks {
            requirements = Map.copyOf(requirements);
            unlocked = Set.copyOf(unlocked);
        }

        private void write(RegistryByteBuf buffer) {
            buffer.writeVarInt(requirements.size());
            requirements.forEach((appearance, advancement) -> {
                buffer.writeIdentifier(appearance);
                buffer.writeIdentifier(advancement);
            });
            buffer.writeVarInt(unlocked.size());
            unlocked.forEach(buffer::writeIdentifier);
        }

        private static Unlocks read(RegistryByteBuf buffer) {
            int count = buffer.readVarInt();
            Map<Identifier, Identifier> requirements = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                requirements.put(buffer.readIdentifier(), buffer.readIdentifier());
            }
            int unlockedCount = buffer.readVarInt();
            Set<Identifier> unlocked = new HashSet<>();
            for (int i = 0; i < unlockedCount; i++) {
                unlocked.add(buffer.readIdentifier());
            }
            return new Unlocks(requirements, unlocked);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** "I have been asked to choose, and closed the screen." Client to server. */
    public record Prompted() implements CustomPayload {

        public static final CustomPayload.Id<Prompted> ID =
                new CustomPayload.Id<>(Identifier.of(AtlasVfxCommon.MOD_ID, "appearance_prompted"));

        public static final PacketCodec<RegistryByteBuf, Prompted> CODEC = PacketCodec.unit(new Prompted());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * "Appearances are on or off here, and these players chose these." Server to client: everyone's,
     * replacing what the client knew, as it joins; one player's, as they choose or join.
     *
     * @param full    whether it replaces everything the client knew
     * @param prompt  whether this client should ask its player to choose, as soon as it may: only ever
     *                on a full sync, to a player who has never been asked here
     * @param choices by player; empty for a player back in their own skin
     */
    public record Sync(boolean enabled, boolean full, boolean prompt, Map<UUID, Optional<Identifier>> choices)
            implements CustomPayload {

        public static final CustomPayload.Id<Sync> ID =
                new CustomPayload.Id<>(Identifier.of(AtlasVfxCommon.MOD_ID, "appearances"));

        public static final PacketCodec<RegistryByteBuf, Sync> CODEC = PacketCodec.of(Sync::write, Sync::read);

        public Sync {
            choices = Map.copyOf(choices);
        }

        private void write(RegistryByteBuf buffer) {
            buffer.writeBoolean(enabled);
            buffer.writeBoolean(full);
            buffer.writeBoolean(prompt);
            buffer.writeVarInt(choices.size());
            for (Map.Entry<UUID, Optional<Identifier>> entry : choices.entrySet()) {
                buffer.writeUuid(entry.getKey());
                buffer.writeBoolean(entry.getValue().isPresent());
                entry.getValue().ifPresent(buffer::writeIdentifier);
            }
        }

        private static Sync read(RegistryByteBuf buffer) {
            boolean enabled = buffer.readBoolean();
            boolean full = buffer.readBoolean();
            boolean prompt = buffer.readBoolean();
            int count = buffer.readVarInt();
            Map<UUID, Optional<Identifier>> choices = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                UUID player = buffer.readUuid();
                choices.put(player, buffer.readBoolean() ? Optional.of(buffer.readIdentifier()) : Optional.empty());
            }
            return new Sync(enabled, full, prompt, choices);
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
