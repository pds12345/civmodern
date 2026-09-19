package sh.okx.civmodern.common.map.snitches;

import net.minecraft.core.BlockPos;
import sh.okx.civmodern.common.AbstractCivModernMod;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The known snitches for one world, persisted in the map database so they survive between
 * sessions. The list only refreshes when the player pages through {@code /jalist}, and a snitch
 * the server has since removed stays here until {@link #clear()}: the GUI gives no way to tell
 * "removed" from "on a page that wasn't visited".
 */
public class Snitches {

    private final Connection connection;
    private final Map<Long, Snitch> byPosition = new HashMap<>();

    public Snitches(Connection connection) {
        this.connection = connection;
        load();
    }

    private void load() {
        synchronized (this.connection) {
            try (Statement statement = connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT x, y, z, name, group_name, type, dormant_at, seen_at FROM snitches");
                while (resultSet.next()) {
                    SnitchType type = SnitchType.fromName(resultSet.getString("type"));
                    if (type == null) {
                        continue;
                    }
                    Snitch snitch = new Snitch(
                        resultSet.getInt("x"), resultSet.getInt("y"), resultSet.getInt("z"),
                        resultSet.getString("name"), resultSet.getString("group_name"), type,
                        Instant.ofEpochMilli(resultSet.getLong("dormant_at")),
                        Instant.ofEpochMilli(resultSet.getLong("seen_at")));
                    byPosition.put(key(snitch), snitch);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static long key(Snitch snitch) {
        return BlockPos.asLong(snitch.x(), snitch.y(), snitch.z());
    }

    /** Adds or refreshes each snitch, keyed by position, and writes them through. */
    public void record(Collection<Snitch> batch) {
        if (batch.isEmpty()) {
            return;
        }
        synchronized (this.connection) {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO snitches (x, y, z, name, group_name, type, dormant_at, seen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT DO UPDATE SET name = ?, group_name = ?, type = ?, dormant_at = ?, seen_at = ?")) {
                for (Snitch snitch : batch) {
                    byPosition.put(key(snitch), snitch);
                    statement.setInt(1, snitch.x());
                    statement.setInt(2, snitch.y());
                    statement.setInt(3, snitch.z());
                    statement.setString(4, snitch.name());
                    statement.setString(5, snitch.group());
                    statement.setString(6, snitch.type().name());
                    statement.setLong(7, snitch.dormantAt().toEpochMilli());
                    statement.setLong(8, snitch.seenAt().toEpochMilli());
                    statement.setString(9, snitch.name());
                    statement.setString(10, snitch.group());
                    statement.setString(11, snitch.type().name());
                    statement.setLong(12, snitch.dormantAt().toEpochMilli());
                    statement.setLong(13, snitch.seenAt().toEpochMilli());
                    statement.addBatch();
                }
                statement.executeBatch();
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.error("Failed to save snitches", e);
            }
        }
    }

    public List<Snitch> getSnitches() {
        return new ArrayList<>(byPosition.values());
    }

    public int size() {
        return byPosition.size();
    }

    /** Forgets every snitch. @return how many were dropped */
    public int clear() {
        int count = byPosition.size();
        byPosition.clear();
        synchronized (this.connection) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM snitches");
            } catch (SQLException e) {
                AbstractCivModernMod.LOGGER.error("Failed to clear snitches", e);
            }
        }
        return count;
    }
}
