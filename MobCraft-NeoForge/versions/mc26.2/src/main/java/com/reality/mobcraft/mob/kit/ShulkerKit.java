package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobLook;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Shulker.
 *
 *   Shell defense   stand still for 2s: Resistance I (II in the End) and
 *                   projectiles bounce off you back at whoever shot them
 *   R  Blocking     your shell appears and snaps shut around you for 5s. You
 *                   stay put inside it, lined up with the block you're on: Resistance II and Blindness
 *                   (no Blindness in the End), then Slowness I for 3s.
 *                   10 energy, 15s cooldown.
 *   G  Teleport     to a random safe spot within 8 blocks. 5s cooldown.
 *   V  Shulker Bullet  at whatever you're looking at within 32 blocks.
 *                   5 energy, 2s cooldown. Turns off shell defense for 5s.
 *   Sneak+V  Elytra Gamble  1 in 50 chance at an Elytra. 50 energy a roll.
 *   RTS Teleport    in RTS mode with nothing selected, click a shulker to
 *                   teleport next to it, or click anywhere in an End City to
 *                   teleport there. 8 energy, no range limit.
 *   Allies          endermen and shulkers
 *   Drops           Elytra too, if killed with a Looting weapon
 */
public final class ShulkerKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.ENDERMAN, EntityTypes.SHULKER);

    /** The scale that makes a 1.8-block player exactly 1 block tall. */
    private static final double ONE_BLOCK = 1.0 / 1.8;

    private static final String BLOCKING = "blocking";
    private static final String TELEPORT = "teleport";
    private static final String BULLET = "shulker_bullet";
    private static final String GAMBLE = "elytra_gamble";
    private static final String CITY_TELEPORT = "end_city_teleport";
    private static final int STILL_TICKS = 40;
    private static final double BULLET_RANGE = 32;

    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Integer> STILL_FOR = new HashMap<>();
    private static final KitUtil.Timers BLOCKING_TIME = new KitUtil.Timers();
    private static final KitUtil.Flags WAS_BLOCKING = new KitUtil.Flags();
    private static final KitUtil.Timers DEFENSE_OFF = new KitUtil.Timers();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        // One block tall, like a real shulker: 1.8 blocks scaled by 1/1.8.
        KitUtil.modifier(player, Attributes.SCALE, "shulker_scale", ONE_BLOCK - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        boolean inEnd = player.level().dimension() == Level.END;

        // How long you've stood still.
        Vec3 pos = player.position();
        Vec3 last = LAST_POS.put(id, pos);
        boolean moved = last == null || last.distanceToSqr(pos) > 0.0025;
        STILL_FOR.put(id, moved ? 0 : STILL_FOR.getOrDefault(id, 0) + 1);

        boolean blocking = BLOCKING_TIME.active(player);
        // Shut in the shell: no walking, jumping or being knocked about.
        KitUtil.modifier(player, Attributes.MOVEMENT_SPEED, "shulker_shell_still", -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, blocking);
        KitUtil.modifier(player, Attributes.JUMP_STRENGTH, "shulker_shell_still", -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, blocking);
        KitUtil.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "shulker_shell_still", 1.0, AttributeModifier.Operation.ADD_VALUE, blocking);
        if (WAS_BLOCKING.is(player) && !blocking) {
            KitUtil.effect(player, MobEffects.SLOWNESS, 60, 0); // shell opening
        }
        WAS_BLOCKING.set(player, blocking);
        MobLook.set(player, MobLook.SHELL, blocking);

        int resistance = -1;
        if (defending(player)) resistance = inEnd ? 1 : 0;
        if (blocking) resistance = 1;
        KitUtil.passive(player, MobEffects.RESISTANCE, Math.max(resistance, 0), resistance >= 0);

        KitUtil.calmAllies(player, 32, ALLIES);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.SCALE, "shulker_scale", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        KitUtil.passive(player, MobEffects.RESISTANCE, 0, false);
        KitUtil.modifier(player, Attributes.MOVEMENT_SPEED, "shulker_shell_still", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        KitUtil.modifier(player, Attributes.JUMP_STRENGTH, "shulker_shell_still", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        KitUtil.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "shulker_shell_still", 0, AttributeModifier.Operation.ADD_VALUE, false);
        MobLook.clear(player);
        forget(player);
    }

    /** Shell defense: still for 2s and not switched off by a bullet. */
    private static boolean defending(ServerPlayer player) {
        return STILL_FOR.getOrDefault(player.getUUID(), 0) >= STILL_TICKS && !DEFENSE_OFF.active(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        switch (slot) {
            case ABILITY_1 -> block(player);
            case ABILITY_2 -> teleport(player);
            default -> {
                if (sneaking) elytraGamble(player); else bullet(player);
            }
        }
    }

    // --- Abilities ----------------------------------------------------------------------

    private static void block(ServerPlayer player) {
        if (!KitUtil.tryUse(player, BLOCKING, "Blocking", 10, 300)) return;
        BLOCKING_TIME.start(player, 100);
        // Settle into the middle of the block, so the shell sits on the block like a real shulker.
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        player.teleportTo(Math.floor(player.getX()) + 0.5, player.getY(), Math.floor(player.getZ()) + 0.5);
        KitUtil.syncMotion(player);
        if (player.level().dimension() != Level.END) KitUtil.effect(player, MobEffects.BLINDNESS, 100, 0);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHULKER_CLOSE, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void teleport(ServerPlayer player) {
        BlockPos spot = KitUtil.randomSafeSpot(player, 8);
        if (spot == null) {
            KitUtil.actionBar(player, "No safe spot nearby.");
            return;
        }
        if (!KitUtil.tryUse(player, TELEPORT, "Teleport", 0, 100)) return;
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        player.resetFallDistance();
        level.playSound(null, spot, SoundEvents.SHULKER_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void bullet(ServerPlayer player) {
        LivingEntity target = targetAhead(player);
        if (target == null) {
            KitUtil.actionBar(player, "Look at something to shoot.");
            return;
        }
        if (!KitUtil.tryUse(player, BULLET, "Shulker Bullet", 5, 40)) return;
        DEFENSE_OFF.start(player, 100);
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition().add(player.getLookAngle());
        KitUtil.command(level, "summon minecraft:shulker_bullet %.3f %.3f %.3f {Owner:%s,Target:%s}",
                eye.x, eye.y, eye.z, KitUtil.uuidNbt(player.getUUID()), KitUtil.uuidNbt(target.getUUID()));
        level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void elytraGamble(ServerPlayer player) {
        if (!KitUtil.tryUse(player, GAMBLE, "Elytra Gamble", 50, 0)) return;
        if (player.getRandom().nextInt(50) == 0) {
            ItemStack elytra = new ItemStack(Items.ELYTRA, 1);
            if (!player.getInventory().add(elytra)) player.level().addFreshEntity(
                    new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), elytra));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
            KitUtil.actionBar(player, "You won an Elytra!");
        } else {
            KitUtil.actionBar(player, "No luck this time.");
        }
    }

    /**
     * RTS teleport, from the RTS click packet. A clicked vanilla shulker takes
     * you next to it. Otherwise the clicked spot has to be inside an End City.
     */
    public static void rtsTeleport(ServerPlayer player, double x, double z, int shulkerId) {
        if (!PlayerMob.has(player, MobType.SHULKER)) return;
        if (!MobAbilities.isEnabled(player, CITY_TELEPORT)) {
            KitUtil.actionBar(player, "RTS Teleport is switched off in /mobcraftsettings.");
            return;
        }
        if (shulkerId >= 0) {
            teleportToShulker(player, shulkerId);
            return;
        }
        ServerLevel level = player.level();
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        BlockPos feet = new BlockPos(bx, y, bz);
        BlockPos floor = feet.below();
        boolean inCity = level.structureManager().getStructureWithPieceAt(floor,
                (Holder<Structure> holder) -> holder.is(BuiltinStructures.END_CITY)).isValid();
        if (!inCity) {
            KitUtil.actionBar(player, "You can only teleport inside an End City.");
            return;
        }
        if (!KitUtil.standable(level, feet)) {
            KitUtil.actionBar(player, "That spot isn't safe.");
            return;
        }
        if (!KitUtil.tryUse(player, CITY_TELEPORT, "End City Teleport", 8, 0)) return;
        player.teleportTo(bx + 0.5, y, bz + 0.5);
        player.resetFallDistance();
        level.playSound(null, feet, SoundEvents.SHULKER_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void teleportToShulker(ServerPlayer player, int shulkerId) {
        ServerLevel level = player.level();
        net.minecraft.world.entity.Entity shulker = level.getEntity(shulkerId);
        if (shulker == null || shulker.getType() != EntityTypes.SHULKER || !shulker.isAlive()) {
            KitUtil.actionBar(player, "That shulker is gone.");
            return;
        }
        BlockPos spot = null;
        BlockPos base = shulker.blockPosition();
        search:
        for (int r = 1; r <= 3; r++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos feet = base.offset(dx, dy, dz);
                        if (KitUtil.standable(level, feet)) {
                            spot = feet;
                            break search;
                        }
                    }
                }
            }
        }
        if (spot == null) {
            KitUtil.actionBar(player, "There's nowhere safe to stand by that shulker.");
            return;
        }
        if (!KitUtil.tryUse(player, CITY_TELEPORT, "RTS Teleport", 8, 0)) return;
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        player.resetFallDistance();
        level.playSound(null, spot, SoundEvents.SHULKER_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** The closest living thing roughly where the player is looking. */
    private static LivingEntity targetAhead(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB area = player.getBoundingBox().inflate(BULLET_RANGE);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            double dot = to.normalize().dot(look);
            if (distance > BULLET_RANGE || dot < 0.95) continue;
            double score = distance * (2.0 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = entity;
            }
        }
        return best;
    }

    // --- Shell defense: bounce projectiles back ----------------------------------------

    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        if (!defending(player) || !(source.getDirectEntity() instanceof Projectile projectile)) return true;
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.0));
        projectile.setOwner(player);
        KitUtil.syncMotion(projectile);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHULKER_HURT_CLOSED, SoundSource.PLAYERS, 1.0f, 1.0f);
        return false;
    }

    // --- Death: Elytra with Looting ------------------------------------------------------

    @Override
    public void onDeath(ServerPlayer player, DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity killer)) return;
        Holder<Enchantment> looting = KitUtil.enchantment(player.level(), Enchantments.LOOTING);
        if (EnchantmentHelper.getItemEnchantmentLevel(looting, killer.getMainHandItem()) <= 0) return;
        player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(),
                new ItemStack(Items.ELYTRA, 1)));
    }

    static void forget(ServerPlayer player) {
        UUID id = player.getUUID();
        LAST_POS.remove(id);
        STILL_FOR.remove(id);
        BLOCKING_TIME.forget(id);
        WAS_BLOCKING.forget(player);
        DEFENSE_OFF.forget(id);
    }

    // --- Ultimate: Bullet Storm ---------------------------------------------------------------
    /**
     * Shulker bullets at up to 10 enemies within 24 blocks, more bullets each
     * when there are fewer targets (3 at most). Resistance II for 5s.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Bullet Storm";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        List<LivingEntity> targets = new ArrayList<>(UltimateFx.enemies(player, 24));
        if (targets.isEmpty()) {
            KitUtil.actionBar(player, "No enemies within 24 blocks.");
            return false;
        }
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        if (targets.size() > 10) targets = new ArrayList<>(targets.subList(0, 10));
        int each = Math.max(1, Math.min(3, 10 / targets.size()));
        ServerLevel level = player.level();
        String owner = KitUtil.uuidNbt(player.getUUID());
        int n = 0;
        for (LivingEntity target : targets) {
            for (int i = 0; i < each; i++) {
                Vec3 d = UltimateFx.around(n++, 12, 0);
                Vec3 start = player.getEyePosition().add(d.scale(1.2));
                KitUtil.command(level, "summon minecraft:shulker_bullet %.3f %.3f %.3f {Owner:%s,Target:%s}",
                        start.x, start.y, start.z, owner, KitUtil.uuidNbt(target.getUUID()));
            }
        }
        KitUtil.effect(player, MobEffects.RESISTANCE, 100, 1);
        level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 2.0f, 0.7f);
        KitUtil.actionBar(player, "Bullet Storm! " + targets.size() + (targets.size() == 1 ? " target." : " targets."));
        return true;
    }
}
