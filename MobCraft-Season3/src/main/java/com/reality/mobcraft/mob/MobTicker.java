package com.reality.mobcraft.mob;

import com.reality.mobcraft.effect.CurseEffect;
import com.reality.mobcraft.effect.ModEffects;
import com.reality.mobcraft.mob.kit.Cooldowns;
import com.reality.mobcraft.mob.kit.KitEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Runs every server tick: energy regeneration, curse countdown and kit ticks. */
public final class MobTicker {

    private MobTicker() {}

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Before the kit's tick, so mobs the new mob also counts as allies stay calm.
            FormerMob.tick(player);
            BlindSync.tick(player);
            Jeff.tick(player);
            MobType type = PlayerMob.get(player);
            if (type == null) continue;
            MobKit kit = MobKits.get(type);

            // Energy. Regen can be negative, like the Piglin's drain outside the Nether.
            float perSecond = kit.energyRegenPerSecond(player, type);
            if (perSecond != 0.0f) {
                PlayerMob.setEnergy(player, PlayerMob.energy(player) + perSecond / 20.0f);
            }

            // Curse countdown.
            int curse = PlayerMob.curseTicks(player);
            if (curse > 0) {
                PlayerMob.setCurse(player, curse - 1);
                if (curse - 1 == 0) {
                    kit.onCurseEnded(player);
                    curseLiftedSound(player);
                    player.sendSystemMessage(Component.literal("Your curse has worn off."));
                }
            }

            CurseEffect.tick(player);
            KitEvents.tickAll(player);
            kit.tick(player);
        }
    }

    /** Server only. Curses a player for the given ticks and tells their kit. */
    public static void curse(ServerPlayer player, int ticks) {
        boolean wasCursed = PlayerMob.isCursed(player);
        PlayerMob.setCurse(player, ticks);
        if (!wasCursed) {
            com.reality.mobcraft.MobAdvancements.award(player, "cursed");
            MobKits.get(PlayerMob.get(player)).onCursed(player);
            player.level().playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.7f, 0.7f);
        }
    }

    /** A soft chime when a curse ends, by timer, Ghast water or the antidote. */
    private static void curseLiftedSound(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    /** Server only. Lifts a curse early. */
    public static void uncurse(ServerPlayer player) {
        // The Curse potion's effect would just curse them again, so it goes too.
        player.removeEffect(ModEffects.CURSE);
        if (!PlayerMob.isCursed(player)) return;
        PlayerMob.setCurse(player, 0);
        MobKits.get(PlayerMob.get(player)).onCurseEnded(player);
        curseLiftedSound(player);
    }

    /**
     * Server only. Switches a player's mob, running the old kit's cleanup and
     * the new kit's setup. Any curse is lifted, since it belonged to the old mob.
     */
    public static void assign(ServerPlayer player, MobType type) {
        if (type != null) com.reality.mobcraft.MobAdvancements.award(player, "become_mob");
        if (type != null) {
            com.reality.mobcraft.MobAdvancements.award(player, "mob_roster");
            com.reality.mobcraft.MobAdvancements.award(player, "mob_" + type.name().toLowerCase(java.util.Locale.ROOT));
        }
        if (type == MobType.ENDER_DRAGON) com.reality.mobcraft.MobAdvancements.award(player, "dragon");
        MobType old = PlayerMob.get(player);
        if (old != null) {
            MobKit oldKit = MobKits.get(old);
            player.removeEffect(ModEffects.CURSE);
            if (PlayerMob.isCursed(player)) {
                PlayerMob.setCurse(player, 0);
                oldKit.onCurseEnded(player);
            }
            oldKit.onRemoved(player);
        }
        Cooldowns.clear(player);
        FormerMob.onSwitch(player, old, type);
        PlayerMob.set(player, type);
        if (type != null) MobKits.get(type).onAssigned(player);
    }
}
