package com.aicraft.npcs;

import com.aicraft.AICompanions;
import com.aicraft.ai.AIManager;
import com.aicraft.database.DatabaseManager;
import com.aicraft.factions.Faction;
import com.aicraft.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all AI NPCs in the server
 */
public class NPCManager {

    private final AICompanions plugin;
    private final DatabaseManager database;
    private final AIManager aiManager;
    private final FactionManager factionManager;

    private final Map<UUID, AINpc> npcs = new ConcurrentHashMap<>();
    private final Map<UUID, AINpc> entityToNpc = new ConcurrentHashMap<>(); // Bukkit entity UUID -> NPC

    // Personality options for random generation
    private static final String[] PERSONALITIES = {
            "Friendly and helpful, always eager to assist travelers",
            "Grumpy and suspicious, trusts no one easily",
            "Wise and mysterious, speaks in riddles",
            "Cheerful merchant, loves to haggle and trade",
            "Battle-hardened veteran, respects strength",
            "Scholarly and curious, seeks knowledge above all",
            "Devout and pious, guided by faith",
            "Sly and cunning, always looking for an advantage",
            "Noble and honorable, follows a strict code",
            "Weary traveler, seen too much of the world",
            "Young and naive, filled with wonder",
            "Paranoid and nervous, fears the unknown",
            "Boastful and proud, loves to tell tales of glory",
            "Quiet and observant, notices everything",
            "Compassionate healer, cannot stand suffering"
    };

    // Name pools for random generation
    private static final String[] FIRST_NAMES = {
            "Aldric", "Brynn", "Cedric", "Dara", "Eldric", "Fiona", "Gareth", "Helena",
            "Ivan", "Jade", "Kael", "Luna", "Magnus", "Nadia", "Osric", "Petra",
            "Quinn", "Rowan", "Silas", "Thea", "Ulric", "Vera", "Wilhelm", "Xena",
            "Yara", "Zephyr", "Astrid", "Bjorn", "Cora", "Dante", "Elara", "Finn"
    };

    private static final String[] LAST_NAMES = {
            "Blackwood", "Stormwind", "Ironforge", "Silverhand", "Nightshade", "Thornwood",
            "Goldleaf", "Darkhollow", "Brightwater", "Shadowmere", "Stoneheart", "Wildfire",
            "Frostborn", "Sunweaver", "Moonblade", "Earthwalker", "Starfall", "Riverwind"
    };

    public NPCManager(AICompanions plugin, DatabaseManager database, AIManager aiManager, FactionManager factionManager) {
        this.plugin = plugin;
        this.database = database;
        this.aiManager = aiManager;
        this.factionManager = factionManager;
    }

    /**
     * Load all NPCs from the database
     */
    public void loadNPCs() {
        List<AINpc> loadedNpcs = database.loadAllNPCs();
        for (AINpc npc : loadedNpcs) {
            npcs.put(npc.getUuid(), npc);
            // Spawn the entity if they were alive
            if (npc.isAlive() && npc.getSpawnLocation() != null) {
                spawnEntity(npc);
            }
        }
        plugin.getLogger().info("Loaded " + npcs.size() + " NPCs from database");
    }

    /**
     * Save all NPCs to the database
     */
    public void saveAll() {
        for (AINpc npc : npcs.values()) {
            database.saveNPC(npc);
        }
        plugin.debug("Saved " + npcs.size() + " NPCs to database");
    }

    /**
     * Create a new NPC with AI-generated backstory
     */
    public AINpc createNPC(String name, String factionName, Location location) {
        UUID uuid = UUID.randomUUID();
        AINpc npc = new AINpc(uuid, name);

        // Set faction
        Faction faction = factionManager.getFaction(factionName);
        if (faction != null) {
            npc.setFaction(faction.getName());
            npc.setHostile(faction.isHostileByDefault());
        } else {
            npc.setFaction("Wanderers");
        }

        // Set random personality
        npc.setPersonality(PERSONALITIES[new Random().nextInt(PERSONALITIES.length)]);

        // Set location
        npc.setSpawnLocation(location);
        npc.setCurrentLocation(location);

        // Generate backstory asynchronously
        aiManager.generateBackstory(name, npc.getFaction(), npc.getPersonality())
                .thenAccept(backstory -> {
                    npc.setBackstory(backstory);
                    database.saveNPC(npc);
                    plugin.debug("Generated backstory for " + name);
                });

        // Set a temporary backstory until AI generates one
        npc.setBackstory("A mysterious figure who keeps their past hidden...");

        // Spawn the entity
        spawnEntity(npc);

        // Register
        npcs.put(uuid, npc);
        database.saveNPC(npc);

        return npc;
    }

    /**
     * Create a random NPC
     */
    public AINpc createRandomNPC(Location location, String factionName) {
        String name = generateRandomName();
        return createNPC(name, factionName, location);
    }

    /**
     * Generate a random name
     */
    public String generateRandomName() {
        Random random = new Random();
        String firstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        String lastName = LAST_NAMES[random.nextInt(LAST_NAMES.length)];
        return firstName + " " + lastName;
    }

