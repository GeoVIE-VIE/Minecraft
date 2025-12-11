package com.aicraft.listeners;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import com.aicraft.npcs.social.NPCRelationship;
import com.aicraft.npcs.social.NPCSocialManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.List;

/**
 * Handles player interactions with NPCs (right-click)
 * Right-clicking selects the NPC for conversation
 */
public class PlayerInteractListener implements Listener {

    private final AICompanions plugin;
    private final NPCManager npcManager;

    public PlayerInteractListener(AICompanions plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        Player player = event.getPlayer();

        // Check if it's an NPC
        AINpc npc = npcManager.getNPCFromEntity(clicked);
        if (npc == null) return;

        event.setCancelled(true);

        if (!npc.isAlive()) {
            player.sendMessage(ChatColor.GRAY + "*" + npc.getName() + " lies motionless...*");
            return;
        }

        // Show NPC info and SELECT them for conversation
        showNPCInfo(player, npc);

        // Select this NPC for conversation
        ChatListener chatListener = plugin.getChatListener();
        if (chatListener != null) {
            chatListener.selectNPC(player, npc);
        }
    }

    /**
     * Display NPC information to player
     */
    private void showNPCInfo(Player player, AINpc npc) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════ " + npc.getName() + " ═══════");
        player.sendMessage(ChatColor.GRAY + "Faction: " + ChatColor.WHITE + npc.getFaction());
        player.sendMessage(ChatColor.GRAY + "Dialect: " + ChatColor.WHITE + npc.getDialectName());

        if (npc.getPersonality() != null) {
            String shortPersonality = npc.getPersonality();
            if (shortPersonality.length() > 50) {
                shortPersonality = shortPersonality.substring(0, 47) + "...";
            }
            player.sendMessage(ChatColor.GRAY + "Demeanor: " + ChatColor.WHITE + shortPersonality);
        }

        if (npc.getCurrentMood() != null && !npc.getCurrentMood().equals("neutral")) {
            player.sendMessage(ChatColor.GRAY + "Mood: " + ChatColor.WHITE + npc.getCurrentMood());
        }

        // Show relationships if social manager exists
        NPCSocialManager socialManager = plugin.getSocialManager();
        if (socialManager != null) {
            List<NPCRelationship> relationships = socialManager.getRelationships(npc.getUuid());
            if (!relationships.isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "Relationships: " +
                        ChatColor.WHITE + socialManager.getRelationshipSummary(npc));
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✓ " + npc.getName() + " selected!");
        player.sendMessage(ChatColor.YELLOW + "Just type to talk, or use " +
                ChatColor.WHITE + "@<message>");

        if (npc.canGiveQuests()) {
            player.sendMessage(ChatColor.GREEN + "This NPC may have quests available.");
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════");
    }
}
