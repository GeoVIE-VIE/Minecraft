package com.aicraft.npcs;

import com.aicraft.AICompanions;
import com.aicraft.factions.Faction;
import com.aicraft.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;
import java.util.Set;

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
    private int minSpawnInterval;
    private int maxSpawnInterval;
    private int minBatchSize;
    private int maxBatchSize;
    private int minPlayerDistance;
    private int maxPlayerDistance;
    private int initialSpawnCount;
    private int spawnChancePercent;
    private boolean travelersEnabled;
    private int travelerChancePercent;

    public NPCSpawner(AICompanions plugin, NPCManager npcManager, FactionManager factionManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.factionManager = factionManager;
        loadConfig();

        // Register listener for player joins
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        maxNpcsPerWorld = plugin.getConfig().getInt("spawning.max-npcs-per-world", 30);
        npcsPerArea = plugin.getConfig().getInt("spawning.npcs-per-area", 5);
        minSpawnInterval = plugin.getConfig().getInt("spawning.min-interval-seconds", 60);
        maxSpawnInterval = plugin.getConfig().getInt("spawning.max-interval-seconds", 240);
        minBatchSize = plugin.getConfig().getInt("spawning.min-batch-size", 1);
        maxBatchSize = plugin.getConfig().getInt("spawning.max-batch-size", 2);
        minPlayerDistance = plugin.getConfig().getInt("spawning.min-distance-from-player", 25);
        maxPlayerDistance = plugin.getConfig().getInt("spawning.max-distance-from-player", 80);
        initialSpawnCount = plugin.getConfig().getInt("spawning.initial-spawn-count", 2);
        spawnChancePercent = plugin.getConfig().getInt("spawning.spawn-chance-percent", 50);
        travelersEnabled = plugin.getConfig().getBoolean("spawning.travelers.enabled", true);
        travelerChancePercent = plugin.getConfig().getInt("spawning.travelers.chance-percent", 25);
    }

    /**
     * Start the auto-spawn task
     */
    public void start() {
        if (spawnTask != null) {
            spawnTask.cancel();
        }

        // Spawn initial NPCs for online players (smaller, sporadic amount)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                spawnInitialNPCs(player);
            }
        }, 200L); // 10 second delay after startup

        // Start sporadic spawning with variable intervals
        scheduleNextSpawn();

        plugin.getLogger().info("NPC auto-spawner started (sporadic mode: " + minSpawnInterval + "-" +
                maxSpawnInterval + "s intervals, " + spawnChancePercent + "% chance)");
    }

    /**
     * Schedule the next spawn attempt with a random interval
     */
    private void scheduleNextSpawn() {
        int intervalSeconds = minSpawnInterval + random.nextInt(maxSpawnInterval - minSpawnInterval + 1);
        long intervalTicks = intervalSeconds * 20L;

        spawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            trySpawnNPCs();
            scheduleNextSpawn(); // Schedule next with new random interval
        }, intervalTicks);
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
     * Spawn initial batch of NPCs around a player (sporadic - fewer NPCs)
     */
    private void spawnInitialNPCs(Player player) {
        int currentNearby = countNPCsNearPlayer(player);

        // Randomize initial spawn (0 to initialSpawnCount)
        int baseSpawn = random.nextInt(initialSpawnCount + 1);
        int toSpawn = Math.min(baseSpawn, npcsPerArea - currentNearby);

        if (toSpawn <= 0) {
            plugin.debug("No initial NPCs spawned near " + player.getName() + " (sporadic roll)");
            return;
        }

        plugin.getLogger().info("Spawning " + toSpawn + " initial NPCs near " + player.getName());

        for (int i = 0; i < toSpawn; i++) {
            Location loc = findSpawnLocation(player);
            if (loc != null) {
                spawnRandomNPC(loc);
            }
        }
    }

    /**
     * Try to spawn NPCs near players who need more (sporadic spawning)
     */
    private void trySpawnNPCs() {
        // Random chance to skip this spawn cycle entirely (makes spawning feel sporadic)
        if (random.nextInt(100) >= spawnChancePercent) {
            plugin.debug("Spawn cycle skipped (chance roll)");
            return;
        }

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

                // Spawn more if below target (with randomized batch size)
                if (nearbyCount < npcsPerArea) {
                    int batchSize = minBatchSize + random.nextInt(maxBatchSize - minBatchSize + 1);
                    int toSpawn = Math.min(batchSize, npcsPerArea - nearbyCount);
                    toSpawn = Math.min(toSpawn, maxNpcsPerWorld - worldNpcCount);

                    for (int i = 0; i < toSpawn; i++) {
                        Location loc = findSpawnLocation(player);
                        if (loc != null) {
                            spawnRandomNPC(loc);
                            worldNpcCount++;
                        }
                    }

                    if (toSpawn > 0) {
                        plugin.debug("Spawned " + toSpawn + " NPCs near " + player.getName() +
                                " (now " + countNPCsNearPlayer(player) + " nearby)");
                    }
                }

                // Chance to spawn a distant traveler
                if (travelersEnabled && random.nextInt(100) < travelerChancePercent) {
                    spawnDistantTraveler(player);
                }
            }
        }
    }

    /**
     * Spawn a traveler NPC at a greater distance (passing through the area)
     */
    private void spawnDistantTraveler(Player player) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return;

        // Travelers spawn further away (100-200 blocks)
        int travelerMinDist = 100;
        int travelerMaxDist = 200;

        for (int attempts = 0; attempts < 10; attempts++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = travelerMinDist + random.nextDouble() * (travelerMaxDist - travelerMinDist);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            int y = world.getHighestBlockYAt((int) x, (int) z);
            Location loc = new Location(world, x, y + 1, z);

            if (isValidSpawnLocation(loc)) {
                // Travelers are always Wanderers or Merchants
                String faction = random.nextBoolean() ? "Wanderers" : "Merchants";
                AINpc npc = npcManager.createRandomNPC(loc, faction);
                plugin.debug("Spawned distant traveler: " + npc.getName() + " at distance " + (int) distance);
                return;
            }
        }
    }

    /**
     * Count NPCs within the spawn radius of a player
     * Uses getNPCsNearPlayers() for efficiency with large NPC counts
     */
    private int countNPCsNearPlayer(Player player) {
        int count = 0;
        Location playerLoc = player.getLocation();

        // Use the efficient nearby NPCs list instead of all NPCs
        for (AINpc npc : npcManager.getNPCsNearPlayers()) {
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

        // Check NPC density in this area - don't spawn if too many NPCs nearby
        // Use getNPCsNearPlayers() for efficiency with large NPC counts
        int nearbyNPCCount = 0;
        for (AINpc npc : npcManager.getNPCsNearPlayers()) {
            Location npcLoc = npc.getCurrentLocation();
            if (npcLoc != null &&
                npcLoc.getWorld().equals(loc.getWorld())) {
                double distance = npcLoc.distance(loc);
                // Don't spawn within 10 blocks of another NPC
                if (distance < 10) {
                    return false;
                }
                // Count NPCs within 50 blocks
                if (distance < 50) {
                    nearbyNPCCount++;
                }
            }
        }

        // Don't spawn if already 5+ NPCs within 50 blocks of this location
        if (nearbyNPCCount >= 5) {
            return false;
        }

        return true;
    }

    /**
     * Spawn a random NPC at location with biome-aware faction selection
     */
    private void spawnRandomNPC(Location location) {
        // Check capacity before spawning
        if (npcManager.isAtCapacity()) {
            plugin.debug("Cannot spawn NPC - at capacity");
            return;
        }

        String factionName = pickFactionForLocation(location);

        AINpc npc = npcManager.createRandomNPC(location, factionName);
        if (npc == null) {
            plugin.debug("Failed to spawn NPC - createRandomNPC returned null");
            return;
        }

        plugin.debug("Spawned: " + npc.getName() + " (" + factionName + ") at " +
                location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
    }

    /**
     * Pick appropriate faction based on location/biome
     */
    private String pickFactionForLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "Wanderers";
        }

        // Check if this is a dungeon/underground location
        if (isDungeonLocation(location)) {
            // High chance of Cultists in dungeons
            int roll = random.nextInt(100);
            if (roll < 60) return "Cultists";
            if (roll < 80) return "Bandits";
            return "Wanderers";
        }

        // Get biome for surface spawning
        Biome biome = location.getBlock().getBiome();

        // Biome-based faction selection
        return pickFactionForBiome(biome);
    }

    /**
     * Check if location is a dungeon/underground area
     */
    private boolean isDungeonLocation(Location location) {
        if (location == null || location.getWorld() == null) return false;

        Block block = location.getBlock();

        // Check if underground (below sea level and enclosed)
        if (location.getY() < 50) {
            // Check for dungeon indicators
            Block above = block.getRelative(0, 2, 0);
            Block floor = block.getRelative(0, -1, 0);

            // Dungeon indicators: stone/cobblestone ceiling and floor
            Set<Material> dungeonMaterials = Set.of(
                    Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
                    Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
                    Material.MOSSY_STONE_BRICKS, Material.DEEPSLATE_BRICKS,
                    Material.DEEPSLATE_TILES, Material.NETHER_BRICKS
            );

            if (dungeonMaterials.contains(above.getType()) ||
                    dungeonMaterials.contains(floor.getType())) {
                return true;
            }

            // Also check for spawner nearby (indicates dungeon)
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (block.getRelative(dx, dy, dz).getType() == Material.SPAWNER) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Pick faction based on biome type
     */
    private String pickFactionForBiome(Biome biome) {
        int roll = random.nextInt(100);

        // Village/Plains biomes - more villagers and guards
        if (isVillageBiome(biome)) {
            if (roll < 40) return "Villagers";
            if (roll < 60) return "Guards";
            if (roll < 75) return "Merchants";
            if (roll < 90) return "Wanderers";
            return "Bandits"; // 10% - occasional trouble
        }

        // Forest biomes - bandits and wanderers
        if (isForestBiome(biome)) {
            if (roll < 30) return "Bandits";
            if (roll < 55) return "Wanderers";
            if (roll < 70) return "Merchants";
            if (roll < 85) return "Villagers";
            return "Guards"; // Patrols
        }

        // Desert/Badlands - more bandits
        if (isDesertBiome(biome)) {
            if (roll < 40) return "Bandits";
            if (roll < 60) return "Wanderers";
            if (roll < 80) return "Merchants";
            return "Guards";
        }

        // Mountain biomes - hermits and guards
        if (isMountainBiome(biome)) {
            if (roll < 35) return "Wanderers";
            if (roll < 55) return "Guards";
            if (roll < 70) return "Villagers";
            if (roll < 85) return "Bandits";
            return "Merchants";
        }

        // Swamp - mysterious types
        if (isSwampBiome(biome)) {
            if (roll < 25) return "Cultists"; // Higher cult presence
            if (roll < 50) return "Wanderers";
            if (roll < 70) return "Bandits";
            return "Villagers";
        }

        // Default distribution
        if (roll < 30) return "Villagers";
        if (roll < 50) return "Wanderers";
        if (roll < 65) return "Merchants";
        if (roll < 80) return "Guards";
        return "Bandits";
    }

    private boolean isVillageBiome(Biome biome) {
        String name = biome.name().toLowerCase();
        return name.contains("plains") || name.contains("savanna") ||
                name.contains("meadow") || name.contains("sunflower");
    }

    private boolean isForestBiome(Biome biome) {
        String name = biome.name().toLowerCase();
        return name.contains("forest") || name.contains("taiga") ||
                name.contains("grove") || name.contains("jungle");
    }

    private boolean isDesertBiome(Biome biome) {
        String name = biome.name().toLowerCase();
        return name.contains("desert") || name.contains("badlands") ||
                name.contains("mesa");
    }

    private boolean isMountainBiome(Biome biome) {
        String name = biome.name().toLowerCase();
        return name.contains("mountain") || name.contains("peak") ||
                name.contains("hill") || name.contains("cliff");
    }

    private boolean isSwampBiome(Biome biome) {
        String name = biome.name().toLowerCase();
        return name.contains("swamp") || name.contains("marsh") ||
                name.contains("mangrove");
    }

    /**
     * Pick a random faction with weighted chances (legacy method)
     */
    private Faction pickRandomFaction() {
        int roll = random.nextInt(100);

        if (roll < 30) {
            return factionManager.getFaction("Villagers");
        } else if (roll < 50) {
            return factionManager.getFaction("Wanderers");
        } else if (roll < 65) {
            return factionManager.getFaction("Merchants");
        } else if (roll < 80) {
            return factionManager.getFaction("Guards");
        } else if (roll < 95) {
            return factionManager.getFaction("Bandits");
        } else {
            return factionManager.getFaction("Cultists");
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
