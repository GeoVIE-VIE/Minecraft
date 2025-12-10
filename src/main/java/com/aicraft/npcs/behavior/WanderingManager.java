package com.aicraft.npcs.behavior;

import com.aicraft.AICompanions;
import com.aicraft.factions.Faction;
import com.aicraft.factions.FactionManager;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/**
 * Manages wandering behavior for NPCs
 * NPCs will move around their spawn area and interact with the world
 */
public class WanderingManager {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final FactionManager factionManager;

    private BukkitTask wanderTask;
    private BukkitTask combatTask;

    private final Random random = new Random();

    // Config values
    private int decisionInterval;
    private int maxWanderDistance;
    private double wanderChance;
    private int detectionRange;

    public WanderingManager(AICompanions plugin, NPCManager npcManager, FactionManager factionManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.factionManager = factionManager;

        loadConfig();
    }

    private void loadConfig() {
        decisionInterval = plugin.getConfig().getInt("npcs.wandering.decision-interval", 200);
        maxWanderDistance = plugin.getConfig().getInt("npcs.wandering.max-distance", 50);
        wanderChance = plugin.getConfig().getDouble("npcs.wandering.wander-chance", 0.3);
        detectionRange = plugin.getConfig().getInt("npcs.combat.detection-range", 16);
    }

    /**
     * Start the wandering and combat behavior tasks
     */
    public void start() {
        // Wandering behavior task
        wanderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processWandering, 100L, decisionInterval);

