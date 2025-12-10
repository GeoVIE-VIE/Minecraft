package com.aicraft.commands;

import com.aicraft.AICompanions;
import com.aicraft.gui.QuestTrackerGUI;
import com.aicraft.quests.Quest;
import com.aicraft.quests.QuestManager;
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
 * Command handler for quest management
 */
public class QuestCommand implements CommandExecutor, TabCompleter {

    private final AICompanions plugin;
    private final QuestManager questManager;
    private QuestTrackerGUI questGUI;

    public QuestCommand(AICompanions plugin, QuestManager questManager) {
        this.plugin = plugin;
        this.questManager = questManager;
    }

    public void setQuestGUI(QuestTrackerGUI questGUI) {
        this.questGUI = questGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        // Default to GUI if no arguments
        if (args.length == 0) {
            if (questGUI != null) {
                questGUI.openGUI(player);
            } else {
                handleList(player);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list", "active" -> handleList(player);
            case "info" -> handleInfo(player, args);
            case "abandon" -> handleAbandon(player, args);
            case "track" -> handleTrack(player, args);
            case "gui", "menu", "journal" -> {
                if (questGUI != null) {
                    questGUI.openGUI(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Quest GUI is not available.");
                }
            }
            case "waypoint", "compass" -> handleWaypoint(player, args);
            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "═══════ Quest Commands ═══════");
        player.sendMessage(ChatColor.YELLOW + "/quest" + ChatColor.GRAY + " - Open quest journal (GUI)");
        player.sendMessage(ChatColor.YELLOW + "/quest list" + ChatColor.GRAY + " - Show your active quests");
        player.sendMessage(ChatColor.YELLOW + "/quest info <number>" + ChatColor.GRAY + " - Show quest details");
        player.sendMessage(ChatColor.YELLOW + "/quest track <number>" + ChatColor.GRAY + " - Track quest waypoint");
        player.sendMessage(ChatColor.YELLOW + "/quest waypoint" + ChatColor.GRAY + " - Show waypoint directions");
        player.sendMessage(ChatColor.YELLOW + "/quest abandon <number>" + ChatColor.GRAY + " - Abandon a quest");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "To get quests, talk to NPCs using " +
                ChatColor.WHITE + "@<message>");
        player.sendMessage(ChatColor.GOLD + "══════════════════════════════");
    }

    private void handleTrack(Player player, String[] args) {
        if (questGUI == null) {
            player.sendMessage(ChatColor.RED + "Quest tracking is not available.");
            return;
        }

        List<Quest> quests = questManager.getActiveQuests(player.getUniqueId());
        if (quests.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no active quests to track.");
            return;
        }

        int index;
        if (args.length < 2) {
            index = 1;
        } else {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid quest number.");
                return;
            }
        }

        if (index < 1 || index > quests.size()) {
            player.sendMessage(ChatColor.RED + "Quest number must be between 1 and " + quests.size());
            return;
        }

        Quest quest = quests.get(index - 1);
        questGUI.trackQuest(player, quest);
    }

