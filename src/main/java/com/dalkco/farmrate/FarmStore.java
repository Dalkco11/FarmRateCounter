package com.dalkco.farmrate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Farms (hopper groups) + statistics per server / singleplayer world + dimension.
 * Saved to config/farmrate.json.
 */
public final class FarmStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("farmrate.json");

    public static final int[] COLORS = {
            0xFF55FF55, 0xFF55FFFF, 0xFFFFAA00, 0xFFFF55FF, 0xFFFF5555, 0xFF5599FF, 0xFFFFFF55, 0xFFFFFFFF
    };
    /** Rolling window for the "recent" rate: 5 minutes of observed time. */
    public static final long WINDOW_TICKS = 20L * 60 * 5;

    private static Data data = new Data();
    private static boolean dirty;

    public static final class Data {
        public boolean showWorld = true;
        public boolean showHud = true;
        public int range = 40;
        public Map<String, World> worlds = new HashMap<>();
    }

    public static final class World {
        public String current = "farm";
        public Map<String, Farm> farms = new LinkedHashMap<>();
    }

    public static final class Farm {
        public String name;
        public int color;
        public List<Long> hoppers = new ArrayList<>();
        public List<Long> chests = new ArrayList<>();
        public Map<String, Long> totals = new LinkedHashMap<>();
        public long observedTicks;
        /** last snapshot of the farm storage, for the "open the chest twice" mode */
        public Snapshot snapshot;

        // runtime only
        transient boolean active;
        transient Deque<long[]> recent; // {observedTick, count}

        public long total() {
            long t = 0;
            for (long v : totals.values()) {
                t += v;
            }
            return t;
        }

        Deque<long[]> recent() {
            if (recent == null) {
                recent = new ArrayDeque<>();
            }
            return recent;
        }

        public void record(String item, long count) {
            totals.merge(item, count, Long::sum);
            recent().addLast(new long[]{observedTicks, count});
            markDirty();
        }

        /** Items collected during the last {@link #WINDOW_TICKS} observed ticks. */
        public long recentCount() {
            Deque<long[]> r = recent();
            while (!r.isEmpty() && r.peekFirst()[0] < observedTicks - WINDOW_TICKS) {
                r.pollFirst();
            }
            long sum = 0;
            for (long[] e : r) {
                sum += e[1];
            }
            return sum;
        }

        public double ratePerHour() {
            return observedTicks <= 0 ? 0 : total() * 72000.0 / observedTicks;
        }

        public double recentRatePerHour() {
            long window = Math.min(observedTicks, WINDOW_TICKS);
            return window < 200 ? ratePerHour() : recentCount() * 72000.0 / window;
        }

        public double itemRate(long count) {
            return observedTicks <= 0 ? 0 : count * 72000.0 / observedTicks;
        }

        public void reset() {
            totals.clear();
            observedTicks = 0;
            recent().clear();
            markDirty();
        }
    }

    public static final class Snapshot {
        public long gameTime;
        public Map<String, Long> items = new HashMap<>();
    }

    private FarmStore() {
    }

    // ------------------------------------------------------------------ io

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Data.class);
                if (loaded != null) {
                    if (loaded.worlds == null) {
                        loaded.worlds = new HashMap<>();
                    }
                    data = loaded;
                }
            }
        } catch (Exception e) {
            FarmRateClient.LOGGER.warn("Failed to read {}", FILE, e);
        }
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public static void save() {
        dirty = false;
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FarmRateClient.LOGGER.warn("Failed to write {}", FILE, e);
        }
    }

    // ------------------------------------------------------------------ settings

    public static boolean showWorld() {
        return data.showWorld;
    }

    public static void setShowWorld(boolean v) {
        data.showWorld = v;
        save();
    }

    public static boolean showHud() {
        return data.showHud;
    }

    public static void setShowHud(boolean v) {
        data.showHud = v;
        save();
    }

    public static int range() {
        return data.range;
    }

    public static void setRange(int v) {
        data.range = v;
        save();
    }

    // ------------------------------------------------------------------ world / farms

    public static String worldKey(Minecraft mc) {
        String base;
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            base = "server:" + server.ip;
        } else if (mc.getSingleplayerServer() != null) {
            base = "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            base = "unknown";
        }
        String dim = mc.level == null ? "?" : mc.level.dimension().identifier().toString();
        return base + "|" + dim;
    }

    public static World world(Minecraft mc) {
        World w = data.worlds.computeIfAbsent(worldKey(mc), k -> new World());
        if (w.farms == null) {
            w.farms = new LinkedHashMap<>();
        }
        return w;
    }

    public static Farm currentFarm(Minecraft mc) {
        World w = world(mc);
        return farm(mc, w.current == null ? "farm" : w.current, true);
    }

    public static Farm farm(Minecraft mc, String name, boolean create) {
        World w = world(mc);
        Farm f = w.farms.get(name);
        if (f == null && create) {
            f = new Farm();
            f.name = name;
            f.color = COLORS[w.farms.size() % COLORS.length];
            w.farms.put(name, f);
            markDirty();
        }
        return f;
    }

    public static void setCurrent(Minecraft mc, String name) {
        world(mc).current = name;
        farm(mc, name, true);
        save();
    }

    public static Iterable<Farm> farms(Minecraft mc) {
        return world(mc).farms.values();
    }

    /** The farm that contains this hopper, or null. */
    public static Farm farmOfHopper(Minecraft mc, BlockPos pos) {
        long key = pos.asLong();
        for (Farm f : farms(mc)) {
            if (f.hoppers.contains(key)) {
                return f;
            }
        }
        return null;
    }

    public static Farm farmOfChest(Minecraft mc, BlockPos pos) {
        long key = pos.asLong();
        for (Farm f : farms(mc)) {
            if (f.chests.contains(key)) {
                return f;
            }
        }
        return null;
    }

    public static boolean deleteFarm(Minecraft mc, String name) {
        World w = world(mc);
        boolean removed = w.farms.remove(name) != null;
        if (removed) {
            if (name.equals(w.current)) {
                w.current = w.farms.isEmpty() ? "farm" : w.farms.keySet().iterator().next();
            }
            save();
        }
        return removed;
    }
}
