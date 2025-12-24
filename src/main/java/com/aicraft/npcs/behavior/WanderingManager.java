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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

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
    private BukkitTask attackTask;

    private final Random random = new Random();

    // Config values
    private int decisionInterval;
    private int maxWanderDistance;
    private double wanderChance;
    private int detectionRange;
    private double npcAttackDamage;
    private int attackCooldownTicks;

    // Track attack cooldowns (NPC UUID -> last attack time)
    private final Map<UUID, Long> attackCooldowns = new HashMap<>();

    // Track current targets (NPC UUID -> Target Entity UUID)
    private final Map<UUID, UUID> npcTargets = new HashMap<>();

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
        npcAttackDamage = plugin.getConfig().getDouble("npcs.combat.attack-damage", 4.0);
        attackCooldownTicks = plugin.getConfig().getInt("npcs.combat.attack-cooldown", 20);
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
            // Attack execution task - NPCs actually deal damage
            attackTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processAttacks, 60L, 10L);
        }

        // Engagement task - makes NPCs look at players they're talking to
        Bukkit.getScheduler().runTaskTimer(plugin, this::processEngagements, 20L, 10L);

        plugin.getLogger().info("Wandering manager started with NPC combat enabled");
    }

    /**
     * Process NPC engagements - make engaged NPCs look at their conversation partner
     */
    private void processEngagements() {
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned() || !npc.isEngaged()) continue;

            UUID playerUuid = npc.getEngagedWithPlayer();
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerUuid);

            if (player == null || !player.isOnline()) {
                // Player left, disengage
                npc.disengageFromPlayer();
                continue;
            }

            // Check if player walked too far away
            Location npcLoc = npc.getCurrentLocation();
            Location playerLoc = player.getLocation();
            double maxDistance = plugin.getConfig().getDouble("npcs.interaction-distance", 10);

            if (npcLoc.getWorld().equals(playerLoc.getWorld()) &&
                npcLoc.distance(playerLoc) > maxDistance) {
                // Player walked away, disengage
                npc.disengageFromPlayer();
                player.sendMessage(org.bukkit.ChatColor.GRAY + "*" + npc.getName() + " returns to their business*");
                continue;
            }

            // Make NPC look at player
            Entity entity = npc.getBukkitEntity();
            if (entity instanceof LivingEntity living) {
                // Calculate look direction
                Location lookAt = playerLoc.clone();
                lookAt.setY(lookAt.getY() + 1.5); // Look at player's head

                org.bukkit.util.Vector direction = lookAt.toVector().subtract(entity.getLocation().toVector());
                Location newLoc = entity.getLocation().clone();
                newLoc.setDirection(direction);

                // Just update the yaw/pitch, don't teleport
                entity.setRotation(newLoc.getYaw(), newLoc.getPitch());
            }

            // Stop any ongoing pathfinding
            if (entity instanceof Mob mob) {
                mob.getPathfinder().stopPathfinding();
            }
        }
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
        if (attackTask != null) {
            attackTask.cancel();
            attackTask = null;
        }
        attackCooldowns.clear();
        npcTargets.clear();
    }

    /**
     * Process wandering behavior for all NPCs
     */
    private void processWandering() {
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned() || !npc.canWander()) continue;

            // Don't wander if engaged in conversation - stay focused on player
            if (npc.isEngaged()) continue;

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
        if (!npc.isSpawned()) return;

        Entity npcEntity = npc.getBukkitEntity();
        if (npcEntity == null || !npcEntity.isValid()) return;

        // Check if already has a valid target
        UUID currentTarget = npcTargets.get(npc.getUuid());
        if (currentTarget != null) {
            Entity target = Bukkit.getEntity(currentTarget);
            if (target != null && target.isValid() && !target.isDead()) {
                double dist = target.getLocation().distance(npcEntity.getLocation());
                if (dist <= detectionRange) {
                    return; // Keep current target
                }
            }
            // Target is invalid, clear it
            npcTargets.remove(npc.getUuid());
        }

        Location npcLoc = npc.getCurrentLocation();

        // Look for enemies - prioritize players if hostile to all, then other NPCs
        for (Entity nearby : npcEntity.getNearbyEntities(detectionRange, detectionRange, detectionRange)) {
            if (!(nearby instanceof LivingEntity living)) continue;
            if (nearby.isDead()) continue;

            // Check players (hostile NPCs attack players)
            if (nearby instanceof Player player) {
                if (npc.isHostile() && plugin.getConfig().getBoolean("npcs.combat.attack-players", true)) {
                    npcTargets.put(npc.getUuid(), player.getUniqueId());
                    if (npcEntity instanceof Mob mob) {
                        mob.setTarget(living);
                    }
                    plugin.debug(npc.getName() + " (" + npc.getFaction() + ") is now targeting player " + player.getName());
                    return;
                }
            }

            // Check other NPCs
            AINpc otherNpc = npcManager.getNPCFromEntity(nearby);
            if (otherNpc != null && otherNpc.isAlive()) {
                if (factionManager.areHostile(npc.getFaction(), otherNpc.getFaction())) {
                    npcTargets.put(npc.getUuid(), otherNpc.getUuid());
                    if (npcEntity instanceof Mob mob && nearby instanceof LivingEntity targetLiving) {
                        mob.setTarget(targetLiving);
                    }
                    plugin.debug(npc.getName() + " (" + npc.getFaction() + ") is now targeting " + otherNpc.getName() + " (" + otherNpc.getFaction() + ")");
                    return;
                }
            }
        }
    }

    /**
     * Process actual attacks - NPCs deal damage to their targets
     */
    private void processAttacks() {
        long currentTime = System.currentTimeMillis();
        long cooldownMs = attackCooldownTicks * 50L; // Convert ticks to ms

        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned() || !npc.isHostile()) continue;

            UUID targetId = npcTargets.get(npc.getUuid());
            if (targetId == null) continue;

            Entity targetEntity = Bukkit.getEntity(targetId);
            if (targetEntity == null || targetEntity.isDead() || !(targetEntity instanceof LivingEntity target)) {
                npcTargets.remove(npc.getUuid());
                continue;
            }

            Entity npcEntity = npc.getBukkitEntity();
            if (npcEntity == null || !npcEntity.isValid()) continue;

            // Check distance - must be within 2.5 blocks to attack
            double distance = npcEntity.getLocation().distance(targetEntity.getLocation());
            if (distance > 2.5) {
                // Move towards target
                if (npcEntity instanceof Mob mob) {
                    mob.getPathfinder().moveTo(targetEntity.getLocation(), 1.2);
                }
                continue;
            }

            // Check cooldown
            Long lastAttack = attackCooldowns.get(npc.getUuid());
            if (lastAttack != null && currentTime - lastAttack < cooldownMs) {
                continue;
            }

            // Deal damage!
            attackCooldowns.put(npc.getUuid(), currentTime);

            // Calculate damage based on faction
            double damage = npcAttackDamage;
            String faction = npc.getFaction();
            if ("Bandits".equalsIgnoreCase(faction)) {
                damage *= 1.2; // Bandits hit harder
            } else if ("Guards".equalsIgnoreCase(faction)) {
                damage *= 1.1; // Guards are trained
            } else if ("Cultists".equalsIgnoreCase(faction)) {
                damage *= 0.9; // Cultists rely on magic, not strength
                // Apply wither effect
                target.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.WITHER, 40, 0));
            }

            // Deal the damage
            target.damage(damage, npcEntity);

            // Visual feedback
            npcEntity.getWorld().spawnParticle(org.bukkit.Particle.CRIT,
                    target.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0.1);

            // Make NPC look at target
            if (npcEntity instanceof LivingEntity livingNpc) {
                Location lookAt = target.getLocation().add(0, 1, 0);
                org.bukkit.util.Vector direction = lookAt.toVector().subtract(npcEntity.getLocation().toVector());
                Location newLoc = npcEntity.getLocation().clone();
                newLoc.setDirection(direction);
                npcEntity.setRotation(newLoc.getYaw(), newLoc.getPitch());
            }

            plugin.debug(npc.getName() + " attacked " + (target instanceof Player p ? p.getName() : target.getName()) +
                    " for " + damage + " damage");
        }
    }

    /**
     * Make non-hostile NPCs defend themselves when attacked
     */
    public void onNPCAttacked(AINpc victim, Entity attacker) {
        if (victim.isHostile()) return; // Already handles combat

        // Non-hostile NPCs will fight back or flee
        if (attacker instanceof LivingEntity living) {
            if (random.nextDouble() < 0.7) {
                // 70% chance to fight back
                npcTargets.put(victim.getUuid(), attacker.getUniqueId());
                victim.setCurrentMood("defensive");
                plugin.debug(victim.getName() + " is defending against attack!");
            } else {
                // 30% chance to flee
                Location fleeDir = victim.getCurrentLocation().clone()
                        .subtract(attacker.getLocation())
                        .toVector().normalize().toLocation(victim.getCurrentLocation().getWorld());
                Location fleeTarget = victim.getCurrentLocation().add(fleeDir.toVector().multiply(15));
                moveNPCTo(victim, findSafeLocation(fleeTarget));
                victim.setCurrentMood("frightened");
            }
        }
    }

    /**
     * Find a valid wander target location
     * Respects NPC's home territory - they won't wander beyond their boundary
     */
    private Location findWanderTarget(AINpc npc) {
        Location home = npc.getHomeLocation(); // Use home, not just spawn
        Location current = npc.getCurrentLocation();

        if (home == null || home.getWorld() == null) {
            home = npc.getSpawnLocation();
            if (home == null || home.getWorld() == null) return null;
        }

        // Determine wander radius based on NPC type
        int wanderRadius;
        if (npc.isNomadic()) {
            // Nomadic NPCs (Wanderers) can roam freely
            wanderRadius = maxWanderDistance;
        } else {
            // Other NPCs stay within their home boundary
            wanderRadius = npc.getHomeBoundaryRadius();
        }

        // Try to find a valid location within boundary
        for (int attempts = 0; attempts < 10; attempts++) {
            // Random offset - smaller movements for town NPCs
            double moveRadius = npc.isNomadic() ? 20 : Math.min(10, wanderRadius / 2.0);
            double offsetX = (random.nextDouble() - 0.5) * moveRadius * 2;
            double offsetZ = (random.nextDouble() - 0.5) * moveRadius * 2;

            Location target = current.clone().add(offsetX, 0, offsetZ);

            // Check distance from home
            double distFromHome = target.distance(home);
            if (distFromHome > wanderRadius) {
                // Too far from home - move back towards home instead
                double angle = random.nextDouble() * 2 * Math.PI;
                double radius = random.nextDouble() * wanderRadius * 0.7; // Stay well within boundary
                target = home.clone().add(
                        Math.cos(angle) * radius,
                        0,
                        Math.sin(angle) * radius
                );
            }

            // Find ground level
            target = findSafeLocation(target);
            if (target != null) {
                return target;
            }
        }

        // If no valid target found, try to return home
        return findSafeLocation(home.clone());
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
