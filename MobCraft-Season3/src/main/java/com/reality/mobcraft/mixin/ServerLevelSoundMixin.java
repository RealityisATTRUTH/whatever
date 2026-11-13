package com.reality.mobcraft.mixin;

import com.reality.mobcraft.item.Hush;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every sound the server plays counts as noise near an Unseenhush being mined. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSoundMixin {

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"))
    private void mobcraft$noiseAt(Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source,
                                  float volume, float pitch, long seed, CallbackInfo ci) {
        Hush.noise((ServerLevel) (Object) this, x, y, z, except);
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V", at = @At("HEAD"))
    private void mobcraft$noiseFrom(Entity except, Entity from, Holder<SoundEvent> sound, SoundSource source,
                                    float volume, float pitch, long seed, CallbackInfo ci) {
        Hush.noise((ServerLevel) (Object) this, from.getX(), from.getY(), from.getZ(), except);
    }
}
