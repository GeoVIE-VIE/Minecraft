package com.aicraft.factions;

import com.aicraft.AICompanions;
import com.aicraft.database.DatabaseManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all factions in the server
 */
public class FactionManager {

    private final AICompanions plugin;
    private final DatabaseManager database;
    private final Map<String, Faction> factions = new ConcurrentHashMap<>();

    public FactionManager(AICompanions plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Load factions from database, creating defaults if needed
     */
    public void loadFactions() {
        // Load from database
        List<Faction> loaded = database.loadAllFactions();

        if (loaded.isEmpty()) {
            // Create default factions from config
            createDefaultFactions();
        } else {
            for (Faction faction : loaded) {
                factions.put(faction.getName().toLowerCase(), faction);
            }
        }

        plugin.getLogger().info("Loaded " + factions.size() + " factions");
    }

    /**
     * Create default factions from configuration
     */
    private void createDefaultFactions() {
        plugin.getLogger().info("Creating default factions...");

        List<Map<?, ?>> defaults = plugin.getConfig().getMapList("factions.defaults");

        for (Map<?, ?> factionData : defaults) {
            String name = (String) factionData.get("name");
            if (name == null) continue;

            Faction faction = new Faction(name);
            faction.setDescription((String) factionData.getOrDefault("description", ""));

            String colorStr = (String) factionData.getOrDefault("color", "WHITE");
            faction.setColorFromString(colorStr);

            // Set hostile factions
            Object hostileTo = factionData.get("hostile-to");
            if (hostileTo instanceof List<?> hostileList) {
                Set<String> hostileFactions = new HashSet<>();
                for (Object h : hostileList) {
                    hostileFactions.add(h.toString());
                }
                faction.setHostileFactions(hostileFactions);
                faction.setHostileByDefault(!hostileFactions.isEmpty());
            }

            factions.put(name.toLowerCase(), faction);
            database.saveFaction(faction);

            plugin.debug("Created faction: " + name);
        }
    }

    /**
     * Reload factions
     */
    public void reload() {
        factions.clear();
        loadFactions();
    }

    /**
     * Create a new faction
     */
    public Faction createFaction(String name, ChatColor color, String description) {
        if (factions.containsKey(name.toLowerCase())) {
            return null; // Already exists
        }

        Faction faction = new Faction(name);
        faction.setColor(color);
        faction.setDescription(description);

        factions.put(name.toLowerCase(), faction);
        database.saveFaction(faction);

        return faction;
    }

    /**
     * Remove a faction
     */
    public boolean removeFaction(String name) {
        Faction removed = factions.remove(name.toLowerCase());
        if (removed != null) {
            database.deleteFaction(removed.getUuid());
            return true;
        }
        return false;
    }

    /**
     * Get a faction by name
     */
    public Faction getFaction(String name) {
        if (name == null) return null;
        return factions.get(name.toLowerCase());
    }

    /**
     * Get all factions
     */
    public Collection<Faction> getAllFactions() {
        return factions.values();
    }

    /**
     * Get faction count
     */
    public int getFactionCount() {
        return factions.size();
    }

    /**
     * Check if two factions are hostile
     */
    public boolean areHostile(String faction1, String faction2) {
        Faction f1 = getFaction(faction1);
        Faction f2 = getFaction(faction2);

        if (f1 == null || f2 == null) return false;

        return f1.isHostileTo(faction2) || f2.isHostileTo(faction1);
    }

    /**
     * Check if two factions are allied
     */
    public boolean areAllied(String faction1, String faction2) {
        Faction f1 = getFaction(faction1);
        Faction f2 = getFaction(faction2);

        if (f1 == null || f2 == null) return false;

        return f1.isAlliedWith(faction2) || f2.isAlliedWith(faction1);
    }

    /**
     * Set relationship between two factions
     */
    public void setRelation(String faction1, String faction2, Faction.FactionRelation relation) {
        Faction f1 = getFaction(faction1);
        Faction f2 = getFaction(faction2);

        if (f1 == null || f2 == null) return;

        switch (relation) {
            case HOSTILE:
                f1.addHostileFaction(faction2);
                f2.addHostileFaction(faction1);
                break;
            case ALLIED:
                f1.addAlliedFaction(faction2);
                f2.addAlliedFaction(faction1);
                break;
            case NEUTRAL:
                f1.removeHostileFaction(faction2);
                f1.removeAlliedFaction(faction2);
                f2.removeHostileFaction(faction1);
                f2.removeAlliedFaction(faction1);
                break;
        }

        database.saveFaction(f1);
        database.saveFaction(f2);
    }

    /**
     * Get a random faction
     */
    public Faction getRandomFaction() {
        if (factions.isEmpty()) return null;
        List<Faction> list = new ArrayList<>(factions.values());
        return list.get(new Random().nextInt(list.size()));
    }

    /**
     * Check if a faction exists
     */
    public boolean factionExists(String name) {
        return factions.containsKey(name.toLowerCase());
    }
}