        // Combat detection task (runs more frequently)
        if (plugin.getConfig().getBoolean("npcs.combat.enabled", true)) {
            combatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processCombat, 50L, 20L);
        }

        plugin.getLogger().info("Wandering manager started");
    }

    /**
     * Stop all behavior tasks
     */
    public void stop() {
        if (wanderTask != null) {
            wanderTask.cancel();
            wanderTask = null;
        }
        if (combatTask != null) {
            combatTask.cancel();
            combatTask = null;
        }
    }

    /**
     * Process wandering behavior for all NPCs
     */
    private void processWandering() {
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned() || !npc.canWander()) continue;

            // Random chance to wander
            if (random.nextDouble() > wanderChance) continue;

            // Find a new location to wander to
            Location target = findWanderTarget(npc);
            if (target != null) {
                moveNPCTo(npc, target);
            }
        }
    }

    /**
     * Process combat detection and behavior
     */
    private void processCombat() {
        boolean damageFromMobs = plugin.getConfig().getBoolean("npcs.vulnerability.damage-from-mobs", true);
        boolean damageFromNpcs = plugin.getConfig().getBoolean("npcs.vulnerability.damage-from-npcs", true);

        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;

            Entity entity = npc.getBukkitEntity();
            if (entity == null || !entity.isValid()) continue;

            Location npcLoc = entity.getLocation();

            // Check for nearby threats
            for (Entity nearby : entity.getNearbyEntities(detectionRange, detectionRange, detectionRange)) {
                // Skip non-living entities
                if (!(nearby instanceof LivingEntity)) continue;

                // Check for hostile mobs
                if (damageFromMobs && nearby instanceof Monster monster) {
                    // Mobs might target the NPC
                    if (monster.getTarget() == null && random.nextDouble() < 0.1) {
                        if (entity instanceof LivingEntity livingNpc) {
                            monster.setTarget(livingNpc);
                        }
                    }
                }

                // Check for hostile NPCs from other factions
                if (damageFromNpcs) {
                    AINpc otherNpc = npcManager.getNPCFromEntity(nearby);
                    if (otherNpc != null && otherNpc.isAlive() && otherNpc.isHostile()) {
                        // Check if factions are hostile
                        if (factionManager.areHostile(npc.getFaction(), otherNpc.getFaction())) {
                            // Hostile NPC detected - engage or flee based on personality
                            handleNPCConflict(npc, otherNpc);
                        }
                    }
                }
            }

            // Hostile NPCs look for targets
            if (npc.isHostile()) {
                findAndEngageTarget(npc);
            }
        }
    }

    /**
     * Handle conflict between two NPCs
     */
    private void handleNPCConflict(AINpc npc, AINpc enemy) {
        if (!npc.isHostile()) {
            // Non-hostile NPCs flee from danger
            Location fleeDir = npc.getCurrentLocation().clone()
                    .subtract(enemy.getCurrentLocation())
                    .toVector().normalize().toLocation(npc.getCurrentLocation().getWorld());

            Location fleeTarget = npc.getCurrentLocation().add(fleeDir.toVector().multiply(10));
            moveNPCTo(npc, findSafeLocation(fleeTarget));

            // Update mood
            npc.setCurrentMood("frightened");
        } else {
            // Hostile NPCs engage
            if (npc.getBukkitEntity() instanceof Mob mob && enemy.getBukkitEntity() instanceof LivingEntity target) {
                mob.setTarget(target);
            }
            npc.setCurrentMood("aggressive");
        }
    }

    /**
     * Find targets for hostile NPCs
     */
    private void findAndEngageTarget(AINpc npc) {
        if (!npc.isSpawned() || !(npc.getBukkitEntity() instanceof Mob mob)) return;

        // Already has a target
        if (mob.getTarget() != null && !mob.getTarget().isDead()) return;

        Location npcLoc = npc.getCurrentLocation();

        // Look for enemies
        for (Entity nearby : npc.getBukkitEntity().getNearbyEntities(detectionRange, detectionRange, detectionRange)) {
            if (!(nearby instanceof LivingEntity living)) continue;

            // Check other NPCs
            AINpc otherNpc = npcManager.getNPCFromEntity(nearby);
            if (otherNpc != null) {
                if (factionManager.areHostile(npc.getFaction(), otherNpc.getFaction())) {
                    mob.setTarget(living);
                    plugin.debug(npc.getName() + " is now targeting " + otherNpc.getName());
                    return;
                }
            }
        }
    }

    /**
     * Find a valid wander target location
     */
    private Location findWanderTarget(AINpc npc) {
        Location spawn = npc.getSpawnLocation();
        Location current = npc.getCurrentLocation();

        if (spawn == null || spawn.getWorld() == null) return null;

        // Try to find a valid location
        for (int attempts = 0; attempts < 10; attempts++) {
            // Random offset from current position
            double offsetX = (random.nextDouble() - 0.5) * 20;
            double offsetZ = (random.nextDouble() - 0.5) * 20;

            Location target = current.clone().add(offsetX, 0, offsetZ);

            // Check distance from spawn
            if (target.distance(spawn) > maxWanderDistance) {
                // Too far, move back towards spawn
                target = spawn.clone().add(
                        (random.nextDouble() - 0.5) * maxWanderDistance,
                        0,
                        (random.nextDouble() - 0.5) * maxWanderDistance
                );
            }

            // Find ground level
            target = findSafeLocation(target);
            if (target != null) {
                return target;
            }
        }

        return null;
    }

    /**
     * Find a safe standing location near the target
     */
    private Location findSafeLocation(Location target) {
        if (target == null || target.getWorld() == null) return null;

        // Get the highest block at the location
        int highestY = target.getWorld().getHighestBlockYAt(target);
        target.setY(highestY + 1);

        Block block = target.getBlock();
        Block below = block.getRelative(0, -1, 0);

        // Check if it's a safe location
        if (below.getType().isSolid() &&
                !below.isLiquid() &&
                block.getType() == Material.AIR) {
            return target;
        }

        return null;
    }

    /**
     * Move an NPC to a target location using pathfinding
     */
    private void moveNPCTo(AINpc npc, Location target) {
        if (target == null) return;

        Entity entity = npc.getBukkitEntity();
        if (entity == null || !entity.isValid()) return;

        // Use Mob pathfinding if available
        if (entity instanceof Mob mob) {
            mob.getPathfinder().moveTo(target, 1.0);
            plugin.debug(npc.getName() + " wandering to " + target.getBlockX() + ", " + target.getBlockZ());
        }
    }

    /**
     * Force an NPC to move to a specific location
     */
    public void teleportNPC(AINpc npc, Location location) {
        if (npc.getBukkitEntity() != null && npc.getBukkitEntity().isValid()) {
            npc.getBukkitEntity().teleport(location);
            npc.setCurrentLocation(location);
        }
    }
}
