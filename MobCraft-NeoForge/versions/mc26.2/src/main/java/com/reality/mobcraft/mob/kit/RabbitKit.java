package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rabbit (Meowwbu).
 *
 *   Passive     Speed II, Jump Boost III, half size, instant respawn
 *   Escorts     R, or left-click while holding a rabbit's foot: up to 5 killer
 *               rabbits, 15.5s cooldown, 25 alive at once. They follow, defend
 *               and attack per the Mob Behavior settings, and take RTS orders.
 *   Home        Desert, Snowy Plains, Snowy Taiga: escorts get Speed I and half
 *               of Strength I ("The Rabbit King Must Protect"). Killer rabbits
 *               never flee ("Keep Fighting for the King").
 *   Double jump press jump in midair to jump again, 3 energy each. Up to 3
 *               midair jumps before you land.
 *   No fall damage
 *   Curse       only one midair jump before you land, and can't self-give raw
 *               rabbit. Raw rabbit already held stays.
 */
public final class RabbitKit implements MobKit {

    private static final String ESCORTS = "summon_escorts";
    private static final int ESCORT_COOLDOWN = 310;   // 15.5 seconds
    private static final int ESCORTS_PER_SUMMON = 5;
    private static final int MAX_ESCORTS = 25;
    private static final double ESCORT_RANGE = 48;

    @Override
    public void tick(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.SCALE, "rabbit_scale", -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        KitUtil.passive(player, MobEffects.SPEED, 1, true);
        KitUtil.passive(player, MobEffects.JUMP_BOOST, 2, true);
        if (player.onGround()) AIR_JUMPS.remove(player.getUUID());

        // Home buff for escorts, checked once a second.
        if (player.tickCount % 20 == 0) {
            boolean home = KitUtil.inBiome(player, Biomes.DESERT, Biomes.SNOWY_PLAINS, Biomes.SNOWY_TAIGA);
            for (Mob escort : escorts(player)) {
                KitUtil.passive(escort, MobEffects.SPEED, 0, home);
                // Strength I is +3 attack damage, so half of it is +1.5.
                KitUtil.modifier(escort, Attributes.ATTACK_DAMAGE, "rabbit_king", 1.5, AttributeModifier.Operation.ADD_VALUE, home);
            }
        }
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.modifier(player, Attributes.SCALE, "rabbit_scale", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        KitUtil.passive(player, MobEffects.SPEED, 1, false);
        KitUtil.passive(player, MobEffects.JUMP_BOOST, 2, false);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot != ABILITY_1) {
            KitUtil.actionBar(player, "The Rabbit only uses the first ability key.");
            return;
        }
        if (!player.getMainHandItem().is(Items.RABBIT_FOOT)) {
            KitUtil.actionBar(player, "Hold a rabbit's foot to call your escorts.");
            return;
        }
        int alive = escorts(player).size();
        int room = Math.min(ESCORTS_PER_SUMMON, MAX_ESCORTS - alive);
        if (room <= 0) {
            KitUtil.actionBar(player, "You already have " + MAX_ESCORTS + " escorts.");
            return;
        }
        if (!KitUtil.tryUse(player, ESCORTS, "Escorts", 0, ESCORT_COOLDOWN)) return;

        for (int i = 0; i < room; i++) {
            double angle = (Math.PI * 2 / room) * i;
            double x = player.getX() + Math.cos(angle) * 1.5;
            double z = player.getZ() + Math.sin(angle) * 1.5;
            // RabbitType 99 is the killer bunny. "variant" covers newer saves.
            KitUtil.summon(player, "minecraft:rabbit", x, player.getY() + 0.2, z, "RabbitType:99,variant:\"evil\"");
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.RABBIT_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.8f);
        KitUtil.actionBar(player, "Called " + room + " escorts.");
    }

    // --- Double jump ---------------------------------------------------------------------

    private static final float JUMP_COST = 3;
    private static final double JUMP_VELOCITY = 0.6;
    private static final int MAX_AIR_JUMPS = 3;
    /** Midair jumps since the player last touched the ground. */
    private static final Map<UUID, Integer> AIR_JUMPS = new HashMap<>();

    /** Called from the double jump packet. */
    public static void doubleJump(ServerPlayer player) {
        if (!PlayerMob.has(player, MobType.RABBIT)) return;
        if (player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying) return;
        int jumps = AIR_JUMPS.getOrDefault(player.getUUID(), 0);
        if (PlayerMob.isCursed(player) && jumps >= 1) {
            KitUtil.actionBar(player, "Cursed: only one midair jump.");
            return;
        }
        if (jumps >= MAX_AIR_JUMPS) {
            KitUtil.actionBar(player, "Out of midair jumps. Land to jump again.");
            return;
        }
        if (!PlayerMob.spend(player, JUMP_COST)) return;
        AIR_JUMPS.put(player.getUUID(), jumps + 1);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, JUMP_VELOCITY, motion.z);
        player.resetFallDistance();
        KitUtil.syncMotion(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        return !source.is(DamageTypes.FALL);
    }

    @Override
    public boolean canSelfGive(ServerPlayer player, String itemId) {
        return !(PlayerMob.isCursed(player) && itemId.equals("rabbit"));
    }

    @Override
    public boolean instantRespawn() {
        return true;
    }

    private static List<Mob> escorts(ServerPlayer player) {
        return KitUtil.ownedMobs(player, ESCORT_RANGE, mob -> mob.getType() == EntityTypes.RABBIT);
    }

    static void forget(ServerPlayer player) {
        AIR_JUMPS.remove(player.getUUID());
    }

    // --- Ultimate: Killer Stampede ---------------------------------------------------------------
    /**
     * Up to 10 killer rabbits at once, no rabbit's foot needed, still 25 at
     * most. Every escort within 32 blocks gets Strength I and Speed II for 20s.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Killer Stampede";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        int room = Math.min(10, MAX_ESCORTS - escorts(player).size());
        for (int i = 0; i < room; i++) {
            double angle = (Math.PI * 2 / room) * i;
            KitUtil.summon(player, "minecraft:rabbit", player.getX() + Math.cos(angle) * 2, player.getY() + 0.2,
                    player.getZ() + Math.sin(angle) * 2, "RabbitType:99,variant:\"evil\"");
        }
        int buffed = 0;
        for (Mob escort : KitUtil.ownedMobs(player, 32, mob -> mob.getType() == EntityTypes.RABBIT)) {
            KitUtil.effect(escort, MobEffects.STRENGTH, 400, 0);
            KitUtil.effect(escort, MobEffects.SPEED, 400, 1);
            buffed++;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.RABBIT_ATTACK, SoundSource.PLAYERS, 1.5f, 0.6f);
        KitUtil.actionBar(player, "Killer Stampede! " + buffed + " escorts charge.");
        return true;
    }
}
