package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.DuskveinAnvil;
import com.reality.mobcraft.item.Tiers;
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

/**
 * Rerolls at the anvil.
 *  - Any anvil: a tool at full durability plus one of its repair material rerolls its tier.
 *  - Duskvein Anvil: a Gloamite tool plus a Dusk Binding rerolls its two traits and keeps its tier.
 */
@Mixin(AnvilMenu.class)
public abstract class TierAnvilMixin {

    @Shadow @Final private DataSlot cost;
    @Shadow private int repairItemCountCost;

    @Inject(method = "createResult", at = @At("RETURN"))
    private void mobcraft$reroll(CallbackInfo ci) {
        ItemCombinerMenu menu = (ItemCombinerMenu) (Object) this;
        ItemStack left = menu.getSlot(0).getItem(), right = menu.getSlot(1).getItem();
        if (left.isEmpty() || right.isEmpty()) return;
        ItemStack out = ItemStack.EMPTY;
        int price = 0;
        boolean strong = ((ItemCombinerAccessor) this).mobcraft$access()
                .evaluate((level, pos) -> level.getBlockState(pos).is(DuskveinAnvil.BLOCK), false);
        if (strong) {
            out = Tiers.rerollTraits(left, right);
            price = Tiers.TRAIT_REROLL_COST;
        }
        if (out.isEmpty()) {
            out = Tiers.rerollTier(left, right);
            price = Tiers.REROLL_COST;
        }
        if (out.isEmpty()) return;
        menu.getSlot(2).set(out);
        cost.set(price);
        repairItemCountCost = 1;
    }
}
