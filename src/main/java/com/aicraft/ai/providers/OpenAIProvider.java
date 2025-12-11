package com.aicraft.ai.providers;

import com.aicraft.AICompanions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI/ChatGPT provider implementation
 */
public class OpenAIProvider implements AIProvider {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AICompanions plugin;
    private final OkHttpClient client;
    private final Gson gson;

    private String apiKey;
    private String model;
    private int maxTokens;

    public OpenAIProvider(AICompanions plugin) {
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
        this.apiKey = plugin.getConfig().getString("ai.openai.api-key", "");
        this.model = plugin.getConfig().getString("ai.openai.model", "gpt-4o");
        this.maxTokens = plugin.getConfig().getInt("ai.openai.max-tokens", 300);
    }

    @Override
    public String getName() {
        return "OpenAI (" + model + ")";
    }

    @Override
    public String chat(String prompt) throws Exception {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenAI API key not configured");
        }

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", maxTokens);
        requestBody.addProperty("temperature", 0.8); // Slightly creative for roleplay

        JsonArray messages = new JsonArray();

        // System message for roleplay
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are an NPC in a Minecraft world. Stay in character and keep responses brief (1-3 sentences).");
        messages.add(systemMessage);

        // User message with the prompt
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", prompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);

        Request request = new Request.Builder()
                .url(API_URL)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(gson.toJson(requestBody), JSON))
                .build();

        plugin.getLogger().info("Sending request to OpenAI API (model: " + model + ")...");

        int retryAttempts = plugin.getConfig().getInt("ai.retry-attempts", 3);

        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                plugin.getLogger().info("OpenAI response code: " + response.code());

                if (!response.isSuccessful()) {
                    plugin.getLogger().severe("OpenAI API error (attempt " + attempt + "): " +
                            response.code() + " - " + responseBody);

                    if (attempt < retryAttempts) {
                        Thread.sleep(1000L * attempt);
                        continue;
                    }
                    throw new IOException("OpenAI API request failed: " + response.code());
                }

                plugin.debug("OpenAI response received");

                // Parse response
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                JsonArray choices = jsonResponse.getAsJsonArray("choices");

                if (choices != null && choices.size() > 0) {
                    JsonObject firstChoice = choices.get(0).getAsJsonObject();
                    JsonObject message = firstChoice.getAsJsonObject("message");
                    if (message != null && message.has("content")) {
                        return message.get("content").getAsString().trim();
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
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_OPENAI_API_KEY_HERE");
    }
}
