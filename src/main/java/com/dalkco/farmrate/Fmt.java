package com.dalkco.farmrate;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Number / time / item-name formatting. */
public final class Fmt {
    private Fmt() {
    }

    /** 1234567 -> "1 234 567" */
    public static String num(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb;
    }

    public static String rate(double perHour) {
        return I18n.get("farmrate.unit.per_hour", num(Math.round(perHour)));
    }

    /** ticks -> "2h 13m" / "4m 05s" (units follow the client language) */
    public static String time(long ticks) {
        long sec = ticks / 20;
        long h = sec / 3600, m = sec / 60 % 60, s = sec % 60;
        if (h > 0) {
            return I18n.get("farmrate.unit.hours_minutes", h, m);
        }
        return I18n.get("farmrate.unit.minutes_seconds", m, (s < 10 ? "0" : "") + s);
    }

    public static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? null : BuiltInRegistries.ITEM.getValue(key);
    }

    public static ItemStack stack(String id) {
        Item item = item(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public static Component name(String id) {
        ItemStack s = stack(id);
        return s.isEmpty() ? Component.literal(id) : s.getHoverName();
    }
}
