package com.aicraft.npcs.building;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;

import java.util.*;

/**
 * Procedural building generator that creates unique buildings each time.
 * Uses randomized floor plans, room layouts, roof styles, and decorations.
 */
public class ProceduralBuildingGenerator {

    private final Random random;
    private final long seed;

    // Floor plan types
    public enum FloorPlanType {
        RECTANGULAR,    // Simple rectangle
        L_SHAPED,       // L-shaped building
        T_SHAPED,       // T-shaped building
        U_SHAPED,       // U-shaped with courtyard
        CROSS           // Cross/plus shaped
    }

    // Roof styles
    public enum RoofStyle {
        FLAT,           // Flat roof with parapet
        PITCHED,        // Standard pitched roof
        HIP,            // Hip roof (slopes on all sides)
        GAMBREL,        // Barn-style roof
        ASYMMETRIC,     // One side longer than other
        MULTI_GABLE     // Multiple gables
    }

    // Room types for interior
    public enum RoomType {
        BEDROOM,
        KITCHEN,
        LIVING_ROOM,
        STORAGE,
        WORKSHOP,
        DINING,
        LIBRARY,
        CELLAR
    }

    // Generated building data
    public static class BuildingPlan {
        public FloorPlanType floorPlan;
        public RoofStyle roofStyle;
        public int baseWidth;
        public int baseDepth;
        public int wallHeight;
        public int floors;
        public boolean hasBasement;
        public boolean hasPorch;
        public boolean hasBalcony;
        public boolean hasChimney;
        public boolean hasDormer;
        public BlockFace entranceFacing;
        public List<RoomLayout> rooms = new ArrayList<>();
        public List<int[]> windowPositions = new ArrayList<>();
        public int porchWidth;
        public int porchDepth;
        public int[] extensionDimensions; // For L, T, U shapes

        @Override
        public String toString() {
            return String.format("%s floor plan, %s roof, %dx%d, %d floors",
                    floorPlan, roofStyle, baseWidth, baseDepth, floors);
        }
    }

    public static class RoomLayout {
        public RoomType type;
        public int x, z;
        public int width, depth;
        public int floor; // 0 = ground, -1 = basement, 1+ = upper

        public RoomLayout(RoomType type, int x, int z, int width, int depth, int floor) {
            this.type = type;
            this.x = x;
            this.z = z;
            this.width = width;
            this.depth = depth;
            this.floor = floor;
        }
    }

    public ProceduralBuildingGenerator() {
        this.seed = System.currentTimeMillis();
        this.random = new Random(seed);
    }

