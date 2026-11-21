package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.HelmetUpgradeLayer;
import com.reality.mobcraft.item.ModItems;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies the worn helmet's and chestplate's upgrades, and the mob's look, into the player's render state for HelmetUpgradeLayer and MobLookLayer. */
@Mixin(PlayerRenderer.class)
public abstract class AvatarHelmetMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("RETURN"))
    private void mobcraft$helmetUpgrade(net.minecraft.client.player.AbstractClientPlayer entity, PlayerRenderState state, float partialTicks, CallbackInfo ci) {
        ItemStack helmet = entity.getItemBySlot(EquipmentSlot.HEAD);
        com.reality.mobcraft.compat.RenderStateDataKey.set(state, HelmetUpgradeLayer.UPGRADE, helmet.get(ModItems.ARMOR_UPGRADE));
        com.reality.mobcraft.compat.RenderStateDataKey.set(state, HelmetUpgradeLayer.FOIL, helmet.hasFoil());
        com.reality.mobcraft.compat.RenderStateDataKey.set(state, com.reality.mobcraft.client.SkullVisorLayer.WORN, helmet.is(ModItems.SKULL_VISOR));
        ItemStack chestplate = entity.getItemBySlot(EquipmentSlot.CHEST);
        com.reality.mobcraft.compat.RenderStateDataKey.set(state, HelmetUpgradeLayer.CHEST_UPGRADE, chestplate.get(ModItems.ARMOR_UPGRADE));
        com.reality.mobcraft.compat.RenderStateDataKey.set(state, HelmetUpgradeLayer.CHEST_FOIL, chestplate.hasFoil());
        com.reality.mobcraft.client.MobLookLayer.extract(entity, state, partialTicks);
    }
}
