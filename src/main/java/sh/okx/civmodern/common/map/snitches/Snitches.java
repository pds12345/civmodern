package sh.okx.civmodern.common.map.snitches;

import net.minecraft.core.BlockPos;
import sh.okx.civmodern.common.AbstractCivModernMod;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The known snitches for one world, persisted in the map database so they survive between
 * sessions. This is advisory: {@code /jalist} is the source of truth, and each complete pass
 * through it (every page, see {@link SnitchListCapture}) replaces the whole set, which is how
 * snitches the server has since removed drop out. A snitch the player hides stays hidden across
 * those passes, matched by position, until it drops out of the list.
 */
public class Snitches {

    private final Connection connection;
    private final Map<Long, Snitch> byPosition = new HashMap<>();
    /**
     * What {@link #getSnitches()} hands out, rebuilt only on a change. The map and minimap read
     * it every frame, and copying a large collection that often is needless garbage.
     */
    private List<Snitch> snapshot = List.of();
    /** The snapshot minus hidden snitches: what the map and minimap draw. */
    private List<Snitch> visibleSnapshot = List.of();
    private final Set<Long> hidden = new HashSet<>();
    private int version;

    public Snitches(Connection connection) {
        this.connection = connection;
        load();
    }

    private void load() {
        synchronized (this.connection) {
            try (Statement statement = connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT x, y, z, name, group_name, type, dormant_at, cull_at, seen_at FROM snitches");
                while (resultSet.next()) {
                    SnitchType type = SnitchType.fromName(resultSet.getString("type"));
                    if (type == null) {
                        continue;
                    }
                    Instant dormantAt = Instant.ofEpochMilli(resultSet.getLong("dormant_at"));
                    long cullAt = resultSet.getLong("cull_at");
                    // Rows from before cull_at existed were all captured active, so estimate it.
                    Instant cull = resultSet.wasNull() ? dormantAt.plus(type.dormancy()) : Instant.ofEpochMilli(cullAt);
                    Snitch snitch = new Snitch(
                        resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"),
                        resultSet.getString("name"), resultSet.getString("group_name"), type,
                        dormantAt, cull, Instant.ofEpochMilli(resultSet.getLong("seen_at")));
                    byPosition.put(key(snitch), snitch);
                }
                ResultSet hiddenRows = statement.executeQuery("SELECT x, y, z FROM snitch_hidden");
                while (hiddenRows.next()) {
                    hidden.add(BlockPos.asLong(hiddenRows.getInt("x"), hiddenRows.getInt("y"), hiddenRows.getInt("z")));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        changed();
    }

    private static long key(Snitch snitch) {
        return BlockPos.asLong(snitch.x(), snitch.y(), snitch.z());
    }

    private void changed() {
        snapshot = List.copyOf(byPosition.values());
        List<Snitch> visible = new ArrayList<>(snapshot.size());
        for (Snitch snitch : snapshot) {
            if (!hidden.contains(key(snitch))) {
                visible.add(snitch);
            }
        }
        visibleSnapshot = List.copyOf(visible);
        version++;
    }

    /**
     * Makes {@code complete} the entire set, dropping every snitch not in it, in one transaction
     * so a crash part-way leaves the previous set rather than half of each.
     */
    public void replaceAll(Collection<Snitch> complete) {
        synchronized (this.connection) {
            try {
                connection.setAutoCommit(false);
                try (Statement delete = connection.createStatement();
                     PreparedStatement insert = connection.prepareStatement(
                         "INSERT OR REPLACE INTO snitches (x, y, z, name, group_name, type, dormant_at, cull_at, seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    delete.executeUpdate("DELETE FROM snitches");
                    for (Snitch snitch : complete) {
                        insert.setInt(1, snitch.x());
                        insert.setInt(2, snitch.y());
                        insert.setInt(3, snitch.z());
                        insert.setString(4, snitch.name());
                        insert.setString(5, snitch.group());
                        insert.setString(6, snitch.type().name());
                        insert.setLong(7, snitch.dormantAt().toEpochMilli());
                        insert.setLong(8, snitch.cullAt().toEpochMilli());
                        insert.setLong(9, snitch.seenAt().toEpochMilli());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                    // A hidden snitch that has dropped out of the list forgets it was hidden.
                    delete.executeUpdate("DELETE FROM snitch_hidden WHERE NOT EXISTS (SELECT 1 FROM snitches s "
                        + "WHERE s.x = snitch_hidden.x AND s.y = snitch_hidden.y AND s.z = snitch_hidden.z)");
                    connection.commit();
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.error("Failed to save snitches", e);
                return;
            }
        }
        byPosition.clear();
        for (Snitch snitch : complete) {
            byPosition.put(key(snitch), snitch);
        }
        hidden.retainAll(byPosition.keySet());
        changed();
    }

    /**
     * Forgets one snitch locally, and that it was hidden; the next complete {@code /jalist} pass
     * brings it back, visible, if it still exists.
     */
    public void delete(Snitch snitch) {
        synchronized (this.connection) {
            try {
                for (String table : new String[]{"snitches", "snitch_hidden"}) {
                    try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE x = ? AND y = ? AND z = ?")) {
                        statement.setInt(1, snitch.x());
                        statement.setInt(2, snitch.y());
                        statement.setInt(3, snitch.z());
                        statement.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.error("Failed to delete snitch", e);
                return;
            }
        }
        byPosition.remove(key(snitch));
        hidden.remove(key(snitch));
        changed();
    }

    public boolean isHidden(Snitch snitch) {
        return hidden.contains(key(snitch));
    }

    /** Hides or shows one snitch on the map and minimap; it stays listed in the snitch manager. */
    public void setHidden(Snitch snitch, boolean hide) {
        if (hidden.contains(key(snitch)) == hide) {
            return;
        }
        synchronized (this.connection) {
            String sql = hide
                ? "INSERT OR IGNORE INTO snitch_hidden (x, y, z) VALUES (?, ?, ?)"
                : "DELETE FROM snitch_hidden WHERE x = ? AND y = ? AND z = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, snitch.x());
                statement.setInt(2, snitch.y());
                statement.setInt(3, snitch.z());
                statement.executeUpdate();
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.error("Failed to save snitch visibility", e);
                return;
            }
        }
        if (hide) {
            hidden.add(key(snitch));
        } else {
            hidden.remove(key(snitch));
        }
        changed();
    }

    /** Every known snitch, culled and hidden ones included; read-only, and safe to hold onto. */
    public List<Snitch> getSnitches() {
        return snapshot;
    }

    /** Every snitch not hidden by the player, culled ones included; read-only. */
    public List<Snitch> getVisibleSnitches() {
        return visibleSnapshot;
    }

    /** Bumped on every change, so a screen can tell when its sorted copy is stale. */
    public int version() {
        return version;
    }

    public int size() {
        return byPosition.size();
    }

    /** Forgets every snitch. @return how many were dropped */
    public int clear() {
        int count = byPosition.size();
        synchronized (this.connection) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM snitches");
                statement.executeUpdate("DELETE FROM snitch_hidden");
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.error("Failed to clear snitches", e);
            }
        }
        byPosition.clear();
        hidden.clear();
        changed();
        return count;
    }
}
