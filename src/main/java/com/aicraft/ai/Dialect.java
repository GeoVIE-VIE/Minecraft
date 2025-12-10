package com.aicraft.ai;

import java.util.Random;

/**
 * Represents different speech dialects for NPCs
 * Adds diversity and regional flavor to NPC conversations
 */
public enum Dialect {

    // Standard fantasy/medieval
    STANDARD("Standard",
        "Speak in standard medieval fantasy English.",
        new String[]{"Indeed", "Pray tell", "Mayhaps", "Verily", "'Tis", "Methinks"}),

    // Southern American (Texas/countryside)
    SOUTHERN("Southern",
        "Speak with a Southern American accent and dialect. Use contractions like 'y'all', 'ain't', 'fixin' to', 'reckon', and 'howdy'. Be warm and hospitable.",
        new String[]{"Y'all", "Ain't", "Fixin' to", "Reckon", "Howdy", "Well I'll be", "Bless your heart"}),

    // New England (Northern Maine)
    NEW_ENGLAND("New England",
        "Speak with a Northern New England/Maine accent. Drop 'r' sounds at end of words, use 'ayuh' for yes, say 'wicked' as an intensifier. Be direct and practical.",
        new String[]{"Ayuh", "Wicked", "Down cellar", "Ayuh, that's right", "Finest kind", "Can't get there from here"}),

    // British English (England)
    BRITISH("British",
        "Speak with proper British English. Use phrases like 'quite', 'rather', 'brilliant', 'bloody', 'cheerio'. Be polite but sometimes sarcastic.",
        new String[]{"Quite", "Rather", "Brilliant", "Bloody hell", "Cheerio", "Right then", "Blimey"}),

    // Scottish
    SCOTTISH("Scottish",
        "Speak with a Scottish accent. Use 'aye' for yes, 'nae' for no, 'wee' for small, 'och' as an exclamation. Be proud and direct.",
        new String[]{"Aye", "Nae", "Wee", "Och", "Lad", "Lass", "Dinnae"}),

    // Irish
    IRISH("Irish",
        "Speak with an Irish accent. Use phrases like 'to be sure', 'grand', 'craic', 'fierce'. Be friendly and tell stories.",
        new String[]{"To be sure", "Grand", "What's the craic", "Fierce", "Yer man", "Sláinte"}),

    // Gruff/Dwarven style
    GRUFF("Gruff",
        "Speak in a gruff, dwarven manner. Short sentences, focus on practical matters. Use 'by my beard', reference stone and metal.",
        new String[]{"By my beard", "Stone and steel", "Hmph", "Bah", "As sure as stone"}),

    // Mysterious/Cryptic (for Cultists)
    CRYPTIC("Cryptic",
        "Speak in mysterious, cryptic riddles. Be vague about your true intentions. Reference shadows, secrets, and hidden truths.",
        new String[]{"The shadows know", "All is revealed in time", "The truth lies beneath", "Secrets within secrets"}),

    // Zealous/Fanatical (for Cultists)
    ZEALOUS("Zealous",
        "Speak with religious fervor and fanaticism. Reference your deity Geodjian constantly. Promise enlightenment to converts.",
        new String[]{"Geodjian watches", "The All-Seer knows", "Blessed be the faithful", "Non-believers shall see"}),

    // Pirate/Sailor
    PIRATE("Pirate",
        "Speak like a sailor or pirate. Use nautical terms, 'arr', 'matey', 'scallywag'. Reference the sea and adventure.",
        new String[]{"Arr", "Matey", "Scallywag", "Shiver me timbers", "Ye", "Ahoy"}),

    // Noble/Aristocratic
    NOBLE("Noble",
        "Speak in a refined, aristocratic manner. Use formal language, express mild disdain for commoners. Be proud.",
        new String[]{"Indeed", "How dreadfully common", "One would expect", "Frightfully", "Commoner"});

    private final String name;
    private final String prompt;
    private final String[] phrases;
    private static final Random random = new Random();

    Dialect(String name, String prompt, String[] phrases) {
        this.name = name;
        this.prompt = prompt;
        this.phrases = phrases;
    }

    public String getName() {
        return name;
    }

    public String getPrompt() {
        return prompt;
    }

    public String[] getPhrases() {
        return phrases;
    }

    public String getRandomPhrase() {
        return phrases[random.nextInt(phrases.length)];
    }

    /**
     * Get a random dialect for general NPCs
     */
    public static Dialect getRandomGeneral() {
        Dialect[] general = {STANDARD, SOUTHERN, NEW_ENGLAND, BRITISH, SCOTTISH, IRISH, GRUFF, NOBLE};
        return general[random.nextInt(general.length)];
    }

    /**
     * Get dialect appropriate for a faction
     */
    public static Dialect getForFaction(String faction) {
        if (faction == null) return getRandomGeneral();

        return switch (faction.toLowerCase()) {
            case "cultists" -> random.nextBoolean() ? CRYPTIC : ZEALOUS;
            case "bandits", "raiders" -> random.nextInt(3) == 0 ? PIRATE : getRandomGeneral();
            case "merchants" -> random.nextInt(3) == 0 ? NOBLE : getRandomGeneral();
            case "guards" -> random.nextInt(3) == 0 ? GRUFF : getRandomGeneral();
            case "villagers" -> getRandomGeneral();
            case "wanderers" -> getRandomGeneral();
            default -> getRandomGeneral();
        };
    }

    /**
     * Parse dialect from string
     */
    public static Dialect fromString(String name) {
        if (name == null) return STANDARD;
        for (Dialect d : values()) {
            if (d.name().equalsIgnoreCase(name) || d.getName().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return STANDARD;
    }
}
