package com.aicraft.commands;

import com.aicraft.AICompanions;
import com.aicraft.ai.AIManager;
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
 * Command handler for AI configuration
 */
public class AIConfigCommand implements CommandExecutor, TabCompleter {

    private final AICompanions plugin;
    private final AIManager aiManager;

    public AIConfigCommand(AICompanions plugin, AIManager aiManager) {
        this.plugin = plugin;
        this.aiManager = aiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "test" -> handleTest(sender);
            case "setprovider" -> handleSetProvider(sender, args);
            default -> showHelp(sender);
        }

        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══════ AI Configuration ═══════");
        sender.sendMessage(ChatColor.YELLOW + "/aiconfig reload" + ChatColor.GRAY + " - Reload configuration");
        sender.sendMessage(ChatColor.YELLOW + "/aiconfig status" + ChatColor.GRAY + " - Show AI status");
        sender.sendMessage(ChatColor.YELLOW + "/aiconfig test" + ChatColor.GRAY + " - Test AI connection");
        sender.sendMessage(ChatColor.YELLOW + "/aiconfig setprovider <claude|openai>" + ChatColor.GRAY + " - Change provider");
        sender.sendMessage(ChatColor.GOLD + "═════════════════════════════════");
    }

    private void handleReload(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Reloading configuration...");
        plugin.reload();
        sender.sendMessage(ChatColor.GREEN + "Configuration reloaded!");
        sender.sendMessage(ChatColor.GRAY + "AI Provider: " + aiManager.getProviderName());
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══════ AI Status ═══════");
        sender.sendMessage(ChatColor.GRAY + "Provider: " + ChatColor.WHITE + aiManager.getProviderName());
        sender.sendMessage(ChatColor.GRAY + "Configured: " +
                (aiManager.getPrimaryProvider().isConfigured() ?
                        ChatColor.GREEN + "Yes" : ChatColor.RED + "No - API key missing"));
        sender.sendMessage(ChatColor.GRAY + "Rate Limit: " + ChatColor.WHITE +
                plugin.getConfig().getInt("ai.rate-limit", 10) + " requests/minute");
        sender.sendMessage(ChatColor.GRAY + "Timeout: " + ChatColor.WHITE +
                plugin.getConfig().getInt("ai.timeout-seconds", 30) + " seconds");
        sender.sendMessage(ChatColor.GOLD + "═════════════════════════");
    }

    private void handleTest(CommandSender sender) {
        if (!aiManager.getPrimaryProvider().isConfigured()) {
            sender.sendMessage(ChatColor.RED + "AI provider is not configured. Please set your API key in config.yml");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "Testing AI connection...");

        aiManager.testConnection().thenAccept(success -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    sender.sendMessage(ChatColor.GREEN + "✓ AI connection successful!");
                } else {
                    sender.sendMessage(ChatColor.RED + "✗ AI connection failed. Check your API key and network.");
                }
            });
        });
    }

    private void handleSetProvider(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /aiconfig setprovider <claude|openai>");
            return;
        }

        String provider = args[1].toLowerCase();
        if (!provider.equals("claude") && !provider.equals("openai")) {
            sender.sendMessage(ChatColor.RED + "Invalid provider. Use 'claude' or 'openai'");
            return;
        }

        // Update config
        plugin.getConfig().set("ai.provider", provider);
        plugin.saveConfig();

        // Reload AI manager
        aiManager.reload();

        sender.sendMessage(ChatColor.GREEN + "AI provider changed to: " + ChatColor.GOLD + aiManager.getProviderName());
        sender.sendMessage(ChatColor.GRAY + "Make sure the API key is configured for this provider.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "status", "test", "setprovider"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setprovider")) {
            completions.addAll(Arrays.asList("claude", "openai"));
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
