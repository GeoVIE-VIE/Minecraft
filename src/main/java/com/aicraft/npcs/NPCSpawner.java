package com.aicraft.npcs;

import com.aicraft.AICompanions;
import com.aicraft.factions.Faction;
import com.aicraft.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;

/**
 * Automatically spawns NPCs around the world based on player locations
 * Maintains a target NPC density around each player
 */
public class NPCSpawner implements Listener {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final FactionManager factionManager;
    private final Random random = new Random();

    private BukkitTask spawnTask;

    // Config values
    private int maxNpcsPerWorld;
    private int npcsPerArea;
    private int spawnInterval;
    private int spawnBatchSize;
    private int minPlayerDistance;
    private int maxPlayerDistance;
    private int initialSpawnCount;

    public NPCSpawner(AICompanions plugin, NPCManager npcManager, FactionManager factionManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.factionManager = factionManager;
        loadConfig();

        // Register listener for player joins
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        maxNpcsPerWorld = plugin.getConfig().getInt("spawning.max-npcs-per-world", 100);
        npcsPerArea = plugin.getConfig().getInt("spawning.npcs-per-area", 20);
        spawnInterval = plugin.getConfig().getInt("spawning.interval-seconds", 30);
        spawnBatchSize = plugin.getConfig().getInt("spawning.spawn-batch-size", 5);
        minPlayerDistance = plugin.getConfig().getInt("spawning.min-distance-from-player", 15);
        maxPlayerDistance = plugin.getConfig().getInt("spawning.max-distance-from-player", 60);
        initialSpawnCount = plugin.getConfig().getInt("spawning.initial-spawn-count", 15);
    }

