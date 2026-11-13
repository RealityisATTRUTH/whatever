package com.reality.mobcraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.reality.mobcraft.item.BoundTotem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A totem bound to an Evoker only saves him and his team. For anyone else the game
 * sees no death protection on it, so it stays in their hand and they die.
 * See item/BoundTotem.
 */
@Mixin(LivingEntity.class)
public abstract class BoundTotemMixin {

    @WrapOperation(method = "checkTotemDeathProtection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"))
    private Object mobcraft$onlyTheirTotem(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
        Object value = original.call(stack, type);
        if (value != null && !BoundTotem.canSave((LivingEntity) (Object) this, stack)) return null;
        return value;
    }
}
