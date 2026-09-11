package com.example.aiplay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Baut eine kompakte Textbeschreibung des aktuellen Zustands, die das
 * Sprachmodell als Grundlage für seine Entscheidung bekommt.
 */
public class GameStateCollector {

    public String describe(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        World world = client.world;
        if (player == null || world == null) {
            return "Kein Spieler/Welt geladen.";
        }

        StringBuilder sb = new StringBuilder();

        BlockPos pos = player.getBlockPos();
        sb.append(String.format("Position: x=%d y=%d z=%d, Blickrichtung: %s%n",
                pos.getX(), pos.getY(), pos.getZ(), player.getHorizontalFacing().asString()));

        sb.append(String.format("Leben: %.1f/20, Hunger: %d/20%n",
                player.getHealth(), player.getHungerManager().getFoodLevel()));

        sb.append("Tageszeit: ").append(world.isDay() ? "Tag" : "Nacht").append("\n");

        // Block, auf den der Spieler gerade schaut (Reichweite ~4.5 Blöcke)
        HitResult hit = client.crosshairTarget;
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos target = blockHit.getBlockPos();
            sb.append("Block im Fadenkreuz: ")
              .append(world.getBlockState(target).getBlock().getName().getString())
              .append("\n");
        } else {
            sb.append("Block im Fadenkreuz: keiner in Reichweite\n");
        }

        // Block unter dem Spieler
        BlockPos below = pos.down();
        sb.append("Block unter den Füßen: ")
          .append(world.getBlockState(below).getBlock().getName().getString())
          .append("\n");

        // Feindliche Mobs in der Nähe (Radius 16 Blöcke), nach Distanz sortiert, max. 5
        Box searchBox = player.getBoundingBox().expand(16.0);
        List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, searchBox, e -> true);
        String hostileDesc = hostiles.stream()
                .sorted((a, b) -> Double.compare(a.squaredDistanceTo(player), b.squaredDistanceTo(player)))
                .limit(5)
                .map(e -> String.format("%s (%.1fm)", e.getName().getString(), Math.sqrt(e.squaredDistanceTo(player))))
                .collect(Collectors.joining(", "));
        sb.append("Feindliche Mobs in der Nähe: ")
          .append(hostileDesc.isEmpty() ? "keine" : hostileDesc)
          .append("\n");

        // Gehaltenes Item
        sb.append("Item in der Hand: ")
          .append(player.getMainHandStack().isEmpty() ? "leer" : player.getMainHandStack().getName().getString())
          .append("\n");

        return sb.toString();
    }
}