    /**
     * Start the auto-spawn task
     */
    public void start() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }

        // Spawn initial NPCs for online players
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                spawnInitialNPCs(player);
            }
        }, 100L); // 5 second delay after startup

        // Regular spawning task
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::trySpawnNPCs,
                200L, spawnInterval * 20L);

        plugin.getLogger().info("NPC auto-spawner started (interval: " + spawnInterval + "s, " +
                npcsPerArea + " NPCs per area)");
    }

    /**
     * Stop the auto-spawn task
     */
    public void stop() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
    }

    /**
     * Spawn initial NPCs when a player joins
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Delay spawn to let the player load in
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int nearbyCount = countNPCsNearPlayer(player);
            if (nearbyCount < npcsPerArea / 2) {
                spawnInitialNPCs(player);
            }
        }, 100L); // 5 second delay
    }

    /**
     * Spawn initial batch of NPCs around a player
     */
    private void spawnInitialNPCs(Player player) {
        int currentNearby = countNPCsNearPlayer(player);
        int toSpawn = Math.min(initialSpawnCount, npcsPerArea - currentNearby);

        if (toSpawn <= 0) return;

        plugin.getLogger().info("Spawning " + toSpawn + " initial NPCs near " + player.getName());

        for (int i = 0; i < toSpawn; i++) {
            Location loc = findSpawnLocation(player);
            if (loc != null) {
                spawnRandomNPC(loc);
            }
        }
    }

    /**
     * Try to spawn NPCs near players who need more
     */
    private void trySpawnNPCs() {
        for (World world : Bukkit.getWorlds()) {
            // Check if spawning is enabled for this world
            List<String> enabledWorlds = plugin.getConfig().getStringList("world.enabled-worlds");
            if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(world.getName())) {
                continue;
            }

            // Check world limit
            int worldNpcCount = npcManager.getNPCsInWorld(world).size();
            if (worldNpcCount >= maxNpcsPerWorld) {
                continue;
            }

            // Process each player
            for (Player player : world.getPlayers()) {
                int nearbyCount = countNPCsNearPlayer(player);

                // Spawn more if below target
                if (nearbyCount < npcsPerArea) {
                    int toSpawn = Math.min(spawnBatchSize, npcsPerArea - nearbyCount);
                    toSpawn = Math.min(toSpawn, maxNpcsPerWorld - worldNpcCount);

                    for (int i = 0; i < toSpawn; i++) {
                        Location loc = findSpawnLocation(player);
                        if (loc != null) {
                            spawnRandomNPC(loc);
                            worldNpcCount++;
                        }
                    }

                    plugin.debug("Spawned NPCs near " + player.getName() +
                            " (now " + countNPCsNearPlayer(player) + " nearby)");
                }
            }
        }
    }

    /**
     * Count NPCs within the spawn radius of a player
     */
    private int countNPCsNearPlayer(Player player) {
        int count = 0;
        Location playerLoc = player.getLocation();

        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive()) continue;

            Location npcLoc = npc.getCurrentLocation();
            if (npcLoc == null) continue;
            if (!npcLoc.getWorld().equals(playerLoc.getWorld())) continue;

            double distance = npcLoc.distance(playerLoc);
            if (distance <= maxPlayerDistance + 20) { // Slightly larger check radius
                count++;
            }
        }

        return count;
    }

    /**
     * Find a valid spawn location near a player
     */
    private Location findSpawnLocation(Player player) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();

        for (int attempts = 0; attempts < 15; attempts++) {
            // Random angle and distance
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minPlayerDistance + random.nextDouble() * (maxPlayerDistance - minPlayerDistance);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            // Find ground level
            int y = world.getHighestBlockYAt((int) x, (int) z);
            Location loc = new Location(world, x, y + 1, z);

            if (isValidSpawnLocation(loc)) {
                return loc;
            }
        }

        return null;
    }

    /**
     * Check if a location is valid for spawning
     */
    private boolean isValidSpawnLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Block block = loc.getBlock();
        Block below = block.getRelative(0, -1, 0);
        Block above = block.getRelative(0, 1, 0);

        // Need solid ground
        if (!below.getType().isSolid()) return false;

        // Need air at feet and head
        if (!block.getType().isAir()) return false;
        if (!above.getType().isAir()) return false;

        // Don't spawn in liquids
        if (below.isLiquid()) return false;

        // Don't spawn too close to other NPCs (minimum 5 blocks apart)
        for (AINpc npc : npcManager.getAllNPCs()) {
            Location npcLoc = npc.getCurrentLocation();
            if (npcLoc != null &&
                npcLoc.getWorld().equals(loc.getWorld()) &&
                npcLoc.distance(loc) < 5) {
                return false;
            }
        }

        return true;
    }

    /**
     * Spawn a random NPC at location with weighted faction selection
     */
    private void spawnRandomNPC(Location location) {
        Faction faction = pickRandomFaction();
        String factionName = faction != null ? faction.getName() : "Wanderers";

        AINpc npc = npcManager.createRandomNPC(location, factionName);

        plugin.debug("Spawned: " + npc.getName() + " (" + factionName + ") at " +
                location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
    }

    /**
     * Pick a random faction with weighted chances
     */
    private Faction pickRandomFaction() {
        int roll = random.nextInt(100);

        // Weighted distribution:
        // 35% Villagers (most common, peaceful)
        // 20% Wanderers (neutral travelers)
        // 15% Merchants (traders)
        // 15% Guards (protectors)
        // 15% Bandits (hostile)

        if (roll < 35) {
            return factionManager.getFaction("Villagers");
        } else if (roll < 55) {
            return factionManager.getFaction("Wanderers");
        } else if (roll < 70) {
            return factionManager.getFaction("Merchants");
        } else if (roll < 85) {
            return factionManager.getFaction("Guards");
        } else {
            return factionManager.getFaction("Bandits");
        }
    }

    /**
     * Force spawn a specific number of NPCs around a player
     */
    public void forceSpawn(Player player, int count) {
        plugin.getLogger().info("Force spawning " + count + " NPCs near " + player.getName());

        int spawned = 0;
        for (int i = 0; i < count && spawned < count; i++) {
            Location loc = findSpawnLocation(player);
            if (loc != null) {
                spawnRandomNPC(loc);
                spawned++;
            }
        }

        plugin.getLogger().info("Spawned " + spawned + " NPCs");
    }
}
