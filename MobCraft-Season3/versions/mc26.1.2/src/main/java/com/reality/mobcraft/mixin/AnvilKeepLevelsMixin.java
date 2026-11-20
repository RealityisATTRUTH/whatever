package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.reality.mobcraft.item.Duskvein;
import com.reality.mobcraft.item.DuskveinAnvil;
import net.minecraft.world.entity.player.Player;
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
 * Greater enchants (levels past the normal max) need the Duskvein Anvil.
 *  - A normal anvil shows no result when either item has a greater enchant.
 *  - The Duskvein Anvil keeps greater levels, merges Wind Burst past III up to V, and is never "Too Expensive"
 *    (the cost tops out at 39 levels).
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilKeepLevelsMixin {

    @Shadow @Final private DataSlot cost;

    private boolean mobcraft$strong() {
        return ((ItemCombinerAccessor) this).mobcraft$access()
                .evaluate((level, pos) -> level.getBlockState(pos).is(DuskveinAnvil.BLOCK), false);
    }

    @WrapOperation(method = "createResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;hasInfiniteMaterials()Z"))
    private boolean mobcraft$neverTooExpensive(Player player, Operation<Boolean> original) {
        return original.call(player) || mobcraft$strong();
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    private void mobcraft$greaterEnchants(CallbackInfo ci) {
        ItemCombinerMenu menu = (ItemCombinerMenu) (Object) this;
        ItemStack left = menu.getSlot(0).getItem(), right = menu.getSlot(1).getItem(), result = menu.getSlot(2).getItem();
        if (result.isEmpty()) return;
        if (!mobcraft$strong()) {
            if (Duskvein.hasGreater(left) || Duskvein.hasGreater(right)) {
                menu.getSlot(2).set(ItemStack.EMPTY);
                cost.set(0);
            }
            return;
        }
        Duskvein.keepLevels(left, right, result);
        Duskvein.mergeWindBurst(left, right, result, DuskveinAnvil.WIND_BURST_CAP);
        if (cost.get() >= 40) cost.set(39);
    }
}
