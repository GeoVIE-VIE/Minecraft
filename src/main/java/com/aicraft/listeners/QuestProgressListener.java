package com.aicraft.listeners;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.quests.Quest;
import com.aicraft.quests.QuestManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Tracks quest progress for players
 * Listens to game events and updates quest progress accordingly:
 * - Kill quests: Entity deaths
 * - Fetch quests: Item pickups and inventory checks
 * - Build quests: Block placement in quest areas
 * - Explore quests: Player reaching target locations
 */
public class QuestProgressListener implements Listener {

    private final AICompanions plugin;
    private final QuestManager questManager;

    // Track blocks placed for build quests (Player UUID -> Quest UUID -> count)
    private final Map<UUID, Map<UUID, Set<Location>>> buildQuestBlocks = new HashMap<>();

    // Cooldown for movement checks to prevent spam
    private final Map<UUID, Long> movementCooldown = new HashMap<>();
    private static final long MOVEMENT_CHECK_INTERVAL = 2000; // 2 seconds

    public QuestProgressListener(AICompanions plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    /**
     * Track kill quests - when player kills a mob
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        // Get the entity type name (e.g., "ZOMBIE", "SKELETON")
        String entityType = entity.getType().name();

        // Check each active kill quest
        for (Quest quest : questManager.getActiveQuests(killer.getUniqueId())) {
            if (!"kill".equalsIgnoreCase(quest.getQuestType())) continue;
            if (quest.getStatus() != Quest.QuestStatus.ACTIVE) continue;

            String target = quest.getTarget();
            if (target == null) continue;

            // Check if this kill matches the quest target
            if (entityType.equalsIgnoreCase(target) ||
                    target.equalsIgnoreCase("any") ||
                    matchesEntityCategory(entity, target)) {

                int oldProgress = quest.getProgress();
                quest.incrementProgress(1);

                if (quest.isComplete()) {
                    showQuestComplete(killer, quest);
                } else {
                    killer.sendMessage(ChatColor.GRAY + "[" + quest.getTitle() + "] " +
                            ChatColor.WHITE + quest.getProgressString() + " " +
                            formatEntityName(target) + " defeated");
                }
            }
        }

        plugin.debug("Quest progress: " + killer.getName() + " killed " + entityType);
    }

    /**
     * Track fetch quests - when player picks up items
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack item = event.getItem().getItemStack();
        String itemType = item.getType().name();
        int amount = item.getAmount();

        updateFetchProgress(player, itemType, amount);
        plugin.debug("Quest progress: " + player.getName() + " picked up " + amount + " " + itemType);
    }

    /**
     * Track crafting for fetch quests
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe().getResult();
        String itemType = result.getType().name();
        int amount = result.getAmount();

        // Schedule check after crafting completes
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            updateFetchProgress(player, itemType, amount);
        }, 1L);
    }

    /**
     * Update fetch quest progress
     */
    private void updateFetchProgress(Player player, String itemType, int amount) {
        for (Quest quest : questManager.getActiveQuests(player.getUniqueId())) {
            if (!"fetch".equalsIgnoreCase(quest.getQuestType())) continue;
            if (quest.getStatus() != Quest.QuestStatus.ACTIVE) continue;

            String target = quest.getTarget();
            if (target == null || !itemType.equalsIgnoreCase(target)) continue;

            int oldProgress = quest.getProgress();
            quest.incrementProgress(amount);

            if (quest.isComplete()) {
                showQuestComplete(player, quest);
            } else if (quest.getProgress() > oldProgress) {
                player.sendMessage(ChatColor.GRAY + "[" + quest.getTitle() + "] " +
                        ChatColor.WHITE + quest.getProgressString() + " " +
                        formatItemName(target) + " collected");
            }
        }
    }

    /**
     * Track inventory changes for fetch quests
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
                        showQuestComplete(player, quest);
                    }
                }
            } catch (IllegalArgumentException e) {
                // Invalid material name, skip
            }
        }
    }

    /**
     * Track block placement for build quests
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        for (Quest quest : questManager.getActiveQuests(player.getUniqueId())) {
            if (!"build".equalsIgnoreCase(quest.getQuestType())) continue;
            if (quest.getStatus() != Quest.QuestStatus.ACTIVE) continue;

            // Check if block is placed in quest area
            Location targetLoc = quest.getTargetLocation(plugin.getServer());
            if (targetLoc == null) {
                // No target location - count any blocks as progress
                trackBuildProgress(player, quest, block);
                continue;
            }

            // Check distance to quest area
            double distance = block.getLocation().distance(targetLoc);
            double buildRadius = plugin.getConfig().getDouble("quests.build-radius", 25.0);

            if (distance <= buildRadius) {
                // Check if block type matches target (if specified)
                String target = quest.getTarget();
                if (target != null && !target.isEmpty() && !target.equalsIgnoreCase("any")) {
                    if (!block.getType().name().equalsIgnoreCase(target) &&
                            !matchesBuildCategory(block.getType(), target)) {
                        continue;
                    }
                }

                trackBuildProgress(player, quest, block);
            }
        }
    }

    /**
     * Track build progress for a quest
     */
    private void trackBuildProgress(Player player, Quest quest, Block block) {
        // Track this block location
        buildQuestBlocks
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .computeIfAbsent(quest.getUuid(), k -> new HashSet<>())
                .add(block.getLocation());

        int oldProgress = quest.getProgress();
        quest.incrementProgress(1);

        if (quest.isComplete()) {
            showQuestComplete(player, quest);
        } else if (quest.getProgress() % 5 == 0 || quest.getProgress() == 1) {
            // Show progress every 5 blocks to avoid spam
            player.sendMessage(ChatColor.GRAY + "[" + quest.getTitle() + "] " +
                    ChatColor.WHITE + quest.getProgressString() +
                    " blocks placed");
        }
    }

    /**
     * Track player movement for explore quests
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check if player moved to a new block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        // Cooldown to prevent spam checks
        long now = System.currentTimeMillis();
        Long lastCheck = movementCooldown.get(player.getUniqueId());
        if (lastCheck != null && now - lastCheck < MOVEMENT_CHECK_INTERVAL) {
            return;
        }
        movementCooldown.put(player.getUniqueId(), now);

        for (Quest quest : questManager.getActiveQuests(player.getUniqueId())) {
            if (!"explore".equalsIgnoreCase(quest.getQuestType())) continue;
            if (quest.getStatus() != Quest.QuestStatus.ACTIVE) continue;

            Location targetLoc = quest.getTargetLocation(plugin.getServer());
            if (targetLoc == null) continue;

            double distance = player.getLocation().distance(targetLoc);
            double exploreRadius = plugin.getConfig().getDouble("quests.explore-radius", 15.0);

            if (distance <= exploreRadius) {
                // Player reached the location!
                quest.incrementProgress(quest.getAmount()); // Complete fully

                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "✓ " + ChatColor.WHITE + "Location discovered!");
                showQuestComplete(player, quest);
            } else if (distance <= exploreRadius * 2.5 && distance > exploreRadius * 2) {
                // Getting close - give hint (only once when entering "close" zone)
                player.sendMessage(ChatColor.AQUA + "⚑ Getting close to your objective! (" +
                        (int) distance + " blocks away)");
            }
        }
    }

    /**
     * Show quest complete message with turn-in info
     */
    private void showQuestComplete(Player player, Quest quest) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✓ Quest objective complete: " +
                ChatColor.GOLD + quest.getTitle());

