package com.aicraft.npcs.building;

import com.aicraft.npcs.AINpc;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a billboard display near an NPC showing their trades/services
 * Uses stacked invisible armor stands with custom names to create floating text
 */
public class NPCBillboard {

    private final UUID billboardId;
    private final UUID npcUuid;
    private Location location;
    private final List<ArmorStand> displayLines = new ArrayList<>();
    private boolean isSpawned = false;

    // Billboard content
    private String title;
    private List<String> contentLines = new ArrayList<>();

    public NPCBillboard(AINpc npc, Location location) {
        this.billboardId = UUID.randomUUID();
        this.npcUuid = npc.getUuid();
        this.location = location.clone().add(0, 2.5, 0); // Float above NPC
        this.title = npc.getName();
        generateContent(npc);
    }

    /**
     * Generate billboard content based on NPC's role
     */
    private void generateContent(AINpc npc) {
        contentLines.clear();

        // Title line with faction color
        String factionColor = getFactionColor(npc.getFaction());
        title = factionColor + "★ " + npc.getName() + " ★";

        // Faction & Role line
        contentLines.add(ChatColor.GRAY + npc.getFaction());

        // Separator
        contentLines.add(ChatColor.DARK_GRAY + "─────────────");

        // Content based on NPC capabilities
        if (npc.canTrade()) {
            contentLines.add(ChatColor.GOLD + "⚖ TRADING");
            addTradeListings(npc);
        }

        if (npc.canGiveQuests()) {
            contentLines.add(ChatColor.YELLOW + "✦ Quests Available");
        }

        // Mood indicator
        String moodEmoji = getMoodEmoji(npc.getCurrentMood());
        contentLines.add(ChatColor.GRAY + "Mood: " + moodEmoji);
    }

    /**
     * Add trade listings for merchant NPCs
     */
    private void addTradeListings(AINpc npc) {
        // Generate some sample trades based on faction
        String faction = npc.getFaction();
        if (faction == null) return;

        switch (faction.toLowerCase()) {
            case "merchants" -> {
                contentLines.add(ChatColor.WHITE + "  🗡 Weapons");
                contentLines.add(ChatColor.WHITE + "  🛡 Armor");
                contentLines.add(ChatColor.WHITE + "  🧪 Potions");
            }
            case "villagers" -> {
                contentLines.add(ChatColor.WHITE + "  🌾 Crops");
                contentLines.add(ChatColor.WHITE + "  🍖 Food");
            }
            case "wanderers" -> {
                contentLines.add(ChatColor.WHITE + "  📜 Maps");
                contentLines.add(ChatColor.WHITE + "  💎 Rare Items");
            }
        }
    }

    /**
     * Spawn the billboard entities in the world
     */
    public void spawn() {
        if (isSpawned) return;
        if (location == null || location.getWorld() == null) return;

        // Spawn title line
        spawnLine(location.clone(), title);

        // Spawn content lines below title
        double yOffset = -0.25;
        for (String line : contentLines) {
            spawnLine(location.clone().add(0, yOffset, 0), line);
            yOffset -= 0.25;
        }

        isSpawned = true;
    }

    /**
     * Spawn a single line of text as an armor stand
     */
    private void spawnLine(Location loc, String text) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setCustomName(text);
        stand.setCustomNameVisible(true);
        stand.setMarker(true); // No hitbox
        stand.setInvulnerable(true);
        stand.setSmall(true);
        displayLines.add(stand);
    }

    /**
     * Remove the billboard from the world
     */
    public void despawn() {
        for (ArmorStand stand : displayLines) {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }
        displayLines.clear();
        isSpawned = false;
    }

    /**
     * Update billboard content (e.g., when NPC mood changes)
     */
    public void update(AINpc npc) {
        if (!isSpawned) return;

        generateContent(npc);

        // Despawn and respawn with new content
        despawn();
        spawn();
    }

    /**
     * Move billboard to new location
     */
    public void moveTo(Location newLocation) {
        if (isSpawned) {
            despawn();
        }
        this.location = newLocation.clone().add(0, 2.5, 0);
        spawn();
    }

    // === Helper Methods ===

    private String getFactionColor(String faction) {
        if (faction == null) return ChatColor.WHITE.toString();
        return switch (faction.toLowerCase()) {
            case "merchants" -> ChatColor.GOLD.toString();
            case "guards" -> ChatColor.BLUE.toString();
            case "villagers" -> ChatColor.GREEN.toString();
            case "wanderers" -> ChatColor.AQUA.toString();
            case "bandits" -> ChatColor.RED.toString();
            case "cultists" -> ChatColor.DARK_PURPLE.toString();
            default -> ChatColor.WHITE.toString();
        };
    }

    private String getMoodEmoji(String mood) {
        if (mood == null) return "😐";
        return switch (mood.toLowerCase()) {
            case "happy", "content" -> "😊";
            case "sad", "melancholy" -> "😢";
            case "angry", "aggressive" -> "😠";
            case "frightened", "scared" -> "😨";
            case "suspicious" -> "🤨";
            case "friendly" -> "😄";
            case "tired" -> "😴";
            case "defensive" -> "😤";
            default -> "😐";
        };
    }

    // === Getters ===

    public UUID getBillboardId() {
        return billboardId;
    }

    public UUID getNpcUuid() {
        return npcUuid;
    }

    public Location getLocation() {
        return location;
    }

    public boolean isSpawned() {
        return isSpawned;
    }
}
