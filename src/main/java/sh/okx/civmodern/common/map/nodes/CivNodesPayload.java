package sh.okx.civmodern.common.map.nodes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Carries a raw civnodes:v1 frame through the custom payload transport untouched.
 * All of the structure lives in {@link NodeProtocol}; the codec is deliberately a plain
 * byte array so this compiles against any mapping of the networking classes.
 *
 * <p>The major protocol version is the channel name, so an incompatible future format would
 * arrive as {@code civnodes:v2} alongside this one.
 */
public record CivNodesPayload(byte[] data) implements CustomPacketPayload {

    public static final Identifier CHANNEL = Identifier.fromNamespaceAndPath("civnodes", "v1");

    public static final CustomPacketPayload.Type<CivNodesPayload> TYPE = new CustomPacketPayload.Type<>(CHANNEL);

    public static final StreamCodec<RegistryFriendlyByteBuf, CivNodesPayload> CODEC = CustomPacketPayload.codec(
        (payload, buf) -> buf.writeBytes(payload.data()),
        buf -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new CivNodesPayload(data);
        });

    @Override
    public CustomPacketPayload.Type<CivNodesPayload> type() {
        return TYPE;
    }
}
