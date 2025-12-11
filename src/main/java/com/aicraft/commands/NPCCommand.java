package com.aicraft.commands;

import com.aicraft.AICompanions;
import com.aicraft.ai.AIManager;
import com.aicraft.factions.Faction;
import com.aicraft.factions.FactionManager;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for NPC management
 */
public class NPCCommand implements CommandExecutor, TabCompleter {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final AIManager aiManager;
    private final FactionManager factionManager;

    public NPCCommand(AICompanions plugin, NPCManager npcManager, AIManager aiManager, FactionManager factionManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.aiManager = aiManager;
        this.factionManager = factionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(player, args);
            case "random" -> handleRandom(player, args);
            case "populate", "spawn" -> handlePopulate(player, args);
            case "remove", "delete" -> handleRemove(player, args);
            case "list" -> handleList(player, args);
            case "info" -> handleInfo(player, args);
            case "teleport", "tp" -> handleTeleport(player, args);
            case "near", "nearby" -> handleNearby(player);
            case "setpersonality" -> handleSetPersonality(player, args);
            case "setfaction" -> handleSetFaction(player, args);
            case "regenerate" -> handleRegenerate(player, args);
            case "clear" -> handleClear(player, args);
            case "clearall", "purge", "wipe" -> handleClearAll(player);
            case "cleardead" -> handleClearDead(player);
            case "respawnall" -> handleRespawnAll(player);
            case "killall" -> handleKillAll(player);
            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══════ AICompanions NPC Commands ═══════");
        player.sendMessage(ChatColor.YELLOW + "/npc create <name> [faction]" + ChatColor.GRAY + " - Create an NPC");
        player.sendMessage(ChatColor.YELLOW + "/npc random [faction]" + ChatColor.GRAY + " - Create random NPC");
        player.sendMessage(ChatColor.YELLOW + "/npc populate [count]" + ChatColor.GRAY + " - Auto-spawn NPCs nearby");
        player.sendMessage(ChatColor.YELLOW + "/npc remove <name>" + ChatColor.GRAY + " - Remove an NPC");
        player.sendMessage(ChatColor.YELLOW + "/npc list [faction]" + ChatColor.GRAY + " - List all NPCs");
        player.sendMessage(ChatColor.YELLOW + "/npc info <name>" + ChatColor.GRAY + " - Show NPC details");
        player.sendMessage(ChatColor.YELLOW + "/npc near" + ChatColor.GRAY + " - Show nearby NPCs");
        player.sendMessage(ChatColor.YELLOW + "/npc tp <name>" + ChatColor.GRAY + " - Teleport to NPC");
        player.sendMessage(ChatColor.YELLOW + "/npc setfaction <name> <faction>" + ChatColor.GRAY + " - Change faction");
        player.sendMessage(ChatColor.YELLOW + "/npc regenerate <name>" + ChatColor.GRAY + " - Regenerate backstory");
        player.sendMessage(ChatColor.RED + "--- Bulk Operations ---");
        player.sendMessage(ChatColor.YELLOW + "/npc clear [faction]" + ChatColor.GRAY + " - Remove NPCs (optionally by faction)");
        player.sendMessage(ChatColor.YELLOW + "/npc clearall" + ChatColor.GRAY + " - Remove ALL NPCs and wipe database");
        player.sendMessage(ChatColor.YELLOW + "/npc cleardead" + ChatColor.GRAY + " - Remove only dead NPCs");
        player.sendMessage(ChatColor.YELLOW + "/npc respawnall" + ChatColor.GRAY + " - Respawn all NPCs");
        player.sendMessage(ChatColor.YELLOW + "/npc killall" + ChatColor.GRAY + " - Kill all NPCs (they can respawn)");
        player.sendMessage(ChatColor.GOLD + "════════════════════════════════════");
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /npc create <name> [faction]");
            return;
        }

        String name = args[1];
        String faction = args.length > 2 ? args[2] : "Wanderers";

