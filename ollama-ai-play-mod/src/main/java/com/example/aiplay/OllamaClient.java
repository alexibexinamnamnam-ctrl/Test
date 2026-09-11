package com.example.aiplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Minimaler Client für die lokale Ollama-API (http://localhost:11434).
 * Nutzt den /api/chat Endpunkt mit stream=false, damit eine einzelne
 * JSON-Antwort zurückkommt statt eines Streams.
 */
public class OllamaClient {

    private static final String OLLAMA_URL = "http://localhost:11434/api/chat";
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Schickt System- und User-Prompt an Ollama und liefert den rohen
     * Textinhalt der Antwort (den content-String der Assistant-Message).
     * Läuft komplett asynchron auf einem Hintergrundthread.
     */
    public CompletableFuture<String> chat(String model, String systemPrompt, String userPrompt) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);

        JsonArray messages = new JsonArray();

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);
        messages.add(sysMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);

        body.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    JsonObject message = json.getAsJsonObject("message");
                    return message.get("content").getAsString();
                });
    }
}
