package com.aicraft.memory;

import com.aicraft.AICompanions;
import com.aicraft.database.DatabaseManager;
import com.aicraft.npcs.AINpc;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages conversation memories for NPCs
 * Allows NPCs to remember past interactions with players
 */
public class MemoryManager {

    private final AICompanions plugin;
    private final DatabaseManager database;

    // Cache: NPC UUID -> (Player UUID -> List of memories)
    private final Map<UUID, Map<UUID, List<ConversationMemory>>> memoryCache = new ConcurrentHashMap<>();

    // Pending saves
    private final List<ConversationMemory> pendingSaves = Collections.synchronizedList(new ArrayList<>());

    private int maxHistory;
    private int retentionDays;

    public MemoryManager(AICompanions plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;

        this.maxHistory = plugin.getConfig().getInt("memory.max-history", 20);
        this.retentionDays = plugin.getConfig().getInt("memory.retention-days", 30);
    }

    /**
     * Record a conversation exchange
     */
    public void recordConversation(AINpc npc, UUID playerUuid, String playerName, String message, String response) {
        ConversationMemory memory = new ConversationMemory(npc.getUuid(), playerUuid, playerName);
        memory.setMessage(message);
        memory.setResponse(response);

        // Add to cache
        memoryCache
                .computeIfAbsent(npc.getUuid(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(playerUuid, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(memory);

        // Trim to max history
        List<ConversationMemory> playerMemories = memoryCache.get(npc.getUuid()).get(playerUuid);
        while (playerMemories.size() > maxHistory) {
            playerMemories.remove(0);
        }

        // Queue for save
        pendingSaves.add(memory);

        plugin.debug("Recorded memory: " + npc.getName() + " <-> " + playerName);
    }

    /**
     * Get conversation history for an NPC-player pair
     */
    public String getConversationHistory(AINpc npc, UUID playerUuid) {
        List<ConversationMemory> memories = getMemories(npc.getUuid(), playerUuid);

        if (memories.isEmpty()) {
            return "";
        }

        StringBuilder history = new StringBuilder();
        for (ConversationMemory memory : memories) {
            history.append(memory.toContextString()).append("\n");
        }

        return history.toString().trim();
    }

    /**
     * Get memories from cache or database
     */
    private List<ConversationMemory> getMemories(UUID npcUuid, UUID playerUuid) {
        // Check cache first
        Map<UUID, List<ConversationMemory>> npcMemories = memoryCache.get(npcUuid);
        if (npcMemories != null) {
            List<ConversationMemory> playerMemories = npcMemories.get(playerUuid);
            if (playerMemories != null && !playerMemories.isEmpty()) {
                return new ArrayList<>(playerMemories);
            }
        }

        // Load from database
        List<ConversationMemory> loaded = database.loadMemories(npcUuid, playerUuid, maxHistory);

        // Cache the results
        if (!loaded.isEmpty()) {
            memoryCache
                    .computeIfAbsent(npcUuid, k -> new ConcurrentHashMap<>())
                    .put(playerUuid, Collections.synchronizedList(new ArrayList<>(loaded)));
        }

        return loaded;
    }

    /**
     * Check if an NPC has memories of a player
     */
    public boolean hasMemoriesOf(AINpc npc, UUID playerUuid) {
        return !getMemories(npc.getUuid(), playerUuid).isEmpty();
    }

    /**
     * Get a summary of what an NPC knows about a player
     */
    public String getPlayerSummary(AINpc npc, UUID playerUuid) {
        List<ConversationMemory> memories = getMemories(npc.getUuid(), playerUuid);
        if (memories.isEmpty()) {
            return "I don't believe we've met before.";
        }

        int conversationCount = memories.size();
        ConversationMemory lastMemory = memories.get(memories.size() - 1);

        return String.format(
                "We have spoken %d times. Our last conversation was about: \"%s\"",
                conversationCount,
                lastMemory.getMessage().length() > 50
                        ? lastMemory.getMessage().substring(0, 50) + "..."
                        : lastMemory.getMessage()
        );
    }

    /**
     * Process gossip - NPCs share information about players
     */
    public void processGossip(Collection<AINpc> npcs) {
        // For now, just clean old memories
        database.cleanOldMemories(retentionDays);

        // Future: Implement gossip system where NPCs in the same faction
        // share information about players they've interacted with
        plugin.debug("Processed gossip for " + npcs.size() + " NPCs");
    }

    /**
     * Save all pending memories to database
     */
    public void saveAll() {
        List<ConversationMemory> toSave;
        synchronized (pendingSaves) {
            toSave = new ArrayList<>(pendingSaves);
            pendingSaves.clear();
        }

        for (ConversationMemory memory : toSave) {
            database.saveMemory(memory);
        }

        if (!toSave.isEmpty()) {
            plugin.debug("Saved " + toSave.size() + " memories to database");
        }
    }

    /**
     * Clear all memories for an NPC
     */
    public void clearNPCMemories(UUID npcUuid) {
        memoryCache.remove(npcUuid);
    }

    /**
     * Clear memories between specific NPC and player
     */
    public void clearMemories(UUID npcUuid, UUID playerUuid) {
        Map<UUID, List<ConversationMemory>> npcMemories = memoryCache.get(npcUuid);
        if (npcMemories != null) {
            npcMemories.remove(playerUuid);
        }
    }
}
