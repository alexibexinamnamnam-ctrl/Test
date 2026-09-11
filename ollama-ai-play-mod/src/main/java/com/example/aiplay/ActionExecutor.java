package com.example.aiplay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Setzt eine {@link Action} tatsächlich in Spieler-Input um.
 * Bewegung wird über player.input gesetzt (wie gedrückte Tasten),
 * Blick über Yaw/Pitch, Angriff/Abbau über den interactionManager.
 */
public class ActionExecutor {

    public enum Action {
        MOVE_FORWARD, MOVE_BACK, STRAFE_LEFT, STRAFE_RIGHT,
        TURN_LEFT, TURN_RIGHT, LOOK_UP, LOOK_DOWN,
        JUMP, ATTACK, MINE, PLACE_BLOCK, STOP, WAIT
    }

    /** Setzt jede Bewegungstaste zurück. Vor jeder neuen Aktion aufrufen. */
    public void resetMovement(ClientPlayerEntity player) {
        player.input.pressForward = false;
        player.input.pressBack = false;
        player.input.pressLeft = false;
        player.input.pressRight = false;
        player.input.jumping = false;
    }

    public void apply(MinecraftClient client, Action action) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        switch (action) {
            case MOVE_FORWARD -> player.input.pressForward = true;
            case MOVE_BACK -> player.input.pressBack = true;
            case STRAFE_LEFT -> player.input.pressLeft = true;
            case STRAFE_RIGHT -> player.input.pressRight = true;
            case JUMP -> player.input.jumping = true;
            case TURN_LEFT -> player.setYaw(player.getYaw() - 20f);
            case TURN_RIGHT -> player.setYaw(player.getYaw() + 20f);
            case LOOK_UP -> player.setPitch(Math.max(player.getPitch() - 15f, -90f));
            case LOOK_DOWN -> player.setPitch(Math.min(player.getPitch() + 15f, 90f));
            case ATTACK -> attackOrMine(client);
            case MINE -> attackOrMine(client);
            case PLACE_BLOCK -> placeBlock(client);
            case STOP, WAIT -> { /* nichts tun, Bewegung bleibt zurückgesetzt */ }
        }
    }

    private void attackOrMine(MinecraftClient client) {
        if (client.crosshairTarget == null) return;
        HitResult.Type type = client.crosshairTarget.getType();

        if (type == HitResult.Type.ENTITY) {
            Entity target = ((net.minecraft.util.hit.EntityHitResult) client.crosshairTarget).getEntity();
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        } else if (type == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) client.crosshairTarget;
            // Mehrfach anstoßen simuliert "graben" über mehrere Ticks hinweg.
            client.interactionManager.attackBlock(blockHit.getBlockPos(), blockHit.getSide());
            client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        }
    }

    private void placeBlock(MinecraftClient client) {
        if (client.crosshairTarget instanceof BlockHitResult blockHit) {
            client.interactionManager.interactBlock(client.player, net.minecraft.util.Hand.MAIN_HAND, blockHit);
        }
    }
}
