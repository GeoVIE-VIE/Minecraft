package com.aicraft.listeners;

import com.aicraft.AICompanions;
import com.aicraft.ai.AIManager;
import com.aicraft.memory.MemoryManager;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import com.aicraft.quests.Quest;
import com.aicraft.quests.QuestManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player chat for NPC conversations
 * Players can talk to nearby NPCs by using a prefix or by being in conversation mode
 */
public class ChatListener implements Listener {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final AIManager aiManager;
    private final MemoryManager memoryManager;
    private final QuestManager questManager;

    // Track which players are in conversation with which NPCs
    private final Map<UUID, UUID> activeConversations = new HashMap<>();

    // Track pending quest offers (Player UUID -> Quest)
    private final Map<UUID, Quest> pendingQuestOffers = new HashMap<>();

    // Chat prefix to talk to NPCs (configurable)
    private static final String NPC_CHAT_PREFIX = "@";

    // Keywords that trigger quest offers
    private static final String[] QUEST_KEYWORDS = {
            "quest", "job", "work", "task", "help you", "need anything",
            "mission", "assignment", "something to do", "earn money", "earn gold"
    };

    public ChatListener(AICompanions plugin, NPCManager npcManager, AIManager aiManager, MemoryManager memoryManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.aiManager = aiManager;
        this.memoryManager = memoryManager;
        this.questManager = plugin.getQuestManager();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Check if player is addressing an NPC with @ prefix
        if (message.startsWith(NPC_CHAT_PREFIX)) {
            event.setCancelled(true);
            String npcMessage = message.substring(1).trim();
            handleNPCChat(player, npcMessage);
            return;
        }

        // Check if player is in active conversation
        UUID conversationNpcId = activeConversations.get(player.getUniqueId());
        if (conversationNpcId != null) {
            AINpc npc = npcManager.getNPC(conversationNpcId);
            if (npc != null && npc.isAlive() && npc.isSpawned()) {
                // Check distance
                double distance = player.getLocation().distance(npc.getCurrentLocation());
                double maxDistance = plugin.getConfig().getDouble("npcs.interaction-distance", 5);

                if (distance <= maxDistance) {
                    event.setCancelled(true);
                    processNPCConversation(player, npc, message);
                    return;
                } else {
                    // Too far, end conversation
                    endConversation(player);
                    player.sendMessage(ChatColor.GRAY + "*You've moved too far from " + npc.getName() + "*");
                }
            } else {
                endConversation(player);
            }
        }
    }

