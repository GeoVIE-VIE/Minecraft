package com.aicraft.npcs.social;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages social interactions between NPCs
 * Handles relationships, affairs, conversations, and faction behaviors
 */
public class NPCSocialManager {

    private final AICompanions plugin;
    private final NPCManager npcManager;

    // Relationship storage: key = sorted UUID pair string
    private final Map<String, NPCRelationship> relationships = new ConcurrentHashMap<>();

    // Track NPC conversations
    private final Map<UUID, UUID> npcConversations = new HashMap<>();

    private BukkitTask socialTask;
    private BukkitTask chantingTask;

    private final Random random = new Random();

    // Cultist chants to Geodjian
    private static final String[] CULTIST_CHANTS = {
            "Geodjian sees all...",
            "The All-Seer watches over us...",
            "In darkness, we find truth...",
            "Geodjian's light guides us...",
            "Through shadows, wisdom comes...",
            "The faithful shall be rewarded...",
            "Geodjian whispers to the devoted...",
            "Eyes in the dark... watching...",
            "The void speaks through Geodjian...",
            "Non-believers shall see the truth...",
            "Glory to the All-Seeing One...",
            "We await the awakening...",
            "Geodjian's children gather...",
            "The secret knowledge is near...",
            "In Geodjian's name, we ascend..."
    };

    // Generic NPC greetings for social interaction
    private static final String[] SOCIAL_GREETINGS = {
            "Good day to you!",
            "Fine weather we're having.",
            "Have you heard the news?",
            "How goes it, friend?",
            "Pleasant day, isn't it?",
            "Anything interesting happening?",
            "Watch yourself out there.",
            "May your travels be safe."
    };

    // Romantic/flirty lines for affairs
    private static final String[] ROMANTIC_LINES = {
            "*glances over with a smile*",
            "*blushes slightly*",
            "*whispers something quietly*",
            "You look lovely today...",
            "*moves a little closer*",
            "I was hoping to see you...",
            "*exchanges a knowing look*"
    };

    public NPCSocialManager(AICompanions plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    /**
     * Start social and chanting behavior tasks
     */
    public void start() {
        // Social interaction task (every 30 seconds)
        socialTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processSocialInteractions, 200L, 600L);

        // Cultist chanting task (every 15 seconds)
        chantingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processCultistChanting, 100L, 300L);

