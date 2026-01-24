package com.aicraft.listeners;

import com.aicraft.AICompanions;
import com.aicraft.npcs.boss.BossLootManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Handles special abilities from legendary boss items
 */
public class LegendaryItemListener implements Listener {

    private final AICompanions plugin;
    private final BossLootManager lootManager;
    private final Random random = new Random();

    // Cooldowns for abilities (player UUID -> ability -> last use time)
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    // Set of blocks currently being broken by multi-mine to prevent recursion
    private final Set<Location> multiMineActive = new HashSet<>();

    public LegendaryItemListener(AICompanions plugin, BossLootManager lootManager) {
        this.plugin = plugin;
        this.lootManager = lootManager;
    }

    /**
     * Handle block breaking for multi-mine pickaxe (3x3 mining)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        String ability = lootManager.getItemAbility(item);
        if (!"multi_mine".equals(ability)) return;

        Block center = event.getBlock();

        // Prevent recursive calls
        if (multiMineActive.contains(center.getLocation())) return;

        // Only works on mineable blocks
        if (!isMineable(center.getType())) return;

        multiMineActive.add(center.getLocation());

        // Get the face the player is looking at to determine the plane
        Location eyeLoc = player.getEyeLocation();
        org.bukkit.util.Vector direction = eyeLoc.getDirection();

        // Determine which axis is most perpendicular to break in a 3x3
        int[][] offsets = get3x3Offsets(direction);

        // Break surrounding blocks
        for (int[] offset : offsets) {
            Block target = center.getRelative(offset[0], offset[1], offset[2]);

            if (target.equals(center)) continue;
            if (!isMineable(target.getType())) continue;
            if (target.getType() == Material.BEDROCK) continue;

            Location targetLoc = target.getLocation();
            if (multiMineActive.contains(targetLoc)) continue;

            multiMineActive.add(targetLoc);

            // Break the block and drop items
            target.breakNaturally(item);
        }

        // Clear after a tick to allow drops
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            multiMineActive.remove(center.getLocation());
            for (int[] offset : offsets) {
                Block target = center.getRelative(offset[0], offset[1], offset[2]);
                multiMineActive.remove(target.getLocation());
            }
        }, 1L);

        // Visual effect
        player.getWorld().spawnParticle(Particle.BLOCK, center.getLocation().add(0.5, 0.5, 0.5),
            30, 1, 1, 1, center.getBlockData());
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 0.5f, 1.5f);
    }

    /**
     * Get 3x3 offsets based on player facing direction
     */
    private int[][] get3x3Offsets(org.bukkit.util.Vector direction) {
        double absX = Math.abs(direction.getX());
        double absY = Math.abs(direction.getY());
        double absZ = Math.abs(direction.getZ());

        // Determine dominant axis
        if (absY > absX && absY > absZ) {
            // Looking up/down - break horizontal plane
            return new int[][] {
                {-1, 0, -1}, {0, 0, -1}, {1, 0, -1},
                {-1, 0, 0},  {0, 0, 0},  {1, 0, 0},
                {-1, 0, 1},  {0, 0, 1},  {1, 0, 1}
            };
        } else if (absX > absZ) {
            // Looking east/west - break YZ plane
            return new int[][] {
                {0, -1, -1}, {0, 0, -1}, {0, 1, -1},
                {0, -1, 0},  {0, 0, 0},  {0, 1, 0},
                {0, -1, 1},  {0, 0, 1},  {0, 1, 1}
            };
        } else {
            // Looking north/south - break XY plane
            return new int[][] {
                {-1, -1, 0}, {0, -1, 0}, {1, -1, 0},
                {-1, 0, 0},  {0, 0, 0},  {1, 0, 0},
                {-1, 1, 0},  {0, 1, 0},  {1, 1, 0}
            };
        }
    }

    /**
     * Check if a block is mineable
     */
    private boolean isMineable(Material mat) {
        return mat.isBlock() && mat.getHardness() >= 0 && mat != Material.AIR &&
               mat != Material.WATER && mat != Material.LAVA && mat != Material.BEDROCK;
    }

