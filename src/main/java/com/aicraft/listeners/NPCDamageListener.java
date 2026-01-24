package com.aicraft.listeners;

import com.aicraft.AICompanions;
import com.aicraft.factions.FactionManager;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import com.aicraft.npcs.boss.BossManager;
import com.aicraft.npcs.loot.NPCLootManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Handles damage and death events for NPCs
 */
public class NPCDamageListener implements Listener {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final FactionManager factionManager;
    private final NPCLootManager lootManager;
    private final BossManager bossManager;

    public NPCDamageListener(AICompanions plugin, NPCManager npcManager, FactionManager factionManager, BossManager bossManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.factionManager = factionManager;
        this.lootManager = new NPCLootManager(plugin);
        this.bossManager = bossManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        AINpc npc = npcManager.getNPCFromEntity(entity);

        if (npc == null) return;

        // Check if NPC can take this type of damage
        if (event instanceof EntityDamageByEntityEvent damageByEntity) {
            Entity damager = damageByEntity.getDamager();

            // Damage from players
            if (damager instanceof Player player) {
                if (!plugin.getConfig().getBoolean("npcs.vulnerability.damage-from-players", false)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.GRAY + "*" + npc.getName() + " cannot be harmed*");
                    return;
                }
            }

            // Damage from monsters
            if (damager instanceof Monster) {
                if (!plugin.getConfig().getBoolean("npcs.vulnerability.damage-from-mobs", true)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Damage from other NPCs
            AINpc attackerNpc = npcManager.getNPCFromEntity(damager);
            if (attackerNpc != null) {
                if (!plugin.getConfig().getBoolean("npcs.vulnerability.damage-from-npcs", true)) {
                    event.setCancelled(true);
                    return;
                }

                // Check faction hostility
                if (!factionManager.areHostile(npc.getFaction(), attackerNpc.getFaction())) {
                    event.setCancelled(true); // Friendly fire disabled
                    return;
                }
            }
        }

        // Update NPC health tracking
        double newHealth = npc.getHealth() - event.getFinalDamage();
        npc.setHealth(newHealth);

        // Update mood when damaged
        npc.setCurrentMood("distressed");

        plugin.debug(npc.getName() + " took " + event.getFinalDamage() +
                " damage, health now: " + npc.getHealth());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        AINpc npc = npcManager.getNPCFromEntity(entity);

        if (npc == null) return;

        // Determine killer
        Entity killer = null;
        Player killerPlayer = null;
        if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
            killer = damageEvent.getDamager();
            if (killer instanceof Player) {
                killerPlayer = (Player) killer;
            }
        }

        // Clear default drops
        event.getDrops().clear();

        // Check if this is a boss - bosses have special loot handling
        if (bossManager != null && bossManager.isBoss(npc)) {
            // Boss death - handled by BossManager for legendary drops
            if (killerPlayer != null) {
                bossManager.onBossDeath(npc, killerPlayer);
            }
            // Boss drops are spawned by BossManager, not here
            event.setDroppedExp(100 + (int)(Math.random() * 100)); // Bosses drop lots of XP
        } else {
            // Regular NPC loot
            if (plugin.getConfig().getBoolean("npcs.loot.enabled", true)) {
                List<ItemStack> loot = lootManager.generateLoot(npc, killerPlayer);
                event.getDrops().addAll(loot);

                // Log drops for debug
                if (!loot.isEmpty()) {
                    plugin.debug(npc.getName() + " dropped " + loot.size() + " item(s)");
                }
            }
        }

        // Calculate and set experience drop
        if (plugin.getConfig().getBoolean("npcs.loot.drop-experience", true)) {
            int baseExp = plugin.getConfig().getInt("npcs.loot.base-experience", 5);
            int exp = calculateExperience(npc, baseExp);
            event.setDroppedExp(exp);
        } else {
            event.setDroppedExp(0);
        }

        // Handle NPC death
        npcManager.handleDeath(npc, killer);

        // Notify nearby players
        String deathMessage;
        if (killer != null) {
            AINpc killerNpc = npcManager.getNPCFromEntity(killer);
            if (killerNpc != null) {
                deathMessage = npc.getName() + " was slain by " + killerNpc.getName();
            } else if (killer instanceof Player player) {
                deathMessage = npc.getName() + " was slain by " + player.getName();
            } else {
                deathMessage = npc.getName() + " was killed by a " +
                        killer.getType().name().toLowerCase().replace('_', ' ');
            }
        } else {
            deathMessage = npc.getName() + " has died";
        }

        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distance(entity.getLocation()) < 50) {
                player.sendMessage(ChatColor.GRAY + "[" + ChatColor.RED + "Death" +
                        ChatColor.GRAY + "] " + deathMessage);
            }
        }
    }

    @EventHandler
    public void onNPCAttacksEntity(EntityDamageByEntityEvent event) {
        // Check if an NPC is the attacker
        AINpc attackerNpc = npcManager.getNPCFromEntity(event.getDamager());
        if (attackerNpc == null) return;

        Entity victim = event.getEntity();

        // NPC attacking player
        if (victim instanceof Player player) {
            // Only hostile NPCs should attack players
            if (!attackerNpc.isHostile()) {
                event.setCancelled(true);
                return;
            }
        }

        // NPC attacking another NPC
        AINpc victimNpc = npcManager.getNPCFromEntity(victim);
        if (victimNpc != null) {
            // Only attack if factions are hostile
            if (!factionManager.areHostile(attackerNpc.getFaction(), victimNpc.getFaction())) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Calculate experience drop based on NPC faction and hostility
     */
    private int calculateExperience(AINpc npc, int baseExp) {
        double multiplier = 1.0;

        // Hostile NPCs give more XP
        if (npc.isHostile()) {
            multiplier *= 2.0;
        }

        // Faction multipliers
        String faction = npc.getFaction() != null ? npc.getFaction().toLowerCase() : "";
        switch (faction) {
            case "bandits" -> multiplier *= 1.5;   // Combat NPCs
            case "cultists" -> multiplier *= 2.0;  // Dangerous enemies
            case "guards" -> multiplier *= 1.3;    // Trained fighters
            case "merchants" -> multiplier *= 0.5; // Non-combatants
            case "villagers" -> multiplier *= 0.3; // Civilians
            case "wanderers" -> multiplier *= 0.8; // Misc
        }

        // Add some randomness (80% - 120%)
        multiplier *= 0.8 + (Math.random() * 0.4);

        return Math.max(1, (int) (baseExp * multiplier));
    }
}
