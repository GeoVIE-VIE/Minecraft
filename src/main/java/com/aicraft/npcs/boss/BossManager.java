package com.aicraft.npcs.boss;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages RGV 956-style boss NPCs with legendary drops
 * These are tough, unique characters with backstories and guaranteed legendary loot
 */
public class BossManager {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final BossLootManager lootManager;
    private final Random random = new Random();

    // Active bosses in the world
    private final Map<UUID, BossNpc> activeBosses = new ConcurrentHashMap<>();

    // Boss respawn timers
    private final Map<String, Long> bossRespawnTimers = new ConcurrentHashMap<>();

    // Scheduled tasks
    private BukkitTask bossCheckTask;

    // Boss definitions
    private final List<BossDefinition> bossDefinitions = new ArrayList<>();

    public BossManager(AICompanions plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.lootManager = new BossLootManager(plugin);
        initializeBossDefinitions();
    }

    /**
     * Initialize the RGV 956-style boss characters
     */
    private void initializeBossDefinitions() {
        // El Bronco 956 - The legendary valley kingpin
        bossDefinitions.add(new BossDefinition(
            "el_bronco_956",
            "El Bronco 956",
            "Bandits",
            EntityType.VINDICATOR,
            200.0, // 10x normal health
            "A legendary figure from the Rio Grande Valley, El Bronco earned his name running " +
            "contraband across the border on horseback. They say he once outran a dozen federales " +
            "through the brush country with nothing but a machete and pure huevos. Now he rules " +
            "these lands with an iron fist, and those who cross him end up feeding the coyotes.",
            "aggressive, fearless, speaks in Spanglish, references the Valley constantly, " +
            "calls everyone 'primo' or 'pendejo' depending on respect",
            Arrays.asList("bronco_machete", "your_mothers_panties", "valley_gold"),
            ChatColor.DARK_RED
        ));

        // La Llorona del Valle - The weeping terror
        bossDefinitions.add(new BossDefinition(
            "la_llorona",
            "La Llorona del Valle",
            "Cultists",
            EntityType.WITCH,
            150.0,
            "They whisper her name in the colonias after dark - La Llorona del Valle. Once a " +
            "beautiful woman from Reynosa, she drowned her children in the Rio Grande after her " +
            "husband abandoned her for a gringa. Now her spirit wanders the riverbanks, " +
            "crying for her lost hijos and dragging the souls of the unfaithful to watery graves.",
            "mournful, terrifying, speaks softly then SCREAMS, obsessed with finding her children, " +
            "hates unfaithful men, cries constantly",
            Arrays.asList("llorona_tears", "soul_dragger", "cursed_wedding_ring"),
            ChatColor.DARK_AQUA
        ));

        // El Chupacabra - The bloodsucker
        bossDefinitions.add(new BossDefinition(
            "el_chupacabra",
            "El Chupacabra",
            "Cultists",
            EntityType.RAVAGER,
            250.0,
            "First spotted in the ranchitos outside McAllen, El Chupacabra has terrorized livestock " +
            "and travelers for decades. Some say it's an alien experiment gone wrong. Others claim " +
            "it's a demon summoned by brujas. All anyone knows for sure is that when you hear the " +
            "goats screaming at night, you lock your doors and pray to la Virgen.",
            "bestial, hungry, makes terrible screeching sounds, only speaks in growls and hisses, " +
            "obsessed with blood and feeding",
            Arrays.asList("chupacabra_fang", "bloodsucker_pickaxe", "goat_soul_essence"),
            ChatColor.DARK_GREEN
        ));

        // Don Cuco the Smuggler King
        bossDefinitions.add(new BossDefinition(
            "don_cuco",
            "Don Cuco",
            "Merchants",
            EntityType.PILLAGER,
            180.0,
            "Don Cuco started as a small-time pollero, sneaking people across near Roma. Now he " +
            "controls half the trade routes in South Texas. His network moves everything - fayuca, " +
            "people, exotic goods from deep in Mexico. They say his abuela was a bruja who blessed " +
            "him with luck, and in 40 years of business, he's never once been caught.",
            "cunning, business-minded, speaks like a sophisticated criminal, always making deals, " +
            "offers to buy your loyalty, mentions his connections constantly",
            Arrays.asList("don_cuco_ledger", "smuggler_boots", "briefcase_of_souls"),
            ChatColor.GOLD
        ));

        // El Diablito - The little devil of the border
        bossDefinitions.add(new BossDefinition(
            "el_diablito",
            "El Diablito",
            "Bandits",
            EntityType.VEX,
            100.0, // Lower health but very fast and annoying
            "A chaotic gremlin who haunts the pulga markets and swap meets of the Valley. " +
            "El Diablito steals wallets, curses your car to break down, and replaces your " +
            "Big Red with off-brand cola. Small but incredibly annoying, this little devil " +
            "has been causing mischief since before your abuela was born.",
            "mischievous, chaotic, speaks very fast, loves pranks and tricks, laughs constantly, " +
            "steals things mid-conversation, makes fun of everyone",
            Arrays.asList("diablito_dice", "chaos_sombrero", "your_mothers_panties"),
            ChatColor.RED
        ));
    }

