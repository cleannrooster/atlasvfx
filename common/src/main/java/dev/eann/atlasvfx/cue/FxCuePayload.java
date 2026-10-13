package dev.eann.atlasvfx.cue;

import dev.eann.atlasvfx.AtlasVfxCommon;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** "Play cue {@code cue} with these arguments." Server to client. */
public record FxCuePayload(Identifier cue, FxCueArgs args) implements CustomPayload {

    public static final CustomPayload.Id<FxCuePayload> ID =
            new CustomPayload.Id<>(Identifier.of(AtlasVfxCommon.MOD_ID, "cue"));

    public static final PacketCodec<RegistryByteBuf, FxCuePayload> CODEC =
            PacketCodec.of(FxCuePayload::write, FxCuePayload::read);

    private void write(RegistryByteBuf buffer) {
        buffer.writeIdentifier(cue);
        args.write(buffer);
    }

    private static FxCuePayload read(RegistryByteBuf buffer) {
        return new FxCuePayload(buffer.readIdentifier(), FxCueArgs.read(buffer));
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
