package com.aicraft.npcs.boss;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Defines a boss NPC type with all its properties
 */
public class BossDefinition {

    public final String id;
    public final String name;
    public final String faction;
    public final EntityType entityType;
    public final double health;
    public final String backstory;
    public final String personality;
    public final List<String> guaranteedDrops; // IDs of legendary items to drop
    public final ChatColor nameColor;

    public BossDefinition(String id, String name, String faction, EntityType entityType,
                          double health, String backstory, String personality,
                          List<String> guaranteedDrops, ChatColor nameColor) {
        this.id = id;
        this.name = name;
        this.faction = faction;
        this.entityType = entityType;
        this.health = health;
        this.backstory = backstory;
        this.personality = personality;
        this.guaranteedDrops = guaranteedDrops;
        this.nameColor = nameColor;
    }
}
