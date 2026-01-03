package com.aicraft.listeners;

import com.aicraft.AICompanions;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Applies special effects to players wearing/holding custom loot items
 * Effects are based on rarity and item type
 */
public class LootEffectsListener implements Listener {

    private final AICompanions plugin;
    private final NamespacedKey rarityKey;

    // Track players with active effects to avoid re-applying constantly
    private final Map<UUID, Set<String>> activePlayerEffects = new HashMap<>();

    // Effect definitions by item name keywords
    private static final Map<String, EffectData[]> ITEM_EFFECTS = new HashMap<>();

    static {
        // === WEAPONS ===
        // Swords with fire-related names
        ITEM_EFFECTS.put("infernal", new EffectData[]{
            new EffectData(PotionEffectType.FIRE_RESISTANCE, 0, true)
        });
        ITEM_EFFECTS.put("blaze", new EffectData[]{
            new EffectData(PotionEffectType.FIRE_RESISTANCE, 0, true)
        });
        ITEM_EFFECTS.put("flame", new EffectData[]{
            new EffectData(PotionEffectType.FIRE_RESISTANCE, 0, true)
        });

        // Void/Ender items
        ITEM_EFFECTS.put("void", new EffectData[]{
            new EffectData(PotionEffectType.SLOW_FALLING, 0, true),
            new EffectData(PotionEffectType.NIGHT_VISION, 0, true)
        });
        ITEM_EFFECTS.put("ender", new EffectData[]{
            new EffectData(PotionEffectType.SLOW_FALLING, 0, true)
        });
        ITEM_EFFECTS.put("abyssal", new EffectData[]{
            new EffectData(PotionEffectType.NIGHT_VISION, 0, true),
            new EffectData(PotionEffectType.WATER_BREATHING, 0, true)
        });

        // Speed/agility items
        ITEM_EFFECTS.put("swift", new EffectData[]{
            new EffectData(PotionEffectType.SPEED, 0, true)
        });
        ITEM_EFFECTS.put("wings", new EffectData[]{
            new EffectData(PotionEffectType.SLOW_FALLING, 1, true),
            new EffectData(PotionEffectType.SPEED, 0, true)
        });
        ITEM_EFFECTS.put("wanderer", new EffectData[]{
            new EffectData(PotionEffectType.SPEED, 0, true),
            new EffectData(PotionEffectType.JUMP_BOOST, 0, true)
        });
        ITEM_EFFECTS.put("traveler", new EffectData[]{
            new EffectData(PotionEffectType.SPEED, 0, true)
        });

        // Combat items
        ITEM_EFFECTS.put("marauder", new EffectData[]{
            new EffectData(PotionEffectType.STRENGTH, 0, true)
        });
        ITEM_EFFECTS.put("dread", new EffectData[]{
            new EffectData(PotionEffectType.STRENGTH, 1, true),
            new EffectData(PotionEffectType.RESISTANCE, 0, true)
        });
        ITEM_EFFECTS.put("reaver", new EffectData[]{
            new EffectData(PotionEffectType.STRENGTH, 0, true),
            new EffectData(PotionEffectType.HASTE, 0, true)
        });
        ITEM_EFFECTS.put("captain", new EffectData[]{
            new EffectData(PotionEffectType.RESISTANCE, 0, true)
        });
        ITEM_EFFECTS.put("vengeance", new EffectData[]{
            new EffectData(PotionEffectType.STRENGTH, 0, true)
        });
        ITEM_EFFECTS.put("vindicator", new EffectData[]{
            new EffectData(PotionEffectType.STRENGTH, 0, true),
            new EffectData(PotionEffectType.HASTE, 0, true)
        });

        // Defensive items
        ITEM_EFFECTS.put("warden", new EffectData[]{
            new EffectData(PotionEffectType.RESISTANCE, 1, true),
            new EffectData(PotionEffectType.REGENERATION, 0, true)
        });
        ITEM_EFFECTS.put("aegis", new EffectData[]{
            new EffectData(PotionEffectType.RESISTANCE, 1, true),
            new EffectData(PotionEffectType.FIRE_RESISTANCE, 0, true)
        });
        ITEM_EFFECTS.put("guardian", new EffectData[]{
            new EffectData(PotionEffectType.RESISTANCE, 0, true),
            new EffectData(PotionEffectType.REGENERATION, 0, true)
        });
        ITEM_EFFECTS.put("elite", new EffectData[]{
            new EffectData(PotionEffectType.RESISTANCE, 0, true)
        });
        ITEM_EFFECTS.put("brigand", new EffectData[]{
            new EffectData(PotionEffectType.RESISTANCE, 0, true)
        });

        // Magic/Ritual items
        ITEM_EFFECTS.put("geodjian", new EffectData[]{
            new EffectData(PotionEffectType.NIGHT_VISION, 0, true),
            new EffectData(PotionEffectType.DARKNESS, 0, false) // Debuff!
        });
        ITEM_EFFECTS.put("cursed", new EffectData[]{
            new EffectData(PotionEffectType.STRENGTH, 1, true),
            new EffectData(PotionEffectType.HUNGER, 0, false) // Debuff!
        });
        ITEM_EFFECTS.put("corrupted", new EffectData[]{
            new EffectData(PotionEffectType.STRENGTH, 0, true),
            new EffectData(PotionEffectType.WITHER, 0, false) // Debuff! (very light)
        });
        ITEM_EFFECTS.put("blessed", new EffectData[]{
            new EffectData(PotionEffectType.REGENERATION, 0, true),
            new EffectData(PotionEffectType.LUCK, 0, true)
        });
        ITEM_EFFECTS.put("sacred", new EffectData[]{
            new EffectData(PotionEffectType.REGENERATION, 0, true)
        });
        ITEM_EFFECTS.put("radiant", new EffectData[]{
            new EffectData(PotionEffectType.GLOWING, 0, true),
            new EffectData(PotionEffectType.REGENERATION, 0, true)
        });

        // Stealth/Shadow items
        ITEM_EFFECTS.put("shadow", new EffectData[]{
            new EffectData(PotionEffectType.INVISIBILITY, 0, true),
            new EffectData(PotionEffectType.SPEED, 0, true)
        });
        ITEM_EFFECTS.put("nightmare", new EffectData[]{
            new EffectData(PotionEffectType.SLOW_FALLING, 0, true),
            new EffectData(PotionEffectType.NIGHT_VISION, 0, true)
        });
        ITEM_EFFECTS.put("ethereal", new EffectData[]{
            new EffectData(PotionEffectType.SLOW_FALLING, 0, true)
        });
        ITEM_EFFECTS.put("spectral", new EffectData[]{
            new EffectData(PotionEffectType.INVISIBILITY, 0, true)
        });

        // Luck/Fortune items
        ITEM_EFFECTS.put("merchant", new EffectData[]{
            new EffectData(PotionEffectType.LUCK, 1, true),
            new EffectData(PotionEffectType.HERO_OF_THE_VILLAGE, 0, true)
        });
        ITEM_EFFECTS.put("fortune", new EffectData[]{
            new EffectData(PotionEffectType.LUCK, 0, true)
        });
        ITEM_EFFECTS.put("treasure", new EffectData[]{
            new EffectData(PotionEffectType.LUCK, 0, true)
        });
        ITEM_EFFECTS.put("commerce", new EffectData[]{
            new EffectData(PotionEffectType.LUCK, 1, true),
            new EffectData(PotionEffectType.HERO_OF_THE_VILLAGE, 0, true)
        });

        // Ocean/Water items
        ITEM_EFFECTS.put("ocean", new EffectData[]{
            new EffectData(PotionEffectType.WATER_BREATHING, 0, true),
            new EffectData(PotionEffectType.DOLPHINS_GRACE, 0, true)
        });
        ITEM_EFFECTS.put("drowned", new EffectData[]{
            new EffectData(PotionEffectType.WATER_BREATHING, 0, true)
        });
        ITEM_EFFECTS.put("trident", new EffectData[]{
            new EffectData(PotionEffectType.DOLPHINS_GRACE, 0, true)
        });
        ITEM_EFFECTS.put("deep", new EffectData[]{
            new EffectData(PotionEffectType.WATER_BREATHING, 0, true),
            new EffectData(PotionEffectType.NIGHT_VISION, 0, true)
        });

        // Guiding/Navigation items
        ITEM_EFFECTS.put("guiding", new EffectData[]{
            new EffectData(PotionEffectType.NIGHT_VISION, 0, true),
            new EffectData(PotionEffectType.SPEED, 0, true)
        });
        ITEM_EFFECTS.put("light", new EffectData[]{
            new EffectData(PotionEffectType.NIGHT_VISION, 0, true)
        });
        ITEM_EFFECTS.put("far-seer", new EffectData[]{
            new EffectData(PotionEffectType.NIGHT_VISION, 0, true)
        });

        // Wither/Death items
        ITEM_EFFECTS.put("wither", new EffectData[]{
            new EffectData(PotionEffectType.FIRE_RESISTANCE, 0, true),
            new EffectData(PotionEffectType.WITHER, 0, false) // Slight debuff
        });
        ITEM_EFFECTS.put("death", new EffectData[]{
            new EffectData(PotionEffectType.STRENGTH, 0, true),
            new EffectData(PotionEffectType.HUNGER, 0, false)
        });

        // Exotic/Special
        ITEM_EFFECTS.put("exotic", new EffectData[]{
            new EffectData(PotionEffectType.SLOW_FALLING, 1, true),
            new EffectData(PotionEffectType.SPEED, 1, true)
        });
        ITEM_EFFECTS.put("elytra", new EffectData[]{
            new EffectData(PotionEffectType.SLOW_FALLING, 1, true)
        });

        // Mining/Utility
        ITEM_EFFECTS.put("ancient", new EffectData[]{
            new EffectData(PotionEffectType.HASTE, 0, true)
        });
    }

