package com.aicraft.npcs.loot;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Manages loot drops from NPCs
 * Simple implementation with complex probability calculations
 */
public class NPCLootManager {

    private final AICompanions plugin;
    private final Random random = new Random();

    // Rarity weights (must sum to 100)
    private static final int COMMON_WEIGHT = 60;
    private static final int UNCOMMON_WEIGHT = 25;
    private static final int RARE_WEIGHT = 10;
    private static final int EPIC_WEIGHT = 4;
    private static final int LEGENDARY_WEIGHT = 1;

    // Faction loot tables: faction -> rarity -> list of possible items
    private final Map<String, Map<Rarity, List<LootEntry>>> lootTables = new HashMap<>();

    // Unique item name components for procedural generation
    private static final String[] PREFIXES = {
        "Ancient", "Cursed", "Blessed", "Forgotten", "Shadowy", "Radiant",
        "Bloodstained", "Ethereal", "Corrupted", "Sacred", "Voidtouched"
    };
    private static final String[] SUFFIXES = {
        "of the Fallen", "of Eternal Night", "of the Lost Souls", "of Reckoning",
        "of the Depths", "of Whispers", "of the Covenant", "of Geodjian"
    };

    public NPCLootManager(AICompanions plugin) {
        this.plugin = plugin;
        initializeLootTables();
    }

    /**
     * Generate loot drops for a killed NPC
     */
    public List<ItemStack> generateLoot(AINpc npc, Player killer) {
        List<ItemStack> drops = new ArrayList<>();

        // Check if loot is enabled
        if (!plugin.getConfig().getBoolean("loot.enabled", true)) {
            return drops;
        }

        // Base drop chance from config
        double baseDropChance = plugin.getConfig().getDouble("loot.base-drop-chance", 0.4);

        // Calculate final drop chance with modifiers
        double dropChance = calculateDropChance(npc, killer, baseDropChance);

        // Roll for drop
        if (random.nextDouble() > dropChance) {
            return drops; // No drop this time
        }

        // Determine rarity
        Rarity rarity = rollRarity(npc);

        // Get faction-specific loot table
        String faction = npc.getFaction() != null ? npc.getFaction().toLowerCase() : "wanderers";
        Map<Rarity, List<LootEntry>> factionLoot = lootTables.getOrDefault(faction, lootTables.get("wanderers"));

        // Get items for this rarity (fallback to lower rarity if empty)
        List<LootEntry> possibleLoot = factionLoot.get(rarity);
        if (possibleLoot == null || possibleLoot.isEmpty()) {
            possibleLoot = factionLoot.get(Rarity.COMMON);
        }

        if (possibleLoot != null && !possibleLoot.isEmpty()) {
            // Pick random item from loot table
            LootEntry entry = possibleLoot.get(random.nextInt(possibleLoot.size()));
            ItemStack item = createLootItem(entry, rarity, npc);
            drops.add(item);

            // Chance for bonus drops at higher rarities
            if (rarity.ordinal() >= Rarity.RARE.ordinal() && random.nextDouble() < 0.3) {
                // Bonus common/uncommon drop
                Rarity bonusRarity = random.nextBoolean() ? Rarity.COMMON : Rarity.UNCOMMON;
                List<LootEntry> bonusLoot = factionLoot.get(bonusRarity);
                if (bonusLoot != null && !bonusLoot.isEmpty()) {
                    LootEntry bonusEntry = bonusLoot.get(random.nextInt(bonusLoot.size()));
                    drops.add(createLootItem(bonusEntry, bonusRarity, npc));
                }
            }
        }

        // Always drop some gold/emeralds from merchants
        if (faction.equals("merchants")) {
            int amount = 1 + random.nextInt(3) + rarity.ordinal();
            drops.add(new ItemStack(Material.EMERALD, amount));
        }

        return drops;
    }

