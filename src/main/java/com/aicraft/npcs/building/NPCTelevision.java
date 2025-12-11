package com.aicraft.npcs.building;

import com.aicraft.AICompanions;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages NPC televisions that "play" content when players are nearby.
 * TVs are built as black concrete blocks with glowstone and display
 * what the NPC is watching via chat messages and action bar.
 */
public class NPCTelevision {

    private final AICompanions plugin;
    private final NPCManager npcManager;

    // Track TV locations and their owners
    private final Map<Location, UUID> tvLocations = new ConcurrentHashMap<>();

    // Track what each TV is currently "playing"
    private final Map<Location, TVChannel> currentChannels = new ConcurrentHashMap<>();

    // Track players watching TVs
    private final Map<UUID, Location> playersWatchingTV = new ConcurrentHashMap<>();

    private BukkitTask tvTask;
    private final Random random = new Random();

    // Simulated YouTube-style video titles
    private static final String[][] VIDEO_CONTENT = {
            // Gaming channel
            {
                    "EPIC Minecraft Castle Build Tutorial (GONE WRONG)",
                    "I Survived 100 Days in Hardcore Minecraft",
                    "Why Creepers Explode - The REAL Story",
                    "Speedrunning Minecraft in Under 10 Minutes",
                    "Building a GIANT Redstone Computer",
                    "How to Find Diamonds FAST in 1.20",
                    "I Built the ENTIRE Overworld in One Block",
                    "Villager Trading Hall - INFINITE Emeralds"
            },
            // Cooking channel
            {
                    "Gordon Ramsey Makes Suspicious Stew",
                    "5-Minute Pumpkin Pie Recipe",
                    "How to Cook Steak (Golden Apple Edition)",
                    "Baking Bread: A Minecraft Cookbook",
                    "The BEST Cake Recipe - Trust Me",
                    "Making Mushroom Stew in the Nether"
            },
            // News channel
            {
                    "BREAKING: Ender Dragon Spotted Near Village",
                    "Weather Report: Thunderstorm Expected Tonight",
                    "Local Villager Wins Emerald Lottery",
                    "Iron Golem Saves Child from Zombie Attack",
                    "Pillager Raid Warning for Eastern Biomes",
                    "New Trade Route Opens to Nether Fortress"
            },
            // Drama/Reality TV
            {
                    "The Real Housewives of Villager Plains",
                    "Keeping Up with the Pillagers",
                    "90 Day Villager: Trading Edition",
                    "Love Island: Mushroom Biome",
                    "Survivor: Deep Dark Challenge",
                    "The Bachelor: Find Your Perfect Villager"
            },
            // Documentary
            {
                    "Planet Overworld: Life in the Taiga",
                    "The Migration of the Wandering Trader",
                    "Deep Ocean Mysteries: The Elder Guardian",
                    "Inside the Mind of an Enderman",
                    "Wither Skeletons: A Documentary",
                    "The Lost Civilization of the Ancient Cities"
            },
            // Music/Entertainment
            {
                    "Top 10 Noteblock Songs of All Time",
                    "LIVE: Disc 11 Full Analysis",
                    "Cat vs Pigstep - Which is Better?",
                    "Making Beats with Noteblocks Tutorial",
                    "The Story Behind 'Stal' - Music Theory"
            },
            // Cultist Channel (special for Cultist NPCs)
            {
                    "Geodjian's Divine Teachings - Episode 47",
                    "Meditation with the All-Seer",
                    "Converting Your Friends to the Truth",
                    "Sacred Rituals for Beginners",
                    "Why Geodjian Watches Over Us All",
                    "The Prophecy Explained (FINALLY)",
                    "Deep Cave Worship Sessions - LIVE"
            }
    };

    // TV dialogue - what NPCs say while watching
    private static final String[] TV_REACTIONS = {
            "*laughs at the TV*",
            "*gasps* \"No way!\"",
            "\"I love this part...\"",
            "*leans forward intently*",
            "\"This is my favorite show!\"",
            "*munches on snacks*",
            "\"I've seen this one before...\"",
            "*yawns but keeps watching*",
            "\"Turn it up!\"",
            "*shakes head at the screen*",
            "\"Classic episode.\"",
            "*nods in agreement*"
    };

