package dev.eann.atlasvfx.cue;

import dev.eann.atlasvfx.AtlasVfxCommon;
import dev.eann.atlasvfx.telegraph.TelegraphArea;
import dev.eann.atlasvfx.telegraph.TelegraphTiming;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * "Warn of this area, landing at this world tick." Server to client.
 *
 * <p>The area is the very object the server hit-tests, already placed and turned; the timing is in
 * world ticks, so a late packet still lands on time.</p>
 *
 * @param style  the name of the telegraph look, as the client registered it with
 *               {@link FxCueStyles}: {@link FxCues#DANGER}, {@link FxCues#WARNING},
 *               {@link FxCues#HELPFUL} or one of a mod's own
 * @param colour drawn in this colour instead, as {@code 0xRRGGBB}; 0 for the style's own
 */
public record TelegraphPayload(TelegraphArea area, TelegraphTiming timing, Identifier style, int colour)
        implements CustomPayload {

    public static final CustomPayload.Id<TelegraphPayload> ID =
            new CustomPayload.Id<>(Identifier.of(AtlasVfxCommon.MOD_ID, "telegraph"));

    public static final PacketCodec<RegistryByteBuf, TelegraphPayload> CODEC =
            PacketCodec.of(TelegraphPayload::write, TelegraphPayload::read);

    private static final int CIRCLE = 0;
    private static final int RING = 1;
    private static final int CONE = 2;
    private static final int LINE = 3;

    private void write(RegistryByteBuf buffer) {
        FxCueArgs.writeVec(buffer, area.origin());
        switch (area) {
            case TelegraphArea.Circle circle -> {
                buffer.writeByte(CIRCLE);
                buffer.writeFloat(circle.radius());
            }
            case TelegraphArea.Ring ring -> {
                buffer.writeByte(RING);
                buffer.writeFloat(ring.inner());
                buffer.writeFloat(ring.outer());
            }
            case TelegraphArea.Cone cone -> {
                buffer.writeByte(CONE);
                buffer.writeFloat(cone.yaw());
                buffer.writeFloat(cone.range());
                buffer.writeFloat(cone.halfAngle());
            }
            case TelegraphArea.Line line -> {
                buffer.writeByte(LINE);
                buffer.writeFloat(line.yaw());
                buffer.writeFloat(line.length());
                buffer.writeFloat(line.halfWidth());
            }
        }
        buffer.writeLong(timing.startTick());
        buffer.writeLong(timing.triggerTick());
        buffer.writeVarInt(timing.fadeTicks());
        buffer.writeIdentifier(style);
        buffer.writeInt(colour);
    }

    private static TelegraphPayload read(RegistryByteBuf buffer) {
        var origin = FxCueArgs.readVec(buffer);
        TelegraphArea area = switch (buffer.readByte()) {
            case RING -> new TelegraphArea.Ring(origin, buffer.readFloat(), buffer.readFloat());
            case CONE -> new TelegraphArea.Cone(origin, buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
            case LINE -> new TelegraphArea.Line(origin, buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
            default -> new TelegraphArea.Circle(origin, buffer.readFloat());
        };
        var timing = new TelegraphTiming(buffer.readLong(), buffer.readLong(), buffer.readVarInt());
        return new TelegraphPayload(area, timing, buffer.readIdentifier(), buffer.readInt());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
