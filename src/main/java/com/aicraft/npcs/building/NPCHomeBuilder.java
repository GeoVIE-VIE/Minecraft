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

    // Procedural building generator for unique buildings
    private final ProceduralBuildingGenerator proceduralGenerator;

    // Config: use procedural generation (creates unique buildings every time)
    private boolean useProceduralGeneration = true;

    // House styles
    public enum HouseStyle {
        COTTAGE,      // Small cozy house with garden
        FARMHOUSE,    // Larger with attached farm
        CABIN,        // Rustic log cabin
        TOWER,        // Tall narrow tower
        UNDERGROUND,  // Hobbit-style burrow
        VILLA         // Larger fancy house
    }

    // House sizes
    public enum HouseSize {
        SMALL(5, 5),      // 5x5 base
        MEDIUM(7, 7),     // 7x7 base
        LARGE(10, 10);    // 10x10 base

        public final int width;
        public final int depth;

        HouseSize(int width, int depth) {
            this.width = width;
            this.depth = depth;
        }
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
        this.proceduralGenerator = new ProceduralBuildingGenerator();
        this.useProceduralGeneration = plugin.getConfig().getBoolean("npcs.building.procedural", true);
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
                // Pick a random style and size appropriate for the faction
                HouseStyle style = pickStyleForFaction(npc.getFaction());
                HouseSize size = pickSizeForNPC(npc);
                buildHome(npc, loc, style, size);
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
     * Pick a house size - weighted towards smaller homes
     */
    private HouseSize pickSizeForNPC(AINpc npc) {
        int roll = random.nextInt(100);

        // Merchants and Guards more likely to have larger homes
        String faction = npc.getFaction() != null ? npc.getFaction().toLowerCase() : "";
        if (faction.equals("merchants") || faction.equals("guards")) {
            if (roll < 20) return HouseSize.SMALL;
            if (roll < 60) return HouseSize.MEDIUM;
            return HouseSize.LARGE;
        }

        // Most NPCs get smaller homes (60% small, 30% medium, 10% large)
        if (roll < 60) return HouseSize.SMALL;
        if (roll < 90) return HouseSize.MEDIUM;
        return HouseSize.LARGE;
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
     * Build a home with the specified style (legacy - uses MEDIUM size)
     */
    public void buildHome(AINpc npc, Location loc, HouseStyle style) {
        buildHome(npc, loc, style, HouseSize.MEDIUM);
    }

    /**
     * Build a home with the specified style and size
     */
    public void buildHome(AINpc npc, Location loc, HouseStyle style, HouseSize size) {
        World world = loc.getWorld();
        if (world == null) return;

        String faction = npc.getFaction() != null ? npc.getFaction().toLowerCase() : "villagers";
        Material[] mats = FACTION_MATERIALS.getOrDefault(faction, FACTION_MATERIALS.get("villagers"));

        int baseX = loc.getBlockX();
        int baseY = loc.getBlockY();
        int baseZ = loc.getBlockZ();

        int homeSize;
        String buildType;

        // Use procedural generation for unique buildings, or template-based for consistency
        if (useProceduralGeneration) {
            // Generate a unique building plan based on NPC's UUID for reproducibility
            ProceduralBuildingGenerator npcGenerator = new ProceduralBuildingGenerator(npc.getUuid().getMostSignificantBits() ^ System.currentTimeMillis());
            ProceduralBuildingGenerator.BuildingPlan plan = npcGenerator.generatePlan(style, size);

            homeSize = npcGenerator.buildFromPlan(world, baseX, baseY, baseZ, plan, mats, faction);
            buildType = plan.toString();

            plugin.debug("Generated procedural building: " + buildType);
        } else {
            // Use template-based building (original method)
            switch (style) {
                case COTTAGE:
                    homeSize = buildCottage(world, baseX, baseY, baseZ, mats, faction, size);
                    break;
                case FARMHOUSE:
                    homeSize = buildFarmhouse(world, baseX, baseY, baseZ, mats, faction, size);
                    break;
                case CABIN:
                    homeSize = buildCabin(world, baseX, baseY, baseZ, mats, faction, size);
                    break;
                case TOWER:
                    homeSize = buildTower(world, baseX, baseY, baseZ, mats, faction, size);
                    break;
                case UNDERGROUND:
                    homeSize = buildUnderground(world, baseX, baseY, baseZ, mats, faction, size);
                    break;
                case VILLA:
                    homeSize = buildVilla(world, baseX, baseY, baseZ, mats, faction, size);
                    break;
                default:
                    homeSize = buildCottage(world, baseX, baseY, baseZ, mats, faction, size);
            }
            buildType = style.name().toLowerCase();
        }

        // Add name sign
        Block signBlock = world.getBlockAt(baseX + 2, baseY + 3, baseZ - 1);
        placeNameSign(signBlock, npc, BlockFace.NORTH);

        // Register home
        NPCHome home = new NPCHome(npc.getUuid(), loc, homeSize, style, size);
        npcHomes.put(npc.getUuid(), home);

        String sizeStr = size.name().toLowerCase();
        // Announce
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(loc) <= 50) {
                player.sendMessage(ChatColor.GOLD + npc.getName() + ChatColor.GRAY +
                        " has built a unique " + sizeStr + " " + style.name().toLowerCase() + " nearby!");
            }
        }

        plugin.debug(npc.getName() + " built a " + sizeStr + " " + buildType + " at " + baseX + ", " + baseY + ", " + baseZ);
    }

    /**
     * Enable or disable procedural generation
     */
    public void setProceduralGeneration(boolean enabled) {
        this.useProceduralGeneration = enabled;
    }

    /**
     * Check if procedural generation is enabled
     */
    public boolean isProceduralGenerationEnabled() {
        return useProceduralGeneration;
    }

    /**
     * Build a cozy cottage with small garden - SIZE AWARE
     */
    private int buildCottage(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction, HouseSize size) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];
        Material roofStairs = mats[3];

        int w = size.width;
        int d = size.depth;
        int wallHeight = size == HouseSize.LARGE ? 4 : 3;

        // Foundation - stone brick base for medium/large
        if (size != HouseSize.SMALL) {
            for (int x = -1; x <= w; x++) {
                for (int z = -1; z <= d; z++) {
                    world.getBlockAt(baseX + x, baseY - 1, baseZ + z).setType(Material.STONE_BRICKS);
                }
            }
        }

        // Floor with carpet pattern for larger homes
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floor);
                // Add carpet in center for medium/large
                if (size != HouseSize.SMALL && x > 1 && x < w-2 && z > 1 && z < d-2) {
                    Material carpet = faction.equals("cultists") ? Material.PURPLE_CARPET :
                                     faction.equals("merchants") ? Material.YELLOW_CARPET :
                                     faction.equals("guards") ? Material.BLUE_CARPET : Material.RED_CARPET;
                    world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(carpet);
                }
            }
        }

        // Walls with windows
        for (int y = 1; y <= wallHeight; y++) {
            // Front and back walls
            for (int x = 0; x < w; x++) {
                // Front wall - door in center
                if (!(x == w/2 && y <= 2)) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                }
                // Back wall with windows
                boolean isWindowLevel = (y == 2) || (size == HouseSize.LARGE && y == 3);
                boolean isWindowPos = (x == w/2) || (size != HouseSize.SMALL && (x == w/4 || x == 3*w/4));
                if (isWindowLevel && isWindowPos) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ + d - 1).setType(Material.GLASS_PANE);
                } else {
                    world.getBlockAt(baseX + x, baseY + y, baseZ + d - 1).setType(wall);
                }
            }
            // Side walls
            for (int z = 1; z < d - 1; z++) {
                boolean isWindowLevel = (y == 2) || (size == HouseSize.LARGE && y == 3);
                boolean isWindowPos = (z == d/2);
                if (isWindowLevel && isWindowPos) {
                    world.getBlockAt(baseX, baseY + y, baseZ + z).setType(Material.GLASS_PANE);
                    world.getBlockAt(baseX + w - 1, baseY + y, baseZ + z).setType(Material.GLASS_PANE);
                } else {
                    world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                    world.getBlockAt(baseX + w - 1, baseY + y, baseZ + z).setType(wall);
                }
            }
        }

        // Decorative corners with trim
        for (int y = 1; y <= wallHeight; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX, baseY + y, baseZ + d - 1).setType(corner);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ + d - 1).setType(corner);
        }

        // Window boxes with flowers (medium and large only)
        if (size != HouseSize.SMALL) {
            addWindowBoxes(world, baseX, baseY, baseZ, w, d);
        }

        // Pitched roof with overhang
        buildPitchedRoof(world, baseX - 1, baseY + wallHeight, baseZ - 1, w + 2, d + 2, roofStairs, wall);

        // Chimney for medium/large
        if (size != HouseSize.SMALL) {
            int chimneyX = baseX + w - 2;
            int chimneyZ = baseZ + d - 2;
            for (int y = 1; y <= wallHeight + 3; y++) {
                world.getBlockAt(chimneyX, baseY + y, chimneyZ).setType(Material.BRICKS);
            }
            world.getBlockAt(chimneyX, baseY + 1, chimneyZ).setType(Material.CAMPFIRE);
        }

        // Interior furnishing
        addInterior(world, baseX, baseY, baseZ, faction, size != HouseSize.SMALL);

        // Porch for medium/large
        if (size != HouseSize.SMALL) {
            buildPorch(world, baseX, baseY, baseZ - 2, w, corner);
        }

        // Garden - size based
        int gardenSize = size == HouseSize.LARGE ? 4 : (size == HouseSize.MEDIUM ? 3 : 2);
        buildFlowerGarden(world, baseX - gardenSize - 1, baseY, baseZ + 1, gardenSize, d - 2);

        // Path to door
        buildPath(world, baseX + w/2, baseY, baseZ - 1, 4);

        return w;
    }

    /**
     * Add window boxes with flowers
     */
    private void addWindowBoxes(World world, int baseX, int baseY, int baseZ, int w, int d) {
        Material[] flowers = {Material.POPPY, Material.DANDELION, Material.BLUE_ORCHID, Material.ALLIUM};

        // Back wall window boxes
        world.getBlockAt(baseX + w/2, baseY + 1, baseZ + d).setType(Material.SPRUCE_TRAPDOOR);
        world.getBlockAt(baseX + w/2, baseY + 2, baseZ + d).setType(flowers[random.nextInt(flowers.length)]);

        // Side wall window boxes
        world.getBlockAt(baseX - 1, baseY + 1, baseZ + d/2).setType(Material.SPRUCE_TRAPDOOR);
        world.getBlockAt(baseX - 1, baseY + 2, baseZ + d/2).setType(flowers[random.nextInt(flowers.length)]);
        world.getBlockAt(baseX + w, baseY + 1, baseZ + d/2).setType(Material.SPRUCE_TRAPDOOR);
        world.getBlockAt(baseX + w, baseY + 2, baseZ + d/2).setType(flowers[random.nextInt(flowers.length)]);
    }

    /**
     * Build a porch in front of the house
     */
    private void buildPorch(World world, int baseX, int baseY, int baseZ, int width, Material fenceMaterial) {
        // Porch floor
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < 2; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(Material.SPRUCE_PLANKS);
            }
        }

        // Fence posts at corners
        world.getBlockAt(baseX, baseY + 1, baseZ).setType(Material.OAK_FENCE);
        world.getBlockAt(baseX + width - 1, baseY + 1, baseZ).setType(Material.OAK_FENCE);

        // Lanterns on posts
        world.getBlockAt(baseX, baseY + 2, baseZ).setType(Material.LANTERN);
        world.getBlockAt(baseX + width - 1, baseY + 2, baseZ).setType(Material.LANTERN);
    }

    /**
     * Build a path from the door
     */
    private void buildPath(World world, int startX, int baseY, int startZ, int length) {
        for (int i = 0; i < length; i++) {
            world.getBlockAt(startX, baseY, startZ - i).setType(Material.GRAVEL);
            // Occasional path border
            if (i % 2 == 0) {
                world.getBlockAt(startX - 1, baseY, startZ - i).setType(Material.COBBLESTONE);
                world.getBlockAt(startX + 1, baseY, startZ - i).setType(Material.COBBLESTONE);
            }
        }
    }

    /**
     * Build a farmhouse with attached farm - SIZE AWARE
     */
    private int buildFarmhouse(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction, HouseSize size) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];
        Material roofStairs = mats[3];

        // Size-based dimensions
        int w = size == HouseSize.LARGE ? 10 : (size == HouseSize.MEDIUM ? 8 : 6);
        int d = size == HouseSize.LARGE ? 8 : (size == HouseSize.MEDIUM ? 7 : 5);
        int wallHeight = size == HouseSize.LARGE ? 4 : 3;

        // Stone foundation for medium/large
        if (size != HouseSize.SMALL) {
            for (int x = -1; x <= w; x++) {
                for (int z = -1; z <= d; z++) {
                    world.getBlockAt(baseX + x, baseY - 1, baseZ + z).setType(Material.COBBLESTONE);
                }
            }
        }

        // Floor with stone border
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean isBorder = (x == 0 || x == w-1 || z == 0 || z == d-1);
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(isBorder ? Material.STONE_BRICKS : floor);
            }
        }

        // Walls with windows
        for (int y = 1; y <= wallHeight; y++) {
            // Front and back walls
            for (int x = 0; x < w; x++) {
                // Front wall - door in center
                int doorX = w/2;
                if (!(x == doorX && y <= 2)) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                }
                // Back wall with windows
                boolean isWindowLevel = (y == 2);
                boolean isWindowPos = (x == w/3 || x == 2*w/3);
                if (isWindowLevel && isWindowPos) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ + d - 1).setType(Material.GLASS_PANE);
                } else {
                    world.getBlockAt(baseX + x, baseY + y, baseZ + d - 1).setType(wall);
                }
            }
            // Side walls with windows
            for (int z = 1; z < d - 1; z++) {
                boolean isWindowLevel = (y == 2);
                boolean isWindowPos = (z == d/2);
                if (isWindowLevel && isWindowPos) {
                    world.getBlockAt(baseX, baseY + y, baseZ + z).setType(Material.GLASS_PANE);
                    world.getBlockAt(baseX + w - 1, baseY + y, baseZ + z).setType(Material.GLASS_PANE);
                } else {
                    world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                    world.getBlockAt(baseX + w - 1, baseY + y, baseZ + z).setType(wall);
                }
            }
        }

        // Decorative log corners
        for (int y = 1; y <= wallHeight; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX, baseY + y, baseZ + d - 1).setType(corner);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ + d - 1).setType(corner);
        }

        // Decorative beam along top of walls
        for (int x = 1; x < w - 1; x++) {
            world.getBlockAt(baseX + x, baseY + wallHeight, baseZ).setType(corner);
            world.getBlockAt(baseX + x, baseY + wallHeight, baseZ + d - 1).setType(corner);
        }

        // Roof with overhang
        buildPitchedRoof(world, baseX - 1, baseY + wallHeight, baseZ - 1, w + 2, d + 2, roofStairs, wall);

        // Chimney with smoke (campfire)
        int chimneyX = baseX + w - 2;
        int chimneyZ = baseZ + d - 2;
        for (int y = 1; y <= wallHeight + 3; y++) {
            world.getBlockAt(chimneyX, baseY + y, chimneyZ).setType(Material.BRICKS);
        }
        world.getBlockAt(chimneyX, baseY + 1, chimneyZ).setType(Material.CAMPFIRE);

        // Interior furnishing
        addFarmhouseInterior(world, baseX, baseY, baseZ, w, d, faction, size);

        // Porch with bench
        buildPorch(world, baseX, baseY, baseZ - 2, w, corner);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ - 1).setType(Material.SPRUCE_STAIRS);
        world.getBlockAt(baseX + 2, baseY + 1, baseZ - 1).setType(Material.SPRUCE_STAIRS);

        // Build farm behind house - size based
        int farmWidth = size == HouseSize.LARGE ? 12 : (size == HouseSize.MEDIUM ? 10 : 7);
        int farmDepth = size == HouseSize.LARGE ? 10 : (size == HouseSize.MEDIUM ? 8 : 5);
        buildFarm(world, baseX - 1, baseY, baseZ + d + 2, farmWidth, farmDepth);

        // Animal pen on the side for medium/large
        if (size != HouseSize.SMALL) {
            buildAnimalPen(world, baseX + w + 2, baseY, baseZ + 1, 5, 4);
        }

        // Silo for large farmhouses
        if (size == HouseSize.LARGE) {
            buildSilo(world, baseX - 4, baseY, baseZ + 2);
        }

        // Path from door to farm
        buildPath(world, baseX + w/2, baseY, baseZ - 1, 3);

        return w;
    }

    /**
     * Add farmhouse-specific interior
     */
    private void addFarmhouseInterior(World world, int baseX, int baseY, int baseZ, int w, int d, String faction, HouseSize size) {
        Material bed = FACTION_BEDS.getOrDefault(faction.toLowerCase(), Material.WHITE_BED);

        // Kitchen area
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(Material.FURNACE);
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + 1).setType(Material.SMOKER);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 2).setType(Material.BARREL);

        // Dining table
        world.getBlockAt(baseX + w/2, baseY + 1, baseZ + d/2).setType(Material.DARK_OAK_PRESSURE_PLATE);
        world.getBlockAt(baseX + w/2, baseY, baseZ + d/2).setType(Material.OAK_FENCE);

        // Bedroom area
        placeBed(world, baseX + w - 2, baseY + 1, baseZ + d - 2, bed, BlockFace.NORTH);
        world.getBlockAt(baseX + w - 2, baseY + 1, baseZ + 1).setType(Material.CHEST);

        // Crafting area
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + d - 2).setType(Material.CRAFTING_TABLE);
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + d - 2).setType(Material.LOOM);

        // Lighting
        world.getBlockAt(baseX + w/2, baseY + 2, baseZ + d/2).setType(Material.LANTERN);
        if (size != HouseSize.SMALL) {
            world.getBlockAt(baseX + 2, baseY + 2, baseZ + 1).setType(Material.LANTERN);
            world.getBlockAt(baseX + w - 3, baseY + 2, baseZ + d - 2).setType(Material.LANTERN);
        }

        // Carpet runner in larger homes
        if (size != HouseSize.SMALL) {
            for (int z = 2; z < d - 2; z++) {
                world.getBlockAt(baseX + w/2, baseY + 1, baseZ + z).setType(Material.BROWN_CARPET);
            }
        }
    }

    /**
     * Build a small animal pen
     */
    private void buildAnimalPen(World world, int baseX, int baseY, int baseZ, int w, int d) {
        // Fence perimeter
        for (int x = 0; x < w; x++) {
            world.getBlockAt(baseX + x, baseY + 1, baseZ).setType(Material.OAK_FENCE);
            world.getBlockAt(baseX + x, baseY + 1, baseZ + d - 1).setType(Material.OAK_FENCE);
        }
        for (int z = 1; z < d - 1; z++) {
            world.getBlockAt(baseX, baseY + 1, baseZ + z).setType(Material.OAK_FENCE);
            world.getBlockAt(baseX + w - 1, baseY + 1, baseZ + z).setType(Material.OAK_FENCE);
        }

        // Gate
        world.getBlockAt(baseX + w/2, baseY + 1, baseZ).setType(Material.OAK_FENCE_GATE);

        // Feeding trough
        world.getBlockAt(baseX + w/2, baseY + 1, baseZ + d - 2).setType(Material.CAULDRON);

        // Hay for animals
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(Material.HAY_BLOCK);
    }

    /**
     * Build a grain silo
     */
    private void buildSilo(World world, int baseX, int baseY, int baseZ) {
        // Circular base (3x3)
        for (int y = 0; y < 7; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(Material.STRIPPED_OAK_LOG);
            world.getBlockAt(baseX + 2, baseY + y, baseZ).setType(Material.STRIPPED_OAK_LOG);
            world.getBlockAt(baseX, baseY + y, baseZ + 2).setType(Material.STRIPPED_OAK_LOG);
            world.getBlockAt(baseX + 2, baseY + y, baseZ + 2).setType(Material.STRIPPED_OAK_LOG);
            world.getBlockAt(baseX + 1, baseY + y, baseZ).setType(Material.OAK_PLANKS);
            world.getBlockAt(baseX + 1, baseY + y, baseZ + 2).setType(Material.OAK_PLANKS);
            world.getBlockAt(baseX, baseY + y, baseZ + 1).setType(Material.OAK_PLANKS);
            world.getBlockAt(baseX + 2, baseY + y, baseZ + 1).setType(Material.OAK_PLANKS);
        }

        // Fill with hay
        for (int y = 1; y < 5; y++) {
            world.getBlockAt(baseX + 1, baseY + y, baseZ + 1).setType(Material.HAY_BLOCK);
        }

        // Conical roof
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                world.getBlockAt(baseX + x, baseY + 7, baseZ + z).setType(Material.DARK_OAK_SLAB);
            }
        }
        world.getBlockAt(baseX + 1, baseY + 8, baseZ + 1).setType(Material.DARK_OAK_SLAB);

        // Door
        world.getBlockAt(baseX + 1, baseY + 1, baseZ).setType(Material.AIR);
        world.getBlockAt(baseX + 1, baseY + 2, baseZ).setType(Material.AIR);
    }

    /**
     * Build a rustic log cabin - SIZE AWARE
     */
    private int buildCabin(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction, HouseSize size) {
        Material corner = mats[1]; // Use logs as main material
        Material floor = mats[2];
        Material wall = mats[0];

        // Size-based dimensions
        int w = size == HouseSize.LARGE ? 9 : (size == HouseSize.MEDIUM ? 7 : 5);
        int d = size == HouseSize.LARGE ? 8 : (size == HouseSize.MEDIUM ? 6 : 5);
        int wallHeight = size == HouseSize.LARGE ? 4 : 3;

        // Raised stone foundation
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                world.getBlockAt(baseX + x, baseY - 1, baseZ + z).setType(Material.COBBLESTONE);
            }
        }

        // Floor with alternating plank pattern
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                Material floorMat = ((x + z) % 2 == 0) ? Material.SPRUCE_PLANKS : Material.DARK_OAK_PLANKS;
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floorMat);
            }
        }

        // Log walls with notched corners (true cabin style)
        for (int y = 1; y <= wallHeight; y++) {
            // Horizontal logs on front/back
            for (int x = 0; x < w; x++) {
                // Door on front
                int doorX = w/2;
                if (!(x == doorX && y <= 2)) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ).setType(corner);
                }
                world.getBlockAt(baseX + x, baseY + y, baseZ + d - 1).setType(corner);
            }
            // Horizontal logs on sides
            for (int z = 1; z < d - 1; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(corner);
                world.getBlockAt(baseX + w - 1, baseY + y, baseZ + z).setType(corner);
            }
        }

        // Notched corner extensions (protruding logs)
        for (int y = 1; y <= wallHeight; y += 2) {
            world.getBlockAt(baseX - 1, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX + w, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX - 1, baseY + y, baseZ + d - 1).setType(corner);
            world.getBlockAt(baseX + w, baseY + y, baseZ + d - 1).setType(corner);
        }

        // Windows cut into logs
        if (size != HouseSize.SMALL) {
            world.getBlockAt(baseX + w/2, baseY + 2, baseZ + d - 1).setType(Material.GLASS_PANE);
            world.getBlockAt(baseX, baseY + 2, baseZ + d/2).setType(Material.GLASS_PANE);
            world.getBlockAt(baseX + w - 1, baseY + 2, baseZ + d/2).setType(Material.GLASS_PANE);
        }

        // Sloped roof with overhang
        for (int layer = 0; layer <= w/2 + 1; layer++) {
            int y = baseY + wallHeight + layer;
            for (int z = -1; z <= d; z++) {
                // Left slope
                if (layer <= w/2) {
                    world.getBlockAt(baseX + layer - 1, y, baseZ + z).setType(
                        z == -1 || z == d ? Material.SPRUCE_PLANKS : corner);
                }
                // Right slope
                if (layer <= w/2) {
                    world.getBlockAt(baseX + w - layer, y, baseZ + z).setType(
                        z == -1 || z == d ? Material.SPRUCE_PLANKS : corner);
                }
            }
        }

        // Ridge cap
        for (int z = -1; z <= d; z++) {
            world.getBlockAt(baseX + w/2, baseY + wallHeight + w/2 + 1, baseZ + z).setType(Material.SPRUCE_SLAB);
        }

        // Large stone chimney
        int chimneyX = baseX + w - 2;
        int chimneyZ = baseZ + d - 2;
        for (int y = 1; y <= wallHeight + w/2 + 2; y++) {
            world.getBlockAt(chimneyX, baseY + y, chimneyZ).setType(Material.COBBLESTONE);
            world.getBlockAt(chimneyX + 1, baseY + y, chimneyZ).setType(Material.COBBLESTONE);
            if (y <= 2) {
                world.getBlockAt(chimneyX, baseY + y, chimneyZ + 1).setType(Material.COBBLESTONE);
                world.getBlockAt(chimneyX + 1, baseY + y, chimneyZ + 1).setType(Material.COBBLESTONE);
            }
        }
        // Fireplace interior
        world.getBlockAt(chimneyX, baseY + 1, chimneyZ).setType(Material.CAMPFIRE);
        world.getBlockAt(chimneyX + 1, baseY + 1, chimneyZ).setType(Material.AIR);

        // Interior furnishing
        addCabinInterior(world, baseX, baseY, baseZ, w, d, faction, size);

        // Front porch with awning
        for (int x = 0; x < w; x++) {
            world.getBlockAt(baseX + x, baseY, baseZ - 1).setType(Material.SPRUCE_PLANKS);
            world.getBlockAt(baseX + x, baseY, baseZ - 2).setType(Material.SPRUCE_PLANKS);
        }
        // Porch posts
        world.getBlockAt(baseX, baseY + 1, baseZ - 2).setType(corner);
        world.getBlockAt(baseX + w - 1, baseY + 1, baseZ - 2).setType(corner);
        world.getBlockAt(baseX, baseY + 2, baseZ - 2).setType(corner);
        world.getBlockAt(baseX + w - 1, baseY + 2, baseZ - 2).setType(corner);
        // Porch roof
        for (int x = 0; x < w; x++) {
            world.getBlockAt(baseX + x, baseY + 3, baseZ - 2).setType(Material.SPRUCE_SLAB);
            world.getBlockAt(baseX + x, baseY + 3, baseZ - 1).setType(Material.SPRUCE_SLAB);
        }

        // Rocking chairs on porch
        world.getBlockAt(baseX + 1, baseY + 1, baseZ - 2).setType(Material.SPRUCE_STAIRS);
        if (w > 5) {
            world.getBlockAt(baseX + w - 2, baseY + 1, baseZ - 2).setType(Material.SPRUCE_STAIRS);
        }

        // Woodpile next to cabin
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                world.getBlockAt(baseX + w + 2 + x, baseY + 1, baseZ + z).setType(corner);
            }
        }

        // Axe (item frame alternative - use a fence with sign)
        world.getBlockAt(baseX + w + 2, baseY + 2, baseZ).setType(Material.OAK_FENCE);

        return w;
    }

    /**
     * Add cabin-specific cozy interior
     */
    private void addCabinInterior(World world, int baseX, int baseY, int baseZ, int w, int d, String faction, HouseSize size) {
        Material bed = FACTION_BEDS.getOrDefault(faction.toLowerCase(), Material.BROWN_BED);

        // Bear rug (brown carpet)
        for (int x = 1; x < w - 2; x++) {
            for (int z = 1; z < d - 2; z++) {
                if (x == w/2 - 1 || x == w/2 || x == w/2 + 1) {
                    world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(Material.BROWN_CARPET);
                }
            }
        }

        // Bed in corner
        placeBed(world, baseX + 1, baseY + 1, baseZ + d - 2, bed, BlockFace.NORTH);

        // Dining table near fireplace
        world.getBlockAt(baseX + w - 4, baseY + 1, baseZ + 2).setType(Material.SPRUCE_PRESSURE_PLATE);
        world.getBlockAt(baseX + w - 4, baseY, baseZ + 2).setType(Material.SPRUCE_FENCE);

        // Chairs
        world.getBlockAt(baseX + w - 5, baseY + 1, baseZ + 2).setType(Material.SPRUCE_STAIRS);
        world.getBlockAt(baseX + w - 3, baseY + 1, baseZ + 2).setType(Material.SPRUCE_STAIRS);

        // Storage
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(Material.CHEST);
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + 1).setType(Material.BARREL);

        // Crafting station
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 2).setType(Material.CRAFTING_TABLE);

        // Weapon rack (fence with banner)
        world.getBlockAt(baseX + 1, baseY + 2, baseZ + 1).setType(Material.OAK_FENCE);

        // Lanterns
        world.getBlockAt(baseX + w/2, baseY + 2, baseZ + d/2).setType(Material.LANTERN);

        // Bookshelf for larger cabins
        if (size != HouseSize.SMALL) {
            world.getBlockAt(baseX + 3, baseY + 1, baseZ + d - 2).setType(Material.BOOKSHELF);
            world.getBlockAt(baseX + 3, baseY + 2, baseZ + d - 2).setType(Material.BOOKSHELF);
        }
    }

    /**
     * Build a tall tower - SIZE AWARE
     */
    private int buildTower(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction, HouseSize size) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];
        Material roofStairs = mats[3];

        // Size-based dimensions
        int w = size == HouseSize.LARGE ? 6 : (size == HouseSize.MEDIUM ? 5 : 4);
        int height = size == HouseSize.LARGE ? 12 : (size == HouseSize.MEDIUM ? 9 : 6);
        int floors = size == HouseSize.LARGE ? 3 : (size == HouseSize.MEDIUM ? 2 : 1);

        // Stone foundation
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= w; z++) {
                world.getBlockAt(baseX + x, baseY - 1, baseZ + z).setType(Material.STONE_BRICKS);
            }
        }

        // Build tower walls
        for (int y = 0; y <= height; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < w; z++) {
                    if (y == 0) {
                        // Ground floor
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(floor);
                    } else if (x == 0 || x == w - 1 || z == 0 || z == w - 1) {
                        // Walls
                        // Door
                        if (x == w/2 && z == 0 && y <= 2) {
                            continue;
                        }
                        // Windows at each floor level
                        boolean isWindowLevel = (y % 4 == 2 || y % 4 == 3);
                        boolean isWindowPos = (x == w/2 || z == w/2);
                        boolean isEdge = (x == 0 || x == w-1) && (z == 0 || z == w-1);

                        if (isWindowLevel && isWindowPos && !isEdge && y > 3) {
                            world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.GLASS_PANE);
                        } else {
                            world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(wall);
                        }
                    }
                }
            }
        }

        // Reinforced stone corners
        for (int y = 1; y <= height; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX, baseY + y, baseZ + w - 1).setType(corner);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ + w - 1).setType(corner);
        }

        // Interior floors with ladders
        for (int f = 1; f < floors; f++) {
            int floorY = baseY + (f * 4);
            // Floor
            for (int x = 1; x < w - 1; x++) {
                for (int z = 1; z < w - 1; z++) {
                    if (!(x == w/2 && z == w/2)) { // Leave hole for ladder
                        world.getBlockAt(baseX + x, floorY, baseZ + z).setType(floor);
                    }
                }
            }
            // Ladder through floors
            world.getBlockAt(baseX + w/2, floorY - 1, baseZ + w - 2).setType(Material.LADDER);
            world.getBlockAt(baseX + w/2, floorY, baseZ + w - 2).setType(Material.LADDER);
            world.getBlockAt(baseX + w/2, floorY + 1, baseZ + w - 2).setType(Material.LADDER);
        }

        // Crenellated battlements
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= w; z++) {
                world.getBlockAt(baseX + x, baseY + height + 1, baseZ + z).setType(floor);
            }
        }
        // Merlons (raised parts of battlements)
        for (int x = -1; x <= w; x += 2) {
            world.getBlockAt(baseX + x, baseY + height + 2, baseZ - 1).setType(wall);
            world.getBlockAt(baseX + x, baseY + height + 2, baseZ + w).setType(wall);
        }
        for (int z = 0; z < w; z += 2) {
            world.getBlockAt(baseX - 1, baseY + height + 2, baseZ + z).setType(wall);
            world.getBlockAt(baseX + w, baseY + height + 2, baseZ + z).setType(wall);
        }

        // Corner turrets for medium/large
        if (size != HouseSize.SMALL) {
            buildCornerTurret(world, baseX - 2, baseY + height, baseZ - 2, wall, corner);
            buildCornerTurret(world, baseX + w, baseY + height, baseZ - 2, wall, corner);
            buildCornerTurret(world, baseX - 2, baseY + height, baseZ + w, wall, corner);
            buildCornerTurret(world, baseX + w, baseY + height, baseZ + w, wall, corner);
        }

        // Flag pole on top
        world.getBlockAt(baseX + w/2, baseY + height + 2, baseZ + w/2).setType(Material.OAK_FENCE);
        world.getBlockAt(baseX + w/2, baseY + height + 3, baseZ + w/2).setType(Material.OAK_FENCE);
        // Banner color by faction
        Material banner = faction.equalsIgnoreCase("guards") ? Material.BLUE_BANNER :
                         faction.equalsIgnoreCase("cultists") ? Material.PURPLE_BANNER :
                         faction.equalsIgnoreCase("bandits") ? Material.BLACK_BANNER : Material.WHITE_BANNER;
        world.getBlockAt(baseX + w/2 + 1, baseY + height + 3, baseZ + w/2).setType(banner);

        // Interior furnishing - ground floor
        addTowerInterior(world, baseX, baseY, baseZ, w, height, faction, size);

        // Torch sconces on exterior
        if (size != HouseSize.SMALL) {
            world.getBlockAt(baseX - 1, baseY + 3, baseZ + w/2).setType(Material.WALL_TORCH);
            world.getBlockAt(baseX + w, baseY + 3, baseZ + w/2).setType(Material.WALL_TORCH);
        }

        return w;
    }

    /**
     * Build a small corner turret
     */
    private void buildCornerTurret(World world, int baseX, int baseY, int baseZ, Material wall, Material corner) {
        // 2x2 raised platform
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(wall);
                world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(Material.AIR);
            }
        }
        // Corner posts
        world.getBlockAt(baseX, baseY + 1, baseZ).setType(corner);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ).setType(corner);
        world.getBlockAt(baseX, baseY + 1, baseZ + 1).setType(corner);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(corner);
        // Conical roof
        world.getBlockAt(baseX, baseY + 2, baseZ).setType(Material.STONE_BRICK_SLAB);
        world.getBlockAt(baseX + 1, baseY + 2, baseZ).setType(Material.STONE_BRICK_SLAB);
        world.getBlockAt(baseX, baseY + 2, baseZ + 1).setType(Material.STONE_BRICK_SLAB);
        world.getBlockAt(baseX + 1, baseY + 2, baseZ + 1).setType(Material.STONE_BRICK_SLAB);
    }

    /**
     * Add tower-specific interior
     */
    private void addTowerInterior(World world, int baseX, int baseY, int baseZ, int w, int height, String faction, HouseSize size) {
        Material bed = FACTION_BEDS.getOrDefault(faction.toLowerCase(), Material.WHITE_BED);

        // Ground floor - storage/armory
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(Material.CHEST);
        world.getBlockAt(baseX + w - 2, baseY + 1, baseZ + 1).setType(Material.BARREL);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + w - 2).setType(Material.ARMOR_STAND);
        world.getBlockAt(baseX + w/2, baseY + 2, baseZ + w/2).setType(Material.LANTERN);

        // Ladder to upper floors
        for (int y = 1; y < 4; y++) {
            world.getBlockAt(baseX + w/2, baseY + y, baseZ + w - 2).setType(Material.LADDER);
        }

        if (size != HouseSize.SMALL) {
            // Second floor - living quarters
            int floor2Y = baseY + 4;
            placeBed(world, baseX + 1, floor2Y + 1, baseZ + w - 2, bed, BlockFace.NORTH);
            world.getBlockAt(baseX + w - 2, floor2Y + 1, baseZ + 1).setType(Material.CRAFTING_TABLE);
            world.getBlockAt(baseX + w/2, floor2Y + 2, baseZ + w/2).setType(Material.LANTERN);
        }

        if (size == HouseSize.LARGE) {
            // Third floor - study/observatory
            int floor3Y = baseY + 8;
            world.getBlockAt(baseX + 1, floor3Y + 1, baseZ + 1).setType(Material.BOOKSHELF);
            world.getBlockAt(baseX + 1, floor3Y + 2, baseZ + 1).setType(Material.BOOKSHELF);
            world.getBlockAt(baseX + w - 2, floor3Y + 1, baseZ + w - 2).setType(Material.ENCHANTING_TABLE);
            world.getBlockAt(baseX + w/2, floor3Y + 2, baseZ + w/2).setType(Material.LANTERN);
        }
    }

    /**
     * Build an underground hobbit-style home - SIZE AWARE
     */
    private int buildUnderground(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction, HouseSize size) {
        Material wall = mats[0];
        Material floor = mats[2];
        Material corner = mats[1];

        // Size-based dimensions
        int w = size == HouseSize.LARGE ? 10 : (size == HouseSize.MEDIUM ? 7 : 5);
        int d = size == HouseSize.LARGE ? 9 : (size == HouseSize.MEDIUM ? 6 : 5);
        int depth = size == HouseSize.LARGE ? 5 : (size == HouseSize.MEDIUM ? 4 : 3);
        int rooms = size == HouseSize.LARGE ? 3 : (size == HouseSize.MEDIUM ? 2 : 1);

        // Dig out main room interior
        for (int y = -depth; y <= 0; y++) {
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    if (y == -depth) {
                        // Floor with pattern
                        boolean isBorder = (x == 0 || x == w-1 || z == 0 || z == d-1);
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(isBorder ? Material.STONE_BRICKS : floor);
                    } else if (y == 0) {
                        // Ceiling - grass on top with skylights
                        boolean isSkylight = (x == w/2 && z == d/2) ||
                                           (size != HouseSize.SMALL && x == w/3 && z == d/3);
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(
                            isSkylight ? Material.GLASS : Material.GRASS_BLOCK);
                    } else {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.AIR);
                    }
                }
            }
        }

        // Stone brick walls with arched ceiling effect
        for (int y = -depth; y < 0; y++) {
            for (int x = 0; x < w; x++) {
                world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                world.getBlockAt(baseX + x, baseY + y, baseZ + d - 1).setType(wall);
            }
            for (int z = 0; z < d; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                world.getBlockAt(baseX + w - 1, baseY + y, baseZ + z).setType(wall);
            }
        }

        // Arched ceiling support beams
        for (int z = 1; z < d - 1; z += 3) {
            for (int x = 1; x < w - 1; x++) {
                world.getBlockAt(baseX + x, baseY - 1, baseZ + z).setType(corner);
            }
        }

        // Round hobbit door entrance (iconic circular shape)
        int doorX = w/2;
        // Clear entrance passage
        for (int i = 0; i < depth; i++) {
            world.getBlockAt(baseX + doorX - 1, baseY - i - 1, baseZ - 1 - i).setType(Material.AIR);
            world.getBlockAt(baseX + doorX, baseY - i - 1, baseZ - 1 - i).setType(Material.AIR);
            world.getBlockAt(baseX + doorX + 1, baseY - i - 1, baseZ - 1 - i).setType(Material.AIR);
            world.getBlockAt(baseX + doorX - 1, baseY - i - 2, baseZ - 1 - i).setType(Material.AIR);
            world.getBlockAt(baseX + doorX, baseY - i - 2, baseZ - 1 - i).setType(Material.AIR);
            world.getBlockAt(baseX + doorX + 1, baseY - i - 2, baseZ - 1 - i).setType(Material.AIR);
        }

        // Entrance stairs with stone walls
        for (int i = 0; i < depth; i++) {
            // Steps
            world.getBlockAt(baseX + doorX - 1, baseY - i, baseZ - 1 - i).setType(Material.STONE_BRICK_STAIRS);
            world.getBlockAt(baseX + doorX, baseY - i, baseZ - 1 - i).setType(Material.STONE_BRICK_STAIRS);
            world.getBlockAt(baseX + doorX + 1, baseY - i, baseZ - 1 - i).setType(Material.STONE_BRICK_STAIRS);
            // Side walls
            world.getBlockAt(baseX + doorX - 2, baseY - i, baseZ - 1 - i).setType(Material.STONE_BRICKS);
            world.getBlockAt(baseX + doorX + 2, baseY - i, baseZ - 1 - i).setType(Material.STONE_BRICKS);
            world.getBlockAt(baseX + doorX - 2, baseY - i - 1, baseZ - 1 - i).setType(Material.STONE_BRICKS);
            world.getBlockAt(baseX + doorX + 2, baseY - i - 1, baseZ - 1 - i).setType(Material.STONE_BRICKS);
        }

        // Round door frame (using wood to simulate circular door)
        world.getBlockAt(baseX + doorX - 1, baseY - depth, baseZ).setType(corner);
        world.getBlockAt(baseX + doorX + 1, baseY - depth, baseZ).setType(corner);
        world.getBlockAt(baseX + doorX - 1, baseY - depth + 1, baseZ).setType(corner);
        world.getBlockAt(baseX + doorX + 1, baseY - depth + 1, baseZ).setType(corner);
        world.getBlockAt(baseX + doorX, baseY - depth + 2, baseZ).setType(corner);
        // Door opening
        world.getBlockAt(baseX + doorX, baseY - depth, baseZ).setType(Material.AIR);
        world.getBlockAt(baseX + doorX, baseY - depth + 1, baseZ).setType(Material.AIR);

        // Decorative hill on top with flowers
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                if (random.nextDouble() < 0.3) {
                    Material flower = random.nextBoolean() ? Material.DANDELION : Material.POPPY;
                    world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(flower);
                }
            }
        }

        // Lantern posts at entrance
        world.getBlockAt(baseX + doorX - 3, baseY + 1, baseZ - depth).setType(Material.OAK_FENCE);
        world.getBlockAt(baseX + doorX - 3, baseY + 2, baseZ - depth).setType(Material.LANTERN);
        world.getBlockAt(baseX + doorX + 3, baseY + 1, baseZ - depth).setType(Material.OAK_FENCE);
        world.getBlockAt(baseX + doorX + 3, baseY + 2, baseZ - depth).setType(Material.LANTERN);

        // Interior furnishing
        addUndergroundInterior(world, baseX, baseY, baseZ, w, d, depth, faction, size);

        // Side rooms for medium/large
        if (size != HouseSize.SMALL) {
            // Storage cellar
            buildStorageCellar(world, baseX + w, baseY - depth, baseZ + 1, faction);
        }
        if (size == HouseSize.LARGE) {
            // Wine cellar / pantry
            buildWineCellar(world, baseX - 5, baseY - depth, baseZ + 1, faction);
        }

        return w;
    }

    /**
     * Add cozy underground interior
     */
    private void addUndergroundInterior(World world, int baseX, int baseY, int baseZ, int w, int d, int depth, String faction, HouseSize size) {
        Material bed = FACTION_BEDS.getOrDefault(faction.toLowerCase(), Material.GREEN_BED);
        int floorY = baseY - depth + 1;

        // Cozy carpet throughout
        for (int x = 2; x < w - 2; x++) {
            for (int z = 2; z < d - 2; z++) {
                world.getBlockAt(baseX + x, floorY, baseZ + z).setType(Material.GREEN_CARPET);
            }
        }

        // Bedroom area (back corner)
        placeBed(world, baseX + 1, floorY, baseZ + d - 2, bed, BlockFace.NORTH);
        world.getBlockAt(baseX + 1, floorY, baseZ + d - 4).setType(Material.CHEST);

        // Living area with fireplace
        world.getBlockAt(baseX + w/2, floorY, baseZ + d - 2).setType(Material.CAMPFIRE);
        world.getBlockAt(baseX + w/2, floorY + 1, baseZ + d - 1).setType(Material.BRICKS);
        world.getBlockAt(baseX + w/2, floorY + 2, baseZ + d - 1).setType(Material.BRICKS);
        // Comfy chair by fire
        world.getBlockAt(baseX + w/2 - 1, floorY, baseZ + d - 3).setType(Material.SPRUCE_STAIRS);
        world.getBlockAt(baseX + w/2 + 1, floorY, baseZ + d - 3).setType(Material.SPRUCE_STAIRS);

        // Kitchen area
        world.getBlockAt(baseX + w - 2, floorY, baseZ + 1).setType(Material.FURNACE);
        world.getBlockAt(baseX + w - 2, floorY, baseZ + 2).setType(Material.CRAFTING_TABLE);
        world.getBlockAt(baseX + w - 2, floorY, baseZ + 3).setType(Material.BARREL);

        // Dining table
        world.getBlockAt(baseX + w/2, floorY, baseZ + d/2).setType(Material.OAK_PRESSURE_PLATE);
        world.getBlockAt(baseX + w/2, floorY - 1, baseZ + d/2).setType(Material.OAK_FENCE);

        // Bookshelves built into walls
        if (size != HouseSize.SMALL) {
            world.getBlockAt(baseX + 1, floorY, baseZ + 1).setType(Material.BOOKSHELF);
            world.getBlockAt(baseX + 1, floorY + 1, baseZ + 1).setType(Material.BOOKSHELF);
            world.getBlockAt(baseX + 2, floorY, baseZ + 1).setType(Material.BOOKSHELF);
        }

        // Hanging lanterns
        world.getBlockAt(baseX + w/2, floorY + depth - 2, baseZ + d/2).setType(Material.LANTERN);
        if (size != HouseSize.SMALL) {
            world.getBlockAt(baseX + 2, floorY + depth - 2, baseZ + 2).setType(Material.LANTERN);
            world.getBlockAt(baseX + w - 3, floorY + depth - 2, baseZ + d - 3).setType(Material.LANTERN);
        }

        // Potted plants
        world.getBlockAt(baseX + 1, floorY, baseZ + d/2).setType(Material.POTTED_FERN);
        world.getBlockAt(baseX + w - 2, floorY, baseZ + d/2).setType(Material.POTTED_OAK_SAPLING);
    }

    /**
     * Build a small storage cellar room
     */
    private void buildStorageCellar(World world, int baseX, int baseY, int baseZ, String faction) {
        // Dig out room (4x4)
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    if (y == 0) {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.STONE_BRICKS);
                    } else {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.AIR);
                    }
                }
            }
        }
        // Walls
        for (int y = 1; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                world.getBlockAt(baseX + x, baseY + y, baseZ + 3).setType(Material.STONE_BRICKS);
            }
            world.getBlockAt(baseX + 3, baseY + y, baseZ).setType(Material.STONE_BRICKS);
            world.getBlockAt(baseX + 3, baseY + y, baseZ + 1).setType(Material.STONE_BRICKS);
            world.getBlockAt(baseX + 3, baseY + y, baseZ + 2).setType(Material.STONE_BRICKS);
        }
        // Ceiling
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                world.getBlockAt(baseX + x, baseY + 3, baseZ + z).setType(Material.STONE_BRICKS);
            }
        }
        // Storage
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 2).setType(Material.CHEST);
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + 2).setType(Material.BARREL);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(Material.BARREL);
    }

    /**
     * Build a wine cellar / pantry
     */
    private void buildWineCellar(World world, int baseX, int baseY, int baseZ, String faction) {
        // Dig out room (4x5)
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 5; z++) {
                    if (y == 0) {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.STONE_BRICKS);
                    } else {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.AIR);
                    }
                }
            }
        }
        // Walls
        for (int y = 1; y < 3; y++) {
            for (int z = 0; z < 5; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(Material.STONE_BRICKS);
            }
            for (int x = 0; x < 4; x++) {
                world.getBlockAt(baseX + x, baseY + y, baseZ + 4).setType(Material.STONE_BRICKS);
            }
        }
        // Ceiling
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 5; z++) {
                world.getBlockAt(baseX + x, baseY + 3, baseZ + z).setType(Material.STONE_BRICKS);
            }
        }
        // Wine barrels
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(Material.BARREL);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 2).setType(Material.BARREL);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 3).setType(Material.BARREL);
        world.getBlockAt(baseX + 1, baseY + 2, baseZ + 1).setType(Material.BARREL);
        world.getBlockAt(baseX + 1, baseY + 2, baseZ + 2).setType(Material.BARREL);
        // Cheese/food storage
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + 1).setType(Material.SMOKER);
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + 2).setType(Material.CHEST);
    }

    /**
     * Build a larger villa - SIZE AWARE
     */
    private int buildVilla(World world, int baseX, int baseY, int baseZ, Material[] mats, String faction, HouseSize size) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floorMat = mats[2];
        Material roofStairs = mats[3];

        // Size-based dimensions
        int w = size == HouseSize.LARGE ? 14 : (size == HouseSize.MEDIUM ? 11 : 8);
        int d = size == HouseSize.LARGE ? 11 : (size == HouseSize.MEDIUM ? 9 : 6);
        int stories = size == HouseSize.LARGE ? 3 : 2;
        int wallHeight = stories * 4 - 1;

        // Grand stone foundation with steps
        for (int x = -2; x <= w + 1; x++) {
            for (int z = -2; z <= d + 1; z++) {
                world.getBlockAt(baseX + x, baseY - 1, baseZ + z).setType(Material.STONE_BRICKS);
            }
        }

        // Decorative floor with checkerboard pattern
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                Material tileMat = ((x + z) % 2 == 0) ? floorMat : Material.POLISHED_ANDESITE;
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(tileMat);
            }
        }

        // Main building walls
        for (int y = 1; y <= wallHeight; y++) {
            // Front and back walls
            for (int x = 0; x < w; x++) {
                // Grand double door entrance
                int doorStart = w/2 - 1;
                if (!(x >= doorStart && x <= doorStart + 1 && y <= 3)) {
                    world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                }
                world.getBlockAt(baseX + x, baseY + y, baseZ + d - 1).setType(wall);
            }
            // Side walls
            for (int z = 1; z < d - 1; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                world.getBlockAt(baseX + w - 1, baseY + y, baseZ + z).setType(wall);
            }
        }

        // Decorative pillar corners
        for (int y = 1; y <= wallHeight; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(Material.QUARTZ_PILLAR);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ).setType(Material.QUARTZ_PILLAR);
            world.getBlockAt(baseX, baseY + y, baseZ + d - 1).setType(Material.QUARTZ_PILLAR);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ + d - 1).setType(Material.QUARTZ_PILLAR);
        }

        // Ornate windows on all walls at each floor level
        for (int story = 0; story < stories; story++) {
            int windowY = baseY + 2 + (story * 4);
            // Front windows (flanking door on ground floor)
            world.getBlockAt(baseX + 2, windowY, baseZ).setType(Material.GLASS_PANE);
            world.getBlockAt(baseX + w - 3, windowY, baseZ).setType(Material.GLASS_PANE);
            if (size != HouseSize.SMALL) {
                world.getBlockAt(baseX + 2, windowY + 1, baseZ).setType(Material.GLASS_PANE);
                world.getBlockAt(baseX + w - 3, windowY + 1, baseZ).setType(Material.GLASS_PANE);
            }
            // Back windows
            for (int wx = 2; wx < w - 2; wx += 3) {
                world.getBlockAt(baseX + wx, windowY, baseZ + d - 1).setType(Material.GLASS_PANE);
                if (size != HouseSize.SMALL) {
                    world.getBlockAt(baseX + wx, windowY + 1, baseZ + d - 1).setType(Material.GLASS_PANE);
                }
            }
            // Side windows
            for (int wz = 2; wz < d - 2; wz += 3) {
                world.getBlockAt(baseX, windowY, baseZ + wz).setType(Material.GLASS_PANE);
                world.getBlockAt(baseX + w - 1, windowY, baseZ + wz).setType(Material.GLASS_PANE);
            }
        }

        // Interior floors with grand staircases
        for (int story = 1; story < stories; story++) {
            int floorY = baseY + (story * 4);
            for (int x = 1; x < w - 1; x++) {
                for (int z = 1; z < d - 1; z++) {
                    // Leave space for stairwell
                    if (!(x >= w - 4 && x <= w - 2 && z >= 1 && z <= 4)) {
                        world.getBlockAt(baseX + x, floorY, baseZ + z).setType(floorMat);
                    }
                }
            }
        }

        // Grand staircase
        for (int story = 0; story < stories - 1; story++) {
            int startY = baseY + 1 + (story * 4);
            for (int i = 0; i < 4; i++) {
                world.getBlockAt(baseX + w - 3, startY + i, baseZ + 1 + i).setType(Material.QUARTZ_STAIRS);
                world.getBlockAt(baseX + w - 2, startY + i, baseZ + 1 + i).setType(Material.QUARTZ_STAIRS);
                // Staircase railing
                world.getBlockAt(baseX + w - 4, startY + i, baseZ + 1 + i).setType(Material.OAK_FENCE);
            }
        }

        // Grand roof with dormers for large villas
        buildPitchedRoof(world, baseX - 1, baseY + wallHeight, baseZ - 1, w + 2, d + 2, roofStairs, wall);

        // Dormer windows for large villa
        if (size == HouseSize.LARGE) {
            buildDormer(world, baseX + 3, baseY + wallHeight + 1, baseZ - 1, roofStairs, wall);
            buildDormer(world, baseX + w - 5, baseY + wallHeight + 1, baseZ - 1, roofStairs, wall);
        }

        // Chimneys on both ends
        int chimneyHeight = wallHeight + 4;
        for (int y = 1; y <= chimneyHeight; y++) {
            world.getBlockAt(baseX + 1, baseY + y, baseZ + d - 2).setType(Material.BRICKS);
            if (size != HouseSize.SMALL) {
                world.getBlockAt(baseX + w - 2, baseY + y, baseZ + d - 2).setType(Material.BRICKS);
            }
        }
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + d - 2).setType(Material.CAMPFIRE);

        // Interior furnishing
        addVillaInterior(world, baseX, baseY, baseZ, w, d, stories, faction, size);

        // Grand entrance portico with columns
        buildPortico(world, baseX + w/2 - 2, baseY, baseZ - 3, 5);

        // Side gardens with fountains for medium/large
        buildFlowerGarden(world, baseX - 4, baseY, baseZ + 1, 3, d - 2);
        buildFlowerGarden(world, baseX + w + 1, baseY, baseZ + 1, 3, d - 2);

        // Fountain for large villas
        if (size == HouseSize.LARGE) {
            buildFountain(world, baseX + w/2, baseY, baseZ - 7);
        }

        // Decorative hedges along front
        for (int x = -2; x <= w + 1; x++) {
            if (x < w/2 - 3 || x > w/2 + 3) {
                world.getBlockAt(baseX + x, baseY + 1, baseZ - 4).setType(Material.OAK_LEAVES);
            }
        }

        // Path to entrance
        for (int z = 1; z <= 5; z++) {
            world.getBlockAt(baseX + w/2 - 1, baseY, baseZ - z).setType(Material.STONE_BRICK_SLAB);
            world.getBlockAt(baseX + w/2, baseY, baseZ - z).setType(Material.STONE_BRICK_SLAB);
        }

        return w;
    }

    /**
     * Build a dormer window on roof
     */
    private void buildDormer(World world, int baseX, int baseY, int baseZ, Material roofMat, Material wall) {
        // Small peaked structure
        for (int x = 0; x < 3; x++) {
            world.getBlockAt(baseX + x, baseY, baseZ).setType(wall);
            world.getBlockAt(baseX + x, baseY + 1, baseZ).setType(wall);
        }
        world.getBlockAt(baseX + 1, baseY, baseZ).setType(Material.GLASS_PANE);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ).setType(Material.GLASS_PANE);
        // Mini roof
        world.getBlockAt(baseX, baseY + 2, baseZ).setType(roofMat);
        world.getBlockAt(baseX + 1, baseY + 2, baseZ).setType(roofMat);
        world.getBlockAt(baseX + 2, baseY + 2, baseZ).setType(roofMat);
    }

    /**
     * Build a grand entrance portico with columns
     */
    private void buildPortico(World world, int baseX, int baseY, int baseZ, int width) {
        // Floor
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < 3; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(Material.POLISHED_ANDESITE);
            }
        }

        // Columns
        for (int y = 1; y <= 4; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(Material.QUARTZ_PILLAR);
            world.getBlockAt(baseX + width - 1, baseY + y, baseZ).setType(Material.QUARTZ_PILLAR);
        }

        // Decorative capitals
        world.getBlockAt(baseX, baseY + 5, baseZ).setType(Material.CHISELED_QUARTZ_BLOCK);
        world.getBlockAt(baseX + width - 1, baseY + 5, baseZ).setType(Material.CHISELED_QUARTZ_BLOCK);

        // Roof
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z < 3; z++) {
                world.getBlockAt(baseX + x, baseY + 6, baseZ + z).setType(Material.STONE_BRICK_SLAB);
            }
        }

        // Steps up to entrance
        world.getBlockAt(baseX + 1, baseY, baseZ - 1).setType(Material.STONE_BRICK_STAIRS);
        world.getBlockAt(baseX + 2, baseY, baseZ - 1).setType(Material.STONE_BRICK_STAIRS);
        world.getBlockAt(baseX + 3, baseY, baseZ - 1).setType(Material.STONE_BRICK_STAIRS);
    }

    /**
     * Build a decorative fountain
     */
    private void buildFountain(World world, int baseX, int baseY, int baseZ) {
        // Basin (3x3)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(Material.STONE_BRICK_SLAB);
                if (x == 0 && z == 0) {
                    world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(Material.WATER);
                } else {
                    world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(Material.STONE_BRICKS);
                }
            }
        }
        // Center spout
        world.getBlockAt(baseX, baseY + 2, baseZ).setType(Material.COBBLESTONE_WALL);
        world.getBlockAt(baseX, baseY + 3, baseZ).setType(Material.COBBLESTONE_WALL);
        world.getBlockAt(baseX, baseY + 4, baseZ).setType(Material.WATER);
    }

    /**
     * Add villa-specific luxurious interior
     */
    private void addVillaInterior(World world, int baseX, int baseY, int baseZ, int w, int d, int stories, String faction, HouseSize size) {
        Material bed = FACTION_BEDS.getOrDefault(faction.toLowerCase(), Material.WHITE_BED);

        // Ground floor - foyer and living areas
        // Grand carpet runner from door
        for (int z = 1; z < d - 2; z++) {
            world.getBlockAt(baseX + w/2 - 1, baseY + 1, baseZ + z).setType(Material.RED_CARPET);
            world.getBlockAt(baseX + w/2, baseY + 1, baseZ + z).setType(Material.RED_CARPET);
        }

        // Living room furniture
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + d - 3).setType(Material.SPRUCE_STAIRS); // Sofa
        world.getBlockAt(baseX + 3, baseY + 1, baseZ + d - 3).setType(Material.SPRUCE_STAIRS);
        world.getBlockAt(baseX + 4, baseY + 1, baseZ + d - 3).setType(Material.SPRUCE_STAIRS);
        world.getBlockAt(baseX + 3, baseY + 1, baseZ + d - 5).setType(Material.OAK_PRESSURE_PLATE); // Coffee table
        world.getBlockAt(baseX + 3, baseY, baseZ + d - 5).setType(Material.OAK_FENCE);

        // Dining area
        for (int x = 1; x < 4; x++) {
            world.getBlockAt(baseX + x, baseY + 1, baseZ + 2).setType(Material.DARK_OAK_PRESSURE_PLATE);
            world.getBlockAt(baseX + x, baseY, baseZ + 2).setType(Material.DARK_OAK_FENCE);
        }
        // Dining chairs
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(Material.DARK_OAK_STAIRS);
        world.getBlockAt(baseX + 3, baseY + 1, baseZ + 1).setType(Material.DARK_OAK_STAIRS);
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 3).setType(Material.DARK_OAK_STAIRS);
        world.getBlockAt(baseX + 3, baseY + 1, baseZ + 3).setType(Material.DARK_OAK_STAIRS);

        // Kitchen area
        world.getBlockAt(baseX + w - 5, baseY + 1, baseZ + 1).setType(Material.FURNACE);
        world.getBlockAt(baseX + w - 5, baseY + 1, baseZ + 2).setType(Material.SMOKER);
        world.getBlockAt(baseX + w - 5, baseY + 1, baseZ + 3).setType(Material.CRAFTING_TABLE);
        world.getBlockAt(baseX + w - 5, baseY + 2, baseZ + 1).setType(Material.BARREL);

        // Grand chandelier
        world.getBlockAt(baseX + w/2, baseY + 3, baseZ + d/2).setType(Material.CHAIN);
        world.getBlockAt(baseX + w/2, baseY + 2, baseZ + d/2).setType(Material.LANTERN);
        world.getBlockAt(baseX + w/2 - 1, baseY + 2, baseZ + d/2).setType(Material.LANTERN);
        world.getBlockAt(baseX + w/2 + 1, baseY + 2, baseZ + d/2).setType(Material.LANTERN);

        // Second floor - bedrooms
        int floor2Y = baseY + 4;
        placeBed(world, baseX + 1, floor2Y + 1, baseZ + d - 2, bed, BlockFace.NORTH);
        world.getBlockAt(baseX + 1, floor2Y + 1, baseZ + 1).setType(Material.CHEST);
        world.getBlockAt(baseX + 3, floor2Y + 1, baseZ + 1).setType(Material.ARMOR_STAND);

        // Second bedroom for larger
        if (size != HouseSize.SMALL) {
            placeBed(world, baseX + w/2, floor2Y + 1, baseZ + d - 2, bed, BlockFace.NORTH);
            world.getBlockAt(baseX + w/2 + 2, floor2Y + 1, baseZ + d - 2).setType(Material.CHEST);
        }

        // Bookshelf/study area
        world.getBlockAt(baseX + 1, floor2Y + 1, baseZ + d/2).setType(Material.BOOKSHELF);
        world.getBlockAt(baseX + 1, floor2Y + 2, baseZ + d/2).setType(Material.BOOKSHELF);
        world.getBlockAt(baseX + 2, floor2Y + 1, baseZ + d/2).setType(Material.LECTERN);

        // Floor lighting
        world.getBlockAt(baseX + w/2, floor2Y + 2, baseZ + d/2).setType(Material.LANTERN);

        // Third floor for large villas - master suite
        if (stories >= 3) {
            int floor3Y = baseY + 8;
            // King bed in center
            placeBed(world, baseX + w/2 - 1, floor3Y + 1, baseZ + d - 2, Material.RED_BED, BlockFace.NORTH);
            // Vanity area
            world.getBlockAt(baseX + 1, floor3Y + 1, baseZ + 1).setType(Material.SMITHING_TABLE); // Vanity
            world.getBlockAt(baseX + 2, floor3Y + 1, baseZ + 1).setType(Material.CHEST);
            // Private study
            world.getBlockAt(baseX + w - 5, floor3Y + 1, baseZ + d - 2).setType(Material.CARTOGRAPHY_TABLE);
            world.getBlockAt(baseX + w - 5, floor3Y + 1, baseZ + d - 3).setType(Material.BOOKSHELF);
            // Lighting
            world.getBlockAt(baseX + w/2, floor3Y + 2, baseZ + d/2).setType(Material.LANTERN);
        }
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
     * Build an intricate farm plot with crops, irrigation, scarecrow, and storage
     */
    private void buildFarm(World world, int baseX, int baseY, int baseZ, int width, int depth) {
        // Level the ground first
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= depth; z++) {
                world.getBlockAt(baseX + x, baseY - 1, baseZ + z).setType(Material.DIRT);
            }
        }

        // Stone brick border foundation
        for (int x = -1; x <= width; x++) {
            world.getBlockAt(baseX + x, baseY, baseZ - 1).setType(Material.STONE_BRICKS);
            world.getBlockAt(baseX + x, baseY, baseZ + depth).setType(Material.STONE_BRICKS);
        }
        for (int z = 0; z < depth; z++) {
            world.getBlockAt(baseX - 1, baseY, baseZ + z).setType(Material.STONE_BRICKS);
            world.getBlockAt(baseX + width, baseY, baseZ + z).setType(Material.STONE_BRICKS);
        }

        // Decorative fence around farm
        for (int x = 0; x < width; x++) {
            world.getBlockAt(baseX + x, baseY + 1, baseZ).setType(Material.OAK_FENCE);
            world.getBlockAt(baseX + x, baseY + 1, baseZ + depth - 1).setType(Material.OAK_FENCE);
        }
        for (int z = 1; z < depth - 1; z++) {
            world.getBlockAt(baseX, baseY + 1, baseZ + z).setType(Material.OAK_FENCE);
            world.getBlockAt(baseX + width - 1, baseY + 1, baseZ + z).setType(Material.OAK_FENCE);
        }

        // Corner posts with lanterns
        world.getBlockAt(baseX, baseY + 2, baseZ).setType(Material.OAK_FENCE);
        world.getBlockAt(baseX + width - 1, baseY + 2, baseZ).setType(Material.OAK_FENCE);
        world.getBlockAt(baseX, baseY + 2, baseZ + depth - 1).setType(Material.OAK_FENCE);
        world.getBlockAt(baseX + width - 1, baseY + 2, baseZ + depth - 1).setType(Material.OAK_FENCE);
        world.getBlockAt(baseX, baseY + 3, baseZ).setType(Material.LANTERN);
        world.getBlockAt(baseX + width - 1, baseY + 3, baseZ + depth - 1).setType(Material.LANTERN);

        // Gate
        world.getBlockAt(baseX + width / 2, baseY + 1, baseZ).setType(Material.OAK_FENCE_GATE);

        // Irrigation system - cross pattern with water
        int centerX = width / 2;
        int centerZ = depth / 2;

        // Main water channel down the middle
        for (int z = 1; z < depth - 1; z++) {
            world.getBlockAt(baseX + centerX, baseY, baseZ + z).setType(Material.WATER);
        }

        // Cross channels for larger farms
        if (width > 7) {
            for (int x = 1; x < width - 1; x++) {
                world.getBlockAt(baseX + x, baseY, baseZ + centerZ).setType(Material.WATER);
            }
        }

        // Organized crop rows - different crops in sections
        Material[][] cropSections = {
            {Material.WHEAT, Material.WHEAT},
            {Material.CARROTS, Material.POTATOES},
            {Material.BEETROOTS, Material.WHEAT}
        };

        for (int x = 1; x < width - 1; x++) {
            for (int z = 1; z < depth - 1; z++) {
                // Skip water channels
                if (x == centerX) continue;
                if (width > 7 && z == centerZ) continue;

                // Farmland
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(Material.FARMLAND);

                // Determine crop based on section
                int sectionX = x < centerX ? 0 : 1;
                int sectionZ = z < centerZ ? 0 : 1;
                Material[] section = cropSections[(sectionX + sectionZ) % cropSections.length];
                Material crop = section[random.nextInt(section.length)];
                world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(crop);
            }
        }

        // Scarecrow in center (if space)
        if (width > 5 && depth > 5) {
            int scarecrowX = baseX + centerX + 2;
            int scarecrowZ = baseZ + centerZ;
            world.getBlockAt(scarecrowX, baseY + 1, scarecrowZ).setType(Material.OAK_FENCE);
            world.getBlockAt(scarecrowX, baseY + 2, scarecrowZ).setType(Material.OAK_FENCE);
            world.getBlockAt(scarecrowX, baseY + 3, scarecrowZ).setType(Material.HAY_BLOCK);
            world.getBlockAt(scarecrowX - 1, baseY + 3, scarecrowZ).setType(Material.OAK_FENCE);
            world.getBlockAt(scarecrowX + 1, baseY + 3, scarecrowZ).setType(Material.OAK_FENCE);
            world.getBlockAt(scarecrowX, baseY + 4, scarecrowZ).setType(Material.CARVED_PUMPKIN);
        }

        // Storage shed corner
        int shedX = baseX + width - 3;
        int shedZ = baseZ + depth - 3;
        world.getBlockAt(shedX, baseY + 1, shedZ).setType(Material.BARREL);
        world.getBlockAt(shedX + 1, baseY + 1, shedZ).setType(Material.COMPOSTER);
        world.getBlockAt(shedX, baseY + 1, shedZ + 1).setType(Material.CHEST);
        world.getBlockAt(shedX + 1, baseY + 1, shedZ + 1).setType(Material.CRAFTING_TABLE);

        // Mini shed roof
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                world.getBlockAt(shedX + x, baseY + 2, shedZ + z).setType(Material.SPRUCE_SLAB);
            }
        }

        // Hay bales near entrance
        world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1).setType(Material.HAY_BLOCK);
        if (width > 5) {
            world.getBlockAt(baseX + width - 2, baseY + 1, baseZ + 1).setType(Material.HAY_BLOCK);
        }
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
     * Represents an NPC's home with style, size and partner info
     */
    public static class NPCHome {
        private final UUID ownerUuid;
        private UUID partnerUuid;
        private final Location location;
        private final int footprint;
        private final HouseStyle style;
        private final HouseSize sizeCategory;
        private final long builtAt;

        public NPCHome(UUID ownerUuid, Location location, int footprint, HouseStyle style, HouseSize sizeCategory) {
            this.ownerUuid = ownerUuid;
            this.location = location;
            this.footprint = footprint;
            this.style = style;
            this.sizeCategory = sizeCategory;
            this.builtAt = System.currentTimeMillis();
        }

        // Legacy constructor
        public NPCHome(UUID ownerUuid, Location location, int footprint, HouseStyle style) {
            this(ownerUuid, location, footprint, style, HouseSize.MEDIUM);
        }

        // Legacy constructor
        public NPCHome(UUID ownerUuid, Location location, int footprint) {
            this(ownerUuid, location, footprint, HouseStyle.COTTAGE, HouseSize.MEDIUM);
        }

        public UUID getOwnerUuid() { return ownerUuid; }
        public UUID getPartnerUuid() { return partnerUuid; }
        public void setPartnerUuid(UUID partnerUuid) { this.partnerUuid = partnerUuid; }
        public Location getLocation() { return location; }
        public int getSize() { return footprint; }
        public int getFootprint() { return footprint; }
        public HouseStyle getStyle() { return style; }
        public HouseSize getSizeCategory() { return sizeCategory; }
        public long getBuiltAt() { return builtAt; }
        public boolean hasPartner() { return partnerUuid != null; }
    }
}
