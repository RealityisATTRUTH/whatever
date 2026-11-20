package com.reality.mobcraft.item;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A netherite sword with a Fracture Nether Star on it. Hold right-click to charge
 * it, raising the sword, for 10 seconds. Let go once it's fully charged and it
 * fires a beam of star energy where you look. Letting go early does nothing.
 *
 *   Making it  netherite upgrade template + netherite sword + Fracture Nether
 *             Star, in a smithing table (star_blade_smithing.json) or anywhere
 *             in a crafting grid (ArmorUpgradeRecipe). Both work.
 *   Beam      up to 48 blocks, stops at the first solid block. Everything
 *             living in its path takes 16 damage (armor counts) and is
 *             thrown back along it. Doesn't break blocks.
 *   Cooldown  45 seconds after firing.
 *   Death     the sword floats up and shatters like an Eye of Ender. It's
 *             gone, shard and all, so it has to be crafted again.
 *
 * It looks like the netherite sword with the star set in its guard and a
 * crack up the blade, which glows while charging (items/star_blade.json,
 * drawn over the game's own netherite sword texture).
 *
 * Holding right-click works because attaching the shard gives the sword a
 * use animation that never finishes, the way a drink would, but without any
 * eating or drinking.
 */
public final class StarBlade {

    public static final int CHARGE_TICKS = 200;   // 10 seconds
    private static final int COOLDOWN_TICKS = 900; // 45 seconds
    private static final double RANGE = 48;
    private static final float DAMAGE = 16.0f;

    private static final Identifier MODEL = MobCraft.id("star_blade");

    private static final Set<UUID> CHARGING = new HashSet<>();
    private static final Set<UUID> CHARGED = new HashSet<>();

    private StarBlade() {}

    public static boolean isStarBlade(ItemStack stack) {
        return stack.is(Items.NETHERITE_SWORD) && ArmorUpgrades.STAR_SHARD.equals(stack.get(ModItems.ARMOR_UPGRADE));
    }

    /** Gives the sword its look and lets it be held up with right-click. Called when the shard is attached. */
    public static void makeChargeable(ItemStack sword) {
        // Its own look first, so it shows even if the hold-to-charge part fails.
        sword.set(DataComponents.ITEM_MODEL, MODEL);
        Consumable consumable = parse("spear");
        if (consumable == null) consumable = parse("bow");
        if (consumable == null) {
            MobCraft.LOGGER.warn("Couldn't make the Nether Star sword chargeable.");
            return;
        }
        sword.set(DataComponents.CONSUMABLE, consumable);
    }

    /**
     * No sound is given here. A sound name can't be read without the game's
     * registries, which made the whole thing fail before. The default eating
     * sound only plays when a use finishes, and this one never does.
     */
    private static Consumable parse(String animation) {
        JsonObject json = new JsonObject();
        json.addProperty("consume_seconds", 1000000.0f); // never actually finishes
        json.addProperty("animation", animation);
        json.addProperty("has_consume_particles", false);
        return Consumable.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
    }

    /** Swords made before this fix have the shard but not the look, so they're fixed when held. */
    private static void repair(ItemStack stack) {
        if (isStarBlade(stack) && !MODEL.equals(stack.get(DataComponents.ITEM_MODEL))) {
            makeChargeable(stack);
        }
    }

    /** How long a sword rises before it shatters: 1.5 seconds. */
    private static final int RISE_TICKS = 30;
    /** Rising swords and how many ticks each has risen. Counted here, since an item's own age restarts on reload. */
    private static final java.util.Map<ItemEntity, Integer> RISING = new java.util.LinkedHashMap<>();
    /** Saved on the item, so a sword that was rising when its chunk unloaded or the server stopped still shatters. */
    private static final String RISING_TAG = "mobcraft_star_rising";

    /**
     * When someone dies, their Nether Star sword doesn't drop. It floats up out
     * of their body and shatters like an Eye of Ender, and nobody can pick it
     * up. The sword and the shard are both gone, so it has to be crafted again.
     */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) shatterOnDeath(player);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> tickRising());
        // A rising sword that comes back into the world (chunk reloaded, server restarted) carries on and shatters.
        // So does any star sword left floating by an older version.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof ItemEntity item) || RISING.containsKey(item) || !isStarBlade(item.getItem())) return;
            if (item.entityTags().contains(RISING_TAG) || item.isNoGravity()) RISING.put(item, RISE_TICKS - 10);
        });
    }

    private static void shatterOnDeath(ServerPlayer player) {
        ServerLevel level = player.level();
        // Swords that just fell out of their inventory.
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4),
                e -> isStarBlade(e.getItem()) && e.tickCount < 5 && !RISING.containsKey(e))) {
            startRising(item, player.position());
            com.reality.mobcraft.MobAdvancements.award(player, "gone");
        }
        // With keepInventory on, nothing drops, so take it from the inventory instead.
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!isStarBlade(stack)) continue;
            inventory.setItem(i, ItemStack.EMPTY);
            ItemEntity item = new ItemEntity(level, player.getX(), player.getY() + 1.0, player.getZ(), stack);
            level.addFreshEntity(item);
            startRising(item, player.position());
            com.reality.mobcraft.MobAdvancements.award(player, "gone");
        }
    }

    private static void startRising(ItemEntity item, Vec3 from) {
        item.setPos(from.x, from.y + 1.0, from.z);
        item.setNeverPickUp();
        item.setNoGravity(true);
        item.setDeltaMovement(0, 0.12, 0);
        item.addTag(RISING_TAG);
        RISING.put(item, 0);
    }

    private static void tickRising() {
        Iterator<java.util.Map.Entry<ItemEntity, Integer>> it = RISING.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<ItemEntity, Integer> entry = it.next();
            ItemEntity item = entry.getKey();
            // Unloaded with its chunk: it picks up again when it loads (ENTITY_LOAD above).
            if (item.isRemoved() || !(item.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            int risen = entry.getValue() + 1;
            entry.setValue(risen);
            item.setDeltaMovement(0, 0.12, 0); // keep rising at a steady pace
            KitUtil.syncMotion(item);
            if (risen % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.2, item.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
            }
            if (risen >= RISE_TICKS) {
                shatter(level, item);
                it.remove();
            }
        }
    }

    /** The Eye of Ender break: its particles and sound, plus a burst of star light. */
    private static void shatter(ServerLevel level, ItemEntity item) {
        BlockPos pos = item.blockPosition();
        level.levelEvent(2003, pos, 0); // the particles an Eye of Ender makes when it breaks
        level.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.2, item.getZ(), 30, 0.1, 0.1, 0.1, 0.15);
        level.sendParticles(ParticleTypes.ENCHANT, item.getX(), item.getY() + 0.2, item.getZ(), 20, 0.3, 0.3, 0.3, 1.0);
        level.playSound(null, pos, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 1.5f, 0.8f);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
        item.discard();
    }

    /** Every tick for every player: charging effects, and firing on release. */
    public static void tick(ServerPlayer player) {
        repair(player.getMainHandItem());
        repair(player.getOffhandItem());
        if (player.tickCount % 20 == 0 && (isStarBlade(player.getMainHandItem()) || isStarBlade(player.getOffhandItem()))) {
            com.reality.mobcraft.MobAdvancements.award(player, "starforged");
        }
        UUID id = player.getUUID();
        boolean using = player.isUsingItem() && isStarBlade(player.getUseItem());
        if (using) {
            CHARGING.add(id);
            int ticks = player.getTicksUsingItem();
            chargeEffects(player, ticks);
            if (ticks >= CHARGE_TICKS) {
                if (CHARGED.add(id)) {
                    player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.6f);
                    KitUtil.actionBar(player, "Fully charged! Let go to fire.");
                }
            } else if (ticks % 20 == 0) {
                KitUtil.actionBar(player, "Charging... " + (ticks * 100 / CHARGE_TICKS) + "%");
            }
        } else if (CHARGING.remove(id)) {
            if (CHARGED.remove(id)) {
                fire(player);
            } else {
                KitUtil.actionBar(player, "Not charged. Hold it for 10 seconds.");
            }
        }
    }

    /** Star energy gathering around you, faster and higher pitched as it fills. */
    private static void chargeEffects(ServerPlayer player, int ticks) {
        ServerLevel level = player.level();
        float progress = Math.min(1.0f, ticks / (float) CHARGE_TICKS);
        if (ticks % 4 == 0) {
            int count = 2 + (int) (progress * 10);
            level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(),
                    count, 0.8, 0.8, 0.8, 0.02);
            if (progress >= 1.0f) {
                level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.4, player.getZ(),
                        6, 0.4, 0.4, 0.4, 0.5);
            }
        }
        if (ticks % 20 == 0 && progress < 1.0f) {
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS,
                    0.3f, 0.5f + progress * 1.5f);
        }
    }

    /** The beam: straight from your eyes to the first block, hitting everything along it. */
    private static void fire(ServerPlayer player) {
        com.reality.mobcraft.MobAdvancements.award(player, "star_beam");
        ServerLevel level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        Vec3 end = eye.add(dir.scale(RANGE));
        Vec3 stop = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        double length = stop.distanceTo(eye);

        for (double d = 1; d <= length; d += 1.5) {
            Vec3 p = eye.add(dir.scale(d));
            level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.01);
        }
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE), e -> e != player && e.isAlive())) {
            Vec3 center = target.getBoundingBox().getCenter();
            double along = center.subtract(eye).dot(dir);
            if (along < 0 || along > length) continue;
            double off = center.distanceTo(eye.add(dir.scale(along)));
            if (off > target.getBbWidth() / 2 + 1.0) continue;
            target.hurtServer(level, player.damageSources().playerAttack(player), DAMAGE);
            target.setDeltaMovement(target.getDeltaMovement().add(dir.x * 1.5, 0.4, dir.z * 1.5));
            KitUtil.syncMotion(target);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 1.2f);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.5f, 0.6f);
        player.getCooldowns().addCooldown(player.getMainHandItem(), COOLDOWN_TICKS);
    }

    public static void forget(ServerPlayer player) {
        CHARGING.remove(player.getUUID());
        CHARGED.remove(player.getUUID());
    }
}
