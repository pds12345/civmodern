package sh.okx.civmodern.common.map.nodes;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.map.MapFolder;
import sh.okx.civmodern.common.map.RegionKey;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Client-side store of node territory, persisted alongside the block map in {@code map.sqlite}
 * so territory you have walked stays drawn across sessions.
 *
 * <p>Reads on the render path never touch SQLite: {@link #getRegion} hands back whatever is
 * already resident and schedules a background load for anything that is not, as {@code MapCache}
 * does for map regions.
 *
 * <p>Disk reads and flushes share one single-threaded executor, and that ordering is load
 * bearing: a region created by a response queues its disk load immediately, and because the
 * executor is FIFO that load always merges in before any flush can write the region back out.
 */
public class NodeCache {

    private static final long FLUSH_INTERVAL_SECONDS = 30;

    private final MapFolder mapFile;

    private final Map<RegionKey, NodeRegion> regions = new ConcurrentHashMap<>();

    /** Regions whose stored blob has been read, or is being read. Independent of residency. */
    private final Set<RegionKey> diskLoadStarted = ConcurrentHashMap.newKeySet();

    /** Guarded by itself; read from the render thread, written when a response lands. */
    private final Int2ObjectMap<NodeInfo> nodes = new Int2ObjectOpenHashMap<>();
    private final Set<Integer> dirtyNodes = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "CivModern node cache"));

    private volatile String worldName;
    private volatile boolean closed;

    public NodeCache(MapFolder mapFile) {
        this.mapFile = mapFile;
        this.worldName = mapFile.getNodeWorldName();

        Int2ObjectMap<NodeInfo> stored = mapFile.loadNodes();
        synchronized (nodes) {
            nodes.putAll(stored);
        }

        this.executor.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_SECONDS, FLUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public static RegionKey regionOf(int chunkX, int chunkZ) {
        return new RegionKey(chunkX >> NodeRegion.SHIFT, chunkZ >> NodeRegion.SHIFT);
    }

    /**
     * Applies the API's world name. Node ids mean nothing across worlds, so a name we have not
     * seen before drops everything cached rather than risk painting one world's territory with
     * another's ids.
     */
    public void setWorldName(String worldName) {
        if (worldName == null || worldName.equals(this.worldName)) {
            return;
        }
        boolean hadData = this.worldName != null;
        this.worldName = worldName;

        regions.clear();
        diskLoadStarted.clear();
        synchronized (nodes) {
            nodes.clear();
        }
        dirtyNodes.clear();

        executor.execute(() -> {
            if (hadData) {
                AbstractCivModernMod.LOGGER.info("Node world changed to '{}', dropping cached territory", worldName);
                mapFile.clearNodeData();
            }
            mapFile.setNodeWorldName(worldName);
        });
    }

    public String getWorldName() {
        return worldName;
    }

    /**
     * @return the region if it is resident, otherwise {@code null} after queueing a load
     */
    public NodeRegion getRegion(RegionKey key) {
        queueDiskLoad(key);
        return regions.get(key);
    }

    public NodeInfo getNode(int nodeId) {
        synchronized (nodes) {
            return nodes.get(nodeId);
        }
    }

    /**
     * A copy of the palette, so the render path can resolve thousands of chunks without taking
     * the lock once per chunk. A window touches tens of nodes, so the copy is cheap.
     */
    public Int2ObjectMap<NodeInfo> snapshotNodes() {
        synchronized (nodes) {
            return new Int2ObjectOpenHashMap<>(nodes);
        }
    }

    /** Reads a region's stored blob at most once per world, whether or not it is resident. */
    private void queueDiskLoad(RegionKey key) {
        if (closed || !diskLoadStarted.add(key)) {
            return;
        }
        executor.execute(() -> {
            try {
                byte[] data = mapFile.getNodeRegionData(key);
                if (data == null) {
                    return;
                }
                NodeRegion loaded = NodeRegion.fromBytes(data);
                if (loaded == null) {
                    return;
                }
                // A response may have created the region already. Merging under compute
                // serialises against apply(), so neither source of truth clobbers the other.
                regions.compute(key, (k, existing) -> {
                    if (existing == null) {
                        return loaded;
                    }
                    existing.mergeMissingFrom(loaded);
                    return existing;
                });
            } catch (IOException e) {
                AbstractCivModernMod.LOGGER.warn("Decoding node region " + key, e);
            }
        });
    }

    /** Writes a served window into the cache. Called on the client thread. */
    public void apply(NodeProtocol.Region response) {
        long now = System.currentTimeMillis();

        synchronized (nodes) {
            for (NodeInfo entry : response.palette()) {
                nodes.put(entry.nodeId(), entry.mergeOver(nodes.get(entry.nodeId())));
                dirtyNodes.add(entry.nodeId());
            }
        }

        int size = response.size();
        int fromRegionX = response.originChunkX() >> NodeRegion.SHIFT;
        int toRegionX = (response.originChunkX() + size - 1) >> NodeRegion.SHIFT;
        int fromRegionZ = response.originChunkZ() >> NodeRegion.SHIFT;
        int toRegionZ = (response.originChunkZ() + size - 1) >> NodeRegion.SHIFT;

        for (int regionZ = fromRegionZ; regionZ <= toRegionZ; regionZ++) {
            for (int regionX = fromRegionX; regionX <= toRegionX; regionX++) {
                applyToRegion(response, new RegionKey(regionX, regionZ), now);
            }
        }
    }

    /**
     * Writes the part of a window that falls inside one region, in a single compute so it cannot
     * interleave with a blob arriving from disk.
     */
    private void applyToRegion(NodeProtocol.Region response, RegionKey key, long now) {
        int size = response.size();
        int baseChunkX = key.x() << NodeRegion.SHIFT;
        int baseChunkZ = key.z() << NodeRegion.SHIFT;

        // Window offsets that land inside this region.
        int fromDx = Math.max(0, baseChunkX - response.originChunkX());
        int toDx = Math.min(size - 1, baseChunkX + NodeRegion.CHUNKS - 1 - response.originChunkX());
        int fromDz = Math.max(0, baseChunkZ - response.originChunkZ());
        int toDz = Math.min(size - 1, baseChunkZ + NodeRegion.CHUNKS - 1 - response.originChunkZ());

        regions.compute(key, (k, existing) -> {
            NodeRegion region = existing == null ? new NodeRegion() : existing;
            for (int dz = fromDz; dz <= toDz; dz++) {
                for (int dx = fromDx; dx <= toDx; dx++) {
                    int lx = (response.originChunkX() + dx) & (NodeRegion.CHUNKS - 1);
                    int lz = (response.originChunkZ() + dz) & (NodeRegion.CHUNKS - 1);

                    int index = response.indexAt(dx, dz);
                    if (index == NodeProtocol.NO_NODE || index >= response.palette().length) {
                        region.clear(lx, lz);
                    } else {
                        region.set(lx, lz, response.palette()[index].nodeId(), response.isProtected(dx, dz));
                    }
                }
            }
            region.touch(now);
            return region;
        });

        // A region created here holds only the window; the rest of its ground may be on disk.
        // The executor is FIFO, so this load merges in before the next flush writes it back.
        queueDiskLoad(key);
    }

    /** How stale the data covering a chunk is, in millis, or {@code -1} if nothing is cached. */
    public long ageAt(int chunkX, int chunkZ) {
        NodeRegion region = regions.get(regionOf(chunkX, chunkZ));
        if (region == null || region.lastUpdated() == 0) {
            return -1;
        }
        return System.currentTimeMillis() - region.lastUpdated();
    }

    private void flush() {
        try {
            Map<RegionKey, byte[]> toSave = new HashMap<>();
            for (RegionKey key : regions.keySet()) {
                // Serialise under compute so a half-written window is never what reaches disk.
                regions.computeIfPresent(key, (k, region) -> {
                    if (region.isDirty()) {
                        region.clearDirty();
                        toSave.put(k, region.toBytes());
                    }
                    return region;
                });
            }

            List<NodeInfo> nodesToSave = new ArrayList<>();
            synchronized (nodes) {
                for (Integer id : dirtyNodes) {
                    NodeInfo node = nodes.get(id.intValue());
                    if (node != null) {
                        nodesToSave.add(node);
                    }
                }
            }
            dirtyNodes.clear();

            mapFile.saveNodeRegions(toSave);
            mapFile.saveNodes(nodesToSave);
        } catch (RuntimeException e) {
            AbstractCivModernMod.LOGGER.warn("Flushing node cache", e);
        }
    }

    /**
     * Flushes and shuts down. Must return before the owning {@link MapFolder} is closed, so it
     * waits for the final write rather than firing and forgetting.
     */
    public void save() {
        closed = true;
        executor.execute(this::flush);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                AbstractCivModernMod.LOGGER.warn("Timed out flushing node cache");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
