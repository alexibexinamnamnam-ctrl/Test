package com.example.aiplay;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class AiPlayMod implements ClientModInitializer {

    public static final AiController controller = new AiController();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(controller::onTick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("aiplay")
                        .then(literal("start").executes(ctx -> {
                            controller.start();
                            ctx.getSource().sendFeedback(Text.literal("§aOllama AI übernimmt jetzt die Steuerung."));
                            return 1;
                        }))
                        .then(literal("stop").executes(ctx -> {
                            controller.stop();
                            ctx.getSource().sendFeedback(Text.literal("§cOllama AI gestoppt."));
                            return 1;
                        }))
                        .then(literal("model").then(argument("name", StringArgumentType.string())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    controller.setModel(name);
                                    ctx.getSource().sendFeedback(Text.literal("§bOllama-Modell gesetzt: " + name));
                                    return 1;
                                })))
                        .then(literal("interval").then(argument("ticks", IntegerArgumentType.integer(5, 200))
                                .executes(ctx -> {
                                    int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                    controller.setIntervalTicks(ticks);
                                    ctx.getSource().sendFeedback(Text.literal("§bIntervall gesetzt: " + ticks + " Ticks"));
                                    return 1;
                                })))
                )
        );
    }
}
