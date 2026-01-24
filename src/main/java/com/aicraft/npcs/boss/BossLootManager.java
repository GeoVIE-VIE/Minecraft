package com.aicraft.npcs.boss;

import com.aicraft.AICompanions;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages legendary loot drops from boss NPCs
 * These items have special abilities tracked via PersistentDataContainer
 */
public class BossLootManager {

    private final AICompanions plugin;
    private final Random random = new Random();

    // Namespaced keys for item abilities
    public final NamespacedKey ABILITY_KEY;
    public final NamespacedKey BOSS_ITEM_KEY;

    // Legendary item definitions
    private final Map<String, LegendaryItemDef> legendaryItems = new HashMap<>();

    // Enchantments
    private Enchantment enchSharpness;
    private Enchantment enchUnbreaking;
    private Enchantment enchEfficiency;
    private Enchantment enchFortune;
    private Enchantment enchFireAspect;
    private Enchantment enchKnockback;
    private Enchantment enchProtection;
    private Enchantment enchThorns;

    public BossLootManager(AICompanions plugin) {
        this.plugin = plugin;
        this.ABILITY_KEY = new NamespacedKey(plugin, "legendary_ability");
        this.BOSS_ITEM_KEY = new NamespacedKey(plugin, "boss_item");
        initializeEnchantments();
        initializeLegendaryItems();
    }

