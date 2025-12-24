package com.aicraft.npcs.building;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Fence;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Allows NPCs to dynamically build structures in their settlements
 * NPCs can place torches, build walls, and maintain their territory
 */
public class NPCBuilder {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final Random random = new Random();

    private BukkitTask buildTask;

    // Config
    private boolean buildingEnabled;
    private int buildInterval; // Ticks between build checks
    private double buildChance; // Chance per NPC per check to build something
    private int maxTorchesPerSettlement;
    private int maxWallSegmentsPerSettlement;

    // Track what's been built (settlement ID -> count)
    private final Map<UUID, Integer> torchCount = new HashMap<>();
    private final Map<UUID, Integer> wallCount = new HashMap<>();

    public NPCBuilder(AICompanions plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        loadConfig();
    }

    private void loadConfig() {
        buildingEnabled = plugin.getConfig().getBoolean("npcs.building.enabled", true);
        buildInterval = plugin.getConfig().getInt("npcs.building.interval", 600); // 30 seconds
        buildChance = plugin.getConfig().getDouble("npcs.building.chance", 0.1); // 10%
        maxTorchesPerSettlement = plugin.getConfig().getInt("npcs.building.max-torches", 20);
        maxWallSegmentsPerSettlement = plugin.getConfig().getInt("npcs.building.max-wall-segments", 50);
    }

