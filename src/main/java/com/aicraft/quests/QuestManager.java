package com.aicraft.quests;

import com.aicraft.AICompanions;
import com.aicraft.ai.AIManager;
import com.aicraft.database.DatabaseManager;
import com.aicraft.npcs.AINpc;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages quests for players
 */
public class QuestManager {

    private final AICompanions plugin;
    private final DatabaseManager database;
    private final AIManager aiManager;
    private final Gson gson = new Gson();

    // Player UUID -> List of quests
    private final Map<UUID, List<Quest>> playerQuests = new ConcurrentHashMap<>();

    // Quest types with their descriptions
    private static final Map<String, String> QUEST_TYPES = new LinkedHashMap<>();
    static {
        QUEST_TYPES.put("fetch", "Collect and bring items");
        QUEST_TYPES.put("kill", "Defeat enemies");
        QUEST_TYPES.put("explore", "Discover locations");
        QUEST_TYPES.put("delivery", "Deliver items to NPCs");
    }

    public QuestManager(AICompanions plugin, DatabaseManager database, AIManager aiManager) {
        this.plugin = plugin;
        this.database = database;
        this.aiManager = aiManager;
    }

    /**
     * Generate and offer a quest from an NPC
     */
    public CompletableFuture<Quest> generateQuest(AINpc npc, Player player) {
        // Pick a random quest type
        List<String> types = plugin.getConfig().getStringList("quests.types");
        if (types.isEmpty()) types = new ArrayList<>(QUEST_TYPES.keySet());
        String questType = types.get(new Random().nextInt(types.size()));

        return aiManager.generateQuest(npc, player.getName(), questType)
                .thenApply(response -> {
                    if (response == null) {
                        return createFallbackQuest(npc, player, questType);
                    }

                    try {
                        // Parse AI-generated quest
                        JsonObject questData = gson.fromJson(response, JsonObject.class);
                        Quest quest = new Quest(player.getUniqueId());
                        quest.setNpcUuid(npc.getUuid());
                        quest.setQuestType(questType);

                        quest.setTitle(questData.has("title") ?
                                questData.get("title").getAsString() : "A Task for " + player.getName());
                        quest.setDescription(questData.has("description") ?
                                questData.get("description").getAsString() : "Help " + npc.getName());
                        quest.setObjective(questData.has("objective") ?
                                questData.get("objective").getAsString() : "Complete the task");
                        quest.setTarget(questData.has("target") ?
                                questData.get("target").getAsString() : "");
                        quest.setAmount(questData.has("amount") ?
                                questData.get("amount").getAsInt() : 1);
                        quest.setRewardXp(questData.has("reward_xp") ?
                                questData.get("reward_xp").getAsInt() : 100);

                        if (questData.has("reward_items")) {
                            questData.getAsJsonArray("reward_items").forEach(item ->
                                    quest.addRewardItem(item.getAsString()));
                        }

                        return quest;

                    } catch (Exception e) {
                        plugin.debug("Failed to parse quest JSON: " + e.getMessage());
                        return createFallbackQuest(npc, player, questType);
                    }
                });
    }

    /**
     * Create a fallback quest when AI generation fails
     */
    private Quest createFallbackQuest(AINpc npc, Player player, String questType) {
        Quest quest = new Quest(player.getUniqueId());
        quest.setNpcUuid(npc.getUuid());
        quest.setQuestType(questType);

        Random random = new Random();

        switch (questType) {
            case "fetch" -> {
                Material[] items = {Material.DIAMOND, Material.IRON_INGOT, Material.GOLD_INGOT,
                        Material.WHEAT, Material.COAL, Material.LEATHER};
                Material target = items[random.nextInt(items.length)];
                int amount = random.nextInt(10) + 5;

                quest.setTitle("Gathering Supplies");
                quest.setDescription(npc.getName() + " needs your help gathering materials.");
                quest.setObjective("Collect " + amount + " " + formatMaterialName(target));
                quest.setTarget(target.name());
                quest.setAmount(amount);
                quest.setRewardXp(amount * 10);
            }
            case "kill" -> {
                String[] mobs = {"ZOMBIE", "SKELETON", "SPIDER", "CREEPER"};
                String target = mobs[random.nextInt(mobs.length)];
                int amount = random.nextInt(10) + 5;

                quest.setTitle("Creature Elimination");
                quest.setDescription("The area is plagued by dangerous creatures.");
                quest.setObjective("Defeat " + amount + " " + target.toLowerCase() + "s");
                quest.setTarget(target);
                quest.setAmount(amount);
                quest.setRewardXp(amount * 20);
            }
            default -> {
                quest.setTitle("A Simple Task");
                quest.setDescription(npc.getName() + " has a task for you.");
                quest.setObjective("Complete the task");
                quest.setTarget("");
                quest.setAmount(1);
                quest.setRewardXp(50);
            }
        }

        return quest;
    }

