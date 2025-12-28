package com.aicraft.npcs.building;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages settlement defense systems
 * - Spawns guard NPCs to protect villager settlements
 * - Builds defensive walls and barriers against hostile mobs
 * - Reacts to threats like creepers with emergency barricades
 */
public class NPCSettlementDefense {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final NPCHomeBuilder homeBuilder;
    private final Random random = new Random();

    // Track settlements (clusters of homes)
    private final Map<String, Settlement> settlements = new ConcurrentHashMap<>();

    // Track defensive structures
    private final Set<Location> defensiveStructures = ConcurrentHashMap.newKeySet();

    // Track assigned guards per settlement
    private final Map<String, Set<UUID>> settlementGuards = new ConcurrentHashMap<>();

    private BukkitTask defenseTask;
    private BukkitTask threatDetectionTask;

    // Config
    private int wallBuildRadius;
    private int guardsPerSettlement;
    private int threatDetectionRange;
    private boolean wallBuildingEnabled;

    // Batch processing to prevent lag
    private int threatBatchIndex = 0;
    private static final int THREAT_BATCH_SIZE = 3; // Process 3 settlements per tick

    public NPCSettlementDefense(AICompanions plugin, NPCManager npcManager, NPCHomeBuilder homeBuilder) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.homeBuilder = homeBuilder;
        loadConfig();
    }

    private void loadConfig() {
        wallBuildRadius = plugin.getConfig().getInt("settlements.defense.wall-radius", 20);
        guardsPerSettlement = plugin.getConfig().getInt("settlements.defense.guards-per-settlement", 2);
        threatDetectionRange = plugin.getConfig().getInt("settlements.defense.threat-range", 30);
        wallBuildingEnabled = plugin.getConfig().getBoolean("settlements.defense.wall-building", true);
    }

    /**
     * Start the defense systems
     */
    public void start() {
        // Defense task - checks settlements and assigns guards
        defenseTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processDefense, 200L, 600L);

        // Threat detection - check for hostile mobs (batched, less frequent)
        threatDetectionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::detectThreats, 60L, 60L);

        plugin.getLogger().info("Settlement Defense System started");
    }

    /**
     * Stop defense systems
     */
    public void stop() {
        if (defenseTask != null) {
            defenseTask.cancel();
            defenseTask = null;
        }
        if (threatDetectionTask != null) {
            threatDetectionTask.cancel();
            threatDetectionTask = null;
        }
    }

    /**
     * Process settlements and assign guards
     */
    private void processDefense() {
        // Update settlements based on NPC homes
        updateSettlements();

        // Assign guards to settlements that need them
        for (Settlement settlement : settlements.values()) {
            assignGuardsToSettlement(settlement);

            // Occasionally build/repair walls
            if (wallBuildingEnabled && random.nextDouble() < 0.1) {
                buildSettlementWalls(settlement);
            }
        }
    }

    /**
     * Update settlement list based on NPC homes
     */
    private void updateSettlements() {
        settlements.clear();

        Collection<NPCHomeBuilder.NPCHome> homes = homeBuilder.getAllHomes();
        if (homes.isEmpty()) return;

        // Group homes into settlements (homes within 30 blocks of each other)
        List<NPCHomeBuilder.NPCHome> unassigned = new ArrayList<>(homes);

        while (!unassigned.isEmpty()) {
            NPCHomeBuilder.NPCHome seed = unassigned.remove(0);
            List<NPCHomeBuilder.NPCHome> cluster = new ArrayList<>();
            cluster.add(seed);

            // Find nearby homes
            Iterator<NPCHomeBuilder.NPCHome> iter = unassigned.iterator();
            while (iter.hasNext()) {
                NPCHomeBuilder.NPCHome home = iter.next();
                for (NPCHomeBuilder.NPCHome inCluster : cluster) {
                    if (home.getLocation().getWorld().equals(inCluster.getLocation().getWorld()) &&
                        home.getLocation().distance(inCluster.getLocation()) < 30) {
                        cluster.add(home);
                        iter.remove();
                        break;
                    }
                }
            }

            // Create settlement if cluster has 2+ homes
            if (cluster.size() >= 2) {
                Settlement settlement = new Settlement(cluster);
                settlements.put(settlement.getId(), settlement);
            }
        }
    }

    /**
     * Assign guard NPCs to protect a settlement
     */
    private void assignGuardsToSettlement(Settlement settlement) {
        Set<UUID> guards = settlementGuards.computeIfAbsent(settlement.getId(), k -> new HashSet<>());

        // Remove dead/invalid guards
        guards.removeIf(uuid -> {
            AINpc npc = npcManager.getNPC(uuid);
            return npc == null || !npc.isAlive() || !npc.isSpawned();
        });

        // Spawn new guards if needed
        int needed = guardsPerSettlement - guards.size();
        if (needed > 0) {
            for (int i = 0; i < needed; i++) {
                spawnGuardForSettlement(settlement, guards);
            }
        }
    }

    /**
     * Spawn a guard NPC for a settlement
     */
    private void spawnGuardForSettlement(Settlement settlement, Set<UUID> guards) {
        Location center = settlement.getCenter();
        if (center == null || center.getWorld() == null) return;

        // Find a spawn point near the settlement edge
        for (int attempts = 0; attempts < 10; attempts++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = wallBuildRadius * 0.8;

            double x = center.getX() + Math.cos(angle) * distance;
            double z = center.getZ() + Math.sin(angle) * distance;
            int y = center.getWorld().getHighestBlockYAt((int) x, (int) z);

            Location spawnLoc = new Location(center.getWorld(), x, y + 1, z);

            Block block = spawnLoc.getBlock();
            if (block.getType().isAir() && block.getRelative(BlockFace.DOWN).getType().isSolid()) {
                // Spawn a guard
                AINpc guard = npcManager.createRandomNPC(spawnLoc, "Guards");
                if (guard != null) {
                    guards.add(guard.getUuid());
                    plugin.debug("Spawned settlement guard: " + guard.getName());
                    return;
                }
            }
        }
    }

    /**
     * Detect and respond to threats near settlements
     * Uses batch processing to prevent lag with many settlements
     */
    private void detectThreats() {
        List<Settlement> allSettlements = new ArrayList<>(settlements.values());
        int totalSettlements = allSettlements.size();

        if (totalSettlements == 0) return;

        // Calculate batch bounds
        int startIndex = threatBatchIndex;
        int endIndex = Math.min(startIndex + THREAT_BATCH_SIZE, totalSettlements);

        // Process this batch
        for (int i = startIndex; i < endIndex; i++) {
            Settlement settlement = allSettlements.get(i);
            Location center = settlement.getCenter();
            if (center == null || center.getWorld() == null) continue;

            // Find hostile mobs near settlement
            for (Entity entity : center.getWorld().getNearbyEntities(center, threatDetectionRange, threatDetectionRange, threatDetectionRange)) {
                if (entity instanceof Monster monster) {
                    handleThreat(settlement, monster);
                }
            }
        }

        // Move to next batch (wrap around)
        threatBatchIndex = endIndex >= totalSettlements ? 0 : endIndex;
    }

    /**
     * Handle a hostile mob threat
     */
    private void handleThreat(Settlement settlement, Monster monster) {
        Location monsterLoc = monster.getLocation();
        Location center = settlement.getCenter();

        // Alert guards to target the monster
        Set<UUID> guards = settlementGuards.get(settlement.getId());
        if (guards != null) {
            for (UUID guardId : guards) {
                AINpc guard = npcManager.getNPC(guardId);
                if (guard != null && guard.isAlive() && guard.isSpawned()) {
                    Entity guardEntity = guard.getBukkitEntity();
                    if (guardEntity instanceof Mob mob) {
                        // Guards target the hostile mob
                        if (mob.getTarget() == null || mob.getTarget().isDead()) {
                            mob.setTarget(monster);
                            plugin.debug(guard.getName() + " is defending against " + monster.getType());
                        }
                    }
                }
            }
        }

        // Special handling for creepers - build emergency barricades
        if (monster instanceof Creeper creeper && wallBuildingEnabled) {
            double distance = monsterLoc.distance(center);
            if (distance < wallBuildRadius && distance > 5) {
                buildEmergencyBarricade(monsterLoc, center);
            }
        }
    }

    /**
     * Build an emergency barricade between a threat and the settlement center
     */
    private void buildEmergencyBarricade(Location threatLoc, Location center) {
        // Only build occasionally to avoid spam
        if (random.nextDouble() > 0.15) return;

        World world = threatLoc.getWorld();
        if (world == null) return;

        // Calculate direction from threat to center
        org.bukkit.util.Vector direction = center.toVector().subtract(threatLoc.toVector()).normalize();

        // Build a small wall between threat and settlement
        Location wallStart = threatLoc.clone().add(direction.multiply(3));
        wallStart.setY(world.getHighestBlockYAt(wallStart) + 1);

        // Check if we already have structures here
        for (Location existing : defensiveStructures) {
            if (existing.getWorld().equals(world) && existing.distance(wallStart) < 3) {
                return; // Already have a barricade nearby
            }
        }

        // Build a 3-wide, 2-high cobblestone wall
        org.bukkit.util.Vector perpendicular = new org.bukkit.util.Vector(-direction.getZ(), 0, direction.getX());

        for (int w = -1; w <= 1; w++) {
            for (int h = 0; h < 2; h++) {
                Location blockLoc = wallStart.clone()
                    .add(perpendicular.clone().multiply(w))
                    .add(0, h, 0);

                Block block = blockLoc.getBlock();
                if (block.getType().isAir()) {
                    block.setType(Material.COBBLESTONE);
                }
            }
        }

        defensiveStructures.add(wallStart.clone());
        plugin.debug("Built emergency barricade at " + wallStart.getBlockX() + ", " + wallStart.getBlockZ());
    }

    /**
     * Build/repair defensive walls around a settlement
     */
    private void buildSettlementWalls(Settlement settlement) {
        Location center = settlement.getCenter();
        if (center == null || center.getWorld() == null) return;

        World world = center.getWorld();

        // Build wall segments at random positions around perimeter
        double angle = random.nextDouble() * 2 * Math.PI;
        double x = center.getX() + Math.cos(angle) * wallBuildRadius;
        double z = center.getZ() + Math.sin(angle) * wallBuildRadius;
        int y = world.getHighestBlockYAt((int) x, (int) z);

        Location wallLoc = new Location(world, x, y, z);

        // Check if solid ground
        if (!wallLoc.getBlock().getRelative(BlockFace.DOWN).getType().isSolid()) return;

        // Build a small wall segment (fence posts)
        org.bukkit.util.Vector tangent = new org.bukkit.util.Vector(-Math.sin(angle), 0, Math.cos(angle));

        for (int i = -2; i <= 2; i++) {
            Location postLoc = wallLoc.clone().add(tangent.clone().multiply(i));
            postLoc.setY(world.getHighestBlockYAt(postLoc) + 1);

            Block block = postLoc.getBlock();
            Block below = block.getRelative(BlockFace.DOWN);

            if (block.getType().isAir() && below.getType().isSolid() && !below.isLiquid()) {
                block.setType(Material.OAK_FENCE);

                // Add torch on some posts
                if (i == 0 && random.nextDouble() < 0.3) {
                    Block above = block.getRelative(BlockFace.UP);
                    if (above.getType().isAir()) {
                        above.setType(Material.TORCH);
                    }
                }
            }
        }
    }

    /**
     * Get all active settlements
     */
    public Collection<Settlement> getSettlements() {
        return settlements.values();
    }

    /**
     * Represents a cluster of NPC homes forming a settlement
     */
    public static class Settlement {
        private final String id;
        private final List<NPCHomeBuilder.NPCHome> homes;
        private final Location center;

        public Settlement(List<NPCHomeBuilder.NPCHome> homes) {
            this.homes = homes;
            this.id = UUID.randomUUID().toString().substring(0, 8);

            // Calculate center
            double totalX = 0, totalY = 0, totalZ = 0;
            World world = null;
            for (NPCHomeBuilder.NPCHome home : homes) {
                totalX += home.getLocation().getX();
                totalY += home.getLocation().getY();
                totalZ += home.getLocation().getZ();
                world = home.getLocation().getWorld();
            }

            this.center = new Location(world,
                totalX / homes.size(),
                totalY / homes.size(),
                totalZ / homes.size());
        }

        public String getId() { return id; }
        public List<NPCHomeBuilder.NPCHome> getHomes() { return homes; }
        public Location getCenter() { return center; }
        public int getSize() { return homes.size(); }
    }
}