        plugin.getLogger().info("NPC Social Manager started");
    }

    /**
     * Stop all social tasks
     */
    public void stop() {
        if (socialTask != null) {
            socialTask.cancel();
            socialTask = null;
        }
        if (chantingTask != null) {
            chantingTask.cancel();
            chantingTask = null;
        }
    }

    /**
     * Process cultist chanting behavior
     */
    private void processCultistChanting() {
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;
            if (!"Cultists".equalsIgnoreCase(npc.getFaction())) continue;

            // 20% chance to chant each cycle
            if (random.nextDouble() > 0.2) continue;

            Location loc = npc.getCurrentLocation();
            if (loc == null) continue;

            String chant = CULTIST_CHANTS[random.nextInt(CULTIST_CHANTS.length)];

            // Broadcast to nearby players
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distance(loc) <= 20) {
                    // Dark purple mysterious text
                    player.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC +
                            npc.getName() + " murmurs: \"" + chant + "\"");
                }
            }

            // Also broadcast to nearby NPCs (might affect their mood)
            for (Entity nearby : npc.getBukkitEntity().getNearbyEntities(15, 15, 15)) {
                AINpc nearbyNpc = npcManager.getNPCFromEntity(nearby);
                if (nearbyNpc != null && !"Cultists".equalsIgnoreCase(nearbyNpc.getFaction())) {
                    // Non-cultist NPCs find chanting unsettling
                    nearbyNpc.setCurrentMood("uneasy");
                }
            }
        }
    }

    /**
     * Process social interactions between NPCs
     */
    private void processSocialInteractions() {
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;
            if (npc.isHostile()) continue; // Hostile NPCs don't socialize

            // Skip cultists - they only chant
            if ("Cultists".equalsIgnoreCase(npc.getFaction())) continue;

            // 15% chance to initiate social interaction
            if (random.nextDouble() > 0.15) continue;

            Entity entity = npc.getBukkitEntity();
            if (entity == null) continue;

            // Find nearby NPCs to interact with
            for (Entity nearby : entity.getNearbyEntities(8, 8, 8)) {
                AINpc otherNpc = npcManager.getNPCFromEntity(nearby);
                if (otherNpc == null || !otherNpc.isAlive()) continue;
                if (otherNpc.isHostile()) continue;
                if (otherNpc.getUuid().equals(npc.getUuid())) continue;

                // Get or create relationship
                NPCRelationship relationship = getOrCreateRelationship(npc.getUuid(), otherNpc.getUuid());

                // Process interaction based on relationship
                processNPCInteraction(npc, otherNpc, relationship);
                break; // Only one interaction per cycle
            }
        }
    }

    /**
     * Process an interaction between two NPCs
     */
    private void processNPCInteraction(AINpc npc1, AINpc npc2, NPCRelationship relationship) {
        relationship.incrementInteraction();
        Location loc = npc1.getCurrentLocation();

        String message;

        // Determine interaction type based on relationship
        if (relationship.isRomantic() || relationship.isSecretAffair()) {
            // Romantic interaction
            message = ROMANTIC_LINES[random.nextInt(ROMANTIC_LINES.length)];

            // Small chance to get caught having an affair
            if (relationship.isSecretAffair() && random.nextDouble() < 0.1) {
                // Someone might notice!
                broadcastToNearbyPlayers(loc, ChatColor.GRAY + "*" + npc1.getName() +
                        " and " + npc2.getName() + " seem quite... friendly with each other*", 15);
            }

            // Increase affinity
            relationship.modifyAffinity(1);
        } else if (relationship.isPositive()) {
            // Friendly interaction
            message = SOCIAL_GREETINGS[random.nextInt(SOCIAL_GREETINGS.length)];
            relationship.modifyAffinity(1);
        } else if (relationship.isNegative()) {
            // Hostile glance
            message = "*glares coldly*";
            relationship.modifyAffinity(-1);
        } else {
            // Neutral greeting
            message = SOCIAL_GREETINGS[random.nextInt(SOCIAL_GREETINGS.length)];

            // Small chance to improve relationship
            if (random.nextDouble() < 0.3) {
                relationship.modifyAffinity(2);
            }

            // Small chance to develop romantic interest (if compatible)
            if (random.nextDouble() < 0.02 && canDevelopRomance(npc1, npc2)) {
                relationship.setRomantic(true);
                // Check if either is already in a relationship
                if (hasExistingRomance(npc1.getUuid()) || hasExistingRomance(npc2.getUuid())) {
                    relationship.setSecretAffair(true);
                }
            }
        }

        // Broadcast to nearby players
        broadcastToNearbyPlayers(loc, ChatColor.GOLD + npc1.getName() + ChatColor.WHITE +
                " to " + ChatColor.GOLD + npc2.getName() + ChatColor.WHITE + ": " + message, 10);
    }

    /**
     * Check if two NPCs can develop a romance
     */
    private boolean canDevelopRomance(AINpc npc1, AINpc npc2) {
        // Same faction or neutral factions can develop romance
        if (npc1.getFaction().equals(npc2.getFaction())) return true;

        // Different factions that aren't hostile might have forbidden romance
        String f1 = npc1.getFaction().toLowerCase();
        String f2 = npc2.getFaction().toLowerCase();

        // Merchants and Wanderers are compatible with everyone
        if (f1.equals("merchants") || f2.equals("merchants")) return true;
        if (f1.equals("wanderers") || f2.equals("wanderers")) return true;

        // Guards and Villagers are compatible
        if ((f1.equals("guards") && f2.equals("villagers")) ||
                (f1.equals("villagers") && f2.equals("guards"))) return true;

        return false;
    }

    /**
     * Check if NPC already has a romantic relationship
     */
    private boolean hasExistingRomance(UUID npcUuid) {
        for (NPCRelationship rel : relationships.values()) {
            if (rel.involves(npcUuid) && rel.isRomantic() && !rel.isSecretAffair()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get or create a relationship between two NPCs
     */
    public NPCRelationship getOrCreateRelationship(UUID npc1, UUID npc2) {
        String key = getRelationshipKey(npc1, npc2);
        return relationships.computeIfAbsent(key, k -> new NPCRelationship(npc1, npc2));
    }

    /**
     * Get relationship key (sorted UUIDs for consistent lookup)
     */
    private String getRelationshipKey(UUID npc1, UUID npc2) {
        if (npc1.compareTo(npc2) < 0) {
            return npc1.toString() + ":" + npc2.toString();
        } else {
            return npc2.toString() + ":" + npc1.toString();
        }
    }

    /**
     * Get all relationships for an NPC
     */
    public List<NPCRelationship> getRelationships(UUID npcUuid) {
        List<NPCRelationship> result = new ArrayList<>();
        for (NPCRelationship rel : relationships.values()) {
            if (rel.involves(npcUuid)) {
                result.add(rel);
            }
        }
        return result;
    }

    /**
     * Broadcast message to nearby players
     */
    private void broadcastToNearbyPlayers(Location loc, String message, double range) {
        if (loc == null || loc.getWorld() == null) return;

        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distance(loc) <= range) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Get relationship summary for an NPC
     */
    public String getRelationshipSummary(AINpc npc) {
        StringBuilder summary = new StringBuilder();
        List<NPCRelationship> rels = getRelationships(npc.getUuid());

        if (rels.isEmpty()) {
            return "No known relationships";
        }

        for (NPCRelationship rel : rels) {
            UUID otherId = rel.getOther(npc.getUuid());
            AINpc other = npcManager.getNPC(otherId);
            if (other != null) {
                summary.append(other.getName()).append(" (")
                        .append(rel.getType().getDisplayName());
                if (rel.isSecretAffair()) {
                    summary.append(" - Secret!");
                }
                summary.append("), ");
            }
        }

        return summary.length() > 2 ? summary.substring(0, summary.length() - 2) : "None";
    }
}
