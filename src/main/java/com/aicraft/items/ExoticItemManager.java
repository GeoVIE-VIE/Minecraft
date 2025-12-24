package com.aicraft.items;

import com.aicraft.AICompanions;
import com.aicraft.items.ExoticItem.Builder;
import com.aicraft.items.ExoticItem.ItemCategory;
import com.aicraft.items.ExoticItem.Rarity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Manages all exotic items in the plugin
 */
public class ExoticItemManager {

    private final AICompanions plugin;
    private final Map<String, ExoticItem> items = new LinkedHashMap<>();
    private final Random random = new Random();
    private final NamespacedKey exoticItemKey;

    public ExoticItemManager(AICompanions plugin) {
        this.plugin = plugin;
        this.exoticItemKey = new NamespacedKey(plugin, "exotic_item_id");
        registerAllItems();
        registerRecipes();
    }

    /**
     * Register all 50 exotic items
     */
    private void registerAllItems() {
        // ========== WEAPONS (15 items) ==========

        register(new Builder("berserker_blade")
                .name("Berserker's Blade")
                .material(Material.NETHERITE_SWORD)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.WEAPON)
                .description("A blood-soaked sword that grows stronger with your rage")
                .effect("+50% damage when below half health")
                .effect("+25% attack speed")
                .drawback("Takes 1 heart of self-damage per kill")
                .damageModifier(1.5)
                .selfDamage(2.0)
                .effectChance(1.0)
                .dropChance(0.02)
                .requiredFaction("Bandits")
                .build());

        register(new Builder("vampiric_dagger")
                .name("Vampiric Dagger")
                .material(Material.IRON_SWORD)
                .rarity(Rarity.RARE)
                .category(ItemCategory.WEAPON)
                .description("A cursed blade that drains life from victims")
                .effect("Heals 1 heart on each hit")
                .effect("Ignores 25% of armor")
                .drawback("Deals 50% less damage in sunlight")
                .healAmount(2.0)
                .effectChance(1.0)
                .questReward(true)
                .requiredFaction("Cultists")
                .build());

        register(new Builder("thunder_hammer")
                .name("Thunder Hammer")
                .material(Material.NETHERITE_AXE)
                .rarity(Rarity.LEGENDARY)
                .category(ItemCategory.WEAPON)
                .description("Forged in the heart of a storm")
                .effect("20% chance to strike lightning on hit")
                .effect("Deals +3 hearts to wet enemies")
                .drawback("Attracts hostile mobs during storms")
                .effectChance(0.2)
                .damageModifier(1.3)
                .glowing(true)
                .craftable(true)
                .build());

