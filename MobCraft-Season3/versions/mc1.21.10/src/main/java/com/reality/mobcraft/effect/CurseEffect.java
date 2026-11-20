package com.reality.mobcraft.effect;

import com.reality.mobcraft.mob.MobTicker;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Curse effect, from the Curse potion. A player with a mob who gets it
 * is cursed for as long as the effect lasts, the same curse their mob sheet
 * describes. Players without a mob just get the icon.
 */
public final class CurseEffect extends MobEffect {

    /** Players whose current curse came from this effect, so it can end with the effect. */
    private static final Set<UUID> FROM_POTION = new HashSet<>();

    /**
     * Every tick: milk can't wash off a potion curse, only the Curse Antidote
     * can. If the effect was cleared while the curse still runs, it comes back
     * for the time left, so the icon always matches the energy bar.
     */
    public static void tick(ServerPlayer player) {
        if (!FROM_POTION.contains(player.getUUID())) return;
        int left = PlayerMob.curseTicks(player);
        if (left <= 0) {
            FROM_POTION.remove(player.getUUID());
        } else if (!player.hasEffect(ModEffects.CURSE)) {
            player.addEffect(new MobEffectInstance(ModEffects.CURSE, left, 0));
        }
    }

    CurseEffect() {
        super(MobEffectCategory.HARMFUL, 0x5A1E78); // dragon purple
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer player && PlayerMob.get(player) != null && !PlayerMob.isCursed(player)) {
            MobEffectInstance effect = player.getEffect(ModEffects.CURSE);
            // Only when the effect is fresh, so the curse doesn't restart in its last second.
            if (effect != null && effect.getDuration() > 20) {
                MobTicker.curse(player, effect.getDuration());
                FROM_POTION.add(player.getUUID());
                player.sendSystemMessage(Component.literal("You've been cursed!").withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
        return super.applyEffectTick(level, entity, amplifier);
    }
}
