package com.dalkco.farmrate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Counts items that hoppers pull in, using only what a vanilla client can see.
 * <p>
 * A hopper sucks item entities from the block above it (vanilla {@code Hopper.SUCK_AABB}).
 * Every tick we remember the item entities inside the zones of the selected hoppers. When one of them
 * disappears (or shrinks) while the farm is in range, the difference was picked up by the hopper,
 * unless it merged into a neighbouring item, despawned of old age, or the player picked it up.
 */
public final class HopperTracker {

    private static final int DESPAWN_AGE = 6000;
    private static final int MERGE_WAIT_TICKS = 3;
    private static final double MERGE_RADIUS = 2.5;

    private record Seen(FarmStore.Farm farm, String item, int count, Vec3 pos, int age) {
    }

    private static final class Pending {
        final FarmStore.Farm farm;
        final String item;
        final Vec3 pos;
        int count;
        int ticksLeft = MERGE_WAIT_TICKS;

        Pending(Seen s) {
            farm = s.farm;
            item = s.item;
            pos = s.pos;
            count = s.count;
        }
    }

    private final Minecraft mc;
    private Map<Integer, Seen> inZone = new HashMap<>();
    /** counts of all item entities around the farms last tick (to spot merges) */
    private Map<Integer, Integer> nearCounts = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private String worldKey;

    public HopperTracker(Minecraft mc) {
        this.mc = mc;
    }

    public void reset() {
        inZone = new HashMap<>();
        nearCounts = new HashMap<>();
        pending.clear();
        worldKey = null;
    }

    public void tick() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        String key = FarmStore.worldKey(mc);
        if (!key.equals(worldKey)) {
            reset();
            worldKey = key;
        }
        double range = FarmStore.range();
        double rangeSq = range * range;

        Map<Integer, Seen> curZone = new HashMap<>();
        Map<Integer, Integer> curNear = new HashMap<>();
        Map<Integer, ItemEntity> nearEntities = new HashMap<>();

        for (FarmStore.Farm farm : FarmStore.farms(mc)) {
            farm.active = false;
            for (long packed : farm.hoppers) {
                BlockPos pos = BlockPos.of(packed);
                if (!mc.level.hasChunkAt(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > rangeSq) {
                    continue;
                }
                farm.active = true;
                AABB zone = suckZone(pos);
                for (ItemEntity e : mc.level.getEntitiesOfClass(ItemEntity.class, zone.inflate(MERGE_RADIUS), Entity::isAlive)) {
                    curNear.put(e.getId(), e.getItem().getCount());
                    nearEntities.put(e.getId(), e);
                    if (e.getBoundingBox().intersects(zone)) {
                        curZone.putIfAbsent(e.getId(), new Seen(farm, itemId(e), e.getItem().getCount(),
                                e.position(), e.getAge()));
                    }
                }
            }
            if (farm.active) {
                farm.observedTicks++;
            }
        }

        // 1. items that left the zones
        for (Map.Entry<Integer, Seen> entry : inZone.entrySet()) {
            Seen was = entry.getValue();
            Seen now = curZone.get(entry.getKey());
            if (now != null) {
                if (now.count < was.count) {
                    // hopper took part of the stack (e.g. it was almost full)
                    was.farm.record(was.item, was.count - now.count);
                }
                continue;
            }
            Entity e = mc.level.getEntity(entry.getKey());
            if (e != null && e.isAlive()) {
                continue; // bounced / flowed out of the zone, not collected
            }
            if (!was.farm.active || was.age >= DESPAWN_AGE - 40 || nearPlayer(player, was.pos)) {
                continue; // out of range (entity untracked), despawned, or the player picked it up
            }
            pending.add(new Pending(was));
        }

        // 2. growth of neighbouring items = merges, not pickups
        Map<Integer, Integer> growth = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : curNear.entrySet()) {
            Integer before = nearCounts.get(entry.getKey());
            if (before != null && entry.getValue() > before) {
                growth.put(entry.getKey(), entry.getValue() - before);
            }
        }
        for (Pending p : pending) {
            for (Iterator<Map.Entry<Integer, Integer>> it = growth.entrySet().iterator(); it.hasNext() && p.count > 0; ) {
                Map.Entry<Integer, Integer> g = it.next();
                ItemEntity grown = nearEntities.get(g.getKey());
                if (grown == null || !itemId(grown).equals(p.item) || grown.position().distanceTo(p.pos) > MERGE_RADIUS) {
                    continue;
                }
                int used = Math.min(g.getValue(), p.count);
                p.count -= used;
                if (g.getValue() - used <= 0) {
                    it.remove();
                } else {
                    g.setValue(g.getValue() - used);
                }
            }
        }

        // 3. pending items that did not turn out to be merges were collected
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.count <= 0) {
                it.remove();
            } else if (--p.ticksLeft <= 0) {
                p.farm.record(p.item, p.count);
                it.remove();
            }
        }

        inZone = curZone;
        nearCounts = curNear;
    }

    /** Vanilla hopper suck area: x/z over the hopper, y from 11/16 up to 2 blocks above its base. */
    public static AABB suckZone(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY() + 11 / 16.0, pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
    }

    private static boolean nearPlayer(LocalPlayer player, Vec3 pos) {
        return player.getBoundingBox().inflate(1.5, 1.0, 1.5).contains(pos);
    }

    static String itemId(ItemEntity e) {
        return BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).toString();
    }
}
