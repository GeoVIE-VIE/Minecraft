package com.aicraft.quests;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a quest that can be given by NPCs to players
 */
public class Quest {

    private final UUID uuid;
    private final UUID playerUuid;
    private UUID npcUuid;

    private String title;
    private String description;
    private String objective;
    private String questType; // fetch, kill, escort, explore, delivery
    private String target;    // item name, mob type, location, etc.
    private int amount;
    private int progress;

    private int rewardXp;
    private List<String> rewardItems;

    private QuestStatus status;
    private long createdAt;
    private long completedAt;

    public Quest(UUID uuid, UUID playerUuid) {
        this.uuid = uuid;
        this.playerUuid = playerUuid;
        this.rewardItems = new ArrayList<>();
        this.status = QuestStatus.ACTIVE;
        this.createdAt = System.currentTimeMillis();
        this.progress = 0;
    }

    public Quest(UUID playerUuid) {
        this(UUID.randomUUID(), playerUuid);
    }

    // === Progress Management ===

    public void incrementProgress() {
        incrementProgress(1);
    }

    public void incrementProgress(int amount) {
        this.progress = Math.min(this.progress + amount, this.amount);
        if (this.progress >= this.amount) {
            this.status = QuestStatus.READY_TO_TURN_IN;
        }
    }

    public boolean isComplete() {
        return progress >= amount;
    }

    public float getProgressPercentage() {
        if (amount == 0) return 100f;
        return (progress / (float) amount) * 100f;
    }

    public void complete() {
        this.status = QuestStatus.COMPLETED;
        this.completedAt = System.currentTimeMillis();
    }

    public void fail() {
        this.status = QuestStatus.FAILED;
        this.completedAt = System.currentTimeMillis();
    }

    public void abandon() {
        this.status = QuestStatus.ABANDONED;
        this.completedAt = System.currentTimeMillis();
    }

    // === Getters and Setters ===

    public UUID getUuid() {
        return uuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public UUID getNpcUuid() {
        return npcUuid;
    }

    public void setNpcUuid(UUID npcUuid) {
        this.npcUuid = npcUuid;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getObjective() {
        return objective;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public String getQuestType() {
        return questType;
    }

    public void setQuestType(String questType) {
        this.questType = questType;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getRewardXp() {
        return rewardXp;
    }

    public void setRewardXp(int rewardXp) {
        this.rewardXp = rewardXp;
    }

    public List<String> getRewardItems() {
        return rewardItems;
    }

    public void setRewardItems(List<String> rewardItems) {
        this.rewardItems = rewardItems != null ? rewardItems : new ArrayList<>();
    }

    public void addRewardItem(String item) {
        this.rewardItems.add(item);
    }

    public QuestStatus getStatus() {
        return status;
    }

    public void setStatus(QuestStatus status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(long completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * Get a formatted progress string
     */
    public String getProgressString() {
        return String.format("%d/%d", progress, amount);
    }

    @Override
    public String toString() {
        return "Quest{" +
                "title='" + title + '\'' +
                ", type='" + questType + '\'' +
                ", progress=" + progress + "/" + amount +
                ", status=" + status +
                '}';
    }

    public enum QuestStatus {
        ACTIVE,
        READY_TO_TURN_IN,
        COMPLETED,
        FAILED,
        ABANDONED
    }
}
