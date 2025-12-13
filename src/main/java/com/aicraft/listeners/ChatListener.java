package com.aicraft.listeners;

import com.aicraft.AICompanions;
import com.aicraft.ai.AIManager;
import com.aicraft.memory.MemoryManager;
import com.aicraft.npcs.AINpc;
import com.aicraft.npcs.NPCManager;
import com.aicraft.quests.Quest;
import com.aicraft.quests.QuestManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.*;

/**
 * Handles player chat for NPC conversations
 * Players can talk to NPCs using:
 * - @message - Talks to nearest NPC
 * - @NPCName message - Talks to specific NPC by name
 * - @NPCName: message - Alternative syntax
 * - Right-click NPC then type normally
 */
public class ChatListener implements Listener {

    private final AICompanions plugin;
    private final NPCManager npcManager;
    private final AIManager aiManager;
    private final MemoryManager memoryManager;
    private final QuestManager questManager;

    // Track which players are in conversation with which NPCs
    private final Map<UUID, UUID> activeConversations = new HashMap<>();

    // Track selected NPCs (from right-click)
    private final Map<UUID, UUID> selectedNPCs = new HashMap<>();

    // Track pending quest offers (Player UUID -> Quest)
    private final Map<UUID, Quest> pendingQuestOffers = new HashMap<>();

