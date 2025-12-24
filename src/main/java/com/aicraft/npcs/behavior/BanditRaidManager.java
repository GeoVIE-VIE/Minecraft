package com.aicraft.npcs.behavior;

import com.aicraft.AICompanions;
import com.aicraft.factions.FactionManager;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages bandit raids on villages/settlements
 * Bandits will occasionally organize raids on nearby settlements
 */
public class BanditRaidManager {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final FactionManager factionManager;
    private final WanderingManager wanderingManager;
    private final Random random = new Random();

    private BukkitTask raidCheckTask;
    private BukkitTask activeRaidTask;

    // Config
    private boolean raidsEnabled;
    private int minRaidIntervalHours;
    private int maxRaidIntervalHours;
    private int minBanditsForRaid;
    private int maxRaidPartySize;
    private int raidDurationMinutes;
    private int raidSearchRadius; // How far bandits look for settlements

    // State
    private long lastRaidTime = 0;
    private long nextRaidTime = 0;
    private ActiveRaid currentRaid = null;

    // Track which bandits are raiding (so they ignore home boundaries)
    private final Set<UUID> raidingBandits = new HashSet<>();

    public BanditRaidManager(AICompanions plugin, NPCManager npcManager,
                             FactionManager factionManager, WanderingManager wanderingManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.factionManager = factionManager;
        this.wanderingManager = wanderingManager;
        loadConfig();
    }

    private void loadConfig() {
        raidsEnabled = plugin.getConfig().getBoolean("raids.enabled", true);
        minRaidIntervalHours = plugin.getConfig().getInt("raids.min-interval-hours", 2);
        maxRaidIntervalHours = plugin.getConfig().getInt("raids.max-interval-hours", 6);
        minBanditsForRaid = plugin.getConfig().getInt("raids.min-bandits-required", 3);
        maxRaidPartySize = plugin.getConfig().getInt("raids.max-party-size", 4);
        raidDurationMinutes = plugin.getConfig().getInt("raids.duration-minutes", 3);
        raidSearchRadius = plugin.getConfig().getInt("raids.search-radius", 150);
    }

    /**
     * Start the raid management system
     */
    public void start() {
        if (!raidsEnabled) {
            plugin.getLogger().info("Bandit raids are disabled in config");
            return;
        }

        // Schedule next raid
        scheduleNextRaid();

        // Check for raid conditions periodically (every 5 minutes)
        raidCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkForRaid, 6000L, 6000L);

