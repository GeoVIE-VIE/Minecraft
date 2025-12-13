package com.aicraft.npcs.building;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages NPC home building with varied designs, farms, and couples
 */
public class NPCHomeBuilder {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private NPCTelevision tvManager;

    // Track NPC homes
    private final Map<UUID, NPCHome> npcHomes = new ConcurrentHashMap<>();

    // Track NPC couples (NPC UUID -> Partner UUID)
    private final Map<UUID, UUID> npcCouples = new ConcurrentHashMap<>();

    private BukkitTask buildTask;
    private BukkitTask coupleMatchTask;
    private final Random random = new Random();

    // House styles
    public enum HouseStyle {
        COTTAGE,      // Small cozy house with garden
        FARMHOUSE,    // Larger with attached farm
        CABIN,        // Rustic log cabin
        TOWER,        // Tall narrow tower
        UNDERGROUND,  // Hobbit-style burrow
        VILLA         // Larger fancy house
    }

    // Building materials by faction [wall, corner/accent, floor, roof]
    private static final Map<String, Material[]> FACTION_MATERIALS = new HashMap<>();
    static {
        FACTION_MATERIALS.put("villagers", new Material[]{Material.OAK_PLANKS, Material.OAK_LOG, Material.COBBLESTONE, Material.OAK_STAIRS});
        FACTION_MATERIALS.put("guards", new Material[]{Material.STONE_BRICKS, Material.COBBLESTONE, Material.POLISHED_ANDESITE, Material.STONE_BRICK_STAIRS});
        FACTION_MATERIALS.put("merchants", new Material[]{Material.BIRCH_PLANKS, Material.BIRCH_LOG, Material.SMOOTH_QUARTZ, Material.BIRCH_STAIRS});
        FACTION_MATERIALS.put("wanderers", new Material[]{Material.SPRUCE_PLANKS, Material.SPRUCE_LOG, Material.COARSE_DIRT, Material.SPRUCE_STAIRS});
        FACTION_MATERIALS.put("bandits", new Material[]{Material.DARK_OAK_PLANKS, Material.DARK_OAK_LOG, Material.MOSSY_COBBLESTONE, Material.DARK_OAK_STAIRS});
        FACTION_MATERIALS.put("cultists", new Material[]{Material.DEEPSLATE_BRICKS, Material.BLACKSTONE, Material.PURPLE_TERRACOTTA, Material.DEEPSLATE_BRICK_STAIRS});
    }

    // Bed colors by faction
    private static final Map<String, Material> FACTION_BEDS = new HashMap<>();
    static {
        FACTION_BEDS.put("villagers", Material.WHITE_BED);
        FACTION_BEDS.put("guards", Material.BLUE_BED);
        FACTION_BEDS.put("merchants", Material.YELLOW_BED);
        FACTION_BEDS.put("wanderers", Material.BROWN_BED);
        FACTION_BEDS.put("bandits", Material.BLACK_BED);
        FACTION_BEDS.put("cultists", Material.PURPLE_BED);
    }

    // Crops for farms
    private static final Material[] FARM_CROPS = {
        Material.WHEAT, Material.CARROTS, Material.POTATOES,
        Material.BEETROOTS, Material.PUMPKIN_STEM, Material.MELON_STEM
    };

    public NPCHomeBuilder(AICompanions plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    public void start() {
        tvManager = new NPCTelevision(plugin, npcManager);
        tvManager.start();

        // Building task runs every 5 minutes
        buildTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processBuildingAttempts, 6000L, 6000L);

        // Couple matching task runs every 10 minutes
        coupleMatchTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processCoupleMaking, 12000L, 12000L);

