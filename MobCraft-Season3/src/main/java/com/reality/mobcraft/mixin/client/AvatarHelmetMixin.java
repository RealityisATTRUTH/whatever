package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.HelmetUpgradeLayer;
import com.reality.mobcraft.item.ModItems;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies the worn helmet's and chestplate's upgrades into the player's render state for HelmetUpgradeLayer. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarHelmetMixin<AvatarlikeEntity extends Avatar & ClientAvatarEntity> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void mobcraft$helmetUpgrade(AvatarlikeEntity entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        ItemStack helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
        state.setData(HelmetUpgradeLayer.UPGRADE, helmet.get(ModItems.ARMOR_UPGRADE));
        state.setData(HelmetUpgradeLayer.FOIL, helmet.hasFoil());
        ItemStack chestplate = entity.getItemBySlot(EquipmentSlot.CHEST);
        state.setData(HelmetUpgradeLayer.CHEST_UPGRADE, chestplate.get(ModItems.ARMOR_UPGRADE));
        state.setData(HelmetUpgradeLayer.CHEST_FOIL, chestplate.hasFoil());
    }
}
