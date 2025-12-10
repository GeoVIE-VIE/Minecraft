package com.aicraft.ai.providers;

/**
 * Interface for AI providers (Claude, OpenAI, etc.)
 */
public interface AIProvider {

    /**
     * Get the name of this provider
     */
    String getName();

    /**
     * Send a chat message and get a response
     *
     * @param prompt The prompt to send
     * @return The AI's response
     * @throws Exception if the request fails
     */
    String chat(String prompt) throws Exception;

    /**
     * Check if this provider is configured and ready to use
     */
    boolean isConfigured();
}
