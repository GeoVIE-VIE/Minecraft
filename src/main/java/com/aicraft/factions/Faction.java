package com.aicraft.factions;

import org.bukkit.ChatColor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a faction that NPCs can belong to
 */
public class Faction {

    private final UUID uuid;
    private String name;
    private String description;
    private ChatColor color;
    private boolean hostileByDefault;

    // Faction relationships
    private final Set<String> hostileFactions = new HashSet<>();
    private final Set<String> alliedFactions = new HashSet<>();

    public Faction(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.color = ChatColor.WHITE;
        this.hostileByDefault = false;
    }

    public Faction(String name) {
        this(UUID.randomUUID(), name);
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ChatColor getColor() {
        return color;
    }

    public void setColor(ChatColor color) {
        this.color = color;
    }

    public void setColorFromString(String colorName) {
        try {
            this.color = ChatColor.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.color = ChatColor.WHITE;
        }
    }

    public boolean isHostileByDefault() {
        return hostileByDefault;
    }

    public void setHostileByDefault(boolean hostileByDefault) {
        this.hostileByDefault = hostileByDefault;
    }

    // === Relationship Management ===

    public void addHostileFaction(String factionName) {
        hostileFactions.add(factionName.toLowerCase());
        alliedFactions.remove(factionName.toLowerCase());
    }

    public void removeHostileFaction(String factionName) {
        hostileFactions.remove(factionName.toLowerCase());
    }

    public void addAlliedFaction(String factionName) {
        alliedFactions.add(factionName.toLowerCase());
        hostileFactions.remove(factionName.toLowerCase());
    }

    public void removeAlliedFaction(String factionName) {
        alliedFactions.remove(factionName.toLowerCase());
    }

    public boolean isHostileTo(String factionName) {
        return hostileFactions.contains(factionName.toLowerCase());
    }

    public boolean isAlliedWith(String factionName) {
        return alliedFactions.contains(factionName.toLowerCase());
    }

    public Set<String> getHostileFactions() {
        return new HashSet<>(hostileFactions);
    }

    public Set<String> getAlliedFactions() {
        return new HashSet<>(alliedFactions);
    }

    public void setHostileFactions(Set<String> factions) {
        hostileFactions.clear();
        for (String f : factions) {
            hostileFactions.add(f.toLowerCase());
        }
    }

    /**
     * Get relationship status with another faction
     */
    public FactionRelation getRelationWith(String factionName) {
        if (isHostileTo(factionName)) return FactionRelation.HOSTILE;
        if (isAlliedWith(factionName)) return FactionRelation.ALLIED;
        return FactionRelation.NEUTRAL;
    }

    /**
     * Get display string with color
     */
    public String getDisplayName() {
        return color + name;
    }

    @Override
    public String toString() {
        return "Faction{" +
                "name='" + name + '\'' +
                ", hostile=" + hostileByDefault +
                '}';
    }

    public enum FactionRelation {
        HOSTILE,
        NEUTRAL,
        ALLIED
    }
}