    private void initializeEnchantments() {
        try {
            enchSharpness = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
            enchUnbreaking = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
            enchEfficiency = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
            enchFortune = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fortune"));
            enchFireAspect = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("fire_aspect"));
            enchKnockback = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("knockback"));
            enchProtection = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("protection"));
            enchThorns = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("thorns"));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load enchantments: " + e.getMessage());
        }
    }

    /**
     * Initialize all legendary item definitions
     */
    private void initializeLegendaryItems() {

        // ============================================
        // EL BRONCO 956 DROPS
        // ============================================

        legendaryItems.put("bronco_machete", new LegendaryItemDef(
            "bronco_machete",
            Material.NETHERITE_SWORD,
            ChatColor.DARK_RED + "" + ChatColor.BOLD + "El Bronco's Machete",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "The blade that carved a legend through",
                ChatColor.GRAY + "the brush country of South Texas.",
                "",
                ChatColor.DARK_RED + "Ability: " + ChatColor.WHITE + "Valley Justice",
                ChatColor.GRAY + "Attacks inflict Wither on enemies",
                "",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "\"Orale, this blade has tasted",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "more blood than a carniceria.\""
            ),
            "valley_justice",
            true
        ));

        legendaryItems.put("your_mothers_panties", new LegendaryItemDef(
            "your_mothers_panties",
            Material.LEATHER_HELMET,
            ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Your Mother's Panties",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "Don't ask how these were obtained.",
                ChatColor.GRAY + "Don't ask why they're a helmet.",
                ChatColor.GRAY + "Just... don't ask.",
                "",
                ChatColor.LIGHT_PURPLE + "Ability: " + ChatColor.WHITE + "Emotional Damage",
                ChatColor.GRAY + "Enemies who see you lose the will to fight",
                ChatColor.GRAY + "(Inflicts Weakness on nearby mobs)",
                "",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "\"Tu madre was here last night, guey\"",
                ChatColor.RED + "" + ChatColor.ITALIC + "- El Bronco 956"
            ),
            "emotional_damage",
            false
        ));

        legendaryItems.put("valley_gold", new LegendaryItemDef(
            "valley_gold",
            Material.GOLD_BLOCK,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Valley Gold Reserve",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "Pure gold from El Bronco's personal",
                ChatColor.GRAY + "stash, smuggled across generations.",
                "",
                ChatColor.YELLOW + "Worth more than your life, primo."
            ),
            null,
            false
        ));

        // ============================================
        // LA LLORONA DROPS
        // ============================================

        legendaryItems.put("llorona_tears", new LegendaryItemDef(
            "llorona_tears",
            Material.HEART_OF_THE_SEA,
            ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Tears of La Llorona",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "Crystallized tears from centuries",
                ChatColor.GRAY + "of mourning for her lost children.",
                "",
                ChatColor.DARK_AQUA + "Ability: " + ChatColor.WHITE + "Mother's Grief",
                ChatColor.GRAY + "Right-click to unleash a wave of sorrow",
                ChatColor.GRAY + "(Area Slowness + Blindness)",
                "",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "\"Mis hijos... donde estan mis hijos...\""
            ),
            "mothers_grief",
            true
        ));

        legendaryItems.put("soul_dragger", new LegendaryItemDef(
            "soul_dragger",
            Material.TRIDENT,
            ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Soul Dragger",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "A spectral trident that pulls souls",
                ChatColor.GRAY + "down to the depths of the Rio Grande.",
                "",
                ChatColor.DARK_AQUA + "Ability: " + ChatColor.WHITE + "Undertow",
                ChatColor.GRAY + "Thrown trident pulls enemies toward you",
                "",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "\"Come to the water, mijo...\""
            ),
            "undertow",
            true
        ));

        legendaryItems.put("cursed_wedding_ring", new LegendaryItemDef(
            "cursed_wedding_ring",
            Material.GOLD_NUGGET,
            ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "La Llorona's Wedding Ring",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "The ring from her unfaithful husband.",
                ChatColor.GRAY + "Cursed with eternal torment.",
                "",
                ChatColor.RED + "CURSED: " + ChatColor.GRAY + "Cannot be dropped normally"
            ),
            "cursed_bind",
            false
        ));

        // ============================================
        // EL CHUPACABRA DROPS
        // ============================================

        legendaryItems.put("chupacabra_fang", new LegendaryItemDef(
            "chupacabra_fang",
            Material.NETHERITE_AXE,
            ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Chupacabra Fang",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "A massive fang ripped from the beast.",
                ChatColor.GRAY + "Still drips with venom.",
                "",
                ChatColor.DARK_GREEN + "Ability: " + ChatColor.WHITE + "Bloodthirst",
                ChatColor.GRAY + "Attacks heal you for damage dealt",
                "",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "*SCREEEEECH*"
            ),
            "bloodthirst",
            true
        ));

        legendaryItems.put("bloodsucker_pickaxe", new LegendaryItemDef(
            "bloodsucker_pickaxe",
            Material.NETHERITE_PICKAXE,
            ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "Bloodsucker's Pick",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "Crafted from the claws of El Chupacabra.",
                ChatColor.GRAY + "Tears through stone like flesh.",
                "",
                ChatColor.DARK_GREEN + "Ability: " + ChatColor.WHITE + "Goat's Curse",
                ChatColor.GREEN + "Mines in a 3x3 area instantly!",
                "",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "\"They heard the goats scream",
                ChatColor.DARK_PURPLE + "" + ChatColor.ITALIC + "from three ranchos away.\""
            ),
            "multi_mine",
            true
        ));

        legendaryItems.put("goat_soul_essence", new LegendaryItemDef(
            "goat_soul_essence",
            Material.DRAGON_BREATH,
            ChatColor.GREEN + "" + ChatColor.BOLD + "Essence of a Thousand Goats",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "The collected life essence of every",
                ChatColor.GRAY + "goat El Chupacabra has ever drained.",
                "",
                ChatColor.GREEN + "Use: " + ChatColor.WHITE + "Grants 5 minutes of Regeneration III"
            ),
            "goat_power",
            false
        ));

        // ============================================
        // DON CUCO DROPS
        // ============================================

        legendaryItems.put("don_cuco_ledger", new LegendaryItemDef(
            "don_cuco_ledger",
            Material.WRITABLE_BOOK,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Don Cuco's Black Ledger",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "40 years of smuggling routes, contacts,",
                ChatColor.GRAY + "and secrets. Worth killing for.",
                "",
                ChatColor.GOLD + "Ability: " + ChatColor.WHITE + "Insider Knowledge",
                ChatColor.GRAY + "Right-click to reveal all nearby ores"
            ),
            "ore_reveal",
            true
        ));

        legendaryItems.put("smuggler_boots", new LegendaryItemDef(
            "smuggler_boots",
            Material.NETHERITE_BOOTS,
            ChatColor.GOLD + "" + ChatColor.BOLD + "Don Cuco's Lucky Boots",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "Blessed by his bruja grandmother.",
                ChatColor.GRAY + "Never been caught wearing these.",
                "",
                ChatColor.GOLD + "Ability: " + ChatColor.WHITE + "Federale's Bane",
                ChatColor.GRAY + "Permanent Speed II while worn",
                ChatColor.GRAY + "Sneaking makes you invisible"
            ),
            "smuggler_speed",
            true
        ));

        legendaryItems.put("briefcase_of_souls", new LegendaryItemDef(
            "briefcase_of_souls",
            Material.SHULKER_BOX,
            ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Don Cuco's Briefcase",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "What's in the briefcase? Money? Drugs?",
                ChatColor.GRAY + "Worse. The souls of his competitors.",
                "",
                ChatColor.DARK_RED + "Contains: " + ChatColor.GRAY + "Unspeakable horrors",
                ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + "(and some diamonds)"
            ),
            null,
            false
        ));

        // ============================================
        // EL DIABLITO DROPS
        // ============================================

        legendaryItems.put("diablito_dice", new LegendaryItemDef(
            "diablito_dice",
            Material.MAGMA_CREAM,
            ChatColor.RED + "" + ChatColor.BOLD + "El Diablito's Lucky Dice",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "Cursed dice that always roll in your",
                ChatColor.GRAY + "favor... at someone else's expense.",
                "",
                ChatColor.RED + "Ability: " + ChatColor.WHITE + "Chaos Roll",
                ChatColor.GRAY + "Right-click for a random effect!",
                ChatColor.GRAY + "(Could be very good or very bad)"
            ),
            "chaos_roll",
            true
        ));

        legendaryItems.put("chaos_sombrero", new LegendaryItemDef(
            "chaos_sombrero",
            Material.LEATHER_HELMET,
            ChatColor.RED + "" + ChatColor.BOLD + "Sombrero del Caos",
            Arrays.asList(
                ChatColor.GOLD + "" + ChatColor.BOLD + "LEGENDARY",
                "",
                ChatColor.GRAY + "El Diablito's signature hat.",
                ChatColor.GRAY + "Radiates pure chaotic energy.",
                "",
                ChatColor.RED + "Ability: " + ChatColor.WHITE + "Mischief Aura",
                ChatColor.GRAY + "Mobs randomly attack each other nearby"
            ),
            "mischief_aura",
            true
        ));
    }

    /**
     * Generate loot drops when a boss is killed
     */
    public List<ItemStack> generateBossLoot(BossNpc boss, Player killer) {
        List<ItemStack> drops = new ArrayList<>();

        // Always drop guaranteed items for this boss
        for (String itemId : boss.getDefinition().guaranteedDrops) {
            LegendaryItemDef def = legendaryItems.get(itemId);
            if (def != null) {
                drops.add(createLegendaryItem(def, boss, killer));
            }
        }

        // Bonus: XP and some extra valuables
        drops.add(new ItemStack(Material.EMERALD, 10 + random.nextInt(20)));
        drops.add(new ItemStack(Material.DIAMOND, 3 + random.nextInt(5)));

        // Chance for extra legendary from pool
        if (random.nextDouble() < 0.25) {
            List<String> allItems = new ArrayList<>(legendaryItems.keySet());
            String bonusItem = allItems.get(random.nextInt(allItems.size()));
            LegendaryItemDef def = legendaryItems.get(bonusItem);
            if (def != null) {
                drops.add(createLegendaryItem(def, boss, killer));
            }
        }

        return drops;
    }

    /**
     * Create a legendary item with proper metadata
     */
    public ItemStack createLegendaryItem(LegendaryItemDef def, BossNpc boss, Player killer) {
        ItemStack item = new ItemStack(def.material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(def.displayName);
            meta.setLore(def.lore);

            // Hide attributes for cleaner look
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Store ability ID in persistent data
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(BOSS_ITEM_KEY, PersistentDataType.STRING, def.id);
            if (def.abilityId != null) {
                pdc.set(ABILITY_KEY, PersistentDataType.STRING, def.abilityId);
            }

            // Add base enchantments
            if (def.addEnchants) {
                addLegendaryEnchantments(item, meta, def.material);
            }

            // Make unbreakable
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Add appropriate enchantments based on item type
     */
    private void addLegendaryEnchantments(ItemStack item, ItemMeta meta, Material material) {
        String name = material.name().toLowerCase();

        if (name.contains("sword") || name.contains("axe")) {
            if (enchSharpness != null) meta.addEnchant(enchSharpness, 7, true);
            if (enchFireAspect != null) meta.addEnchant(enchFireAspect, 2, true);
            if (enchKnockback != null) meta.addEnchant(enchKnockback, 2, true);
            if (enchUnbreaking != null) meta.addEnchant(enchUnbreaking, 5, true);
        } else if (name.contains("pickaxe")) {
            if (enchEfficiency != null) meta.addEnchant(enchEfficiency, 7, true);
            if (enchFortune != null) meta.addEnchant(enchFortune, 4, true);
            if (enchUnbreaking != null) meta.addEnchant(enchUnbreaking, 5, true);
        } else if (name.contains("helmet") || name.contains("chestplate") ||
                   name.contains("leggings") || name.contains("boots")) {
            if (enchProtection != null) meta.addEnchant(enchProtection, 5, true);
            if (enchUnbreaking != null) meta.addEnchant(enchUnbreaking, 5, true);
            if (enchThorns != null) meta.addEnchant(enchThorns, 3, true);
        } else if (name.contains("trident")) {
            if (enchSharpness != null) meta.addEnchant(enchSharpness, 5, true);
            if (enchUnbreaking != null) meta.addEnchant(enchUnbreaking, 5, true);
        }
    }

    /**
     * Get a legendary item definition by ID
     */
    public LegendaryItemDef getItemDef(String id) {
        return legendaryItems.get(id);
    }

    /**
     * Check if an item is a legendary boss item
     */
    public boolean isLegendaryItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(BOSS_ITEM_KEY, PersistentDataType.STRING);
    }

    /**
     * Get the ability ID of a legendary item
     */
    public String getItemAbility(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(ABILITY_KEY, PersistentDataType.STRING);
    }

    /**
     * Legendary item definition
     */
    public static class LegendaryItemDef {
        public final String id;
        public final Material material;
        public final String displayName;
        public final List<String> lore;
        public final String abilityId;
        public final boolean addEnchants;

        public LegendaryItemDef(String id, Material material, String displayName,
                                List<String> lore, String abilityId, boolean addEnchants) {
            this.id = id;
            this.material = material;
            this.displayName = displayName;
            this.lore = lore;
            this.abilityId = abilityId;
            this.addEnchants = addEnchants;
        }
    }
}
