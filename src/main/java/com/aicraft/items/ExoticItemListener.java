package com.aicraft.items;

import com.aicraft.AICompanions;
import com.aicraft.items.ExoticItem.ItemCategory;
import com.aicraft.npcs.AINpc;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Handles all exotic item special effects
 */
public class ExoticItemListener implements Listener {

    private final AICompanions plugin;
    private final ExoticItemManager itemManager;
    private final Random random = new Random();

    // Track kills for soulbound axe
    private final Map<UUID, Integer> soulboundKills = new HashMap<>();

    // Track sacrificial knife cooldowns
    private final Map<UUID, Long> sacrificialLastKill = new HashMap<>();

    // Cooldowns for various effects
    private final Map<UUID, Long> doubleJumpCooldown = new HashMap<>();
    private final Map<UUID, Long> chaosOrbCooldown = new HashMap<>();

    // Invisibility cloak tracking
    private final Map<UUID, Long> trueInvisibilityRevealedUntil = new HashMap<>(); // Tracks when revealed players become invisible again
    private final Map<UUID, Long> cursedCloakLastDrain = new HashMap<>(); // Tracks health drain timing

    public ExoticItemListener(AICompanions plugin, ExoticItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;

        // Start periodic effect tasks
        startPeriodicTasks();
    }

