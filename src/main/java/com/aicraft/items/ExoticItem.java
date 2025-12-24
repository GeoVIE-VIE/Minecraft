package com.aicraft.items;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an exotic item with special properties and effects
 */
public class ExoticItem {

    public enum Rarity {
        COMMON(ChatColor.WHITE, "Common"),
        UNCOMMON(ChatColor.GREEN, "Uncommon"),
        RARE(ChatColor.BLUE, "Rare"),
        EPIC(ChatColor.DARK_PURPLE, "Epic"),
        LEGENDARY(ChatColor.GOLD, "Legendary"),
        MYTHIC(ChatColor.LIGHT_PURPLE, "Mythic");

        private final ChatColor color;
        private final String displayName;

        Rarity(ChatColor color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }

        public ChatColor getColor() { return color; }
        public String getDisplayName() { return displayName; }
    }

    public enum ItemCategory {
        WEAPON,
        TOOL,
        ARMOR,
        ACCESSORY,
        CONSUMABLE,
        THROWABLE
    }

    private final String id;
    private final String name;
    private final Material material;
    private final Rarity rarity;
    private final ItemCategory category;
    private final String description;
    private final String drawback;
    private final List<String> effects;
    private final String requiredFaction; // null = any faction can use
    private final boolean craftable;
    private final boolean questReward;
    private final double dropChance; // 0.0 to 1.0

    // Special effect parameters
    private double effectChance = 0.0;      // Chance for effect to trigger (0-1)
    private double damageModifier = 1.0;    // Multiplier for damage
    private double selfDamage = 0.0;        // Damage to self on use
    private double speedModifier = 1.0;     // Speed multiplier
    private double healAmount = 0.0;        // Healing on hit/use
    private int durabilityModifier = 0;     // Extra/less durability
    private boolean glowing = false;        // Item glows

    private ExoticItem(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.material = builder.material;
        this.rarity = builder.rarity;
        this.category = builder.category;
        this.description = builder.description;
        this.drawback = builder.drawback;
        this.effects = builder.effects;
        this.requiredFaction = builder.requiredFaction;
        this.craftable = builder.craftable;
        this.questReward = builder.questReward;
        this.dropChance = builder.dropChance;
        this.effectChance = builder.effectChance;
        this.damageModifier = builder.damageModifier;
        this.selfDamage = builder.selfDamage;
        this.speedModifier = builder.speedModifier;
        this.healAmount = builder.healAmount;
        this.durabilityModifier = builder.durabilityModifier;
        this.glowing = builder.glowing;
    }

    /**
     * Create the actual ItemStack for this exotic item
     */
    public ItemStack createItemStack(Plugin plugin) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set display name with rarity color
        meta.setDisplayName(rarity.getColor() + "" + ChatColor.BOLD + name);

        // Build lore
        List<String> lore = new ArrayList<>();
        lore.add(rarity.getColor() + rarity.getDisplayName() + " " + category.name().toLowerCase());
        lore.add("");

        // Description
        if (description != null && !description.isEmpty()) {
            lore.add(ChatColor.GRAY + description);
            lore.add("");
        }

        // Effects
        if (!effects.isEmpty()) {
            lore.add(ChatColor.GREEN + "Effects:");
            for (String effect : effects) {
                lore.add(ChatColor.DARK_GREEN + "  ✦ " + effect);
            }
            lore.add("");
        }

        // Drawback
        if (drawback != null && !drawback.isEmpty()) {
            lore.add(ChatColor.RED + "Drawback:");
            lore.add(ChatColor.DARK_RED + "  ⚠ " + drawback);
            lore.add("");
        }

        // Faction requirement
        if (requiredFaction != null) {
            lore.add(ChatColor.YELLOW + "Requires: " + requiredFaction + " faction");
        }

        meta.setLore(lore);

        // Add glow effect
        if (glowing || rarity == Rarity.LEGENDARY || rarity == Rarity.MYTHIC) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        // Hide attributes for cleaner look
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Store exotic item ID in persistent data
        NamespacedKey key = new NamespacedKey(plugin, "exotic_item_id");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);

        item.setItemMeta(meta);
        return item;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public Material getMaterial() { return material; }
    public Rarity getRarity() { return rarity; }
    public ItemCategory getCategory() { return category; }
    public String getDescription() { return description; }
    public String getDrawback() { return drawback; }
    public List<String> getEffects() { return effects; }
    public String getRequiredFaction() { return requiredFaction; }
    public boolean isCraftable() { return craftable; }
    public boolean isQuestReward() { return questReward; }
    public double getDropChance() { return dropChance; }
    public double getEffectChance() { return effectChance; }
    public double getDamageModifier() { return damageModifier; }
    public double getSelfDamage() { return selfDamage; }
    public double getSpeedModifier() { return speedModifier; }
    public double getHealAmount() { return healAmount; }
    public int getDurabilityModifier() { return durabilityModifier; }
    public boolean isGlowing() { return glowing; }

    /**
     * Builder for ExoticItem
     */
    public static class Builder {
        private final String id;
        private String name;
        private Material material = Material.STICK;
        private Rarity rarity = Rarity.COMMON;
        private ItemCategory category = ItemCategory.WEAPON;
        private String description = "";
        private String drawback = "";
        private List<String> effects = new ArrayList<>();
        private String requiredFaction = null;
        private boolean craftable = false;
        private boolean questReward = true;
        private double dropChance = 0.01;
        private double effectChance = 0.0;
        private double damageModifier = 1.0;
        private double selfDamage = 0.0;
        private double speedModifier = 1.0;
        private double healAmount = 0.0;
        private int durabilityModifier = 0;
        private boolean glowing = false;

        public Builder(String id) {
            this.id = id;
            this.name = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder material(Material material) { this.material = material; return this; }
        public Builder rarity(Rarity rarity) { this.rarity = rarity; return this; }
        public Builder category(ItemCategory category) { this.category = category; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder drawback(String drawback) { this.drawback = drawback; return this; }
        public Builder effect(String effect) { this.effects.add(effect); return this; }
        public Builder effects(List<String> effects) { this.effects = effects; return this; }
        public Builder requiredFaction(String faction) { this.requiredFaction = faction; return this; }
        public Builder craftable(boolean craftable) { this.craftable = craftable; return this; }
        public Builder questReward(boolean questReward) { this.questReward = questReward; return this; }
        public Builder dropChance(double chance) { this.dropChance = chance; return this; }
        public Builder effectChance(double chance) { this.effectChance = chance; return this; }
        public Builder damageModifier(double modifier) { this.damageModifier = modifier; return this; }
        public Builder selfDamage(double damage) { this.selfDamage = damage; return this; }
        public Builder speedModifier(double modifier) { this.speedModifier = modifier; return this; }
        public Builder healAmount(double amount) { this.healAmount = amount; return this; }
        public Builder durabilityModifier(int modifier) { this.durabilityModifier = modifier; return this; }
        public Builder glowing(boolean glowing) { this.glowing = glowing; return this; }

        public ExoticItem build() {
            return new ExoticItem(this);
        }
    }
}
