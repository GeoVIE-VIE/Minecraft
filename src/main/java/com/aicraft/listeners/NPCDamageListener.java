package com.aicraft.listeners;

import com.aicraft.AICompanions;
import com.aicraft.factions.FactionManager;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
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

/**
 * Handles damage and death events for NPCs
 */
public class NPCDamageListener implements Listener {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final FactionManager factionManager;

    public NPCDamageListener(AICompanions plugin, NPCManager npcManager, FactionManager factionManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.factionManager = factionManager;
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
        if (entity.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
            killer = damageEvent.getDamager();
        }

        // Clear drops (NPCs don't drop items by default)
        event.getDrops().clear();
        event.setDroppedExp(0);

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
}
