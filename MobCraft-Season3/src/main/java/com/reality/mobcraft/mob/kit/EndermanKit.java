package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.item.ArmorUpgrades;
import com.reality.mobcraft.item.ModItems;
import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/**
 * Enderman. Draft numbers, waiting on Reality's sheet.
 *
 *   R  Teleport     to the block you're looking at, up to 32 blocks away.
 *                   15 energy, 3s cooldown.
 *   Auto Dodge      passive: a projectile that would hit you teleports you up
 *                   to 8 blocks away instead. 5 energy each time. Switch off in
 *                   /mobcraftsettings.
 *   Water           water and rain hurt you, half a heart a second
 *   Ender pearls    no pearl damage
 *   Allies          endermen and endermites
 *   Curse           no Teleport and no Auto Dodge
 */
public final class EndermanKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.ENDERMAN, EntityTypes.ENDERMITE);

    private static final String TELEPORT = "teleport";
    private static final String AUTO_DODGE = "auto_dodge";
    private static final double TELEPORT_RANGE = 32;
    private static final float DODGE_COST = 5;

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        if (player.isInWaterOrRain() && player.tickCount % 20 == 0) {
            player.hurtServer(player.level(), player.damageSources().drown(), 1.0f);
        }
        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot != ABILITY_1) {
            KitUtil.actionBar(player, "Auto Dodge is passive. Switch it in /mobcraftsettings.");
            return;
        }
        if (PlayerMob.isCursed(player)) {
            KitUtil.actionBar(player, "You can't teleport while cursed.");
            return;
        }
        BlockPos target = lookTarget(player);
        if (target == null) {
            KitUtil.actionBar(player, "Look at a block you can stand on.");
            return;
        }
        if (!KitUtil.tryUse(player, TELEPORT, "Teleport", 15, 60)) return;
        teleportTo(player, target);
    }

    /** Where you'd stand on the block you're looking at, or null. */
    private static BlockPos lookTarget(ServerPlayer player) {
        HitResult hit = player.pick(TELEPORT_RANGE, 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        ServerLevel level = player.level();
        BlockPos start = blockHit.getBlockPos().relative(blockHit.getDirection());
        // Drop down to the floor below the face you hit, or step up onto the block.
        for (int dy = 0; dy >= -4; dy--) {
            BlockPos feet = start.offset(0, dy, 0);
            if (KitUtil.standable(level, feet)) return feet;
        }
        BlockPos onTop = blockHit.getBlockPos().above();
        return KitUtil.standable(level, onTop) ? onTop : null;
    }

    private static void teleportTo(ServerPlayer player, BlockPos feet) {
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        player.resetFallDistance();
        level.playSound(null, feet, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        if (source.is(DamageTypes.ENDER_PEARL)) return false;

        // Auto Dodge.
        if (source.getDirectEntity() instanceof Projectile
                && !PlayerMob.isCursed(player)
                && MobAbilities.isEnabled(player, AUTO_DODGE)
                && PlayerMob.energy(player) >= DODGE_COST) {
            BlockPos spot = KitUtil.randomSafeSpot(player, 8);
            if (spot != null) {
                PlayerMob.spend(player, DODGE_COST);
                teleportTo(player, spot);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDeath(ServerPlayer player, DamageSource source) {
        ArmorUpgrades.dropAt(player, ModItems.ENDER_PEARL_SHARD);
    }

    static void forget(ServerPlayer player) {}

    // --- Ultimate: Void Rift ---------------------------------------------------------------
    /**
     * Every enemy within 16 blocks is teleported to a random spot 8 to 16
     * blocks from where they stood, with Nausea for 8s. Players are blinded
     * for 3s too.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Void Rift";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        ServerLevel level = player.level();
        RandomSource random = player.getRandom();
        int moved = 0;
        for (LivingEntity enemy : UltimateFx.enemies(player, 16)) {
            BlockPos spot = null;
            for (int tries = 0; tries < 10 && spot == null; tries++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 8 + random.nextDouble() * 8;
                double x = enemy.getX() + Math.cos(angle) * distance;
                double z = enemy.getZ() + Math.sin(angle) * distance;
                double y = KitUtil.groundY(level, x, enemy.getY() + 8, z);
                if (Double.isNaN(y)) continue;
                BlockPos feet = BlockPos.containing(x, y, z);
                if (KitUtil.standable(level, feet)) spot = feet;
            }
            if (spot == null) continue;
            level.sendParticles(ParticleTypes.PORTAL, enemy.getX(), enemy.getY() + 1, enemy.getZ(), 40, 0.4, 0.8, 0.4, 0.4);
            enemy.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            enemy.resetFallDistance();
            KitUtil.syncMotion(enemy);
            KitUtil.effect(enemy, MobEffects.NAUSEA, 160, 0);
            if (enemy instanceof Player) KitUtil.effect(enemy, MobEffects.BLINDNESS, 60, 0);
            level.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.8f);
            moved++;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 1.5f, 0.8f);
        KitUtil.actionBar(player, "Void Rift! " + moved + (moved == 1 ? " enemy" : " enemies") + " scattered.");
        return true;
    }
}
