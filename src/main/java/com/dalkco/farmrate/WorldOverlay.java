package com.dalkco.farmrate;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Outlines selected hoppers / storage chests and shows a rate label above each farm.
 * Uses vanilla gizmos, emitted every client tick (the client tick runs inside the per-tick gizmo collector).
 */
public final class WorldOverlay {
    private static final double DRAW_DISTANCE = 96.0;

    private WorldOverlay() {
    }

    public static void draw(Minecraft mc) {
        if (mc.player == null || mc.level == null || !FarmStore.showWorld()) {
            return;
        }
        double maxSq = DRAW_DISTANCE * DRAW_DISTANCE;
        for (FarmStore.Farm farm : FarmStore.farms(mc)) {
            int color = farm.color;
            double sx = 0, sz = 0;
            double topY = Double.NEGATIVE_INFINITY;
            int n = 0;
            for (long packed : farm.hoppers) {
                BlockPos pos = BlockPos.of(packed);
                if (mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > maxSq) {
                    continue;
                }
                Gizmos.cuboid(new AABB(pos).inflate(0.02), GizmoStyle.strokeAndFill(color, 2.0f, (color & 0x00FFFFFF) | 0x28000000));
                sx += pos.getX() + 0.5;
                sz += pos.getZ() + 0.5;
                topY = Math.max(topY, pos.getY() + 1);
                n++;
            }
            for (long packed : farm.chests) {
                BlockPos pos = BlockPos.of(packed);
                if (mc.player.distanceToSqr(Vec3.atCenterOf(pos)) > maxSq) {
                    continue;
                }
                Gizmos.cuboid(new AABB(pos).inflate(0.04), GizmoStyle.stroke(color, 1.0f));
            }
            if (n == 0) {
                continue;
            }
            Vec3 label = new Vec3(sx / n, topY + 1.6, sz / n);
            String head = farm.name + ": " + Fmt.rate(farm.ratePerHour());
            String sub = (farm.active ? "5 мин: " + Fmt.rate(farm.recentRatePerHour()) : "пауза — далеко")
                    + " · " + Fmt.time(farm.observedTicks);
            Gizmos.billboardText(head, label, TextGizmo.Style.forColorAndCentered(color)).setAlwaysOnTop();
            Gizmos.billboardText(sub, label.add(0, -0.3, 0),
                    TextGizmo.Style.forColorAndCentered(0xFFDDDDDD).withScale(TextGizmo.Style.DEFAULT_SCALE * 0.8f))
                    .setAlwaysOnTop();
        }
    }
}
