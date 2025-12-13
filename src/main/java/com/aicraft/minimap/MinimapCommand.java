package com.aicraft.minimap;

import org.bukkit.ChatColor;
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
 * Command handler for minimap functionality
 * /minimap - Get or toggle minimap
 * /minimap give [circular|square] - Give minimap item
 * /minimap toggle - Toggle between circular and square
 * /minimap waypoint add <name> - Add custom waypoint
 * /minimap waypoint remove <name> - Remove custom waypoint
 * /minimap waypoint list - List all waypoints
 */
public class MinimapCommand implements CommandExecutor, TabCompleter {

    private final MinimapManager minimapManager;

    public MinimapCommand(MinimapManager minimapManager) {
        this.minimapManager = minimapManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            // Default: give minimap or toggle if already has one
            if (minimapManager.hasMinmap(player)) {
                minimapManager.toggleMinimapStyle(player);
            } else {
                minimapManager.giveMinimapToPlayer(player, true); // Default to circular
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give" -> handleGive(player, args);
            case "toggle" -> handleToggle(player);
            case "waypoint", "wp" -> handleWaypoint(player, args);
            case "help" -> showHelp(player);
            default -> {
                player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /minimap help");
            }
        }

        return true;
    }

    private void handleGive(Player player, String[] args) {
        boolean circular = true;

        if (args.length > 1) {
            String style = args[1].toLowerCase();
            if (style.equals("square") || style.equals("sq")) {
                circular = false;
            } else if (!style.equals("circular") && !style.equals("circle")) {
                player.sendMessage(ChatColor.RED + "Invalid style. Use 'circular' or 'square'.");
                return;
            }
        }

        minimapManager.giveMinimapToPlayer(player, circular);
    }

    private void handleToggle(Player player) {
        if (!minimapManager.hasMinmap(player)) {
            player.sendMessage(ChatColor.RED + "You don't have a minimap! Use /minimap give first.");
            return;
        }
        minimapManager.toggleMinimapStyle(player);
    }

    private void handleWaypoint(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /minimap waypoint <add|remove|list> [name]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /minimap waypoint add <name>");
                    return;
                }
                // Join remaining args as waypoint name
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                minimapManager.addCustomWaypoint(player, name);
            }
            case "remove", "delete" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /minimap waypoint remove <name>");
                    return;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                minimapManager.removeCustomWaypoint(player, name);
            }
            case "list" -> minimapManager.listWaypoints(player);
            default -> player.sendMessage(ChatColor.RED + "Unknown action. Use add, remove, or list.");
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Minimap Commands ===");
        player.sendMessage(ChatColor.YELLOW + "/minimap" + ChatColor.WHITE + " - Get minimap or toggle style");
        player.sendMessage(ChatColor.YELLOW + "/minimap give [circular|square]" + ChatColor.WHITE + " - Get a minimap item");
        player.sendMessage(ChatColor.YELLOW + "/minimap toggle" + ChatColor.WHITE + " - Switch between circular/square");
        player.sendMessage(ChatColor.YELLOW + "/minimap waypoint add <name>" + ChatColor.WHITE + " - Mark current location");
        player.sendMessage(ChatColor.YELLOW + "/minimap waypoint remove <name>" + ChatColor.WHITE + " - Remove a waypoint");
        player.sendMessage(ChatColor.YELLOW + "/minimap waypoint list" + ChatColor.WHITE + " - List all waypoints");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Hold the map item to view terrain, NPCs, and quest waypoints.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("give", "toggle", "waypoint", "wp", "help"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give")) {
                completions.addAll(Arrays.asList("circular", "square"));
            } else if (sub.equals("waypoint") || sub.equals("wp")) {
                completions.addAll(Arrays.asList("add", "remove", "list"));
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
