package sh.okx.civmodern.common.map;

import com.github.luben.zstd.RecyclingBufferPool;
import com.google.gson.Gson;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import sh.okx.civmodern.common.AbstractCivModernMod;
import sh.okx.civmodern.common.map.data.RegionLoader;
import sh.okx.civmodern.common.map.nodes.NodeInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class MapFolder {

    private static final int VERSION = 0;

    private final File folder;
    private final Connection connection;

    private static final Gson GSON = new Gson();
    private final File historyFile;
    private final History history;

    private final ThreadLocal<PreparedStatement> getRegionData = new ThreadLocal<>();

    public MapFolder(File folder) {
        this.folder = folder;

        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + folder.toPath().resolve("map.sqlite").toAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT NOT NULL PRIMARY KEY, value BLOB)");
                statement.execute("CREATE TABLE IF NOT EXISTS waypoints (name TEXT NOT NULL, x INT NOT NULL, y INT NOT NULL, z INT NOT NULL, icon TEXT NOT NULL, colour INT NOT NULL, UNIQUE (x, y, z))");
                statement.execute("CREATE TABLE IF NOT EXISTS blocks (name TEXT NOT NULL UNIQUE, id INTEGER NOT NULL UNIQUE)");
                statement.execute("CREATE TABLE IF NOT EXISTS biomes (name TEXT NOT NULL UNIQUE, id INTEGER NOT NULL UNIQUE)");
                statement.execute("CREATE TABLE IF NOT EXISTS regions (x INT NOT NULL, z INT NOT NULL, type TEXT NOT NULL, data BLOB NOT NULL, PRIMARY KEY (x, z, type))");

                // civnodes:v1 territory overlay. Scoped by the same folder as the block map, and
                // additionally guarded by the API's own world name held in node_meta.
                statement.execute("CREATE TABLE IF NOT EXISTS node_meta (key TEXT NOT NULL PRIMARY KEY, value TEXT)");
                statement.execute("CREATE TABLE IF NOT EXISTS nodes (id INT NOT NULL PRIMARY KEY, flags INT NOT NULL, colour INT NOT NULL, name TEXT, group_name TEXT, bastion_x INT, bastion_z INT, has_bastion INT NOT NULL, updated INT NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS node_regions (x INT NOT NULL, z INT NOT NULL, data BLOB NOT NULL, PRIMARY KEY (x, z))");

                statement.execute("INSERT INTO meta VALUES (\"version\", " + VERSION + ") ON CONFLICT DO NOTHING");

                statement.execute("CREATE INDEX IF NOT EXISTS region_pos ON regions (x, z)");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        this.historyFile = folder.toPath().resolve("history.json").toFile();
        history = readHistory(historyFile);
    }

    public History getHistory() {
        return history;
    }

    public void saveHistory() {
        synchronized (historyFile) {
            try (FileWriter writer = new FileWriter(historyFile)) {
                GSON.toJson(history, writer);
            } catch (IOException e) {
                AbstractCivModernMod.LOGGER.error(e);
            }
        }
    }

    private History readHistory(File file) {
        if (!file.exists()) {
            return defaultHistory();
        }

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, History.class);
        } catch (IOException e) {
            AbstractCivModernMod.LOGGER.warn(e);
            return defaultHistory();
        }
    }

    private static History defaultHistory() {
        History config = new History();
        config.mods = new HashMap<>();
        config.settings = new Settings();
        config.settings.enableImportPrompt = true;
        return config;
    }

    public static class History {
        public Map<String, ModData> mods;
        public Settings settings;
    }

    public static class ModData {
        public List<String> regions;
    }

    public static class Settings {
        public boolean enableImportPrompt;
    }

    public File getFolder() {
        return folder;
    }

    public Connection getConnection() {
        return connection;
    }

    public void saveBulk(Map<RegionKey, RegionLoader> dataMap, ExecutorService parallel) {
        synchronized (this.connection) {
            Map<RegionKey, Map<RegionDataType, byte[]>> compressed = new ConcurrentHashMap<>();
            CountDownLatch latch = new CountDownLatch(dataMap.size());
            for (Map.Entry<RegionKey, RegionLoader> entry : dataMap.entrySet()) {
                compressed.put(entry.getKey(), new HashMap<>());
                parallel.submit(() -> {
                    try {
                        for (RegionDataType data : entry.getValue().getLoaded()) {
                            Map<RegionDataType, byte[]> map = compressed.get(entry.getKey());

                            try {
                                ByteBuffer buf;
                                if (data == RegionDataType.MAP) {
                                    buf = ByteBuffer.allocate(512 * 512 * 4);
                                    buf.asIntBuffer().put(entry.getValue().getOrLoadMapData());
                                } else if (data == RegionDataType.Y_LEVELS) {
                                    buf = ByteBuffer.allocate(512 * 512 * 2);
                                    buf.asShortBuffer().put(entry.getValue().getOrLoadYLevels());
                                } else if (data == RegionDataType.WATER_Y_LEVELS) {
                                    buf = ByteBuffer.allocate(512 * 512 * 2);
                                    buf.asShortBuffer().put(entry.getValue().getOrLoadWaterYLevels());
                                } else if (data == RegionDataType.CHUNK_TIMESTAMPS) {
                                    buf = ByteBuffer.allocate(512 / 16 * 512 / 16 * 8);
                                    buf.asLongBuffer().put(entry.getValue().getOrLoadChunkTimestamps());
                                } else {
                                    throw new IllegalArgumentException();
                                }
                                ByteArrayOutputStream out = new ByteArrayOutputStream();
                                ZstdCompressorOutputStream zstd = new ZstdCompressorOutputStream(out);
                                zstd.write(buf.array());
                                zstd.close();
                                byte[] bytes = out.toByteArray();
                                map.put(data, bytes);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            try (PreparedStatement statement = this.connection.prepareStatement("INSERT INTO regions (x, z, type, data) VALUES (?, ?, ?, ?) ON CONFLICT DO UPDATE SET data = excluded.data")) {
                for (Map.Entry<RegionKey, RegionLoader> entry : dataMap.entrySet()) {
                    for (RegionDataType data : entry.getValue().getLoaded()) {
                        byte[] bytes = compressed.get(entry.getKey()).get(data);
                        if (bytes == null) {
                            continue;
                        }
                        statement.setInt(1, entry.getKey().x());
                        statement.setInt(2, entry.getKey().z());
                        statement.setString(3, data.getDatabaseKey());

                        statement.setBytes(4, bytes);
                        statement.addBatch();
                    }
                }

                statement.executeBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public Set<RegionKey> listRegions() {
        synchronized (this.connection) {
            try (Statement statement = this.connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT x, z FROM regions");

                Set<RegionKey> keys = new HashSet<>();
                while (resultSet.next()) {
                    keys.add(new RegionKey(resultSet.getInt("x"), resultSet.getInt("z")));
                }

                return keys;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Int2ObjectMap<String> blockIds() {
        synchronized (this.connection) {
            try (Statement statement = this.connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT name, id FROM blocks");

                Int2ObjectMap<String> blockIds = new Int2ObjectOpenHashMap<>();
                while (resultSet.next()) {
                    blockIds.put(resultSet.getInt("id"), resultSet.getString("name"));
                }

                return blockIds;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Int2ObjectMap<String> biomeIds() {
        synchronized (this.connection) {
            try (Statement statement = this.connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT name, id FROM biomes");

                Int2ObjectMap<String> biomeIds = new Int2ObjectOpenHashMap<>();
                while (resultSet.next()) {
                    biomeIds.put(resultSet.getInt("id"), resultSet.getString("name"));
                }

                return biomeIds;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void saveBlockIds(Int2ObjectMap<String> blockIds) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement("INSERT INTO blocks (name, id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {

                for (Int2ObjectMap.Entry<String> entry : blockIds.int2ObjectEntrySet()) {
                    statement.setString(1, entry.getValue());
                    statement.setInt(2, entry.getIntKey());
                    statement.addBatch();
                }

                statement.executeBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveBiomeIds(Int2ObjectMap<String> biomeIds) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement("INSERT INTO biomes (name, id) VALUES (?, ?) ON CONFLICT DO NOTHING")) {

                for (Int2ObjectMap.Entry<String> entry : biomeIds.int2ObjectEntrySet()) {
                    statement.setString(1, entry.getValue());
                    statement.setInt(2, entry.getIntKey());
                    statement.addBatch();
                }

                statement.executeBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public byte[] getRegionData(RegionKey key, RegionDataType type) {
        PreparedStatement statement = getRegionData.get();
        if (statement == null) {
            try {
                statement = this.connection.prepareStatement("SELECT data FROM regions WHERE x = ? AND z = ? AND type = ?");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            getRegionData.set(statement);
            // just don't ever close the connections for perf :)
        }
        try {
            statement.setInt(1, key.x());
            statement.setInt(2, key.z());
            statement.setString(3, type.getDatabaseKey());

            ResultSet resultSet = statement.executeQuery();

            byte[] compressed;
            if (resultSet.next()) {
                compressed = resultSet.getBytes("data");
            } else {
                return null;
            }

            byte[] data;
            ByteArrayInputStream in = new ByteArrayInputStream(compressed);
            try (ZstdCompressorInputStream zstd = new ZstdCompressorInputStream(in, RecyclingBufferPool.INSTANCE)) {
                data = zstd.readAllBytes();
            }

            return data;
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<RegionDataType, byte[]> getAllRegionData(RegionKey key) {
        try (PreparedStatement statement = this.connection.prepareStatement("SELECT type, data FROM regions WHERE x = ? AND z = ?")) {
            statement.setInt(1, key.x());
            statement.setInt(2, key.z());

            ResultSet resultSet = statement.executeQuery();

            Map<RegionDataType, byte[]> datas = new HashMap<>();
            while (resultSet.next()) {
                byte[] data;
                byte[] compressed = resultSet.getBytes("data");
                ByteArrayInputStream in = new ByteArrayInputStream(compressed);
                try (ZstdCompressorInputStream zstd = new ZstdCompressorInputStream(in, RecyclingBufferPool.INSTANCE)) {
                    data = zstd.readAllBytes();
                }
                datas.put(RegionDataType.valueOf(resultSet.getString("type").toUpperCase()), data);
            }
            return datas;
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------ civnodes territory

    /**
     * The API world name the cached node data belongs to, or {@code null} if nothing is cached.
     * Node ids are scoped to a world, so a mismatch means the cache must be dropped.
     */
    public String getNodeWorldName() {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement("SELECT value FROM node_meta WHERE key = 'world'")) {
                ResultSet resultSet = statement.executeQuery();
                return resultSet.next() ? resultSet.getString("value") : null;
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.warn("Reading node world name", e);
                return null;
            }
        }
    }

    public void setNodeWorldName(String worldName) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement("INSERT INTO node_meta (key, value) VALUES ('world', ?) ON CONFLICT DO UPDATE SET value = excluded.value")) {
                statement.setString(1, worldName);
                statement.executeUpdate();
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.warn("Writing node world name", e);
            }
        }
    }

    public void clearNodeData() {
        synchronized (this.connection) {
            try (Statement statement = this.connection.createStatement()) {
                statement.execute("DELETE FROM nodes");
                statement.execute("DELETE FROM node_regions");
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.warn("Clearing node data", e);
            }
        }
    }

    public Int2ObjectMap<NodeInfo> loadNodes() {
        synchronized (this.connection) {
            Int2ObjectMap<NodeInfo> nodes = new Int2ObjectOpenHashMap<>();
            try (Statement statement = this.connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT id, flags, colour, name, group_name, bastion_x, bastion_z, has_bastion FROM nodes");
                while (resultSet.next()) {
                    int id = resultSet.getInt("id");
                    nodes.put(id, new NodeInfo(
                        id,
                        resultSet.getInt("flags"),
                        (byte) resultSet.getInt("colour"),
                        resultSet.getString("name"),
                        resultSet.getString("group_name"),
                        resultSet.getInt("has_bastion") != 0,
                        resultSet.getInt("bastion_x"),
                        resultSet.getInt("bastion_z")
                    ));
                }
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.warn("Loading nodes", e);
            }
            return nodes;
        }
    }

    public void saveNodes(Collection<NodeInfo> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement(
                "INSERT INTO nodes (id, flags, colour, name, group_name, bastion_x, bastion_z, has_bastion, updated) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT DO UPDATE SET flags = excluded.flags, colour = excluded.colour, name = excluded.name, "
                    + "group_name = excluded.group_name, bastion_x = excluded.bastion_x, bastion_z = excluded.bastion_z, "
                    + "has_bastion = excluded.has_bastion, updated = excluded.updated")) {
                long now = System.currentTimeMillis();
                for (NodeInfo node : nodes) {
                    statement.setInt(1, node.nodeId());
                    statement.setInt(2, node.flags());
                    statement.setInt(3, node.colorIndex());
                    statement.setString(4, node.name());
                    statement.setString(5, node.groupName());
                    statement.setInt(6, node.bastionChunkX());
                    statement.setInt(7, node.bastionChunkZ());
                    statement.setInt(8, node.bastionInWindow() ? 1 : 0);
                    statement.setLong(9, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.warn("Saving nodes", e);
            }
        }
    }

    /**
     * @return the decompressed node region blob, or {@code null} if nothing is stored there
     */
    public byte[] getNodeRegionData(RegionKey key) {
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement("SELECT data FROM node_regions WHERE x = ? AND z = ?")) {
                statement.setInt(1, key.x());
                statement.setInt(2, key.z());

                ResultSet resultSet = statement.executeQuery();
                if (!resultSet.next()) {
                    return null;
                }

                ByteArrayInputStream in = new ByteArrayInputStream(resultSet.getBytes("data"));
                try (ZstdCompressorInputStream zstd = new ZstdCompressorInputStream(in, RecyclingBufferPool.INSTANCE)) {
                    return zstd.readAllBytes();
                }
            } catch (SQLException | IOException e) {
                AbstractCivModernMod.LOGGER.warn("Loading node region " + key, e);
                return null;
            }
        }
    }

    public void saveNodeRegions(Map<RegionKey, byte[]> regions) {
        if (regions.isEmpty()) {
            return;
        }
        synchronized (this.connection) {
            try (PreparedStatement statement = this.connection.prepareStatement("INSERT INTO node_regions (x, z, data) VALUES (?, ?, ?) ON CONFLICT DO UPDATE SET data = excluded.data")) {
                for (Map.Entry<RegionKey, byte[]> entry : regions.entrySet()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (ZstdCompressorOutputStream zstd = new ZstdCompressorOutputStream(out)) {
                        zstd.write(entry.getValue());
                    }

                    statement.setInt(1, entry.getKey().x());
                    statement.setInt(2, entry.getKey().z());
                    statement.setBytes(3, out.toByteArray());
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException | IOException e) {
                AbstractCivModernMod.LOGGER.warn("Saving node regions", e);
            }
        }
    }

    public void close() {
        try {
            this.connection.close();
        } catch (SQLException e) {
        }
    }
}
