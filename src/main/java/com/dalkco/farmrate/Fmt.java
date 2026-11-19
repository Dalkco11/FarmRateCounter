package com.dalkco.farmrate;

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
        return num(Math.round(perHour)) + "/ч";
    }

    /** ticks -> "2ч 13м" / "4м 05с" */
    public static String time(long ticks) {
        long sec = ticks / 20;
        long h = sec / 3600, m = sec / 60 % 60, s = sec % 60;
        if (h > 0) {
            return h + "ч " + m + "м";
        }
        return m + "м " + (s < 10 ? "0" : "") + s + "с";
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
