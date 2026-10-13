package dev.eann.atlasvfx.cue;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * The arguments every cue gets, sent from the server. Each cue reads the ones it needs: a nova uses
 * {@code at} and {@code size}; a beam, arc or slash also uses {@code to} - an end point, or a
 * direction for a cone or a slash; {@code angle} is a cone's or a slash's half-angle;
 * {@code entityId} lets a cue follow an entity, -1 for none.
 *
 * <p>Safe on a server: nothing here touches the client.</p>
 *
 * @param style the name of the look it is drawn in, as the client registered it with
 *              {@link FxCueStyles}; an unknown one is drawn in the default look
 * @param tier  0 to 2, how much it throws off
 * @param seed  so every client draws the same random detail
 */
public record FxCueArgs(Identifier style, Vec3d at, Vec3d to, float size, float angle, int tier, long seed,
                        int entityId) {

    /** A cue at a point. */
    public static FxCueArgs at(Identifier style, Vec3d at, float size, int tier, long seed) {
        return new FxCueArgs(style, at, at, size, 0.0f, tier, seed, -1);
    }

    /** A cue from one point to another: a beam, an arc. */
    public static FxCueArgs between(Identifier style, Vec3d from, Vec3d to, float size, int tier, long seed) {
        return new FxCueArgs(style, from, to, size, 0.0f, tier, seed, -1);
    }

    /** A cue thrown one way from a point, within {@code halfAngle} degrees of {@code axis}. */
    public static FxCueArgs cone(Identifier style, Vec3d at, Vec3d axis, float halfAngle, float reach, int tier,
                                 long seed) {
        return new FxCueArgs(style, at, axis, reach, halfAngle, tier, seed, -1);
    }

    /** The same cue, following an entity. */
    public FxCueArgs following(int newEntityId) {
        return new FxCueArgs(style, at, to, size, angle, tier, seed, newEntityId);
    }

    public void write(RegistryByteBuf buffer) {
        buffer.writeIdentifier(style);
        writeVec(buffer, at);
        writeVec(buffer, to);
        buffer.writeFloat(size);
        buffer.writeFloat(angle);
        buffer.writeByte(tier);
        buffer.writeLong(seed);
        buffer.writeVarInt(entityId + 1);
    }

    public static FxCueArgs read(RegistryByteBuf buffer) {
        return new FxCueArgs(buffer.readIdentifier(), readVec(buffer), readVec(buffer), buffer.readFloat(),
                buffer.readFloat(), buffer.readByte(), buffer.readLong(), buffer.readVarInt() - 1);
    }

    static void writeVec(RegistryByteBuf buffer, Vec3d vec) {
        buffer.writeDouble(vec.x);
        buffer.writeDouble(vec.y);
        buffer.writeDouble(vec.z);
    }

    static Vec3d readVec(RegistryByteBuf buffer) {
        return new Vec3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