    // Track numbered NPC list for crowded selection (Player UUID -> List of NPCs)
    private final Map<UUID, List<AINpc>> numberedSelections = new HashMap<>();

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
     * Handle chat directed at NPCs
     * Supports: @message (nearest), @NPCName message, @NPCName: message, @1/@2/@3 (numbered selection)
     */
    private void handleNPCChat(Player player, String message) {
        if (message.isEmpty()) {
            showNearbyNPCsNumbered(player);
            return;
        }

        // Check rate limit
        if (!aiManager.canMakeRequest(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Please wait before sending another message.");
            player.sendMessage(ChatColor.GRAY + "Remaining requests: " +
                    aiManager.getRemainingRequests(player.getUniqueId()) + " per minute");
            return;
        }

        double maxDistance = plugin.getConfig().getDouble("npcs.interaction-distance", 10);
        AINpc npc = null;
        String actualMessage = message;

        // Check for numbered selection (@1, @2, etc.) from crowded list
        String[] parts = message.split("\\s+", 2);
        String firstWord = parts[0].replace(":", "").trim();

        if (firstWord.matches("\\d+")) {
            // Player is selecting by number
            int selection = Integer.parseInt(firstWord);
            List<AINpc> numberedList = numberedSelections.get(player.getUniqueId());

            if (numberedList != null && selection >= 1 && selection <= numberedList.size()) {
                npc = numberedList.get(selection - 1); // 1-indexed for user
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    actualMessage = parts[1].trim();
                } else {
                    // Just selected by number, show info
                    showNPCInfo(player, npc);
                    selectedNPCs.put(player.getUniqueId(), npc.getUuid());
                    return;
                }
            } else if (numberedList == null) {
                player.sendMessage(ChatColor.GRAY + "Type @ to see a numbered list of nearby NPCs first.");
                return;
            } else {
                player.sendMessage(ChatColor.RED + "Invalid selection. Use a number between 1 and " + numberedList.size());
                return;
            }
        }

        // Check if player has a selected NPC (from right-click or previous @)
        if (npc == null) {
            UUID selectedId = selectedNPCs.get(player.getUniqueId());
            if (selectedId != null) {
                npc = npcManager.getNPC(selectedId);
                if (npc != null && npc.isAlive() && npc.isSpawned()) {
                    // Check still in range
                    if (player.getLocation().distance(npc.getCurrentLocation()) > maxDistance) {
                        npc = null;
                        selectedNPCs.remove(player.getUniqueId());
                    }
                } else {
                    npc = null;
                }
            }
        }

        // If no selected NPC, try to find one
        if (npc == null) {
            // Get all nearby NPCs sorted by distance
            List<AINpc> nearbyNPCs = npcManager.getNPCsNearLocation(player.getLocation(), maxDistance);

            // Check if there are multiple NPCs in close proximity (crowded)
            if (nearbyNPCs.size() > 1) {
                // Check if first two are within 3 blocks of each other (crowded)
                AINpc first = nearbyNPCs.get(0);
                AINpc second = nearbyNPCs.get(1);
                double distBetween = first.getCurrentLocation().distance(second.getCurrentLocation());

                if (distBetween < 3.0) {
                    // Try to match by name first in crowded situations
                    AINpc namedNpc = findNPCByName(player, firstWord, maxDistance);
                    if (namedNpc != null) {
                        npc = namedNpc;
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            actualMessage = parts[1].trim();
                        } else {
                            showNPCInfo(player, npc);
                            selectedNPCs.put(player.getUniqueId(), npc.getUuid());
                            return;
                        }
                    } else {
                        // Check line of sight - prefer NPC player is looking at
                        AINpc lookingAt = getNPCPlayerIsLookingAt(player, nearbyNPCs);
                        if (lookingAt != null) {
                            npc = lookingAt;
                            actualMessage = message;
                        } else {
                            // Still crowded and can't determine target, show numbered list
                            showNearbyNPCsNumbered(player);
                            player.sendMessage(ChatColor.YELLOW + "Multiple NPCs nearby! Use @<name> or @<number> to specify who you're talking to.");
                            player.sendMessage(ChatColor.GRAY + "Example: @" + first.getName().split(" ")[0] + " hello  OR  @1 hello");
                            return;
                        }
                    }
                } else {
                    // Not crowded, use nearest
                    npc = first;
                }
            } else if (nearbyNPCs.size() == 1) {
                // Only one NPC, easy choice
                npc = nearbyNPCs.get(0);
            } else {
                // Try name match as last resort
                AINpc namedNpc = findNPCByName(player, firstWord, maxDistance);
                if (namedNpc != null) {
                    npc = namedNpc;
                    if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                        actualMessage = parts[1].trim();
                    }
                }
            }
        }

        if (npc == null) {
            player.sendMessage(ChatColor.GRAY + "*No one is nearby to hear you*");
            showNearbyNPCsNumbered(player);
            return;
        }

        if (!npc.isAlive()) {
            player.sendMessage(ChatColor.GRAY + "*" + npc.getName() + " cannot respond...*");
            return;
        }

        // Start/continue conversation
        activeConversations.put(player.getUniqueId(), npc.getUuid());
        selectedNPCs.put(player.getUniqueId(), npc.getUuid());
        processNPCConversation(player, npc, actualMessage);
    }

    /**
     * Get the NPC the player is looking at (line-of-sight check)
     */
    private AINpc getNPCPlayerIsLookingAt(Player player, List<AINpc> candidates) {
        // Get player's view direction
        org.bukkit.util.Vector playerDirection = player.getLocation().getDirection().normalize();
        AINpc bestMatch = null;
        double bestScore = 0.5; // Minimum dot product (within ~60 degree cone)

        for (AINpc npc : candidates) {
            if (npc.getCurrentLocation() == null) continue;

            // Get direction from player to NPC
            org.bukkit.util.Vector toNpc = npc.getCurrentLocation().toVector()
                    .subtract(player.getLocation().toVector()).normalize();

            // Dot product - higher means more aligned with player's view
            double dot = playerDirection.dot(toNpc);

            if (dot > bestScore) {
                bestScore = dot;
                bestMatch = npc;
            }
        }

        return bestMatch;
    }

    /**
     * Find NPC by name within range
     */
    private AINpc findNPCByName(Player player, String name, double maxDistance) {
        if (name == null || name.isEmpty()) return null;

        String searchName = name.toLowerCase();
        AINpc bestMatch = null;
        double bestDistance = maxDistance;

        for (AINpc npc : npcManager.getAllNPCs()) {
            if (!npc.isAlive() || !npc.isSpawned()) continue;
            if (npc.getCurrentLocation() == null) continue;

            String npcName = npc.getName().toLowerCase();
            // Check if name matches (full name or first name)
            if (npcName.equals(searchName) ||
                    npcName.startsWith(searchName) ||
                    npcName.split(" ")[0].equals(searchName)) {

                double distance = player.getLocation().distance(npc.getCurrentLocation());
                if (distance < bestDistance) {
                    bestMatch = npc;
                    bestDistance = distance;
                }
            }
        }

        return bestMatch;
    }

    /**
     * Show numbered list of nearby NPCs for easy selection
     */
    private void showNearbyNPCsNumbered(Player player) {
        double maxDistance = plugin.getConfig().getDouble("npcs.interaction-distance", 10);
        List<AINpc> nearbyNPCs = npcManager.getNPCsNearLocation(player.getLocation(), maxDistance);

        if (nearbyNPCs.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No NPCs nearby. Try getting closer to someone.");
            numberedSelections.remove(player.getUniqueId());
            return;
        }

        // Store the list for numbered selection
        numberedSelections.put(player.getUniqueId(), nearbyNPCs);

        player.sendMessage(ChatColor.GOLD + "═══ Nearby NPCs ═══");
        int num = 1;
        for (AINpc npc : nearbyNPCs) {
            double distance = player.getLocation().distance(npc.getCurrentLocation());
            String dialectInfo = npc.getDialect() != null ? " [" + npc.getDialect().getName() + "]" : "";
            player.sendMessage(ChatColor.YELLOW + "" + num + ". " + ChatColor.WHITE + npc.getName() +
                    ChatColor.GRAY + " (" + npc.getFaction() + ")" + dialectInfo + " - " +
                    String.format("%.1f", distance) + " blocks");
            num++;
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "To talk, use one of these:");
        player.sendMessage(ChatColor.WHITE + "  @<number> <message>" + ChatColor.GRAY + " - e.g. @1 hello");
        player.sendMessage(ChatColor.WHITE + "  @<name> <message>" + ChatColor.GRAY + " - e.g. @" + nearbyNPCs.get(0).getName().split(" ")[0] + " hello");
        player.sendMessage(ChatColor.WHITE + "  @<message>" + ChatColor.GRAY + " - talks to whoever you're looking at");
    }

    /**
     * Show info about a specific NPC
     */
    private void showNPCInfo(Player player, AINpc npc) {
        player.sendMessage(ChatColor.GOLD + "═══ " + npc.getName() + " ═══");
        player.sendMessage(ChatColor.GRAY + "Faction: " + ChatColor.WHITE + npc.getFaction());
        player.sendMessage(ChatColor.GRAY + "Mood: " + ChatColor.WHITE + npc.getCurrentMood());
        if (npc.canGiveQuests()) {
            player.sendMessage(ChatColor.GREEN + "This NPC can give quests!");
        }
        player.sendMessage(ChatColor.GRAY + "Say something to start a conversation.");
    }

    /**
     * Select an NPC for conversation (called from right-click)
     * NPC will stop, face the player, and stay focused until conversation ends
     */
    public void selectNPC(Player player, AINpc npc) {
        // Clear any previous engagement
        UUID previousNpcId = selectedNPCs.get(player.getUniqueId());
        if (previousNpcId != null && !previousNpcId.equals(npc.getUuid())) {
            AINpc previousNpc = npcManager.getNPC(previousNpcId);
            if (previousNpc != null) {
                previousNpc.disengageFromPlayer();
            }
        }

        selectedNPCs.put(player.getUniqueId(), npc.getUuid());
        activeConversations.put(player.getUniqueId(), npc.getUuid());

        // Engage the NPC - they will stop wandering and face the player
        npc.engageWithPlayer(player.getUniqueId());

        // Stop NPC movement immediately
        if (npc.getBukkitEntity() instanceof org.bukkit.entity.Mob mob) {
            mob.getPathfinder().stopPathfinding();
        }

        player.sendMessage(ChatColor.GREEN + "Now talking to " + ChatColor.GOLD + npc.getName());
        player.sendMessage(ChatColor.GRAY + "Say 'bye' or walk away to end the conversation.");
    }

    /**
     * Clear NPC selection and disengage the NPC
     */
    public void clearSelection(Player player) {
        UUID npcId = selectedNPCs.remove(player.getUniqueId());
        if (npcId != null) {
            AINpc npc = npcManager.getNPC(npcId);
            if (npc != null) {
                npc.disengageFromPlayer();
            }
        }
    }

    // Keywords that end conversations
    private static final String[] GOODBYE_KEYWORDS = {
            "bye", "goodbye", "farewell", "see you", "later", "gotta go",
            "have to go", "leaving", "cya", "gtg", "take care"
    };

    /**
     * Process a conversation with an NPC
     */
    private void processNPCConversation(Player player, AINpc npc, String message) {
        String lowerMessage = message.toLowerCase();

        // Check if player is saying goodbye
        for (String goodbye : GOODBYE_KEYWORDS) {
            if (lowerMessage.contains(goodbye)) {
                // Send a farewell response from NPC
                player.sendMessage(ChatColor.WHITE + "You: " + ChatColor.GRAY + message);
                String farewellResponse = generateFarewellResponse(npc);
                player.sendMessage(ChatColor.GOLD + npc.getName() + ": " + ChatColor.WHITE + farewellResponse);

                // End the conversation
                endConversation(player);
                return;
            }
        }

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
     * Generate a farewell response based on NPC personality/dialect
     */
    private String generateFarewellResponse(AINpc npc) {
        java.util.Random rand = new java.util.Random();
        String[] standardFarewells = {
                "Safe travels, friend.",
                "Until we meet again.",
                "Farewell, traveler.",
                "Take care out there.",
                "May your path be clear."
        };

        String[] hostileFarewells = {
                "Good riddance.",
                "Don't let the door hit you.",
                "Finally, some peace.",
                "Off with you then."
        };

        String[] friendlyFarewells = {
                "It was lovely chatting! Come back soon!",
                "Safe journeys, my friend!",
                "Do come visit again!",
                "Blessings on your travels!"
        };

        if (npc.isHostile()) {
            return hostileFarewells[rand.nextInt(hostileFarewells.length)];
        } else if (npc.getCurrentMood().equals("pleased")) {
            return friendlyFarewells[rand.nextInt(friendlyFarewells.length)];
        } else {
            return standardFarewells[rand.nextInt(standardFarewells.length)];
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
     * End a conversation and disengage the NPC
     */
    public void endConversation(Player player) {
        UUID npcId = activeConversations.remove(player.getUniqueId());
        selectedNPCs.remove(player.getUniqueId());
        pendingQuestOffers.remove(player.getUniqueId());

        if (npcId != null) {
            AINpc npc = npcManager.getNPC(npcId);
            if (npc != null) {
                // Disengage the NPC so they resume normal behavior
                npc.disengageFromPlayer();
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

    /**
     * Clean up all conversation state for a player (used on death/quit/respawn)
     */
    public void cleanupPlayerState(UUID playerUuid) {
        // Clear active conversation
        UUID npcId = activeConversations.remove(playerUuid);
        if (npcId != null) {
            AINpc npc = npcManager.getNPC(npcId);
            if (npc != null) {
                npc.disengageFromPlayer();
            }
        }

        // Clear selected NPC (might be different from active conversation)
        UUID selectedNpcId = selectedNPCs.remove(playerUuid);
        if (selectedNpcId != null && !selectedNpcId.equals(npcId)) {
            AINpc selectedNpc = npcManager.getNPC(selectedNpcId);
            if (selectedNpc != null) {
                selectedNpc.disengageFromPlayer();
            }
        }

        // Clear other state
        pendingQuestOffers.remove(playerUuid);
        numberedSelections.remove(playerUuid);

        plugin.debug("Cleaned up conversation state for player " + playerUuid);
    }

    /**
     * Handle player death - clean up conversation state
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        cleanupPlayerState(player.getUniqueId());
    }

    /**
     * Handle player quit - clean up conversation state
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cleanupPlayerState(player.getUniqueId());
    }

    /**
     * Handle player respawn - ensure clean state
     */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Double-check cleanup on respawn in case death event was missed
        cleanupPlayerState(player.getUniqueId());
    }
}
