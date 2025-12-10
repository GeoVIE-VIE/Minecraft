package com.aicraft.memory;

import java.util.UUID;

/**
 * Represents a single conversation exchange between an NPC and a player
 */
public class ConversationMemory {

    private final UUID npcUuid;
    private final UUID playerUuid;
    private final String playerName;
    private String message;
    private String response;
    private long timestamp;

    public ConversationMemory(UUID npcUuid, UUID playerUuid, String playerName) {
        this.npcUuid = npcUuid;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getNpcUuid() {
        return npcUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Format this memory for inclusion in AI context
     */
    public String toContextString() {
        return String.format("%s: \"%s\"\nYou: \"%s\"", playerName, message, response);
    }
}
