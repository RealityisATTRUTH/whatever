package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.glow.MobGlow;
import com.reality.mobcraft.item.ArmorUpgrades;
import com.reality.mobcraft.item.ModItems;
import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * Bat (Fishy).
 *
 *   Passive       half size, flight at about sprint speed, instant respawn
 *   Lifesteal     hitting a player heals you for a quarter of the damage
 *   Confuse       R primes it. Your next hit on a player stops them moving and
 *                 attacking for 25s and jams their RTS. A mob you hit can't get
 *                 new RTS orders for 25s but keeps its current one.
 *                 35 energy, 1:30 cooldown. Stays primed until a hit lands,
 *                 cleared when you log out.
 *   Echolocation  G, only in caves: every living thing within 24 blocks glows
 *                 on your screen for 10s. 15 energy, 10s cooldown.
 *   Curse         Blinded: Blindness the whole time, but Echolocation is
 *                 always on, anywhere, open air or cave. Every living thing
 *                 within 24 blocks glows on your screen and gets a vibration
 *                 marker drawn over the blindness. The mod darkens the screen
 *                 itself (BlindOverlay), so mods that hide Blindness don't help.
 */
public final class BatKit implements MobKit {

    private static final String CONFUSE = "confuse";
    private static final String ECHO = "echolocation";
    private static final String LIFESTEAL = "lifesteal";
    private static final int CONFUSE_TICKS = 500;   // 25 seconds
    private static final float FLY_SPEED = 0.026f;  // about sprinting speed
    private static final float LIFESTEAL_SHARE = 0.25f;
    private static final double ECHO_RANGE = 24;
    private static final int ECHO_COLOR = 0x20FFFF;

    private static final Set<UUID> PRIMED = new HashSet<>();

    @Override
    public void tick(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.SCALE, "bat_scale", -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        KitUtil.flight(player, true, FLY_SPEED);

        // Cursed: blind, but always echolocating.
        boolean cursed = PlayerMob.isCursed(player);
        KitUtil.passive(player, MobEffects.BLINDNESS, 0, cursed);
        if (cursed && player.tickCount % 10 == 0) {
            for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(ECHO_RANGE), e -> e != player && e.isAlive())) {
                MobGlow.glow(player, entity, ECHO_COLOR, 15);
            }
        }
    }

    @Override
    public void onCursed(ServerPlayer player) {
        KitUtil.actionBar(player, "Blinded! You can only see by echolocation.");
    }

    @Override
    public void onCurseEnded(ServerPlayer player) {
        KitUtil.passive(player, MobEffects.BLINDNESS, 0, false);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.SCALE, "bat_scale", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        KitUtil.flight(player, false, FLY_SPEED);
        KitUtil.passive(player, MobEffects.BLINDNESS, 0, false);
        forget(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot == ABILITY_1) {
            if (PRIMED.contains(player.getUUID())) {
                KitUtil.actionBar(player, "Confuse is already primed. Hit someone.");
                return;
            }
            if (!KitUtil.tryUse(player, CONFUSE, "Confuse", 35, 1800)) return;
            PRIMED.add(player.getUUID());
            player.level().playSound(null, player.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 0.8f, 1.3f);
            KitUtil.actionBar(player, "Confuse primed. Your next hit stuns.");
        } else if (slot == ABILITY_2) {
            if (!inCave(player)) {
                KitUtil.actionBar(player, "Echolocation only works in caves.");
                return;
            }
            if (!KitUtil.tryUse(player, ECHO, "Echolocation", 15, 200)) return;
            for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(ECHO_RANGE), e -> e != player && e.isAlive())) {
                MobGlow.glow(player, entity, ECHO_COLOR, 200);
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.BAT_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.5f);
        } else {
            KitUtil.actionBar(player, "The Bat has no third ability.");
        }
    }

    @Override
    public void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        if (victim instanceof Player && MobAbilities.isEnabled(player, LIFESTEAL)) {
            player.heal(damage * LIFESTEAL_SHARE);
        }
        if (PRIMED.remove(player.getUUID())) {
            Confusion.apply(victim, CONFUSE_TICKS, KitUtil.now(player));
            player.level().playSound(null, victim.blockPosition(), SoundEvents.BAT_HURT, SoundSource.PLAYERS, 1.0f, 0.6f);
            if (victim instanceof ServerPlayer target) KitUtil.actionBar(target, "You've been confused for 25 seconds!");
        }
    }

    @Override
    public void onDeath(ServerPlayer player, DamageSource source) {
        ArmorUpgrades.dropAt(player, player.getRandom().nextBoolean()
                ? ModItems.BAT_EAR_LEFT : ModItems.BAT_EAR_RIGHT);
    }

    @Override
    public boolean instantRespawn() {
        return true;
    }

    /** Underground with no sky above. */
    private static boolean inCave(ServerPlayer player) {
        return player.getY() < 60 && !player.level().canSeeSky(player.blockPosition());
    }

    static void forget(ServerPlayer player) {
        PRIMED.remove(player.getUUID());
    }

    // --- Ultimate: Night Swarm ---------------------------------------------------------------
    /**
     * 12 bats burst out around you for 10s. Every enemy within 14 blocks
     * glows for 15s, and enemy players are blinded for 6s.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Night Swarm";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        for (int i = 0; i < 12; i++) {
            Vec3 d = UltimateFx.around(i, 12, 0);
            UltimateFx.summonFor(player, "minecraft:bat", player.getX() + d.x, player.getY() + 1.5, player.getZ() + d.z, "", 200);
        }
        int hit = 0;
        for (LivingEntity enemy : UltimateFx.enemies(player, 14)) {
            KitUtil.effect(enemy, MobEffects.GLOWING, 300, 0);
            if (enemy instanceof Player) KitUtil.effect(enemy, MobEffects.BLINDNESS, 120, 0);
            hit++;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1.5f, 0.7f);
        KitUtil.actionBar(player, "Night Swarm! " + hit + (hit == 1 ? " enemy" : " enemies") + " marked.");
        return true;
    }
}
