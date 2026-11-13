package com.shumsic.autodrinkominousbottles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.OminousBottleAmplifier;


/** Minecraft 1.21.9 to 1.21.11. */
public final class GameImpl implements Game {

    private static final String VICTORY = "event.minecraft.raid.victory.full";
    /** Looked up each time: Forge and NeoForge build the mod before the game exists. */
    private Minecraft mc;

    @Override
    public boolean inWorld() {
        mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.level != null && mc.gameMode != null;
    }
    @Override public boolean screenOpen() { return mc.screen != null; }

    @Override
    public boolean raidVictoryShowing() {
        for (Object o : BossBars.events(mc.gui.getBossOverlay())) {
            if (o instanceof LerpingBossEvent bar && bar.getName().getContents() instanceof TranslatableContents t
                    && VICTORY.equals(t.getKey())) return true;
        }
        return false;
    }

    @Override
    public int bottleLevel(int slot) {
        ItemStack s = mc.player.getInventory().getItem(slot);
        if (!s.is(Items.OMINOUS_BOTTLE)) return 0;
        OminousBottleAmplifier a = s.get(DataComponents.OMINOUS_BOTTLE_AMPLIFIER);
        return 1 + (a == null ? 0 : a.value());
    }

    @Override public boolean slotEmpty(int slot) { return mc.player.getInventory().getItem(slot).isEmpty(); }
    @Override public int selectedSlot() { return mc.player.getInventory().getSelectedSlot(); }

    @Override
    public void selectSlot(int hotbarSlot) {
        mc.player.getInventory().setSelectedSlot(hotbarSlot);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(hotbarSlot));
    }

    @Override
    public void swapWithHotbar(int inventorySlot, int hotbarSlot) {
        mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, inventorySlot, hotbarSlot, ClickType.SWAP, mc.player);
    }

    @Override
    public void startUsing() {
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.options.keyUse.setDown(true);
    }

    @Override public void stopUsing() { mc.options.keyUse.setDown(false); }
    @Override public boolean usingItem() { return mc.player.isUsingItem(); }
    @Override public void actionBar(String message) { mc.player.displayClientMessage(Component.literal(message), true); }
}
