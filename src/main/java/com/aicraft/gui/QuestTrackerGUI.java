package com.aicraft.gui;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import com.aicraft.quests.Quest;
import com.aicraft.quests.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI for tracking quests with detailed information and waypoint navigation
 */
public class QuestTrackerGUI implements Listener, InventoryHolder {

    private final AICompanions plugin;
    private final QuestManager questManager;
    private final NPCManager npcManager;

    private static final String GUI_TITLE = ChatColor.GOLD + "Quest Journal";
    private static final int GUI_SIZE = 54; // 6 rows

    // Track which quest each slot represents
    private final Map<UUID, Map<Integer, Quest>> playerQuestSlots = new HashMap<>();

    // Track selected quest for waypoint
    private final Map<UUID, Quest> trackedQuests = new HashMap<>();

    public QuestTrackerGUI(AICompanions plugin, QuestManager questManager, NPCManager npcManager) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.npcManager = npcManager;

        // Register events
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    /**
     * Open the quest tracker GUI for a player
     */
    public void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(this, GUI_SIZE, GUI_TITLE);

        // Get player's quests
        List<Quest> quests = questManager.getActiveQuests(player.getUniqueId());
        Map<Integer, Quest> questSlots = new HashMap<>();

        // Header decoration
        ItemStack headerPane = createItem(Material.GOLD_INGOT, ChatColor.GOLD + "═══ Your Quests ═══",
                Arrays.asList(
                        ChatColor.GRAY + "Click a quest for details",
                        ChatColor.GRAY + "Right-click to track waypoint"
                ));
        gui.setItem(4, headerPane);