        // Validate faction
        if (!factionManager.factionExists(faction)) {
            player.sendMessage(ChatColor.RED + "Faction '" + faction + "' does not exist.");
            player.sendMessage(ChatColor.GRAY + "Available factions: " +
                    factionManager.getAllFactions().stream()
                            .map(Faction::getName)
                            .collect(Collectors.joining(", ")));
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Creating NPC '" + name + "'...");

        AINpc npc = npcManager.createNPC(name, faction, player.getLocation());

        player.sendMessage(ChatColor.GREEN + "Created NPC: " + ChatColor.GOLD + npc.getName());
        player.sendMessage(ChatColor.GRAY + "Faction: " + npc.getFaction());
        player.sendMessage(ChatColor.GRAY + "Backstory is being generated by AI...");
    }

    private void handleRandom(Player player, String[] args) {
        String faction;
        if (args.length > 1) {
            faction = args[1];
            if (!factionManager.factionExists(faction)) {
                player.sendMessage(ChatColor.RED + "Faction '" + faction + "' does not exist.");
                return;
            }
        } else {
            Faction randomFaction = factionManager.getRandomFaction();
            faction = randomFaction != null ? randomFaction.getName() : "Wanderers";
        }

        player.sendMessage(ChatColor.YELLOW + "Creating random NPC...");
        AINpc npc = npcManager.createRandomNPC(player.getLocation(), faction);

        player.sendMessage(ChatColor.GREEN + "Created random NPC: " + ChatColor.GOLD + npc.getName());
        player.sendMessage(ChatColor.GRAY + "Faction: " + npc.getFaction());
    }

    private void handlePopulate(Player player, String[] args) {
        int count = 10; // Default
        if (args.length > 1) {
            try {
                count = Integer.parseInt(args[1]);
                count = Math.min(count, 50); // Cap at 50
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid number. Using default (10)");
            }
        }

        player.sendMessage(ChatColor.YELLOW + "Spawning " + count + " NPCs nearby...");

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            // Pick random faction
            Faction faction = factionManager.getRandomFaction();
            String factionName = faction != null ? faction.getName() : "Wanderers";

            // Find spawn location near player
            Location loc = findSpawnLocation(player);
            if (loc != null) {
                npcManager.createRandomNPC(loc, factionName);
                spawned++;
            }
        }

