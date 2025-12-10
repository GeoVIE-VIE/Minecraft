package com.aicraft.commands;

import com.aicraft.AICompanions;
import com.aicraft.factions.Faction;
import com.aicraft.factions.FactionManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command handler for faction management
 */
public class FactionCommand implements CommandExecutor, TabCompleter {

    private final AICompanions plugin;
    private final FactionManager factionManager;

    public FactionCommand(AICompanions plugin, FactionManager factionManager) {
        this.plugin = plugin;
        this.factionManager = factionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create" -> handleCreate(sender, args);
            case "remove", "delete" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "setrelation" -> handleSetRelation(sender, args);
            case "setcolor" -> handleSetColor(sender, args);
            default -> showHelp(sender);
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══════ Faction Commands ═══════");
        sender.sendMessage(ChatColor.YELLOW + "/faction create <name> [color] [description]");
        sender.sendMessage(ChatColor.YELLOW + "/faction remove <name>");
        sender.sendMessage(ChatColor.YELLOW + "/faction list");
        sender.sendMessage(ChatColor.YELLOW + "/faction info <name>");
        sender.sendMessage(ChatColor.YELLOW + "/faction setrelation <faction1> <faction2> <hostile|neutral|allied>");
        sender.sendMessage(ChatColor.YELLOW + "/faction setcolor <name> <color>");
        sender.sendMessage(ChatColor.GOLD + "════════════════════════════════");
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /faction create <name> [color] [description]");
            return;
        }

        String name = args[1];
        ChatColor color = ChatColor.WHITE;
        String description = "";

        if (args.length > 2) {
            try {
                color = ChatColor.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Invalid color. Using WHITE.");
            }
        }

        if (args.length > 3) {
            description = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        }

        if (factionManager.factionExists(name)) {
            sender.sendMessage(ChatColor.RED + "Faction '" + name + "' already exists.");
            return;
        }

        Faction faction = factionManager.createFaction(name, color, description);
        if (faction != null) {
            sender.sendMessage(ChatColor.GREEN + "Created faction: " + faction.getDisplayName());
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to create faction.");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /faction remove <name>");
            return;
        }

        String name = args[1];
        if (factionManager.removeFaction(name)) {
            sender.sendMessage(ChatColor.GREEN + "Removed faction: " + name);
        } else {
            sender.sendMessage(ChatColor.RED + "Faction '" + name + "' not found.");
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══════ Factions ═══════");

        for (Faction faction : factionManager.getAllFactions()) {
            sender.sendMessage(faction.getDisplayName() + ChatColor.GRAY +
                    (faction.getDescription() != null ? " - " + faction.getDescription() : ""));
        }

        sender.sendMessage(ChatColor.GOLD + "════════════════════════");
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /faction info <name>");
            return;
        }

        String name = args[1];
        Faction faction = factionManager.getFaction(name);

        if (faction == null) {
            sender.sendMessage(ChatColor.RED + "Faction '" + name + "' not found.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "═══════ " + faction.getDisplayName() + ChatColor.GOLD + " ═══════");
        sender.sendMessage(ChatColor.GRAY + "Description: " + ChatColor.WHITE +
                (faction.getDescription() != null ? faction.getDescription() : "None"));
        sender.sendMessage(ChatColor.GRAY + "Color: " + faction.getColor() + faction.getColor().name());
        sender.sendMessage(ChatColor.GRAY + "Hostile by default: " + ChatColor.WHITE + faction.isHostileByDefault());

        if (!faction.getHostileFactions().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Hostile to: " + ChatColor.RED +
                    String.join(", ", faction.getHostileFactions()));
        }

        if (!faction.getAlliedFactions().isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Allied with: " + ChatColor.GREEN +
                    String.join(", ", faction.getAlliedFactions()));
        }

        // Count NPCs in this faction
        long npcCount = plugin.getNPCManager().getNPCsByFaction(faction.getName()).size();
        sender.sendMessage(ChatColor.GRAY + "NPCs in faction: " + ChatColor.WHITE + npcCount);

        sender.sendMessage(ChatColor.GOLD + "══════════════════════════════");
    }

    private void handleSetRelation(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /faction setrelation <faction1> <faction2> <hostile|neutral|allied>");
            return;
        }

        String faction1 = args[1];
        String faction2 = args[2];
        String relationType = args[3].toLowerCase();

        if (!factionManager.factionExists(faction1)) {
            sender.sendMessage(ChatColor.RED + "Faction '" + faction1 + "' not found.");
            return;
        }

        if (!factionManager.factionExists(faction2)) {
            sender.sendMessage(ChatColor.RED + "Faction '" + faction2 + "' not found.");
            return;
        }

        Faction.FactionRelation relation;
        try {
            relation = Faction.FactionRelation.valueOf(relationType.toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid relation type. Use: hostile, neutral, or allied");
            return;
        }

        factionManager.setRelation(faction1, faction2, relation);

        String relationColor = switch (relation) {
            case HOSTILE -> ChatColor.RED.toString();
            case ALLIED -> ChatColor.GREEN.toString();
            case NEUTRAL -> ChatColor.GRAY.toString();
        };

        sender.sendMessage(ChatColor.GREEN + "Set " + faction1 + " and " + faction2 +
                " as " + relationColor + relation.name().toLowerCase());
    }

    private void handleSetColor(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /faction setcolor <name> <color>");
            return;
        }

        String name = args[1];
        Faction faction = factionManager.getFaction(name);

        if (faction == null) {
            sender.sendMessage(ChatColor.RED + "Faction '" + name + "' not found.");
            return;
        }

        try {
            ChatColor color = ChatColor.valueOf(args[2].toUpperCase());
            faction.setColor(color);
            sender.sendMessage(ChatColor.GREEN + "Set " + faction.getDisplayName() +
                    ChatColor.GREEN + "'s color to " + color + color.name());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Invalid color. Available colors:");
            sender.sendMessage(ChatColor.GRAY + "RED, BLUE, GREEN, YELLOW, GOLD, AQUA, WHITE, GRAY, DARK_RED, etc.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("create", "remove", "list", "info", "setrelation", "setcolor"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (!sub.equals("create") && !sub.equals("list")) {
                for (Faction faction : factionManager.getAllFactions()) {
                    completions.add(faction.getName());
                }
            }
            if (sub.equals("create")) {
                completions.add("<name>");
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("setrelation")) {
                for (Faction faction : factionManager.getAllFactions()) {
                    completions.add(faction.getName());
                }
            } else if (sub.equals("create") || sub.equals("setcolor")) {
                completions.addAll(Arrays.asList("RED", "BLUE", "GREEN", "YELLOW", "GOLD",
                        "AQUA", "WHITE", "GRAY", "DARK_RED", "DARK_BLUE"));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("setrelation")) {
            completions.addAll(Arrays.asList("hostile", "neutral", "allied"));
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
