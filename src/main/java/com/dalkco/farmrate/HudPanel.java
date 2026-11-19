package com.dalkco.farmrate;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small panel in the top-left corner: the nearest active farm (or the current one) and its top items. */
public final class HudPanel {
    private static final int PAD = 4;
    private static final int TOP_ITEMS = 3;

    private HudPanel() {
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !FarmStore.showHud()) {
            return;
        }
        FarmStore.Farm farm = pickFarm(mc);
        if (farm == null || (farm.hoppers.isEmpty() && farm.total() == 0)) {
            return;
        }
        Font font = mc.font;
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(null, Component.literal(farm.name), farm.color));
        lines.add(new Line(null, Component.literal(Fmt.rate(farm.ratePerHour())
                + (farm.active ? "  (5 мин: " + Fmt.rate(farm.recentRatePerHour()) + ")" : "  пауза")), 0xFFFFFFFF));
        lines.add(new Line(null, Component.literal("Собрано " + Fmt.num(farm.total()) + " за "
                + Fmt.time(farm.observedTicks)), 0xFFAAAAAA));
        farm.totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_ITEMS)
                .forEach(e -> lines.add(new Line(e.getKey(),
                        Component.empty().append(Fmt.name(e.getKey()))
                                .append(" " + Fmt.rate(farm.itemRate(e.getValue()))), 0xFFDDDDDD)));

        int width = 0;
        int height = 0;
        for (Line l : lines) {
            width = Math.max(width, font.width(l.text) + (l.item != null ? 18 : 0));
            height += l.item != null ? 17 : font.lineHeight + 2;
        }
        int x = PAD, y = PAD;
        g.fill(x - 2, y - 2, x + width + 2, y + height, 0x90000000);
        for (Line l : lines) {
            if (l.item != null) {
                g.item(Fmt.stack(l.item), x, y);
                g.text(font, l.text, x + 18, y + 4, l.color, true);
                y += 17;
            } else {
                g.text(font, l.text, x, y, l.color, true);
                y += font.lineHeight + 2;
            }
        }
    }

    /** The closest farm that is being measured right now, otherwise the current farm. */
    private static FarmStore.Farm pickFarm(Minecraft mc) {
        FarmStore.Farm best = null;
        double bestDist = Double.MAX_VALUE;
        for (FarmStore.Farm f : FarmStore.farms(mc)) {
            if (!f.active || f.hoppers.isEmpty()) {
                continue;
            }
            double d = mc.player.distanceToSqr(Vec3.atCenterOf(net.minecraft.core.BlockPos.of(f.hoppers.get(0))));
            if (d < bestDist) {
                bestDist = d;
                best = f;
            }
        }
        return best != null ? best : FarmStore.currentFarm(mc);
    }

    private record Line(String item, Component text, int color) {
    }
}
