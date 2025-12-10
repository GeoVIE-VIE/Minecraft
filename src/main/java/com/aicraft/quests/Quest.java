package com.aicraft.quests;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a quest that can be given by NPCs to players
 */
public class Quest {

    private final UUID uuid;
    private final UUID playerUuid;
    private UUID npcUuid;

    private String title;
    private String description;
    private String objective;
    private String questType; // fetch, kill, escort, explore, delivery
    private String target;    // item name, mob type, location, etc.
    private int amount;
    private int progress;

    private int rewardXp;
    private List<String> rewardItems;

    private QuestStatus status;
    private long createdAt;
    private long completedAt;

    // Location tracking for waypoints
    private String targetWorldName;
    private double targetX;
    private double targetY;
    private double targetZ;
    private boolean hasTargetLocation = false;

    // Turn-in location (NPC location)
    private String turnInWorldName;
    private double turnInX;
    private double turnInY;
    private double turnInZ;
    private boolean hasTurnInLocation = false;

    public Quest(UUID uuid, UUID playerUuid) {
        this.uuid = uuid;
        this.playerUuid = playerUuid;
        this.rewardItems = new ArrayList<>();
        this.status = QuestStatus.ACTIVE;
        this.createdAt = System.currentTimeMillis();
        this.progress = 0;
    }

    public Quest(UUID playerUuid) {
        this(UUID.randomUUID(), playerUuid);
    }

    // === Progress Management ===

    public void incrementProgress() {
        incrementProgress(1);
    }

    public void incrementProgress(int amount) {
        this.progress = Math.min(this.progress + amount, this.amount);
        if (this.progress >= this.amount) {
            this.status = QuestStatus.READY_TO_TURN_IN;
        }
    }

    public boolean isComplete() {
        return progress >= amount;
    }

    public float getProgressPercentage() {
        if (amount == 0) return 100f;
        return (progress / (float) amount) * 100f;
    }

    public void complete() {
        this.status = QuestStatus.COMPLETED;
        this.completedAt = System.currentTimeMillis();
    }

    public void fail() {
        this.status = QuestStatus.FAILED;
        this.completedAt = System.currentTimeMillis();
    }

    public void abandon() {
        this.status = QuestStatus.ABANDONED;
        this.completedAt = System.currentTimeMillis();
    }

    // === Getters and Setters ===

