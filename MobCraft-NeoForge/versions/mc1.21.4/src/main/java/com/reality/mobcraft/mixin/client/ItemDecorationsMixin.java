package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.ArmorIconOverlay;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.5 has no Fabric event for drawing over item icons, so armor upgrades are drawn
 * after the icon's count and bars, lifted above the item like the count text.
 */
@Mixin(GuiGraphics.class)
public abstract class ItemDecorationsMixin {
    @Inject(method = "renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("TAIL"))
    private void mobcraft$armorOverlay(Font font, ItemStack stack, int x, int y, String text, CallbackInfo ci) {
        if (stack.isEmpty()) return;
        GuiGraphics graphics = (GuiGraphics) (Object) this;
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 200f);
        ArmorIconOverlay.draw(graphics, stack, x, y);
        graphics.pose().popPose();
    }
}