        player.sendMessage(ChatColor.GREEN + "Spawned " + spawned + " NPCs!");
    }

    private Location findSpawnLocation(Player player) {
        java.util.Random random = new java.util.Random();
        Location playerLoc = player.getLocation();

        for (int attempts = 0; attempts < 10; attempts++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = 10 + random.nextDouble() * 40; // 10-50 blocks away

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;
            int y = playerLoc.getWorld().getHighestBlockYAt((int) x, (int) z);

            Location loc = new Location(playerLoc.getWorld(), x, y + 1, z);

            // Basic validation
            if (loc.getBlock().getType().isAir()) {
                return loc;
            }
        }
        return player.getLocation(); // Fallback
    }

    private void handleClear(Player player, String[] args) {
        String faction = args.length > 1 ? args[1] : null;
        int count = 0;

        // Remove NPCs (optionally filtered by faction)
        for (AINpc npc : new java.util.ArrayList<>(npcManager.getAllNPCs())) {
            if (faction == null || npc.getFaction().equalsIgnoreCase(faction)) {
                npcManager.removeNPC(npc.getUuid());
                count++;
            }
        }

        if (faction != null) {
            player.sendMessage(ChatColor.GREEN + "Removed " + count + " NPCs from faction: " + faction);
        } else {
            player.sendMessage(ChatColor.GREEN + "Removed " + count + " NPCs.");
        }
    }

    private void handleClearAll(Player player) {
        int count = npcManager.getNPCCount();

        // Remove all NPCs
        for (AINpc npc : new java.util.ArrayList<>(npcManager.getAllNPCs())) {
            npcManager.removeNPC(npc.getUuid());
        }

        // Also clear the database
        plugin.getDatabaseManager().clearAllNPCs();

        player.sendMessage(ChatColor.GREEN + "Completely wiped " + count + " NPCs and cleared database.");
        player.sendMessage(ChatColor.YELLOW + "Use /npc populate <count> to spawn fresh NPCs!");
    }

    private void handleClearDead(Player player) {
        int count = 0;

        for (AINpc npc : new java.util.ArrayList<>(npcManager.getAllNPCs())) {
            if (!npc.isAlive()) {
                npcManager.removeNPC(npc.getUuid());
                count++;
            }
        }

        player.sendMessage(ChatColor.GREEN + "Removed " + count + " dead NPCs.");
    }

    private void handleRespawnAll(Player player) {
        int count = 0;

        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isSpawned() && npc.getSpawnLocation() != null) {
                npc.setAlive(true);
                npc.setHealth(npc.getMaxHealth());
                npcManager.spawnEntity(npc);
                count++;
            }
        }

        player.sendMessage(ChatColor.GREEN + "Respawned " + count + " NPCs.");
    }

    private void handleKillAll(Player player) {
        int count = 0;

        for (AINpc npc : npcManager.getAllNPCs()) {
            if (npc.isAlive()) {
                npc.setAlive(false);
                npc.setHealth(0);
                if (npc.getBukkitEntity() != null) {
                    npc.getBukkitEntity().remove();
                }
                count++;
            }
        }

        player.sendMessage(ChatColor.RED + "Killed " + count + " NPCs. They will respawn if enabled.");
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /npc remove <name>");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        AINpc npc = findNPCByName(name);

        if (npc == null) {
            player.sendMessage(ChatColor.RED + "NPC '" + name + "' not found.");
            return;
        }

        npcManager.removeNPC(npc.getUuid());
        player.sendMessage(ChatColor.GREEN + "Removed NPC: " + npc.getName());
    }

    private void handleList(Player player, String[] args) {
        String filterFaction = args.length > 1 ? args[1] : null;

        List<AINpc> npcs;
        if (filterFaction != null) {
            npcs = npcManager.getNPCsByFaction(filterFaction);
            player.sendMessage(ChatColor.GOLD + "═══════ NPCs in " + filterFaction + " ═══════");
        } else {
            npcs = new ArrayList<>(npcManager.getAllNPCs());
            player.sendMessage(ChatColor.GOLD + "═══════ All NPCs (" + npcs.size() + ") ═══════");
        }

        if (npcs.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No NPCs found.");
            return;
        }

        for (AINpc npc : npcs) {
            String status = npc.isAlive() ? ChatColor.GREEN + "●" : ChatColor.RED + "●";
            player.sendMessage(status + " " + ChatColor.WHITE + npc.getName() +
                    ChatColor.GRAY + " (" + npc.getFaction() + ")");
        }
    }

    private void handleInfo(Player player, String[] args) {
        AINpc npc;
        if (args.length < 2) {
            // Try to find nearest NPC
            npc = npcManager.getNearestNPC(player.getLocation(), 10);
            if (npc == null) {
                player.sendMessage(ChatColor.RED + "Usage: /npc info <name>");
                return;
            }
        } else {
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            npc = findNPCByName(name);
        }

        if (npc == null) {
            player.sendMessage(ChatColor.RED + "NPC not found.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "═══════ " + npc.getName() + " ═══════");
        player.sendMessage(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + npc.getUuid());
        player.sendMessage(ChatColor.GRAY + "Faction: " + ChatColor.WHITE + npc.getFaction());
        player.sendMessage(ChatColor.GRAY + "Status: " + (npc.isAlive() ?
                ChatColor.GREEN + "Alive" : ChatColor.RED + "Dead"));
        player.sendMessage(ChatColor.GRAY + "Health: " + ChatColor.WHITE +
                String.format("%.1f/%.1f", npc.getHealth(), npc.getMaxHealth()));
        player.sendMessage(ChatColor.GRAY + "Mood: " + ChatColor.WHITE + npc.getCurrentMood());
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Personality: " + ChatColor.WHITE + npc.getPersonality());
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Backstory: " + ChatColor.WHITE + npc.getBackstory());
        player.sendMessage(ChatColor.GOLD + "══════════════════════════════");
    }

    private void handleTeleport(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /npc tp <name>");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        AINpc npc = findNPCByName(name);

        if (npc == null) {
            player.sendMessage(ChatColor.RED + "NPC '" + name + "' not found.");
            return;
        }

        if (npc.getCurrentLocation() == null) {
            player.sendMessage(ChatColor.RED + "NPC has no valid location.");
            return;
        }

        player.teleport(npc.getCurrentLocation());
        player.sendMessage(ChatColor.GREEN + "Teleported to " + npc.getName());
    }

    private void handleNearby(Player player) {
        double range = 50;
        List<AINpc> nearby = new ArrayList<>();

        for (AINpc npc : npcManager.getAllNPCs()) {
            if (npc.getCurrentLocation() != null &&
                    npc.getCurrentLocation().getWorld().equals(player.getWorld()) &&
                    npc.getCurrentLocation().distance(player.getLocation()) <= range) {
                nearby.add(npc);
            }
        }

        if (nearby.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No NPCs within " + (int)range + " blocks.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "═══════ Nearby NPCs ═══════");
        for (AINpc npc : nearby) {
            double distance = npc.getCurrentLocation().distance(player.getLocation());
            String status = npc.isAlive() ? ChatColor.GREEN + "●" : ChatColor.RED + "●";
            player.sendMessage(status + " " + ChatColor.WHITE + npc.getName() +
                    ChatColor.GRAY + " - " + String.format("%.1f", distance) + " blocks");
        }
    }

    private void handleSetPersonality(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /npc setpersonality <name> <personality>");
            return;
        }

        String name = args[1];
        String personality = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        AINpc npc = findNPCByName(name);
        if (npc == null) {
            player.sendMessage(ChatColor.RED + "NPC '" + name + "' not found.");
            return;
        }

        npc.setPersonality(personality);
        player.sendMessage(ChatColor.GREEN + "Updated personality for " + npc.getName());
    }

    private void handleSetFaction(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /npc setfaction <name> <faction>");
            return;
        }

        String name = args[1];
        String faction = args[2];

        AINpc npc = findNPCByName(name);
        if (npc == null) {
            player.sendMessage(ChatColor.RED + "NPC '" + name + "' not found.");
            return;
        }

        if (!factionManager.factionExists(faction)) {
            player.sendMessage(ChatColor.RED + "Faction '" + faction + "' does not exist.");
            return;
        }

        npc.setFaction(faction);
        Faction f = factionManager.getFaction(faction);
        npc.setHostile(f != null && f.isHostileByDefault());

        // Respawn to update name color
        npcManager.spawnEntity(npc);

        player.sendMessage(ChatColor.GREEN + "Changed " + npc.getName() + "'s faction to " + faction);
    }

    private void handleRegenerate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /npc regenerate <name>");
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        AINpc npc = findNPCByName(name);

        if (npc == null) {
            player.sendMessage(ChatColor.RED + "NPC '" + name + "' not found.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Regenerating backstory for " + npc.getName() + "...");

        aiManager.generateBackstory(npc.getName(), npc.getFaction(), npc.getPersonality())
                .thenAccept(backstory -> {
                    npc.setBackstory(backstory);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.GREEN + "New backstory generated!");
                        player.sendMessage(ChatColor.GRAY + backstory);
                    });
                });
    }

    private AINpc findNPCByName(String name) {
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (npc.getName().equalsIgnoreCase(name)) {
                return npc;
            }
        }
        // Try partial match
        for (AINpc npc : npcManager.getAllNPCs()) {
            if (npc.getName().toLowerCase().contains(name.toLowerCase())) {
                return npc;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "random", "populate", "remove", "list", "info",
                    "teleport", "near", "setpersonality", "setfaction", "regenerate",
                    "clear", "clearall", "cleardead", "respawnall", "killall"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("remove") || sub.equals("info") || sub.equals("tp") ||
                    sub.equals("teleport") || sub.equals("setpersonality") ||
                    sub.equals("setfaction") || sub.equals("regenerate")) {
                // Complete with NPC names
                for (AINpc npc : npcManager.getAllNPCs()) {
                    completions.add(npc.getName().replace(" ", "_"));
                }
            } else if (sub.equals("create") || sub.equals("random") || sub.equals("list")) {
                // Complete with faction names
                for (Faction faction : factionManager.getAllFactions()) {
                    completions.add(faction.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("setfaction")) {
            // Complete with faction names
            for (Faction faction : factionManager.getAllFactions()) {
                completions.add(faction.getName());
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