    /**
     * Handle chat directed at nearby NPCs
     */
    private void handleNPCChat(Player player, String message) {
        if (message.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Use @<message> to talk to nearby NPCs");
            return;
        }

        // Check rate limit
        if (!aiManager.canMakeRequest(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Please wait before sending another message.");
            player.sendMessage(ChatColor.GRAY + "Remaining requests: " +
                    aiManager.getRemainingRequests(player.getUniqueId()) + " per minute");
            return;
        }

        // Find nearest NPC
        double maxDistance = plugin.getConfig().getDouble("npcs.interaction-distance", 5);
        AINpc npc = npcManager.getNearestNPC(player.getLocation(), maxDistance);

        if (npc == null) {
            player.sendMessage(ChatColor.GRAY + "*No one is nearby to hear you*");
            return;
        }

        if (!npc.isAlive()) {
            player.sendMessage(ChatColor.GRAY + "*" + npc.getName() + " cannot respond...*");
            return;
        }

        // Start/continue conversation
        activeConversations.put(player.getUniqueId(), npc.getUuid());
        processNPCConversation(player, npc, message);
    }

    /**
     * Process a conversation with an NPC
     */
    private void processNPCConversation(Player player, AINpc npc, String message) {
        String lowerMessage = message.toLowerCase();

        // Check if player is accepting a pending quest offer
        if (pendingQuestOffers.containsKey(player.getUniqueId())) {
            if (lowerMessage.contains("yes") || lowerMessage.contains("accept") ||
                    lowerMessage.contains("sure") || lowerMessage.contains("ok") || lowerMessage.contains("i'll do it")) {
                Quest quest = pendingQuestOffers.remove(player.getUniqueId());
                questManager.acceptQuest(player, quest);
                player.sendMessage(ChatColor.GOLD + npc.getName() + ": " + ChatColor.WHITE +
                        "Excellent! Return to me when you're done.");
                return;
            } else if (lowerMessage.contains("no") || lowerMessage.contains("decline") ||
                    lowerMessage.contains("can't") || lowerMessage.contains("busy")) {
                pendingQuestOffers.remove(player.getUniqueId());
                player.sendMessage(ChatColor.GOLD + npc.getName() + ": " + ChatColor.WHITE +
                        "Very well. Come back if you change your mind.");
                return;
            }
        }

        // Check if player is asking for a quest
        boolean askingForQuest = false;
        for (String keyword : QUEST_KEYWORDS) {
            if (lowerMessage.contains(keyword)) {
                askingForQuest = true;
                break;
            }
        }

        // Record rate limit
        aiManager.recordRequest(player.getUniqueId());

        // Update NPC's last interaction
        npc.setLastInteraction(System.currentTimeMillis());
        npc.setLastInteractedPlayer(player.getUniqueId());

        // Show player message
        player.sendMessage(ChatColor.WHITE + "You: " + ChatColor.GRAY + message);

        // If asking for quest and NPC can give quests, generate one
        if (askingForQuest && npc.canGiveQuests()) {
            player.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + npc.getName() + " considers your request...");

            questManager.generateQuest(npc, player)
                    .thenAccept(quest -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            // Store the pending quest offer
                            pendingQuestOffers.put(player.getUniqueId(), quest);

                            // Present the quest to the player
                            player.sendMessage("");
                            player.sendMessage(ChatColor.GOLD + npc.getName() + ": " + ChatColor.WHITE + quest.getDescription());
                            player.sendMessage("");
                            player.sendMessage(ChatColor.YELLOW + "═══════ Quest Offer ═══════");
                            player.sendMessage(ChatColor.GOLD + quest.getTitle());
                            player.sendMessage(ChatColor.GRAY + "Objective: " + ChatColor.WHITE + quest.getObjective());
                            player.sendMessage(ChatColor.GRAY + "Reward: " + ChatColor.GREEN + quest.getRewardXp() + " XP");
                            if (!quest.getRewardItems().isEmpty()) {
                                player.sendMessage(ChatColor.GRAY + "Items: " + ChatColor.AQUA +
                                        String.join(", ", quest.getRewardItems()));
                            }
                            player.sendMessage(ChatColor.YELLOW + "═══════════════════════════");
                            player.sendMessage(ChatColor.GREEN + "Say 'yes' or 'accept' to take this quest!");
                            player.sendMessage("");
                        });
                    });
            return;
        }

        // Show typing indicator
        player.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC + npc.getName() + " is thinking...");

        // Get conversation history
        String history = memoryManager.getConversationHistory(npc, player.getUniqueId());

        // Generate AI response asynchronously
        aiManager.generateResponse(npc, player.getName(), message, history)
                .thenAccept(response -> {
                    // Run on main thread for Bukkit API calls
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Format and send response
                        String formattedResponse = formatNPCResponse(npc, response);
                        player.sendMessage(formattedResponse);

                        // Record conversation in memory
                        memoryManager.recordConversation(npc, player.getUniqueId(),
                                player.getName(), message, response);

                        // Update NPC mood based on conversation (simple heuristic)
                        updateNPCMood(npc, message.toLowerCase());
                    });
                })
                .exceptionally(e -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(ChatColor.GRAY + "*" + npc.getName() +
                                " seems distracted and doesn't respond*");
                        plugin.getLogger().warning("AI response failed: " + e.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Format NPC response for display
     */
    private String formatNPCResponse(AINpc npc, String response) {
        ChatColor nameColor = ChatColor.GOLD;

        // Clean up response
        response = response.trim();

        // Handle multi-line responses
        if (response.contains("\n")) {
            StringBuilder formatted = new StringBuilder();
            formatted.append(nameColor).append(npc.getName()).append(": ");
            String[] lines = response.split("\n");
            formatted.append(ChatColor.WHITE).append(lines[0]);
            for (int i = 1; i < lines.length; i++) {
                formatted.append("\n").append(ChatColor.WHITE).append("  ").append(lines[i]);
            }
            return formatted.toString();
        }

        return nameColor + npc.getName() + ": " + ChatColor.WHITE + response;
    }

    /**
     * Update NPC mood based on conversation keywords
     */
    private void updateNPCMood(AINpc npc, String message) {
        if (message.contains("thank") || message.contains("help") || message.contains("please")) {
            npc.setCurrentMood("pleased");
        } else if (message.contains("attack") || message.contains("kill") || message.contains("die")) {
            npc.setCurrentMood("alarmed");
        } else if (message.contains("quest") || message.contains("job") || message.contains("work")) {
            npc.setCurrentMood("interested");
        } else if (message.contains("bye") || message.contains("farewell") || message.contains("leave")) {
            npc.setCurrentMood("neutral");
        }
    }

    /**
     * Start a conversation with an NPC
     */
    public void startConversation(Player player, AINpc npc) {
        activeConversations.put(player.getUniqueId(), npc.getUuid());
        player.sendMessage(ChatColor.GREEN + "You are now talking to " +
                ChatColor.GOLD + npc.getName() + ChatColor.GREEN + ".");
        player.sendMessage(ChatColor.GRAY + "Type normally to continue the conversation, " +
                "or walk away to end it.");

        // Check if they've met before
        if (memoryManager.hasMemoriesOf(npc, player.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "" + ChatColor.ITALIC +
                    "*" + npc.getName() + " seems to recognize you*");
        }
    }

    /**
     * End a conversation
     */
    public void endConversation(Player player) {
        UUID npcId = activeConversations.remove(player.getUniqueId());
        if (npcId != null) {
            AINpc npc = npcManager.getNPC(npcId);
            if (npc != null) {
                player.sendMessage(ChatColor.GRAY + "*You end your conversation with " +
                        npc.getName() + "*");
            }
        }
    }

    /**
     * Check if player is in conversation
     */
    public boolean isInConversation(Player player) {
        return activeConversations.containsKey(player.getUniqueId());
    }

    /**
     * Get the NPC the player is talking to
     */
    public AINpc getConversationPartner(Player player) {
        UUID npcId = activeConversations.get(player.getUniqueId());
        return npcId != null ? npcManager.getNPC(npcId) : null;
    }
}
