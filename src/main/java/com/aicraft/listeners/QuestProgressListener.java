package com.aicraft.listeners;

import com.aicraft.AICompanions;
import com.aicraft.quests.Quest;
import com.aicraft.quests.QuestManager;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Tracks quest progress for players
 * Listens to game events and updates quest progress accordingly
 */
public class QuestProgressListener implements Listener {

    private final AICompanions plugin;
    private final QuestManager questManager;

    public QuestProgressListener(AICompanions plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    /**
     * Track kill quests - when player kills a mob
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        // Get the entity type name (e.g., "ZOMBIE", "SKELETON")
        String entityType = entity.getType().name();

        // Update kill quest progress
        questManager.updateProgress(killer, "kill", entityType, 1);

        plugin.debug("Quest progress: " + killer.getName() + " killed " + entityType);
    }

    /**
     * Track fetch quests - when player picks up items
     */
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack item = event.getItem().getItemStack();
        String itemType = item.getType().name();
        int amount = item.getAmount();

        // Update fetch quest progress
        questManager.updateProgress(player, "fetch", itemType, amount);

        plugin.debug("Quest progress: " + player.getName() + " picked up " + amount + " " + itemType);
    }

    /**
     * Track fetch quests - also check inventory for items player already has
     * This runs when player clicks in inventory (crafting, moving items, etc.)
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Delay check to after the inventory action completes
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            checkInventoryForQuests(player);
        }, 1L);
    }

    /**
     * Check if player has items needed for fetch quests
     */
    private void checkInventoryForQuests(Player player) {
        for (Quest quest : questManager.getActiveQuests(player.getUniqueId())) {
            if (!quest.getQuestType().equalsIgnoreCase("fetch")) continue;
            if (quest.getStatus() != Quest.QuestStatus.ACTIVE) continue;
            if (quest.getTarget() == null || quest.getTarget().isEmpty()) continue;

            try {
                Material targetMaterial = Material.valueOf(quest.getTarget().toUpperCase());
                int count = countItems(player, targetMaterial);

                // Update progress to match inventory count (don't go backwards)
                if (count > quest.getProgress()) {
                    int gained = count - quest.getProgress();
                    quest.incrementProgress(gained);

                    if (quest.isComplete()) {
                        player.sendMessage("§a[Quest] §6" + quest.getTitle() + " §acomplete! Return to the quest giver.");
                    }
                }
            } catch (IllegalArgumentException e) {
                // Invalid material name, skip
            }
        }
    }

    /**
     * Count how many of an item a player has
     */
    private int countItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }
}