    /**
     * Calculate drop chance with various modifiers
     */
    private double calculateDropChance(AINpc npc, Player killer, double baseChance) {
        double chance = baseChance;

        // Hostile NPCs have higher drop rates
        if (npc.isHostile()) {
            chance *= 1.5;
        }

        // Faction modifiers
        String faction = npc.getFaction() != null ? npc.getFaction().toLowerCase() : "";
        switch (faction) {
            case "bandits" -> chance *= 1.3;  // Bandits carry stolen goods
            case "merchants" -> chance *= 1.8; // Merchants have inventory
            case "cultists" -> chance *= 1.2;  // Cultists have ritual items
            case "guards" -> chance *= 0.8;    // Guards have standard gear
            case "villagers" -> chance *= 0.5; // Villagers have little of value
        }

        // Looting enchantment bonus
        if (killer != null && killer.getInventory().getItemInMainHand() != null) {
            int lootingLevel = killer.getInventory().getItemInMainHand()
                .getEnchantmentLevel(Enchantment.LOOTING);
            chance += lootingLevel * 0.1; // +10% per looting level
        }

        // Cap at 95%
        return Math.min(chance, 0.95);
    }

    /**
     * Roll for item rarity using weighted random
     */
    private Rarity rollRarity(AINpc npc) {
        // Base weights
        int common = COMMON_WEIGHT;
        int uncommon = UNCOMMON_WEIGHT;
        int rare = RARE_WEIGHT;
        int epic = EPIC_WEIGHT;
        int legendary = LEGENDARY_WEIGHT;

        // Hostile NPCs have better rarity chances
        if (npc.isHostile()) {
            common -= 10;
            uncommon += 5;
            rare += 3;
            epic += 1;
            legendary += 1;
        }

        // Faction rarity modifiers
        String faction = npc.getFaction() != null ? npc.getFaction().toLowerCase() : "";
        switch (faction) {
            case "cultists" -> {
                // Cultists have higher chance of rare/epic (ritual items)
                common -= 15;
                rare += 8;
                epic += 5;
                legendary += 2;
            }
            case "merchants" -> {
                // Merchants have diverse inventory
                common -= 10;
                uncommon += 10;
            }
            case "bandits" -> {
                // Bandits have stolen rare goods occasionally
                common -= 5;
                rare += 5;
            }
        }

        // Weighted random selection
        int roll = random.nextInt(common + uncommon + rare + epic + legendary);

        if (roll < common) return Rarity.COMMON;
        roll -= common;
        if (roll < uncommon) return Rarity.UNCOMMON;
        roll -= uncommon;
        if (roll < rare) return Rarity.RARE;
        roll -= rare;
        if (roll < epic) return Rarity.EPIC;
        return Rarity.LEGENDARY;
    }