    public LootEffectsListener(AICompanions plugin) {
        this.plugin = plugin;
        this.rarityKey = new NamespacedKey(plugin, "loot_rarity");

        // Start the effect refresh task
        startEffectRefreshTask();
    }

    /**
     * Periodically refresh effects for players holding/wearing special items
     */
    private void startEffectRefreshTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    applyEquipmentEffects(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 60L); // Every 3 seconds
    }

    /**
     * Apply effects based on equipped armor and held items
     */
    private void applyEquipmentEffects(Player player) {
        Set<String> currentEffects = new HashSet<>();

        // Check held item
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.hasItemMeta()) {
            checkItemForEffects(player, mainHand, currentEffects);
        }

        // Check off-hand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.hasItemMeta()) {
            checkItemForEffects(player, offHand, currentEffects);
        }

        // Check armor
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.hasItemMeta()) {
                checkItemForEffects(player, armor, currentEffects);
            }
        }

        // Store current effects
        activePlayerEffects.put(player.getUniqueId(), currentEffects);
    }

    /**
     * Check an item for special effects and apply them
     */
    private void checkItemForEffects(Player player, ItemStack item, Set<String> appliedEffects) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
        Rarity rarity = getRarityFromLore(meta);

        // Only rare+ items have effects
        if (rarity.ordinal() < Rarity.RARE.ordinal()) return;

        // Check each effect keyword
        for (Map.Entry<String, EffectData[]> entry : ITEM_EFFECTS.entrySet()) {
            if (displayName.contains(entry.getKey())) {
                for (EffectData effectData : entry.getValue()) {
                    String effectKey = entry.getKey() + "_" + effectData.type.getKey().getKey();

                    // Don't re-apply if already active
                    if (appliedEffects.contains(effectKey)) continue;

                    // Calculate amplifier based on rarity
                    int amplifier = effectData.baseAmplifier;
                    if (rarity == Rarity.EPIC) amplifier += 1;
                    if (rarity == Rarity.LEGENDARY) amplifier += 2;

                    // Cap amplifier for debuffs
                    if (!effectData.isBuff) {
                        amplifier = Math.min(amplifier, 0); // Keep debuffs weak
                    }

                    // Apply the effect (5 second duration, refreshed every 3 seconds)
                    player.addPotionEffect(new PotionEffect(
                        effectData.type,
                        100, // 5 seconds
                        amplifier,
                        true,  // ambient
                        false, // no particles
                        true   // show icon
                    ));

                    appliedEffects.add(effectKey);
                }
            }
        }
    }

    /**
     * Get rarity from item lore
     */
    private Rarity getRarityFromLore(ItemMeta meta) {
        if (!meta.hasLore()) return Rarity.COMMON;

        for (String line : meta.getLore()) {
            String stripped = ChatColor.stripColor(line).toUpperCase();
            for (Rarity r : Rarity.values()) {
                if (stripped.equals(r.name())) {
                    return r;
                }
            }
        }
        return Rarity.COMMON;
    }

    /**
     * Special on-hit effects for weapons
     */
    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;

        String displayName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
        Rarity rarity = getRarityFromLore(meta);

        // Only epic+ weapons have on-hit effects
        if (rarity.ordinal() < Rarity.EPIC.ordinal()) return;

        // Life steal for "vampire" or "blood" weapons
        if (displayName.contains("blood") || displayName.contains("vampire") || displayName.contains("drain")) {
            double heal = event.getFinalDamage() * 0.2; // 20% life steal
            player.setHealth(Math.min(player.getHealth() + heal, player.getMaxHealth()));
        }

        // Fire effect for infernal weapons
        if (displayName.contains("infernal") || displayName.contains("blaze") || displayName.contains("fire")) {
            event.getEntity().setFireTicks(60); // 3 seconds of fire
        }

        // Wither effect for death weapons
        if (displayName.contains("wither") || displayName.contains("death") || displayName.contains("doom")) {
            if (event.getEntity() instanceof org.bukkit.entity.LivingEntity living) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
            }
        }

        // Slow effect for frost weapons
        if (displayName.contains("frost") || displayName.contains("ice") || displayName.contains("frozen")) {
            if (event.getEntity() instanceof org.bukkit.entity.LivingEntity living) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            }
        }

        // Bonus damage for legendary weapons
        if (rarity == Rarity.LEGENDARY) {
            event.setDamage(event.getDamage() * 1.25); // 25% bonus damage
        }
    }

    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent event) {
        // Trigger an immediate effect refresh when switching items
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            applyEquipmentEffects(event.getPlayer());
        }, 1L);
    }

    private enum Rarity {
        COMMON,
        UNCOMMON,
        RARE,
        EPIC,
        LEGENDARY
    }

    private static class EffectData {
        final PotionEffectType type;
        final int baseAmplifier;
        final boolean isBuff;

        EffectData(PotionEffectType type, int baseAmplifier, boolean isBuff) {
            this.type = type;
            this.baseAmplifier = baseAmplifier;
            this.isBuff = isBuff;
        }
    }
}