    /**
     * Start the NPC building system
     */
    public void start() {
        if (!buildingEnabled) {
            plugin.getLogger().info("NPC building is disabled in config");
            return;
        }

        buildTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processBuildingActions, 200L, buildInterval);
        plugin.getLogger().info("NPC builder started");
    }

    /**
     * Stop the NPC building system
     */
    public void stop() {
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }
    }

    /**
     * Process building actions for all eligible NPCs
     */
    private void processBuildingActions() {
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!canBuild(npc)) continue;
            if (random.nextDouble() > buildChance) continue;

            // Decide what to build
            BuildAction action = decideBuildAction(npc);
            if (action != null) {
                executeBuildAction(npc, action);
            }
        }
    }

    /**
     * Check if an NPC can build
     */
    private boolean canBuild(AINpc npc) {
        if (!npc.isAlive() || !npc.isSpawned()) return false;
        if (npc.isHostile()) return false; // Bandits don't build nice things
        if (npc.isEngaged()) return false; // Busy talking

        // Only certain factions build
        String faction = npc.getFaction();
        if (faction == null) return false;
        return switch (faction.toLowerCase()) {
            case "villagers", "guards", "merchants" -> true;
            default -> false;
        };
    }

    /**
     * Decide what building action to take
     */
    private BuildAction decideBuildAction(AINpc npc) {
        Location loc = npc.getCurrentLocation();
        if (loc == null) return null;

        // Check if area needs lighting (prioritize safety)
        if (needsLighting(loc)) {
            return BuildAction.PLACE_TORCH;
        }

        // Guards might build defensive walls
        if ("Guards".equalsIgnoreCase(npc.getFaction()) && random.nextDouble() < 0.3) {
            return BuildAction.BUILD_WALL_SEGMENT;
        }

        // Villagers might place decorative elements
        if ("Villagers".equalsIgnoreCase(npc.getFaction()) && random.nextDouble() < 0.2) {
            if (random.nextBoolean()) {
                return BuildAction.PLACE_TORCH;
            }
        }

        return null;
    }

    /**
     * Execute a build action
     */
    private void executeBuildAction(AINpc npc, BuildAction action) {
        Location npcLoc = npc.getCurrentLocation();
        if (npcLoc == null || npcLoc.getWorld() == null) return;

        UUID settlementId = npc.getSettlementId();
        if (settlementId == null) {
            settlementId = npc.getUuid(); // Use NPC's own ID if no settlement
        }

        switch (action) {
            case PLACE_TORCH -> placeTorch(npc, npcLoc, settlementId);
            case BUILD_WALL_SEGMENT -> buildWallSegment(npc, npcLoc, settlementId);
            case PLACE_LANTERN -> placeLantern(npc, npcLoc, settlementId);
        }
    }

    /**
     * Check if an area needs more lighting
     */
    private boolean needsLighting(Location loc) {
        if (loc.getWorld() == null) return false;

        // Check light level in a small area
        int darkBlocks = 0;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                Block block = loc.clone().add(x, 0, z).getBlock();
                if (block.getLightLevel() < 8) {
                    darkBlocks++;
                }
            }
        }

        // If more than half the area is dark, needs lighting
        return darkBlocks > 24;
    }

    /**
     * Place a torch near the NPC
     */
    private void placeTorch(AINpc npc, Location npcLoc, UUID settlementId) {
        // Check torch limit
        int current = torchCount.getOrDefault(settlementId, 0);
        if (current >= maxTorchesPerSettlement) return;

        // Find a good spot for a torch
        Location torchLoc = findTorchLocation(npcLoc);
        if (torchLoc == null) return;

        Block block = torchLoc.getBlock();

        // Place wall torch if there's an adjacent wall, otherwise ground torch
        BlockFace wallFace = findAdjacentWall(block);
        if (wallFace != null) {
            // Wall torch
            Material torchType = Material.WALL_TORCH;
            block.setType(torchType);
            if (block.getBlockData() instanceof Directional directional) {
                directional.setFacing(wallFace.getOppositeFace());
                block.setBlockData(directional);
            }
        } else {
            // Ground torch - needs solid block below
            Block below = block.getRelative(BlockFace.DOWN);
            if (below.getType().isSolid()) {
                block.setType(Material.TORCH);
            } else {
                return; // Can't place here
            }
        }

        torchCount.put(settlementId, current + 1);
        npc.setCurrentMood("content");

        // Visual feedback
        npcLoc.getWorld().spawnParticle(Particle.FLAME, torchLoc.clone().add(0.5, 0.5, 0.5), 5, 0.1, 0.1, 0.1, 0);
        plugin.debug(npc.getName() + " placed a torch");
    }

    /**
     * Find a suitable location for a torch
     */
    private Location findTorchLocation(Location npcLoc) {
        World world = npcLoc.getWorld();
        if (world == null) return null;

        // Search in a small radius
        for (int attempts = 0; attempts < 10; attempts++) {
            int x = npcLoc.getBlockX() + random.nextInt(7) - 3;
            int z = npcLoc.getBlockZ() + random.nextInt(7) - 3;
            int y = world.getHighestBlockYAt(x, z) + 1;

            Location loc = new Location(world, x, y, z);
            Block block = loc.getBlock();

            // Must be air
            if (block.getType() != Material.AIR) continue;

            // Must not already have a light source nearby
            if (hasNearbyLight(loc)) continue;

            return loc;
        }

        return null;
    }

    /**
     * Check if there's a light source nearby
     */
    private boolean hasNearbyLight(Location loc) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = loc.clone().add(x, y, z).getBlock();
                    Material type = block.getType();
                    if (type == Material.TORCH || type == Material.WALL_TORCH ||
                        type == Material.LANTERN || type == Material.SOUL_LANTERN ||
                        type == Material.GLOWSTONE || type == Material.SEA_LANTERN) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Find an adjacent solid wall to attach a torch to
     */
    private BlockFace findAdjacentWall(Block block) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            Block relative = block.getRelative(face);
            if (relative.getType().isSolid() && relative.getType().isOccluding()) {
                return face;
            }
        }
        return null;
    }

    /**
     * Build a wall segment (fence) for defense
     */
    private void buildWallSegment(AINpc npc, Location npcLoc, UUID settlementId) {
        // Check wall limit
        int current = wallCount.getOrDefault(settlementId, 0);
        if (current >= maxWallSegmentsPerSettlement) return;

        // Find edge of NPC's territory for wall placement
        Location wallLoc = findWallLocation(npc, npcLoc);
        if (wallLoc == null) return;

        Block block = wallLoc.getBlock();
        Block below = block.getRelative(BlockFace.DOWN);

        // Must have solid ground
        if (!below.getType().isSolid()) return;
        if (block.getType() != Material.AIR) return;

        // Place oak fence as wall
        block.setType(Material.OAK_FENCE);

        // Update fence connections
        if (block.getBlockData() instanceof Fence fence) {
            updateFenceConnections(fence, block);
            block.setBlockData(fence);
        }

        wallCount.put(settlementId, current + 1);

        // Visual feedback
        npcLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, wallLoc.clone().add(0.5, 0.5, 0.5), 3);
        plugin.debug(npc.getName() + " built a wall segment");
    }

    /**
     * Find a good location for a wall segment
     */
    private Location findWallLocation(AINpc npc, Location npcLoc) {
        World world = npcLoc.getWorld();
        if (world == null) return null;

        Location home = npc.getHomeLocation();
        int radius = npc.getHomeBoundaryRadius();

        // Try to find a spot at the edge of territory
        for (int attempts = 0; attempts < 10; attempts++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = radius * 0.8 + random.nextDouble() * radius * 0.2; // 80-100% of boundary

            int x = home.getBlockX() + (int) (Math.cos(angle) * distance);
            int z = home.getBlockZ() + (int) (Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z) + 1;

            Location loc = new Location(world, x, y, z);
            Block block = loc.getBlock();

            if (block.getType() == Material.AIR) {
                Block below = block.getRelative(BlockFace.DOWN);
                if (below.getType().isSolid() && !below.isLiquid()) {
                    return loc;
                }
            }
        }

        return null;
    }

    /**
     * Update fence connections to nearby fences
     */
    private void updateFenceConnections(Fence fence, Block block) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            Block relative = block.getRelative(face);
            if (relative.getType().name().contains("FENCE") ||
                relative.getType().name().contains("WALL") ||
                relative.getType().isSolid()) {
                fence.setFace(face, true);
            }
        }
    }

    /**
     * Place a lantern (more decorative lighting)
     */
    private void placeLantern(AINpc npc, Location npcLoc, UUID settlementId) {
        // Check torch limit (lanterns count as torches)
        int current = torchCount.getOrDefault(settlementId, 0);
        if (current >= maxTorchesPerSettlement) return;

        Location lanternLoc = findTorchLocation(npcLoc);
        if (lanternLoc == null) return;

        Block block = lanternLoc.getBlock();
        Block below = block.getRelative(BlockFace.DOWN);

        // Lanterns need a solid surface below or can hang
        if (below.getType().isSolid()) {
            block.setType(Material.LANTERN);
            torchCount.put(settlementId, current + 1);

            npcLoc.getWorld().spawnParticle(Particle.FLAME, lanternLoc.clone().add(0.5, 0.5, 0.5), 8, 0.1, 0.1, 0.1, 0);
            plugin.debug(npc.getName() + " placed a lantern");
        }
    }

    /**
     * Possible building actions
     */
    private enum BuildAction {
        PLACE_TORCH,
        BUILD_WALL_SEGMENT,
        PLACE_LANTERN
    }

    // === Public API ===

    /**
     * Force an NPC to place a torch at a specific location
     */
    public boolean forcePlaceTorch(AINpc npc, Location location) {
        if (location == null || location.getWorld() == null) return false;

        Block block = location.getBlock();
        if (block.getType() != Material.AIR) return false;

        Block below = block.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid()) return false;

        block.setType(Material.TORCH);
        return true;
    }

    /**
     * Force an NPC to build a wall segment at a specific location
     */
    public boolean forceBuildWall(AINpc npc, Location location) {
        if (location == null || location.getWorld() == null) return false;

        Block block = location.getBlock();
        if (block.getType() != Material.AIR) return false;

        Block below = block.getRelative(BlockFace.DOWN);
        if (!below.getType().isSolid()) return false;

        block.setType(Material.OAK_FENCE);
        return true;
    }

    /**
     * Get total structures built by a settlement
     */
    public int getTotalBuiltStructures(UUID settlementId) {
        return torchCount.getOrDefault(settlementId, 0) + wallCount.getOrDefault(settlementId, 0);
    }
}
