package com.aicraft.npcs.building;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages billboards that display NPC trades and services
 * Billboards appear near NPCs that can trade or give quests
 */
public class BillboardManager {

    private final AICompanions plugin;
    private final NPCManager npcManager;

    // Billboard storage (NPC UUID -> Billboard)
    private final Map<UUID, NPCBillboard> billboards = new HashMap<>();

    // Update task
    private BukkitTask updateTask;

    // Config
    private boolean billboardsEnabled;
    private int updateInterval;
    private double billboardRange; // How close player must be to see billboard

    public BillboardManager(AICompanions plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        loadConfig();
    }

    private void loadConfig() {
        billboardsEnabled = plugin.getConfig().getBoolean("billboards.enabled", true);
        updateInterval = plugin.getConfig().getInt("billboards.update-interval", 100); // 5 seconds
        billboardRange = plugin.getConfig().getDouble("billboards.visible-range", 20.0);
    }

    /**
     * Start the billboard management system
     */
    public void start() {
        if (!billboardsEnabled) {
            plugin.getLogger().info("Billboards are disabled in config");
            return;
        }

        // Initial billboard creation for existing NPCs
        Bukkit.getScheduler().runTaskLater(plugin, this::createInitialBillboards, 100L);

        // Periodic update task
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBillboards, 200L, updateInterval);

        plugin.getLogger().info("Billboard manager started");
    }

    /**
     * Stop the billboard system
     */
    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }

        // Despawn all billboards
        for (NPCBillboard billboard : billboards.values()) {
            billboard.despawn();
        }
        billboards.clear();
    }

    /**
     * Create billboards for all existing NPCs that can trade or give quests
     */
    private void createInitialBillboards() {
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (shouldHaveBillboard(npc)) {
                createBillboard(npc);
            }
        }
        plugin.getLogger().info("Created " + billboards.size() + " NPC billboards");
    }

    /**
     * Check if an NPC should have a billboard
     */
    private boolean shouldHaveBillboard(AINpc npc) {
        if (!npc.isAlive() || !npc.isSpawned()) return false;

        // Merchants always get billboards
        if (npc.canTrade()) return true;

        // Quest givers get billboards
        if (npc.canGiveQuests()) return true;

        return false;
    }

    /**
     * Create a billboard for an NPC
     */
    public void createBillboard(AINpc npc) {
        if (billboards.containsKey(npc.getUuid())) return;

        Location npcLoc = npc.getCurrentLocation();
        if (npcLoc == null || npcLoc.getWorld() == null) return;

        // Position billboard slightly behind and above NPC
        Location billboardLoc = npcLoc.clone().add(0, 0, -1);

        NPCBillboard billboard = new NPCBillboard(npc, billboardLoc);
        billboard.spawn();

        billboards.put(npc.getUuid(), billboard);
        plugin.debug("Created billboard for NPC: " + npc.getName());
    }

    /**
     * Remove a billboard for an NPC
     */
    public void removeBillboard(UUID npcUuid) {
        NPCBillboard billboard = billboards.remove(npcUuid);
        if (billboard != null) {
            billboard.despawn();
        }
    }

    /**
     * Update all billboards periodically
     */
    private void updateBillboards() {
        for (Map.Entry<UUID, NPCBillboard> entry : new HashMap<>(billboards).entrySet()) {
            UUID npcUuid = entry.getKey();
            NPCBillboard billboard = entry.getValue();

            AINpc npc = npcManager.getNPCByUUID(npcUuid);

            if (npc == null || !npc.isAlive() || !npc.isSpawned()) {
                // NPC is gone, remove billboard
                billboard.despawn();
                billboards.remove(npcUuid);
                continue;
            }

            // Check if billboard needs to move (NPC moved)
            Location npcLoc = npc.getCurrentLocation();
            if (npcLoc != null) {
                double dist = billboard.getLocation().distance(npcLoc.clone().add(0, 2.5, -1));
                if (dist > 3) {
                    // NPC moved significantly, update billboard position
                    billboard.moveTo(npcLoc.clone().add(0, 0, -1));
                }
            }

            // Update content (mood changes, etc.)
            billboard.update(npc);
        }

        // Check for new NPCs that need billboards
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (shouldHaveBillboard(npc) && !billboards.containsKey(npc.getUuid())) {
                createBillboard(npc);
            }
        }
    }

    /**
     * Get a billboard by NPC UUID
     */
    public NPCBillboard getBillboard(UUID npcUuid) {
        return billboards.get(npcUuid);
    }

    /**
     * Check if an NPC has a billboard
     */
    public boolean hasBillboard(UUID npcUuid) {
        return billboards.containsKey(npcUuid);
    }

    /**
     * Get count of active billboards
     */
    public int getBillboardCount() {
        return billboards.size();
    }

    /**
     * Called when a new NPC is spawned
     */
    public void onNPCSpawned(AINpc npc) {
        if (billboardsEnabled && shouldHaveBillboard(npc)) {
            // Delay slightly to ensure NPC entity is ready
            Bukkit.getScheduler().runTaskLater(plugin, () -> createBillboard(npc), 20L);
        }
    }

    /**
     * Called when an NPC is removed/despawned
     */
    public void onNPCRemoved(UUID npcUuid) {
        removeBillboard(npcUuid);
    }
}
