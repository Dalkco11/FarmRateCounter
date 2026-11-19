package com.dalkco.farmrate;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class FarmRateClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("farmrate");

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("farmrate", "farmrate"));

    private KeyMapping selectKey;
    private KeyMapping hudKey;
    private HopperTracker tracker;

    // drag selection
    private boolean selectWasDown;
    private Boolean dragAdding;
    private BlockPos lastDragPos;

    // storage snapshot detection
    private BlockPos lastUsedBlock;
    private int lastUsedAge = Integer.MAX_VALUE;
    private int containerOpenTicks;
    private boolean snapshotHandled;
    private int saveTimer;

    @Override
    public void onInitializeClient() {
        FarmStore.load();
        Minecraft mc = Minecraft.getInstance();
        tracker = new HopperTracker(mc);

        selectKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.farmrate.select", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, CATEGORY));
        hudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.farmrate.hud", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) {
                lastUsedBlock = hit.getBlockPos();
                lastUsedAge = 0;
            }
            return InteractionResult.PASS;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FarmStore.save();
            tracker.reset();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                return;
            }
            while (hudKey.consumeClick()) {
                FarmStore.setShowHud(!FarmStore.showHud());
            }
            handleSelection(client);
            tracker.tick();
            detectStorageOpen(client);
            WorldOverlay.draw(client);
            if (++saveTimer >= 200) {
                saveTimer = 0;
                FarmStore.saveIfDirty();
            }
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("farmrate", "panel"), HudPanel::render);

        registerCommands();
    }

    // ------------------------------------------------------------------ selection

    private static BlockPos lookedAtHopper(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockState(bhr.getBlockPos()).is(Blocks.HOPPER)) {
            return bhr.getBlockPos();
        }
        return null;
    }

    private static BlockPos lookedAtContainer(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockEntity(bhr.getBlockPos()) instanceof Container) {
            return bhr.getBlockPos();
        }
        return null;
    }

    /** Tap = toggle the hopper under the crosshair; hold and sweep = add (or remove) every hopper you look at. */
    private void handleSelection(Minecraft mc) {
        while (selectKey.consumeClick()) {
            // state is read through isDown() below
        }
        boolean down = selectKey.isDown() && mc.gui.screen() == null;
        if (!down) {
            selectWasDown = false;
            dragAdding = null;
            lastDragPos = null;
            return;
        }
        selectWasDown = true;
        BlockPos pos = lookedAtHopper(mc);
        if (pos == null || pos.equals(lastDragPos)) {
            return;
        }
        lastDragPos = pos;
        FarmStore.Farm owner = FarmStore.farmOfHopper(mc, pos);
        if (dragAdding == null) {
            dragAdding = owner == null;
        }
        FarmStore.Farm current = FarmStore.currentFarm(mc);
        if (dragAdding && owner == null) {
            current.hoppers.add(pos.asLong());
            FarmStore.markDirty();
            actionBar(mc, "+ воронка → " + current.name + " (" + current.hoppers.size() + ")", current.color);
        } else if (!dragAdding && owner != null) {
            owner.hoppers.remove(pos.asLong());
            FarmStore.markDirty();
            actionBar(mc, "− воронка из " + owner.name + " (" + owner.hoppers.size() + ")", owner.color);
        }
    }

    private void detectStorageOpen(Minecraft mc) {
        lastUsedAge++;
        var player = mc.player;
        if (player.containerMenu == player.inventoryMenu) {
            containerOpenTicks = 0;
            snapshotHandled = false;
            return;
        }
        if (snapshotHandled || lastUsedBlock == null || lastUsedAge > 60 || ++containerOpenTicks < 3) {
            return;
        }
        snapshotHandled = true;
        FarmStore.Farm farm = ChestSnapshots.farmFor(mc, lastUsedBlock);
        if (farm != null) {
            ChestSnapshots.onOpened(mc, player, farm);
        }
    }

    private static void actionBar(Minecraft mc, String text, int color) {
        mc.player.sendOverlayMessage(Component.literal(text).withColor(color & 0xFFFFFF));
    }

    // ------------------------------------------------------------------ commands

    private static final SuggestionProvider<FabricClientCommandSource> FARM_NAMES = (ctx, builder) -> {
        Minecraft mc = Minecraft.getInstance();
        java.util.List<String> names = new java.util.ArrayList<>();
        for (FarmStore.Farm f : FarmStore.farms(mc)) {
            names.add(f.name);
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, Component c) {
        ctx.getSource().sendFeedback(Component.literal("[Farm Rate] ").withStyle(ChatFormatting.DARK_AQUA).append(c));
    }

    private static FarmStore.Farm farmArg(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft mc = Minecraft.getInstance();
        FarmStore.Farm f = FarmStore.farm(mc, StringArgumentType.getString(ctx, "name"), false);
        if (f == null) {
            ctx.getSource().sendError(Component.literal("Нет фермы с таким названием"));
        }
        return f;
    }

    private static int stats(CommandContext<FabricClientCommandSource> ctx, FarmStore.Farm f) {
        if (f == null) {
            return 0;
        }
        feedback(ctx, Component.literal(f.name).withColor(f.color & 0xFFFFFF)
                .append(Component.literal(": " + Fmt.rate(f.ratePerHour())
                        + " · 5 мин: " + Fmt.rate(f.recentRatePerHour())
                        + " · собрано " + Fmt.num(f.total()) + " за " + Fmt.time(f.observedTicks)
                        + " · воронок " + f.hoppers.size() + (f.active ? "" : " · пауза (далеко)"))
                        .withStyle(ChatFormatting.WHITE)));
        f.totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> ctx.getSource().sendFeedback(Component.literal("  ")
                        .append(Fmt.name(e.getKey()))
                        .append(": " + Fmt.num(e.getValue()) + " (" + Fmt.rate(f.itemRate(e.getValue())) + ")")
                        .withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("farmrate")
                        .executes(ctx -> stats(ctx, FarmStore.currentFarm(Minecraft.getInstance())))
                        .then(ClientCommands.literal("farm")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(FARM_NAMES)
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            FarmStore.setCurrent(Minecraft.getInstance(), name);
                                            FarmStore.Farm f = FarmStore.currentFarm(Minecraft.getInstance());
                                            feedback(ctx, Component.literal("Текущая ферма: ")
                                                    .append(Component.literal(name).withColor(f.color & 0xFFFFFF))
                                                    .append(" — новые воронки попадут в неё (бинд J или /farmrate add)"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("farms").executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            boolean any = false;
                            for (FarmStore.Farm f : FarmStore.farms(mc)) {
                                any = true;
                                feedback(ctx, Component.literal(f.name).withColor(f.color & 0xFFFFFF)
                                        .append(Component.literal(": " + Fmt.rate(f.ratePerHour()) + ", воронок "
                                                + f.hoppers.size() + ", сундуков " + f.chests.size()
                                                + (f.name.equals(FarmStore.world(mc).current) ? "  ← текущая" : ""))
                                                .withStyle(ChatFormatting.WHITE)));
                            }
                            if (!any) {
                                feedback(ctx, Component.literal("Ферм пока нет: /farmrate farm <название>"));
                            }
                            return 1;
                        }))
                        .then(ClientCommands.literal("add").executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            BlockPos pos = lookedAtHopper(mc);
                            if (pos == null) {
                                ctx.getSource().sendError(Component.literal("Посмотри на воронку"));
                                return 0;
                            }
                            if (FarmStore.farmOfHopper(mc, pos) != null) {
                                ctx.getSource().sendError(Component.literal("Эта воронка уже выделена"));
                                return 0;
                            }
                            FarmStore.Farm f = FarmStore.currentFarm(mc);
                            f.hoppers.add(pos.asLong());
                            FarmStore.save();
                            feedback(ctx, Component.literal("Воронка добавлена в " + f.name + " (" + f.hoppers.size() + ")"));
                            return 1;
                        }))
                        .then(ClientCommands.literal("remove").executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            BlockPos pos = lookedAtHopper(mc);
                            FarmStore.Farm f = pos == null ? null : FarmStore.farmOfHopper(mc, pos);
                            if (f == null) {
                                ctx.getSource().sendError(Component.literal("Посмотри на выделенную воронку"));
                                return 0;
                            }
                            f.hoppers.remove(pos.asLong());
                            FarmStore.save();
                            feedback(ctx, Component.literal("Воронка убрана из " + f.name));
                            return 1;
                        }))
                        .then(ClientCommands.literal("chest").executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            BlockPos pos = lookedAtContainer(mc);
                            if (pos == null) {
                                ctx.getSource().sendError(Component.literal("Посмотри на сундук / бочку хранилища фермы"));
                                return 0;
                            }
                            FarmStore.Farm owner = ChestSnapshots.farmFor(mc, pos);
                            if (owner != null) {
                                owner.chests.remove(pos.asLong());
                                BlockPos other = ChestSnapshots.otherHalf(mc, pos);
                                if (other != null) {
                                    owner.chests.remove(other.asLong());
                                }
                                FarmStore.save();
                                feedback(ctx, Component.literal("Хранилище убрано из " + owner.name));
                                return 1;
                            }
                            FarmStore.Farm f = FarmStore.currentFarm(mc);
                            f.chests.add(pos.asLong());
                            FarmStore.save();
                            feedback(ctx, Component.literal("Хранилище фермы " + f.name
                                    + " отмечено. Открывай его время от времени — покажу, сколько пришло и сколько в час."));
                            return 1;
                        }))
                        .then(ClientCommands.literal("stats")
                                .executes(ctx -> stats(ctx, FarmStore.currentFarm(Minecraft.getInstance())))
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(FARM_NAMES)
                                        .executes(ctx -> stats(ctx, farmArg(ctx)))))
                        .then(ClientCommands.literal("reset")
                                .executes(ctx -> {
                                    FarmStore.Farm f = FarmStore.currentFarm(Minecraft.getInstance());
                                    f.reset();
                                    FarmStore.save();
                                    feedback(ctx, Component.literal("Счётчик " + f.name + " сброшен"));
                                    return 1;
                                })
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(FARM_NAMES)
                                        .executes(ctx -> {
                                            FarmStore.Farm f = farmArg(ctx);
                                            if (f == null) {
                                                return 0;
                                            }
                                            f.reset();
                                            FarmStore.save();
                                            feedback(ctx, Component.literal("Счётчик " + f.name + " сброшен"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("delete")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(FARM_NAMES)
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            if (!FarmStore.deleteFarm(Minecraft.getInstance(), name)) {
                                                ctx.getSource().sendError(Component.literal("Нет фермы с таким названием"));
                                                return 0;
                                            }
                                            feedback(ctx, Component.literal("Ферма " + name + " удалена"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("show")
                                .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            FarmStore.setShowWorld(BoolArgumentType.getBool(ctx, "enabled"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("hud")
                                .then(ClientCommands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            FarmStore.setShowHud(BoolArgumentType.getBool(ctx, "enabled"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("range")
                                .then(ClientCommands.argument("blocks", IntegerArgumentType.integer(8, 128))
                                        .executes(ctx -> {
                                            int r = IntegerArgumentType.getInteger(ctx, "blocks");
                                            FarmStore.setRange(r);
                                            feedback(ctx, Component.literal("Считаю, когда ты ближе " + r + " блоков к воронке"));
                                            return 1;
                                        })))
        ));
    }
}
