package com.shumsic.autodrinkominousbottles;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.OminousBottleAmplifier;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.Map;

public class AutoDrinkOminousBottlesClient implements ClientModInitializer {
    // Hard-set config (per request)
    private static final boolean PULL_FROM_INVENTORY = true;
    private static final boolean PRIORITIZE_HIGHEST_LEVEL = true;
    private static final boolean AUTO_DISABLE = false; // unused (no auto-disable)

    // Stable timings (no settings UI)
    private static final int HOLD_USE_TICKS = 45;
    private static final int SWAP_BACK_DELAY_TICKS = 3;
    private static final int COOLDOWN_TICKS = 0;

    private static boolean enabled = false;

    private int ticksToReleaseUse = -1;
    private int ticksToSwapBack = -1;
    private int prevHotbarSlot = -1;
    private boolean useWasForced = false;

    private long lastTriggerTick = -1000;
    private boolean victoryVisibleLastTick = false;
    private boolean firedThisVictory = false;

    private int victoryMissingTicks = 0;
    private int triggerLockTicks = 0;

    @Override
    public void onInitializeClient() {
        KeyMapping toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.auto_drink_ominous_bottles.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            KeyMapping.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggle.consumeClick()) {
                enabled = !enabled;
                if (client.player != null) {
                    client.player.sendOverlayMessage(Component.literal("Auto-Drink Ominous Bottles: " + (enabled ? "Enabled" : "Disabled")));
                }
                firedThisVictory = false;
                victoryVisibleLastTick = false;
                useWasForced = false;
                ticksToReleaseUse = -1;
                ticksToSwapBack = -1;
                prevHotbarSlot = -1;
            }

            if (!enabled) return;
            if (client.player == null || client.level == null || client.gameMode == null) return;

            if (triggerLockTicks > 0) triggerLockTicks--;

            boolean victoryNow = isRaidVictoryBarVisible(client);

if (victoryNow) {
    victoryMissingTicks = 0;
    if (!victoryVisibleLastTick && !firedThisVictory && triggerLockTicks == 0) {
        tryDrink(client);
    }
} else {
    victoryMissingTicks++;
    if (victoryMissingTicks >= 40) { // ~2 seconds at 20 TPS
        firedThisVictory = false;
    }
}

victoryVisibleLastTick = victoryNow;

