package com.aicraft.ai;

import com.aicraft.AICompanions;
import com.aicraft.ai.providers.AIProvider;
import com.aicraft.ai.providers.ClaudeProvider;
import com.aicraft.ai.providers.OpenAIProvider;
import com.aicraft.npcs.AINpc;
import org.bukkit.Location;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages AI providers and handles rate limiting for AI requests
 */
public class AIManager {

    private final AICompanions plugin;
    private AIProvider primaryProvider;
    private AIProvider fallbackProvider;

    // Rate limiting
    private final Map<UUID, RateLimitTracker> playerRateLimits = new ConcurrentHashMap<>();
    private int rateLimit;

    public AIManager(AICompanions plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String providerName = plugin.getConfig().getString("ai.provider", "claude");
        this.rateLimit = plugin.getConfig().getInt("ai.rate-limit", 10);

        // Initialize primary provider
        if ("claude".equalsIgnoreCase(providerName)) {
            primaryProvider = new ClaudeProvider(plugin);
            fallbackProvider = new OpenAIProvider(plugin);
        } else {
            primaryProvider = new OpenAIProvider(plugin);
            fallbackProvider = new ClaudeProvider(plugin);
        }

        plugin.getLogger().info("AI Provider set to: " + primaryProvider.getName());
    }

    /**
     * Generate a chat response from an NPC asynchronously
     */
    public CompletableFuture<String> generateResponse(AINpc npc, String playerName, String playerMessage, String conversationHistory) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildPrompt(npc, playerName, playerMessage, conversationHistory);
                plugin.getLogger().info("Sending AI request for NPC: " + npc.getName() + " (player: " + playerName + ")");

                String response = primaryProvider.chat(prompt);

                if (response == null || response.isEmpty()) {
                    plugin.getLogger().warning("Primary provider returned empty response, trying fallback...");
                    response = fallbackProvider.chat(prompt);
                }

                if (response == null || response.isEmpty()) {
                    plugin.getLogger().warning("Both AI providers returned empty - using default response");
                    return getDefaultResponse(npc);
                }

