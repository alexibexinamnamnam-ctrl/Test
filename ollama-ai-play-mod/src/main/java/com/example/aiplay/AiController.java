package com.example.aiplay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiController {

    private static final String SYSTEM_PROMPT = """
            Du steuerst einen Minecraft-Spieler. Du bekommst eine Beschreibung des
            aktuellen Zustands und musst GENAU EINE Aktion wählen.
            Erlaubte Aktionen: MOVE_FORWARD, MOVE_BACK, STRAFE_LEFT, STRAFE_RIGHT,
            TURN_LEFT, TURN_RIGHT, LOOK_UP, LOOK_DOWN, JUMP, ATTACK, MINE,
            PLACE_BLOCK, STOP, WAIT.
            Antworte AUSSCHLIESSLICH mit einem JSON-Objekt, ohne weiteren Text,
            in genau diesem Format:
            {"action": "MOVE_FORWARD", "reason": "kurze Begründung"}
            """;

    private final OllamaClient ollama = new OllamaClient();
    private final GameStateCollector stateCollector = new GameStateCollector();
    private final ActionExecutor executor = new ActionExecutor();
    private final Pattern jsonPattern = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private boolean running = false;
    private boolean waitingForResponse = false;
    private int ticksUntilNextDecision = 0;
    private int intervalTicks = 30; // ca. 1.5 Sekunden bei 20 TPS
    private String model = "llama3.1";

    public void start() {
        running = true;
        ticksUntilNextDecision = 0;
    }

    public void stop() {
        running = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            executor.resetMovement(client.player);
        }
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setIntervalTicks(int ticks) {
        this.intervalTicks = ticks;
    }

    public void onTick(MinecraftClient client) {
        if (!running || client.player == null || client.world == null) {
            return;
        }

        if (waitingForResponse) {
            return; // Warten, bis die letzte Anfrage beantwortet wurde
        }

        if (ticksUntilNextDecision > 0) {
            ticksUntilNextDecision--;
            return;
        }

        ticksUntilNextDecision = intervalTicks;
        requestNextAction(client);
    }

    private void requestNextAction(MinecraftClient client) {
        waitingForResponse = true;
        String stateDescription = stateCollector.describe(client);

        ollama.chat(model, SYSTEM_PROMPT, stateDescription)
                .thenAccept(response -> {
                    ActionExecutor.Action action = parseAction(response);
                    // Ausführung muss auf dem Client-Thread passieren, daher einreihen:
                    client.execute(() -> {
                        executor.resetMovement(client.player);
                        executor.apply(client, action);
                        waitingForResponse = false;
                    });
                })
                .exceptionally(ex -> {
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(
                                    Text.literal("§c[AI Play] Ollama-Fehler: " + ex.getMessage()), false);
                        }
                        waitingForResponse = false;
                    });
                    return null;
                });
    }

    private ActionExecutor.Action parseAction(String rawResponse) {
        try {
            Matcher matcher = jsonPattern.matcher(rawResponse);
            String jsonPart = matcher.find() ? matcher.group() : rawResponse;
            JsonObject obj = JsonParser.parseString(jsonPart).getAsJsonObject();
            String actionName = obj.get("action").getAsString().trim().toUpperCase();
            return ActionExecutor.Action.valueOf(actionName);
        } catch (Exception e) {
            // Konnte die Antwort nicht parsen -> sicherheitshalber nichts tun
            return ActionExecutor.Action.WAIT;
        }
    }
}
