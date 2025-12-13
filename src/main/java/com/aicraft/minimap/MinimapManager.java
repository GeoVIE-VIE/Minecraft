package com.aicraft.minimap;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import com.aicraft.quests.Quest;
import com.aicraft.quests.QuestManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages minimaps for all players
 */
public class MinimapManager {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final QuestManager questManager;

    // Track player minimaps
    private final Map<UUID, MapView> playerMaps = new ConcurrentHashMap<>();
    private final Map<UUID, MinimapSettings> playerSettings = new ConcurrentHashMap<>();

    // Waypoints per player
    private final Map<UUID, List<Waypoint>> playerWaypoints = new ConcurrentHashMap<>();

    // Global waypoints (settlements, etc.)
    private final List<Waypoint> globalWaypoints = Collections.synchronizedList(new ArrayList<>());

    private BukkitTask updateTask;
    private BukkitTask waypointCleanupTask;

    public MinimapManager(AICompanions plugin, NPCManager npcManager, QuestManager questManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.questManager = questManager;
    }

    /**
     * Start the minimap system
     */
    public void start() {
        // Update task - refreshes maps periodically
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllMaps, 20L, 10L);

        // Waypoint cleanup - removes temporary waypoints when players reach them
        waypointCleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupWaypoints, 40L, 40L);

        plugin.getLogger().info("Minimap Manager started");
    }

    /**
     * Stop the minimap system
     */
    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        if (waypointCleanupTask != null) {
            waypointCleanupTask.cancel();
            waypointCleanupTask = null;
        }
    }

    /**
     * Give a player a minimap item
     */
    public void giveMinimapToPlayer(Player player, boolean circular) {
        UUID uuid = player.getUniqueId();

        // Create or get existing map
        MapView mapView = playerMaps.get(uuid);
        if (mapView == null) {
            mapView = Bukkit.createMap(player.getWorld());
            mapView.setScale(MapView.Scale.CLOSEST);
            mapView.setTrackingPosition(false);
            mapView.setUnlimitedTracking(false);

            // Remove default renderers
            for (org.bukkit.map.MapRenderer renderer : mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }

            // Add our custom renderer
            int radius = plugin.getConfig().getInt("minimap.radius", 64);
            mapView.addRenderer(new MinimapRenderer(this, circular, radius));

            playerMaps.put(uuid, mapView);
        }

        // Save settings
        playerSettings.put(uuid, new MinimapSettings(circular));

        // Create map item
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        if (meta != null) {
            meta.setMapView(mapView);
            meta.setDisplayName(ChatColor.GOLD + "Minimap" + (circular ? " (Circular)" : " (Square)"));
            meta.setLore(Arrays.asList(
                ChatColor.GRAY + "Shows in corner when in offhand",
                ChatColor.GRAY + "Displays NPCs, players, and quests",
                ChatColor.YELLOW + "Use /minimap toggle to change style"
            ));
            mapItem.setItemMeta(meta);
        }

        // Put in offhand for HUD display, or give to inventory if offhand is full
        ItemStack currentOffhand = player.getInventory().getItemInOffHand();
        if (currentOffhand == null || currentOffhand.getType() == Material.AIR) {
            player.getInventory().setItemInOffHand(mapItem);
            player.sendMessage(ChatColor.GREEN + "Minimap equipped in offhand - it will show in the corner of your screen!");
        } else {
            player.getInventory().addItem(mapItem);
            player.sendMessage(ChatColor.GREEN + "You received a minimap! Put it in your offhand (press F) to see it on screen.");
        }
    }

    /**
     * Toggle between circular and square minimap
     */
    public void toggleMinimapStyle(Player player) {
        UUID uuid = player.getUniqueId();
        MinimapSettings settings = playerSettings.get(uuid);

        if (settings == null) {
            player.sendMessage(ChatColor.RED + "You don't have a minimap! Use /minimap to get one.");
            return;
        }

        boolean newCircular = !settings.isCircular();
        playerSettings.put(uuid, new MinimapSettings(newCircular));

        // Update the renderer
        MapView mapView = playerMaps.get(uuid);
        if (mapView != null) {
            for (org.bukkit.map.MapRenderer renderer : mapView.getRenderers()) {
                mapView.removeRenderer(renderer);
            }

            int radius = plugin.getConfig().getInt("minimap.radius", 64);
            mapView.addRenderer(new MinimapRenderer(this, newCircular, radius));
        }

        player.sendMessage(ChatColor.GREEN + "Minimap style changed to: " +
            ChatColor.GOLD + (newCircular ? "Circular" : "Square"));
    }

    /**
     * Update all player maps
     */
    private void updateAllMaps() {
        // Maps auto-update through the renderer
        // This task handles waypoint updates from quests
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateQuestWaypoints(player);
        }
    }

    /**
     * Update quest waypoints for a player
     */
    private void updateQuestWaypoints(Player player) {
        UUID uuid = player.getUniqueId();
        List<Waypoint> waypoints = playerWaypoints.computeIfAbsent(uuid, k -> new ArrayList<>());

        // Remove old quest waypoints
        waypoints.removeIf(wp -> wp.getType() == Waypoint.WaypointType.QUEST_OBJECTIVE ||
                                wp.getType() == Waypoint.WaypointType.QUEST_TURNIN);

        // Add waypoints from active quests
        List<Quest> activeQuests = questManager.getActiveQuests(player.getUniqueId());
        if (activeQuests != null) {
            for (Quest quest : activeQuests) {
                // Add objective waypoint if quest has location
                Location targetLoc = quest.getTargetLocation(plugin.getServer());
                if (targetLoc != null) {
                    Waypoint wp = new Waypoint(
                        "quest_" + quest.getUuid() + "_objective",
                        quest.getTitle() + " (Objective)",
                        targetLoc.getWorld().getName(),
                        targetLoc.getBlockX(),
                        targetLoc.getBlockY(),
                        targetLoc.getBlockZ(),
                        Waypoint.WaypointType.QUEST_OBJECTIVE,
                        uuid
                    );
                    waypoints.add(wp);
                }

                // Add turn-in waypoint (quest giver NPC)
                AINpc questGiver = npcManager.getNPC(quest.getNpcUuid());
                if (questGiver != null && questGiver.getCurrentLocation() != null) {
                    Location npcLoc = questGiver.getCurrentLocation();
                    Waypoint wp = new Waypoint(
                        "quest_" + quest.getUuid() + "_turnin",
                        quest.getTitle() + " (Turn In)",
                        npcLoc.getWorld().getName(),
                        npcLoc.getBlockX(),
                        npcLoc.getBlockY(),
                        npcLoc.getBlockZ(),
                        Waypoint.WaypointType.QUEST_TURNIN,
                        uuid
                    );
                    waypoints.add(wp);
                }
            }
        }
    }

    /**
     * Clean up temporary waypoints when players reach them
     */
    private void cleanupWaypoints() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            List<Waypoint> waypoints = playerWaypoints.get(uuid);
            if (waypoints == null) continue;

            Location playerLoc = player.getLocation();
            waypoints.removeIf(wp ->
                wp.shouldRemove(playerLoc.getBlockX(), playerLoc.getBlockY(), playerLoc.getBlockZ())
            );
        }
    }

    /**
     * Add a waypoint for a player
     */
    public void addWaypoint(Player player, Waypoint waypoint) {
        List<Waypoint> waypoints = playerWaypoints.computeIfAbsent(
            player.getUniqueId(), k -> new ArrayList<>()
        );
        waypoints.add(waypoint);
    }

    /**
     * Remove a waypoint by ID
     */
    public void removeWaypoint(Player player, String waypointId) {
        List<Waypoint> waypoints = playerWaypoints.get(player.getUniqueId());
        if (waypoints != null) {
            waypoints.removeIf(wp -> wp.getId().equals(waypointId));
        }
    }

    /**
     * Get all waypoints for a player (including global ones)
     */
    public List<Waypoint> getWaypoints(Player player) {
        List<Waypoint> result = new ArrayList<>();

        // Add player-specific waypoints
        List<Waypoint> playerWps = playerWaypoints.get(player.getUniqueId());
        if (playerWps != null) {
            result.addAll(playerWps);
        }

        // Add global waypoints
        result.addAll(globalWaypoints);

        return result;
    }

    /**
     * Add a global waypoint (visible to all players)
     */
    public void addGlobalWaypoint(Waypoint waypoint) {
        globalWaypoints.add(waypoint);
    }

    /**
     * Remove a global waypoint
     */
    public void removeGlobalWaypoint(String waypointId) {
        globalWaypoints.removeIf(wp -> wp.getId().equals(waypointId));
    }

    /**
     * Get nearby NPC locations for minimap display
     */
    public List<Location> getNearbyNPCLocations(Player player) {
        List<Location> locations = new ArrayList<>();
        Location playerLoc = player.getLocation();

        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;

            Location npcLoc = npc.getCurrentLocation();
            if (npcLoc == null) continue;
            if (!npcLoc.getWorld().equals(playerLoc.getWorld())) continue;

            double distance = playerLoc.distance(npcLoc);
            if (distance <= 100) { // Only show NPCs within 100 blocks
                locations.add(npcLoc);
            }
        }

        return locations;
    }

    /**
     * Add death waypoint when player dies
     */
    public void addDeathWaypoint(Player player, Location deathLoc) {
        UUID uuid = player.getUniqueId();

        // Remove old death waypoint
        List<Waypoint> waypoints = playerWaypoints.computeIfAbsent(uuid, k -> new ArrayList<>());
        waypoints.removeIf(wp -> wp.getType() == Waypoint.WaypointType.DEATH_POINT);

        // Add new death waypoint
        Waypoint deathWp = new Waypoint(
            "death_" + uuid,
            "Death Location",
            deathLoc.getWorld().getName(),
            deathLoc.getBlockX(),
            deathLoc.getBlockY(),
            deathLoc.getBlockZ(),
            Waypoint.WaypointType.DEATH_POINT,
            uuid
        );
        deathWp.setTemporary(true).setRemoveDistance(10);
        waypoints.add(deathWp);
    }

    /**
     * Add a custom waypoint via command
     */
    public void addCustomWaypoint(Player player, String name) {
        Location loc = player.getLocation();
        UUID uuid = player.getUniqueId();

        // Limit to 10 custom waypoints
        List<Waypoint> waypoints = playerWaypoints.computeIfAbsent(uuid, k -> new ArrayList<>());
        long customCount = waypoints.stream()
            .filter(wp -> wp.getType() == Waypoint.WaypointType.PLAYER_MARKER)
            .count();

        if (customCount >= 10) {
            player.sendMessage(ChatColor.RED + "You can only have 10 custom waypoints. Remove one first.");
            return;
        }

        String waypointId = "custom_" + uuid + "_" + System.currentTimeMillis();
        Waypoint wp = new Waypoint(
            waypointId,
            name,
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ(),
            Waypoint.WaypointType.PLAYER_MARKER,
            uuid
        );
        waypoints.add(wp);

        player.sendMessage(ChatColor.GREEN + "Waypoint '" + name + "' added at your location!");
    }

    /**
     * List player's custom waypoints
     */
    public void listWaypoints(Player player) {
        List<Waypoint> waypoints = playerWaypoints.get(player.getUniqueId());
        if (waypoints == null || waypoints.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no waypoints.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Your Waypoints ===");
        int index = 1;
        for (Waypoint wp : waypoints) {
            String typeIcon;
            switch (wp.getType()) {
                case QUEST_OBJECTIVE:
                    typeIcon = ChatColor.YELLOW + "[Quest]";
                    break;
                case QUEST_TURNIN:
                    typeIcon = ChatColor.GREEN + "[Turn In]";
                    break;
                case DEATH_POINT:
                    typeIcon = ChatColor.RED + "[Death]";
                    break;
                case PLAYER_MARKER:
                    typeIcon = ChatColor.AQUA + "[Custom]";
                    break;
                default:
                    typeIcon = ChatColor.GRAY + "[?]";
            }

            player.sendMessage(ChatColor.WHITE + "" + index + ". " + typeIcon + " " +
                ChatColor.WHITE + wp.getName() + ChatColor.GRAY + " (" +
                wp.getX() + ", " + wp.getY() + ", " + wp.getZ() + ")");
            index++;
        }
    }

    /**
     * Remove a custom waypoint by name
     */
    public void removeCustomWaypoint(Player player, String name) {
        List<Waypoint> waypoints = playerWaypoints.get(player.getUniqueId());
        if (waypoints == null) {
            player.sendMessage(ChatColor.RED + "You have no waypoints.");
            return;
        }

        boolean removed = waypoints.removeIf(wp ->
            wp.getType() == Waypoint.WaypointType.PLAYER_MARKER &&
            wp.getName().equalsIgnoreCase(name)
        );

        if (removed) {
            player.sendMessage(ChatColor.GREEN + "Waypoint '" + name + "' removed.");
        } else {
            player.sendMessage(ChatColor.RED + "Waypoint '" + name + "' not found.");
        }
    }

    /**
     * Check if player has a minimap
     */
    public boolean hasMinmap(Player player) {
        return playerMaps.containsKey(player.getUniqueId());
    }

    /**
     * Settings for a player's minimap
     */
    public static class MinimapSettings {
        private final boolean circular;

        public MinimapSettings(boolean circular) {
            this.circular = circular;
        }

        public boolean isCircular() {
            return circular;
        }
    }
}