    public ProceduralBuildingGenerator(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /**
     * Generate a unique building plan based on style and size constraints
     */
    public BuildingPlan generatePlan(NPCHomeBuilder.HouseStyle style, NPCHomeBuilder.HouseSize size) {
        BuildingPlan plan = new BuildingPlan();

        // Base dimensions from size
        int minWidth = size.width;
        int minDepth = size.depth;

        // Add variation to dimensions (+/- 2 blocks)
        plan.baseWidth = minWidth + random.nextInt(3) - 1;
        plan.baseDepth = minDepth + random.nextInt(3) - 1;

        // Ensure minimum viable size
        plan.baseWidth = Math.max(5, plan.baseWidth);
        plan.baseDepth = Math.max(5, plan.baseDepth);

        // Determine floor plan based on style and random chance
        plan.floorPlan = pickFloorPlan(style, size);

        // Generate extension dimensions for complex floor plans
        if (plan.floorPlan != FloorPlanType.RECTANGULAR) {
            plan.extensionDimensions = generateExtensionDimensions(plan.floorPlan, plan.baseWidth, plan.baseDepth);
        }

        // Determine wall height and floors
        plan.wallHeight = pickWallHeight(style, size);
        plan.floors = pickFloors(style, size);

        // Roof style based on building type
        plan.roofStyle = pickRoofStyle(style, plan.floorPlan);

        // Optional features (weighted by style)
        plan.hasBasement = shouldHaveBasement(style, size);
        plan.hasPorch = shouldHavePorch(style);
        plan.hasBalcony = plan.floors > 1 && random.nextFloat() < 0.3f;
        plan.hasChimney = random.nextFloat() < 0.7f;
        plan.hasDormer = plan.roofStyle == RoofStyle.PITCHED && random.nextFloat() < 0.4f;

        // Porch dimensions if applicable
        if (plan.hasPorch) {
            plan.porchWidth = Math.max(3, plan.baseWidth / 2 + random.nextInt(2));
            plan.porchDepth = 2 + random.nextInt(2);
        }

        // Entrance direction
        BlockFace[] directions = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        plan.entranceFacing = directions[random.nextInt(4)];

        // Generate window positions
        generateWindowPositions(plan);

        // Generate room layouts
        generateRoomLayouts(plan, style);

        return plan;
    }

    private FloorPlanType pickFloorPlan(NPCHomeBuilder.HouseStyle style, NPCHomeBuilder.HouseSize size) {
        float roll = random.nextFloat();

        switch (style) {
            case COTTAGE:
                // Cottages are usually simple
                if (size == NPCHomeBuilder.HouseSize.LARGE && roll < 0.3f) return FloorPlanType.L_SHAPED;
                return FloorPlanType.RECTANGULAR;

            case FARMHOUSE:
                // Farmhouses often have extensions
                if (roll < 0.4f) return FloorPlanType.L_SHAPED;
                if (roll < 0.6f) return FloorPlanType.T_SHAPED;
                return FloorPlanType.RECTANGULAR;

            case CABIN:
                // Cabins are simple
                if (size == NPCHomeBuilder.HouseSize.LARGE && roll < 0.2f) return FloorPlanType.L_SHAPED;
                return FloorPlanType.RECTANGULAR;

            case TOWER:
                // Towers are rectangular or cross
                if (roll < 0.3f) return FloorPlanType.CROSS;
                return FloorPlanType.RECTANGULAR;

            case UNDERGROUND:
                // Underground can be complex
                if (roll < 0.3f) return FloorPlanType.T_SHAPED;
                if (roll < 0.5f) return FloorPlanType.U_SHAPED;
                return FloorPlanType.RECTANGULAR;

            case VILLA:
                // Villas are often complex
                if (roll < 0.25f) return FloorPlanType.U_SHAPED;
                if (roll < 0.5f) return FloorPlanType.L_SHAPED;
                if (roll < 0.7f) return FloorPlanType.T_SHAPED;
                return FloorPlanType.RECTANGULAR;

            default:
                return FloorPlanType.RECTANGULAR;
        }
    }

    private int[] generateExtensionDimensions(FloorPlanType type, int baseWidth, int baseDepth) {
        switch (type) {
            case L_SHAPED:
                // Extension width, extension depth, offset from corner
                return new int[]{
                    3 + random.nextInt(Math.max(1, baseWidth / 2)),
                    2 + random.nextInt(Math.max(1, baseDepth / 3)),
                    0
                };

            case T_SHAPED:
                // Extension on one side
                return new int[]{
                    2 + random.nextInt(3),
                    3 + random.nextInt(Math.max(1, baseDepth / 2)),
                    baseWidth / 3 + random.nextInt(Math.max(1, baseWidth / 3))
                };

            case U_SHAPED:
                // Two wings
                int wingWidth = 2 + random.nextInt(3);
                int wingDepth = 3 + random.nextInt(Math.max(1, baseDepth / 2));
                return new int[]{wingWidth, wingDepth, wingWidth, wingDepth};

            case CROSS:
                int armLength = 2 + random.nextInt(3);
                return new int[]{armLength, armLength, armLength, armLength};

            default:
                return new int[]{0, 0, 0};
        }
    }

    private int pickWallHeight(NPCHomeBuilder.HouseStyle style, NPCHomeBuilder.HouseSize size) {
        int base = 3;

        switch (size) {
            case SMALL: base = 3; break;
            case MEDIUM: base = 3 + random.nextInt(2); break;
            case LARGE: base = 4 + random.nextInt(2); break;
        }

        // Style adjustments
        if (style == NPCHomeBuilder.HouseStyle.TOWER) base += 2;
        if (style == NPCHomeBuilder.HouseStyle.UNDERGROUND) base = 3;

        return base;
    }

    private int pickFloors(NPCHomeBuilder.HouseStyle style, NPCHomeBuilder.HouseSize size) {
        switch (style) {
            case TOWER:
                return 2 + random.nextInt(2); // 2-3 floors

            case VILLA:
                return size == NPCHomeBuilder.HouseSize.LARGE ? 2 : 1;

            case FARMHOUSE:
                return size == NPCHomeBuilder.HouseSize.LARGE && random.nextFloat() < 0.5f ? 2 : 1;

            case UNDERGROUND:
                return 1; // Underground has "basement" instead

            default:
                return size == NPCHomeBuilder.HouseSize.LARGE && random.nextFloat() < 0.3f ? 2 : 1;
        }
    }

    private RoofStyle pickRoofStyle(NPCHomeBuilder.HouseStyle style, FloorPlanType floorPlan) {
        float roll = random.nextFloat();

        switch (style) {
            case COTTAGE:
                if (roll < 0.5f) return RoofStyle.PITCHED;
                if (roll < 0.7f) return RoofStyle.HIP;
                return RoofStyle.GAMBREL;

            case FARMHOUSE:
                if (roll < 0.4f) return RoofStyle.GAMBREL;
                if (roll < 0.7f) return RoofStyle.PITCHED;
                return RoofStyle.ASYMMETRIC;

            case CABIN:
                if (roll < 0.6f) return RoofStyle.PITCHED;
                if (roll < 0.8f) return RoofStyle.ASYMMETRIC;
                return RoofStyle.HIP;

            case TOWER:
                return RoofStyle.FLAT; // Towers have battlements

            case UNDERGROUND:
                return RoofStyle.FLAT; // Underground has grass/earth cover

            case VILLA:
                if (floorPlan == FloorPlanType.RECTANGULAR) {
                    if (roll < 0.4f) return RoofStyle.HIP;
                    return RoofStyle.PITCHED;
                }
                if (roll < 0.5f) return RoofStyle.MULTI_GABLE;
                return RoofStyle.HIP;

            default:
                return RoofStyle.PITCHED;
        }
    }

    private boolean shouldHaveBasement(NPCHomeBuilder.HouseStyle style, NPCHomeBuilder.HouseSize size) {
        if (style == NPCHomeBuilder.HouseStyle.UNDERGROUND) return true;
        if (size == NPCHomeBuilder.HouseSize.SMALL) return false;
        return random.nextFloat() < 0.25f;
    }

    private boolean shouldHavePorch(NPCHomeBuilder.HouseStyle style) {
        switch (style) {
            case COTTAGE: return random.nextFloat() < 0.5f;
            case FARMHOUSE: return random.nextFloat() < 0.7f;
            case CABIN: return random.nextFloat() < 0.6f;
            case VILLA: return random.nextFloat() < 0.8f;
            default: return false;
        }
    }

    private void generateWindowPositions(BuildingPlan plan) {
        plan.windowPositions.clear();
        int windowLevel = 2;

        // Front wall windows
        int numFrontWindows = Math.max(1, (plan.baseWidth - 2) / 3);
        int spacing = plan.baseWidth / (numFrontWindows + 1);
        for (int i = 1; i <= numFrontWindows; i++) {
            int x = spacing * i;
            // Avoid door position (center)
            if (Math.abs(x - plan.baseWidth / 2) > 1) {
                plan.windowPositions.add(new int[]{x, windowLevel, 0});
            }
        }

        // Back wall windows
        for (int i = 1; i <= numFrontWindows; i++) {
            plan.windowPositions.add(new int[]{spacing * i, windowLevel, plan.baseDepth - 1});
        }

        // Side windows
        int numSideWindows = Math.max(1, (plan.baseDepth - 2) / 3);
        int sideSpacing = plan.baseDepth / (numSideWindows + 1);
        for (int i = 1; i <= numSideWindows; i++) {
            plan.windowPositions.add(new int[]{0, windowLevel, sideSpacing * i});
            plan.windowPositions.add(new int[]{plan.baseWidth - 1, windowLevel, sideSpacing * i});
        }

        // Upper floor windows if multi-story
        if (plan.floors > 1) {
            int upperLevel = plan.wallHeight + 2;
            for (int i = 1; i <= numFrontWindows; i++) {
                plan.windowPositions.add(new int[]{spacing * i, upperLevel, 0});
                plan.windowPositions.add(new int[]{spacing * i, upperLevel, plan.baseDepth - 1});
            }
        }
    }

    private void generateRoomLayouts(BuildingPlan plan, NPCHomeBuilder.HouseStyle style) {
        plan.rooms.clear();

        // Every building needs at least a bedroom
        int bedroomX = plan.baseWidth - 4;
        int bedroomZ = plan.baseDepth - 4;
        plan.rooms.add(new RoomLayout(RoomType.BEDROOM, bedroomX, bedroomZ, 4, 4, 0));

        // Add rooms based on size
        if (plan.baseWidth >= 7 && plan.baseDepth >= 7) {
            // Kitchen near entrance
            plan.rooms.add(new RoomLayout(RoomType.KITCHEN, 0, 0, 3, 3, 0));

            // Living room
            plan.rooms.add(new RoomLayout(RoomType.LIVING_ROOM, 0, 3, 3, plan.baseDepth - 3, 0));
        }

        // Storage for farmhouses and larger buildings
        if (style == NPCHomeBuilder.HouseStyle.FARMHOUSE || plan.baseWidth >= 9) {
            plan.rooms.add(new RoomLayout(RoomType.STORAGE, plan.baseWidth / 2 - 1, 0, 3, 2, 0));
        }

        // Basement rooms
        if (plan.hasBasement) {
            plan.rooms.add(new RoomLayout(RoomType.CELLAR, 1, 1, plan.baseWidth - 2, plan.baseDepth - 2, -1));
        }

        // Upper floor rooms
        if (plan.floors > 1) {
            plan.rooms.add(new RoomLayout(RoomType.BEDROOM, 0, 0, plan.baseWidth / 2, plan.baseDepth, 1));
            if (plan.baseWidth >= 8) {
                plan.rooms.add(new RoomLayout(RoomType.LIBRARY, plan.baseWidth / 2, 0, plan.baseWidth / 2, plan.baseDepth, 1));
            }
        }
    }

    /**
     * Build the actual structure based on a plan
     */
    public int buildFromPlan(World world, int baseX, int baseY, int baseZ,
                             BuildingPlan plan, Material[] mats, String faction) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];
        Material roofStairs = mats[3];