        plugin.getLogger().info("Bandit raid manager started - next raid check in " +
            ((nextRaidTime - System.currentTimeMillis()) / 1000 / 60) + " minutes");
    }

    /**
     * Stop the raid system
     */
    public void stop() {
        if (raidCheckTask != null) {
            raidCheckTask.cancel();
            raidCheckTask = null;
        }
        if (activeRaidTask != null) {
            activeRaidTask.cancel();
            activeRaidTask = null;
        }

        // Return raiding bandits to normal behavior
        for (UUID banditId : raidingBandits) {
            AINpc bandit = npcManager.getNPCByUUID(banditId);
            if (bandit != null) {
                returnBanditHome(bandit);
            }
        }
        raidingBandits.clear();
        currentRaid = null;
    }

    /**
     * Schedule the next raid time
     */
    private void scheduleNextRaid() {
        int intervalHours = minRaidIntervalHours + random.nextInt(maxRaidIntervalHours - minRaidIntervalHours + 1);
        // Add some randomness in minutes
        int intervalMinutes = intervalHours * 60 + random.nextInt(30);
        nextRaidTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000L);

        plugin.getLogger().info("Next bandit raid scheduled in approximately " + intervalHours + " hours");
    }

    /**
     * Check if conditions are right for a raid
     */
    private void checkForRaid() {
        // Already raiding?
        if (currentRaid != null) return;

        // Not time yet?
        if (System.currentTimeMillis() < nextRaidTime) return;

        // Additional conditions
        if (!checkRaidConditions()) {
            // Reschedule for later
            nextRaidTime = System.currentTimeMillis() + (30 * 60 * 1000L); // Try again in 30 min
            return;
        }

        // Find a bandit camp that can raid
        BanditCamp camp = findRaidCapableCamp();
        if (camp == null) {
            nextRaidTime = System.currentTimeMillis() + (60 * 60 * 1000L); // Try again in 1 hour
            return;
        }

        // Find a target settlement
        Location targetSettlement = findNearbySettlement(camp.center, raidSearchRadius);
        if (targetSettlement == null) {
            nextRaidTime = System.currentTimeMillis() + (60 * 60 * 1000L); // No targets, try later
            return;
        }

        // Start the raid!
        startRaid(camp, targetSettlement);
    }

    /**
     * Check environmental conditions for raiding
     */
    private boolean checkRaidConditions() {
        // Get any world with players
        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            long time = world.getTime();
            boolean isNight = time >= 13000 && time <= 23000;
            boolean isStorming = world.hasStorm();

            // Bandits prefer to raid at night or during storms
            if (isNight || isStorming) {
                return true;
            }

            // Small chance to raid during day (desperate bandits)
            if (random.nextDouble() < 0.1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find a bandit camp with enough members to raid
     */
    private BanditCamp findRaidCapableCamp() {
        Map<Location, List<AINpc>> banditGroups = new HashMap<>();

        // Group bandits by their home location
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;
            if (!"Bandits".equalsIgnoreCase(npc.getFaction())) continue;

            Location home = npc.getHomeLocation();
            if (home == null) continue;

            // Round to chunk-level grouping
            Location key = new Location(home.getWorld(),
                Math.floor(home.getX() / 32) * 32,
                home.getY(),
                Math.floor(home.getZ() / 32) * 32);

            banditGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(npc);
        }

        // Find a camp with enough bandits
        for (Map.Entry<Location, List<AINpc>> entry : banditGroups.entrySet()) {
            if (entry.getValue().size() >= minBanditsForRaid) {
                return new BanditCamp(entry.getKey(), entry.getValue());
            }
        }

        return null;
    }

    /**
     * Find a nearby settlement (cluster of non-bandit NPCs)
     */
    private Location findNearbySettlement(Location from, int radius) {
        Map<Location, Integer> settlements = new HashMap<>();

        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;
            if ("Bandits".equalsIgnoreCase(npc.getFaction())) continue;
            if ("Cultists".equalsIgnoreCase(npc.getFaction())) continue; // Don't raid cultists

            Location home = npc.getHomeLocation();
            if (home == null || !home.getWorld().equals(from.getWorld())) continue;

            double distance = home.distance(from);
            if (distance < 50 || distance > radius) continue; // Not too close, not too far

            // Round to settlement grouping
            Location key = new Location(home.getWorld(),
                Math.floor(home.getX() / 30) * 30,
                home.getY(),
                Math.floor(home.getZ() / 30) * 30);

            settlements.merge(key, 1, Integer::sum);
        }

        // Find the best target (most NPCs, within range)
        Location bestTarget = null;
        int maxNpcs = 0;

        for (Map.Entry<Location, Integer> entry : settlements.entrySet()) {
            if (entry.getValue() > maxNpcs && entry.getValue() >= 2) {
                maxNpcs = entry.getValue();
                bestTarget = entry.getKey();
            }
        }

        return bestTarget;
    }

    /**
     * Start a raid!
     */
    private void startRaid(BanditCamp camp, Location target) {
        // Select raid party
        List<AINpc> raidParty = new ArrayList<>();
        List<AINpc> available = new ArrayList<>(camp.bandits);
        Collections.shuffle(available);

        int partySize = Math.min(maxRaidPartySize, available.size());
        for (int i = 0; i < partySize; i++) {
            AINpc bandit = available.get(i);
            raidParty.add(bandit);
            raidingBandits.add(bandit.getUuid());
            bandit.setCurrentMood("aggressive");
        }

        currentRaid = new ActiveRaid(camp.center, target, raidParty, System.currentTimeMillis());
        lastRaidTime = System.currentTimeMillis();

        // Announce raid to nearby players
        announceRaid(target);

        plugin.getLogger().info("BANDIT RAID STARTED! " + raidParty.size() +
            " bandits attacking settlement at " + target.getBlockX() + ", " + target.getBlockZ());

        // Start raid behavior task
        activeRaidTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processRaid, 20L, 40L);

        // Schedule raid end
        long raidEndTime = System.currentTimeMillis() + (raidDurationMinutes * 60 * 1000L);
        Bukkit.getScheduler().runTaskLater(plugin, this::endRaid, raidDurationMinutes * 60 * 20L);
    }

    /**
     * Announce raid to nearby players
     */
    private void announceRaid(Location target) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(target.getWorld())) continue;

            double distance = player.getLocation().distance(target);
            if (distance < 200) {
                player.sendMessage(ChatColor.DARK_RED + "⚔ " + ChatColor.RED +
                    "RAID! Bandits are attacking a nearby settlement!");
                player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f);
            } else if (distance < 500) {
                player.sendMessage(ChatColor.GRAY + "You hear distant sounds of battle...");
            }
        }
    }

    /**
     * Process ongoing raid behavior
     */
    private void processRaid() {
        if (currentRaid == null) return;

        Location target = currentRaid.targetLocation;
        List<AINpc> survivors = new ArrayList<>();
        int casualties = 0;

        for (AINpc bandit : currentRaid.raidParty) {
            if (!bandit.isAlive() || !bandit.isSpawned()) {
                casualties++;
                raidingBandits.remove(bandit.getUuid());
                continue;
            }
            survivors.add(bandit);

            Entity entity = bandit.getBukkitEntity();
            if (entity == null || !entity.isValid()) continue;

            Location banditLoc = entity.getLocation();
            double distToTarget = banditLoc.distance(target);

            // Move toward target if not there yet
            if (distToTarget > 10) {
                if (entity instanceof Mob mob) {
                    mob.getPathfinder().moveTo(target, 1.3); // Move faster during raid
                }
                continue;
            }

            // At target - find and attack enemies
            attackNearbyEnemies(bandit, entity);
        }

        currentRaid.raidParty = survivors;

        // Check if raid should end early
        if (survivors.isEmpty()) {
            plugin.getLogger().info("Raid failed - all bandits eliminated!");
            endRaid();
        } else if (casualties >= currentRaid.raidParty.size() / 2 && random.nextDouble() < 0.3) {
            // Heavy losses - chance to retreat
            plugin.getLogger().info("Bandits retreating due to heavy losses!");
            endRaid();
        }
    }

    /**
     * Make a bandit attack nearby enemies
     */
    private void attackNearbyEnemies(AINpc bandit, Entity banditEntity) {
        // Find nearby targets
        for (Entity nearby : banditEntity.getNearbyEntities(16, 8, 16)) {
            if (!(nearby instanceof LivingEntity living)) continue;
            if (nearby.isDead()) continue;

            // Check if it's a hostile target
            AINpc targetNpc = npcManager.getNPCFromEntity(nearby);
            if (targetNpc != null) {
                if (factionManager.areHostile(bandit.getFaction(), targetNpc.getFaction())) {
                    // Attack this NPC
                    if (banditEntity instanceof Mob mob) {
                        mob.setTarget(living);
                    }
                    return;
                }
            }

            // Attack players too
            if (nearby instanceof Player && plugin.getConfig().getBoolean("npcs.combat.attack-players", true)) {
                if (banditEntity instanceof Mob mob) {
                    mob.setTarget(living);
                }
                return;
            }
        }
    }

    /**
     * End the current raid
     */
    private void endRaid() {
        if (activeRaidTask != null) {
            activeRaidTask.cancel();
            activeRaidTask = null;
        }

        if (currentRaid != null) {
            // Return surviving bandits to their camp
            for (AINpc bandit : currentRaid.raidParty) {
                if (bandit.isAlive() && bandit.isSpawned()) {
                    returnBanditHome(bandit);
                }
                raidingBandits.remove(bandit.getUuid());
            }

            // Announce raid end
            Location target = currentRaid.targetLocation;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getWorld().equals(target.getWorld())) continue;
                if (player.getLocation().distance(target) < 200) {
                    player.sendMessage(ChatColor.GREEN + "The bandits are retreating!");
                }
            }

            plugin.getLogger().info("Raid ended. Survivors returning to camp.");
            currentRaid = null;
        }

        // Schedule next raid
        scheduleNextRaid();
    }

    /**
     * Return a bandit to their home after raid
     */
    private void returnBanditHome(AINpc bandit) {
        bandit.setCurrentMood("tired");

        // Move back toward home
        Location home = bandit.getHomeLocation();
        if (home != null && bandit.getBukkitEntity() instanceof Mob mob) {
            mob.getPathfinder().moveTo(home, 1.0);
        }
    }

    /**
     * Check if a bandit is currently raiding (used by WanderingManager to ignore home bounds)
     */
    public boolean isRaiding(UUID banditUuid) {
        return raidingBandits.contains(banditUuid);
    }

    /**
     * Check if a raid is currently active
     */
    public boolean isRaidActive() {
        return currentRaid != null;
    }

    /**
     * Get info about current raid for display
     */
    public String getRaidStatus() {
        if (currentRaid == null) {
            long minutesToNext = (nextRaidTime - System.currentTimeMillis()) / 1000 / 60;
            return "No active raid. Next possible raid in ~" + minutesToNext + " minutes.";
        }
        return "RAID IN PROGRESS! " + currentRaid.raidParty.size() + " bandits attacking settlement.";
    }

    // === Inner Classes ===

    private static class BanditCamp {
        final Location center;
        final List<AINpc> bandits;

        BanditCamp(Location center, List<AINpc> bandits) {
            this.center = center;
            this.bandits = bandits;
        }
    }

    private static class ActiveRaid {
        final Location originCamp;
        final Location targetLocation;
        List<AINpc> raidParty;
        final long startTime;

        ActiveRaid(Location origin, Location target, List<AINpc> party, long start) {
            this.originCamp = origin;
            this.targetLocation = target;
            this.raidParty = party;
            this.startTime = start;
        }
    }
}