    /**
     * Spawn the Bukkit entity for an NPC
     */
    public void spawnEntity(AINpc npc) {
        if (npc.getSpawnLocation() == null) return;

        Location loc = npc.getSpawnLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Spawn on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Remove existing entity if present
            if (npc.getBukkitEntity() != null && npc.getBukkitEntity().isValid()) {
                entityToNpc.remove(npc.getBukkitEntity().getUniqueId());
                npc.getBukkitEntity().remove();
            }

            // Spawn new entity
            Entity entity = world.spawnEntity(loc, npc.getEntityType());

            // Configure entity
            if (entity instanceof LivingEntity living) {
                living.setCustomName(formatNPCName(npc));
                living.setCustomNameVisible(true);
                living.setRemoveWhenFarAway(false);

                // Set health
                if (living.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                    living.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(npc.getMaxHealth());
                }
                living.setHealth(npc.getHealth());

                // Disable default AI for villagers
                if (entity instanceof Villager villager) {
                    villager.setAI(false); // We control movement ourselves
                    villager.setAI(true);  // Re-enable for pathfinding, but we override behavior
                }
            }

            // Mark as our NPC
            entity.setMetadata("ainpc", new FixedMetadataValue(plugin, npc.getUuid().toString()));
            entity.setPersistent(true);

            // Store references
            npc.setBukkitEntity(entity);
            entityToNpc.put(entity.getUniqueId(), npc);

            plugin.debug("Spawned NPC entity: " + npc.getName() + " at " + loc);
        });
    }

    /**
     * Format the NPC's display name with faction color
     */
    private String formatNPCName(AINpc npc) {
        Faction faction = factionManager.getFaction(npc.getFaction());
        ChatColor color = ChatColor.WHITE;
        if (faction != null) {
            color = faction.getColor();
        }
        return color + npc.getName();
    }

    /**
     * Remove an NPC
     */
    public void removeNPC(UUID uuid) {
        AINpc npc = npcs.remove(uuid);
        if (npc != null) {
            if (npc.getBukkitEntity() != null) {
                entityToNpc.remove(npc.getBukkitEntity().getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (npc.getBukkitEntity().isValid()) {
                        npc.getBukkitEntity().remove();
                    }
                });
            }
            database.deleteNPC(uuid);
        }
    }

    /**
     * Handle NPC death
     */
    public void handleDeath(AINpc npc, Entity killer) {
        npc.setAlive(false);
        npc.setHealth(0);

        if (npc.getBukkitEntity() != null) {
            entityToNpc.remove(npc.getBukkitEntity().getUniqueId());
        }

        // Log death
        String killerName = killer != null ? killer.getName() : "unknown causes";
        plugin.getLogger().info("NPC " + npc.getName() + " was killed by " + killerName);

        // Broadcast death message nearby
        if (npc.getCurrentLocation() != null) {
            for (Player player : npc.getCurrentLocation().getWorld().getPlayers()) {
                if (player.getLocation().distance(npc.getCurrentLocation()) < 50) {
                    player.sendMessage(ChatColor.GRAY + "[Death] " + npc.getName() + " has been slain.");
                }
            }
        }

        // Schedule respawn if enabled
        if (plugin.getConfig().getBoolean("npcs.vulnerability.respawn", true)) {
            int delay = plugin.getConfig().getInt("npcs.vulnerability.respawn-delay", 300);
            Bukkit.getScheduler().runTaskLater(plugin, () -> respawnNPC(npc), delay * 20L);
        }

        database.saveNPC(npc);
    }

    /**
     * Respawn a dead NPC
     */
    public void respawnNPC(AINpc npc) {
        if (npc.isAlive()) return; // Already alive

        npc.setAlive(true);
        npc.setHealth(npc.getMaxHealth());
        spawnEntity(npc);

        plugin.getLogger().info("NPC " + npc.getName() + " has respawned");
        database.saveNPC(npc);
    }

    /**
     * Get NPC by UUID
     */
    public AINpc getNPC(UUID uuid) {
        return npcs.get(uuid);
    }

    /**
     * Get NPC from a Bukkit entity
     */
    public AINpc getNPCFromEntity(Entity entity) {
        if (entity == null) return null;

        // Check our cache first
        AINpc npc = entityToNpc.get(entity.getUniqueId());
        if (npc != null) return npc;

        // Check metadata
        if (entity.hasMetadata("ainpc")) {
            String uuidStr = entity.getMetadata("ainpc").get(0).asString();
            try {
                UUID uuid = UUID.fromString(uuidStr);
                return npcs.get(uuid);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Get all NPCs
     */
    public Collection<AINpc> getAllNPCs() {
        return npcs.values();
    }

    /**
     * Get NPCs in a specific world
     */
    public List<AINpc> getNPCsInWorld(World world) {
        List<AINpc> result = new ArrayList<>();
        for (AINpc npc : npcs.values()) {
            if (npc.getSpawnLocation() != null &&
                    npc.getSpawnLocation().getWorld() != null &&
                    npc.getSpawnLocation().getWorld().equals(world)) {
                result.add(npc);
            }
        }
        return result;
    }

    /**
     * Get NPCs by faction
     */
    public List<AINpc> getNPCsByFaction(String factionName) {
        List<AINpc> result = new ArrayList<>();
        for (AINpc npc : npcs.values()) {
            if (factionName.equalsIgnoreCase(npc.getFaction())) {
                result.add(npc);
            }
        }
        return result;
    }

    /**
     * Find the nearest NPC to a location
     */
    public AINpc getNearestNPC(Location location, double maxDistance) {
        AINpc nearest = null;
        double nearestDistance = maxDistance;

        for (AINpc npc : npcs.values()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;

            Location npcLoc = npc.getCurrentLocation();
            if (npcLoc == null || !npcLoc.getWorld().equals(location.getWorld())) continue;

            double distance = npcLoc.distance(location);
            if (distance < nearestDistance) {
                nearest = npc;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    /**
     * Get the total NPC count
     */
    public int getNPCCount() {
        return npcs.size();
    }

    /**
     * Check if entity is an AI NPC
     */
    public boolean isNPC(Entity entity) {
        return getNPCFromEntity(entity) != null;
    }
}