        int w = plan.baseWidth;
        int d = plan.baseDepth;
        int wallHeight = plan.wallHeight;

        // Clear the build area
        clearBuildArea(world, baseX, baseY, baseZ, w + 4, wallHeight + 8, d + 4);

        // Build basement if applicable
        if (plan.hasBasement) {
            buildBasement(world, baseX, baseY - 4, baseZ, w, d, mats);
        }

        // Build main structure based on floor plan
        switch (plan.floorPlan) {
            case RECTANGULAR:
                buildRectangularStructure(world, baseX, baseY, baseZ, w, d, wallHeight, mats);
                break;
            case L_SHAPED:
                buildLShapedStructure(world, baseX, baseY, baseZ, w, d, wallHeight, plan.extensionDimensions, mats);
                break;
            case T_SHAPED:
                buildTShapedStructure(world, baseX, baseY, baseZ, w, d, wallHeight, plan.extensionDimensions, mats);
                break;
            case U_SHAPED:
                buildUShapedStructure(world, baseX, baseY, baseZ, w, d, wallHeight, plan.extensionDimensions, mats);
                break;
            case CROSS:
                buildCrossStructure(world, baseX, baseY, baseZ, w, d, wallHeight, plan.extensionDimensions, mats);
                break;
        }

        // Add windows
        for (int[] windowPos : plan.windowPositions) {
            int wx = baseX + windowPos[0];
            int wy = baseY + windowPos[1];
            int wz = baseZ + windowPos[2];
            Block windowBlock = world.getBlockAt(wx, wy, wz);
            if (windowBlock.getType() == wall) {
                windowBlock.setType(Material.GLASS_PANE);
            }
        }

