package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.item.ArmorUpgrades;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobKits;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.rts.MobBehavior;
import com.reality.mobcraft.rts.MobOwnership;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;

/**
 * Connects Fabric's events to the kits. A kit never registers events itself,
 * it just overrides the MobKit hooks.
 */
public final class KitEvents {

    private KitEvents() {}

    /** The kit of a player with a mob, or null for anything else. */
    public static MobKit kitOf(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) return null;
        MobType type = PlayerMob.get(player);
        return type == null ? null : MobKits.get(type);
    }

    public static void register() {
        // Before damage: kits can cancel hits they make or take.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            // A Breeze Core on your boots: no fall damage.
            if (com.reality.mobcraft.item.ArmorUpgrades.blocksDamage(victim, source)) return false;
            // Mobs you own can never hurt you, even for a tick before they're calmed.
            if (victim instanceof ServerPlayer owner && source.getEntity() != null
                    && MobOwnership.isOwnedBy(source.getEntity(), owner)) return false;
            // A phasing Vex can't attack.
            if (source.getEntity() instanceof ServerPlayer phaser && VexKit.isPhasing(phaser)) return false;
            // Players stunned by the Bat's Confuse can't attack.
            if (source.getEntity() instanceof ServerPlayer attacker && Confusion.isConfused(attacker)) return false;
            MobKit attackerKit = kitOf(source.getEntity());
            if (attackerKit != null && !attackerKit.allowAttack((ServerPlayer) source.getEntity(), victim, source)) {
                return false;
            }
            MobKit victimKit = kitOf(victim);
            return victimKit == null || victimKit.allowHurt((ServerPlayer) victim, source, amount);
        });

        // After damage: escorts join in, the golem's toss, hunting partner tracking.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damage, blocked) -> {
            if (blocked) return;
            if (source.getEntity() instanceof ServerPlayer attacker) CombatTag.mark(attacker);
            if (victim instanceof ServerPlayer hurt && source.getEntity() != null) CombatTag.mark(hurt);
            MobKit attackerKit = kitOf(source.getEntity());
            if (attackerKit != null) attackerKit.afterAttack((ServerPlayer) source.getEntity(), victim, source, damage);
            MobKit victimKit = kitOf(victim);
            if (victimKit != null) victimKit.afterHurt((ServerPlayer) victim, source, damage);

            // Mob Behavior: your mobs join your fights.
            if (source.getEntity() instanceof ServerPlayer attacker) MobBehavior.ownerAttacked(attacker, victim);
            if (victim instanceof ServerPlayer owner && source.getEntity() instanceof LivingEntity attacker) {
                MobBehavior.ownerHurt(owner, attacker);
            }
        });

        // Mob Behavior: owned mobs follow, about once a second.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int now = server.getTickCount();
            if (now % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) MobBehavior.tickFollow(player, now);
        });

        // Timers that run for everyone, not just players with a mob.
        ServerTickEvents.END_SERVER_TICK.register(Confusion::tick);
        ServerTickEvents.END_SERVER_TICK.register(SkeletonKit::tickHorsemen);

        // Projectiles: the Skeleton's biome arrows and Lock On.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof Projectile projectile)) return;
            MobKit kit = kitOf(projectile.getOwner());
            if (kit != null && projectile.tickCount == 0) kit.onProjectileFired((ServerPlayer) projectile.getOwner(), projectile);
        });

        // Right-click with an item.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            MobKit kit = kitOf(player);
            return kit == null ? InteractionResult.PASS : kit.onUseItem((ServerPlayer) player, hand);
        });

        // Right-click on a block, like opening a chest.
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            MobKit kit = kitOf(player);
            return kit == null ? InteractionResult.PASS : kit.onUseBlock((ServerPlayer) player, hand);
        });

        // Right-clicking another player: riding a Ghast, repairing an Iron Golem.
        UseEntityCallback.EVENT.register((player, level, hand, target, hitResult) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer helper) || !(target instanceof ServerPlayer other)) return InteractionResult.PASS;
            if (PlayerMob.has(other, MobType.GHAST)) return GhastKit.board(helper, other, hand);
            if (!PlayerMob.has(other, MobType.IRON_GOLEM)) return InteractionResult.PASS;
            return IronGolemKit.repairByTeammate(helper, other, hand);
        });

        // Hunting Partner: a teammate finishes off what the Axolotl hurt.
        ServerLivingEntityEvents.AFTER_DEATH.register(AxolotlKit::onDeath);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            MobKit kit = kitOf(entity);
            if (kit != null && DeathOnce.first((ServerPlayer) entity, "kit")) kit.onDeath((ServerPlayer) entity, source);
        });

        // Left-clicking a block: the Ender Dragon breaks end portal frames.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer dragon)) return InteractionResult.PASS;
            if (!PlayerMob.has(dragon, MobType.ENDER_DRAGON)) return InteractionResult.PASS;
            if (!level.getBlockState(pos).is(Blocks.END_PORTAL_FRAME)) return InteractionResult.PASS;
            return EnderDragonKit.breakFrame(dragon, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });

        EndRules.register();

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
    }

    /** Called from the damage mixin with the raw damage amount. */
    public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
        MobKit attackerKit = kitOf(source.getEntity());
        if (attackerKit != null) {
            amount = attackerKit.modifyOutgoingDamage((ServerPlayer) source.getEntity(), victim, source, amount);
        }
        MobKit victimKit = kitOf(victim);
        if (victimKit != null) {
            amount = victimKit.modifyIncomingDamage((ServerPlayer) victim, source, amount);
        }
        amount = com.reality.mobcraft.item.GloamiteTraits.modifyDamage(victim, source, amount);
        amount = com.reality.mobcraft.item.TrollTiers.modifyDamage(victim, source, amount);
        amount = com.reality.mobcraft.item.GearEnchants.modifyDamage(victim, source, amount);
        return com.reality.mobcraft.item.ArmorUpgrades.modifyIncoming(victim, source, amount);
    }

    /** Called from the undead mixin. */
    public static boolean isUndeadPlayer(LivingEntity entity) {
        MobKit kit = kitOf(entity);
        return kit != null && kit.isUndead();
    }

    /** Runs every tick for every player with a mob, before their kit's tick. */
    public static void tickAll(ServerPlayer player) {
        if (player.isAlive()) DeathOnce.reset(player);
        // Mobs you own never turn on you.
        KitUtil.calmAllies(player, 32, mob -> MobOwnership.isOwnedBy(mob, player));
    }

    private static void forget(ServerPlayer player) {
        RabbitKit.forget(player);
        SkeletonKit.forget(player);
        AxolotlKit.forget(player);
        IronGolemKit.forget(player);
        PiglinKit.forget(player);
        BatKit.forget(player);
        GhastKit.forget(player);
        EvokerKit.forget(player);
        WardenKit.forget(player);
        VexKit.forget(player);
        ShulkerKit.forget(player);
        CreeperKit.forget(player);
        SilverfishKit.forget(player);
        EndermanKit.forget(player);
        WitherKit.forget(player);
        IllusionerKit.forget(player);
        BlazeKit.forget(player);
        EnderDragonKit.forget(player);
        ElderGuardianKit.forget(player);
        BreezeKit.forget(player);
        CombatTag.forget(player);
        ArmorUpgrades.forget(player);
    }
}