    private void startPeriodicTasks() {
        // Check for sacrificial knife requirement
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkSacrificialKnife(player);
                    checkChaosOrb(player);
                    applyPassiveEffects(player);
                }
            }
        }.runTaskTimer(plugin, 100L, 20L); // Every second
    }

    // ========== COMBAT EFFECTS ==========

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        String itemId = itemManager.getExoticItemId(weapon);
        if (itemId == null) return;

        ExoticItem exoticItem = itemManager.getItem(itemId);
        if (exoticItem == null) return;

        double damage = event.getDamage();
        Entity victim = event.getEntity();

        switch (itemId) {
            case "berserker_blade" -> {
                // +50% damage when below half health
                if (player.getHealth() < player.getMaxHealth() / 2) {
                    damage *= 1.5;
                    player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, victim.getLocation(), 5);
                }
            }

            case "vampiric_dagger" -> {
                // Heal on hit
                long time = player.getWorld().getTime();
                boolean isDaytime = time < 12300 || time > 23850;

                if (isDaytime) {
                    damage *= 0.5; // Reduced damage in sunlight
                }
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 2));
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 3);
            }

            case "thunder_hammer" -> {
                // 20% chance to strike lightning
                if (random.nextDouble() < 0.2) {
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    if (victim instanceof LivingEntity living) {
                        living.damage(6.0, player);
                    }
                }
                // Extra damage to wet enemies
                if (victim instanceof LivingEntity living) {
                    if (living.isInWater() || living.getWorld().hasStorm()) {
                        damage += 6.0;
                    }
                }
            }

            case "paranoid_sword" -> {
                // 5% chance to swing at allies
                if (random.nextDouble() < 0.05) {
                    List<Entity> nearby = player.getNearbyEntities(3, 3, 3);
                    for (Entity e : nearby) {
                        if (e instanceof LivingEntity living && !(e instanceof Monster)) {
                            living.damage(2.0, player);
                            player.sendMessage(ChatColor.RED + "Your paranoid sword lashes out!");
                            break;
                        }
                    }
                }
            }

            case "gamblers_rapier" -> {
                // Random damage 1-20 hearts
                damage = 2 + random.nextDouble() * 38; // 1-20 hearts (2-40 damage)
                // 10% chance to hurt self
                if (random.nextDouble() < 0.1) {
                    player.damage(4.0);
                    player.sendMessage(ChatColor.RED + "Bad luck! You stabbed yourself!");
                    event.setCancelled(true);
                    return;
                }
                // Critical hit bonus
                if (random.nextDouble() < 0.1) {
                    damage *= 3;
                    player.sendMessage(ChatColor.GOLD + "JACKPOT! Critical hit!");
                    player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, victim.getLocation(), 20);
                }
            }

            case "soulbound_axe" -> {
                // Bonus damage based on kills
                int kills = soulboundKills.getOrDefault(player.getUniqueId(), 0);
                double bonus = Math.min(kills / 10.0 * 0.5, 5.0); // +0.5 per 10 kills, max +5
                damage += bonus * 2; // Convert hearts to damage
            }

            case "void_blade" -> {
                // Ignores armor, applies wither
                event.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
                if (victim instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1));
                }
                // Drain hunger
                player.setFoodLevel(Math.max(0, player.getFoodLevel() - 1));
            }

            case "pacifists_blade" -> {
                // Cannot kill, grants speed
                if (victim instanceof LivingEntity living) {
                    if (living.getHealth() - damage <= 2) {
                        damage = living.getHealth() - 2; // Leave at 1 heart
                    }
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0));
            }

            case "bone_cleaver" -> {
                // Double damage to undead
                if (victim instanceof Zombie || victim instanceof Skeleton ||
                        victim instanceof Wither || victim instanceof Phantom) {
                    damage *= 2;
                }
            }

            case "flame_tongue" -> {
                // Set on fire
                victim.setFireTicks(100);
            }

            case "frost_edge" -> {
                // Apply slowness
                if (victim instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation(), 20);
                }
            }

            case "merchant_cane" -> {
                // First hit bonus (check if target was recently hit)
                // Simplified: Just give bonus damage sometimes
                if (random.nextDouble() < 0.3) {
                    damage *= 3;
                    player.sendMessage(ChatColor.GOLD + "Surprise attack!");
                }
            }

            case "chain_whip" -> {
                // Pull effect
                if (victim instanceof LivingEntity && random.nextDouble() < 0.3) {
                    org.bukkit.util.Vector pull = player.getLocation().toVector()
                            .subtract(victim.getLocation().toVector()).normalize().multiply(0.5);
                    victim.setVelocity(pull);
                }
            }

            case "sacrificial_knife" -> {
                sacrificialLastKill.put(player.getUniqueId(), System.currentTimeMillis());
            }

            case "peace_keeper" -> {
                // Check if victim is hostile NPC
                AINpc npc = plugin.getNPCManager().getNPCFromEntity(victim);
                if (npc != null) {
                    if (npc.isHostile()) {
                        damage *= 1.75;
                    } else {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.GRAY + "The Peacekeeper refuses to harm the innocent.");
                        return;
                    }
                }
            }

            case "bounty_compass" -> {
                damage *= 1.1; // +10% to tracked targets
            }
        }

        event.setDamage(damage);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        String itemId = itemManager.getExoticItemId(weapon);
        if (itemId == null) return;

        switch (itemId) {
            case "berserker_blade" -> {
                // Self damage on kill
                killer.damage(2.0);
                killer.sendMessage(ChatColor.DARK_RED + "The blade's bloodlust hurts you...");
            }

            case "soulbound_axe" -> {
                int kills = soulboundKills.getOrDefault(killer.getUniqueId(), 0);
                soulboundKills.put(killer.getUniqueId(), kills + 1);
                if ((kills + 1) % 10 == 0) {
                    killer.sendMessage(ChatColor.DARK_PURPLE + "Soulbound Axe grows stronger! (+" +
                            Math.min((kills + 1) / 10 * 0.5, 5.0) + " damage)");
                }
            }

            case "sacrificial_knife" -> {
                killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1));
                killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0));
            }

            case "bone_cleaver" -> {
                // Bonus bone drops from undead
                if (event.getEntity() instanceof Zombie || event.getEntity() instanceof Skeleton) {
                    if (random.nextDouble() < 0.1) {
                        event.getDrops().add(new ItemStack(Material.BONE, random.nextInt(3) + 1));
                    }
                }
            }
        }

        // Check for exotic item drops from NPCs
        AINpc npc = plugin.getNPCManager().getNPCFromEntity(event.getEntity());
        if (npc != null) {
            ExoticItem drop = itemManager.getRandomFactionDrop(npc.getFaction());
            if (drop != null) {
                event.getDrops().add(drop.createItemStack(plugin));
                killer.sendMessage(ChatColor.GOLD + "You found: " + drop.getRarity().getColor() + drop.getName());
            }
        }
    }

    // ========== INVISIBILITY CLOAK HANDLERS ==========

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Check for true_invisibility_cloak - reveal when damaged
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) continue;
            String itemId = itemManager.getExoticItemId(armor);
            if ("true_invisibility_cloak".equals(itemId)) {
                // Reveal for 5 seconds
                trueInvisibilityRevealedUntil.put(player.getUniqueId(), System.currentTimeMillis() + 5000);
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
                player.sendMessage(ChatColor.RED + "The cloak's invisibility falters as you take damage!");
                player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.1);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDealDamageWithCloak(EntityDamageByEntityEvent event) {
        // Check if attacker is wearing true_invisibility_cloak and is invisible
        if (!(event.getDamager() instanceof Player player)) return;

        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) continue;
            String itemId = itemManager.getExoticItemId(armor);
            if ("true_invisibility_cloak".equals(itemId)) {
                // Check if currently invisible (not revealed)
                Long revealedUntil = trueInvisibilityRevealedUntil.get(player.getUniqueId());
                boolean isRevealed = revealedUntil != null && System.currentTimeMillis() < revealedUntil;

                if (!isRevealed) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.GRAY + "You cannot deal damage while truly invisible...");
                    return;
                }
                break;
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if trying to remove cursed_cloak while below 3 hearts
        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        String itemId = itemManager.getExoticItemId(current);
        if ("cursed_cloak".equals(itemId)) {
            // Check if it's in armor slot
            int slot = event.getRawSlot();
            if (slot >= 5 && slot <= 8) { // Armor slots in player inventory
                if (player.getHealth() <= 6) { // 3 hearts or less
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.DARK_RED + "The cursed cloak refuses to release you!");
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.5f);
                }
            }
        }
    }

    // ========== TOOL EFFECTS ==========

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        String itemId = itemManager.getExoticItemId(tool);
        if (itemId == null) return;

        ExoticItem exoticItem = itemManager.getItem(itemId);
        if (exoticItem == null || exoticItem.getCategory() != ItemCategory.TOOL) return;

        Block block = event.getBlock();

        switch (itemId) {
            case "hasty_pickaxe" -> {
                // 5% chance to hurt yourself
                if (random.nextDouble() < 0.05) {
                    player.damage(1.0);
                    player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 5);
                }
                // Auto-smelt ores
                if (isOre(block.getType())) {
                    event.setDropItems(false);
                    ItemStack smelted = getSmeltedOre(block.getType());
                    if (smelted != null) {
                        block.getWorld().dropItemNaturally(block.getLocation(), smelted);
                    }
                }
            }

            case "greedy_shovel" -> {
                // Mine 3x3
                if (isShovelBlock(block.getType())) {
                    breakArea(player, block, 3, tool);
                }
            }

            case "unstable_axe" -> {
                // One-shot trees
                if (isLog(block.getType())) {
                    breakConnectedLogs(player, block, tool);
                    // 10% chance to explode
                    if (random.nextDouble() < 0.1) {
                        player.getWorld().createExplosion(block.getLocation(), 2f, false, false);
                        player.damage(6.0);
                        player.sendMessage(ChatColor.RED + "The axe exploded!");
                    }
                }
            }

            case "vein_miner" -> {
                // Mine all connected ores
                if (isOre(block.getType())) {
                    breakConnectedOres(player, block, tool, block.getType());
                }
            }

            case "woodsmans_friend" -> {
                // Chop connected logs, auto-plant
                if (isLog(block.getType())) {
                    breakConnectedLogs(player, block, tool);
                    // Auto-plant sapling
                    Material sapling = getSaplingForLog(block.getType());
                    if (sapling != null && random.nextDouble() < 0.3) {
                        Block below = block.getRelative(0, -1, 0);
                        if (below.getType() == Material.DIRT || below.getType() == Material.GRASS_BLOCK) {
                            block.setType(sapling);
                        }
                    }
                }
            }

            case "fortune_finder" -> {
                // 5% chance for bonus diamonds
                if (block.getType() == Material.DIAMOND_ORE || block.getType() == Material.DEEPSLATE_DIAMOND_ORE) {
                    if (random.nextDouble() < 0.05) {
                        block.getWorld().dropItemNaturally(block.getLocation(),
                                new ItemStack(Material.DIAMOND, random.nextInt(3) + 1));
                        player.sendMessage(ChatColor.AQUA + "Lucky find!");
                    }
                }
            }

            case "excavator" -> {
                // Mine 5x5, but not ores
                if (!isOre(block.getType()) && (isShovelBlock(block.getType()) || isPickaxeBlock(block.getType()))) {
                    breakArea(player, block, 5, tool);
                }
            }

            case "netheric_pick" -> {
                // Speed bonus on nether blocks (already handled by efficiency)
                if (block.getWorld().getEnvironment() == World.Environment.NETHER) {
                    event.setExpToDrop(event.getExpToDrop() * 2);
                }
            }
        }
    }

    // ========== PASSIVE EFFECTS ==========

    private void applyPassiveEffects(Player player) {
        // Check main hand and armor
        checkHeldItemEffects(player);
        checkArmorEffects(player);
    }

    private void checkHeldItemEffects(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String itemId = itemManager.getExoticItemId(held);
        if (itemId == null) return;

        switch (itemId) {
            case "miners_lantern_pick" -> {
                // Night vision underground
                if (player.getLocation().getBlockY() < 50) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 60, 0, false, false));
                } else if (!player.getWorld().hasStorm()) {
                    // Blindness in sunlight
                    long time = player.getWorld().getTime();
                    if (time < 12300 || time > 23850) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                    }
                }
            }

            case "peace_keeper" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
            }

            case "netheric_pick" -> {
                // Lava immunity while held
                if (player.getLocation().getBlock().getType() == Material.LAVA) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false));
                }
            }
        }
    }

    private void checkArmorEffects(Player player) {
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) continue;
            String itemId = itemManager.getExoticItemId(armor);
            if (itemId == null) continue;

            switch (itemId) {
                case "shadow_cloak" -> {
                    long time = player.getWorld().getTime();
                    boolean isNight = time >= 12300 && time <= 23850;
                    if (isNight) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
                    } else {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));
                    }
                }

                case "guardian_plate" -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
                }

                case "wanderer_boots" -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
                    // Disable sprint handled elsewhere
                }

                case "molten_helm" -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false));
                }

                case "ocean_helm" -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 40, 0, false, false));
                    if (player.isInWater()) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, false, false));
                    } else {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
                    }
                }

                // ========== INVISIBILITY CLOAKS ==========

                case "true_invisibility_cloak" -> {
                    // Permanent invisibility unless recently damaged
                    Long revealedUntil = trueInvisibilityRevealedUntil.get(player.getUniqueId());
                    boolean isRevealed = revealedUntil != null && System.currentTimeMillis() < revealedUntil;

                    if (!isRevealed) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
                        // Make mobs ignore the player
                        for (Entity e : player.getNearbyEntities(32, 16, 32)) {
                            if (e instanceof Mob mob && mob.getTarget() == player) {
                                mob.setTarget(null);
                            }
                        }
                    } else {
                        // Show particles when revealed
                        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0);
                    }
                }

                case "phantom_cloak" -> {
                    // Invisible unless sprinting
                    if (!player.isSprinting()) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
                    }
                    // Glow faintly at night
                    long time = player.getWorld().getTime();
                    boolean isNight = time >= 12300 && time <= 23850;
                    if (isNight) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                    }
                }

                case "thiefs_cloak" -> {
                    // Invisible only while sneaking
                    if (player.isSneaking()) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
                    }
                    // Glow when near NPCs (within 5 blocks)
                    boolean nearNpc = false;
                    for (Entity e : player.getNearbyEntities(5, 5, 5)) {
                        if (plugin.getNPCManager().getNPCFromEntity(e) != null) {
                            nearNpc = true;
                            break;
                        }
                    }
                    if (nearNpc) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                    }
                }

                case "cursed_cloak" -> {
                    // Full invisibility always
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));

                    // Drain 0.5 hearts every 3 seconds
                    Long lastDrain = cursedCloakLastDrain.get(player.getUniqueId());
                    if (lastDrain == null || System.currentTimeMillis() - lastDrain >= 3000) {
                        player.damage(1.0); // 0.5 hearts
                        cursedCloakLastDrain.put(player.getUniqueId(), System.currentTimeMillis());
                        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0.02);

                        // Cannot be removed while below 3 hearts
                        if (player.getHealth() <= 6) {
                            player.sendMessage(ChatColor.DARK_RED + "The cursed cloak clings to you... you cannot remove it!");
                        }
                    }
                }
            }
        }
    }

    private void checkSacrificialKnife(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String itemId = itemManager.getExoticItemId(held);
        if (!"sacrificial_knife".equals(itemId)) return;

        Long lastKill = sacrificialLastKill.get(player.getUniqueId());
        if (lastKill == null) {
            sacrificialLastKill.put(player.getUniqueId(), System.currentTimeMillis());
            return;
        }

        // Must kill once per minute
        if (System.currentTimeMillis() - lastKill > 60000) {
            player.damage(2.0);
            player.sendMessage(ChatColor.DARK_RED + "The sacrificial knife demands blood...");
            sacrificialLastKill.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    private void checkChaosOrb(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            String itemId = itemManager.getExoticItemId(item);
            if ("chaos_orb".equals(itemId)) {
                Long lastEffect = chaosOrbCooldown.get(player.getUniqueId());
                if (lastEffect == null || System.currentTimeMillis() - lastEffect > 30000) {
                    applyRandomPotionEffect(player);
                    chaosOrbCooldown.put(player.getUniqueId(), System.currentTimeMillis());
                }
                break;
            }
        }
    }

    private void applyRandomPotionEffect(Player player) {
        PotionEffectType[] effects = {
                PotionEffectType.SPEED, PotionEffectType.SLOWNESS, PotionEffectType.STRENGTH,
                PotionEffectType.REGENERATION, PotionEffectType.JUMP_BOOST, PotionEffectType.INVISIBILITY,
                PotionEffectType.NIGHT_VISION, PotionEffectType.POISON, PotionEffectType.LUCK
        };
        PotionEffectType effect = effects[random.nextInt(effects.length)];
        player.addPotionEffect(new PotionEffect(effect, 200, 0));
        player.sendMessage(ChatColor.LIGHT_PURPLE + "The Chaos Orb grants you " +
                effect.getName().toLowerCase().replace('_', ' ') + "!");
    }

    // ========== SPECIAL INTERACTIONS ==========

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT")) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        String itemId = itemManager.getExoticItemId(item);
        if (itemId == null) return;

        ExoticItem exoticItem = itemManager.getItem(itemId);
        if (exoticItem == null) return;

        // Handle throwables
        if (exoticItem.getCategory() == ItemCategory.THROWABLE) {
            throwExoticItem(player, itemId, item);
            event.setCancelled(true);
        }

        // Handle consumables
        if (exoticItem.getCategory() == ItemCategory.CONSUMABLE) {
            consumeExoticItem(player, itemId, item);
            event.setCancelled(true);
        }
    }

    private void throwExoticItem(Player player, String itemId, ItemStack item) {
        // Reduce item count
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }

        Location loc = player.getEyeLocation();
        org.bukkit.util.Vector direction = loc.getDirection();

        switch (itemId) {
            case "faction_bomb" -> {
                // Spawn projectile
                Snowball projectile = player.launchProjectile(Snowball.class);
                projectile.setCustomName("faction_bomb");

                // Schedule explosion effect
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!projectile.isValid()) return;
                        Location impact = projectile.getLocation();

                        // Damage only hostile NPCs
                        for (Entity e : impact.getNearbyEntities(3, 3, 3)) {
                            AINpc npc = plugin.getNPCManager().getNPCFromEntity(e);
                            if (npc != null && plugin.getFactionManager().areHostile(
                                    "Villagers", npc.getFaction())) {
                                if (e instanceof LivingEntity living) {
                                    living.damage(10.0, player);
                                }
                            }
                        }
                        impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 1);
                        projectile.remove();
                    }
                }.runTaskLater(plugin, 40L);
            }

            case "smoke_bomb" -> {
                Snowball projectile = player.launchProjectile(Snowball.class);
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (!projectile.isValid() || ticks > 20) {
                            Location impact = projectile.getLocation();
                            // Create smoke cloud
                            for (int i = 0; i < 50; i++) {
                                impact.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                                        impact.clone().add(random.nextDouble() * 4 - 2, random.nextDouble() * 2,
                                                random.nextDouble() * 4 - 2), 1);
                            }
                            // Grant invisibility
                            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0));
                            projectile.remove();
                            cancel();
                            return;
                        }
                        ticks++;
                    }
                }.runTaskTimer(plugin, 1L, 1L);
            }

            case "frost_bomb" -> {
                Snowball projectile = player.launchProjectile(Snowball.class);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!projectile.isValid()) return;
                        Location impact = projectile.getLocation();

                        // Freeze area
                        for (Entity e : impact.getNearbyEntities(4, 4, 4)) {
                            if (e instanceof LivingEntity living) {
                                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
                                living.setFreezeTicks(60);
                            }
                        }
                        // Ice floor
                        for (int x = -2; x <= 2; x++) {
                            for (int z = -2; z <= 2; z++) {
                                Block b = impact.clone().add(x, -1, z).getBlock();
                                if (b.getType() == Material.WATER) {
                                    b.setType(Material.ICE);
                                }
                            }
                        }
                        impact.getWorld().spawnParticle(Particle.SNOWFLAKE, impact, 50, 2, 2, 2);
                        projectile.remove();
                    }
                }.runTaskLater(plugin, 20L);
            }

            case "chain_lightning" -> {
                // Strike initial location
                Location target = player.getTargetBlock(null, 50).getLocation();
                target.getWorld().strikeLightningEffect(target);

                // Chain to nearby enemies
                List<Entity> hit = new ArrayList<>();
                Location current = target;
                for (int i = 0; i < 5; i++) {
                    Entity closest = null;
                    double closestDist = 10;
                    for (Entity e : current.getNearbyEntities(10, 10, 10)) {
                        if (!(e instanceof LivingEntity) || hit.contains(e) || e == player) continue;
                        double dist = e.getLocation().distance(current);
                        if (dist < closestDist) {
                            closest = e;
                            closestDist = dist;
                        }
                    }
                    if (closest != null) {
                        hit.add(closest);
                        closest.getWorld().strikeLightningEffect(closest.getLocation());
                        if (closest instanceof LivingEntity living) {
                            living.damage(6.0, player);
                        }
                        current = closest.getLocation();
                    }
                }
                // Chance to chain to self
                if (random.nextDouble() < 0.1 && player.getLocation().distance(current) < 10) {
                    player.damage(6.0);
                    player.sendMessage(ChatColor.YELLOW + "The lightning chained back to you!");
                }
            }

            case "gravity_orb" -> {
                Location target = player.getTargetBlock(null, 30).getLocation().add(0, 1, 0);
                target.getWorld().spawnParticle(Particle.PORTAL, target, 100, 2, 2, 2);

                // Pull entities
                for (Entity e : target.getNearbyEntities(8, 8, 8)) {
                    if (e instanceof LivingEntity) {
                        org.bukkit.util.Vector pull = target.toVector().subtract(e.getLocation().toVector())
                                .normalize().multiply(0.8);
                        e.setVelocity(pull);
                        ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 0));
                    }
                }
            }
        }
    }

    private void consumeExoticItem(Player player, String itemId, ItemStack item) {
        switch (itemId) {
            case "berserker_brew" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 2400, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 2400, 1));
                // Blindness after
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0));
                    }
                }.runTaskLater(plugin, 2400L);
                consumeItem(player, item);
            }

            case "healing_salve" -> {
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 20));
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    if (effect.getType().equals(PotionEffectType.POISON) ||
                            effect.getType().equals(PotionEffectType.WITHER) ||
                            effect.getType().equals(PotionEffectType.SLOWNESS)) {
                        player.removePotionEffect(effect.getType());
                    }
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 600, 0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 1200, 2));
                consumeItem(player, item);
            }

            case "void_essence" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 4)); // Near invulnerable
                player.setMaxHealth(player.getMaxHealth() * 0.5); // Reduce max health temporarily
                player.sendMessage(ChatColor.DARK_PURPLE + "You phase partially into the void...");
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        player.setMaxHealth(20.0);
                        player.sendMessage(ChatColor.GREEN + "Your connection to reality stabilizes.");
                    }
                }.runTaskLater(plugin, 72000L); // 1 hour
                consumeItem(player, item);
            }

            case "memory_potion", "faction_elixir" -> {
                player.sendMessage(ChatColor.YELLOW + "Use this on an NPC by right-clicking them!");
            }
        }
    }

    private void consumeItem(Player player, ItemStack item) {
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().remove(item);
        }
    }

    // ========== MOVEMENT EFFECTS ==========

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Check for featherfall boots double jump
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) continue;
            String itemId = itemManager.getExoticItemId(armor);
            if ("featherfall_boots".equals(itemId)) {
                // No fall damage is handled by Minecraft's enchantment
                // Double jump would require more complex handling
            }

            // Frost edge freezes water
            ItemStack held = player.getInventory().getItemInMainHand();
            String heldId = itemManager.getExoticItemId(held);
            if ("frost_edge".equals(heldId)) {
                Block below = player.getLocation().add(0, -1, 0).getBlock();
                if (below.getType() == Material.WATER) {
                    below.setType(Material.FROSTED_ICE);
                }
            }
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        // Apply/remove effects when switching items
        Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                applyPassiveEffects(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    // ========== HELPER METHODS ==========

    private boolean isOre(Material mat) {
        String name = mat.name();
        return name.contains("_ORE");
    }

    private boolean isLog(Material mat) {
        String name = mat.name();
        return name.contains("_LOG") || name.contains("_WOOD");
    }

    private boolean isShovelBlock(Material mat) {
        return mat == Material.DIRT || mat == Material.GRASS_BLOCK || mat == Material.SAND ||
                mat == Material.GRAVEL || mat == Material.SOUL_SAND || mat == Material.CLAY;
    }

    private boolean isPickaxeBlock(Material mat) {
        String name = mat.name();
        return name.contains("STONE") || name.contains("COBBLE") || name.contains("BRICK") ||
                name.contains("DEEPSLATE") || name.contains("NETHERRACK");
    }

    private ItemStack getSmeltedOre(Material ore) {
        return switch (ore) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new ItemStack(Material.IRON_INGOT);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE -> new ItemStack(Material.GOLD_INGOT);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new ItemStack(Material.COPPER_INGOT);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP);
            default -> null;
        };
    }

    private Material getSaplingForLog(Material log) {
        String name = log.name();
        if (name.contains("OAK")) return Material.OAK_SAPLING;
        if (name.contains("BIRCH")) return Material.BIRCH_SAPLING;
        if (name.contains("SPRUCE")) return Material.SPRUCE_SAPLING;
        if (name.contains("JUNGLE")) return Material.JUNGLE_SAPLING;
        if (name.contains("ACACIA")) return Material.ACACIA_SAPLING;
        if (name.contains("DARK_OAK")) return Material.DARK_OAK_SAPLING;
        return null;
    }

    private void breakArea(Player player, Block center, int size, ItemStack tool) {
        int half = size / 2;
        for (int x = -half; x <= half; x++) {
            for (int y = -half; y <= half; y++) {
                for (int z = -half; z <= half; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Block b = center.getRelative(x, y, z);
                    if (b.getType() != Material.AIR && b.getType() != Material.BEDROCK) {
                        b.breakNaturally(tool);
                    }
                }
            }
        }
    }

    private void breakConnectedLogs(Player player, Block start, ItemStack tool) {
        Set<Block> toBreak = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty() && toBreak.size() < 64) {
            Block current = queue.poll();
            if (toBreak.contains(current)) continue;
            if (!isLog(current.getType())) continue;

            toBreak.add(current);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        queue.add(current.getRelative(x, y, z));
                    }
                }
            }
        }

        for (Block b : toBreak) {
            if (b != start) b.breakNaturally(tool);
        }
    }

    private void breakConnectedOres(Player player, Block start, ItemStack tool, Material oreType) {
        Set<Block> toBreak = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty() && toBreak.size() < 64) {
            Block current = queue.poll();
            if (toBreak.contains(current)) continue;
            if (current.getType() != oreType) continue;

            toBreak.add(current);
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;
                        queue.add(current.getRelative(x, y, z));
                    }
                }
            }
        }

        for (Block b : toBreak) {
            if (b != start) b.breakNaturally(tool);
        }
    }
}
