package com.aicraft.ai.providers;

import com.aicraft.AICompanions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Claude AI provider implementation
 */
public class ClaudeProvider implements AIProvider {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AICompanions plugin;
    private final OkHttpClient client;
    private final Gson gson;

    private String apiKey;
    private String model;
    private int maxTokens;

    public ClaudeProvider(AICompanions plugin) {
        this.plugin = plugin;
        this.gson = new Gson();

        int timeout = plugin.getConfig().getInt("ai.timeout-seconds", 30);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build();

        loadConfig();
    }

    private void loadConfig() {
        this.apiKey = plugin.getConfig().getString("ai.claude.api-key", "");
        this.model = plugin.getConfig().getString("ai.claude.model", "claude-sonnet-4-20250514");
        this.maxTokens = plugin.getConfig().getInt("ai.claude.max-tokens", 300);
    }

    @Override
    public String getName() {
        return "Claude (" + model + ")";
    }

    @Override
    public String chat(String prompt) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("Claude API key not configured");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", maxTokens);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);

        Request request = new Request.Builder()
                .url(API_URL)
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(RequestBody.create(gson.toJson(requestBody), JSON))
                .build();

        plugin.debug("Sending request to Claude API...");

        int retryAttempts = plugin.getConfig().getInt("ai.retry-attempts", 3);

        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    plugin.getLogger().warning("Claude API error (attempt " + attempt + "): " +
                            response.code() + " - " + responseBody);

                    if (attempt < retryAttempts) {
                        Thread.sleep(1000L * attempt); // Exponential backoff
                        continue;
                    }
                    throw new IOException("Claude API request failed: " + response.code());
                }

                plugin.debug("Claude response received");

                // Parse response
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonArray content = jsonResponse.getAsJsonArray("content");

                if (content != null && content.size() > 0) {
                    JsonObject firstContent = content.get(0).getAsJsonObject();
                    if (firstContent.has("text")) {
                        return firstContent.get("text").getAsString().trim();
                    }
                }

                return null;

            } catch (IOException e) {
                if (attempt < retryAttempts) {
                    plugin.debug("Request failed, retrying... (attempt " + attempt + ")");
                    Thread.sleep(1000L * attempt);
                } else {
                    throw e;
                }
            }
        }

        return null;
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_CLAUDE_API_KEY_HERE");
    }
}