        // Fill top row decoration
        ItemStack glassPane = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 9; i++) {
            if (i != 4) gui.setItem(i, glassPane);
        }

        // Quest slots (rows 2-5, slots 9-44)
        int slot = 10;
        for (Quest quest : quests) {
            if (slot >= 44) break; // Max quests
            if (slot % 9 == 0) slot++; // Skip first column
            if (slot % 9 == 8) slot += 2; // Skip last column

            ItemStack questItem = createQuestItem(quest, player);
            gui.setItem(slot, questItem);
            questSlots.put(slot, quest);
            slot++;
        }

        // Bottom row - controls
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, glassPane);
        }

        // Info button
        ItemStack infoItem = createItem(Material.BOOK, ChatColor.YELLOW + "Quest Help",
                Arrays.asList(
                        ChatColor.GRAY + "Talk to NPCs using " + ChatColor.WHITE + "@message",
                        ChatColor.GRAY + "Ask for 'quests' or 'jobs'",
                        "",
                        ChatColor.GOLD + "Left-click" + ChatColor.GRAY + " = View details",
                        ChatColor.GOLD + "Right-click" + ChatColor.GRAY + " = Track quest"
                ));
        gui.setItem(45, infoItem);

        // Currently tracked quest indicator
        Quest tracked = trackedQuests.get(player.getUniqueId());
        if (tracked != null) {
            ItemStack trackedItem = createItem(Material.COMPASS, ChatColor.GREEN + "Tracking: " + tracked.getTitle(),
                    Arrays.asList(
                            ChatColor.GRAY + "Waypoint active",
                            ChatColor.GRAY + "Check your compass!"
                    ));
            gui.setItem(49, trackedItem);
        } else {
            ItemStack noTrackItem = createItem(Material.COMPASS, ChatColor.GRAY + "No Quest Tracked",
                    Collections.singletonList(ChatColor.GRAY + "Right-click a quest to track"));
            gui.setItem(49, noTrackItem);
        }

        // Close button
        ItemStack closeItem = createItem(Material.BARRIER, ChatColor.RED + "Close", null);
        gui.setItem(53, closeItem);

        // Store quest slots for this player
        playerQuestSlots.put(player.getUniqueId(), questSlots);

        player.openInventory(gui);
    }

    /**
     * Create an ItemStack representing a quest
     */
    private ItemStack createQuestItem(Quest quest, Player player) {
        Material material;
        ChatColor titleColor;

        // Choose material and color based on status
        switch (quest.getStatus()) {
            case READY_TO_TURN_IN:
                material = Material.EMERALD;
                titleColor = ChatColor.GREEN;
                break;
            case ACTIVE:
            default:
                material = getMaterialForQuestType(quest.getQuestType());
                titleColor = ChatColor.GOLD;
                break;
        }

        List<String> lore = new ArrayList<>();

        // Status line
        if (quest.getStatus() == Quest.QuestStatus.READY_TO_TURN_IN) {
            lore.add(ChatColor.GREEN + "✓ Ready to turn in!");
        } else {
            lore.add(ChatColor.YELLOW + "● In Progress");
        }

        lore.add("");

        // Description
        String desc = quest.getDescription();
        if (desc != null && desc.length() > 40) {
            // Word wrap
            lore.add(ChatColor.WHITE + desc.substring(0, 40) + "...");
        } else if (desc != null) {
            lore.add(ChatColor.WHITE + desc);
        }

        lore.add("");

        // Objective
        lore.add(ChatColor.YELLOW + "Objective:");
        lore.add(ChatColor.GRAY + "  " + quest.getObjective());

        // Progress bar
        lore.add("");
        int barLength = 15;
        int filled = (int) (quest.getProgressPercentage() / 100 * barLength);
        String progressBar = ChatColor.GREEN + "█".repeat(Math.max(0, filled)) +
                ChatColor.GRAY + "░".repeat(Math.max(0, barLength - filled));
        lore.add(ChatColor.GRAY + "Progress: " + progressBar + " " +
                ChatColor.WHITE + quest.getProgressString());

        // Location/Direction info
        Location waypoint = quest.getWaypointLocation(plugin.getServer());
        if (waypoint != null) {
            double distance = quest.getDistanceToWaypoint(player.getLocation(), plugin.getServer());
            String direction = quest.getDirectionToWaypoint(player.getLocation(), plugin.getServer());

            lore.add("");
            if (quest.getStatus() == Quest.QuestStatus.READY_TO_TURN_IN) {
                lore.add(ChatColor.AQUA + "⚑ Return to quest giver");
            } else {
                lore.add(ChatColor.AQUA + "⚑ Quest Location:");
            }
            if (distance >= 0) {
                lore.add(ChatColor.GRAY + "  " + (int) distance + " blocks " + direction);
            }
        }

        // Rewards
        lore.add("");
        lore.add(ChatColor.GOLD + "Rewards:");
        lore.add(ChatColor.YELLOW + "  +" + quest.getRewardXp() + " XP");
        for (String item : quest.getRewardItems()) {
            lore.add(ChatColor.AQUA + "  +" + formatItemName(item));
        }

        // Quest giver
        if (quest.getNpcUuid() != null) {
            AINpc npc = npcManager.getNPC(quest.getNpcUuid());
            if (npc != null) {
                lore.add("");
                lore.add(ChatColor.GRAY + "From: " + ChatColor.WHITE + npc.getName());
            }
        }

        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Right-click to track waypoint");

        return createItem(material, titleColor + quest.getTitle(), lore);
    }

    /**
     * Get appropriate material for quest type
     */
    private Material getMaterialForQuestType(String type) {
        if (type == null) return Material.PAPER;
        return switch (type.toLowerCase()) {
            case "kill" -> Material.IRON_SWORD;
            case "fetch" -> Material.CHEST;
            case "explore" -> Material.FILLED_MAP;
            case "delivery" -> Material.ENDER_PEARL;
            case "escort" -> Material.LEAD;
            default -> Material.PAPER;
        };
    }

    /**
     * Create a simple ItemStack with name and lore
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Format item name for display
     */
    private String formatItemName(String itemName) {
        if (itemName == null) return "Unknown";
        return itemName.toLowerCase().replace('_', ' ');
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof QuestTrackerGUI)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= GUI_SIZE) return;

        // Close button
        if (slot == 53) {
            player.closeInventory();
            return;
        }

        // Check if clicked on a quest
        Map<Integer, Quest> questSlots = playerQuestSlots.get(player.getUniqueId());
        if (questSlots == null) return;

        Quest quest = questSlots.get(slot);
        if (quest == null) return;

        if (event.isRightClick()) {
            // Track quest waypoint
            trackQuest(player, quest);
            player.closeInventory();
        } else {
            // Show detailed quest info
            showQuestDetails(player, quest);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof QuestTrackerGUI) {
            playerQuestSlots.remove(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Track a quest and set compass waypoint
     */
    public void trackQuest(Player player, Quest quest) {
        trackedQuests.put(player.getUniqueId(), quest);

        Location waypoint = quest.getWaypointLocation(plugin.getServer());
        if (waypoint != null) {
            player.setCompassTarget(waypoint);
            player.sendMessage(ChatColor.GREEN + "Now tracking: " + ChatColor.GOLD + quest.getTitle());

            double distance = quest.getDistanceToWaypoint(player.getLocation(), plugin.getServer());
            String direction = quest.getDirectionToWaypoint(player.getLocation(), plugin.getServer());

            if (distance >= 0) {
                player.sendMessage(ChatColor.GRAY + "Distance: " + (int) distance + " blocks " + direction);
                player.sendMessage(ChatColor.GRAY + "Your compass now points to the quest location!");
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "Tracking: " + ChatColor.GOLD + quest.getTitle());
            player.sendMessage(ChatColor.GRAY + "No specific location for this quest.");
        }
    }

    /**
     * Show detailed quest information
     */
    private void showQuestDetails(Player player, Quest quest) {
        player.closeInventory();

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.GOLD + "  " + quest.getTitle());
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage("");

        // Status
        ChatColor statusColor = quest.getStatus() == Quest.QuestStatus.READY_TO_TURN_IN ?
                ChatColor.GREEN : ChatColor.YELLOW;
        player.sendMessage(statusColor + "Status: " + quest.getStatus().toString().replace('_', ' '));
        player.sendMessage("");

        // Description
        player.sendMessage(ChatColor.WHITE + quest.getDescription());
        player.sendMessage("");

        // Objective
        player.sendMessage(ChatColor.YELLOW + "Objective: " + ChatColor.WHITE + quest.getObjective());

        // Progress
        int barLength = 20;
        int filled = (int) (quest.getProgressPercentage() / 100 * barLength);
        String progressBar = ChatColor.GREEN + "█".repeat(Math.max(0, filled)) +
                ChatColor.GRAY + "░".repeat(Math.max(0, barLength - filled));
        player.sendMessage(ChatColor.GRAY + "Progress: " + progressBar + " " +
                ChatColor.WHITE + quest.getProgressString() +
                ChatColor.GRAY + " (" + String.format("%.0f%%", quest.getProgressPercentage()) + ")");
        player.sendMessage("");

        // Location
        Location waypoint = quest.getWaypointLocation(plugin.getServer());
        if (waypoint != null) {
            double distance = quest.getDistanceToWaypoint(player.getLocation(), plugin.getServer());
            String direction = quest.getDirectionToWaypoint(player.getLocation(), plugin.getServer());

            player.sendMessage(ChatColor.AQUA + "⚑ Waypoint:");
            if (distance >= 0) {
                player.sendMessage(ChatColor.GRAY + "  Distance: " + ChatColor.WHITE + (int) distance + " blocks");
                player.sendMessage(ChatColor.GRAY + "  Direction: " + ChatColor.WHITE + direction);
                player.sendMessage(ChatColor.GRAY + "  Coordinates: " + ChatColor.WHITE +
                        String.format("%.0f, %.0f, %.0f", waypoint.getX(), waypoint.getY(), waypoint.getZ()));
            }
            player.sendMessage("");
        }

        // Rewards
        player.sendMessage(ChatColor.GOLD + "Rewards:");
        player.sendMessage(ChatColor.GREEN + "  +" + quest.getRewardXp() + " XP");
        for (String item : quest.getRewardItems()) {
            player.sendMessage(ChatColor.AQUA + "  +1 " + formatItemName(item));
        }

        // Quest giver
        if (quest.getNpcUuid() != null) {
            AINpc npc = npcManager.getNPC(quest.getNpcUuid());
            if (npc != null) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Quest Giver: " + ChatColor.WHITE + npc.getName() +
                        ChatColor.GRAY + " (" + npc.getFaction() + ")");
            }
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.GRAY + "Use /quests to open the quest journal");
    }

    /**
     * Get the currently tracked quest for a player
     */
    public Quest getTrackedQuest(UUID playerId) {
        return trackedQuests.get(playerId);
    }

    /**
     * Stop tracking a quest
     */
    public void stopTracking(UUID playerId) {
        trackedQuests.remove(playerId);
    }

    /**
     * Update compass target for tracked quests
     * Called periodically to keep waypoints accurate
     */
    public void updateTrackedWaypoints() {
        for (Map.Entry<UUID, Quest> entry : trackedQuests.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            Quest quest = entry.getValue();
            Location waypoint = quest.getWaypointLocation(plugin.getServer());
            if (waypoint != null) {
                player.setCompassTarget(waypoint);
            }
        }
    }
}
