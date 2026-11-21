package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.Duskvein;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enchanting with Duskvein: right after the table puts the enchants on, and before it uses up the lapis, enchants
 * at their max may be pushed past it (Duskvein.empower).
 */
@Mixin(EnchantmentMenu.class)
public abstract class EnchantDuskveinMixin {

    @Shadow @Final private Container enchantSlots;

    @Inject(method = "lambda$clickMenuButton$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void mobcraft$empower(ItemStack input, int button, Player player, int cost, ItemStack lapis, Level level,
                                  BlockPos pos, CallbackInfo ci) {
        if (Duskvein.is(lapis)) Duskvein.empower(enchantSlots.getItem(0), player, level.getRandom());
    }
}
