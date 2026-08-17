package sh.okx.civmodern.common.map.nodes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Node ownership for one 32x32 block of chunks.
 *
 * <p>Deliberately keyed the same way the block map keys its regions
 * ({@code chunk >> 5}, i.e. 512 blocks a side), so a node region and a map region cover exactly
 * the same ground and the two caches agree on coordinates.
 *
 * <p>Three states per chunk, not two: <em>unknown</em> (never served to us), <em>known and
 * unowned</em> (ocean, or past the generated border), and <em>owned</em>. Keeping the first two
 * apart is what lets a region loaded from disk merge into one already in memory without either
 * erasing the other.
 *
 * <p>Not thread safe. {@link NodeCache} confines every mutation to a single map bin lock.
 */
public final class NodeRegion {

    /** Chunks along one edge of a region. */
    public static final int CHUNKS = 32;
    public static final int SHIFT = 5;

    private static final int CELLS = CHUNKS * CHUNKS;
    private static final int BITSET_BYTES = CELLS / 8;
    private static final byte VERSION = 2;

    private final int[] nodeIds = new int[CELLS];
    private final byte[] known = new byte[BITSET_BYTES];
    private final byte[] owned = new byte[BITSET_BYTES];
    private final byte[] protectedBits = new byte[BITSET_BYTES];

    private long lastUpdated;
    private boolean dirty;

    private static int index(int localX, int localZ) {
        return localZ * CHUNKS + localX;
    }

    private static boolean bit(byte[] bits, int i) {
        return (bits[i >> 3] & (1 << (i & 7))) != 0;
    }

    private static void bit(byte[] bits, int i, boolean value) {
        if (value) {
            bits[i >> 3] |= (byte) (1 << (i & 7));
        } else {
            bits[i >> 3] &= (byte) ~(1 << (i & 7));
        }
    }

    /** Whether the server has ever told us about this chunk. */
    public boolean isKnown(int localX, int localZ) {
        return bit(known, index(localX, localZ));
    }

    /** Whether a node owns this chunk. False for both unknown and known-unowned chunks. */
    public boolean hasNode(int localX, int localZ) {
        return bit(owned, index(localX, localZ));
    }

    public int nodeId(int localX, int localZ) {
        return nodeIds[index(localX, localZ)];
    }

    public boolean isProtected(int localX, int localZ) {
        return bit(protectedBits, index(localX, localZ));
    }

    public void set(int localX, int localZ, int nodeId, boolean isProtected) {
        int i = index(localX, localZ);
        if (bit(known, i) && bit(owned, i) && nodeIds[i] == nodeId && bit(protectedBits, i) == isProtected) {
            return;
        }
        nodeIds[i] = nodeId;
        bit(known, i, true);
        bit(owned, i, true);
        bit(protectedBits, i, isProtected);
        dirty = true;
    }

    /** Records that no node owns this chunk, which is itself worth remembering. */
    public void clear(int localX, int localZ) {
        int i = index(localX, localZ);
        if (bit(known, i) && !bit(owned, i)) {
            return;
        }
        nodeIds[i] = 0;
        bit(known, i, true);
        bit(owned, i, false);
        bit(protectedBits, i, false);
        dirty = true;
    }

    /**
     * Fills in chunks this region has never been told about from {@code other}, leaving everything
     * it does know alone. Used when a blob loaded from disk arrives after a response has already
     * created the in-memory region, so neither source of truth clobbers the other.
     */
    public void mergeMissingFrom(NodeRegion other) {
        for (int i = 0; i < CELLS; i++) {
            if (bit(known, i) || !bit(other.known, i)) {
                continue;
            }
            nodeIds[i] = other.nodeIds[i];
            bit(known, i, true);
            bit(owned, i, bit(other.owned, i));
            bit(protectedBits, i, bit(other.protectedBits, i));
        }
        this.lastUpdated = Math.max(this.lastUpdated, other.lastUpdated);
    }

    public void touch(long millis) {
        this.lastUpdated = Math.max(this.lastUpdated, millis);
    }

    public long lastUpdated() {
        return lastUpdated;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeLong(lastUpdated);
            out.write(known);
            out.write(owned);
            out.write(protectedBits);
            for (int id : nodeIds) {
                out.writeInt(id);
            }
        } catch (IOException e) {
            throw new AssertionError("ByteArrayOutputStream does not throw", e);
        }
        return bytes.toByteArray();
    }

    /**
     * @return the decoded region, or {@code null} if the blob is from a format we do not read,
     *         in which case the caller should treat the ground as simply not yet cached
     */
    public static NodeRegion fromBytes(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        if (in.readByte() != VERSION) {
            return null;
        }
        NodeRegion region = new NodeRegion();
        region.lastUpdated = in.readLong();
        in.readFully(region.known);
        in.readFully(region.owned);
        in.readFully(region.protectedBits);
        for (int i = 0; i < CELLS; i++) {
            region.nodeIds[i] = in.readInt();
        }
        return region;
    }
}
