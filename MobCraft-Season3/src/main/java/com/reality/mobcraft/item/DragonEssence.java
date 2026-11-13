package com.reality.mobcraft.item;

import com.reality.mobcraft.mob.kit.KitUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dragon Essence: the Ender Dragon's mob item, its egg cracked open with a purple glow
 * coming out. It drops when another player kills the Ender Dragon (see MobRelics).
 *
 * Right-click it to absorb it, like the Shulker Essence (ShulkerEssence.EssenceItem).
 * Then the Essence Power key (B) pours the dragon's strength into you:
 *
 *   Absorption IV   2:00
 *   Regeneration II 0:30
 *   Resistance I    1:00
 *
 *   Cooldown    4 minutes from each use
 *   Keeps       until you die, or until you absorb a different essence
 *   Not for     the Ender Dragon player
 */
public final class DragonEssence {

    public static final String DRAGON = "dragon";
    private static final int COOLDOWN_TICKS = 4800;
    private static final Map<UUID, Integer> READY_AT = new HashMap<>();

    private DragonEssence() {}

    public static void use(ServerPlayer player) {
        int now = player.level().getServer().getTickCount();
        Integer ready = READY_AT.get(player.getUUID());
        if (ready != null && now < ready) {
            int seconds = (ready - now + 19) / 20;
            KitUtil.actionBar(player, "Dragon Essence ready in " + (seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s") + ".");
            return;
        }
        READY_AT.put(player.getUUID(), now + COOLDOWN_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 1200, 0));
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.6f, 1.3f);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 0.5, 0.9, 0.5, 0.05);
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.6);
        KitUtil.actionBar(player, "The dragon's strength fills you.");
    }
}