    private void handleWaypoint(Player player, String[] args) {
        if (questGUI == null) {
            player.sendMessage(ChatColor.RED + "Quest tracking is not available.");
            return;
        }

        Quest tracked = questGUI.getTrackedQuest(player.getUniqueId());
        if (tracked == null) {
            player.sendMessage(ChatColor.YELLOW + "You are not tracking any quest.");
            player.sendMessage(ChatColor.GRAY + "Use /quest track <number> to track a quest.");
            return;
        }

        Location waypoint = tracked.getWaypointLocation(plugin.getServer());
        if (waypoint == null) {
            player.sendMessage(ChatColor.YELLOW + "Tracking: " + ChatColor.GOLD + tracked.getTitle());
            player.sendMessage(ChatColor.GRAY + "This quest has no specific waypoint location.");
            return;
        }

        double distance = tracked.getDistanceToWaypoint(player.getLocation(), plugin.getServer());
        String direction = tracked.getDirectionToWaypoint(player.getLocation(), plugin.getServer());

        player.sendMessage(ChatColor.GOLD + "═══════ Quest Waypoint ═══════");
        player.sendMessage(ChatColor.YELLOW + "Tracking: " + ChatColor.WHITE + tracked.getTitle());
        player.sendMessage("");
        if (distance >= 0) {
            player.sendMessage(ChatColor.AQUA + "⚑ Distance: " + ChatColor.WHITE + (int) distance + " blocks");
            player.sendMessage(ChatColor.AQUA + "⚑ Direction: " + ChatColor.WHITE + direction);
            player.sendMessage(ChatColor.AQUA + "⚑ Coordinates: " + ChatColor.WHITE +
                    String.format("%.0f, %.0f, %.0f", waypoint.getX(), waypoint.getY(), waypoint.getZ()));
        } else {
            player.sendMessage(ChatColor.GRAY + "Waypoint is in a different world.");
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Your compass points to this location!");
        player.sendMessage(ChatColor.GOLD + "══════════════════════════════");
    }

    private void handleList(Player player) {
        List<Quest> quests = questManager.getActiveQuests(player.getUniqueId());

        if (quests.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no active quests.");
            player.sendMessage(ChatColor.GRAY + "Talk to NPCs to receive quests!");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "═══════ Your Quests ═══════");

        int index = 1;
        for (Quest quest : quests) {
            ChatColor statusColor = switch (quest.getStatus()) {
                case ACTIVE -> ChatColor.YELLOW;
                case READY_TO_TURN_IN -> ChatColor.GREEN;
                default -> ChatColor.GRAY;
            };

            String statusSymbol = quest.getStatus() == Quest.QuestStatus.READY_TO_TURN_IN ? "✓" : "●";

            player.sendMessage(statusColor + statusSymbol + " " + ChatColor.WHITE + index + ". " +
                    ChatColor.GOLD + quest.getTitle());
            player.sendMessage(ChatColor.GRAY + "   Progress: " +
                    ChatColor.WHITE + quest.getProgressString() +
                    ChatColor.GRAY + " (" + String.format("%.0f", quest.getProgressPercentage()) + "%)");

            index++;
        }

        player.sendMessage(ChatColor.GOLD + "════════════════════════════");
        player.sendMessage(ChatColor.GRAY + "Use /quest info <number> for details");
    }

    private void handleInfo(Player player, String[] args) {
        List<Quest> quests = questManager.getActiveQuests(player.getUniqueId());

        if (quests.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no active quests.");
            return;
        }

        int index;
        if (args.length < 2) {
            index = 1; // Default to first quest
        } else {
            try {
                index = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid quest number.");
                return;
            }
        }

        if (index < 1 || index > quests.size()) {
            player.sendMessage(ChatColor.RED + "Quest number must be between 1 and " + quests.size());
            return;
        }

        Quest quest = quests.get(index - 1);

        player.sendMessage(ChatColor.GOLD + "═══════ " + quest.getTitle() + " ═══════");
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.WHITE + quest.getQuestType());
        player.sendMessage("");
        player.sendMessage(ChatColor.WHITE + quest.getDescription());
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Objective: " + ChatColor.WHITE + quest.getObjective());
        player.sendMessage(ChatColor.GRAY + "Progress: " + ChatColor.WHITE + quest.getProgressString());

        // Progress bar
        int barLength = 20;
        int filled = (int) (quest.getProgressPercentage() / 100 * barLength);
        String progressBar = ChatColor.GREEN + "█".repeat(filled) +
                ChatColor.GRAY + "░".repeat(barLength - filled);
        player.sendMessage(progressBar + " " + String.format("%.0f%%", quest.getProgressPercentage()));

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Rewards:");
        player.sendMessage(ChatColor.GOLD + "  +" + quest.getRewardXp() + " XP");
        if (!quest.getRewardItems().isEmpty()) {
            for (String item : quest.getRewardItems()) {
                player.sendMessage(ChatColor.AQUA + "  +" + item);
            }
        }

        if (quest.getStatus() == Quest.QuestStatus.READY_TO_TURN_IN) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ Quest complete! Return to the quest giver.");
        }

        player.sendMessage(ChatColor.GOLD + "══════════════════════════════════");
    }

    private void handleAbandon(Player player, String[] args) {
        List<Quest> quests = questManager.getActiveQuests(player.getUniqueId());

        if (quests.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no active quests.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /quest abandon <number>");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid quest number.");
            return;
        }

        if (index < 1 || index > quests.size()) {
            player.sendMessage(ChatColor.RED + "Quest number must be between 1 and " + quests.size());
            return;
        }

        Quest quest = quests.get(index - 1);
        questManager.abandonQuest(player, quest);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("list", "info", "abandon", "track", "waypoint", "gui"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("info") || sub.equals("abandon") || sub.equals("track")) {
                if (sender instanceof Player player) {
                    List<Quest> quests = questManager.getActiveQuests(player.getUniqueId());
                    for (int i = 1; i <= quests.size(); i++) {
                        completions.add(String.valueOf(i));
                    }
                }
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