        // Add door
        addDoor(world, baseX + w / 2, baseY + 1, baseZ, plan.entranceFacing, faction);

        // Build porch if applicable
        if (plan.hasPorch) {
            buildPorch(world, baseX, baseY, baseZ - plan.porchDepth, plan.porchWidth, plan.porchDepth, mats);
        }

        // Build roof based on style
        int roofY = baseY + wallHeight;
        switch (plan.roofStyle) {
            case FLAT:
                buildFlatRoof(world, baseX, roofY, baseZ, w, d, wall);
                break;
            case PITCHED:
                buildPitchedRoof(world, baseX - 1, roofY, baseZ - 1, w + 2, d + 2, roofStairs, wall);
                break;
            case HIP:
                buildHipRoof(world, baseX - 1, roofY, baseZ - 1, w + 2, d + 2, roofStairs);
                break;
            case GAMBREL:
                buildGambrelRoof(world, baseX - 1, roofY, baseZ - 1, w + 2, d + 2, roofStairs, wall);
                break;
            case ASYMMETRIC:
                buildAsymmetricRoof(world, baseX - 1, roofY, baseZ - 1, w + 2, d + 2, roofStairs, wall);
                break;
            case MULTI_GABLE:
                buildMultiGableRoof(world, baseX - 1, roofY, baseZ - 1, w + 2, d + 2, roofStairs, wall);
                break;
        }

        // Add chimney if applicable
        if (plan.hasChimney) {
            int chimneyX = baseX + w - 2 + random.nextInt(2);
            int chimneyZ = baseZ + d - 2 + random.nextInt(2);
            buildChimney(world, chimneyX, roofY, chimneyZ, 3 + random.nextInt(2));
        }

        // Add dormer if applicable
        if (plan.hasDormer) {
            int dormerX = baseX + w / 3 + random.nextInt(w / 3);
            buildDormer(world, dormerX, roofY + 1, baseZ - 1, roofStairs, wall);
        }

        // Add balcony if applicable
        if (plan.hasBalcony && plan.floors > 1) {
            buildBalcony(world, baseX + w / 2 - 1, baseY + plan.wallHeight, baseZ - 1, 3, 2, mats);
        }

        // Furnish rooms
        furnishRooms(world, baseX, baseY, baseZ, plan, mats, faction);