        // Get quest giver info
        String giverName = "the quest giver";
        if (quest.getNpcUuid() != null) {
            AINpc npc = plugin.getNPCManager().getNPC(quest.getNpcUuid());
            if (npc != null) {
                giverName = npc.getName();
            }
        }

        player.sendMessage(ChatColor.GRAY + "Return to " + ChatColor.WHITE + giverName +
                ChatColor.GRAY + " to claim your reward!");

        // Show waypoint info
        Location turnIn = quest.getTurnInLocation(plugin.getServer());
        if (turnIn != null && turnIn.getWorld().equals(player.getWorld())) {
            double distance = player.getLocation().distance(turnIn);
            String direction = quest.getDirectionToWaypoint(player.getLocation(), plugin.getServer());

            player.sendMessage(ChatColor.AQUA + "⚑ " + (int) distance + " blocks " + direction);
            player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.WHITE + "/quest track" +
                    ChatColor.GRAY + " to set compass waypoint");

            // Auto-set compass to turn-in location
            player.setCompassTarget(turnIn);
        }
        player.sendMessage("");
    }

    /**
     * Check if entity matches a category target
     */
    private boolean matchesEntityCategory(LivingEntity entity, String target) {
        String lowerTarget = target.toLowerCase();

        return switch (lowerTarget) {
            case "hostile", "monster", "monsters" -> entity instanceof Monster;
            case "undead" -> entity instanceof Zombie || entity instanceof Skeleton ||
                    entity instanceof Wither || entity instanceof Phantom;
            case "animal", "animals" -> entity instanceof Animals;
            case "arthropod", "arthropods", "bug", "bugs" ->
                    entity instanceof Spider || entity instanceof Silverfish ||
                            entity instanceof Endermite || entity instanceof Bee;
            default -> false;
        };
    }

    /**
     * Check if block matches a build category
     */
    private boolean matchesBuildCategory(Material material, String target) {
        String lowerTarget = target.toLowerCase();
        String matName = material.name().toLowerCase();

        return switch (lowerTarget) {
            case "fence", "fences" -> matName.contains("fence");
            case "wall", "walls" -> matName.contains("wall") || matName.contains("brick");
            case "wood", "wooden" -> matName.contains("wood") || matName.contains("log") ||
                    matName.contains("plank");
            case "stone" -> matName.contains("stone") || matName.contains("cobble");
            case "glass" -> matName.contains("glass");
            case "floor", "flooring" -> matName.contains("slab") || matName.contains("plank") ||
                    matName.contains("stone") || matName.contains("tile");
            case "door", "doors" -> matName.contains("door");
            case "stairs" -> matName.contains("stairs");
            default -> false;
        };
    }

    /**
     * Format entity name for display
     */
    private String formatEntityName(String entityType) {
        if (entityType == null) return "creatures";
        String formatted = entityType.toLowerCase().replace('_', ' ');
        // Add 's' for plural if doesn't already end in 's'
        if (!formatted.endsWith("s")) {
            formatted += "s";
        }
        return formatted;
    }

    /**
     * Format item name for display
     */
    private String formatItemName(String itemType) {
        if (itemType == null) return "items";
        return itemType.toLowerCase().replace('_', ' ');
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

    /**
     * Get blocks placed for a build quest (for verification/cleanup)
     */
    public Set<Location> getBuildQuestBlocks(UUID playerUuid, UUID questUuid) {
        Map<UUID, Set<Location>> playerBlocks = buildQuestBlocks.get(playerUuid);
        if (playerBlocks == null) return Collections.emptySet();
        return playerBlocks.getOrDefault(questUuid, Collections.emptySet());
    }

    /**
     * Clear tracked build blocks when quest is completed
     */
    public void clearBuildQuestBlocks(UUID playerUuid, UUID questUuid) {
        Map<UUID, Set<Location>> playerBlocks = buildQuestBlocks.get(playerUuid);
        if (playerBlocks != null) {
            playerBlocks.remove(questUuid);
        }
    }

    /**
     * Save quest state when player quits
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        savePlayerQuests(player);

        // Clean up cooldown tracking
        movementCooldown.remove(player.getUniqueId());
        buildQuestBlocks.remove(player.getUniqueId());

        plugin.debug("Saved quest state for disconnecting player: " + player.getName());
    }

    /**
     * Load quest state when player joins
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load quests from database (this is handled by QuestManager.getActiveQuests)
        List<Quest> quests = questManager.getActiveQuests(player.getUniqueId());

        if (!quests.isEmpty()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(ChatColor.AQUA + "You have " + quests.size() + " active quest(s).");
                player.sendMessage(ChatColor.GRAY + "Use /quest list to view them.");
            }, 40L); // 2 second delay after join
        }

        plugin.debug("Loaded " + quests.size() + " quests for joining player: " + player.getName());
    }

    /**
     * Save quest state when player dies (before respawn)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        savePlayerQuests(player);
        plugin.debug("Saved quest state for dying player: " + player.getName());
    }

    /**
     * Notify player of quests when they respawn
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            List<Quest> quests = questManager.getActiveQuests(player.getUniqueId());

            // Check for READY_TO_TURN_IN quests
            long completedCount = quests.stream()
                    .filter(q -> q.getStatus() == Quest.QuestStatus.READY_TO_TURN_IN)
                    .count();

            if (completedCount > 0) {
                player.sendMessage(ChatColor.GREEN + "Reminder: You have " + completedCount +
                        " completed quest(s) ready to turn in!");
            }
        }, 20L); // 1 second after respawn
    }

    /**
     * Save quest state when player changes world
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        savePlayerQuests(player);
        plugin.debug("Saved quest state for world change: " + player.getName() +
                " from " + event.getFrom().getName() + " to " + player.getWorld().getName());
    }

    /**
     * Save all active quests for a player to the database
     */
    private void savePlayerQuests(Player player) {
        List<Quest> quests = questManager.getActiveQuests(player.getUniqueId());
        for (Quest quest : quests) {
            plugin.getDatabaseManager().saveQuest(quest);
        }
    }
}
