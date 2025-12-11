package com.aicraft.npcs.social;

import java.util.UUID;

/**
 * Represents a relationship between two NPCs
 */
public class NPCRelationship {

    public enum RelationType {
        STRANGER(0, "Stranger"),
        ACQUAINTANCE(20, "Acquaintance"),
        FRIEND(50, "Friend"),
        CLOSE_FRIEND(70, "Close Friend"),
        ROMANTIC(80, "Romantic Partner"),
        SPOUSE(90, "Spouse"),
        RIVAL(-30, "Rival"),
        ENEMY(-60, "Enemy");

        private final int minAffinity;
        private final String displayName;

        RelationType(int minAffinity, String displayName) {
            this.minAffinity = minAffinity;
            this.displayName = displayName;
        }

        public int getMinAffinity() { return minAffinity; }
        public String getDisplayName() { return displayName; }

        public static RelationType fromAffinity(int affinity, boolean romantic) {
            if (romantic && affinity >= 90) return SPOUSE;
            if (romantic && affinity >= 80) return ROMANTIC;
            if (affinity >= 70) return CLOSE_FRIEND;
            if (affinity >= 50) return FRIEND;
            if (affinity >= 20) return ACQUAINTANCE;
            if (affinity <= -60) return ENEMY;
            if (affinity <= -30) return RIVAL;
            return STRANGER;
        }
    }

    private final UUID uuid;
    private final UUID npc1Uuid;
    private final UUID npc2Uuid;

    private int affinity; // -100 to 100
    private boolean romantic;
    private boolean secretAffair;

    private long lastInteraction;
    private int interactionCount;

    // Relationship history
    private String sharedMemory;

    public NPCRelationship(UUID npc1Uuid, UUID npc2Uuid) {
        this.uuid = UUID.randomUUID();
        this.npc1Uuid = npc1Uuid;
        this.npc2Uuid = npc2Uuid;
        this.affinity = 0;
        this.romantic = false;
        this.secretAffair = false;
        this.lastInteraction = System.currentTimeMillis();
        this.interactionCount = 0;
    }

    public UUID getUuid() { return uuid; }
    public UUID getNpc1Uuid() { return npc1Uuid; }
    public UUID getNpc2Uuid() { return npc2Uuid; }

    public int getAffinity() { return affinity; }

    public void setAffinity(int affinity) {
        this.affinity = Math.max(-100, Math.min(100, affinity));
    }

    public void modifyAffinity(int delta) {
        setAffinity(this.affinity + delta);
    }

    public boolean isRomantic() { return romantic; }
    public void setRomantic(boolean romantic) { this.romantic = romantic; }

    public boolean isSecretAffair() { return secretAffair; }
    public void setSecretAffair(boolean secretAffair) { this.secretAffair = secretAffair; }

    public long getLastInteraction() { return lastInteraction; }
    public void setLastInteraction(long lastInteraction) { this.lastInteraction = lastInteraction; }

    public int getInteractionCount() { return interactionCount; }
    public void incrementInteraction() {
        this.interactionCount++;
        this.lastInteraction = System.currentTimeMillis();
    }

    public String getSharedMemory() { return sharedMemory; }
    public void setSharedMemory(String sharedMemory) { this.sharedMemory = sharedMemory; }

    /**
     * Get the type of relationship based on affinity
     */
    public RelationType getType() {
        return RelationType.fromAffinity(affinity, romantic);
    }

    /**
     * Check if this relationship involves a specific NPC
     */
    public boolean involves(UUID npcUuid) {
        return npc1Uuid.equals(npcUuid) || npc2Uuid.equals(npcUuid);
    }

    /**
     * Get the other NPC in this relationship
     */
    public UUID getOther(UUID npcUuid) {
        if (npc1Uuid.equals(npcUuid)) return npc2Uuid;
        if (npc2Uuid.equals(npcUuid)) return npc1Uuid;
        return null;
    }

    /**
     * Check if relationship is positive (friends or better)
     */
    public boolean isPositive() {
        return affinity >= 20;
    }

    /**
     * Check if relationship is negative (rivals or worse)
     */
    public boolean isNegative() {
        return affinity <= -30;
    }
}