    /**
     * Handle attack abilities (Valley Justice, Bloodthirst, etc.)
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        String ability = lootManager.getItemAbility(item);
        if (ability == null) return;

        Entity victim = event.getEntity();
        double damage = event.getFinalDamage();

        switch (ability) {
            case "valley_justice" -> {
                // El Bronco's Machete - inflict wither
                if (victim instanceof LivingEntity living) {
                    living.addPotionEffect(new PotionEffect(
                        PotionEffectType.WITHER, 100, 2, false, true));
                    player.getWorld().spawnParticle(Particle.SMOKE,
                        victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
                }
            }
            case "bloodthirst" -> {
                // Chupacabra Fang - lifesteal
                double healAmount = damage * 0.5;
                double newHealth = Math.min(player.getHealth() + healAmount, player.getMaxHealth());
                player.setHealth(newHealth);
                player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR,
                    player.getLocation().add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.5f, 0.5f);
            }
            case "undertow" -> {
                // Soul Dragger - pull enemies
                if (victim instanceof LivingEntity living) {
                    org.bukkit.util.Vector pull = player.getLocation().toVector()
                        .subtract(victim.getLocation().toVector()).normalize().multiply(1.5);
                    pull.setY(0.3);
                    victim.setVelocity(pull);
                    player.getWorld().spawnParticle(Particle.FALLING_WATER,
                        victim.getLocation(), 30, 0.5, 1, 0.5, 0);
                }
            }
        }
    }

    /**
     * Handle right-click abilities
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().name().contains("RIGHT")) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        String ability = lootManager.getItemAbility(item);
        if (ability == null) return;

        // Check cooldown
        if (isOnCooldown(player, ability)) {
            long remaining = getCooldownRemaining(player, ability) / 1000;
            player.sendMessage(ChatColor.RED + "Ability on cooldown: " + remaining + "s");
            return;
        }

        switch (ability) {
            case "mothers_grief" -> {
                // La Llorona's Tears - AoE sadness
                setCooldown(player, ability, 30000); // 30s cooldown

                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 2.0f, 0.5f);
                player.sendMessage(ChatColor.DARK_AQUA + "You unleash the sorrow of La Llorona...");

                for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
                    if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 2));
                        living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
                    }
                }

                // Crying particle effect
                for (int i = 0; i < 50; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double radius = random.nextDouble() * 10;
                    Location particleLoc = player.getLocation().add(
                        Math.cos(angle) * radius, random.nextDouble() * 3, Math.sin(angle) * radius);
                    player.getWorld().spawnParticle(Particle.FALLING_WATER, particleLoc, 1);
                }
            }

            case "ore_reveal" -> {
                // Don Cuco's Ledger - reveal ores
                setCooldown(player, ability, 60000); // 60s cooldown

                player.sendMessage(ChatColor.GOLD + "The ledger reveals hidden treasures...");

                Location center = player.getLocation();
                int revealed = 0;

                for (int x = -15; x <= 15; x++) {
                    for (int y = -15; y <= 15; y++) {
                        for (int z = -15; z <= 15; z++) {
                            Block block = center.clone().add(x, y, z).getBlock();
                            if (isOre(block.getType())) {
                                // Glowing particles at ore locations
                                player.spawnParticle(Particle.GLOW, block.getLocation().add(0.5, 0.5, 0.5),
                                    3, 0.2, 0.2, 0.2, 0);
                                revealed++;
                            }
                        }
                    }
                }

                player.sendMessage(ChatColor.YELLOW + "Revealed " + revealed + " ore deposits!");
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
            }

            case "chaos_roll" -> {
                // El Diablito's Dice - random effect
                setCooldown(player, ability, 15000); // 15s cooldown

                player.sendMessage(ChatColor.RED + "Rolling the dice of chaos...");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.5f);

                // Delay for dramatic effect
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    applyChaosEffect(player);
                }, 20L);
            }

            case "goat_power" -> {
                // Goat Soul Essence - regeneration
                setCooldown(player, ability, 300000); // 5 min cooldown

                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 6000, 2));
                player.sendMessage(ChatColor.GREEN + "The power of a thousand goats flows through you!");
                player.playSound(player.getLocation(), Sound.ENTITY_GOAT_SCREAMING_AMBIENT, 2.0f, 0.5f);

                // Consume the item
                item.setAmount(item.getAmount() - 1);
            }
        }
    }

    /**
     * Handle passive abilities (smuggler boots, emotional damage aura, etc.)
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Check boots for smuggler speed
        ItemStack boots = player.getInventory().getBoots();
        if (boots != null) {
            String ability = lootManager.getItemAbility(boots);
            if ("smuggler_speed".equals(ability)) {
                // Permanent speed boost
                if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, 100, 1, false, false));
                }
                // Invisible while sneaking
                if (player.isSneaking() && !player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY, 40, 0, false, false));
                }
            }
        }

        // Check helmet for emotional damage aura
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet != null) {
            String ability = lootManager.getItemAbility(helmet);
            if ("emotional_damage".equals(ability)) {
                // Weakness aura to nearby mobs
                for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
                    if (entity instanceof Monster monster) {
                        if (!monster.hasPotionEffect(PotionEffectType.WEAKNESS)) {
                            monster.addPotionEffect(new PotionEffect(
                                PotionEffectType.WEAKNESS, 60, 1, false, false));
                        }
                    }
                }
            }
            if ("mischief_aura".equals(ability)) {
                // Make mobs attack each other randomly
                List<Monster> nearbyMobs = new ArrayList<>();
                for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
                    if (entity instanceof Monster m) nearbyMobs.add(m);
                }
                if (nearbyMobs.size() >= 2 && random.nextDouble() < 0.05) {
                    Monster attacker = nearbyMobs.get(random.nextInt(nearbyMobs.size()));
                    Monster target = nearbyMobs.get(random.nextInt(nearbyMobs.size()));
                    if (attacker != target) {
                        attacker.setTarget(target);
                    }
                }
            }
        }
    }

    /**
     * Apply a random chaos effect from El Diablito's dice
     */
    private void applyChaosEffect(Player player) {
        int roll = random.nextInt(20) + 1;

        player.sendMessage(ChatColor.RED + "You rolled a " + ChatColor.GOLD + roll + ChatColor.RED + "!");

        switch (roll) {
            case 1 -> {
                // Critical fail - bad things happen
                player.sendMessage(ChatColor.DARK_RED + "CRITICAL FAIL! La mala suerte!");
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 200, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 2));
                player.getWorld().strikeLightningEffect(player.getLocation());
            }
            case 2, 3, 4 -> {
                player.sendMessage(ChatColor.RED + "You feel weaker...");
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 400, 1));
            }
            case 5, 6, 7 -> {
                player.sendMessage(ChatColor.RED + "You feel sluggish...");
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, 1));
            }
            case 8, 9, 10 -> {
                player.sendMessage(ChatColor.YELLOW + "Nothing happens... how boring.");
            }
            case 11, 12, 13 -> {
                player.sendMessage(ChatColor.GREEN + "You feel a bit stronger!");
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 0));
            }
            case 14, 15, 16 -> {
                player.sendMessage(ChatColor.GREEN + "Speed boost!");
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 400, 1));
            }
            case 17, 18, 19 -> {
                player.sendMessage(ChatColor.AQUA + "The dice favor you!");
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
            }
            case 20 -> {
                // Natural 20 - amazing things happen
                player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "NATURAL 20! SUERTE INCREIBLE!");
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 600, 2));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 600, 0));

                // Firework celebration
                Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
                fw.setFireworkMeta(fw.getFireworkMeta());
                fw.detonate();
            }
        }
    }

    /**
     * Check if a material is an ore
     */
    private boolean isOre(Material mat) {
        String name = mat.name().toLowerCase();
        return name.contains("ore") || name.contains("ancient_debris");
    }

    // Cooldown management
    private boolean isOnCooldown(Player player, String ability) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return false;
        Long lastUse = playerCooldowns.get(ability);
        if (lastUse == null) return false;
        return System.currentTimeMillis() - lastUse < getCooldownDuration(ability);
    }

    private long getCooldownRemaining(Player player, String ability) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;
        Long lastUse = playerCooldowns.get(ability);
        if (lastUse == null) return 0;
        long elapsed = System.currentTimeMillis() - lastUse;
        return Math.max(0, getCooldownDuration(ability) - elapsed);
    }

    private long getCooldownDuration(String ability) {
        return switch (ability) {
            case "mothers_grief" -> 30000;
            case "ore_reveal" -> 60000;
            case "chaos_roll" -> 15000;
            case "goat_power" -> 300000;
            default -> 10000;
        };
    }

    private void setCooldown(Player player, String ability, long duration) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(ability, System.currentTimeMillis());
    }
}
