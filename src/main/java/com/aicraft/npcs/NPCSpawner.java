package com.aicraft.npcs;

import com.aicraft.AICompanions;
import com.aicraft.factions.Faction;
import com.aicraft.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;

/**
 * Automatically spawns NPCs around the world
 */
public class NPCSpawner {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final FactionManager factionManager;
    private final Random random = new Random();

    private BukkitTask spawnTask;

    // Config values
    private int maxNpcsPerWorld;
    private int spawnInterval;
    private int spawnRadius;
    private int minPlayerDistance;
    private int maxPlayerDistance;

    public NPCSpawner(AICompanions plugin, NPCManager npcManager, FactionManager factionManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.factionManager = factionManager;
        loadConfig();
    }

    private void loadConfig() {
        maxNpcsPerWorld = plugin.getConfig().getInt("spawning.max-npcs-per-world", 20);
        spawnInterval = plugin.getConfig().getInt("spawning.interval-seconds", 60);
        spawnRadius = plugin.getConfig().getInt("spawning.radius-from-player", 50);
        minPlayerDistance = plugin.getConfig().getInt("spawning.min-distance-from-player", 20);
        maxPlayerDistance = plugin.getConfig().getInt("spawning.max-distance-from-player", 80);
    }

    /**
     * Start the auto-spawn task
     */
    public void start() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }

        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::trySpawnNPCs,
                200L, spawnInterval * 20L); // Convert seconds to ticks

        plugin.getLogger().info("NPC auto-spawner started (interval: " + spawnInterval + "s)");
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
     * Try to spawn NPCs near players
     */
    private void trySpawnNPCs() {
        for (World world : Bukkit.getWorlds()) {
            // Check if spawning is enabled for this world
            List<String> enabledWorlds = plugin.getConfig().getStringList("world.enabled-worlds");
            if (!enabledWorlds.isEmpty() && !enabledWorlds.contains(world.getName())) {
                continue;
            }

            // Check current NPC count
            int currentCount = npcManager.getNPCsInWorld(world).size();
            if (currentCount >= maxNpcsPerWorld) {
                continue;
            }

            // Try to spawn near each player
            for (Player player : world.getPlayers()) {
                // Random chance to spawn
                if (random.nextDouble() > 0.3) continue; // 30% chance per player per cycle

                Location spawnLoc = findSpawnLocation(player);
                if (spawnLoc != null) {
                    spawnRandomNPC(spawnLoc);
                    plugin.debug("Auto-spawned NPC near " + player.getName());
                }
            }
        }
    }

    /**
     * Find a valid spawn location near a player
     */
    private Location findSpawnLocation(Player player) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();

        // Try multiple times to find a good spot
        for (int attempts = 0; attempts < 10; attempts++) {
            // Random angle and distance
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minPlayerDistance + random.nextDouble() * (maxPlayerDistance - minPlayerDistance);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            // Find ground level
            int y = world.getHighestBlockYAt((int) x, (int) z);
            Location loc = new Location(world, x, y + 1, z);

            // Validate location
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

        // Need solid ground, air at feet and head level
        if (!below.getType().isSolid()) return false;
        if (!block.getType().isAir()) return false;
        if (!above.getType().isAir()) return false;

        // Don't spawn in water/lava
        if (below.isLiquid()) return false;

        // Don't spawn too close to other NPCs
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (npc.getCurrentLocation() != null &&
                npc.getCurrentLocation().getWorld().equals(loc.getWorld())) {
                if (npc.getCurrentLocation().distance(loc) < 10) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Spawn a random NPC at location
     */
    private void spawnRandomNPC(Location location) {
        // Pick a random faction with weighted chances
        Faction faction = pickRandomFaction();
        if (faction == null) {
            faction = factionManager.getFaction("Wanderers");
        }

        String factionName = faction != null ? faction.getName() : "Wanderers";

        // Create the NPC
        AINpc npc = npcManager.createRandomNPC(location, factionName);

        plugin.getLogger().info("Auto-spawned: " + npc.getName() + " (" + factionName + ") at " +
                location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
    }

    /**
     * Pick a random faction with weighted chances
     */
    private Faction pickRandomFaction() {
        // Weighted random selection
        int roll = random.nextInt(100);

        if (roll < 30) {
            return factionManager.getFaction("Villagers");      // 30%
        } else if (roll < 50) {
            return factionManager.getFaction("Wanderers");      // 20%
        } else if (roll < 65) {
            return factionManager.getFaction("Merchants");      // 15%
        } else if (roll < 80) {
            return factionManager.getFaction("Guards");         // 15%
        } else {
            return factionManager.getFaction("Bandits");        // 20%
        }
    }

    /**
     * Force spawn a batch of NPCs (for initial population)
     */
    public void populateWorld(World world, int count) {
        plugin.getLogger().info("Populating " + world.getName() + " with " + count + " NPCs...");

        int spawned = 0;
        for (Player player : world.getPlayers()) {
            for (int i = 0; i < count && spawned < count; i++) {
                Location loc = findSpawnLocation(player);
                if (loc != null) {
                    spawnRandomNPC(loc);
                    spawned++;
                }
            }
        }

        plugin.getLogger().info("Spawned " + spawned + " NPCs in " + world.getName());
    }
}
