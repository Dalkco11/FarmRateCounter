package com.dalkco.farmrate;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Open the storage twice" mode: when the player opens a chest marked as a farm's storage,
 * compare it with the previous snapshot and print what arrived and the rate per hour
 * (server game time, so it also covers the time the player was away).
 */
public final class ChestSnapshots {
    private static final long MIN_TICKS = 20 * 10;

    private ChestSnapshots() {
    }

    /** Farm whose storage is this chest (either half of a double chest), or null. */
    public static FarmStore.Farm farmFor(Minecraft mc, BlockPos pos) {
        FarmStore.Farm f = FarmStore.farmOfChest(mc, pos);
        if (f == null) {
            BlockPos other = otherHalf(mc, pos);
            if (other != null) {
                f = FarmStore.farmOfChest(mc, other);
            }
        }
        return f;
    }

    public static BlockPos otherHalf(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            return ChestBlock.getConnectedBlockPos(pos, state);
        }
        return null;
    }

    public static void onOpened(Minecraft mc, LocalPlayer player, FarmStore.Farm farm) {
        Map<String, Long> now = new HashMap<>();
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack s = slot.getItem();
            if (!s.isEmpty()) {
                now.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), (long) s.getCount(), Long::sum);
            }
        }
        long time = mc.level.getGameTime();
        FarmStore.Snapshot prev = farm.snapshot;

        FarmStore.Snapshot snap = new FarmStore.Snapshot();
        snap.gameTime = time;
        snap.items = now;

        if (prev == null || time <= prev.gameTime) {
            farm.snapshot = snap;
            FarmStore.save();
            msg(player, Component.literal(farm.name + ": снимок хранилища сохранён. Открой его позже — покажу, сколько пришло.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        long elapsed = time - prev.gameTime;
        if (elapsed < MIN_TICKS) {
            return; // reopened right away, keep the older snapshot for a meaningful interval
        }
        farm.snapshot = snap;
        FarmStore.save();

        Set<String> keys = new HashSet<>(now.keySet());
        keys.addAll(prev.items.keySet());
        List<Map.Entry<String, Long>> diff = new ArrayList<>();
        long totalGain = 0;
        for (String k : keys) {
            long d = now.getOrDefault(k, 0L) - prev.items.getOrDefault(k, 0L);
            if (d != 0) {
                diff.add(Map.entry(k, d));
                if (d > 0) {
                    totalGain += d;
                }
            }
        }
        diff.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        double hours = elapsed / 72000.0;

        MutableComponent head = Component.literal(farm.name + " — хранилище за " + Fmt.time(elapsed) + ": ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("+" + Fmt.num(totalGain) + " (" + Fmt.rate(totalGain / hours) + ")")
                        .withStyle(ChatFormatting.WHITE));
        msg(player, head);
        if (diff.isEmpty()) {
            msg(player, Component.literal("  ничего не изменилось").withStyle(ChatFormatting.GRAY));
        }
        int shown = 0;
        for (Map.Entry<String, Long> e : diff) {
            if (shown++ >= 8) {
                msg(player, Component.literal("  … ещё " + (diff.size() - 8)).withStyle(ChatFormatting.GRAY));
                break;
            }
            long d = e.getValue();
            msg(player, Component.literal(d > 0 ? "  +" : "  ").append(Fmt.num(d) + " ").append(Fmt.name(e.getKey()))
                    .append(d > 0 ? " (" + Fmt.rate(d / hours) + ")" : " (забрали)")
                    .withStyle(d > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
    }

    private static void msg(LocalPlayer player, Component c) {
        player.sendSystemMessage(Component.literal("[Farm Rate] ").withStyle(ChatFormatting.DARK_AQUA).append(c));
    }
}