                plugin.getLogger().info("AI response received successfully for " + npc.getName());
                return response;

            } catch (Exception e) {
                plugin.getLogger().warning("AI request failed: " + e.getMessage());
                e.printStackTrace();
                try {
                    String prompt = buildPrompt(npc, playerName, playerMessage, conversationHistory);
                    return fallbackProvider.chat(prompt);
                } catch (Exception e2) {
                    plugin.getLogger().warning("Fallback AI also failed: " + e2.getMessage());
                    return getDefaultResponse(npc);
                }
            }
        });
    }

    /**
     * Generate a backstory for a new NPC
     */
    public CompletableFuture<String> generateBackstory(String npcName, String faction, String personality) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildBackstoryPrompt(npcName, faction, personality);
                String response = primaryProvider.chat(prompt);
                return response != null ? response : generateFallbackBackstory(npcName, faction);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to generate backstory: " + e.getMessage());
                return generateFallbackBackstory(npcName, faction);
            }
        });
    }

    /**
     * Generate a quest from an NPC
     */
    public CompletableFuture<String> generateQuest(AINpc npc, String playerName, String questType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String prompt = buildQuestPrompt(npc, playerName, questType);
                return primaryProvider.chat(prompt);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to generate quest: " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Check if a player can make another AI request (rate limiting)
     */
    public boolean canMakeRequest(UUID playerId) {
        RateLimitTracker tracker = playerRateLimits.computeIfAbsent(playerId, k -> new RateLimitTracker());
        return tracker.canMakeRequest(rateLimit);
    }

    /**
     * Record that a player made a request
     */
    public void recordRequest(UUID playerId) {
        RateLimitTracker tracker = playerRateLimits.computeIfAbsent(playerId, k -> new RateLimitTracker());
        tracker.recordRequest();
    }

    /**
     * Get remaining requests for a player
     */
    public int getRemainingRequests(UUID playerId) {
        RateLimitTracker tracker = playerRateLimits.get(playerId);
        if (tracker == null) return rateLimit;
        return Math.max(0, rateLimit - tracker.getRequestsInLastMinute());
    }

    private String buildPrompt(AINpc npc, String playerName, String playerMessage, String conversationHistory) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are roleplaying as an NPC in a Minecraft world. Stay completely in character.\n\n");

        prompt.append("=== YOUR CHARACTER ===\n");
        prompt.append("Name: ").append(npc.getName()).append("\n");
        prompt.append("Faction: ").append(npc.getFaction()).append("\n");
        prompt.append("Personality: ").append(npc.getPersonality()).append("\n");
        prompt.append("Backstory: ").append(npc.getBackstory()).append("\n");

        if (npc.getCurrentMood() != null) {
            prompt.append("Current Mood: ").append(npc.getCurrentMood()).append("\n");
        }

        // Add dialect instructions
        Dialect dialect = npc.getDialect();
        prompt.append("Speech Style: ").append(dialect.getName()).append("\n");

        // Add awareness of nearby NPCs
        String nearbyNPCInfo = getNearbyNPCInfo(npc);
        if (!nearbyNPCInfo.isEmpty()) {
            prompt.append("\n=== PEOPLE YOU KNOW NEARBY ===\n");
            prompt.append(nearbyNPCInfo);
            prompt.append("(You can reference these NPCs in conversation, send the player to them, or share gossip about them)\n");
        }

        prompt.append("\n=== ROLEPLAY RULES ===\n");
        prompt.append("1. Stay completely in character as ").append(npc.getName()).append("\n");
        prompt.append("2. Keep responses SHORT (1-3 sentences, like real game NPCs)\n");
        prompt.append("3. Reference your backstory and personality naturally\n");
        prompt.append("4. React based on your faction's relationship with the player\n");
        prompt.append("5. You can offer quests, trade, share rumors, or just chat\n");
        prompt.append("6. ").append(dialect.getPrompt()).append("\n");
        prompt.append("7. If attacked or threatened, respond appropriately to your personality\n");
        prompt.append("8. DO NOT break character or mention being an AI\n");
        prompt.append("9. You can suggest the player visit other NPCs you know for specific needs\n");

        // Special instructions for Cultists
        if ("Cultists".equalsIgnoreCase(npc.getFaction())) {
            prompt.append("10. You worship the god Geodjian. Be mysterious and try to subtly recruit the player.\n");
            prompt.append("11. Never reveal your true intentions immediately. Be deceptive but intriguing.\n");
        }

        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            prompt.append("\n=== CONVERSATION HISTORY ===\n");
            prompt.append(conversationHistory);
        }

        prompt.append("\n=== CURRENT INTERACTION ===\n");
        prompt.append("Player '").append(playerName).append("' says: \"").append(playerMessage).append("\"\n");
        prompt.append("\nRespond as ").append(npc.getName()).append(" (in character, 1-3 sentences):");

        return prompt.toString();
    }

    /**
     * Get information about nearby NPCs for cross-NPC awareness
     */
    private String getNearbyNPCInfo(AINpc npc) {
        StringBuilder info = new StringBuilder();
        Location npcLoc = npc.getCurrentLocation();
        if (npcLoc == null) return "";

        List<AINpc> nearbyNPCs = plugin.getNPCManager().getNPCsNearLocation(npcLoc, 150);
        int count = 0;
        for (AINpc other : nearbyNPCs) {
            if (other.getUuid().equals(npc.getUuid())) continue; // Skip self
            if (!other.isAlive()) continue;
            if (count >= 5) break; // Limit to 5 nearby NPCs to keep prompt size reasonable

            String relationship = "";
            if (other.getFaction().equals(npc.getFaction())) {
                relationship = " (your faction ally)";
            } else if (plugin.getFactionManager().areHostile(npc.getFaction(), other.getFaction())) {
                relationship = " (enemy faction)";
            }

            info.append("- ").append(other.getName())
                .append(" (").append(other.getFaction()).append(")")
                .append(relationship).append("\n");
            count++;
        }

        return info.toString();
    }

    private String buildBackstoryPrompt(String npcName, String faction, String personality) {
        return "Generate a brief backstory (2-3 sentences) for a Minecraft NPC with these traits:\n" +
                "Name: " + npcName + "\n" +
                "Faction: " + faction + "\n" +
                "Personality: " + personality + "\n\n" +
                "The backstory should:\n" +
                "- Explain how they joined their faction\n" +
                "- Include a personal motivation or goal\n" +
                "- Fit a medieval fantasy setting\n" +
                "- Be unique and memorable\n\n" +
                "Write ONLY the backstory, no introduction or explanation:";
    }

    private String buildQuestPrompt(AINpc npc, String playerName, String questType) {
        return "Generate a quest for a Minecraft NPC to give a player.\n\n" +
                "NPC Name: " + npc.getName() + "\n" +
                "NPC Faction: " + npc.getFaction() + "\n" +
                "NPC Personality: " + npc.getPersonality() + "\n" +
                "Quest Type: " + questType + "\n" +
                "Player Name: " + playerName + "\n\n" +
                "Format your response as JSON:\n" +
                "{\n" +
                "  \"title\": \"Quest title\",\n" +
                "  \"description\": \"What the NPC says when giving the quest\",\n" +
                "  \"objective\": \"Clear objective description\",\n" +
                "  \"target\": \"item name or mob type or location\",\n" +
                "  \"amount\": number,\n" +
                "  \"reward_xp\": number,\n" +
                "  \"reward_items\": [\"item1\", \"item2\"]\n" +
                "}\n\n" +
                "Make the quest fit the NPC's personality and faction. Keep it achievable in Minecraft.";
    }

    private String getDefaultResponse(AINpc npc) {
        String[] responses = {
                "*" + npc.getName() + " nods silently*",
                "Hmm... I have nothing to say right now.",
                "*" + npc.getName() + " seems distracted*",
                "Perhaps we can talk later, traveler.",
                "*" + npc.getName() + " is lost in thought*"
        };
        return responses[(int) (Math.random() * responses.length)];
    }

    private String generateFallbackBackstory(String npcName, String faction) {
        return npcName + " is a member of the " + faction +
                ", having joined after seeking a new purpose in life. " +
                "They prefer to keep their past private, but are known for their dedication to their faction's cause.";
    }

    public String getProviderName() {
        return primaryProvider != null ? primaryProvider.getName() : "None";
    }

    public AIProvider getPrimaryProvider() {
        return primaryProvider;
    }

    /**
     * Test the AI connection
     */
    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = primaryProvider.chat("Say 'Connection successful!' in exactly those words.");
                return response != null && !response.isEmpty();
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * Rate limit tracker for individual players
     */
    private static class RateLimitTracker {
        private final java.util.Deque<Long> requestTimes = new java.util.LinkedList<>();

        public synchronized boolean canMakeRequest(int limit) {
            cleanOldRequests();
            return requestTimes.size() < limit;
        }

        public synchronized void recordRequest() {
            cleanOldRequests();
            requestTimes.addLast(System.currentTimeMillis());
        }

        public synchronized int getRequestsInLastMinute() {
            cleanOldRequests();
            return requestTimes.size();
        }

        private void cleanOldRequests() {
            long oneMinuteAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1);
            while (!requestTimes.isEmpty() && requestTimes.peekFirst() < oneMinuteAgo) {
                requestTimes.pollFirst();
            }
        }
    }
}