        plugin.getLogger().info("NPC Home Builder started (with couples and farms)");
    }

    public void stop() {
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }
        if (coupleMatchTask != null) {
            coupleMatchTask.cancel();
            coupleMatchTask = null;
        }
        if (tvManager != null) {
            tvManager.stop();
        }
    }

    /**
     * Process building attempts for homeless NPCs
     */
    private void processBuildingAttempts() {
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;
            if (npcHomes.containsKey(npc.getUuid())) continue;
            if (npc.isHostile()) continue;

            // 5% chance per cycle
            if (random.nextDouble() > 0.05) continue;

            Location loc = findBuildLocation(npc);
            if (loc != null) {
                // Pick a random style appropriate for the faction
                HouseStyle style = pickStyleForFaction(npc.getFaction());
                buildHome(npc, loc, style);
            }
        }
    }

    /**
     * Try to match single NPCs into couples
     */
    private void processCoupleMaking() {
        List<AINpc> singleNPCs = new ArrayList<>();

        // Find single NPCs with homes
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;
            if (npc.isHostile()) continue;
            if (npcCouples.containsKey(npc.getUuid())) continue;

            // Must have a home to be eligible
            if (npcHomes.containsKey(npc.getUuid())) {
                singleNPCs.add(npc);
            }
        }

        // Try to match compatible NPCs
        for (int i = 0; i < singleNPCs.size(); i++) {
            AINpc npc1 = singleNPCs.get(i);
            if (npcCouples.containsKey(npc1.getUuid())) continue;

            for (int j = i + 1; j < singleNPCs.size(); j++) {
                AINpc npc2 = singleNPCs.get(j);
                if (npcCouples.containsKey(npc2.getUuid())) continue;

                // Check compatibility (same faction, nearby homes)
                if (areCompatible(npc1, npc2)) {
                    // 10% chance to form couple
                    if (random.nextDouble() < 0.10) {
                        formCouple(npc1, npc2);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Check if two NPCs are compatible for coupling
     */
    private boolean areCompatible(AINpc npc1, AINpc npc2) {
        // Must be same faction
        if (!npc1.getFaction().equals(npc2.getFaction())) return false;

        // Homes must be within 50 blocks
        NPCHome home1 = npcHomes.get(npc1.getUuid());
        NPCHome home2 = npcHomes.get(npc2.getUuid());
        if (home1 == null || home2 == null) return false;

        Location loc1 = home1.getLocation();
        Location loc2 = home2.getLocation();
        if (!loc1.getWorld().equals(loc2.getWorld())) return false;

        return loc1.distance(loc2) <= 50;
    }

    /**
     * Form a couple between two NPCs
     */
    private void formCouple(AINpc npc1, AINpc npc2) {
        npcCouples.put(npc1.getUuid(), npc2.getUuid());
        npcCouples.put(npc2.getUuid(), npc1.getUuid());

        // Update home to be shared
        NPCHome home1 = npcHomes.get(npc1.getUuid());
        if (home1 != null) {
            home1.setPartnerUuid(npc2.getUuid());

            // Add second bed to home
            addSecondBed(home1, npc1.getFaction());
        }

        // Remove npc2's separate home registration (they move in with npc1)
        npcHomes.remove(npc2.getUuid());

        // Announce to nearby players
        Location loc = home1.getLocation();
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distance(loc) <= 50) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + npc1.getName() + " and " +
                    npc2.getName() + " have become a couple!");
            }
        }

        plugin.debug(npc1.getName() + " and " + npc2.getName() + " formed a couple");
    }

    /**
     * Add a second bed to a home for couples
     */
    private void addSecondBed(NPCHome home, String faction) {
        Location loc = home.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Material bedMaterial = FACTION_BEDS.getOrDefault(faction.toLowerCase(), Material.WHITE_BED);

        // Place second bed next to first (offset by 1 on X axis)
        int baseX = loc.getBlockX();
        int baseY = loc.getBlockY();
        int baseZ = loc.getBlockZ();

        placeBed(world, baseX + 2, baseY + 1, baseZ + 1, bedMaterial, BlockFace.SOUTH);
    }

    /**
     * Pick a house style appropriate for faction
     */
    private HouseStyle pickStyleForFaction(String faction) {
        if (faction == null) faction = "villagers";
        faction = faction.toLowerCase();

        switch (faction) {
            case "villagers":
                return random.nextBoolean() ? HouseStyle.COTTAGE : HouseStyle.FARMHOUSE;
            case "guards":
                return random.nextBoolean() ? HouseStyle.TOWER : HouseStyle.COTTAGE;
            case "merchants":
                return random.nextBoolean() ? HouseStyle.VILLA : HouseStyle.COTTAGE;
            case "wanderers":
                return random.nextBoolean() ? HouseStyle.CABIN : HouseStyle.UNDERGROUND;
            case "bandits":
                return random.nextBoolean() ? HouseStyle.CABIN : HouseStyle.UNDERGROUND;
            case "cultists":
                return random.nextBoolean() ? HouseStyle.UNDERGROUND : HouseStyle.TOWER;
            default:
                return HouseStyle.values()[random.nextInt(HouseStyle.values().length)];
        }
    }

    private Location findBuildLocation(AINpc npc) {
        Location spawn = npc.getSpawnLocation();
        if (spawn == null || spawn.getWorld() == null) return null;

        for (int attempts = 0; attempts < 10; attempts++) {
            int offsetX = random.nextInt(30) - 15;
            int offsetZ = random.nextInt(30) - 15;

            Location potential = spawn.clone().add(offsetX, 0, offsetZ);
            potential.setY(spawn.getWorld().getHighestBlockYAt(potential));

            if (isValidBuildLocation(potential)) {
                return potential;
            }
        }
        return null;
    }

    private boolean isValidBuildLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        Block block = loc.getBlock();
        if (block.isLiquid()) return false;

        int baseY = loc.getBlockY();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int y = loc.getWorld().getHighestBlockYAt(loc.getBlockX() + x, loc.getBlockZ() + z);
                if (Math.abs(y - baseY) > 2) return false;
            }
        }

        for (NPCHome home : npcHomes.values()) {
            if (home.getLocation().getWorld().equals(loc.getWorld()) &&
                    home.getLocation().distance(loc) < 20) {
                return false;
            }
        }
        return true;
    }

    /**
     * Build a home with the specified style
     */
    public void buildHome(AINpc npc, Location loc, HouseStyle style) {
        World world = loc.getWorld();
        if (world == null) return;

        String faction = npc.getFaction() != null ? npc.getFaction().toLowerCase() : "villagers";
        Material[] mats = FACTION_MATERIALS.getOrDefault(faction, FACTION_MATERIALS.get("villagers"));

        int baseX = loc.getBlockX();
        int baseY = loc.getBlockY();
        int baseZ = loc.getBlockZ();

        int homeSize;

        switch (style) {
            case COTTAGE:
                homeSize = buildCottage(world, baseX, baseY, baseZ, mats, faction);
                break;
            case FARMHOUSE:
                homeSize = buildFarmhouse(world, baseX, baseY, baseZ, mats, faction);
                break;
            case CABIN:
                homeSize = buildCabin(world, baseX, baseY, baseZ, mats, faction);
                break;
            case TOWER:
                homeSize = buildTower(world, baseX, baseY, baseZ, mats, faction);
                break;
            case UNDERGROUND:
                homeSize = buildUnderground(world, baseX, baseY, baseZ, mats, faction);
                break;
            case VILLA:
                homeSize = buildVilla(world, baseX, baseY, baseZ, mats, faction);
                break;
            default:
                homeSize = buildCottage(world, baseX, baseY, baseZ, mats, faction);
        }

        // Add name sign
        Block signBlock = world.getBlockAt(baseX + 2, baseY + 3, baseZ - 1);
        placeNameSign(signBlock, npc, BlockFace.NORTH);

        // Register home
        NPCHome home = new NPCHome(npc.getUuid(), loc, homeSize, style);
        npcHomes.put(npc.getUuid(), home);

        // Announce
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(loc) <= 50) {
                player.sendMessage(ChatColor.GOLD + npc.getName() + ChatColor.GRAY +
                        " has built a " + style.name().toLowerCase() + " nearby!");
            }
        }

        plugin.debug(npc.getName() + " built a " + style + " at " + baseX + ", " + baseY + ", " + baseZ);
    }

    /**
     * Build a cozy cottage with small garden
     */
    private int buildCottage(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];
        Material roofStairs = mats[3];

        // Floor 5x5
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floor);
            }
        }

        // Walls with window
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 5; x++) {
                if (!(x == 2 && y <= 2)) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                }
                // Back wall with window
                if (y == 2 && x == 2) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ + 4).setType(Material.GLASS_PANE);
                } else {
                    world.getBlockAt(baseX + x, baseY + y, baseZ + 4).setType(wall);
                }
            }
            for (int z = 1; z < 4; z++) {
                // Side walls with windows
                if (y == 2 && z == 2) {
                    world.getBlockAt(baseX, baseY + y, baseZ + z).setType(Material.GLASS_PANE);
                    world.getBlockAt(baseX + 4, baseY + y, baseZ + z).setType(Material.GLASS_PANE);
                } else {
                    world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                    world.getBlockAt(baseX + 4, baseY + y, baseZ + z).setType(wall);
                }
            }
        }

        // Corners
        for (int y = 1; y <= 3; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX + 4, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX, baseY + y, baseZ + 4).setType(corner);
            world.getBlockAt(baseX + 4, baseY + y, baseZ + 4).setType(corner);
        }

        // Pitched roof
        buildPitchedRoof(world, baseX - 1, baseY + 3, baseZ - 1, 7, 7, roofStairs, wall);

        // Interior
        addInterior(world, baseX, baseY, baseZ, faction, false);

        // Small flower garden outside
        buildFlowerGarden(world, baseX - 3, baseY, baseZ + 1, 2, 3);

        return 5;
    }

    /**
     * Build a farmhouse with attached farm
     */
    private int buildFarmhouse(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];
        Material roofStairs = mats[3];

        // Larger floor 7x6
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 6; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floor);
            }
        }

        // Walls
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 7; x++) {
                if (!(x == 3 && y <= 2)) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                }
                world.getBlockAt(baseX + x, baseY + y, baseZ + 5).setType(wall);
            }
            for (int z = 1; z < 5; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                world.getBlockAt(baseX + 6, baseY + y, baseZ + z).setType(wall);
            }
        }

        // Windows
        world.getBlockAt(baseX + 2, baseY + 2, baseZ + 5).setType(Material.GLASS_PANE);
        world.getBlockAt(baseX + 4, baseY + 2, baseZ + 5).setType(Material.GLASS_PANE);

        // Corners
        for (int y = 1; y <= 3; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX + 6, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX, baseY + y, baseZ + 5).setType(corner);
            world.getBlockAt(baseX + 6, baseY + y, baseZ + 5).setType(corner);
        }

        // Roof
        buildPitchedRoof(world, baseX - 1, baseY + 3, baseZ - 1, 9, 8, roofStairs, wall);

        // Interior with extra space
        addInterior(world, baseX, baseY, baseZ, faction, true);

        // Build farm behind house
        buildFarm(world, baseX - 1, baseY, baseZ + 7, 9, 6);

        return 7;
    }

    /**
     * Build a rustic log cabin
     */
    private int buildCabin(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction) {
        Material corner = mats[1]; // Use logs as main material
        Material floor = mats[2];

        // Floor
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 5; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floor);
            }
        }

        // Log walls
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 6; x++) {
                if (!(x == 2 && y <= 2)) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ).setType(corner);
                }
                world.getBlockAt(baseX + x, baseY + y, baseZ + 4).setType(corner);
            }
            for (int z = 1; z < 4; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(corner);
                world.getBlockAt(baseX + 5, baseY + y, baseZ + z).setType(corner);
            }
        }

        // Flat log roof
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 5; z++) {
                world.getBlockAt(baseX + x, baseY + 4, baseZ + z).setType(corner);
            }
        }

        // Chimney
        for (int y = 1; y <= 6; y++) {
            world.getBlockAt(baseX + 4, baseY + y, baseZ + 3).setType(Material.COBBLESTONE);
        }
        world.getBlockAt(baseX + 4, baseY + 1, baseZ + 3).setType(Material.CAMPFIRE);

        // Interior
        addInterior(world, baseX, baseY, baseZ, faction, false);

        return 6;
    }

    /**
     * Build a tall tower
     */
    private int buildTower(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];

        // 4x4 tower, 6 blocks tall
        for (int y = 0; y <= 6; y++) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    if (y == 0) {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(floor);
                    } else if (x == 0 || x == 3 || z == 0 || z == 3) {
                        // Door
                        if (x == 1 && z == 0 && y <= 2) {
                            continue;
                        }
                        // Windows every other level
                        if ((y == 3 || y == 5) && ((x == 1 && (z == 0 || z == 3)) || (z == 1 && (x == 0 || x == 3)))) {
                            world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.GLASS_PANE);
                        } else {
                            world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(wall);
                        }
                    }
                }
            }
        }

        // Corners reinforced
        for (int y = 1; y <= 6; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX + 3, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX, baseY + y, baseZ + 3).setType(corner);
            world.getBlockAt(baseX + 3, baseY + y, baseZ + 3).setType(corner);
        }

        // Crenellated top
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                world.getBlockAt(baseX + x, baseY + 7, baseZ + z).setType(floor);
                if ((x == 0 || x == 3) && (z == 0 || z == 3)) {
                    world.getBlockAt(baseX + x, baseY + 8, baseZ + z).setType(wall);
                }
            }
        }

        // Interior - bed on ground floor
        Material bed = FACTION_BEDS.getOrDefault(faction, Material.WHITE_BED);
        placeBed(world, baseX + 1, baseY + 1, baseZ + 2, bed, BlockFace.NORTH);
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + 1).setType(Material.CHEST);
        world.getBlockAt(baseX + 1, baseY + 3, baseZ + 1).setType(Material.TORCH);

        return 4;
    }

    /**
     * Build an underground hobbit-style home
     */
    private int buildUnderground(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction) {
        Material wall = mats[0];
        Material floor = mats[2];

        // Dig out interior
        for (int y = -3; y <= 0; y++) {
            for (int x = 0; x < 6; x++) {
                for (int z = 0; z < 5; z++) {
                    if (y == -3) {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(floor);
                    } else if (y == 0) {
                        // Ceiling with some glass skylights
                        if ((x == 2 || x == 3) && z == 2) {
                            world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.GLASS);
                        } else {
                            world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.GRASS_BLOCK);
                        }
                    } else {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.AIR);
                    }
                }
            }
        }

        // Walls
        for (int y = -3; y < 0; y++) {
            for (int x = 0; x < 6; x++) {
                world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                world.getBlockAt(baseX + x, baseY + y, baseZ + 4).setType(wall);
            }
            for (int z = 0; z < 5; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                world.getBlockAt(baseX + 5, baseY + y, baseZ + z).setType(wall);
            }
        }

        // Entrance stairs
        for (int i = 0; i < 3; i++) {
            world.getBlockAt(baseX + 2, baseY - i, baseZ - 1 - i).setType(Material.OAK_STAIRS);
            world.getBlockAt(baseX + 3, baseY - i, baseZ - 1 - i).setType(Material.OAK_STAIRS);
        }

        // Round door frame
        world.getBlockAt(baseX + 2, baseY - 3, baseZ).setType(Material.AIR);
        world.getBlockAt(baseX + 3, baseY - 3, baseZ).setType(Material.AIR);
        world.getBlockAt(baseX + 2, baseY - 2, baseZ).setType(Material.AIR);
        world.getBlockAt(baseX + 3, baseY - 2, baseZ).setType(Material.AIR);

        // Interior at lower level
        Material bed = FACTION_BEDS.getOrDefault(faction, Material.WHITE_BED);
        placeBed(world, baseX + 1, baseY - 2, baseZ + 3, bed, BlockFace.SOUTH);
        world.getBlockAt(baseX + 4, baseY - 2, baseZ + 1).setType(Material.CRAFTING_TABLE);
        world.getBlockAt(baseX + 4, baseY - 2, baseZ + 3).setType(Material.CHEST);
        world.getBlockAt(baseX + 3, baseY - 1, baseZ + 2).setType(Material.LANTERN);

        return 6;
    }

    /**
     * Build a larger villa
     */
    private int buildVilla(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];
        Material roofStairs = mats[3];

        // Large floor 9x7
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 7; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floor);
            }
        }

        // Two-story walls
        for (int y = 1; y <= 6; y++) {
            for (int x = 0; x < 9; x++) {
                if (!(x == 4 && y <= 2)) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                }
                world.getBlockAt(baseX + x, baseY + y, baseZ + 6).setType(wall);
            }
            for (int z = 1; z < 6; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                world.getBlockAt(baseX + 8, baseY + y, baseZ + z).setType(wall);
            }
        }

        // Many windows
        for (int y : new int[]{2, 5}) {
            world.getBlockAt(baseX + 2, baseY + y, baseZ).setType(Material.GLASS_PANE);
            world.getBlockAt(baseX + 6, baseY + y, baseZ).setType(Material.GLASS_PANE);
            world.getBlockAt(baseX + 2, baseY + y, baseZ + 6).setType(Material.GLASS_PANE);
            world.getBlockAt(baseX + 4, baseY + y, baseZ + 6).setType(Material.GLASS_PANE);
            world.getBlockAt(baseX + 6, baseY + y, baseZ + 6).setType(Material.GLASS_PANE);
        }

        // Second floor
        for (int x = 1; x < 8; x++) {
            for (int z = 1; z < 6; z++) {
                world.getBlockAt(baseX + x, baseY + 3, baseZ + z).setType(wall);
            }
        }

        // Staircase inside
        for (int i = 0; i < 3; i++) {
            world.getBlockAt(baseX + 7, baseY + 1 + i, baseZ + 1 + i).setType(Material.OAK_STAIRS);
            world.getBlockAt(baseX + 7, baseY + 4 + i, baseZ + 1 + i).setType(Material.AIR);
        }

        // Roof
        buildPitchedRoof(world, baseX - 1, baseY + 6, baseZ - 1, 11, 9, roofStairs, wall);

        // Interior - downstairs
        Material bed = FACTION_BEDS.getOrDefault(faction, Material.WHITE_BED);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(Material.CRAFTING_TABLE);
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + 1).setType(Material.FURNACE);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 4).setType(Material.CHEST);

        // Interior - upstairs bedroom
        placeBed(world, baseX + 1, baseY + 4, baseZ + 4, bed, BlockFace.SOUTH);
        world.getBlockAt(baseX + 3, baseY + 4, baseZ + 4).setType(Material.CHEST);
        world.getBlockAt(baseX + 4, baseY + 5, baseZ + 3).setType(Material.LANTERN);

        // Garden
        buildFlowerGarden(world, baseX - 3, baseY, baseZ + 2, 2, 4);

        return 9;
    }

    /**
     * Build a pitched roof
     */
    private void buildPitchedRoof(World world, int baseX, int baseY, int baseZ, int width, int depth, Material stairs, Material fill) {
        int peakHeight = width / 2;

        for (int layer = 0; layer < peakHeight; layer++) {
            int y = baseY + 1 + layer;

            for (int z = 0; z < depth; z++) {
                // Left slope
                Block leftBlock = world.getBlockAt(baseX + layer, y, baseZ + z);
                leftBlock.setType(stairs);
                if (leftBlock.getBlockData() instanceof Stairs stairData) {
                    stairData.setFacing(BlockFace.EAST);
                    leftBlock.setBlockData(stairData);
                }

                // Right slope
                Block rightBlock = world.getBlockAt(baseX + width - 1 - layer, y, baseZ + z);
                rightBlock.setType(stairs);
                if (rightBlock.getBlockData() instanceof Stairs stairData) {
                    stairData.setFacing(BlockFace.WEST);
                    rightBlock.setBlockData(stairData);
                }
            }

            // Fill middle
            for (int x = layer + 1; x < width - 1 - layer; x++) {
                for (int z = 0; z < depth; z++) {
                    world.getBlockAt(baseX + x, y, baseZ + z).setType(fill);
                }
            }
        }
    }

    /**
     * Add interior furnishing
     */
    private void addInterior(World world, int baseX, int baseY, int baseZ, String faction, boolean large) {
        Material bed = FACTION_BEDS.getOrDefault(faction.toLowerCase(), Material.WHITE_BED);

        // Bed
        placeBed(world, baseX + 1, baseY + 1, baseZ + 3, bed, BlockFace.SOUTH);

        // Crafting table
        world.getBlockAt(baseX + 3, baseY + 1, baseZ + 1).setType(Material.CRAFTING_TABLE);

        // Chest
        world.getBlockAt(baseX + 3, baseY + 1, baseZ + 3).setType(Material.CHEST);

        // Torch/light
        world.getBlockAt(baseX + 2, baseY + 2, baseZ + 2).setType(Material.LANTERN);

        if (large) {
            // Extra furniture for larger homes
            world.getBlockAt(baseX + 5, baseY + 1, baseZ + 1).setType(Material.FURNACE);
            world.getBlockAt(baseX + 5, baseY + 1, baseZ + 3).setType(Material.BARREL);
            world.getBlockAt(baseX + 4, baseY + 2, baseZ + 4).setType(Material.LANTERN);
        }
    }

    /**
     * Place a bed (handles the two-block bed mechanic)
     */
    private void placeBed(World world, int x, int y, int z, Material bedMaterial, BlockFace facing) {
        Block footBlock = world.getBlockAt(x, y, z);
        Block headBlock;

        // Determine head block position based on facing
        switch (facing) {
            case NORTH:
                headBlock = world.getBlockAt(x, y, z - 1);
                break;
            case SOUTH:
                headBlock = world.getBlockAt(x, y, z + 1);
                break;
            case EAST:
                headBlock = world.getBlockAt(x + 1, y, z);
                break;
            case WEST:
                headBlock = world.getBlockAt(x - 1, y, z);
                break;
            default:
                headBlock = world.getBlockAt(x, y, z + 1);
        }

        // Place foot
        footBlock.setType(bedMaterial);
        if (footBlock.getBlockData() instanceof Bed bedData) {
            bedData.setPart(Bed.Part.FOOT);
            bedData.setFacing(facing);
            footBlock.setBlockData(bedData);
        }

        // Place head
        headBlock.setType(bedMaterial);
        if (headBlock.getBlockData() instanceof Bed bedData) {
            bedData.setPart(Bed.Part.HEAD);
            bedData.setFacing(facing);
            headBlock.setBlockData(bedData);
        }
    }

    /**
     * Build a farm plot with crops
     */
    private void buildFarm(World world, int baseX, int baseY, int baseZ, int width, int depth) {
        // Fence around farm
        for (int x = 0; x < width; x++) {
            world.getBlockAt(baseX + x, baseY + 1, baseZ).setType(Material.OAK_FENCE);
            world.getBlockAt(baseX + x, baseY + 1, baseZ + depth - 1).setType(Material.OAK_FENCE);
        }
        for (int z = 1; z < depth - 1; z++) {
            world.getBlockAt(baseX, baseY + 1, baseZ + z).setType(Material.OAK_FENCE);
            world.getBlockAt(baseX + width - 1, baseY + 1, baseZ + z).setType(Material.OAK_FENCE);
        }

        // Gate
        world.getBlockAt(baseX + width / 2, baseY + 1, baseZ).setType(Material.OAK_FENCE_GATE);

        // Farm rows with water channels
        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                if (x == width / 2) {
                    // Water channel
                    world.getBlockAt(baseX + x, baseY, baseZ + z).setType(Material.WATER);
                } else {
                    // Farmland with crops
                    world.getBlockAt(baseX + x, baseY, baseZ + z).setType(Material.FARMLAND);

                    // Random crop
                    Material crop = FARM_CROPS[random.nextInt(FARM_CROPS.length)];
                    world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(crop);
                }
            }
        }

        // Composter
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + depth - 2).setType(Material.COMPOSTER);
    }

    /**
     * Build a small flower garden
     */
    private void buildFlowerGarden(World world, int baseX, int baseY, int baseZ, int width, int depth) {
        Material[] flowers = {
            Material.POPPY, Material.DANDELION, Material.BLUE_ORCHID,
            Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP,
            Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP,
            Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY
        };

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                // Grass base
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(Material.GRASS_BLOCK);

                // Random flower (70% chance)
                if (random.nextDouble() < 0.7) {
                    Material flower = flowers[random.nextInt(flowers.length)];
                    world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(flower);
                }
            }
        }
    }

    private void placeNameSign(Block block, AINpc npc, BlockFace facing) {
        Material signMaterial = Material.OAK_WALL_SIGN;
        block.setType(signMaterial);

        if (block.getBlockData() instanceof WallSign wallSign) {
            wallSign.setFacing(facing);
            block.setBlockData(wallSign);
        }

        if (block.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).setLine(0, ChatColor.GOLD + "═══════");
            sign.getSide(Side.FRONT).setLine(1, ChatColor.DARK_GREEN + npc.getName().split(" ")[0]);
            if (npc.getName().contains(" ")) {
                sign.getSide(Side.FRONT).setLine(2, ChatColor.DARK_GREEN + npc.getName().split(" ")[1]);
            }
            sign.getSide(Side.FRONT).setLine(3, ChatColor.GOLD + "═══════");
            sign.update();
        }
    }

    // Getters

    public NPCHome getHome(UUID npcUuid) {
        return npcHomes.get(npcUuid);
    }

    public boolean hasHome(UUID npcUuid) {
        return npcHomes.containsKey(npcUuid);
    }

    public void removeHome(UUID npcUuid) {
        npcHomes.remove(npcUuid);
    }

    public Collection<NPCHome> getAllHomes() {
        return npcHomes.values();
    }

    public NPCTelevision getTVManager() {
        return tvManager;
    }

    public UUID getPartner(UUID npcUuid) {
        return npcCouples.get(npcUuid);
    }

    public boolean hasPartner(UUID npcUuid) {
        return npcCouples.containsKey(npcUuid);
    }

    /**
     * Represents an NPC's home with style and partner info
     */
    public static class NPCHome {
        private final UUID ownerUuid;
        private UUID partnerUuid;
        private final Location location;
        private final int size;
        private final HouseStyle style;
        private final long builtAt;

        public NPCHome(UUID ownerUuid, Location location, int size, HouseStyle style) {
            this.ownerUuid = ownerUuid;
            this.location = location;
            this.size = size;
            this.style = style;
            this.builtAt = System.currentTimeMillis();
        }

        // Legacy constructor
        public NPCHome(UUID ownerUuid, Location location, int size) {
            this(ownerUuid, location, size, HouseStyle.COTTAGE);
        }

        public UUID getOwnerUuid() { return ownerUuid; }
        public UUID getPartnerUuid() { return partnerUuid; }
        public void setPartnerUuid(UUID partnerUuid) { this.partnerUuid = partnerUuid; }
        public Location getLocation() { return location; }
        public int getSize() { return size; }
        public HouseStyle getStyle() { return style; }
        public long getBuiltAt() { return builtAt; }
        public boolean hasPartner() { return partnerUuid != null; }
    }
}
