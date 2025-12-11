package com.aicraft.database;

import com.aicraft.AICompanions;
import com.aicraft.factions.Faction;
import com.aicraft.npcs.AINpc;
import com.aicraft.memory.ConversationMemory;
import com.aicraft.quests.Quest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Handles all database operations using SQLite
 */
public class DatabaseManager {

    private final AICompanions plugin;
    private final Gson gson = new Gson();
    private Connection connection;

    public DatabaseManager(AICompanions plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize database connection and create tables
     */
    public void initialize() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, "aicompanions.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            connection = DriverManager.getConnection(url);
            createTables();

            plugin.getLogger().info("Database initialized: " + dbFile.getName());

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Create all required tables
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // NPCs table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS npcs (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    display_name TEXT,
                    faction TEXT,
                    personality TEXT,
                    backstory TEXT,
                    current_mood TEXT,
                    entity_type TEXT,
                    spawn_world TEXT,
                    spawn_x REAL,
                    spawn_y REAL,
                    spawn_z REAL,
                    spawn_yaw REAL,
                    spawn_pitch REAL,
                    is_alive INTEGER DEFAULT 1,
                    health REAL DEFAULT 20,
                    max_health REAL DEFAULT 20,
                    can_wander INTEGER DEFAULT 1,
                    is_hostile INTEGER DEFAULT 0,
                    can_trade INTEGER DEFAULT 0,
                    can_give_quests INTEGER DEFAULT 1,
                    created_at INTEGER,
                    updated_at INTEGER
                )
            """);

            // Factions table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS factions (
                    uuid TEXT PRIMARY KEY,
                    name TEXT UNIQUE NOT NULL,
                    description TEXT,
                    color TEXT,
                    hostile_by_default INTEGER DEFAULT 0,
                    hostile_factions TEXT,
                    allied_factions TEXT
                )
            """);

            // Conversation memory table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    npc_uuid TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT,
                    message TEXT,
                    response TEXT,
                    timestamp INTEGER,
                    FOREIGN KEY (npc_uuid) REFERENCES npcs(uuid)
                )
            """);

            // Quests table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS quests (
                    uuid TEXT PRIMARY KEY,
                    npc_uuid TEXT,
                    player_uuid TEXT,
                    title TEXT,
                    description TEXT,
                    objective TEXT,
                    quest_type TEXT,
                    target TEXT,
                    amount INTEGER,
                    progress INTEGER DEFAULT 0,
                    reward_xp INTEGER,
                    reward_items TEXT,
                    status TEXT DEFAULT 'ACTIVE',
                    created_at INTEGER,
                    completed_at INTEGER,
                    FOREIGN KEY (npc_uuid) REFERENCES npcs(uuid)
                )
            """);

            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memories_npc ON memories(npc_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_memories_player ON memories(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_quests_player ON quests(player_uuid)");
        }
    }

    /**
     * Close the database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing database: " + e.getMessage());
        }
    }

    // === NPC Operations ===

    public void saveNPC(AINpc npc) {
        String sql = """
            INSERT OR REPLACE INTO npcs
            (uuid, name, display_name, faction, personality, backstory, current_mood,
             entity_type, spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch,
             is_alive, health, max_health, can_wander, is_hostile, can_trade, can_give_quests,
             created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, npc.getUuid().toString());
            ps.setString(2, npc.getName());
            ps.setString(3, npc.getDisplayName());
            ps.setString(4, npc.getFaction());
            ps.setString(5, npc.getPersonality());
            ps.setString(6, npc.getBackstory());
            ps.setString(7, npc.getCurrentMood());
            ps.setString(8, npc.getEntityType().name());

            Location spawn = npc.getSpawnLocation();
            if (spawn != null && spawn.getWorld() != null) {
                ps.setString(9, spawn.getWorld().getName());
                ps.setDouble(10, spawn.getX());
                ps.setDouble(11, spawn.getY());
                ps.setDouble(12, spawn.getZ());
                ps.setFloat(13, spawn.getYaw());
                ps.setFloat(14, spawn.getPitch());
            } else {
                ps.setNull(9, Types.VARCHAR);
                ps.setNull(10, Types.REAL);
                ps.setNull(11, Types.REAL);
                ps.setNull(12, Types.REAL);
                ps.setNull(13, Types.REAL);
                ps.setNull(14, Types.REAL);
            }

            ps.setInt(15, npc.isAlive() ? 1 : 0);
            ps.setDouble(16, npc.getHealth());
            ps.setDouble(17, npc.getMaxHealth());
            ps.setInt(18, npc.canWander() ? 1 : 0);
            ps.setInt(19, npc.isHostile() ? 1 : 0);
            ps.setInt(20, npc.canTrade() ? 1 : 0);
            ps.setInt(21, npc.canGiveQuests() ? 1 : 0);
            ps.setLong(22, System.currentTimeMillis());
            ps.setLong(23, System.currentTimeMillis());

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save NPC: " + e.getMessage());
        }
    }

    public List<AINpc> loadAllNPCs() {
        List<AINpc> npcs = new ArrayList<>();
        String sql = "SELECT * FROM npcs";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                AINpc npc = loadNPCFromResultSet(rs);
                if (npc != null) {
                    npcs.add(npc);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load NPCs: " + e.getMessage());
        }

        return npcs;
    }

    private AINpc loadNPCFromResultSet(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String name = rs.getString("name");

        AINpc npc = new AINpc(uuid, name);
        npc.setDisplayName(rs.getString("display_name"));
        npc.setFaction(rs.getString("faction"));
        npc.setPersonality(rs.getString("personality"));
        npc.setBackstory(rs.getString("backstory"));
        npc.setCurrentMood(rs.getString("current_mood"));

        String entityType = rs.getString("entity_type");
        if (entityType != null) {
            try {
                npc.setEntityType(EntityType.valueOf(entityType));
            } catch (IllegalArgumentException ignored) {}
        }

        String worldName = rs.getString("spawn_world");
        if (worldName != null) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                Location spawn = new Location(
                        world,
                        rs.getDouble("spawn_x"),
                        rs.getDouble("spawn_y"),
                        rs.getDouble("spawn_z"),
                        rs.getFloat("spawn_yaw"),
                        rs.getFloat("spawn_pitch")
                );
                npc.setSpawnLocation(spawn);
                npc.setCurrentLocation(spawn);
            }
        }

        npc.setAlive(rs.getInt("is_alive") == 1);
        npc.setHealth(rs.getDouble("health"));
        npc.setMaxHealth(rs.getDouble("max_health"));
        npc.setCanWander(rs.getInt("can_wander") == 1);
        npc.setHostile(rs.getInt("is_hostile") == 1);
        npc.setCanTrade(rs.getInt("can_trade") == 1);
        npc.setCanGiveQuests(rs.getInt("can_give_quests") == 1);

        return npc;
    }

    public void deleteNPC(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM npcs WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete NPC: " + e.getMessage());
        }
    }

    /**
     * Clear all NPCs from the database
     */
    public void clearAllNPCs() {
        try (Statement stmt = connection.createStatement()) {
            int deleted = stmt.executeUpdate("DELETE FROM npcs");
            plugin.getLogger().info("Cleared " + deleted + " NPCs from database");
            // Also clear related memories
            stmt.executeUpdate("DELETE FROM memories");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to clear NPCs: " + e.getMessage());
        }
    }

    // === Faction Operations ===

    public void saveFaction(Faction faction) {
        String sql = """
            INSERT OR REPLACE INTO factions
            (uuid, name, description, color, hostile_by_default, hostile_factions, allied_factions)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, faction.getUuid().toString());
            ps.setString(2, faction.getName());
            ps.setString(3, faction.getDescription());
            ps.setString(4, faction.getColor().name());
            ps.setInt(5, faction.isHostileByDefault() ? 1 : 0);
            ps.setString(6, gson.toJson(faction.getHostileFactions()));
            ps.setString(7, gson.toJson(faction.getAlliedFactions()));

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save faction: " + e.getMessage());
        }
    }

    public List<Faction> loadAllFactions() {
        List<Faction> factions = new ArrayList<>();
        String sql = "SELECT * FROM factions";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String name = rs.getString("name");

                Faction faction = new Faction(uuid, name);
                faction.setDescription(rs.getString("description"));
                faction.setColorFromString(rs.getString("color"));
                faction.setHostileByDefault(rs.getInt("hostile_by_default") == 1);

                String hostileJson = rs.getString("hostile_factions");
                if (hostileJson != null) {
                    Set<String> hostile = gson.fromJson(hostileJson, new TypeToken<Set<String>>(){}.getType());
                    if (hostile != null) faction.setHostileFactions(hostile);
                }

                factions.add(faction);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load factions: " + e.getMessage());
        }

        return factions;
    }

    public void deleteFaction(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM factions WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete faction: " + e.getMessage());
        }
    }

    // === Memory Operations ===

    public void saveMemory(ConversationMemory memory) {
        String sql = """
            INSERT INTO memories (npc_uuid, player_uuid, player_name, message, response, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, memory.getNpcUuid().toString());
            ps.setString(2, memory.getPlayerUuid().toString());
            ps.setString(3, memory.getPlayerName());
            ps.setString(4, memory.getMessage());
            ps.setString(5, memory.getResponse());
            ps.setLong(6, memory.getTimestamp());

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save memory: " + e.getMessage());
        }
    }

    public List<ConversationMemory> loadMemories(UUID npcUuid, UUID playerUuid, int limit) {
        List<ConversationMemory> memories = new ArrayList<>();
        String sql = """
            SELECT * FROM memories
            WHERE npc_uuid = ? AND player_uuid = ?
            ORDER BY timestamp DESC
            LIMIT ?
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, npcUuid.toString());
            ps.setString(2, playerUuid.toString());
            ps.setInt(3, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ConversationMemory memory = new ConversationMemory(
                            UUID.fromString(rs.getString("npc_uuid")),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name")
                    );
                    memory.setMessage(rs.getString("message"));
                    memory.setResponse(rs.getString("response"));
                    memory.setTimestamp(rs.getLong("timestamp"));
                    memories.add(memory);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load memories: " + e.getMessage());
        }

        // Reverse to get chronological order
        Collections.reverse(memories);
        return memories;
    }

    public void cleanOldMemories(int retentionDays) {
        if (retentionDays <= 0) return;

        long cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
        String sql = "DELETE FROM memories WHERE timestamp < ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, cutoff);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                plugin.debug("Cleaned " + deleted + " old memories");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to clean memories: " + e.getMessage());
        }
    }

    // === Quest Operations ===

    public void saveQuest(Quest quest) {
        String sql = """
            INSERT OR REPLACE INTO quests
            (uuid, npc_uuid, player_uuid, title, description, objective, quest_type,
             target, amount, progress, reward_xp, reward_items, status, created_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, quest.getUuid().toString());
            ps.setString(2, quest.getNpcUuid() != null ? quest.getNpcUuid().toString() : null);
            ps.setString(3, quest.getPlayerUuid().toString());
            ps.setString(4, quest.getTitle());
            ps.setString(5, quest.getDescription());
            ps.setString(6, quest.getObjective());
            ps.setString(7, quest.getQuestType());
            ps.setString(8, quest.getTarget());
            ps.setInt(9, quest.getAmount());
            ps.setInt(10, quest.getProgress());
            ps.setInt(11, quest.getRewardXp());
            ps.setString(12, gson.toJson(quest.getRewardItems()));
            ps.setString(13, quest.getStatus().name());
            ps.setLong(14, quest.getCreatedAt());
            ps.setLong(15, quest.getCompletedAt());

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save quest: " + e.getMessage());
        }
    }

    public List<Quest> loadPlayerQuests(UUID playerUuid) {
        List<Quest> quests = new ArrayList<>();
        String sql = "SELECT * FROM quests WHERE player_uuid = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Quest quest = loadQuestFromResultSet(rs);
                    if (quest != null) quests.add(quest);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load quests: " + e.getMessage());
        }

        return quests;
    }

    private Quest loadQuestFromResultSet(ResultSet rs) throws SQLException {
        Quest quest = new Quest(
                UUID.fromString(rs.getString("uuid")),
                UUID.fromString(rs.getString("player_uuid"))
        );

        String npcUuid = rs.getString("npc_uuid");
        if (npcUuid != null) quest.setNpcUuid(UUID.fromString(npcUuid));

        quest.setTitle(rs.getString("title"));
        quest.setDescription(rs.getString("description"));
        quest.setObjective(rs.getString("objective"));
        quest.setQuestType(rs.getString("quest_type"));
        quest.setTarget(rs.getString("target"));
        quest.setAmount(rs.getInt("amount"));
        quest.setProgress(rs.getInt("progress"));
        quest.setRewardXp(rs.getInt("reward_xp"));

        String itemsJson = rs.getString("reward_items");
        if (itemsJson != null) {
            List<String> items = gson.fromJson(itemsJson, new TypeToken<List<String>>(){}.getType());
            if (items != null) quest.setRewardItems(items);
        }

        quest.setStatus(Quest.QuestStatus.valueOf(rs.getString("status")));
        quest.setCreatedAt(rs.getLong("created_at"));
        quest.setCompletedAt(rs.getLong("completed_at"));

        return quest;
    }
}
