package com.aicraft.listeners;

import com.aicraft.AICompanions;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Handles custom loot drops from vanilla Minecraft monsters
 */
public class MobLootListener implements Listener {

    private final AICompanions plugin;
    private final Random random = new Random();

    // Cached enchantments
    private Enchantment enchSharpness;
    private Enchantment enchProtection;
    private Enchantment enchUnbreaking;

    // Mob loot tables
    private final Map<EntityType, List<LootEntry>> mobLootTables = new EnumMap<>(EntityType.class);

    public MobLootListener(AICompanions plugin) {
        this.plugin = plugin;
        initializeEnchantments();
        initializeMobLootTables();
    }

    private void initializeEnchantments() {
        try {
            enchSharpness = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
            enchProtection = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
            enchUnbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
        } catch (Exception e) {
            plugin.getLogger().warning("MobLootListener: Failed to initialize enchantments: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Skip if not enabled
        if (!plugin.getConfig().getBoolean("npcs.loot.mob-loot-enabled", true)) {
            return;
        }

        // Skip non-monsters and AI NPCs (handled by NPCDamageListener)
        if (!(entity instanceof Monster)) return;
        if (entity.hasMetadata("ainpc")) return;

        // Get loot table for this mob type
        List<LootEntry> lootTable = mobLootTables.get(entity.getType());
        if (lootTable == null || lootTable.isEmpty()) return;

        // Base drop chance
        double baseChance = plugin.getConfig().getDouble("npcs.loot.mob-drop-chance", 0.15);

        // Looting enchantment bonus from killer
        Player killer = entity.getKiller();
        if (killer != null && killer.getInventory().getItemInMainHand() != null) {
            Enchantment looting = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("looting"));
            if (looting != null) {
                int lootingLevel = killer.getInventory().getItemInMainHand().getEnchantmentLevel(looting);
                baseChance += lootingLevel * 0.05; // +5% per looting level
            }
        }

        // Roll for drop
        if (random.nextDouble() > baseChance) return;

        // Pick random item from loot table
        LootEntry entry = lootTable.get(random.nextInt(lootTable.size()));

        // Calculate amount
        int amount = entry.minAmount;
        if (entry.maxAmount > entry.minAmount) {
            amount += random.nextInt(entry.maxAmount - entry.minAmount + 1);
        }

        ItemStack item = new ItemStack(entry.material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null && entry.name != null) {
            // Roll for rarity
            Rarity rarity = rollRarity();

            meta.setDisplayName(rarity.color + entry.name);

            List<String> lore = new ArrayList<>();
            lore.add(rarity.color + rarity.name());
            lore.add(ChatColor.GRAY + "Dropped by: " + ChatColor.WHITE + formatMobName(entity.getType()));

            if (entry.lore != null) {
                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + entry.lore);
            }

            meta.setLore(lore);

            // Add enchantments for rare+ items
            if (rarity.ordinal() >= Rarity.RARE.ordinal()) {
                addEnchantments(item, meta, rarity);
            }

            item.setItemMeta(meta);
        }

        // Add to drops
        event.getDrops().add(item);

        plugin.debug("Mob " + entity.getType() + " dropped custom loot: " + entry.material);
    }

    private Rarity rollRarity() {
        int roll = random.nextInt(100);
        if (roll < 65) return Rarity.COMMON;
        if (roll < 85) return Rarity.UNCOMMON;
        if (roll < 95) return Rarity.RARE;
        if (roll < 99) return Rarity.EPIC;
        return Rarity.LEGENDARY;
    }

