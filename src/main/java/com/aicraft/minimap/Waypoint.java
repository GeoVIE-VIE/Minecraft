package com.aicraft.minimap;

import org.bukkit.map.MapPalette;

import java.util.UUID;

/**
 * Represents a waypoint that can be displayed on the minimap
 */
public class Waypoint {

    public enum WaypointType {
        QUEST_OBJECTIVE,   // Active quest target
        QUEST_TURNIN,      // Quest completion NPC
        NPC_HOME,          // NPC settlement
        PLAYER_MARKER,     // Custom player waypoint
        DEATH_POINT,       // Where player died
        SETTLEMENT         // Settlement center
    }

    private final String id;
    private final String name;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final WaypointType type;
    private final UUID ownerUuid; // Player who owns this waypoint (for personal waypoints)
    private final long createdAt;
    private boolean temporary;     // Auto-remove after reaching
    private int removeDistance;    // Distance at which to auto-remove

    // Colors for different waypoint types
    private static final byte COLOR_QUEST = MapPalette.matchColor(255, 215, 0);      // Gold
    private static final byte COLOR_QUEST_TURNIN = MapPalette.matchColor(50, 205, 50); // Lime green
    private static final byte COLOR_NPC = MapPalette.matchColor(0, 255, 127);          // Spring green
    private static final byte COLOR_PLAYER = MapPalette.matchColor(255, 255, 0);       // Yellow
    private static final byte COLOR_DEATH = MapPalette.matchColor(255, 0, 0);          // Red
    private static final byte COLOR_SETTLEMENT = MapPalette.matchColor(255, 165, 0);   // Orange

    public Waypoint(String id, String name, String world, int x, int y, int z, WaypointType type, UUID ownerUuid) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.ownerUuid = ownerUuid;
        this.createdAt = System.currentTimeMillis();
        this.temporary = false;
        this.removeDistance = 5;
    }

    // Builder-style setters
    public Waypoint setTemporary(boolean temporary) {
        this.temporary = temporary;
        return this;
    }

    public Waypoint setRemoveDistance(int distance) {
        this.removeDistance = distance;
        return this;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public WaypointType getType() { return type; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public long getCreatedAt() { return createdAt; }
    public boolean isTemporary() { return temporary; }
    public int getRemoveDistance() { return removeDistance; }

    /**
     * Get the map color for this waypoint type
     */
    public byte getColor() {
        switch (type) {
            case QUEST_OBJECTIVE:
                return COLOR_QUEST;
            case QUEST_TURNIN:
                return COLOR_QUEST_TURNIN;
            case NPC_HOME:
                return COLOR_NPC;
            case PLAYER_MARKER:
                return COLOR_PLAYER;
            case DEATH_POINT:
                return COLOR_DEATH;
            case SETTLEMENT:
                return COLOR_SETTLEMENT;
            default:
                return COLOR_PLAYER;
        }
    }

    /**
     * Calculate distance to a location
     */
    public double distanceTo(int otherX, int otherY, int otherZ) {
        int dx = x - otherX;
        int dy = y - otherY;
        int dz = z - otherZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Check if player is within remove distance
     */
    public boolean shouldRemove(int playerX, int playerY, int playerZ) {
        return temporary && distanceTo(playerX, playerY, playerZ) <= removeDistance;
    }

    @Override
    public String toString() {
        return String.format("Waypoint{name='%s', type=%s, pos=(%d,%d,%d)}", name, type, x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Waypoint waypoint = (Waypoint) obj;
        return id.equals(waypoint.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