            // Maintain hold-to-use and swap-back
            if (useWasForced) {
                if (ticksToReleaseUse <= 0 || !client.player.isUsingItem() || (client.player.isUsingItem() && client.player.getUseItemRemainingTicks() <= 1)) {
                    client.options.keyUse.setDown(false);
                    useWasForced = false;
                    ticksToSwapBack = SWAP_BACK_DELAY_TICKS;
                } else {
                    ticksToReleaseUse--;
                }
            } else if (ticksToSwapBack >= 0) {
                if (ticksToSwapBack == 0) {
                    if (prevHotbarSlot >= 0) {
                        client.player.getInventory().setSelectedSlot(prevHotbarSlot);
                        if (client.getConnection() != null) {
                            client.getConnection().send(new ServerboundSetCarriedItemPacket(prevHotbarSlot));
                        }
                    }
                    prevHotbarSlot = -1;
                    ticksToSwapBack = -1;
                } else {
                    ticksToSwapBack--;
                }
            }
        });
    }

    private boolean isRaidVictoryBarVisible(Minecraft client) {
    try {
        BossHealthOverlay hud = client.gui.getBossOverlay();
        boolean sawMap = false;

        for (Field f : hud.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            Object v = f.get(hud);
            if (!(v instanceof Map<?, ?> m)) continue;

            sawMap = true;
            for (Object o : m.values()) {
                if (o instanceof LerpingBossEvent bar) {
                    if (RaidMessageDetector.isRaidVictory(bar.getName())) return true;
                }
            }
        }

        if (sawMap) return false;
    } catch (Throwable ignored) { }
    return false;
    }

    private void tryDrink(Minecraft client) {
        firedThisVictory = true;
        triggerLockTicks = 60; // ~3 seconds lock

        long now = client.level.getGameTime();
        if (now - lastTriggerTick < COOLDOWN_TICKS) return;
        lastTriggerTick = now;

        int hotbarSlot = selectBottleHotbarSlot(client);

        if (hotbarSlot == -1 && PULL_FROM_INVENTORY) {
            int invSlotId = selectBottleInventorySlotId(client); // slot id for SWAP
            if (invSlotId != -1) {
                int targetHotbar = firstEmptyHotbarSlot(client);
                if (targetHotbar == -1) targetHotbar = (client.player.getInventory().getSelectedSlot() + 1) % 9;

                try {
                    client.gameMode.handleContainerInput(
                        client.player.containerMenu.containerId,
                        invSlotId,
                        targetHotbar,
                        ContainerInput.SWAP,
                        client.player
                    );
                    hotbarSlot = targetHotbar;
                } catch (Throwable ignored) {
                    hotbarSlot = -1;
                }
            }
        }

        if (hotbarSlot == -1) {
            if (PULL_FROM_INVENTORY) {
                say(client, "No Ominous Bottles found!");
            } else {
                say(client, "No Ominous Bottles found in hotbar!");
            }
            return;
        }

        prevHotbarSlot = client.player.getInventory().getSelectedSlot();
        client.player.getInventory().setSelectedSlot(hotbarSlot);
        if (client.getConnection() != null) {
            client.getConnection().send(new ServerboundSetCarriedItemPacket(hotbarSlot));
        }

        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.options.keyUse.setDown(true);
        useWasForced = true;
        ticksToReleaseUse = HOLD_USE_TICKS;
    }

    private int selectBottleHotbarSlot(Minecraft client) {
        if (!PRIORITIZE_HIGHEST_LEVEL) {
            for (int i = 0; i < 9; i++) {
                ItemStack st = client.player.getInventory().getItem(i);
                if (isOminousBottle(st)) return i;
            }
            return -1;
        }

        int bestSlot = -1;
        int bestLevel = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = client.player.getInventory().getItem(i);
            if (!isOminousBottle(st)) continue;
            int lvl = getOminousLevel(st);
            if (bestSlot == -1 || lvl > bestLevel) {
                bestSlot = i;
                bestLevel = lvl;
            }
        }
        return bestSlot;
    }

    // Returns slot id for SWAP: inventory slots 9..35
    private int selectBottleInventorySlotId(Minecraft client) {
        if (!PRIORITIZE_HIGHEST_LEVEL) {
            for (int slotId = 9; slotId <= 35; slotId++) {
                ItemStack st = client.player.getInventory().getItem(slotId);
                if (isOminousBottle(st)) return slotId;
            }
            return -1;
        }

        int bestSlotId = -1;
        int bestLevel = -1;
        for (int slotId = 9; slotId <= 35; slotId++) {
            ItemStack st = client.player.getInventory().getItem(slotId);
            if (!isOminousBottle(st)) continue;
            int lvl = getOminousLevel(st);
            if (bestSlotId == -1 || lvl > bestLevel) {
                bestSlotId = slotId;
                bestLevel = lvl;
            }
        }
        return bestSlotId;
    }

    private int firstEmptyHotbarSlot(Minecraft client) {
        for (int i = 0; i < 9; i++) {
            if (client.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return -1;
    }

    private boolean isOminousBottle(ItemStack st) {
        return st != null && !st.isEmpty() && st.getItem() == Items.OMINOUS_BOTTLE;
    }

    private int getOminousLevel(ItemStack st) {
        try {
            OminousBottleAmplifier comp = st.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER);
            int amp = comp != null ? comp.value() : 0;
            return 1 + amp;
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private void say(Minecraft client, String msg) {
        if (client.player != null) client.player.sendSystemMessage(Component.literal(msg));
    }
}
