package com.reality.mobcraft.item;

import com.reality.mobcraft.item.Tiers.Kind;
import com.reality.mobcraft.mixin.AbstractArrowAccessor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the troll tiers (and the plain bow tiers) actually do. Tiers decides who has which tier.
 *
 * Weapons and tools: Squeaky (hits squeak, extra knockback is an attribute), Butterfingers (1 hit in 50 drops it),
 * Confetti (kills burst into confetti), Backwards (held upside down, client only: BackwardsItemMixin).
 * Armor: Squeaky (steps squeak), Itchy (client: a scratch and a camera shake now and then), Drippy (drips water),
 * Bouncy (less fall damage and a bounce).
 * Bows: the normal tiers change the arrow as it's fired; Boomerang, Party, Wobbly and Chicken Shot follow it in flight.
 */
public final class TrollTiers {

    private TrollTiers() {}

    private static final int[] CONFETTI = {0xFF4D6D, 0xFFD23F, 0x3BCEAC, 0x5E60CE, 0xF77F00, 0x00BBF9};

    private enum Flight { BOOMERANG, PARTY, WOBBLY, CHICKEN }
    private record Tracked(AbstractArrow arrow, Flight what, long born) {}
    private static final List<Tracked> FLYING = new ArrayList<>();
    private static final Map<UUID, Double> WALKED = new HashMap<>();
    private static final Map<UUID, Vec3> LAST = new HashMap<>();

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
            if (!(source.getEntity() instanceof Player p) || source.getDirectEntity() != p || !(victim.level() instanceof ServerLevel level)) return;
            ItemStack held = p.getMainHandItem();
            if (Tiers.is(held, Kind.TOOL, "squeaky")) {
                level.playSound(null, victim.blockPosition(), SoundEvents.BAT_HURT, SoundSource.PLAYERS, 1.0f, 1.9f);
            }
            if (Tiers.is(held, Kind.TOOL, "butterfingers") && p.getRandom().nextInt(50) == 0) {
                ItemStack slip = held.copy();
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                p.drop(slip, true, Prediction.SERVER_ONLY);
                level.playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0f, 0.6f);
                if (p instanceof ServerPlayer sp) sp.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Butterfingers!"));
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof Player p) || !Tiers.is(p.getMainHandItem(), Kind.TOOL, "confetti")) return;
            if (!(entity.level() instanceof ServerLevel level)) return;
            double y = entity.getY() + entity.getBbHeight() / 2;
            for (int c : CONFETTI) {
                level.sendParticles(new DustParticleOptions(c, 1.2f), entity.getX(), y, entity.getZ(), 12, 0.5, 0.5, 0.5, 0.2);
            }
            level.sendParticles(ParticleTypes.FIREWORK, entity.getX(), y, entity.getZ(), 20, 0.2, 0.2, 0.2, 0.15);
            level.playSound(null, entity.blockPosition(), SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 1.0f, 1.2f);
            level.playSound(null, entity.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 1.6f);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) armorTick(p);
            flightTick();
        });
    }

    // ---------------------------------------------------------------- armor

    private static void armorTick(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        Vec3 now = p.position(), before = LAST.put(p.getUUID(), now);
        if (before == null || before.distanceToSqr(now) > 25) return;   // just joined or teleported
        double step = Math.hypot(now.x - before.x, now.z - before.z);
        boolean walking = p.onGround() && step > 0.01;
        // Squeaky: a squeak every block and a half, sneaking or not.
        if (walking && Tiers.wearing(p, "squeaky")) {
            double walked = WALKED.merge(p.getUUID(), step, Double::sum);
            if (walked >= 1.5) {
                WALKED.put(p.getUUID(), 0.0);
                level.playSound(null, p.blockPosition(), SoundEvents.BAT_HURT, SoundSource.PLAYERS, 0.6f, 1.8f + p.getRandom().nextFloat() * 0.3f);
            }
        }
        // Drippy: water drips off you as you move, wherever you are.
        if (step > 0.01 && p.tickCount % 3 == 0 && Tiers.wearing(p, "drippy")) {
            level.sendParticles(ParticleTypes.DRIPPING_WATER, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.4, 0.5, 0.4, 0);
            level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.3, 0.05, 0.3, 0);
        }
    }

    /** Bouncy: called from the damage hook. Takes a fifth off fall damage and bounces the wearer back up. */
    public static float modifyDamage(LivingEntity victim, DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.IS_FALL) || !Tiers.wearing(victim, "bouncy")) return amount;
        Vec3 v = victim.getDeltaMovement();
        victim.setDeltaMovement(v.x, Math.min(0.9, 0.3 + amount * 0.05), v.z);
        com.reality.mobcraft.mob.kit.KitUtil.syncMotion(victim);
        if (victim.level() instanceof ServerLevel level) {
            level.playSound(null, victim.blockPosition(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
        return amount * 0.8f;
    }

    // ---------------------------------------------------------------- bows

    /** Called when any entity loads: arrows from a tiered bow get changed as they leave it. */
    public static void onArrow(Entity entity, ServerLevel level) {
        if (!(entity instanceof AbstractArrow arrow) || arrow.tickCount > 0 || ((AbstractArrowAccessor) arrow).mobcraft$inGround()) return;
        ItemStack bow = arrow.getWeaponItem();
        if (bow == null || bow.isEmpty()) return;
        String id = Tiers.id(bow, Kind.BOW);
        if (id == null) return;
        AbstractArrowAccessor acc = (AbstractArrowAccessor) arrow;
        double dmg = acc.mobcraft$baseDamage();
        var random = level.getRandom();
        switch (id) {
            case "slack" -> arrow.setBaseDamage(dmg * 0.92);
            case "taut" -> arrow.setBaseDamage(dmg * 1.10);
            case "legendary" -> arrow.setBaseDamage(dmg * 1.15);
            case "far_shot" -> {
                // Faster and farther; damage scales with speed, so the base comes down to keep hits the same.
                arrow.setDeltaMovement(arrow.getDeltaMovement().scale(1.2));
                arrow.setBaseDamage(dmg / 1.2);
            }
            case "warped" -> {
                Vec3 v = arrow.getDeltaMovement();
                double len = v.length();
                arrow.setDeltaMovement(v.add(random.nextGaussian() * 0.05 * len, random.nextGaussian() * 0.05 * len,
                        random.nextGaussian() * 0.05 * len));
            }
            case "boomerang" -> { if (random.nextInt(10) == 0) FLYING.add(new Tracked(arrow, Flight.BOOMERANG, level.getGameTime())); }
            case "party" -> FLYING.add(new Tracked(arrow, Flight.PARTY, level.getGameTime()));
            case "wobbly" -> FLYING.add(new Tracked(arrow, Flight.WOBBLY, level.getGameTime()));
            case "chicken" -> { if (random.nextInt(20) == 0) FLYING.add(new Tracked(arrow, Flight.CHICKEN, level.getGameTime())); }
            default -> {}
        }
    }

    private static void flightTick() {
        for (Iterator<Tracked> it = FLYING.iterator(); it.hasNext(); ) {
            Tracked t = it.next();
            AbstractArrow a = t.arrow();
            if (!(a.level() instanceof ServerLevel level)) { it.remove(); continue; }
            boolean landed = a.isRemoved() || ((AbstractArrowAccessor) a).mobcraft$inGround();
            long age = level.getGameTime() - t.born();
            if (age > 200) { it.remove(); continue; }
            switch (t.what()) {
                case PARTY -> {
                    if (landed) {
                        for (int c : CONFETTI) level.sendParticles(new DustParticleOptions(c, 1.0f), a.getX(), a.getY(), a.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
                        level.sendParticles(ParticleTypes.FIREWORK, a.getX(), a.getY(), a.getZ(), 10, 0.1, 0.1, 0.1, 0.1);
                        level.playSound(null, a.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.8f, 1.4f);
                        it.remove();
                    } else {
                        int c = CONFETTI[(int) (age % CONFETTI.length)];
                        level.sendParticles(new DustParticleOptions(c, 0.9f), a.getX(), a.getY(), a.getZ(), 2, 0.05, 0.05, 0.05, 0);
                    }
                }
                case WOBBLY -> {
                    if (landed) { it.remove(); continue; }
                    Vec3 v = a.getDeltaMovement();
                    Vec3 side = new Vec3(-v.z, 0, v.x);
                    if (side.lengthSqr() > 1e-6) {
                        side = side.normalize().scale(Math.cos(age * 0.7) * 0.12 * Math.min(1, v.length()));
                        a.setDeltaMovement(v.add(side));
                        com.reality.mobcraft.mob.kit.KitUtil.syncMotion(a);
                    }
                }
                case BOOMERANG -> {
                    if (landed) { it.remove(); continue; }
                    if (age == 12 && a.getOwner() instanceof LivingEntity owner) {
                        Vec3 back = owner.getEyePosition().subtract(a.position());
                        a.setDeltaMovement(back.normalize().scale(Math.max(0.8, a.getDeltaMovement().length())));
                        com.reality.mobcraft.mob.kit.KitUtil.syncMotion(a);
                        a.pickup = AbstractArrow.Pickup.ALLOWED;
                        level.playSound(null, a.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8f, 1.5f);
                        it.remove();
                    }
                }
                case CHICKEN -> {
                    if (!landed) continue;
                    var chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.TRIGGERED);
                    if (chicken != null) {
                        chicken.snapTo(a.getX(), a.getY(), a.getZ(), a.getYRot(), 0);
                        level.addFreshEntity(chicken);
                        level.sendParticles(ParticleTypes.POOF, a.getX(), a.getY(), a.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
                        level.playSound(null, a.blockPosition(), SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.0f, 1.0f);
                    }
                    if (!a.isRemoved()) a.discard();
                    it.remove();
                }
            }
        }
    }

    // ---------------------------------------------------------------- bow draw speed

    /** How many ticks of drawing a bow counts as, for Quick and Legendary bows. */
    public static int drawTicks(ItemStack bow, int ticks) {
        String id = Tiers.id(bow, Kind.BOW);
        if ("quick".equals(id)) return Math.round(ticks * 1.15f);
        if ("legendary".equals(id)) return Math.round(ticks * 1.10f);
        return ticks;
    }

    /** A crossbow's charge time, for Quick and Legendary crossbows. */
    public static int chargeTicks(ItemStack crossbow, int ticks) {
        String id = Tiers.id(crossbow, Kind.BOW);
        if ("quick".equals(id)) return Math.max(1, Math.round(ticks / 1.15f));
        if ("legendary".equals(id)) return Math.max(1, Math.round(ticks / 1.10f));
        return ticks;
    }
}
