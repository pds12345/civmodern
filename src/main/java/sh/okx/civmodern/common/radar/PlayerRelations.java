package sh.okx.civmodern.common.radar;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which players are marked friendly/neutral/hostile, for radar name colouring. Backed by its own
 * sqlite database one directory above the per-dimension map data (i.e. under civmap/&lt;type&gt;/
 * &lt;server&gt;/), so a relation set in one dimension still applies after a portal or /kill respawn
 * moves the player to another - unlike waypoints, hostility isn't tied to a location.
 * Usernames are matched case-insensitively; the map is keyed by the lowercased username, while
 * the entry itself keeps the casing it was added with for display.
 */
public class PlayerRelations {

    private final Map<String, PlayerRelationEntry> entries = new HashMap<>();
    private final Connection connection;

    public PlayerRelations(File serverFolder) {
        serverFolder.mkdirs();
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + serverFolder.toPath().resolve("player_relations.sqlite").toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS player_relations (username TEXT NOT NULL PRIMARY KEY, relation TEXT NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL)");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        load();
    }

    public void close() {
        try {
            this.connection.close();
        } catch (SQLException e) {
        }
    }

    private void load() {
        synchronized (this.connection) {
            try (Statement statement = connection.createStatement()) {
                ResultSet resultSet = statement.executeQuery("SELECT username, relation, created, updated FROM player_relations");

                while (resultSet.next()) {
                    String username = resultSet.getString("username");
                    entries.put(username.toLowerCase(Locale.ROOT), new PlayerRelationEntry(
                        username,
                        PlayerRelation.fromDatabaseKey(resultSet.getString("relation")),
                        resultSet.getLong("created"),
                        resultSet.getLong("updated")
                    ));
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** NEUTRAL for both an unlisted player and one explicitly marked neutral - same radar colour either way. */
    public PlayerRelation getRelation(String username) {
        PlayerRelationEntry entry = entries.get(username.toLowerCase(Locale.ROOT));
        return entry == null ? PlayerRelation.NEUTRAL : entry.relation();
    }

    public List<PlayerRelationEntry> getByRelation(PlayerRelation relation) {
        List<PlayerRelationEntry> list = new ArrayList<>();
        for (PlayerRelationEntry entry : entries.values()) {
            if (entry.relation() == relation) {
                list.add(entry);
            }
        }
        return list;
    }

    /** Adds the player to the given list, or moves them to it if they were already on another. */
    public void setRelation(String username, PlayerRelation relation) {
        String key = username.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long created = entries.containsKey(key) ? entries.get(key).created() : now;
        PlayerRelationEntry entry = new PlayerRelationEntry(username, relation, created, now);
        entries.put(key, entry);

        synchronized (this.connection) {
            try {
                // The primary key is case-sensitive, but usernames are matched case-insensitively
                // above, so upsert manually rather than relying on ON CONFLICT to catch a case
                // change (e.g. an existing "Notch" row when this call is for "notch").
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM player_relations WHERE lower(username) = ?")) {
                    delete.setString(1, key);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO player_relations (username, relation, created, updated) VALUES (?, ?, ?, ?)")) {
                    insert.setString(1, entry.username());
                    insert.setString(2, relation.toDatabaseKey());
                    insert.setLong(3, entry.created());
                    insert.setLong(4, entry.updated());
                    insert.executeUpdate();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void remove(String username) {
        String key = username.toLowerCase(Locale.ROOT);
        if (entries.remove(key) == null) {
            return;
        }

        synchronized (this.connection) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM player_relations WHERE lower(username) = ?")) {
                statement.setString(1, key);
                statement.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