    private void addEnchantments(ItemStack item, ItemMeta meta, Rarity rarity) {
        Material mat = item.getType();
        List<Enchantment> applicable = new ArrayList<>();

        if (mat.name().contains("SWORD") && enchSharpness != null) {
            applicable.add(enchSharpness);
        }
        if ((mat.name().contains("HELMET") || mat.name().contains("CHESTPLATE") ||
             mat.name().contains("LEGGINGS") || mat.name().contains("BOOTS")) && enchProtection != null) {
            applicable.add(enchProtection);
        }
        if (enchUnbreaking != null) {
            applicable.add(enchUnbreaking);
        }

        if (applicable.isEmpty()) return;

        int maxLevel = rarity == Rarity.LEGENDARY ? 4 : (rarity == Rarity.EPIC ? 3 : 2);
        Enchantment ench = applicable.get(random.nextInt(applicable.size()));
        int level = 1 + random.nextInt(Math.min(maxLevel, ench.getMaxLevel()));
        meta.addEnchant(ench, level, true);
    }

    private String formatMobName(EntityType type) {
        String name = type.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(" ");
        }
        return result.toString().trim();
    }

    private void initializeMobLootTables() {
        // Zombie loot
        mobLootTables.put(EntityType.ZOMBIE, Arrays.asList(
            new LootEntry(Material.IRON_NUGGET, 1, 3, "Corroded Coin", null),
            new LootEntry(Material.IRON_SWORD, 1, 1, "Rusty Blade", "Pried from undead fingers."),
            new LootEntry(Material.LEATHER_CHESTPLATE, 1, 1, "Tattered Armor", null)
        ));

        // Skeleton loot
        mobLootTables.put(EntityType.SKELETON, Arrays.asList(
            new LootEntry(Material.BONE, 1, 3, "Ancient Bone", null),
            new LootEntry(Material.BOW, 1, 1, "Skeletal Bow", "Still strung with sinew."),
            new LootEntry(Material.ARROW, 5, 15, "Cursed Arrows", null)
        ));

        // Creeper loot
        mobLootTables.put(EntityType.CREEPER, Arrays.asList(
            new LootEntry(Material.GUNPOWDER, 2, 5, "Volatile Powder", "Handle with care."),
            new LootEntry(Material.TNT, 1, 1, "Unstable Core", "The creeper's essence.")
        ));

        // Spider loot
        mobLootTables.put(EntityType.SPIDER, Arrays.asList(
            new LootEntry(Material.STRING, 2, 5, "Spider Silk", null),
            new LootEntry(Material.SPIDER_EYE, 1, 2, "Venomous Eye", "Still twitching."),
            new LootEntry(Material.FERMENTED_SPIDER_EYE, 1, 1, "Potent Venom", null)
        ));

        // Enderman loot
        mobLootTables.put(EntityType.ENDERMAN, Arrays.asList(
            new LootEntry(Material.ENDER_PEARL, 1, 3, "Void Pearl", "Hums with energy."),
            new LootEntry(Material.CHORUS_FRUIT, 1, 2, "Ender Fruit", null),
            new LootEntry(Material.OBSIDIAN, 1, 2, "Void Stone", "Cold to the touch.")
        ));

        // Witch loot
        mobLootTables.put(EntityType.WITCH, Arrays.asList(
            new LootEntry(Material.GLASS_BOTTLE, 2, 4, "Witch's Vial", null),
            new LootEntry(Material.GLOWSTONE_DUST, 1, 3, "Arcane Dust", null),
            new LootEntry(Material.REDSTONE, 2, 5, "Magical Catalyst", null),
            new LootEntry(Material.SUGAR, 1, 3, "Enchanted Sugar", null)
        ));

        // Blaze loot
        mobLootTables.put(EntityType.BLAZE, Arrays.asList(
            new LootEntry(Material.BLAZE_ROD, 1, 2, "Infernal Rod", "Burns eternally."),
            new LootEntry(Material.FIRE_CHARGE, 1, 3, "Blaze Core", null),
            new LootEntry(Material.MAGMA_CREAM, 1, 2, "Molten Essence", null)
        ));

        // Wither Skeleton loot
        mobLootTables.put(EntityType.WITHER_SKELETON, Arrays.asList(
            new LootEntry(Material.COAL, 2, 5, "Charred Remains", null),
            new LootEntry(Material.STONE_SWORD, 1, 1, "Wither Blade", "Drains life force."),
            new LootEntry(Material.WITHER_ROSE, 1, 1, "Death's Flower", "Do not smell.")
        ));

        // Piglin loot
        mobLootTables.put(EntityType.PIGLIN, Arrays.asList(
            new LootEntry(Material.GOLD_NUGGET, 3, 8, "Piglin Gold", null),
            new LootEntry(Material.GOLDEN_SWORD, 1, 1, "Piglin Blade", "Crude but effective."),
            new LootEntry(Material.GOLD_INGOT, 1, 2, "Bartered Gold", null)
        ));

        // Phantom loot
        mobLootTables.put(EntityType.PHANTOM, Arrays.asList(
            new LootEntry(Material.PHANTOM_MEMBRANE, 1, 2, "Nightmare Wing", "From the realm of dreams."),
            new LootEntry(Material.FEATHER, 2, 4, "Spectral Feather", null)
        ));

        // Drowned loot
        mobLootTables.put(EntityType.DROWNED, Arrays.asList(
            new LootEntry(Material.KELP, 2, 5, "Drowned Seaweed", null),
            new LootEntry(Material.NAUTILUS_SHELL, 1, 1, "Ocean Relic", "Prized by the deep."),
            new LootEntry(Material.TRIDENT, 1, 1, "Corroded Trident", "Still sharp.")
        ));

        // Pillager loot
        mobLootTables.put(EntityType.PILLAGER, Arrays.asList(
            new LootEntry(Material.CROSSBOW, 1, 1, "Raider's Crossbow", null),
            new LootEntry(Material.EMERALD, 1, 3, "Pillaged Emerald", "Stolen goods."),
            new LootEntry(Material.ARROW, 5, 15, "Pillager Bolts", null)
        ));

        // Vindicator loot
        mobLootTables.put(EntityType.VINDICATOR, Arrays.asList(
            new LootEntry(Material.IRON_AXE, 1, 1, "Vindicator's Axe", "Johnny was here."),
            new LootEntry(Material.EMERALD, 2, 5, "Raid Bounty", null)
        ));

        // Evoker loot
        mobLootTables.put(EntityType.EVOKER, Arrays.asList(
            new LootEntry(Material.TOTEM_OF_UNDYING, 1, 1, "Evoker's Totem", "Defies death itself."),
            new LootEntry(Material.BOOK, 1, 1, "Dark Grimoire", "Filled with forbidden spells."),
            new LootEntry(Material.EMERALD, 3, 7, "Illager Treasure", null)
        ));

        // Ravager loot
        mobLootTables.put(EntityType.RAVAGER, Arrays.asList(
            new LootEntry(Material.SADDLE, 1, 1, "Beast Saddle", "For the brave."),
            new LootEntry(Material.LEATHER, 3, 6, "Ravager Hide", "Tough as iron."),
            new LootEntry(Material.IRON_INGOT, 2, 4, "Ravager Plating", null)
        ));

        // Warden loot (rare!)
        mobLootTables.put(EntityType.WARDEN, Arrays.asList(
            new LootEntry(Material.ECHO_SHARD, 2, 4, "Warden's Echo", "Resonates with darkness."),
            new LootEntry(Material.SCULK_CATALYST, 1, 1, "Heart of the Deep", "Still pulsing."),
            new LootEntry(Material.NETHERITE_INGOT, 1, 1, "Abyssal Metal", "Forged in darkness.")
        ));
    }

    private enum Rarity {
        COMMON(ChatColor.WHITE),
        UNCOMMON(ChatColor.GREEN),
        RARE(ChatColor.BLUE),
        EPIC(ChatColor.DARK_PURPLE),
        LEGENDARY(ChatColor.GOLD);

        public final ChatColor color;
        Rarity(ChatColor color) { this.color = color; }
    }

    private static class LootEntry {
        final Material material;
        final int minAmount;
        final int maxAmount;
        final String name;
        final String lore;

        LootEntry(Material material, int minAmount, int maxAmount, String name, String lore) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.name = name;
            this.lore = lore;
        }
    }
}
