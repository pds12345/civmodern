package sh.okx.civmodern.common.map.nodes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Encoding and decoding of the civnodes:v1 wire format.
 *
 * <p>Every frame opens with a packet-id byte. Integers are big-endian. Strings are Java's
 * {@link DataOutputStream#writeUTF}, which {@link DataInputStream#readUTF} reads directly.
 * Deliberately free of any Minecraft types so it stays independent of mappings.
 *
 * <p>Forward compatibility, per the API contract: unknown packet ids decode to {@code null}
 * rather than throwing, unknown flag bits are ignored rather than asserted zero, and trailing
 * bytes past the fields we understand are left unread. That is exactly how a minor-version
 * addition reaches us without breaking anything.
 */
public final class NodeProtocol {

    public static final int C2S_HELLO = 0x01;
    public static final int C2S_QUERY = 0x02;
    public static final int S2C_HELLO = 0x81;
    public static final int S2C_REGION = 0x82;
    public static final int S2C_ERROR = 0x83;

    public static final int FLAG_CLAIMED = 0x01;
    public static final int FLAG_HAS_ACCESS = 0x02;
    public static final int FLAG_HAS_NAME = 0x04;
    public static final int FLAG_BASTION_IN_WINDOW = 0x08;
    public static final int FLAG_HAS_GROUP_NAME = 0x10;

    /** Palette index meaning no node owns the chunk: ocean, or past the generated border. */
    public static final int NO_NODE = 0xFF;

    /** The minor version we were built against. Never rejected by the server today. */
    public static final byte CLIENT_MINOR = 1;

    private NodeProtocol() {
    }

    public enum ErrorReason {
        DISABLED,
        NO_NODES_HERE,
        MALFORMED,
        UNSUPPORTED_VERSION,
        UNKNOWN;

        static ErrorReason of(int code) {
            return switch (code) {
                case 1 -> DISABLED;
                case 2 -> NO_NODES_HERE;
                case 3 -> MALFORMED;
                case 4 -> UNSUPPORTED_VERSION;
                default -> UNKNOWN;
            };
        }
    }

    /** Marker for every decoded server frame. */
    public sealed interface Frame permits Hello, Region, ErrorFrame {
    }

    public record Hello(
        byte serverMinor,
        int maxSize,
        int minQueryIntervalMs,
        boolean worldHasNodes,
        String worldName
    ) implements Frame {
    }

    /**
     * A served window. Render from {@code originChunkX}/{@code originChunkZ}/{@code size} and
     * never from what was requested: the server discards the centre we send and answers from
     * our real server-side position, so the window may not be the one we asked for.
     */
    public record Region(
        int originChunkX,
        int originChunkZ,
        int size,
        NodeInfo[] palette,
        byte[] grid,
        byte[] protectedBits,
        int frameLength
    ) implements Frame {

        /** Palette index at a window-local offset, or {@link NodeProtocol#NO_NODE}. */
        public int indexAt(int dx, int dz) {
            return grid[dz * size + dx] & 0xFF;
        }

        public boolean isProtected(int dx, int dz) {
            int i = dz * size + dx;
            return (protectedBits[i >> 3] & (1 << (i & 7))) != 0;
        }
    }

    public record ErrorFrame(ErrorReason reason) implements Frame {
    }

    // ---------------------------------------------------------------- outbound

    public static byte[] encodeHello(String modId) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(C2S_HELLO);
            out.writeByte(CLIENT_MINOR);
            out.writeUTF(modId.length() > 64 ? modId.substring(0, 64) : modId);
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream does not throw", e);
        }
        return bytes.toByteArray();
    }

    public static byte[] encodeQuery(int centerChunkX, int centerChunkZ, int size) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(C2S_QUERY);
            // Advisory only: the server re-centres on our real position and echoes the origin.
            out.writeInt(centerChunkX);
            out.writeInt(centerChunkZ);
            out.writeByte(size);
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream does not throw", e);
        }
        return bytes.toByteArray();
    }

    // ---------------------------------------------------------------- inbound

    /**
     * Decodes one server frame.
     *
     * @return the frame, or {@code null} for a packet id we do not recognise
     * @throws IOException if a frame we do recognise does not decode
     */
    public static Frame decode(byte[] payload) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        int id = in.readUnsignedByte();
        return switch (id) {
            case S2C_HELLO -> decodeHello(in);
            case S2C_REGION -> decodeRegion(in, payload.length);
            case S2C_ERROR -> new ErrorFrame(ErrorReason.of(in.readUnsignedByte()));
            default -> null;
        };
    }

    private static Hello decodeHello(DataInputStream in) throws IOException {
        byte serverMinor = in.readByte();
        int maxSize = in.readUnsignedByte();
        int minQueryIntervalMs = in.readInt();
        boolean worldHasNodes = in.readBoolean();
        String worldName = in.readUTF();
        return new Hello(serverMinor, maxSize, minQueryIntervalMs, worldHasNodes, worldName);
    }

    private static Region decodeRegion(DataInputStream in, int frameLength) throws IOException {
        int originX = in.readInt();
        int originZ = in.readInt();
        int size = in.readUnsignedByte();
        int paletteLength = in.readUnsignedByte();

        if (size <= 0) {
            throw new IOException("Region size " + size + " is not renderable");
        }

        NodeInfo[] palette = new NodeInfo[paletteLength];
        for (int i = 0; i < paletteLength; i++) {
            int nodeId = in.readInt();
            int flags = in.readUnsignedByte();
            byte colorIndex = in.readByte(); // signed: -1 means unassigned

            String nodeName = (flags & FLAG_HAS_NAME) != 0 ? in.readUTF() : null;
            String groupName = (flags & FLAG_HAS_GROUP_NAME) != 0 ? in.readUTF() : null;

            boolean bastionHere = (flags & FLAG_BASTION_IN_WINDOW) != 0;
            int bx = 0;
            int bz = 0;
            if (bastionHere) {
                bx = in.readInt();
                bz = in.readInt();
            }

            palette[i] = new NodeInfo(nodeId, flags, colorIndex, nodeName, groupName, bastionHere, bx, bz);
        }

        byte[] grid = in.readNBytes(size * size);
        byte[] protectedBits = in.readNBytes((size * size + 7) / 8);

        if (grid.length != size * size || protectedBits.length != (size * size + 7) / 8) {
            throw new IOException("Truncated region body for size " + size);
        }

        return new Region(originX, originZ, size, palette, grid, protectedBits, frameLength);
    }
}
