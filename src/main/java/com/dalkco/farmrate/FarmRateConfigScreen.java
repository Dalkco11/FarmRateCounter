package com.dalkco.farmrate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Settings screen (opened from Mod Menu, the settings key or /farmrate config). */
public class FarmRateConfigScreen extends Screen {
    private static final int MIN_RANGE = 8;
    private static final int MAX_RANGE = 128;
    private static final int W = 220;
    private static final int H = 20;
    private static final int GAP = 24;

    private final Screen parent;
    private int range;

    public FarmRateConfigScreen(Screen parent) {
        super(Component.translatable("farmrate.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        range = FarmStore.range();
        int x = this.width / 2 - W / 2;
        int y = Math.max(32, this.height / 6);

        addRenderableWidget(CycleButton.onOffBuilder(FarmStore.showWorld())
                .create(x, y, W, H, Component.translatable("farmrate.config.overlay"),
                        (b, v) -> FarmStore.setShowWorld(v)));
        y += GAP;

        addRenderableWidget(CycleButton.onOffBuilder(FarmStore.showHud())
                .create(x, y, W, H, Component.translatable("farmrate.config.hud"),
                        (b, v) -> FarmStore.setShowHud(v)));
        y += GAP;

        addRenderableWidget(CycleButton.<Integer>builder(
                        c -> Component.translatable("farmrate.config.corner." + c), FarmStore.hudCorner())
                .withValues(0, 1, 2, 3)
                .create(x, y, W, H, Component.translatable("farmrate.config.corner"),
                        (b, v) -> FarmStore.setHudCorner(v)));
        y += GAP;

        addRenderableWidget(new RangeSlider(x, y, W, H));
        y += GAP;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            List<String> names = new ArrayList<>();
            for (FarmStore.Farm f : FarmStore.farms(mc)) {
                names.add(f.name);
            }
            String current = FarmStore.currentFarm(mc).name;
            if (!names.contains(current)) {
                names.add(current);
            }
            y += 6;
            addRenderableWidget(CycleButton.<String>builder(Component::literal, current)
                    .withValues(names)
                    .create(x, y, W, H, Component.translatable("farmrate.config.current"),
                            (b, v) -> FarmStore.setCurrent(mc, v)));
            y += GAP;

            addRenderableWidget(Button.builder(Component.translatable("farmrate.config.reset"), b -> {
                FarmStore.Farm f = FarmStore.currentFarm(mc);
                f.reset();
                FarmStore.save();
                b.setMessage(Component.translatable("farmrate.config.reset.done", f.name));
                b.active = false;
            }).bounds(x, y, W, H).build());
            y += GAP;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, Math.max(y + 12, this.height - 32), W, H).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);
        if (Minecraft.getInstance().level == null) {
            g.centeredText(this.font, Component.translatable("farmrate.config.no_world"),
                    this.width / 2, Math.max(32, this.height / 6) + GAP * 4 + 6, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        FarmStore.setRange(range);
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(parent);
        }
    }

    private final class RangeSlider extends AbstractSliderButton {
        RangeSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (range - MIN_RANGE) / (double) (MAX_RANGE - MIN_RANGE));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("farmrate.config.range", range));
        }

        @Override
        protected void applyValue() {
            range = Mth.clamp((int) Math.round(MIN_RANGE + value * (MAX_RANGE - MIN_RANGE)), MIN_RANGE, MAX_RANGE);
        }
    }
}