    public void start() {
        // Check for boss spawns every 5 minutes
        bossCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkBossSpawns, 6000L, 6000L);
        plugin.getLogger().info("Boss Manager started - " + bossDefinitions.size() + " boss types registered");
    }

    public void stop() {
        if (bossCheckTask != null) {
            bossCheckTask.cancel();
            bossCheckTask = null;
        }
    }

    /**
     * Check if any bosses should spawn
     */
    private void checkBossSpawns() {
        if (!plugin.getConfig().getBoolean("npcs.bosses.enabled", true)) {
            return;
        }

        int maxBosses = plugin.getConfig().getInt("npcs.bosses.max-active", 1);
        if (activeBosses.size() >= maxBosses) {
            return;
        }

        // Check for players in the world
        for (Player player : Bukkit.getOnlinePlayers()) {
            // 5% chance per check per player to spawn a boss nearby
            if (random.nextDouble() < 0.05) {
                trySpawnBossNear(player);
                break; // Only try once per cycle
            }
        }
    }

    /**
     * Try to spawn a random boss near a player
     */
    public void trySpawnBossNear(Player player) {
        // Pick a random boss that isn't on cooldown
        List<BossDefinition> available = new ArrayList<>();
        long now = System.currentTimeMillis();
        long respawnDelay = plugin.getConfig().getLong("npcs.bosses.respawn-delay-minutes", 30) * 60 * 1000;

        for (BossDefinition def : bossDefinitions) {
            Long lastDeath = bossRespawnTimers.get(def.id);
            if (lastDeath == null || (now - lastDeath) > respawnDelay) {
                available.add(def);
            }
        }

        if (available.isEmpty()) {
            return;
        }

        BossDefinition chosen = available.get(random.nextInt(available.size()));
        Location spawnLoc = findBossSpawnLocation(player);

        if (spawnLoc != null) {
            spawnBoss(chosen, spawnLoc);
        }
    }

    /**
     * Force spawn a specific boss
     */
    public BossNpc spawnBoss(String bossId, Location location) {
        for (BossDefinition def : bossDefinitions) {
            if (def.id.equalsIgnoreCase(bossId)) {
                return spawnBoss(def, location);
            }
        }
        return null;
    }

    /**
     * Spawn a boss from definition
     */
    private BossNpc spawnBoss(BossDefinition def, Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        // Create the boss NPC
        AINpc baseNpc = npcManager.createRandomNPC(location, def.faction);
        if (baseNpc == null) return null;

        // Upgrade to boss
        baseNpc.setName(def.name);
        baseNpc.setDisplayName(def.nameColor + "" + ChatColor.BOLD + def.name);
        baseNpc.setBackstory(def.backstory);
        baseNpc.setPersonality(def.personality);
        baseNpc.setMaxHealth(def.health);
        baseNpc.setHealth(def.health);
        baseNpc.setHostile(true);
        baseNpc.setCanWander(false);
        baseNpc.setEntityType(def.entityType);

        // Create boss wrapper
        BossNpc boss = new BossNpc(baseNpc, def);
        activeBosses.put(baseNpc.getUuid(), boss);

        // Respawn the entity with the correct type
        if (baseNpc.getBukkitEntity() != null) {
            baseNpc.getBukkitEntity().remove();
        }

        Entity entity = world.spawnEntity(location, def.entityType);
        if (entity instanceof LivingEntity living) {
            living.setCustomName(def.nameColor + "" + ChatColor.BOLD + def.name);
            living.setCustomNameVisible(true);

            // Set health
            if (living.getAttribute(Attribute.MAX_HEALTH) != null) {
                living.getAttribute(Attribute.MAX_HEALTH).setBaseValue(def.health);
                living.setHealth(def.health);
            }

            // Add boss effects
            living.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));
            living.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 1, false, false));

            // Glowing effect so players can see them
            living.setGlowing(true);

            baseNpc.setBukkitEntity(entity);
        }

        // Announce the boss spawn
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distance(location) <= 100) {
                p.sendTitle(
                    def.nameColor + "" + ChatColor.BOLD + def.name,
                    ChatColor.GRAY + "has appeared!",
                    10, 70, 20
                );
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
            }
        }

        plugin.getLogger().info("Boss spawned: " + def.name + " at " + location);
        return boss;
    }

    /**
     * Find a suitable location to spawn a boss
     */
    private Location findBossSpawnLocation(Player player) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return null;

        // Spawn 50-100 blocks away
        for (int attempts = 0; attempts < 20; attempts++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 50 + random.nextDouble() * 50;

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;
            int y = world.getHighestBlockYAt((int) x, (int) z);

            Location loc = new Location(world, x, y + 1, z);

            // Basic validation
            if (loc.getBlock().getType().isAir() &&
                loc.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                return loc;
            }
        }

        return null;
    }

    /**
     * Handle boss death
     */
    public void onBossDeath(AINpc npc, Player killer) {
        BossNpc boss = activeBosses.remove(npc.getUuid());
        if (boss == null) return;

        // Set respawn timer
        bossRespawnTimers.put(boss.getDefinition().id, System.currentTimeMillis());

        // Generate and drop legendary loot
        List<ItemStack> loot = lootManager.generateBossLoot(boss, killer);
        Location deathLoc = npc.getCurrentLocation();

        if (deathLoc != null && deathLoc.getWorld() != null) {
            for (ItemStack item : loot) {
                deathLoc.getWorld().dropItemNaturally(deathLoc, item);
            }

            // Death announcement
            for (Player p : deathLoc.getWorld().getPlayers()) {
                p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + boss.getDefinition().name +
                    ChatColor.YELLOW + " has been defeated by " +
                    ChatColor.GREEN + killer.getName() + ChatColor.YELLOW + "!");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            }
        }

        plugin.getLogger().info("Boss defeated: " + boss.getDefinition().name + " by " + killer.getName());
    }

    /**
     * Check if an NPC is a boss
     */
    public boolean isBoss(AINpc npc) {
        return activeBosses.containsKey(npc.getUuid());
    }

    /**
     * Get a boss by NPC
     */
    public BossNpc getBoss(AINpc npc) {
        return activeBosses.get(npc.getUuid());
    }

    /**
     * Get all active bosses
     */
    public Collection<BossNpc> getActiveBosses() {
        return activeBosses.values();
    }

    /**
     * Get boss definitions for commands
     */
    public List<BossDefinition> getBossDefinitions() {
        return bossDefinitions;
    }

    public BossLootManager getLootManager() {
        return lootManager;
    }
}