    /**
     * Create the actual ItemStack with proper naming and lore
     */
    private ItemStack createLootItem(LootEntry entry, Rarity rarity, AINpc npc) {
        // Calculate stack size
        int amount = entry.minAmount;
        if (entry.maxAmount > entry.minAmount) {
            amount += random.nextInt(entry.maxAmount - entry.minAmount + 1);
        }

        ItemStack item = new ItemStack(entry.material, amount);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Generate name for rare+ items
            if (rarity.ordinal() >= Rarity.RARE.ordinal() && entry.canBeUnique) {
                String uniqueName = generateUniqueName(entry, rarity, npc);
                meta.setDisplayName(rarity.color + uniqueName);
            } else if (entry.customName != null) {
                meta.setDisplayName(rarity.color + entry.customName);
            }

            // Generate lore
            List<String> lore = new ArrayList<>();

            // Rarity tag
            lore.add(rarity.color + rarity.name());

            // Origin lore
            if (rarity.ordinal() >= Rarity.UNCOMMON.ordinal()) {
                lore.add(ChatColor.GRAY + "Obtained from: " + ChatColor.WHITE + npc.getName());
                if (npc.getFaction() != null) {
                    lore.add(ChatColor.GRAY + "Faction: " + ChatColor.WHITE + npc.getFaction());
                }
            }

            // Special lore for legendary items
            if (rarity == Rarity.LEGENDARY) {
                lore.add("");
                lore.add(ChatColor.LIGHT_PURPLE + "" + ChatColor.ITALIC + "A truly exceptional find...");
            }

            // Flavor text for epic+
            if (rarity.ordinal() >= Rarity.EPIC.ordinal() && entry.loreText != null) {
                lore.add("");
                lore.add(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + entry.loreText);
            }

            meta.setLore(lore);

            // Add enchantments for rare+ weapons/armor
            if (rarity.ordinal() >= Rarity.RARE.ordinal()) {
                addRarityEnchantments(item, meta, rarity);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Generate a unique procedural name for rare items
     */
    private String generateUniqueName(LootEntry entry, Rarity rarity, AINpc npc) {
        StringBuilder name = new StringBuilder();

        // Chance for prefix increases with rarity
        if (random.nextDouble() < 0.3 + (rarity.ordinal() * 0.15)) {
            name.append(PREFIXES[random.nextInt(PREFIXES.length)]).append(" ");
        }

        // Base name
        name.append(entry.customName != null ? entry.customName : formatMaterialName(entry.material));

        // Chance for suffix
        if (random.nextDouble() < 0.2 + (rarity.ordinal() * 0.1)) {
            name.append(" ").append(SUFFIXES[random.nextInt(SUFFIXES.length)]);
        }

        // Legendary items might reference the NPC
        if (rarity == Rarity.LEGENDARY && random.nextDouble() < 0.5) {
            String[] parts = npc.getName().split(" ");
            name.append(" of ").append(parts[parts.length - 1]);
        }

        return name.toString();
    }

    /**
     * Add enchantments based on rarity
     */
    private void addRarityEnchantments(ItemStack item, ItemMeta meta, Rarity rarity) {
        Material mat = item.getType();

        // Determine applicable enchantments based on item type
        List<Enchantment> applicable = new ArrayList<>();

        if (mat.name().contains("SWORD")) {
            applicable.addAll(Arrays.asList(Enchantment.SHARPNESS, Enchantment.FIRE_ASPECT,
                Enchantment.LOOTING, Enchantment.KNOCKBACK));
        } else if (mat.name().contains("BOW")) {
            applicable.addAll(Arrays.asList(Enchantment.POWER, Enchantment.PUNCH,
                Enchantment.FLAME, Enchantment.INFINITY));
        } else if (mat.name().contains("HELMET") || mat.name().contains("CHESTPLATE") ||
                   mat.name().contains("LEGGINGS") || mat.name().contains("BOOTS")) {
            applicable.addAll(Arrays.asList(Enchantment.PROTECTION, Enchantment.UNBREAKING,
                Enchantment.THORNS));
            if (mat.name().contains("BOOTS")) {
                applicable.add(Enchantment.FEATHER_FALLING);
            }
        } else if (mat.name().contains("PICKAXE") || mat.name().contains("AXE") ||
                   mat.name().contains("SHOVEL")) {
            applicable.addAll(Arrays.asList(Enchantment.EFFICIENCY, Enchantment.UNBREAKING,
                Enchantment.FORTUNE));
        }

        if (applicable.isEmpty()) return;

        // Number of enchantments based on rarity
        int enchantCount = switch (rarity) {
            case RARE -> 1;
            case EPIC -> 1 + random.nextInt(2);
            case LEGENDARY -> 2 + random.nextInt(2);
            default -> 0;
        };

        // Enchantment level based on rarity
        int maxLevel = switch (rarity) {
            case RARE -> 2;
            case EPIC -> 3;
            case LEGENDARY -> 5;
            default -> 1;
        };

        Collections.shuffle(applicable);
        for (int i = 0; i < Math.min(enchantCount, applicable.size()); i++) {
            Enchantment ench = applicable.get(i);
            int level = 1 + random.nextInt(Math.min(maxLevel, ench.getMaxLevel()));
            meta.addEnchant(ench, level, true);
        }
    }

    /**
     * Format material name for display
     */
    private String formatMaterialName(Material mat) {
        String name = mat.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(" ");
        }
        return result.toString().trim();
    }

    /**
     * Initialize faction-specific loot tables
     */
    private void initializeLootTables() {
        // Bandits - weapons, stolen goods
        Map<Rarity, List<LootEntry>> banditLoot = new EnumMap<>(Rarity.class);
        banditLoot.put(Rarity.COMMON, Arrays.asList(
            new LootEntry(Material.IRON_NUGGET, 1, 5, "Tarnished Coin", false, null),
            new LootEntry(Material.ROTTEN_FLESH, 1, 3, null, false, null),
            new LootEntry(Material.BONE, 1, 2, null, false, null),
            new LootEntry(Material.ARROW, 3, 12, null, false, null)
        ));
        banditLoot.put(Rarity.UNCOMMON, Arrays.asList(
            new LootEntry(Material.IRON_SWORD, 1, 1, "Bandit's Blade", true, null),
            new LootEntry(Material.IRON_INGOT, 1, 3, null, false, null),
            new LootEntry(Material.GOLD_INGOT, 1, 2, "Stolen Gold", false, null),
            new LootEntry(Material.CROSSBOW, 1, 1, "Highwayman's Crossbow", true, null)
        ));
        banditLoot.put(Rarity.RARE, Arrays.asList(
            new LootEntry(Material.DIAMOND, 1, 2, "Stolen Gem", true, "Pried from cold, dead hands."),
            new LootEntry(Material.IRON_SWORD, 1, 1, "Cutthroat's Edge", true, "Still warm with blood."),
            new LootEntry(Material.CHAINMAIL_CHESTPLATE, 1, 1, "Brigand's Mail", true, null)
        ));
        banditLoot.put(Rarity.EPIC, Arrays.asList(
            new LootEntry(Material.DIAMOND_SWORD, 1, 1, "Marauder's Fang", true, "Terror of the roads."),
            new LootEntry(Material.GOLDEN_APPLE, 1, 1, "Stolen Treasure", false, null)
        ));
        banditLoot.put(Rarity.LEGENDARY, Arrays.asList(
            new LootEntry(Material.NETHERITE_SWORD, 1, 1, "Dread Reaver", true, "A weapon of infamy.")
        ));
        lootTables.put("bandits", banditLoot);

        // Cultists - ritual items, mysterious artifacts
        Map<Rarity, List<LootEntry>> cultistLoot = new EnumMap<>(Rarity.class);
        cultistLoot.put(Rarity.COMMON, Arrays.asList(
            new LootEntry(Material.BONE, 1, 3, "Ritual Bone", false, null),
            new LootEntry(Material.CANDLE, 1, 4, "Black Candle", false, null),
            new LootEntry(Material.INK_SAC, 1, 2, "Dark Ink", false, null),
            new LootEntry(Material.PAPER, 1, 3, "Strange Writings", false, null)
        ));
        cultistLoot.put(Rarity.UNCOMMON, Arrays.asList(
            new LootEntry(Material.BOOK, 1, 1, "Forbidden Tome", true, null),
            new LootEntry(Material.ENDER_PEARL, 1, 2, "Void Essence", false, null),
            new LootEntry(Material.FERMENTED_SPIDER_EYE, 1, 3, "Ritual Component", false, null)
        ));
        cultistLoot.put(Rarity.RARE, Arrays.asList(
            new LootEntry(Material.ENCHANTED_BOOK, 1, 1, "Grimoire Page", true, "Whispers dark secrets."),
            new LootEntry(Material.ECHO_SHARD, 1, 2, "Shard of the Void", true, "Hums with dark energy."),
            new LootEntry(Material.WITHER_ROSE, 1, 1, "Geodjian's Bloom", false, null)
        ));
        cultistLoot.put(Rarity.EPIC, Arrays.asList(
            new LootEntry(Material.NETHER_STAR, 1, 1, "Star of Geodjian", true, "The All-Seeing watches."),
            new LootEntry(Material.TOTEM_OF_UNDYING, 1, 1, "Idol of False Life", true, "Geodjian denies death.")
        ));
        cultistLoot.put(Rarity.LEGENDARY, Arrays.asList(
            new LootEntry(Material.DRAGON_EGG, 1, 1, "Eye of Geodjian", true, "It sees all.")
        ));
        lootTables.put("cultists", cultistLoot);

        // Merchants - trade goods, valuables
        Map<Rarity, List<LootEntry>> merchantLoot = new EnumMap<>(Rarity.class);
        merchantLoot.put(Rarity.COMMON, Arrays.asList(
            new LootEntry(Material.EMERALD, 1, 3, null, false, null),
            new LootEntry(Material.WHEAT, 4, 12, null, false, null),
            new LootEntry(Material.LEATHER, 2, 5, null, false, null),
            new LootEntry(Material.STRING, 2, 6, null, false, null)
        ));
        merchantLoot.put(Rarity.UNCOMMON, Arrays.asList(
            new LootEntry(Material.EMERALD, 3, 8, null, false, null),
            new LootEntry(Material.GOLD_INGOT, 2, 5, "Trade Gold", false, null),
            new LootEntry(Material.COMPASS, 1, 1, "Trader's Compass", true, null),
            new LootEntry(Material.MAP, 1, 1, "Trade Route Map", true, null)
        ));
        merchantLoot.put(Rarity.RARE, Arrays.asList(
            new LootEntry(Material.DIAMOND, 1, 3, "Gem Stock", false, null),
            new LootEntry(Material.GOLDEN_APPLE, 1, 2, "Premium Goods", false, null),
            new LootEntry(Material.SADDLE, 1, 1, "Fine Saddle", true, null)
        ));
        merchantLoot.put(Rarity.EPIC, Arrays.asList(
            new LootEntry(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, "Merchant's Pride", true, "Worth a fortune."),
            new LootEntry(Material.ELYTRA, 1, 1, "Exotic Wings", true, "From distant lands.")
        ));
        merchantLoot.put(Rarity.LEGENDARY, Arrays.asList(
            new LootEntry(Material.HEART_OF_THE_SEA, 1, 1, "Heart of Commerce", true, "Priceless artifact.")
        ));
        lootTables.put("merchants", merchantLoot);

        // Guards - armor, weapons, standard issue
        Map<Rarity, List<LootEntry>> guardLoot = new EnumMap<>(Rarity.class);
        guardLoot.put(Rarity.COMMON, Arrays.asList(
            new LootEntry(Material.IRON_NUGGET, 2, 6, null, false, null),
            new LootEntry(Material.BREAD, 1, 3, "Rations", false, null),
            new LootEntry(Material.ARROW, 5, 15, null, false, null)
        ));
        guardLoot.put(Rarity.UNCOMMON, Arrays.asList(
            new LootEntry(Material.IRON_SWORD, 1, 1, "Guard's Sword", true, null),
            new LootEntry(Material.IRON_HELMET, 1, 1, "Guard's Helm", true, null),
            new LootEntry(Material.SHIELD, 1, 1, "Tower Shield", true, null)
        ));
        guardLoot.put(Rarity.RARE, Arrays.asList(
            new LootEntry(Material.IRON_CHESTPLATE, 1, 1, "Guard Captain's Plate", true, "Worn with honor."),
            new LootEntry(Material.DIAMOND_SWORD, 1, 1, "Officer's Blade", true, "Symbol of rank.")
        ));
        guardLoot.put(Rarity.EPIC, Arrays.asList(
            new LootEntry(Material.DIAMOND_CHESTPLATE, 1, 1, "Elite Guard Armor", true, "For the finest warriors."),
            new LootEntry(Material.DIAMOND_SWORD, 1, 1, "Captain's Vengeance", true, "Justice delivered.")
        ));
        guardLoot.put(Rarity.LEGENDARY, Arrays.asList(
            new LootEntry(Material.NETHERITE_CHESTPLATE, 1, 1, "Warden's Aegis", true, "Protector of the realm.")
        ));
        lootTables.put("guards", guardLoot);

        // Villagers - simple goods, food
        Map<Rarity, List<LootEntry>> villagerLoot = new EnumMap<>(Rarity.class);
        villagerLoot.put(Rarity.COMMON, Arrays.asList(
            new LootEntry(Material.WHEAT, 1, 4, null, false, null),
            new LootEntry(Material.POTATO, 1, 4, null, false, null),
            new LootEntry(Material.CARROT, 1, 4, null, false, null),
            new LootEntry(Material.BREAD, 1, 2, null, false, null)
        ));
        villagerLoot.put(Rarity.UNCOMMON, Arrays.asList(
            new LootEntry(Material.IRON_INGOT, 1, 2, null, false, null),
            new LootEntry(Material.BOOK, 1, 1, "Journal", false, null),
            new LootEntry(Material.GOLDEN_CARROT, 1, 2, null, false, null)
        ));
        villagerLoot.put(Rarity.RARE, Arrays.asList(
            new LootEntry(Material.EMERALD, 2, 5, "Life Savings", false, null),
            new LootEntry(Material.DIAMOND, 1, 1, "Family Heirloom", true, "Passed down for generations.")
        ));
        villagerLoot.put(Rarity.EPIC, Arrays.asList(
            new LootEntry(Material.ENCHANTED_BOOK, 1, 1, "Ancient Knowledge", true, "Wisdom of the elders.")
        ));
        villagerLoot.put(Rarity.LEGENDARY, Arrays.asList(
            new LootEntry(Material.TOTEM_OF_UNDYING, 1, 1, "Village Guardian", true, "Protected the village for centuries.")
        ));
        lootTables.put("villagers", villagerLoot);

        // Wanderers - misc adventuring gear (default)
        Map<Rarity, List<LootEntry>> wandererLoot = new EnumMap<>(Rarity.class);
        wandererLoot.put(Rarity.COMMON, Arrays.asList(
            new LootEntry(Material.STICK, 2, 5, null, false, null),
            new LootEntry(Material.LEATHER, 1, 3, null, false, null),
            new LootEntry(Material.COAL, 1, 4, null, false, null),
            new LootEntry(Material.COOKED_BEEF, 1, 3, "Trail Rations", false, null)
        ));
        wandererLoot.put(Rarity.UNCOMMON, Arrays.asList(
            new LootEntry(Material.IRON_INGOT, 1, 3, null, false, null),
            new LootEntry(Material.COMPASS, 1, 1, "Traveler's Compass", true, null),
            new LootEntry(Material.LEATHER_BOOTS, 1, 1, "Worn Boots", true, null)
        ));
        wandererLoot.put(Rarity.RARE, Arrays.asList(
            new LootEntry(Material.SPYGLASS, 1, 1, "Far-Seer", true, "Reveals distant secrets."),
            new LootEntry(Material.MAP, 1, 1, "Treasure Map", true, "X marks the spot..."),
            new LootEntry(Material.DIAMOND, 1, 2, "Hidden Stash", false, null)
        ));
        wandererLoot.put(Rarity.EPIC, Arrays.asList(
            new LootEntry(Material.ELYTRA, 1, 1, "Wings of the Wanderer", true, "Freedom incarnate."),
            new LootEntry(Material.ENCHANTED_BOOK, 1, 1, "Lost Lore", true, "Knowledge from forgotten lands.")
        ));
        wandererLoot.put(Rarity.LEGENDARY, Arrays.asList(
            new LootEntry(Material.NETHER_STAR, 1, 1, "Guiding Light", true, "Never lost, never alone.")
        ));
        lootTables.put("wanderers", wandererLoot);
    }

    /**
     * Rarity tiers with display colors
     */
    public enum Rarity {
        COMMON(ChatColor.WHITE),
        UNCOMMON(ChatColor.GREEN),
        RARE(ChatColor.BLUE),
        EPIC(ChatColor.DARK_PURPLE),
        LEGENDARY(ChatColor.GOLD);

        public final ChatColor color;

        Rarity(ChatColor color) {
            this.color = color;
        }
    }

    /**
     * Single loot table entry
     */
    private static class LootEntry {
        final Material material;
        final int minAmount;
        final int maxAmount;
        final String customName;
        final boolean canBeUnique;
        final String loreText;

        LootEntry(Material material, int minAmount, int maxAmount, String customName,
                  boolean canBeUnique, String loreText) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.customName = customName;
            this.canBeUnique = canBeUnique;
            this.loreText = loreText;
        }
    }
}
