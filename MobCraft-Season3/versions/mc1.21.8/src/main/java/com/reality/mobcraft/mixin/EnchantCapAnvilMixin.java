package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.EnchantCap;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The anvil shows no result when it would put an item past its MobCraft enchant cap (see EnchantCap). */
@Mixin(AnvilMenu.class)
public abstract class EnchantCapAnvilMixin {
    @Shadow @Final private DataSlot cost;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mobcraft$enchantCap(CallbackInfo ci) {
        ItemCombinerMenu menu = (ItemCombinerMenu) (Object) this;
        ItemStack left = menu.getSlot(0).getItem(), result = menu.getSlot(2).getItem();
        if (result.isEmpty()) return;
        int after = EnchantCap.count(result);
        if (after > EnchantCap.cap(result) && after > EnchantCap.count(left)) {
            menu.getSlot(2).set(ItemStack.EMPTY);
            cost.set(0);
        }
    }
}
