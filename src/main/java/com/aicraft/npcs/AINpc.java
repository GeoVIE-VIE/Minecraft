package com.aicraft.npcs;

import com.aicraft.ai.Dialect;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * Represents an AI-powered NPC with personality, backstory, and faction
 */
public class AINpc {

    private final UUID uuid;
    private String name;
    private String displayName;
    private String faction;
    private String personality;
    private String backstory;
    private String currentMood;
    private Dialect dialect;

    private Location spawnLocation;
    private Location currentLocation;
    private EntityType entityType;

    private boolean isAlive;
    private double health;
    private double maxHealth;

    private long lastInteraction;
    private UUID lastInteractedPlayer;

    // Behavior flags
    private boolean canWander;
    private boolean isHostile;
    private boolean canTrade;
    private boolean canGiveQuests;

    // Home territory system - NPCs stay within their home boundary
    private Location homeLocation;          // Center of NPC's home territory (town center, house, etc.)
    private int homeBoundaryRadius;         // How far NPC can roam from home (default: 15 for town, 100 for wanderers)
    private boolean isNomadic;              // True for NPCs with no fixed home (Wanderers, some Bandits)
    private UUID settlementId;              // ID of settlement this NPC belongs to (if any)

    // Conversation engagement - NPC stops and focuses on this player
    private UUID engagedWithPlayer;
    private long engagementStartTime;

    // Entity reference (for the actual Minecraft entity)
    private transient org.bukkit.entity.Entity bukkitEntity;

    public AINpc(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.displayName = name;
        this.isAlive = true;
        this.health = 20.0;
        this.maxHealth = 20.0;
        this.entityType = EntityType.VILLAGER;
        this.canWander = true;
        this.isHostile = false;
        this.canTrade = false;
        this.canGiveQuests = true;
        this.currentMood = "neutral";
        this.homeBoundaryRadius = 15; // Default: stay within 15 blocks of home
        this.isNomadic = false;
    }

    // === Getters and Setters ===

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFaction() {
        return faction;
    }

    public void setFaction(String faction) {
        this.faction = faction;
    }

    public String getPersonality() {
        return personality;
    }

    public void setPersonality(String personality) {
        this.personality = personality;
    }

    public String getBackstory() {
        return backstory;
    }

    public void setBackstory(String backstory) {
        this.backstory = backstory;
    }

    public String getCurrentMood() {
        return currentMood;
    }