        return w; // Return building width
    }

    private void clearBuildArea(World world, int baseX, int baseY, int baseZ, int w, int h, int d) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                for (int z = 0; z < d; z++) {
                    world.getBlockAt(baseX + x - 2, baseY + y, baseZ + z - 2).setType(Material.AIR);
                }
            }
        }
    }

    private void buildBasement(World world, int baseX, int baseY, int baseZ, int w, int d, Material[] mats) {
        Material wall = mats[0];
        Material floor = mats[2];

        // Dig out basement
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < d; z++) {
                    if (x == 0 || x == w - 1 || z == 0 || z == d - 1) {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.STONE_BRICKS);
                    } else if (y == 0) {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(floor);
                    } else {
                        world.getBlockAt(baseX + x, baseY + y, baseZ + z).setType(Material.AIR);
                    }
                }
            }
        }

        // Trapdoor access
        world.getBlockAt(baseX + w / 2, baseY + 4, baseZ + d / 2).setType(Material.OAK_TRAPDOOR);
    }

    private void buildRectangularStructure(World world, int baseX, int baseY, int baseZ,
                                           int w, int d, int h, Material[] mats) {
        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];

        // Foundation
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                world.getBlockAt(baseX + x, baseY - 1, baseZ + z).setType(Material.STONE_BRICKS);
            }
        }

        // Floor
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floor);
            }
        }

        // Walls
        for (int y = 1; y <= h; y++) {
            for (int x = 0; x < w; x++) {
                world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                world.getBlockAt(baseX + x, baseY + y, baseZ + d - 1).setType(wall);
            }
            for (int z = 0; z < d; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                world.getBlockAt(baseX + w - 1, baseY + y, baseZ + z).setType(wall);
            }
        }

        // Corners
        for (int y = 1; y <= h; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX, baseY + y, baseZ + d - 1).setType(corner);
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ + d - 1).setType(corner);
        }
    }

    private void buildLShapedStructure(World world, int baseX, int baseY, int baseZ,
                                       int w, int d, int h, int[] ext, Material[] mats) {
        // Build main rectangle
        buildRectangularStructure(world, baseX, baseY, baseZ, w, d, h, mats);

        // Build extension
        int extW = ext[0];
        int extD = ext[1];
        buildRectangularStructure(world, baseX + w, baseY, baseZ, extW, extD, h, mats);

        // Remove wall between them
        Material floor = mats[2];
        for (int y = 1; y <= h; y++) {
            for (int z = 1; z < Math.min(d, extD) - 1; z++) {
                world.getBlockAt(baseX + w - 1, baseY + y, baseZ + z).setType(Material.AIR);
            }
        }
    }

    private void buildTShapedStructure(World world, int baseX, int baseY, int baseZ,
                                       int w, int d, int h, int[] ext, Material[] mats) {
        buildRectangularStructure(world, baseX, baseY, baseZ, w, d, h, mats);

        // T extension on one side
        int extW = ext[0];
        int extD = ext[1];
        int offset = ext[2];
        buildRectangularStructure(world, baseX - extW, baseY, baseZ + offset, extW, extD, h, mats);

        // Connect
        for (int y = 1; y <= h; y++) {
            for (int z = offset + 1; z < offset + extD - 1; z++) {
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(Material.AIR);
            }
        }
    }

    private void buildUShapedStructure(World world, int baseX, int baseY, int baseZ,
                                       int w, int d, int h, int[] ext, Material[] mats) {
        // Main section
        buildRectangularStructure(world, baseX, baseY, baseZ, w, d / 2, h, mats);

        // Left wing
        int wingW = ext[0];
        int wingD = ext[1];
        buildRectangularStructure(world, baseX - wingW, baseY, baseZ, wingW, wingD, h, mats);

        // Right wing
        buildRectangularStructure(world, baseX + w, baseY, baseZ, ext[2], ext[3], h, mats);

        // Connect wings to main
        for (int y = 1; y <= h; y++) {
            // Left connection
            world.getBlockAt(baseX, baseY + y, baseZ + 1).setType(Material.AIR);
            // Right connection
            world.getBlockAt(baseX + w - 1, baseY + y, baseZ + 1).setType(Material.AIR);
        }
    }

    private void buildCrossStructure(World world, int baseX, int baseY, int baseZ,
                                     int w, int d, int h, int[] ext, Material[] mats) {
        // Center
        buildRectangularStructure(world, baseX, baseY, baseZ, w, d, h, mats);

        int armLen = ext[0];

        // North arm
        buildRectangularStructure(world, baseX + w / 3, baseY, baseZ - armLen, w / 3, armLen, h, mats);
        // South arm
        buildRectangularStructure(world, baseX + w / 3, baseY, baseZ + d, w / 3, armLen, h, mats);

        // Connect arms
        for (int y = 1; y <= h; y++) {
            for (int x = w / 3 + 1; x < 2 * w / 3 - 1; x++) {
                world.getBlockAt(baseX + x, baseY + y, baseZ).setType(Material.AIR);
                world.getBlockAt(baseX + x, baseY + y, baseZ + d - 1).setType(Material.AIR);
            }
        }
    }

    private void addDoor(World world, int x, int y, int z, BlockFace facing, String faction) {
        Material doorMat = faction.equals("cultists") ? Material.DARK_OAK_DOOR :
                          faction.equals("guards") ? Material.IRON_DOOR :
                          faction.equals("merchants") ? Material.BIRCH_DOOR :
                          Material.OAK_DOOR;

        Block lower = world.getBlockAt(x, y, z);
        Block upper = world.getBlockAt(x, y + 1, z);

        lower.setType(doorMat);
        upper.setType(doorMat);

        if (lower.getBlockData() instanceof org.bukkit.block.data.type.Door) {
            org.bukkit.block.data.type.Door lowerData = (org.bukkit.block.data.type.Door) lower.getBlockData();
            lowerData.setHalf(Bisected.Half.BOTTOM);
            lower.setBlockData(lowerData);
        }
        if (upper.getBlockData() instanceof org.bukkit.block.data.type.Door) {
            org.bukkit.block.data.type.Door upperData = (org.bukkit.block.data.type.Door) upper.getBlockData();
            upperData.setHalf(Bisected.Half.TOP);
            upper.setBlockData(upperData);
        }
    }

    private void buildPorch(World world, int baseX, int baseY, int baseZ, int w, int d, Material[] mats) {
        Material floor = mats[2];
        Material corner = mats[1];

        // Porch floor
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floor);
            }
        }

        // Porch posts
        world.getBlockAt(baseX, baseY + 1, baseZ).setType(corner);
        world.getBlockAt(baseX, baseY + 2, baseZ).setType(corner);
        world.getBlockAt(baseX + w - 1, baseY + 1, baseZ).setType(corner);
        world.getBlockAt(baseX + w - 1, baseY + 2, baseZ).setType(corner);

        // Porch roof
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                world.getBlockAt(baseX + x, baseY + 3, baseZ + z).setType(mats[0]);
            }
        }

        // Fencing
        for (int x = 1; x < w - 1; x++) {
            world.getBlockAt(baseX + x, baseY + 1, baseZ).setType(Material.OAK_FENCE);
        }
    }

    private void buildFlatRoof(World world, int baseX, int baseY, int baseZ, int w, int d, Material wall) {
        // Flat roof with parapet
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                world.getBlockAt(baseX + x, baseY + 1, baseZ + z).setType(Material.SMOOTH_STONE_SLAB);
            }
        }

        // Parapet walls
        for (int x = -1; x <= w; x++) {
            world.getBlockAt(baseX + x, baseY + 2, baseZ - 1).setType(Material.STONE_BRICK_WALL);
            world.getBlockAt(baseX + x, baseY + 2, baseZ + d).setType(Material.STONE_BRICK_WALL);
        }
        for (int z = 0; z < d; z++) {
            world.getBlockAt(baseX - 1, baseY + 2, baseZ + z).setType(Material.STONE_BRICK_WALL);
            world.getBlockAt(baseX + w, baseY + 2, baseZ + z).setType(Material.STONE_BRICK_WALL);
        }
    }

    private void buildPitchedRoof(World world, int baseX, int baseY, int baseZ, int w, int d, Material stairs, Material fill) {
        int peakHeight = (w / 2) + 1;

        for (int level = 0; level <= peakHeight; level++) {
            for (int z = 0; z < d; z++) {
                // Left side stairs
                Block leftStair = world.getBlockAt(baseX + level, baseY + level + 1, baseZ + z);
                leftStair.setType(stairs);
                if (leftStair.getBlockData() instanceof Stairs) {
                    Stairs stairData = (Stairs) leftStair.getBlockData();
                    stairData.setFacing(BlockFace.EAST);
                    leftStair.setBlockData(stairData);
                }

                // Right side stairs
                Block rightStair = world.getBlockAt(baseX + w - 1 - level, baseY + level + 1, baseZ + z);
                rightStair.setType(stairs);
                if (rightStair.getBlockData() instanceof Stairs) {
                    Stairs stairData = (Stairs) rightStair.getBlockData();
                    stairData.setFacing(BlockFace.WEST);
                    rightStair.setBlockData(stairData);
                }

                // Fill underneath
                for (int fillX = level + 1; fillX < w - 1 - level; fillX++) {
                    world.getBlockAt(baseX + fillX, baseY + level + 1, baseZ + z).setType(fill);
                }
            }
        }
    }

    private void buildHipRoof(World world, int baseX, int baseY, int baseZ, int w, int d, Material stairs) {
        int levels = Math.min(w, d) / 2;

        for (int level = 0; level <= levels; level++) {
            for (int x = level; x < w - level; x++) {
                for (int z = level; z < d - level; z++) {
                    // Edge blocks are stairs
                    if (x == level || x == w - 1 - level || z == level || z == d - 1 - level) {
                        Block b = world.getBlockAt(baseX + x, baseY + level + 1, baseZ + z);
                        b.setType(stairs);

                        if (b.getBlockData() instanceof Stairs) {
                            Stairs stairData = (Stairs) b.getBlockData();
                            if (z == level) stairData.setFacing(BlockFace.SOUTH);
                            else if (z == d - 1 - level) stairData.setFacing(BlockFace.NORTH);
                            else if (x == level) stairData.setFacing(BlockFace.EAST);
                            else stairData.setFacing(BlockFace.WEST);
                            b.setBlockData(stairData);
                        }
                    }
                }
            }
        }
    }

    private void buildGambrelRoof(World world, int baseX, int baseY, int baseZ, int w, int d, Material stairs, Material fill) {
        // Lower steep section
        for (int z = 0; z < d; z++) {
            Block leftLower = world.getBlockAt(baseX, baseY + 1, baseZ + z);
            leftLower.setType(stairs);
            setStairFacing(leftLower, BlockFace.EAST);

            Block rightLower = world.getBlockAt(baseX + w - 1, baseY + 1, baseZ + z);
            rightLower.setType(stairs);
            setStairFacing(rightLower, BlockFace.WEST);

            world.getBlockAt(baseX + 1, baseY + 2, baseZ + z).setType(fill);
            world.getBlockAt(baseX + w - 2, baseY + 2, baseZ + z).setType(fill);
        }

        // Upper shallow section
        int upperStart = 2;
        buildPitchedRoof(world, baseX + upperStart, baseY + 2, baseZ, w - 2 * upperStart, d, stairs, fill);
    }

    private void buildAsymmetricRoof(World world, int baseX, int baseY, int baseZ, int w, int d, Material stairs, Material fill) {
        // One side is longer/lower than the other
        for (int level = 0; level <= w / 3; level++) {
            for (int z = 0; z < d; z++) {
                // Steep left side
                Block leftStair = world.getBlockAt(baseX + level, baseY + level + 1, baseZ + z);
                leftStair.setType(stairs);
                setStairFacing(leftStair, BlockFace.EAST);

                // Gradual right side (starts higher, goes further)
                if (level < w / 2) {
                    Block rightStair = world.getBlockAt(baseX + w - 1 - level * 2, baseY + level + 1, baseZ + z);
                    rightStair.setType(stairs);
                    setStairFacing(rightStair, BlockFace.WEST);
                }

                // Fill
                for (int fillX = level + 1; fillX < w - 1 - level * 2; fillX++) {
                    world.getBlockAt(baseX + fillX, baseY + level + 1, baseZ + z).setType(fill);
                }
            }
        }
    }

    private void buildMultiGableRoof(World world, int baseX, int baseY, int baseZ, int w, int d, Material stairs, Material fill) {
        int gableWidth = w / 2;

        // Front gable
        buildPitchedRoof(world, baseX, baseY, baseZ, gableWidth, d / 2, stairs, fill);
        // Back gable (offset)
        buildPitchedRoof(world, baseX + gableWidth / 2, baseY, baseZ + d / 2, gableWidth, d / 2, stairs, fill);
    }

    private void setStairFacing(Block block, BlockFace facing) {
        if (block.getBlockData() instanceof Stairs) {
            Stairs stairData = (Stairs) block.getBlockData();
            stairData.setFacing(facing);
            block.setBlockData(stairData);
        }
    }

    private void buildChimney(World world, int x, int baseY, int z, int height) {
        for (int y = 0; y < height; y++) {
            world.getBlockAt(x, baseY + y, z).setType(Material.BRICKS);
        }
        // Campfire inside at base (below roof level)
        world.getBlockAt(x, baseY - 1, z).setType(Material.CAMPFIRE);
    }

    private void buildDormer(World world, int x, int y, int z, Material stairs, Material wall) {
        // Small window bump-out on roof
        world.getBlockAt(x, y, z).setType(wall);
        world.getBlockAt(x + 1, y, z).setType(Material.GLASS_PANE);
        world.getBlockAt(x + 2, y, z).setType(wall);

        world.getBlockAt(x, y + 1, z).setType(stairs);
        setStairFacing(world.getBlockAt(x, y + 1, z), BlockFace.EAST);
        world.getBlockAt(x + 1, y + 1, z).setType(wall);
        world.getBlockAt(x + 2, y + 1, z).setType(stairs);
        setStairFacing(world.getBlockAt(x + 2, y + 1, z), BlockFace.WEST);
    }

    private void buildBalcony(World world, int x, int y, int z, int w, int d, Material[] mats) {
        Material floor = mats[2];

        // Balcony floor
        for (int bx = 0; bx < w; bx++) {
            for (int bz = 0; bz < d; bz++) {
                world.getBlockAt(x + bx, y, z - bz).setType(floor);
            }
        }

        // Railing
        for (int bx = 0; bx < w; bx++) {
            world.getBlockAt(x + bx, y + 1, z - d + 1).setType(Material.OAK_FENCE);
        }
        world.getBlockAt(x, y + 1, z).setType(Material.OAK_FENCE);
        world.getBlockAt(x + w - 1, y + 1, z).setType(Material.OAK_FENCE);
    }

    private void furnishRooms(World world, int baseX, int baseY, int baseZ, BuildingPlan plan, Material[] mats, String faction) {
        for (RoomLayout room : plan.rooms) {
            int roomX = baseX + room.x;
            int roomY = baseY + (room.floor * (plan.wallHeight + 1));
            int roomZ = baseZ + room.z;

            switch (room.type) {
                case BEDROOM:
                    furnishBedroom(world, roomX, roomY, roomZ, room.width, room.depth, faction);
                    break;
                case KITCHEN:
                    furnishKitchen(world, roomX, roomY, roomZ, room.width, room.depth);
                    break;
                case LIVING_ROOM:
                    furnishLivingRoom(world, roomX, roomY, roomZ, room.width, room.depth);
                    break;
                case STORAGE:
                    furnishStorage(world, roomX, roomY, roomZ, room.width, room.depth);
                    break;
                case LIBRARY:
                    furnishLibrary(world, roomX, roomY, roomZ, room.width, room.depth);
                    break;
                case CELLAR:
                    furnishCellar(world, roomX, roomY, roomZ, room.width, room.depth);
                    break;
            }
        }
    }

    private void furnishBedroom(World world, int x, int y, int z, int w, int d, String faction) {
        // Bed
        Material bedMat = Material.RED_BED;
        if (faction.equals("guards")) bedMat = Material.BLUE_BED;
        else if (faction.equals("merchants")) bedMat = Material.YELLOW_BED;
        else if (faction.equals("cultists")) bedMat = Material.PURPLE_BED;

        placeBed(world, x + 1, y + 1, z + d - 2, bedMat, BlockFace.SOUTH);

        // Chest
        world.getBlockAt(x + w - 2, y + 1, z + 1).setType(Material.CHEST);

        // Crafting table or bookshelf
        if (random.nextBoolean()) {
            world.getBlockAt(x + 1, y + 1, z + 1).setType(Material.CRAFTING_TABLE);
        } else {
            world.getBlockAt(x + 1, y + 1, z + 1).setType(Material.BOOKSHELF);
        }

        // Carpet (random color)
        Material[] carpets = {Material.RED_CARPET, Material.BLUE_CARPET, Material.GREEN_CARPET, Material.WHITE_CARPET};
        Material carpet = carpets[random.nextInt(carpets.length)];
        for (int cx = 1; cx < w - 1; cx++) {
            for (int cz = 1; cz < d - 1; cz++) {
                if (random.nextFloat() < 0.3f) {
                    world.getBlockAt(x + cx, y + 1, z + cz).setType(carpet);
                }
            }
        }
    }

    private void placeBed(World world, int x, int y, int z, Material bedMat, BlockFace facing) {
        Block foot = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y, z + 1);

        foot.setType(bedMat);
        head.setType(bedMat);

        if (foot.getBlockData() instanceof org.bukkit.block.data.type.Bed) {
            org.bukkit.block.data.type.Bed footData = (org.bukkit.block.data.type.Bed) foot.getBlockData();
            footData.setPart(org.bukkit.block.data.type.Bed.Part.FOOT);
            footData.setFacing(facing);
            foot.setBlockData(footData);
        }
        if (head.getBlockData() instanceof org.bukkit.block.data.type.Bed) {
            org.bukkit.block.data.type.Bed headData = (org.bukkit.block.data.type.Bed) head.getBlockData();
            headData.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
            headData.setFacing(facing);
            head.setBlockData(headData);
        }
    }

    private void furnishKitchen(World world, int x, int y, int z, int w, int d) {
        // Furnace
        world.getBlockAt(x + 1, y + 1, z + d - 2).setType(Material.FURNACE);

        // Crafting table
        world.getBlockAt(x + 2, y + 1, z + d - 2).setType(Material.CRAFTING_TABLE);

        // Cauldron (sink)
        world.getBlockAt(x + w - 2, y + 1, z + d - 2).setType(Material.CAULDRON);

        // Barrel (storage)
        if (w >= 3) {
            world.getBlockAt(x + w - 2, y + 1, z + 1).setType(Material.BARREL);
        }

        // Smoker for cooking
        if (random.nextBoolean()) {
            world.getBlockAt(x + 1, y + 1, z + 1).setType(Material.SMOKER);
        }
    }

    private void furnishLivingRoom(World world, int x, int y, int z, int w, int d) {
        // Chairs (stairs as seats)
        world.getBlockAt(x + 1, y + 1, z + d / 2).setType(Material.OAK_STAIRS);
        setStairFacing(world.getBlockAt(x + 1, y + 1, z + d / 2), BlockFace.EAST);

        if (w >= 3) {
            world.getBlockAt(x + w - 2, y + 1, z + d / 2).setType(Material.OAK_STAIRS);
            setStairFacing(world.getBlockAt(x + w - 2, y + 1, z + d / 2), BlockFace.WEST);
        }

        // Table (fence + carpet/pressure plate)
        world.getBlockAt(x + w / 2, y + 1, z + d / 2).setType(Material.OAK_FENCE);
        world.getBlockAt(x + w / 2, y + 2, z + d / 2).setType(Material.OAK_PRESSURE_PLATE);

        // Fireplace if room is big enough
        if (d >= 4) {
            world.getBlockAt(x + w / 2, y + 1, z + d - 2).setType(Material.CAMPFIRE);
            world.getBlockAt(x + w / 2, y + 2, z + d - 1).setType(Material.BRICKS);
            world.getBlockAt(x + w / 2, y + 3, z + d - 1).setType(Material.BRICKS);
        }

        // Random decorations
        if (random.nextBoolean()) {
            world.getBlockAt(x + 1, y + 1, z + 1).setType(Material.FLOWER_POT);
        }
    }

    private void furnishStorage(World world, int x, int y, int z, int w, int d) {
        // Chests along the wall
        for (int cx = 1; cx < w - 1; cx++) {
            world.getBlockAt(x + cx, y + 1, z + d - 2).setType(Material.CHEST);
        }

        // Barrels
        if (d >= 3) {
            world.getBlockAt(x + 1, y + 1, z + 1).setType(Material.BARREL);
            world.getBlockAt(x + w - 2, y + 1, z + 1).setType(Material.BARREL);
        }
    }

    private void furnishLibrary(World world, int x, int y, int z, int w, int d) {
        // Bookshelves along walls
        for (int bz = 1; bz < d - 1; bz++) {
            world.getBlockAt(x + 1, y + 1, z + bz).setType(Material.BOOKSHELF);
            world.getBlockAt(x + 1, y + 2, z + bz).setType(Material.BOOKSHELF);
            world.getBlockAt(x + w - 2, y + 1, z + bz).setType(Material.BOOKSHELF);
            world.getBlockAt(x + w - 2, y + 2, z + bz).setType(Material.BOOKSHELF);
        }

        // Reading desk
        world.getBlockAt(x + w / 2, y + 1, z + d / 2).setType(Material.OAK_STAIRS);
        setStairFacing(world.getBlockAt(x + w / 2, y + 1, z + d / 2), BlockFace.NORTH);
        world.getBlockAt(x + w / 2, y + 1, z + d / 2 - 1).setType(Material.LECTERN);
    }

    private void furnishCellar(World world, int x, int y, int z, int w, int d) {
        // Wine barrels
        for (int bx = 1; bx < w - 1; bx += 2) {
            world.getBlockAt(x + bx, y + 1, z + d - 2).setType(Material.BARREL);
        }

        // Chests for storage
        world.getBlockAt(x + 1, y + 1, z + 1).setType(Material.CHEST);
        world.getBlockAt(x + w - 2, y + 1, z + 1).setType(Material.CHEST);

        // Cobwebs for atmosphere
        if (random.nextFloat() < 0.3f) {
            world.getBlockAt(x + w / 2, y + 3, z + d / 2).setType(Material.COBWEB);
        }
    }

    public long getSeed() {
        return seed;
    }
}
