package com.aicraft.npcs;

import com.aicraft.AICompanions;
import com.aicraft.ai.AIManager;
import com.aicraft.ai.Dialect;
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

    // Spanish names
    private static final String[] SPANISH_FIRST_NAMES = {
            "Carlos", "Maria", "Jose", "Sofia", "Miguel", "Isabella", "Diego", "Valentina",
            "Alejandro", "Camila", "Luis", "Elena", "Antonio", "Rosa", "Ricardo", "Carmen",
            "Roberto", "Lucia", "Javier", "Ana", "Fernando", "Gabriela", "Pedro", "Marisol"
    };

    private static final String[] SPANISH_LAST_NAMES = {
            "Garcia", "Rodriguez", "Martinez", "Lopez", "Hernandez", "Gonzalez", "Ramirez",
            "Sanchez", "Torres", "Rivera", "Flores", "Morales", "Ortiz", "Castillo", "Reyes",
            "Cruz", "Mendoza", "Delgado", "Vargas", "Santos"
    };

    // Urban/Modern names
    private static final String[] MODERN_FIRST_NAMES = {
            "Deshawn", "Aaliyah", "Tyrone", "Shaniqua", "Marcus", "Destiny", "Jaylen", "Diamond",
            "Terrell", "Jasmine", "DeAndre", "Keisha", "Malik", "Tiffany", "Andre", "Latoya",
            "Devon", "Brianna", "Chris", "Ashley", "Tyler", "Jordan", "Alex", "Morgan",
            "Brandon", "Brittany", "Kyle", "Madison", "Jake", "Taylor", "Mike", "Nikki"
    };

    private static final String[] MODERN_LAST_NAMES = {
            "Johnson", "Williams", "Brown", "Jones", "Davis", "Miller", "Wilson", "Moore",
            "Taylor", "Anderson", "Thomas", "Jackson", "White", "Harris", "Martin", "Thompson",
            "Washington", "King", "Scott", "Green", "Baker", "Adams", "Nelson", "Hill"
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

        // Set random personality based on faction
        npc.setPersonality(getPersonalityForFaction(factionName));

        // Assign dialect based on faction
        npc.setDialect(Dialect.getForFaction(factionName));

        // Configure behavior based on faction
        configureFactionBehavior(npc, factionName);

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
        npc.setBackstory(getDefaultBackstoryForFaction(factionName, name));

        // Spawn the entity
        spawnEntity(npc);

        // Register
        npcs.put(uuid, npc);
        database.saveNPC(npc);

        return npc;
    }

    /**
     * Get appropriate personality for faction
     */
    private String getPersonalityForFaction(String faction) {
        Random random = new Random();

        if (faction == null) {
            return PERSONALITIES[random.nextInt(PERSONALITIES.length)];
        }

        String[] factionPersonalities = switch (faction.toLowerCase()) {
            case "cultists" -> new String[]{
                    "Mysterious and cryptic, speaks in riddles about Geodjian",
                    "Zealous believer, eager to convert others to the faith of Geodjian",
                    "Soft-spoken and unsettling, hides dark secrets behind kind words",
                    "Fanatical devotee who sees Geodjian's will in everything",
                    "Seemingly normal but occasionally slips into strange prophecies"
            };
            case "bandits", "raiders" -> new String[]{
                    "Ruthless and cunning, values only gold and power",
                    "Brutal fighter with no mercy for the weak",
                    "Sly opportunist always looking for the next score",
                    "Former soldier turned to banditry, bitter and dangerous",
                    "Wild and unpredictable, enjoys causing chaos"
            };
            case "guards" -> new String[]{
                    "Dutiful protector, takes the job seriously",
                    "Gruff veteran who has seen too many battles",
                    "Honorable soldier following orders without question",
                    "Suspicious of strangers, trusts no one easily",
                    "Proud defender of the realm, eager to prove worth"
            };
            case "merchants" -> new String[]{
                    "Shrewd businessperson, always looking for a deal",
                    "Friendly trader with goods from exotic lands",
                    "Cunning haggler who never gives a fair price willingly",
                    "Jovial shopkeeper who loves to chat",
                    "Mysterious merchant with unusual wares"
            };
            case "villagers" -> new String[]{
                    "Simple farmer trying to make an honest living",
                    "Friendly neighbor always ready to help",
                    "Worried about recent dangers in the area",
                    "Gossip who knows everyone's business",
                    "Hard-working craftsperson proud of their trade"
            };
            case "wanderers" -> new String[]{
                    "Mysterious traveler with stories from distant lands",
                    "Weary pilgrim seeking something lost",
                    "Adventurer between quests, looking for the next challenge",
                    "Hermit who prefers solitude but is surprisingly knowledgeable",
                    "Lost soul wandering without clear purpose"
            };
            default -> PERSONALITIES;
        };

        return factionPersonalities[random.nextInt(factionPersonalities.length)];
    }

    /**
     * Configure NPC behavior based on faction
     */
    private void configureFactionBehavior(AINpc npc, String faction) {
        if (faction == null) return;

        switch (faction.toLowerCase()) {
            case "cultists" -> {
                npc.setCanWander(false); // Stay in dungeons
                npc.setHostile(false); // Deceptive, not openly hostile
                npc.setCanTrade(false);
                npc.setCanGiveQuests(true); // Dark quests
            }
            case "bandits", "raiders" -> {
                npc.setCanWander(true);
                npc.setHostile(true); // Hostile to all
                npc.setCanTrade(false);
                npc.setCanGiveQuests(false);
            }
            case "guards" -> {
                npc.setCanWander(false); // Stay at posts
                npc.setHostile(false);
                npc.setCanTrade(false);
                npc.setCanGiveQuests(true);
            }
            case "merchants" -> {
                npc.setCanWander(true); // Travel to sell goods
                npc.setHostile(false);
                npc.setCanTrade(true);
                npc.setCanGiveQuests(true);
            }
            case "villagers" -> {
                npc.setCanWander(false); // Stay in villages
                npc.setHostile(false);
                npc.setCanTrade(false);
                npc.setCanGiveQuests(true);
            }
            case "wanderers" -> {
                npc.setCanWander(true); // Always wandering
                npc.setHostile(false);
                npc.setCanTrade(false);
                npc.setCanGiveQuests(true);
            }
        }
    }

    /**
     * Get default backstory for faction
     */
    private String getDefaultBackstoryForFaction(String faction, String name) {
        if (faction == null) {
            return "A mysterious figure who keeps their past hidden...";
        }

        return switch (faction.toLowerCase()) {
            case "cultists" -> name + " is a devoted follower of Geodjian, the All-Seeing. Their true purposes remain shrouded in mystery.";
            case "bandits", "raiders" -> name + " turned to a life of crime after society abandoned them. They take what they need to survive.";
            case "guards" -> name + " serves the realm faithfully, sworn to protect the innocent from those who would do harm.";
            case "merchants" -> name + " travels the land seeking fortune through trade, always looking for the next profitable venture.";
            case "villagers" -> name + " lives a simple life in the village, working hard and hoping for peaceful days ahead.";
            case "wanderers" -> name + " roams the world without clear destination, gathering stories and wisdom along the way.";
            default -> "A mysterious figure who keeps their past hidden...";
        };
    }

    /**
     * Create a random NPC with dialect-appropriate name
     */
    public AINpc createRandomNPC(Location location, String factionName) {
        // Determine dialect first so we can pick appropriate name
        Dialect dialect = Dialect.getForFaction(factionName);
        String name = generateNameForDialect(dialect);

        // Create NPC with the dialect-appropriate name
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

        // Set random personality based on faction
        npc.setPersonality(getPersonalityForFaction(factionName));

        // Assign the pre-determined dialect
        npc.setDialect(dialect);

        // Configure behavior based on faction
        configureFactionBehavior(npc, factionName);

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
        npc.setBackstory(getDefaultBackstoryForFaction(factionName, name));

        // Spawn the entity
        spawnEntity(npc);

        // Register
        npcs.put(uuid, npc);
        database.saveNPC(npc);

        return npc;
    }

    /**
     * Generate a random name appropriate for the dialect
     */
    public String generateNameForDialect(Dialect dialect) {
        Random random = new Random();

        if (dialect.usesSpanishNames()) {
            // Spanglish dialect gets Spanish names
            String firstName = SPANISH_FIRST_NAMES[random.nextInt(SPANISH_FIRST_NAMES.length)];
            String lastName = SPANISH_LAST_NAMES[random.nextInt(SPANISH_LAST_NAMES.length)];
            return firstName + " " + lastName;
        } else if (dialect.isModern()) {
            // Modern dialects (AAVE, Urban, NYC, Modern Slang, Surfer) get contemporary names
            String firstName = MODERN_FIRST_NAMES[random.nextInt(MODERN_FIRST_NAMES.length)];
            String lastName = MODERN_LAST_NAMES[random.nextInt(MODERN_LAST_NAMES.length)];
            return firstName + " " + lastName;
        } else {
            // Fantasy/traditional dialects get fantasy names
            return generateRandomName();
        }
    }

    /**
     * Generate a random fantasy-style name
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
        if (npc != null) {
            // Verify the entity is still valid and update reference if needed
            if (npc.getBukkitEntity() == null || !npc.getBukkitEntity().isValid()) {
                npc.setBukkitEntity(entity);
            }
            return npc;
        }

        // Check metadata - entity might have been reloaded with different UUID
        if (entity.hasMetadata("ainpc")) {
            String uuidStr = entity.getMetadata("ainpc").get(0).asString();
            try {
                UUID uuid = UUID.fromString(uuidStr);
                npc = npcs.get(uuid);
                if (npc != null) {
                    // Update cache and entity reference for this newly found NPC
                    entityToNpc.put(entity.getUniqueId(), npc);
                    npc.setBukkitEntity(entity);
                    plugin.debug("Re-associated NPC " + npc.getName() + " with entity " + entity.getUniqueId());
                    return npc;
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    /**
     * Refresh entity mappings - call periodically to fix stale cache
     */
    public void refreshEntityMappings() {
        // Remove stale entries where entity is no longer valid
        entityToNpc.entrySet().removeIf(entry -> {
            AINpc npc = entry.getValue();
            Entity entity = npc.getBukkitEntity();
            return entity == null || !entity.isValid() || entity.isDead();
        });

        // Re-scan for NPCs that need new entity references
        for (AINpc npc : npcs.values()) {
            if (!npc.isAlive()) continue;

            Entity entity = npc.getBukkitEntity();
            if (entity == null || !entity.isValid()) {
                // Try to find the entity by scanning nearby entities
                Location loc = npc.getCurrentLocation();
                if (loc != null && loc.getWorld() != null) {
                    for (Entity nearby : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
                        if (nearby.hasMetadata("ainpc")) {
                            String uuidStr = nearby.getMetadata("ainpc").get(0).asString();
                            if (uuidStr.equals(npc.getUuid().toString())) {
                                npc.setBukkitEntity(nearby);
                                entityToNpc.put(nearby.getUniqueId(), npc);
                                plugin.debug("Refreshed entity mapping for " + npc.getName());
                                break;
                            }
                        }
                    }
                }
            }
        }
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

        plugin.getLogger().info("[NPC Search] Looking for nearest NPC. Total NPCs: " + npcs.size() + ", maxDistance: " + maxDistance);

        int alive = 0, spawned = 0, inWorld = 0;

        for (AINpc npc : npcs.values()) {
            if (!npc.isAlive()) {
                continue;
            }
            alive++;

            if (!npc.isSpawned()) {
                continue;
            }
            spawned++;

            Location npcLoc = npc.getCurrentLocation();
            if (npcLoc == null || !npcLoc.getWorld().equals(location.getWorld())) {
                continue;
            }
            inWorld++;

            double distance = npcLoc.distance(location);
            if (distance < nearestDistance) {
                nearest = npc;
                nearestDistance = distance;
            }
        }

        plugin.getLogger().info("[NPC Search] " + alive + " alive, " + spawned + " spawned, " + inWorld + " in same world. Found: " + (nearest != null ? nearest.getName() + " at " + String.format("%.1f", nearestDistance) + " blocks" : "NONE"));

        return nearest;
    }

    /**
     * Get all NPCs near a location, sorted by distance (closest first)
     * Useful for handling crowded areas where multiple NPCs are nearby
     */
    public List<AINpc> getNPCsNearLocation(Location location, double maxDistance) {
        List<Map.Entry<AINpc, Double>> npcDistances = new ArrayList<>();

        for (AINpc npc : npcs.values()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;

            Location npcLoc = npc.getCurrentLocation();
            if (npcLoc == null || !npcLoc.getWorld().equals(location.getWorld())) continue;

            double distance = npcLoc.distance(location);
            if (distance <= maxDistance) {
                npcDistances.add(new AbstractMap.SimpleEntry<>(npc, distance));
            }
        }

        // Sort by distance (closest first)
        npcDistances.sort(Comparator.comparingDouble(Map.Entry::getValue));

        // Extract just the NPCs
        List<AINpc> result = new ArrayList<>();
        for (Map.Entry<AINpc, Double> entry : npcDistances) {
            result.add(entry.getKey());
        }

        return result;
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