    public void setCurrentMood(String currentMood) {
        this.currentMood = currentMood;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public Location getCurrentLocation() {
        if (bukkitEntity != null && bukkitEntity.isValid()) {
            return bukkitEntity.getLocation();
        }
        return currentLocation != null ? currentLocation : spawnLocation;
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public boolean isAlive() {
        return isAlive;
    }

    public void setAlive(boolean alive) {
        isAlive = alive;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = Math.max(0, Math.min(health, maxHealth));
        if (this.health <= 0) {
            this.isAlive = false;
        }
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public void setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public void damage(double amount) {
        setHealth(health - amount);
    }

    public void heal(double amount) {
        setHealth(health + amount);
    }

    public long getLastInteraction() {
        return lastInteraction;
    }

    public void setLastInteraction(long lastInteraction) {
        this.lastInteraction = lastInteraction;
    }

    public UUID getLastInteractedPlayer() {
        return lastInteractedPlayer;
    }

    public void setLastInteractedPlayer(UUID lastInteractedPlayer) {
        this.lastInteractedPlayer = lastInteractedPlayer;
    }

    public boolean canWander() {
        return canWander;
    }

    public void setCanWander(boolean canWander) {
        this.canWander = canWander;
    }

    public boolean isHostile() {
        return isHostile;
    }

    public void setHostile(boolean hostile) {
        isHostile = hostile;
    }

    public boolean canTrade() {
        return canTrade;
    }

    public void setCanTrade(boolean canTrade) {
        this.canTrade = canTrade;
    }

    public boolean canGiveQuests() {
        return canGiveQuests;
    }

    public void setCanGiveQuests(boolean canGiveQuests) {
        this.canGiveQuests = canGiveQuests;
    }

    // === Home Territory Methods ===

    /**
     * Get the NPC's home location (center of their territory)
     * Falls back to spawn location if no home is set
     */
    public Location getHomeLocation() {
        return homeLocation != null ? homeLocation : spawnLocation;
    }

    public void setHomeLocation(Location homeLocation) {
        this.homeLocation = homeLocation;
    }

    /**
     * Get how far this NPC can roam from their home
     */
    public int getHomeBoundaryRadius() {
        return homeBoundaryRadius;
    }

    public void setHomeBoundaryRadius(int radius) {
        this.homeBoundaryRadius = radius;
    }

    /**
     * Check if this NPC is nomadic (no fixed home, can roam freely)
     */
    public boolean isNomadic() {
        return isNomadic;
    }

    public void setNomadic(boolean nomadic) {
        this.isNomadic = nomadic;
    }

    /**
     * Get the settlement this NPC belongs to
     */
    public UUID getSettlementId() {
        return settlementId;
    }

    public void setSettlementId(UUID settlementId) {
        this.settlementId = settlementId;
    }

    /**
     * Check if the NPC is within their home boundary
     */
    public boolean isWithinHomeBoundary() {
        Location current = getCurrentLocation();
        Location home = getHomeLocation();
        if (current == null || home == null) return true;
        if (!current.getWorld().equals(home.getWorld())) return false;
        return current.distance(home) <= homeBoundaryRadius;
    }

    /**
     * Check if this NPC is currently engaged in conversation with a player
     */
    public boolean isEngaged() {
        return engagedWithPlayer != null;
    }

    /**
     * Get the player this NPC is engaged with
     */
    public UUID getEngagedWithPlayer() {
        return engagedWithPlayer;
    }

    /**
     * Engage this NPC in conversation with a player (stops wandering, focuses on player)
     */
    public void engageWithPlayer(UUID playerUuid) {
        this.engagedWithPlayer = playerUuid;
        this.engagementStartTime = System.currentTimeMillis();
    }

    /**
     * End engagement with player (resumes normal behavior)
     */
    public void disengageFromPlayer() {
        this.engagedWithPlayer = null;
        this.engagementStartTime = 0;
    }

    /**
     * Get how long this NPC has been engaged in conversation (milliseconds)
     */
    public long getEngagementDuration() {
        if (engagedWithPlayer == null) return 0;
        return System.currentTimeMillis() - engagementStartTime;
    }

    public Dialect getDialect() {
        return dialect != null ? dialect : Dialect.STANDARD;
    }

    public void setDialect(Dialect dialect) {
        this.dialect = dialect;
    }

    public String getDialectName() {
        return dialect != null ? dialect.getName() : "Standard";
    }

    public void setDialectFromString(String dialectName) {
        this.dialect = Dialect.fromString(dialectName);
    }

    public org.bukkit.entity.Entity getBukkitEntity() {
        return bukkitEntity;
    }

    public void setBukkitEntity(org.bukkit.entity.Entity bukkitEntity) {
        this.bukkitEntity = bukkitEntity;
    }

    /**
     * Check if the NPC is currently spawned in the world
     */
    public boolean isSpawned() {
        return bukkitEntity != null && bukkitEntity.isValid() && !bukkitEntity.isDead();
    }

    /**
     * Get a brief description of this NPC for display
     */
    public String getDescription() {
        return String.format("%s (%s) - %s", name, faction, personality);
    }

    @Override
    public String toString() {
        return "AINpc{" +
                "uuid=" + uuid +
                ", name='" + name + '\'' +
                ", faction='" + faction + '\'' +
                ", alive=" + isAlive +
                '}';
    }
}