    private static final String[] CULTIST_TV_REACTIONS = {
            "*nods solemnly* \"Truth...\"",
            "\"Geodjian speaks through this...\"",
            "*makes a sacred gesture*",
            "\"The prophecy unfolds...\"",
            "*whispers prayers*",
            "\"All will see eventually...\"",
            "*eyes glow faintly*"
    };

    public NPCTelevision(AICompanions plugin, NPCManager npcManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
    }

    /**
     * Start the TV system
     */
    public void start() {
        int tickRate = plugin.getConfig().getInt("npcs.television.tick-rate", 100); // 5 seconds default

        tvTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickTVs();
            }
        }.runTaskTimer(plugin, tickRate, tickRate);

        plugin.getLogger().info("NPC Television system started!");
    }

    /**
     * Stop the TV system
     */
    public void stop() {
        if (tvTask != null) {
            tvTask.cancel();
            tvTask = null;
        }
    }

    /**
     * Build a TV at a location for an NPC
     */
    public void buildTV(Location location, AINpc npc, BlockFace facing) {
        World world = location.getWorld();
        if (world == null) return;

        // TV is 2 blocks wide, 2 blocks tall
        // Structure: Black concrete with sea lantern "screen"
        Block base = location.getBlock();

        // Determine the perpendicular direction for width
        BlockFace left, right;
        switch (facing) {
            case NORTH:
                left = BlockFace.WEST;
                right = BlockFace.EAST;
                break;
            case SOUTH:
                left = BlockFace.EAST;
                right = BlockFace.WEST;
                break;
            case EAST:
                left = BlockFace.NORTH;
                right = BlockFace.SOUTH;
                break;
            case WEST:
            default:
                left = BlockFace.SOUTH;
                right = BlockFace.NORTH;
                break;
        }

        // Build TV frame (2x2)
        Material frameMaterial = Material.BLACK_CONCRETE;
        Material screenMaterial = Material.SEA_LANTERN;

        // Bottom row - frame
        base.setType(frameMaterial);
        base.getRelative(right).setType(frameMaterial);

        // Top row - screen
        Block topLeft = base.getRelative(BlockFace.UP);
        Block topRight = topLeft.getRelative(right);
        topLeft.setType(screenMaterial);
        topRight.setType(screenMaterial);

        // Add "antenna" on top (for retro look)
        Block antenna = topLeft.getRelative(BlockFace.UP);
        antenna.setType(Material.LIGHTNING_ROD);

        // Register TV location (use the sea lantern as the main location)
        Location tvLoc = topLeft.getLocation();
        tvLocations.put(tvLoc, npc.getUuid());

        // Set initial channel based on NPC faction
        TVChannel channel = getChannelForFaction(npc.getFaction());
        currentChannels.put(tvLoc, channel);

        plugin.debug("Built TV for " + npc.getName() + " at " + tvLoc);
    }

    /**
     * Get appropriate channel for faction
     */
    private TVChannel getChannelForFaction(String faction) {
        if (faction == null) return new TVChannel(0, "Gaming");

        switch (faction.toLowerCase()) {
            case "cultists":
                return new TVChannel(6, "Geodjian Broadcasting Network");
            case "villagers":
            case "merchants":
                return new TVChannel(random.nextInt(5), getRandomChannelName());
            case "raiders":
            case "bandits":
                return new TVChannel(3, "Reality TV"); // Drama lovers
            case "guards":
            case "soldiers":
                return new TVChannel(2, "News Network");
            default:
                return new TVChannel(random.nextInt(6), getRandomChannelName());
        }
    }

    private String getRandomChannelName() {
        String[] names = {"MineTube", "BlockFlix", "CraftTV", "Overworld News", "Village Network"};
        return names[random.nextInt(names.length)];
    }

    /**
     * Process TV ticks - show content to nearby players
     */
    private void tickTVs() {
        double viewDistance = plugin.getConfig().getDouble("npcs.television.view-distance", 8.0);

        for (Map.Entry<Location, UUID> entry : tvLocations.entrySet()) {
            Location tvLoc = entry.getKey();
            UUID npcId = entry.getValue();

            if (tvLoc.getWorld() == null) continue;

            // Find nearby players
            for (Entity entity : tvLoc.getWorld().getNearbyEntities(tvLoc, viewDistance, viewDistance, viewDistance)) {
                if (!(entity instanceof Player)) continue;
                Player player = (Player) entity;

                // Check line of sight (roughly)
                if (!player.hasLineOfSight(tvLoc)) continue;

                // Show TV content
                showTVContent(player, tvLoc, npcId);
            }
        }

        // Occasionally change channels or show NPC reactions
        if (random.nextInt(4) == 0) {
            showRandomNPCReaction();
        }
    }

    /**
     * Show TV content to a player
     */
    private void showTVContent(Player player, Location tvLoc, UUID npcId) {
        TVChannel channel = currentChannels.get(tvLoc);
        if (channel == null) return;

        AINpc npc = npcManager.getNPC(npcId);
        String npcName = npc != null ? npc.getName() : "Someone";

        // Get current video
        String[] videos = VIDEO_CONTENT[channel.categoryIndex];
        String currentVideo = videos[random.nextInt(videos.length)];

        // Show action bar with current "video"
        String actionBarMsg = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "\u25B6" +
                ChatColor.DARK_GRAY + "] " + ChatColor.WHITE + currentVideo;
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionBarMsg));

        // Occasionally show the channel name
        if (random.nextInt(10) == 0) {
            player.sendMessage(ChatColor.DARK_GRAY + "[TV - " + channel.channelName + "]");
        }

        // Track that player is watching
        playersWatchingTV.put(player.getUniqueId(), tvLoc);
    }

    /**
     * Show a random NPC reacting to TV
     */
    private void showRandomNPCReaction() {
        if (tvLocations.isEmpty()) return;

        // Pick random TV
        List<Map.Entry<Location, UUID>> entries = new ArrayList<>(tvLocations.entrySet());
        Map.Entry<Location, UUID> entry = entries.get(random.nextInt(entries.size()));

        Location tvLoc = entry.getKey();
        UUID npcId = entry.getValue();
        AINpc npc = npcManager.getNPC(npcId);

        if (npc == null || !npc.isAlive()) return;

        // Get reaction based on faction
        String reaction;
        if ("cultists".equalsIgnoreCase(npc.getFaction())) {
            reaction = CULTIST_TV_REACTIONS[random.nextInt(CULTIST_TV_REACTIONS.length)];
        } else {
            reaction = TV_REACTIONS[random.nextInt(TV_REACTIONS.length)];
        }

        // Broadcast to nearby players
        double viewDistance = plugin.getConfig().getDouble("npcs.television.view-distance", 8.0);
        if (tvLoc.getWorld() != null) {
            for (Entity entity : tvLoc.getWorld().getNearbyEntities(tvLoc, viewDistance, viewDistance, viewDistance)) {
                if (entity instanceof Player) {
                    Player player = (Player) entity;
                    player.sendMessage(ChatColor.GRAY + npc.getName() + " " + reaction);
                }
            }
        }
    }

    /**
     * Change the channel on a TV
     */
    public void changeChannel(Location tvLoc, int categoryIndex) {
        if (!tvLocations.containsKey(tvLoc)) return;

        int safeIndex = Math.max(0, Math.min(categoryIndex, VIDEO_CONTENT.length - 1));
        TVChannel channel = new TVChannel(safeIndex, getRandomChannelName());
        currentChannels.put(tvLoc, channel);

        // Notify nearby players
        if (tvLoc.getWorld() != null) {
            for (Entity entity : tvLoc.getWorld().getNearbyEntities(tvLoc, 8, 8, 8)) {
                if (entity instanceof Player) {
                    Player player = (Player) entity;
                    player.sendMessage(ChatColor.DARK_GRAY + "*click* " +
                            ChatColor.GRAY + "Channel changed to " + channel.channelName);
                }
            }
        }
    }

    /**
     * Remove a TV
     */
    public void removeTV(Location tvLoc) {
        tvLocations.remove(tvLoc);
        currentChannels.remove(tvLoc);
    }

    /**
     * Get all TV locations
     */
    public Set<Location> getTVLocations() {
        return new HashSet<>(tvLocations.keySet());
    }

    /**
     * Check if location has a TV
     */
    public boolean hasTV(Location location) {
        return tvLocations.containsKey(location);
    }

    /**
     * Get NPC owner of TV
     */
    public UUID getTVOwner(Location location) {
        return tvLocations.get(location);
    }

    /**
     * Inner class to track channel info
     */
    private static class TVChannel {
        final int categoryIndex;
        final String channelName;

        TVChannel(int categoryIndex, String channelName) {
            this.categoryIndex = categoryIndex;
            this.channelName = channelName;
        }
    }
}
