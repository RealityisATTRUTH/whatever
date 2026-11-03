package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Iron Golem (Evan).
 *
 *   Passive       Resistance I, +3 hearts attack damage, 1.4 size, knockback
 *                 immunity, no fall damage, Slowness I. Mace smash does half damage.
 *   Golem Toss    R: your next empty-hand hit launches the target upward.
 *                 A tossed player is stunned for 2s: no moving, jumping or attacking.
 *                 15 energy, 8s cooldown.
 *   Iron Repair   right-click an iron ingot: heal 4 hearts, 10s cooldown.
 *                 Teammates can right-click you with an ingot to repair you.
 *   Allies        villagers and iron golems
 *   Home          Villages: Resistance II and Regeneration I
 *   Curse Rusted  loses Resistance and knockback immunity, Slowness II
 */
public final class IronGolemKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.IRON_GOLEM, EntityTypes.VILLAGER);

    private static final String TOSS = "golem_toss";
    private static final String REPAIR = "iron_repair";
    private static final float REPAIR_HEAL = 8.0f;  // 4 hearts
    private static final int REPAIR_COOLDOWN = 200;

    private static final int TOSS_STUN_TICKS = 40; // 2 seconds
    private static final Set<UUID> TOSS_PRIMED = new HashSet<>();
    private static final KitUtil.Flags AT_HOME = new KitUtil.Flags();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        boolean cursed = PlayerMob.isCursed(player);
        if (player.tickCount % 20 == 0) AT_HOME.set(player, KitUtil.inStructure(player, StructureTags.VILLAGE));
        boolean home = !cursed && AT_HOME.is(player);

        KitUtil.modifier(player, Attributes.SCALE, "golem_scale", 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        KitUtil.modifier(player, Attributes.ATTACK_DAMAGE, "golem_damage", 6.0, AttributeModifier.Operation.ADD_VALUE, true);
        KitUtil.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "golem_knockback", 1.0, AttributeModifier.Operation.ADD_VALUE, !cursed);
        KitUtil.modifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "golem_blast_knockback", 1.0, AttributeModifier.Operation.ADD_VALUE, !cursed);

        KitUtil.passive(player, MobEffects.RESISTANCE, home ? 1 : 0, !cursed);
        KitUtil.passive(player, MobEffects.REGENERATION, 0, home);
        KitUtil.passive(player, MobEffects.SLOWNESS, cursed ? 1 : 0, true);

        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.SCALE, "golem_scale", 0, AttributeModifier.Operation.ADD_VALUE, false);
        KitUtil.modifier(player, Attributes.ATTACK_DAMAGE, "golem_damage", 0, AttributeModifier.Operation.ADD_VALUE, false);
        KitUtil.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "golem_knockback", 0, AttributeModifier.Operation.ADD_VALUE, false);
        KitUtil.modifier(player, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "golem_blast_knockback", 0, AttributeModifier.Operation.ADD_VALUE, false);
        KitUtil.passive(player, MobEffects.RESISTANCE, 0, false);
        KitUtil.passive(player, MobEffects.REGENERATION, 0, false);
        KitUtil.passive(player, MobEffects.SLOWNESS, 0, false);
        forget(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot != ABILITY_1) {
            KitUtil.actionBar(player, "Iron Repair is right-click with an iron ingot.");
            return;
        }
        if (TOSS_PRIMED.contains(player.getUUID())) {
            KitUtil.actionBar(player, "Golem Toss is already ready. Hit something with an empty hand.");
            return;
        }
        if (!KitUtil.tryUse(player, TOSS, "Golem Toss", 15, 160)) return;
        TOSS_PRIMED.add(player.getUUID());
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 0.8f, 0.8f);
        KitUtil.actionBar(player, "Golem Toss ready. Hit something with an empty hand.");
    }

    @Override
    public void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        if (source.getDirectEntity() != player || !player.getMainHandItem().isEmpty()) return;
        if (!TOSS_PRIMED.remove(player.getUUID())) return;
        Vec3 motion = victim.getDeltaMovement();
        victim.setDeltaMovement(motion.x, 1.2, motion.z);
        KitUtil.syncMotion(victim);
        player.level().playSound(null, victim.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0f, 1.0f);
        // A tossed player is stunned for 2 seconds: no moving, jumping or attacking while they fly.
        if (victim instanceof ServerPlayer tossed) {
            Confusion.apply(tossed, TOSS_STUN_TICKS, KitUtil.now(player));
            KitUtil.actionBar(tossed, "Tossed! You're stunned for 2 seconds.");
        }
    }

    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        return !source.is(DamageTypes.FALL);
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayer player, LivingEntity victim, DamageSource source, float amount) {
        boolean maceHit = source.getDirectEntity() == player && player.getMainHandItem().is(Items.MACE);
        return maceHit ? amount * 0.5f : amount;
    }

    @Override
    public InteractionResult onUseItem(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.IRON_INGOT)) return InteractionResult.PASS;
        if (player.getHealth() >= player.getMaxHealth()) {
            KitUtil.actionBar(player, "You're already fully repaired.");
            return InteractionResult.FAIL;
        }
        if (!KitUtil.tryUse(player, REPAIR, "Iron Repair", 0, REPAIR_COOLDOWN)) return InteractionResult.FAIL;
        repair(player, held);
        return InteractionResult.SUCCESS;
    }

    /** A teammate right-clicks the golem holding an iron ingot. Shares the golem's cooldown. */
    static InteractionResult repairByTeammate(ServerPlayer helper, ServerPlayer golem, InteractionHand hand) {
        ItemStack held = helper.getItemInHand(hand);
        if (!held.is(Items.IRON_INGOT)) return InteractionResult.PASS;
        if (!helper.isAlliedTo(golem)) {
            KitUtil.actionBar(helper, "Only teammates can repair this golem.");
            return InteractionResult.FAIL;
        }
        if (!MobAbilities.isEnabled(golem, REPAIR) || golem.getHealth() >= golem.getMaxHealth()) return InteractionResult.PASS;
        int left = Cooldowns.remaining(golem, REPAIR);
        if (left > 0) {
            KitUtil.actionBar(helper, "Iron Repair ready in " + KitUtil.seconds(left) + ".");
            return InteractionResult.FAIL;
        }
        Cooldowns.start(golem, REPAIR, REPAIR_COOLDOWN);
        repair(golem, held);
        return InteractionResult.SUCCESS;
    }

    private static void repair(ServerPlayer golem, ItemStack ingot) {
        ingot.shrink(1);
        golem.heal(REPAIR_HEAL);
        golem.level().playSound(null, golem.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    static void forget(ServerPlayer player) {
        TOSS_PRIMED.remove(player.getUUID());
        AT_HOME.forget(player);
    }

    // --- Ultimate: Earthquake ---------------------------------------------------------------
    /**
     * You slam the ground. Enemies within 7 blocks take 4 hearts and are
     * thrown up. Players are stunned for 3s, mobs slowed to a crawl.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Earthquake";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        ServerLevel level = player.level();
        int now = KitUtil.now(player);
        int hit = 0;
        for (LivingEntity enemy : UltimateFx.enemies(player, 7)) {
            enemy.hurtServer(level, player.damageSources().mobAttack(player), 8.0f);
            UltimateFx.throwFrom(enemy, player.position(), 0.4, 1.1);
            if (enemy instanceof ServerPlayer stunned) {
                Confusion.apply(stunned, 60, now);
                KitUtil.actionBar(stunned, "Earthquake! You're stunned for 3 seconds.");
            } else {
                KitUtil.effect(enemy, MobEffects.SLOWNESS, 60, 9);
            }
            hit++;
        }
        for (int i = 0; i < 12; i++) {
            Vec3 d = UltimateFx.around(i, 12, 0);
            level.sendParticles(ParticleTypes.EXPLOSION, player.getX() + d.x * 4, player.getY() + 0.2, player.getZ() + d.z * 4, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 2.0f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 2.0f, 0.6f);
        KitUtil.actionBar(player, "Earthquake! Hit " + hit + (hit == 1 ? " enemy." : " enemies."));
        return true;
    }
}