        register(new Builder("paranoid_sword")
                .name("Paranoid Sword")
                .material(Material.DIAMOND_SWORD)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.WEAPON)
                .description("The sword sees enemies everywhere...")
                .effect("Auto-blocks attacks from behind")
                .effect("+20% damage to invisible enemies")
                .drawback("5% chance to swing at nearby allies")
                .effectChance(0.05)
                .damageModifier(1.2)
                .requiredFaction("Guards")
                .build());

        register(new Builder("gamblers_rapier")
                .name("Gambler's Rapier")
                .material(Material.IRON_SWORD)
                .rarity(Rarity.RARE)
                .category(ItemCategory.WEAPON)
                .description("Fortune favors the bold... or does it?")
                .effect("Random damage: 1-20 hearts per hit")
                .effect("Critical hits deal triple damage")
                .drawback("10% chance to hurt yourself instead")
                .effectChance(0.1)
                .selfDamage(4.0)
                .build());

        register(new Builder("soulbound_axe")
                .name("Soulbound Axe")
                .material(Material.DIAMOND_AXE)
                .rarity(Rarity.LEGENDARY)
                .category(ItemCategory.WEAPON)
                .description("Bound to your soul, it grows with every kill")
                .effect("Gains +0.5 damage per 10 kills (max +5)")
                .effect("Cannot be dropped or stolen")
                .drawback("Drops ALL your XP on death")
                .damageModifier(1.0)
                .glowing(true)
                .dropChance(0.005)
                .build());

        register(new Builder("void_blade")
                .name("Cultist's Void Blade")
                .material(Material.NETHERITE_SWORD)
                .rarity(Rarity.MYTHIC)
                .category(ItemCategory.WEAPON)
                .description("Whispers from the void empower this blade")
                .effect("Ignores ALL armor")
                .effect("Inflicts Wither II for 3 seconds")
                .drawback("Drains 0.5 hunger per hit")
                .damageModifier(0.8)
                .effectChance(1.0)
                .requiredFaction("Cultists")
                .dropChance(0.001)
                .build());

        register(new Builder("pacifists_blade")
                .name("Pacifist's Blade")
                .material(Material.GOLDEN_SWORD)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.WEAPON)
                .description("A blade that refuses to kill")
                .effect("Cannot reduce target below 1 heart")
                .effect("Grants Speed I for 2s on hit")
                .drawback("Deals only 1 heart of damage")
                .damageModifier(0.25)
                .questReward(true)
                .build());

        register(new Builder("bone_cleaver")
                .name("Bone Cleaver")
                .material(Material.STONE_AXE)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.WEAPON)
                .description("Made from the bones of fallen warriors")
                .effect("+100% damage to undead")
                .effect("10% chance for bonus bone drops")
                .drawback("Takes double damage from undead while held")
                .damageModifier(2.0)
                .effectChance(0.1)
                .craftable(true)
                .build());

        register(new Builder("flame_tongue")
                .name("Flame Tongue")
                .material(Material.GOLDEN_SWORD)
                .rarity(Rarity.RARE)
                .category(ItemCategory.WEAPON)
                .description("A blade wreathed in eternal flames")
                .effect("Sets enemies on fire for 5 seconds")
                .effect("+50% damage to cold biome mobs")
                .drawback("Burns items in your off-hand")
                .effectChance(1.0)
                .damageModifier(1.2)
                .glowing(true)
                .build());

        register(new Builder("frost_edge")
                .name("Frost Edge")
                .material(Material.DIAMOND_SWORD)
                .rarity(Rarity.RARE)
                .category(ItemCategory.WEAPON)
                .description("Forged from eternal ice")
                .effect("Applies Slowness II for 3 seconds")
                .effect("Freezes water blocks you walk on")
                .drawback("Deals 50% less damage to Nether mobs")
                .effectChance(1.0)
                .damageModifier(1.1)
                .build());

        register(new Builder("merchant_cane")
                .name("Merchant's Cane Sword")
                .material(Material.STICK)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.WEAPON)
                .description("Looks harmless, strikes without mercy")
                .effect("Hidden blade: First hit deals +200% damage")
                .effect("Enemies don't see you as a threat")
                .drawback("Very low base damage")
                .damageModifier(0.5)
                .effectChance(1.0)
                .requiredFaction("Merchants")
                .build());

        register(new Builder("chain_whip")
                .name("Spiked Chain Whip")
                .material(Material.CHAIN)
                .rarity(Rarity.RARE)
                .category(ItemCategory.WEAPON)
                .description("Reach enemies from a distance")
                .effect("+3 block attack range")
                .effect("Can pull enemies towards you")
                .drawback("Slow attack speed")
                .damageModifier(0.9)
                .speedModifier(0.7)
                .requiredFaction("Bandits")
                .build());

        register(new Builder("sacrificial_knife")
                .name("Sacrificial Knife")
                .material(Material.IRON_SWORD)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.WEAPON)
                .description("Used in dark rituals")
                .effect("Killing grants Strength II for 10s")
                .effect("Killing grants Regeneration I for 5s")
                .drawback("Must kill once per minute or take damage")
                .effectChance(1.0)
                .requiredFaction("Cultists")
                .build());

        register(new Builder("peace_keeper")
                .name("The Peacekeeper")
                .material(Material.NETHERITE_SWORD)
                .rarity(Rarity.LEGENDARY)
                .category(ItemCategory.WEAPON)
                .description("A legendary blade of the Guard order")
                .effect("+75% damage to hostile faction NPCs")
                .effect("Cannot harm neutral NPCs")
                .effect("Grants Resistance I while held")
                .drawback("Cannot be used to attack first")
                .damageModifier(1.75)
                .requiredFaction("Guards")
                .glowing(true)
                .build());

        // ========== TOOLS (12 items) ==========

        register(new Builder("hasty_pickaxe")
                .name("Hasty Pickaxe")
                .material(Material.DIAMOND_PICKAXE)
                .rarity(Rarity.RARE)
                .category(ItemCategory.TOOL)
                .description("Mines with reckless speed")
                .effect("Efficiency X mining speed")
                .effect("Auto-smelts ores")
                .drawback("5% chance to hurt yourself per block")
                .effectChance(0.05)
                .selfDamage(1.0)
                .speedModifier(3.0)
                .craftable(true)
                .build());

        register(new Builder("greedy_shovel")
                .name("Greedy Shovel")
                .material(Material.GOLDEN_SHOVEL)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.TOOL)
                .description("Digs in a 3x3 area")
                .effect("Mines 3x3 blocks at once")
                .effect("Auto-collects drops")
                .drawback("Breaks 3x faster than normal")
                .durabilityModifier(-100)
                .speedModifier(2.0)
                .questReward(true)
                .build());

        register(new Builder("magnetic_pickaxe")
                .name("Magnetic Pickaxe")
                .material(Material.IRON_PICKAXE)
                .rarity(Rarity.RARE)
                .category(ItemCategory.TOOL)
                .description("Pulls ores towards you")
                .effect("Ores auto-fly to your inventory")
                .effect("Reveals nearby ores (glowing)")
                .drawback("Attracts Iron Golems")
                .effectChance(1.0)
                .build());

        register(new Builder("unstable_axe")
                .name("Unstable Axe")
                .material(Material.NETHERITE_AXE)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.TOOL)
                .description("Chops entire trees... violently")
                .effect("One-shots entire trees")
                .effect("Logs auto-collect")
                .drawback("10% chance to explode (no block damage)")
                .effectChance(0.1)
                .selfDamage(6.0)
                .craftable(true)
                .build());

        register(new Builder("miners_lantern_pick")
                .name("Miner's Lantern Pick")
                .material(Material.DIAMOND_PICKAXE)
                .rarity(Rarity.RARE)
                .category(ItemCategory.TOOL)
                .description("Illuminates the depths")
                .effect("Night Vision while held underground")
                .effect("Reveals valuable ores through walls")
                .drawback("Blindness for 3s when entering sunlight")
                .effectChance(1.0)
                .dropChance(0.01)
                .build());

        register(new Builder("phantom_shovel")
                .name("Phantom Shovel")
                .material(Material.NETHERITE_SHOVEL)
                .rarity(Rarity.MYTHIC)
                .category(ItemCategory.TOOL)
                .description("Can dig through the undiggable")
                .effect("Can break bedrock (1 layer only)")
                .effect("Phases through blocks when sneaking")
                .drawback("Only works at night")
                .dropChance(0.001)
                .glowing(true)
                .build());

        register(new Builder("silken_shears")
                .name("Silken Shears")
                .material(Material.SHEARS)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.TOOL)
                .description("Harvests with supernatural precision")
                .effect("Silk Touch on all blocks")
                .effect("+50% crop yields")
                .drawback("Cannot harvest hostile mob drops")
                .effectChance(1.0)
                .build());

        register(new Builder("fortune_finder")
                .name("Fortune Finder")
                .material(Material.GOLDEN_PICKAXE)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.TOOL)
                .description("The luckiest pickaxe ever made")
                .effect("Fortune V on ores")
                .effect("5% chance for bonus diamonds")
                .drawback("Very low durability")
                .effectChance(0.05)
                .durabilityModifier(-200)
                .glowing(true)
                .build());

        register(new Builder("excavator")
                .name("The Excavator")
                .material(Material.NETHERITE_SHOVEL)
                .rarity(Rarity.LEGENDARY)
                .category(ItemCategory.TOOL)
                .description("Moves mountains with ease")
                .effect("Mines 5x5 area")
                .effect("Never takes durability damage")
                .drawback("Cannot mine ores")
                .speedModifier(2.5)
                .glowing(true)
                .dropChance(0.003)
                .build());

        register(new Builder("netheric_pick")
                .name("Netheric Pickaxe")
                .material(Material.NETHERITE_PICKAXE)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.TOOL)
                .description("Forged in the Nether's core")
                .effect("+200% speed on Nether blocks")
                .effect("Immune to lava damage while held")
                .drawback("-50% speed on Overworld blocks")
                .speedModifier(3.0)
                .build());

        register(new Builder("woodsmans_friend")
                .name("Woodsman's Friend")
                .material(Material.IRON_AXE)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.TOOL)
                .description("Every lumberjack's dream")
                .effect("Chops connected logs")
                .effect("+25% wood drops")
                .effect("Saplings auto-plant")
                .drawback("Cannot be used as a weapon")
                .damageModifier(0.1)
                .build());

        register(new Builder("vein_miner")
                .name("Vein Miner")
                .material(Material.DIAMOND_PICKAXE)
                .rarity(Rarity.RARE)
                .category(ItemCategory.TOOL)
                .description("Mines entire ore veins")
                .effect("Breaks all connected ore blocks")
                .effect("Works on up to 64 blocks")
                .drawback("Uses durability for each block")
                .durabilityModifier(-50)
                .craftable(true)
                .build());

        // ========== ARMOR (10 items) ==========

        register(new Builder("berserker_chestplate")
                .name("Berserker's Chestplate")
                .material(Material.NETHERITE_CHESTPLATE)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.ARMOR)
                .description("Armor that amplifies rage")
                .effect("+30% damage dealt")
                .effect("Strength I when below 50% health")
                .drawback("+20% damage taken")
                .damageModifier(1.3)
                .requiredFaction("Bandits")
                .build());

        register(new Builder("shadow_cloak")
                .name("Cloak of Shadows")
                .material(Material.LEATHER_CHESTPLATE)
                .rarity(Rarity.LEGENDARY)
                .category(ItemCategory.ARMOR)
                .description("Woven from pure darkness")
                .effect("Invisibility at night")
                .effect("+50% movement speed at night")
                .drawback("Weakness I during day")
                .speedModifier(1.5)
                .requiredFaction("Cultists")
                .glowing(true)
                .build());

        register(new Builder("guardian_plate")
                .name("Guardian's Oath Plate")
                .material(Material.DIAMOND_CHESTPLATE)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.ARMOR)
                .description("Sworn to protect")
                .effect("Immune to NPC damage")
                .effect("Resistance I always")
                .drawback("Cannot harm neutral NPCs")
                .requiredFaction("Guards")
                .build());

        register(new Builder("wanderer_boots")
                .name("Wanderer's Swift Boots")
                .material(Material.LEATHER_BOOTS)
                .rarity(Rarity.RARE)
                .category(ItemCategory.ARMOR)
                .description("Made for the open road")
                .effect("+50% movement speed")
                .effect("No fall damage")
                .drawback("Cannot sprint")
                .speedModifier(1.5)
                .requiredFaction("Wanderers")
                .build());

        register(new Builder("thorned_armor")
                .name("Thorned Plate")
                .material(Material.IRON_CHESTPLATE)
                .rarity(Rarity.RARE)
                .category(ItemCategory.ARMOR)
                .description("Touch it and bleed")
                .effect("Attackers take 2 hearts damage")
                .effect("Thorns effect on all pieces")
                .drawback("Cannot wear other chest armor")
                .effectChance(1.0)
                .build());

        register(new Builder("featherfall_boots")
                .name("Featherfall Boots")
                .material(Material.GOLDEN_BOOTS)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.ARMOR)
                .description("Light as a feather")
                .effect("No fall damage")
                .effect("Slow falling when sneaking")
                .effect("Double jump (cooldown: 3s)")
                .drawback("Very fragile")
                .durabilityModifier(-100)
                .build());

        register(new Builder("molten_helm")
                .name("Molten Helm")
                .material(Material.NETHERITE_HELMET)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.ARMOR)
                .description("Forged in liquid fire")
                .effect("Fire Resistance always")
                .effect("Attackers catch fire")
                .drawback("Takes double damage from water/rain")
                .effectChance(1.0)
                .build());

        register(new Builder("ocean_helm")
                .name("Helm of the Depths")
                .material(Material.DIAMOND_HELMET)
                .rarity(Rarity.RARE)
                .category(ItemCategory.ARMOR)
                .description("Breathe beneath the waves")
                .effect("Water Breathing always")
                .effect("Dolphin's Grace in water")
                .effect("+50% swim speed")
                .drawback("Slowness I on land")
                .speedModifier(1.5)
                .build());

        register(new Builder("phantom_leggings")
                .name("Phantom Leggings")
                .material(Material.CHAINMAIL_LEGGINGS)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.ARMOR)
                .description("Phase through your enemies")
                .effect("5% chance to dodge attacks")
                .effect("Can walk through mobs")
                .drawback("Reduced armor value")
                .effectChance(0.05)
                .build());

        register(new Builder("giants_boots")
                .name("Giant's Boots")
                .material(Material.IRON_BOOTS)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.ARMOR)
                .description("Walk with thundering steps")
                .effect("Knockback resistance")
                .effect("Stomping damages nearby mobs")
                .drawback("-25% movement speed")
                .speedModifier(0.75)
                .damageModifier(1.0)
                .build());

        // ========== INVISIBILITY CLOAKS (4 items - Chest loot only) ==========

        register(new Builder("true_invisibility_cloak")
                .name("Cloak of True Invisibility")
                .material(Material.LEATHER_CHESTPLATE)
                .rarity(Rarity.LEGENDARY)
                .category(ItemCategory.ARMOR)
                .description("Woven from shadows themselves")
                .effect("Permanent invisibility while worn")
                .effect("Mobs cannot detect you")
                .drawback("Cannot deal damage while invisible")
                .drawback("Taking damage reveals you for 5s")
                .dropChance(0.008)
                .questReward(false)
                .craftable(false)
                .glowing(true)
                .build());

        register(new Builder("phantom_cloak")
                .name("Phantom Cloak")
                .material(Material.CHAINMAIL_CHESTPLATE)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.ARMOR)
                .description("Phase through the physical realm")
                .effect("Invisible + can walk through mobs")
                .effect("No collision with entities")
                .drawback("Visible when sprinting")
                .drawback("Glows faintly at night")
                .dropChance(0.01)
                .questReward(false)
                .craftable(false)
                .build());

        register(new Builder("thiefs_cloak")
                .name("Thief's Cloak")
                .material(Material.LEATHER_CHESTPLATE)
                .rarity(Rarity.RARE)
                .category(ItemCategory.ARMOR)
                .description("Perfect for sneaky operations")
                .effect("Invisible while sneaking")
                .effect("Silent footsteps")
                .drawback("Visible when standing/walking")
                .drawback("Glows when near NPCs (within 5 blocks)")
                .dropChance(0.015)
                .questReward(false)
                .craftable(false)
                .build());

        register(new Builder("cursed_cloak")
                .name("Cursed Cloak of Shadows")
                .material(Material.NETHERITE_CHESTPLATE)
                .rarity(Rarity.MYTHIC)
                .category(ItemCategory.ARMOR)
                .description("A cloak that hungers for life")
                .effect("Full invisibility always")
                .effect("Invisible to ALL detection")
                .effect("+50% movement speed")
                .drawback("Drains 0.5 hearts every 3 seconds")
                .drawback("Cannot be removed while below 3 hearts")
                .selfDamage(1.0)
                .speedModifier(1.5)
                .dropChance(0.003)
                .questReward(false)
                .craftable(false)
                .glowing(true)
                .build());

        // ========== ACCESSORIES (8 items) ==========

        register(new Builder("lucky_coin")
                .name("Merchant's Lucky Coin")
                .material(Material.GOLD_NUGGET)
                .rarity(Rarity.RARE)
                .category(ItemCategory.ACCESSORY)
                .description("Fortune smiles upon the holder")
                .effect("+25% loot from all sources")
                .effect("+10% XP gain")
                .drawback("5% chance to lose random item on death")
                .effectChance(0.05)
                .requiredFaction("Merchants")
                .build());

        register(new Builder("oath_ring")
                .name("Guardian's Oath Ring")
                .material(Material.GOLD_INGOT)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.ACCESSORY)
                .description("A sworn protector's bond")
                .effect("Immune to friendly NPC damage")
                .effect("+25% damage to hostile NPCs")
                .drawback("Cannot harm neutral NPCs")
                .damageModifier(1.25)
                .requiredFaction("Guards")
                .build());

        register(new Builder("chaos_orb")
                .name("Orb of Chaos")
                .material(Material.ENDER_PEARL)
                .rarity(Rarity.LEGENDARY)
                .category(ItemCategory.ACCESSORY)
                .description("Reality bends around it")
                .effect("Random teleport when hit (5 block radius)")
                .effect("Random potion effect every 30s")
                .drawback("May teleport into danger")
                .effectChance(0.3)
                .glowing(true)
                .build());

        register(new Builder("soul_lantern")
                .name("Soul Lantern")
                .material(Material.SOUL_LANTERN)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.ACCESSORY)
                .description("Captures souls of the fallen")
                .effect("Captures essence of killed NPCs")
                .effect("Can resurrect NPCs at shrines")
                .drawback("Or consume souls for power boost")
                .requiredFaction("Cultists")
                .glowing(true)
                .build());

        register(new Builder("faction_detector")
                .name("Faction Compass")
                .material(Material.COMPASS)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.ACCESSORY)
                .description("Points to faction territories")
                .effect("Shows faction territories")
                .effect("Warns when entering hostile territory")
                .effect("Points to nearest faction NPC")
                .drawback("Doesn't work in the Nether")
                .build());

        register(new Builder("memory_crystal")
                .name("Memory Crystal")
                .material(Material.AMETHYST_SHARD)
                .rarity(Rarity.RARE)
                .category(ItemCategory.ACCESSORY)
                .description("Stores and shares memories")
                .effect("Records NPC conversations")
                .effect("Can share gossip between NPCs")
                .effect("Reveals NPC secrets")
                .drawback("NPCs may react to planted memories")
                .build());

        register(new Builder("mood_lantern")
                .name("Mood Lantern")
                .material(Material.LANTERN)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.ACCESSORY)
                .description("Reveals the hearts of NPCs")
                .effect("Shows NPC mood as colored particles")
                .effect("Reveals hidden hostility")
                .effect("Identifies faction allegiance")
                .drawback("Makes you more noticeable")
                .build());

        register(new Builder("bounty_compass")
                .name("Bounty Hunter's Compass")
                .material(Material.COMPASS)
                .rarity(Rarity.RARE)
                .category(ItemCategory.ACCESSORY)
                .description("Always finds its mark")
                .effect("Points to hostile NPCs")
                .effect("Shows distance and threat level")
                .effect("+10% damage to tracked targets")
                .drawback("Target knows they're being hunted")
                .damageModifier(1.1)
                .requiredFaction("Bandits")
                .build());

        // ========== CONSUMABLES (5 items) ==========

        register(new Builder("berserker_brew")
                .name("Berserker's Brew")
                .material(Material.POTION)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.CONSUMABLE)
                .description("Liquid rage in a bottle")
                .effect("Strength III for 2 minutes")
                .effect("Speed II for 2 minutes")
                .drawback("Blindness for 10s after effect ends")
                .effectChance(1.0)
                .requiredFaction("Bandits")
                .craftable(true)
                .build());

        register(new Builder("memory_potion")
                .name("Potion of Forgetting")
                .material(Material.POTION)
                .rarity(Rarity.RARE)
                .category(ItemCategory.CONSUMABLE)
                .description("Makes NPCs forget...")
                .effect("Target NPC forgets your crimes")
                .effect("Resets relationship to neutral")
                .drawback("NPC also forgets your friendship")
                .requiredFaction("Cultists")
                .build());

        register(new Builder("faction_elixir")
                .name("Elixir of Allegiance")
                .material(Material.POTION)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.CONSUMABLE)
                .description("Temporarily shift your allegiance")
                .effect("Join any faction for 5 minutes")
                .effect("NPCs treat you as ally")
                .drawback("Permanent rep loss with old faction")
                .dropChance(0.01)
                .build());

        register(new Builder("healing_salve")
                .name("Ancient Healing Salve")
                .material(Material.HONEYCOMB)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.CONSUMABLE)
                .description("A miraculous healing substance")
                .effect("Instantly heals 10 hearts")
                .effect("Removes all negative effects")
                .effect("Fire resistance for 30s")
                .drawback("Hunger III for 1 minute")
                .healAmount(20.0)
                .craftable(true)
                .build());

        register(new Builder("void_essence")
                .name("Void Essence")
                .material(Material.DRAGON_BREATH)
                .rarity(Rarity.MYTHIC)
                .category(ItemCategory.CONSUMABLE)
                .description("Pure distilled nothingness")
                .effect("Temporary invulnerability (5s)")
                .effect("Can phase through walls")
                .drawback("Lose 50% max health for 1 hour")
                .dropChance(0.001)
                .glowing(true)
                .build());

        // ========== THROWABLES (5 items) ==========

        register(new Builder("faction_bomb")
                .name("Faction Bomb")
                .material(Material.FIRE_CHARGE)
                .rarity(Rarity.RARE)
                .category(ItemCategory.THROWABLE)
                .description("Only harms your enemies")
                .effect("Damages only hostile faction NPCs")
                .effect("3 block explosion radius")
                .effect("Does not destroy blocks")
                .drawback("Alerts all nearby enemies")
                .craftable(true)
                .build());

        register(new Builder("smoke_bomb")
                .name("Smoke Bomb")
                .material(Material.GUNPOWDER)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.THROWABLE)
                .description("Disappear in a cloud of smoke")
                .effect("Creates blinding smoke cloud")
                .effect("Grants invisibility for 5s")
                .effect("NPCs lose track of you")
                .drawback("Slow movement in smoke")
                .craftable(true)
                .build());

        register(new Builder("chain_lightning")
                .name("Chain Lightning Orb")
                .material(Material.HEART_OF_THE_SEA)
                .rarity(Rarity.EPIC)
                .category(ItemCategory.THROWABLE)
                .description("Lightning that leaps between foes")
                .effect("Strikes initial target")
                .effect("Chains to 5 nearby enemies")
                .effect("Each chain deals 3 hearts")
                .drawback("May chain to you if too close")
                .selfDamage(6.0)
                .effectChance(0.1)
                .build());

        register(new Builder("frost_bomb")
                .name("Frost Bomb")
                .material(Material.SNOWBALL)
                .rarity(Rarity.UNCOMMON)
                .category(ItemCategory.THROWABLE)
                .description("Winter in a ball")
                .effect("Freezes enemies for 3 seconds")
                .effect("Creates ice floor")
                .effect("Slowness III in area")
                .drawback("You're also slowed if caught")
                .craftable(true)
                .build());

        register(new Builder("gravity_orb")
                .name("Gravity Orb")
                .material(Material.ENDER_EYE)
                .rarity(Rarity.LEGENDARY)
                .category(ItemCategory.THROWABLE)
                .description("Bends gravity itself")
                .effect("Pulls all entities to impact point")
                .effect("Levitation for 3s to all affected")
                .effect("Fall damage on landing")
                .drawback("Affects you too if in range")
                .glowing(true)
                .dropChance(0.005)
                .build());

        plugin.getLogger().info("Registered " + items.size() + " exotic items");
    }

    private void register(ExoticItem item) {
        items.put(item.getId(), item);
    }

    /**
     * Register crafting recipes for craftable items
     */
    private void registerRecipes() {
        // Thunder Hammer recipe
        registerRecipe("thunder_hammer", new String[]{
                "DLD",
                "DSD",
                " S "
        }, 'D', Material.DIAMOND, 'L', Material.LIGHTNING_ROD, 'S', Material.STICK);

        // Hasty Pickaxe recipe
        registerRecipe("hasty_pickaxe", new String[]{
                "DRD",
                " S ",
                " S "
        }, 'D', Material.DIAMOND, 'R', Material.REDSTONE_BLOCK, 'S', Material.STICK);

        // Unstable Axe recipe
        registerRecipe("unstable_axe", new String[]{
                "NT",
                "NS",
                " S"
        }, 'N', Material.NETHERITE_INGOT, 'T', Material.TNT, 'S', Material.STICK);

        // Bone Cleaver recipe
        registerRecipe("bone_cleaver", new String[]{
                "BB",
                "BS",
                " S"
        }, 'B', Material.BONE_BLOCK, 'S', Material.STICK);

        // Vein Miner recipe
        registerRecipe("vein_miner", new String[]{
                "DED",
                " S ",
                " S "
        }, 'D', Material.DIAMOND, 'E', Material.ENDER_EYE, 'S', Material.STICK);

        // Berserker Brew recipe
        registerRecipe("berserker_brew", new String[]{
                "NNN",
                "NBN",
                "NPN"
        }, 'N', Material.NETHER_WART, 'B', Material.BLAZE_POWDER, 'P', Material.GLASS_BOTTLE);

        // Healing Salve recipe
        registerRecipe("healing_salve", new String[]{
                "GHG",
                "HMH",
                "GHG"
        }, 'G', Material.GOLD_NUGGET, 'H', Material.HONEYCOMB, 'M', Material.GLISTERING_MELON_SLICE);

        // Faction Bomb recipe
        registerRecipe("faction_bomb", new String[]{
                " G ",
                "GTG",
                " G "
        }, 'G', Material.GUNPOWDER, 'T', Material.TNT);

        // Smoke Bomb recipe
        registerRecipe("smoke_bomb", new String[]{
                "III",
                "IGI",
                "III"
        }, 'I', Material.INK_SAC, 'G', Material.GUNPOWDER);

        // Frost Bomb recipe
        registerRecipe("frost_bomb", new String[]{
                "SSS",
                "SPS",
                "SSS"
        }, 'S', Material.SNOWBALL, 'P', Material.PACKED_ICE);

        plugin.getLogger().info("Registered exotic item crafting recipes");
    }

    private void registerRecipe(String itemId, String[] shape, Object... ingredients) {
        ExoticItem item = items.get(itemId);
        if (item == null || !item.isCraftable()) return;

        try {
            NamespacedKey key = new NamespacedKey(plugin, "exotic_" + itemId);
            ShapedRecipe recipe = new ShapedRecipe(key, item.createItemStack(plugin));
            recipe.shape(shape);

            for (int i = 0; i < ingredients.length; i += 2) {
                char c = (Character) ingredients[i];
                Material m = (Material) ingredients[i + 1];
                recipe.setIngredient(c, m);
            }

            plugin.getServer().addRecipe(recipe);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register recipe for " + itemId + ": " + e.getMessage());
        }
    }

    // ========== PUBLIC API ==========

    /**
     * Get an exotic item by ID
     */
    public ExoticItem getItem(String id) {
        return items.get(id);
    }

    /**
     * Get all registered exotic items
     */
    public Collection<ExoticItem> getAllItems() {
        return items.values();
    }

    /**
     * Create an ItemStack for an exotic item
     */
    public ItemStack createItemStack(String id) {
        ExoticItem item = items.get(id);
        if (item == null) return null;
        return item.createItemStack(plugin);
    }

    /**
     * Get the exotic item ID from an ItemStack
     */
    public String getExoticItemId(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) return null;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(exoticItemKey, PersistentDataType.STRING);
    }

    /**
     * Check if an ItemStack is an exotic item
     */
    public boolean isExoticItem(ItemStack itemStack) {
        return getExoticItemId(itemStack) != null;
    }

    /**
     * Get a random exotic item based on rarity weights
     */
    public ExoticItem getRandomItem() {
        List<ExoticItem> eligible = new ArrayList<>(items.values());
        if (eligible.isEmpty()) return null;

        // Weight by rarity (rarer = less likely)
        double totalWeight = 0;
        for (ExoticItem item : eligible) {
            totalWeight += getRarityWeight(item.getRarity());
        }

        double roll = random.nextDouble() * totalWeight;
        double current = 0;
        for (ExoticItem item : eligible) {
            current += getRarityWeight(item.getRarity());
            if (roll <= current) return item;
        }

        return eligible.get(0);
    }

    /**
     * Get a random quest reward item
     */
    public ExoticItem getRandomQuestReward() {
        List<ExoticItem> eligible = items.values().stream()
                .filter(ExoticItem::isQuestReward)
                .toList();
        if (eligible.isEmpty()) return getRandomItem();

        // Weight by rarity
        double totalWeight = 0;
        for (ExoticItem item : eligible) {
            totalWeight += getRarityWeight(item.getRarity());
        }

        double roll = random.nextDouble() * totalWeight;
        double current = 0;
        for (ExoticItem item : eligible) {
            current += getRarityWeight(item.getRarity());
            if (roll <= current) return item;
        }

        return eligible.get(0);
    }

    /**
     * Get a random item that drops from a faction
     */
    public ExoticItem getRandomFactionDrop(String faction) {
        List<ExoticItem> eligible = items.values().stream()
                .filter(item -> item.getRequiredFaction() == null ||
                        item.getRequiredFaction().equalsIgnoreCase(faction))
                .filter(item -> random.nextDouble() < item.getDropChance())
                .toList();

        if (eligible.isEmpty()) return null;
        return eligible.get(random.nextInt(eligible.size()));
    }

    /**
     * Get items available for a specific faction
     */
    public List<ExoticItem> getItemsForFaction(String faction) {
        return items.values().stream()
                .filter(item -> item.getRequiredFaction() == null ||
                        item.getRequiredFaction().equalsIgnoreCase(faction))
                .toList();
    }

    /**
     * Give a random exotic item to a player
     */
    public ExoticItem giveRandomItem(Player player) {
        ExoticItem item = getRandomItem();
        if (item != null) {
            player.getInventory().addItem(item.createItemStack(plugin));
        }
        return item;
    }

    /**
     * Give a specific exotic item to a player
     */
    public boolean giveItem(Player player, String itemId) {
        ExoticItem item = items.get(itemId);
        if (item == null) return false;
        player.getInventory().addItem(item.createItemStack(plugin));
        return true;
    }

    private double getRarityWeight(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 100.0;
            case UNCOMMON -> 50.0;
            case RARE -> 25.0;
            case EPIC -> 10.0;
            case LEGENDARY -> 3.0;
            case MYTHIC -> 1.0;
        };
    }
}
