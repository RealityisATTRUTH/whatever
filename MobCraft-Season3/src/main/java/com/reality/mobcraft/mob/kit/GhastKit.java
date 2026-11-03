package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobTicker;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ghast (Bench).
 *
 *   Passive     slow flight, Fire Resistance, Regeneration I only in the Nether
 *   Fireball    R: one ghast fireball. 15 energy, 3s cooldown. Can be punched back.
 *   Barrage     G: three fireballs in a spread. 45 energy, 30s cooldown.
 *   Ferry       up to 4 teammates (same /team) can right-click you to ride
 *               along beside you. Sneak to get off.
 *   Allies      ghasts and happy ghasts
 *   Home        Nether Wastes and Soul Sand Valley: faster flight, double energy regen
 *   Curse       Dried Out: can't fly or shoot. Standing in water ends it early.
 */
public final class GhastKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.GHAST, EntityTypes.HAPPY_GHAST);

    private static final String FIREBALL = "fireball";
    private static final String BARRAGE = "barrage";
    private static final float FLY_SPEED = 0.02f;
    private static final float HOME_FLY_SPEED = 0.035f;

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        boolean cursed = PlayerMob.isCursed(player);
        boolean home = atHome(player);

        // Dried Out ends early in water.
        if (cursed && player.isInWater()) {
            MobTicker.uncurse(player);
            KitUtil.actionBar(player, "The water revived you.");
            cursed = false;
        }

        KitUtil.flight(player, !cursed, home ? HOME_FLY_SPEED : FLY_SPEED);
        KitUtil.passive(player, MobEffects.FIRE_RESISTANCE, 0, true);
        KitUtil.passive(player, MobEffects.REGENERATION, 0, player.level().dimension() == Level.NETHER);

        KitUtil.calmAllies(player, 48, ALLIES);
        tickSeats(player);
    }

    // --- Ferry ------------------------------------------------------------------------

    private static final String FERRY = "ferry";
    private static final int MAX_RIDERS = 4;
    private static final String SEAT_TAG = "mobcraft_seat";

    /**
     * Players can't carry several passengers, so each rider sits on an
     * invisible marker armor stand that's moved to the Ghast every tick.
     */
    private static final Map<UUID, List<ArmorStand>> SEATS = new HashMap<>();

    /** A teammate right-clicked the Ghast. Called from KitEvents. */
    static InteractionResult board(ServerPlayer rider, ServerPlayer ghast, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || rider.isPassenger()) return InteractionResult.PASS;
        if (!MobAbilities.isEnabled(ghast, FERRY)) {
            KitUtil.actionBar(rider, "This Ghast isn't taking passengers.");
            return InteractionResult.FAIL;
        }
        if (!rider.isAlliedTo(ghast)) {
            KitUtil.actionBar(rider, "Only teammates can ride this Ghast.");
            return InteractionResult.FAIL;
        }
        List<ArmorStand> seats = SEATS.computeIfAbsent(ghast.getUUID(), u -> new ArrayList<>());
        seats.removeIf(seat -> seat.isRemoved() || seat.getPassengers().isEmpty());
        if (seats.size() >= MAX_RIDERS) {
            KitUtil.actionBar(rider, "This Ghast is full.");
            return InteractionResult.FAIL;
        }
        ArmorStand seat = spawnSeat(ghast);
        if (seat == null) return InteractionResult.FAIL;
        seats.add(seat);
        rider.startRiding(seat);
        ghast.level().playSound(null, ghast.blockPosition(), SoundEvents.GHAST_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.4f);
        KitUtil.actionBar(rider, "Riding the Ghast. Sneak to get off.");
        return InteractionResult.SUCCESS;
    }

    private static ArmorStand spawnSeat(ServerPlayer ghast) {
        ServerLevel level = ghast.level();
        KitUtil.command(level, "summon minecraft:armor_stand %.3f %.3f %.3f {Invisible:1b,NoGravity:1b,Marker:1b,Invulnerable:1b,Silent:1b,Tags:[\"%s\"]}",
                ghast.getX(), ghast.getY(), ghast.getZ(), SEAT_TAG);
        AABB area = ghast.getBoundingBox().inflate(2);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, area,
                s -> s.entityTags().contains(SEAT_TAG) && s.getPassengers().isEmpty())) {
            boolean taken = SEATS.values().stream().anyMatch(list -> list.contains(stand));
            if (!taken) return stand;
        }
        return null;
    }

    /** Moves each seat to its spot around the Ghast, and clears empty ones. */
    private static void tickSeats(ServerPlayer ghast) {
        List<ArmorStand> seats = SEATS.get(ghast.getUUID());
        if (seats == null) return;
        Iterator<ArmorStand> it = seats.iterator();
        int index = 0;
        while (it.hasNext()) {
            ArmorStand seat = it.next();
            if (seat.isRemoved() || seat.getPassengers().isEmpty() || seat.level() != ghast.level()) {
                seat.discard();
                it.remove();
                continue;
            }
            double angle = Math.PI / 2 * index + Math.toRadians(ghast.getYRot());
            seat.teleportTo(ghast.getX() + Math.cos(angle) * 1.0, ghast.getY() + 0.3, ghast.getZ() + Math.sin(angle) * 1.0);
            index++;
        }
        if (seats.isEmpty()) SEATS.remove(ghast.getUUID());
    }

    private static void clearSeats(ServerPlayer ghast) {
        List<ArmorStand> seats = SEATS.remove(ghast.getUUID());
        if (seats == null) return;
        for (ArmorStand seat : seats) {
            seat.ejectPassengers();
            seat.discard();
        }
    }

    @Override
    public void onDeath(ServerPlayer player, DamageSource source) {
        clearSeats(player);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        clearSeats(player);
        KitUtil.flight(player, false, FLY_SPEED);
        KitUtil.passive(player, MobEffects.FIRE_RESISTANCE, 0, false);
        KitUtil.passive(player, MobEffects.REGENERATION, 0, false);
    }

    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        return KitUtil.doubledAtHome(atHome(player), type);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot != ABILITY_1 && slot != ABILITY_2) {
            KitUtil.actionBar(player, "The Ghast has no third ability.");
            return;
        }
        if (PlayerMob.isCursed(player)) {
            KitUtil.actionBar(player, "You're dried out and can't shoot.");
            return;
        }
        if (slot == ABILITY_1) {
            if (!KitUtil.tryUse(player, FIREBALL, "Fireball", 15, 60)) return;
            shoot(player, 0, 0);
        } else {
            if (!KitUtil.tryUse(player, BARRAGE, "Barrage", 45, 600)) return;
            shoot(player, -10, -1.5);
            shoot(player, 0, 0);
            shoot(player, 10, 1.5);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * Fires one fireball where the player looks, turned sideways by the given
     * degrees and started the given blocks to the side. Barrage fireballs start
     * apart so they don't touch, and ProjectileSiblingMixin stops them hitting
     * each other anyway.
     */
    private static void shoot(ServerPlayer player, float yawOffset, double sideways) {
        ServerLevel level = player.level();
        Vec3 aim = player.getLookAngle();
        Vec3 look = Vec3.directionFromRotation(player.getXRot(), player.getYRot() + yawOffset);
        Vec3 side = new Vec3(-aim.z, 0, aim.x);
        side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize().scale(sideways);
        LargeFireball fireball = new LargeFireball(level, player, look, 1);
        Vec3 eye = player.getEyePosition();
        fireball.setPos(eye.x + aim.x * 2.0 + side.x, eye.y + aim.y * 2.0 - 0.5, eye.z + aim.z * 2.0 + side.z);
        level.addFreshEntity(fireball);
    }

    private static boolean atHome(ServerPlayer player) {
        return KitUtil.inBiome(player, Biomes.NETHER_WASTES, Biomes.SOUL_SAND_VALLEY);
    }

    static void forget(ServerPlayer player) {
        clearSeats(player);
    }

    // --- Ultimate: Hellfire ---------------------------------------------------------------
    /** Two volleys of 5 fireballs fanned out where you look, a second apart. */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Hellfire";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        volley(player);
        UltimateFx.later(player, 20, GhastKit::volley);
        KitUtil.actionBar(player, "Hellfire!");
        return true;
    }

    private static void volley(ServerPlayer player) {
        for (int i = -2; i <= 2; i++) shoot(player, i * 10, i * 1.5);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 2.0f, 0.7f);
    }
}