    /**
     * Accept a quest for a player
     */
    public void acceptQuest(Player player, Quest quest) {
        List<Quest> quests = playerQuests.computeIfAbsent(player.getUniqueId(),
                k -> Collections.synchronizedList(new ArrayList<>()));
        quests.add(quest);
        database.saveQuest(quest);

        player.sendMessage(ChatColor.GREEN + "Quest accepted: " + ChatColor.GOLD + quest.getTitle());
        player.sendMessage(ChatColor.GRAY + quest.getObjective());
    }

    /**
     * Get active quests for a player
     */
    public List<Quest> getActiveQuests(UUID playerUuid) {
        List<Quest> quests = playerQuests.get(playerUuid);
        if (quests == null) {
            // Load from database
            quests = database.loadPlayerQuests(playerUuid);
            playerQuests.put(playerUuid, Collections.synchronizedList(new ArrayList<>(quests)));
        }
        return quests.stream()
                .filter(q -> q.getStatus() == Quest.QuestStatus.ACTIVE ||
                        q.getStatus() == Quest.QuestStatus.READY_TO_TURN_IN)
                .toList();
    }

    /**
     * Update quest progress for a player
     */
    public void updateProgress(Player player, String questType, String target, int amount) {
        List<Quest> quests = getActiveQuests(player.getUniqueId());

        for (Quest quest : quests) {
            if (!quest.getQuestType().equalsIgnoreCase(questType)) continue;
            if (quest.getStatus() != Quest.QuestStatus.ACTIVE) continue;

            // Check if target matches
            if (target != null && quest.getTarget() != null &&
                    !quest.getTarget().equalsIgnoreCase(target)) continue;

            quest.incrementProgress(amount);
            database.saveQuest(quest);

            if (quest.isComplete()) {
                player.sendMessage(ChatColor.GREEN + "Quest objective complete! " +
                        ChatColor.GOLD + quest.getTitle());
                player.sendMessage(ChatColor.GRAY + "Return to the quest giver to claim your reward.");
            } else {
                player.sendMessage(ChatColor.GRAY + "[Quest] " + quest.getTitle() +
                        ": " + quest.getProgressString());
            }
        }
    }

    /**
     * Turn in a completed quest
     */
    public boolean turnInQuest(Player player, Quest quest, AINpc npc) {
        if (quest.getStatus() != Quest.QuestStatus.READY_TO_TURN_IN) {
            player.sendMessage(ChatColor.RED + "This quest is not ready to turn in yet.");
            return false;
        }

        // Verify it's the right NPC
        if (quest.getNpcUuid() != null && !quest.getNpcUuid().equals(npc.getUuid())) {
            player.sendMessage(ChatColor.RED + "This quest must be turned in to " + npc.getName());
            return false;
        }

        // Grant rewards
        quest.complete();

        // XP reward
        if (quest.getRewardXp() > 0) {
            player.giveExp(quest.getRewardXp());
            player.sendMessage(ChatColor.GREEN + "+" + quest.getRewardXp() + " XP");
        }

        // Item rewards
        for (String itemName : quest.getRewardItems()) {
            try {
                Material material = Material.valueOf(itemName.toUpperCase());
                player.getInventory().addItem(new ItemStack(material, 1));
                player.sendMessage(ChatColor.GREEN + "+1 " + formatMaterialName(material));
            } catch (IllegalArgumentException e) {
                plugin.debug("Invalid reward item: " + itemName);
            }
        }

        player.sendMessage(ChatColor.GOLD + "Quest completed: " + quest.getTitle());
        database.saveQuest(quest);

        return true;
    }

    /**
     * Abandon a quest
     */
    public void abandonQuest(Player player, Quest quest) {
        quest.abandon();
        database.saveQuest(quest);
        player.sendMessage(ChatColor.YELLOW + "Quest abandoned: " + quest.getTitle());
    }

    /**
     * Save all quests
     */
    public void saveAll() {
        for (List<Quest> quests : playerQuests.values()) {
            for (Quest quest : quests) {
                if (quest.getStatus() == Quest.QuestStatus.ACTIVE ||
                        quest.getStatus() == Quest.QuestStatus.READY_TO_TURN_IN) {
                    database.saveQuest(quest);
                }
            }
        }
    }

    /**
     * Format material name for display
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * Get quest by UUID
     */
    public Quest getQuest(UUID questUuid) {
        for (List<Quest> quests : playerQuests.values()) {
            for (Quest quest : quests) {
                if (quest.getUuid().equals(questUuid)) {
                    return quest;
                }
            }
        }
        return null;
    }
}