    public UUID getUuid() {
        return uuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public UUID getNpcUuid() {
        return npcUuid;
    }

    public void setNpcUuid(UUID npcUuid) {
        this.npcUuid = npcUuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public String getQuestType() {
        return questType;
    }

    public void setQuestType(String questType) {
        this.questType = questType;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getRewardXp() {
        return rewardXp;
    }

    public void setRewardXp(int rewardXp) {
        this.rewardXp = rewardXp;
    }

    public List<String> getRewardItems() {
        return rewardItems;
    }

    public void setRewardItems(List<String> rewardItems) {
        this.rewardItems = rewardItems != null ? rewardItems : new ArrayList<>();
    }

    public void addRewardItem(String item) {
        this.rewardItems.add(item);
    }

    public QuestStatus getStatus() {
        return status;
    }

    public void setStatus(QuestStatus status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Get a formatted progress string
     */
    public String getProgressString() {
        return String.format("%d/%d", progress, amount);
    }

    @Override
    public String toString() {
        return "Quest{" +
                "title='" + title + '\'' +
                ", type='" + questType + '\'' +
                ", progress=" + progress + "/" + amount +
                ", status=" + status +
                '}';
    }

    // === Location Methods ===

    /**
     * Set the target location for this quest (where to do the quest)
     */
    public void setTargetLocation(Location location) {
        if (location != null && location.getWorld() != null) {
            this.targetWorldName = location.getWorld().getName();
            this.targetX = location.getX();
            this.targetY = location.getY();
            this.targetZ = location.getZ();
            this.hasTargetLocation = true;
        }
    }

    /**
     * Get the target location for this quest
     */
    public Location getTargetLocation(org.bukkit.Server server) {
        if (!hasTargetLocation || targetWorldName == null) return null;
        World world = server.getWorld(targetWorldName);
        if (world == null) return null;
        return new Location(world, targetX, targetY, targetZ);
    }

    public boolean hasTargetLocation() {
        return hasTargetLocation;
    }

    /**
     * Set the turn-in location (where the quest giver NPC is)
     */
    public void setTurnInLocation(Location location) {
        if (location != null && location.getWorld() != null) {
            this.turnInWorldName = location.getWorld().getName();
            this.turnInX = location.getX();
            this.turnInY = location.getY();
            this.turnInZ = location.getZ();
            this.hasTurnInLocation = true;
        }
    }

    /**
     * Get the turn-in location
     */
    public Location getTurnInLocation(org.bukkit.Server server) {
        if (!hasTurnInLocation || turnInWorldName == null) return null;
        World world = server.getWorld(turnInWorldName);
        if (world == null) return null;
        return new Location(world, turnInX, turnInY, turnInZ);
    }

    public boolean hasTurnInLocation() {
        return hasTurnInLocation;
    }

    // Serialization helpers for database storage
    public String getTargetWorldName() { return targetWorldName; }
    public void setTargetWorldName(String name) { this.targetWorldName = name; }
    public double getTargetX() { return targetX; }
    public void setTargetX(double x) { this.targetX = x; }
    public double getTargetY() { return targetY; }
    public void setTargetY(double y) { this.targetY = y; }
    public double getTargetZ() { return targetZ; }
    public void setTargetZ(double z) { this.targetZ = z; }
    public void setHasTargetLocation(boolean has) { this.hasTargetLocation = has; }

    public String getTurnInWorldName() { return turnInWorldName; }
    public void setTurnInWorldName(String name) { this.turnInWorldName = name; }
    public double getTurnInX() { return turnInX; }
    public void setTurnInX(double x) { this.turnInX = x; }
    public double getTurnInY() { return turnInY; }
    public void setTurnInY(double y) { this.turnInY = y; }
    public double getTurnInZ() { return turnInZ; }
    public void setTurnInZ(double z) { this.turnInZ = z; }
    public void setHasTurnInLocation(boolean has) { this.hasTurnInLocation = has; }

    /**
     * Get the appropriate waypoint location based on quest status
     */
    public Location getWaypointLocation(org.bukkit.Server server) {
        if (status == QuestStatus.READY_TO_TURN_IN && hasTurnInLocation) {
            return getTurnInLocation(server);
        } else if (hasTargetLocation) {
            return getTargetLocation(server);
        }
        return null;
    }

    /**
     * Get distance to waypoint from a location
     */
    public double getDistanceToWaypoint(Location from, org.bukkit.Server server) {
        Location waypoint = getWaypointLocation(server);
        if (waypoint == null || from == null) return -1;
        if (!waypoint.getWorld().equals(from.getWorld())) return -1;
        return from.distance(waypoint);
    }

    /**
     * Get compass direction to waypoint
     */
    public String getDirectionToWaypoint(Location from, org.bukkit.Server server) {
        Location waypoint = getWaypointLocation(server);
        if (waypoint == null || from == null) return "Unknown";
        if (!waypoint.getWorld().equals(from.getWorld())) return "Different World";

        double dx = waypoint.getX() - from.getX();
        double dz = waypoint.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx));

        // Convert to compass direction
        if (angle < 0) angle += 360;

        if (angle >= 337.5 || angle < 22.5) return "East";
        if (angle >= 22.5 && angle < 67.5) return "Southeast";
        if (angle >= 67.5 && angle < 112.5) return "South";
        if (angle >= 112.5 && angle < 157.5) return "Southwest";
        if (angle >= 157.5 && angle < 202.5) return "West";
        if (angle >= 202.5 && angle < 247.5) return "Northwest";
        if (angle >= 247.5 && angle < 292.5) return "North";
        return "Northeast";
    }

    public enum QuestStatus {
        ACTIVE,
        READY_TO_TURN_IN,
        COMPLETED,
        FAILED,
        ABANDONED
    }
}
