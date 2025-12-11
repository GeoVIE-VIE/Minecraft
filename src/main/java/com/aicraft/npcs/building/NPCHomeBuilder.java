package com.aicraft.npcs.building;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages NPC home building
 * NPCs can claim plots and build simple structures
 */
public class NPCHomeBuilder {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private NPCTelevision tvManager;

    // Track NPC homes
    private final Map<UUID, NPCHome> npcHomes = new ConcurrentHashMap<>();

    private BukkitTask buildTask;
    private final Random random = new Random();

    // Building materials by faction
    private static final Map<String, Material[]> FACTION_MATERIALS = new HashMap<>();
    static {
        FACTION_MATERIALS.put("villagers", new Material[]{Material.OAK_PLANKS, Material.OAK_LOG, Material.COBBLESTONE});
        FACTION_MATERIALS.put("guards", new Material[]{Material.STONE_BRICKS, Material.COBBLESTONE, Material.OAK_PLANKS});
        FACTION_MATERIALS.put("merchants", new Material[]{Material.BIRCH_PLANKS, Material.BIRCH_LOG, Material.GLASS});
        FACTION_MATERIALS.put("wanderers", new Material[]{Material.SPRUCE_PLANKS, Material.SPRUCE_LOG, Material.DIRT});
        FACTION_MATERIALS.put("bandits", new Material[]{Material.DARK_OAK_PLANKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE});
        FACTION_MATERIALS.put("cultists", new Material[]{Material.DEEPSLATE_BRICKS, Material.BLACKSTONE, Material.PURPLE_TERRACOTTA});
    }

    public NPCHomeBuilder(AICompanions plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    /**
     * Start the building task
     */
    public void start() {
        // Initialize TV manager
        tvManager = new NPCTelevision(plugin, npcManager);
        tvManager.start();

        // Building task runs every 5 minutes
        buildTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processBuildingAttempts, 6000L, 6000L);
        plugin.getLogger().info("NPC Home Builder started");
    }

