package com.aicraft.npcs.boss;

import com.aicraft.npcs.AINpc;

/**
 * Wrapper class for boss NPCs with additional boss-specific data
 */
public class BossNpc {

    private final AINpc baseNpc;
    private final BossDefinition definition;
    private int phase = 1;
    private long spawnTime;

    public BossNpc(AINpc baseNpc, BossDefinition definition) {
        this.baseNpc = baseNpc;
        this.definition = definition;
        this.spawnTime = System.currentTimeMillis();
    }

    public AINpc getBaseNpc() {
        return baseNpc;
    }

    public BossDefinition getDefinition() {
        return definition;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    /**
     * Check health thresholds for phase transitions
     */
    public void checkPhaseTransition() {
        double healthPercent = baseNpc.getHealth() / baseNpc.getMaxHealth();

        if (healthPercent <= 0.25 && phase < 3) {
            phase = 3;
        } else if (healthPercent <= 0.5 && phase < 2) {
            phase = 2;
        }
    }

    public long getSpawnTime() {
        return spawnTime;
    }

    public long getAliveTime() {
        return System.currentTimeMillis() - spawnTime;
    }
}
