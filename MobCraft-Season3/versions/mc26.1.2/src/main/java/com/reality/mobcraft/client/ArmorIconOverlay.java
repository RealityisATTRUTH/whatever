package com.reality.mobcraft.client;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.item.ArmorUpgrades;
import com.reality.mobcraft.item.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.ExtractItemDecorationsCallback;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a piece's upgrade on top of its icon in inventories and the hotbar:
 * the orange eye or the pearl trim on a chestplate, ears or tendrils on top
 * of a helmet. The armor's own icon is drawn first, so trims still show.
 */
public final class ArmorIconOverlay {

    private ArmorIconOverlay() {}

    public static void register() {
        ExtractItemDecorationsCallback.EVENT.register((graphics, font, stack, x, y) -> draw(graphics, stack, x, y));
    }

    private static void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        String upgrade = stack.get(ModItems.ARMOR_UPGRADE);
        if (upgrade == null) return;
        EquipmentSlot slot = ArmorUpgrades.armorSlot(stack);
        if (slot == EquipmentSlot.CHEST) {
            if (upgrade.equals(ArmorUpgrades.STALKER_SHARD)) overlay(graphics, "stalker_eye", x, y);
            else if (upgrade.equals(ArmorUpgrades.ENDER_SHARD)) overlay(graphics, "pearl_trim", x, y);
        } else if (slot == EquipmentSlot.HEAD) {
            switch (upgrade) {
                case ArmorUpgrades.EAR_LEFT -> overlay(graphics, "bat_ear_left", x, y);
                case ArmorUpgrades.EAR_RIGHT -> overlay(graphics, "bat_ear_right", x, y);
                case ArmorUpgrades.EAR_BOTH -> {
                    overlay(graphics, "bat_ear_left", x, y);
                    overlay(graphics, "bat_ear_right", x, y);
                }
                // The tendrils' names are swapped from their ids, so each shows on its named side.
                case ArmorUpgrades.TENDRIL_LEFT -> overlay(graphics, "warden_tendril_right", x, y);
                case ArmorUpgrades.TENDRIL_RIGHT -> overlay(graphics, "warden_tendril_left", x, y);
                case ArmorUpgrades.TENDRIL_BOTH -> {
                    overlay(graphics, "warden_tendril_left", x, y);
                    overlay(graphics, "warden_tendril_right", x, y);
                }
                default -> { }
            }
        }
    }

    /** Each overlay is a 16x16 picture laid over the whole icon. */
    private static void overlay(GuiGraphicsExtractor graphics, String name, int x, int y) {
        Identifier texture = MobCraft.id("textures/gui/armor_overlay/" + name + ".png");
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, 16, 16, 16, 16);
    }
}