    /**
     * Stop the building task
     */
    public void stop() {
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
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
            if (npcHomes.containsKey(npc.getUuid())) continue; // Already has home
            if (npc.isHostile()) continue; // Hostile NPCs don't build homes

            // 5% chance per cycle to attempt building
            if (random.nextDouble() > 0.05) continue;

            // Try to build a home
            Location loc = findBuildLocation(npc);
            if (loc != null) {
                buildHome(npc, loc);
            }
        }
    }

    /**
     * Find a suitable location to build
     */
    private Location findBuildLocation(AINpc npc) {
        Location spawn = npc.getSpawnLocation();
        if (spawn == null || spawn.getWorld() == null) return null;

        // Try to find a flat area near spawn
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

    /**
     * Check if location is valid for building
     */
    private boolean isValidBuildLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        // Check it's not underwater
        Block block = loc.getBlock();
        if (block.isLiquid()) return false;

        // Check area is relatively flat
        int baseY = loc.getBlockY();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                int y = loc.getWorld().getHighestBlockYAt(loc.getBlockX() + x, loc.getBlockZ() + z);
                if (Math.abs(y - baseY) > 2) return false;
            }
        }

        // Check no existing homes nearby
        for (NPCHome home : npcHomes.values()) {
            if (home.getLocation().getWorld().equals(loc.getWorld()) &&
                    home.getLocation().distance(loc) < 15) {
                return false;
            }
        }

        return true;
    }

    /**
     * Build a home for an NPC
     */
    public void buildHome(AINpc npc, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        // Get materials based on faction
        String faction = npc.getFaction() != null ? npc.getFaction().toLowerCase() : "villagers";
        Material[] mats = FACTION_MATERIALS.getOrDefault(faction, FACTION_MATERIALS.get("villagers"));

        Material wall = mats[0];
        Material corner = mats[1];
        Material floor = mats[2];

        int baseX = loc.getBlockX();
        int baseY = loc.getBlockY();
        int baseZ = loc.getBlockZ();

        // Build a simple 5x5x4 house
        // Floor
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                world.getBlockAt(baseX + x, baseY, baseZ + z).setType(floor);
            }
        }

        // Walls
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 5; x++) {
                // Front and back walls
                if (!(x == 2 && y <= 2)) { // Door opening
                    world.getBlockAt(baseX + x, baseY + y, baseZ).setType(wall);
                }
                world.getBlockAt(baseX + x, baseY + y, baseZ + 4).setType(wall);
            }
            for (int z = 1; z < 4; z++) {
                // Side walls
                world.getBlockAt(baseX, baseY + y, baseZ + z).setType(wall);
                world.getBlockAt(baseX + 4, baseY + y, baseZ + z).setType(wall);
            }
        }

        // Corners with logs
        for (int y = 1; y <= 3; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX + 4, baseY + y, baseZ).setType(corner);
            world.getBlockAt(baseX, baseY + y, baseZ + 4).setType(corner);
            world.getBlockAt(baseX + 4, baseY + y, baseZ + 4).setType(corner);
        }

        // Roof (simple flat roof)
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                world.getBlockAt(baseX + x, baseY + 4, baseZ + z).setType(wall);
            }
        }

        // Add a torch inside
        world.getBlockAt(baseX + 2, baseY + 2, baseZ + 2).setType(Material.TORCH);

        // Add a TV against the back wall (if TV system is enabled)
        if (tvManager != null && plugin.getConfig().getBoolean("npcs.television.enabled", true)) {
            Location tvLoc = new Location(world, baseX + 1, baseY + 1, baseZ + 3);
            tvManager.buildTV(tvLoc, npc, BlockFace.NORTH);
        }

        // Add a chair/seat in front of TV
        world.getBlockAt(baseX + 2, baseY + 1, baseZ + 2).setType(Material.OAK_STAIRS);

        // Add name sign above door
        Block signBlock = world.getBlockAt(baseX + 2, baseY + 3, baseZ - 1);
        placeNameSign(signBlock, npc, BlockFace.NORTH);

        // Register the home
        NPCHome home = new NPCHome(npc.getUuid(), loc, 5);
        npcHomes.put(npc.getUuid(), home);

        // Announce to nearby players
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(loc) <= 50) {
                player.sendMessage(ChatColor.GOLD + npc.getName() + ChatColor.GRAY +
                        " has built a home nearby!");
            }
        }

        plugin.debug(npc.getName() + " built a home at " + baseX + ", " + baseY + ", " + baseZ);
    }

    /**
     * Place a sign with the NPC's name
     */
    private void placeNameSign(Block block, AINpc npc, BlockFace facing) {
        // Place sign
        Material signMaterial = Material.OAK_WALL_SIGN;
        block.setType(signMaterial);

        // Configure sign direction
        if (block.getBlockData() instanceof WallSign wallSign) {
            wallSign.setFacing(facing);
            block.setBlockData(wallSign);
        }

        // Set sign text
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

    /**
     * Get an NPC's home
     */
    public NPCHome getHome(UUID npcUuid) {
        return npcHomes.get(npcUuid);
    }

    /**
     * Check if NPC has a home
     */
    public boolean hasHome(UUID npcUuid) {
        return npcHomes.containsKey(npcUuid);
    }

    /**
     * Remove an NPC's home (doesn't destroy blocks)
     */
    public void removeHome(UUID npcUuid) {
        npcHomes.remove(npcUuid);
    }

    /**
     * Get all homes
     */
    public Collection<NPCHome> getAllHomes() {
        return npcHomes.values();
    }

    /**
     * Get the TV manager
     */
    public NPCTelevision getTVManager() {
        return tvManager;
    }

    /**
     * Represents an NPC's home
     */
    public static class NPCHome {
        private final UUID ownerUuid;
        private final Location location;
        private final int size;
        private final long builtAt;

        public NPCHome(UUID ownerUuid, Location location, int size) {
            this.ownerUuid = ownerUuid;
            this.location = location;
            this.size = size;
            this.builtAt = System.currentTimeMillis();
        }

        public UUID getOwnerUuid() { return ownerUuid; }
        public Location getLocation() { return location; }
        public int getSize() { return size; }
        public long getBuiltAt() { return builtAt; }
    }
}
