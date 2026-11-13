package com.reality.mobcraft.item;

import com.mojang.serialization.Codec;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobLook;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

/**
 * Creeper Essence: the Creeper's mob item, a jagged red crystal with orange cracks that pulse brighter and brighter
 * (models/item/creeper_fuse.json; the item id is still creeper_fuse, from when it was the Creeper Fuse). It drops
 * when another player kills the Creeper (see MobRelics).
 *
 * Right-click it to absorb it, like the other essences (ShulkerEssence.EssenceItem). Then, like Rex Splode:
 *   Press B      whatever you're holding is charged with explosive energy. An orange glow swirls over the item,
 *                hugging its shape (client/ExplosiveItemGlow), orange sparks crackle round your hand, and it hisses like a creeper. You glow orange all over, like the Enderman's
 *                purple teleport glow, flickering faster the longer you hold it (MobLookLayer).
 *   Right-click  throw it. It glows orange as it flies, still wrapped in the swirl, and blows up where it lands.
 *                How hard depends on its density (Density): packed ice is a charged creeper's blast, power 6, growing
 *                with the cube root of density, from 1 up to 12. A water bucket is 4.6, bigger than TNT. It breaks blocks like TNT, as far as that power reaches:
 *                light things crack dirt and sand, dense ones chew through stone. Blocks only stand up to it with a
 *                quarter of their blast resistance (CONCENTRATED), except the ones built to survive anything. The thrown item is used up: one from the stack.
 *   Press B      again before throwing to let the charge go.
 *   Sneak+B      Rex's last stand: you swell and flash for 1.5 seconds, glowing orange, then blow yourself up
 *                with three creepers' blast, power 9, for an inventory as dense as packed ice on average, from 2 up to 16.
 *                It breaks blocks like TNT. You die and the essence is gone.
 *   Charge       lasts 10 seconds, then fizzles out with no cooldown.
 *   Cooldown     45 seconds from each throw.
 *   Keeps        until you die, or until you absorb a different essence.
 *   Not for      the Creeper player, who has his own blast.
 * Chestplates that already have a Creeper Fuse on them keep its old effect.
 */
public final class CreeperEssence {

    public static final String CREEPER = "creeper";
    private static final int CHARGE_TICKS = 200;
    private static final int COOLDOWN_TICKS = 900;
    private static final float POWER = 6f;          // a charged creeper's blast, for something as dense as packed ice
    private static final float MIN_POWER = 1f, MAX_POWER = 12f;
    private static final float SPEED = 1.5f;        // like a snowball
    private static final DustParticleOptions SPARK = new DustParticleOptions(0xFF8A1E, 1.0f);

    /** On a stack charged with explosive energy: the server tick it fizzles at. Seen by the client too. */
    public static final DataComponentType<Long> CHARGE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            MobCraft.id("creeper_charge"),
            DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());

    public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, MobCraft.id("charged_throw"));
    public static final EntityType<Thrown> ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.<Thrown>of(Thrown::new, MobCategory.MISC).noLootTable()
                    .sized(0.3f, 0.3f).clientTrackingRange(6).updateInterval(5).build(KEY));

    /**
     * The charge is concentrated: blocks stand up to it with only a quarter of their usual blast resistance, so stone
     * breaks about like dirt does against TNT. Blocks built to survive anything (obsidian, bedrock) still do.
     */
    private static final ExplosionDamageCalculator CONCENTRATED = new ExplosionDamageCalculator() {
        @Override
        public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos,
                                                           BlockState state, FluidState fluid) {
            return super.getBlockExplosionResistance(explosion, level, pos, state, fluid).map(r -> r < 100f ? r * 0.25f : r);
        }
    };

    /** Who has a charge going, and until when. */
    private record Charging(long until) {}

    private static final Map<UUID, Charging> CHARGING = new HashMap<>();
    private static final Map<UUID, Integer> READY_AT = new HashMap<>();
    /** Sneak+B: who's about to go off, and the server tick they do. */
    private static final Map<UUID, Integer> FUSING = new HashMap<>();
    private static final int SELF_FUSE = 30;           // 1.5 seconds, like a creeper
    private static final float SELF_POWER = 9f;        // three creepers, carrying packed ice on average
    private static final float SELF_MIN = 2f, SELF_MAX = 16f;

    private CreeperEssence() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> throwIfCharged(player, hand));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> throwIfCharged(player, hand));
        ServerTickEvents.END_SERVER_TICK.register(CreeperEssence::tick);
    }

    /** The Essence Power key with Creeper Essence. */
    public static void use(ServerPlayer player) {
        if (player.isShiftKeyDown()) {
            lastStand(player);
            return;
        }
        if (FUSING.containsKey(player.getUUID())) return;
        if (CHARGING.containsKey(player.getUUID())) {
            discharge(player);
            KitUtil.actionBar(player, "You let the charge go.");
            player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
            return;
        }
        int now = player.level().getServer().getTickCount();
        Integer ready = READY_AT.get(player.getUUID());
        if (ready != null && now < ready) {
            KitUtil.actionBar(player, "Creeper Essence ready in " + KitUtil.seconds(ready - now) + ".");
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            KitUtil.actionBar(player, "Hold something to charge it.");
            return;
        }
        long until = player.level().getGameTime() + CHARGE_TICKS;
        held.set(CHARGE, until);
        CHARGING.put(player.getUUID(), new Charging(until));
        MobLook.set(player, MobLook.EXPLOSIVE, true);     // glowing orange all over (MobLookLayer)
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 1f, 1.1f);
        sparks(player, 18);
        double density = Density.of(held);
        KitUtil.actionBar(player, "Charged! " + Density.describe(density) + String.format(", power %.1f.", Density.power(density, POWER, MIN_POWER, MAX_POWER)));
    }

    /** Right-click with a charged item: throw it instead of using it. */
    private static InteractionResult throwIfCharged(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.has(CHARGE)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;   // the client swings; the server throws
        Charging charging = CHARGING.get(sp.getUUID());
        if (charging == null || sp.level().getGameTime() > charging.until()) {
            stack.remove(CHARGE);
            return InteractionResult.PASS;
        }
        ServerLevel level = sp.level();
        ItemStack one = stack.copyWithCount(1);
        Thrown thrown = new Thrown(sp, level, one);
        thrown.shootFromRotation(sp, sp.getXRot(), sp.getYRot(), 0f, SPEED, 1f);
        level.addFreshEntity(thrown);
        stack.consume(1, sp);
        discharge(sp);
        READY_AT.put(sp.getUUID(), level.getServer().getTickCount() + COOLDOWN_TICKS);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f, 0.7f);
        return InteractionResult.SUCCESS;
    }

    /** Takes the charge off whatever's charged, and forgets it. */
    private static void discharge(ServerPlayer player) {
        CHARGING.remove(player.getUUID());
        MobLook.set(player, MobLook.EXPLOSIVE, false);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.has(CHARGE)) stack.remove(CHARGE);
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.has(CHARGE)) carried.remove(CHARGE);
    }

    /**
     * Sneak+B: Rex's last stand. You swell and flash like a creeper for 1.5 seconds, glowing orange, and there's no
     * stopping it. Then you go off, as hard as the density of what you carry, and you die. The essence is
     * gone. Ready any time, cooldown or not.
     */
    private static void lastStand(ServerPlayer player) {
        if (FUSING.containsKey(player.getUUID())) return;
        if (CHARGING.containsKey(player.getUUID())) discharge(player);
        FUSING.put(player.getUUID(), player.level().getServer().getTickCount() + SELF_FUSE);
        MobLook.set(player, MobLook.EXPLOSIVE, true);
        MobLook.set(player, MobLook.SELF_FUSE, true);       // swelling and flashing, on anyone (MobLookLayer)
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.PLAYERS, 2f, 0.8f);
        level.sendParticles(SPARK, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.7, 0.4, 0.05);
        double density = Density.average(player.getInventory());
        KitUtil.actionBar(player, "No turning back now. " + Density.describe(density) + String.format(", power %.1f.", Density.power(density, SELF_POWER, SELF_MIN, SELF_MAX)));
    }

    private static void goOff(ServerPlayer player) {
        FUSING.remove(player.getUUID());
        MobLook.set(player, MobLook.SELF_FUSE, false);
        MobLook.set(player, MobLook.EXPLOSIVE, false);
        if (!player.isAlive()) return;
        ServerLevel level = player.level();
        player.removeAttached(MobAttachments.ESSENCE);      // used up
        // The blast hits everyone round you, not you: then you go too.
        // How hard: the average density of everything you carry (Density). It breaks blocks like TNT, as far as its power reaches.
        double density = Density.average(player.getInventory());
        level.explode(player, null, CONCENTRATED, player.getX(), player.getY() + 1, player.getZ(),
                Density.power(density, SELF_POWER, SELF_MIN, SELF_MAX), false, Level.ExplosionInteraction.TNT);
        level.sendParticles(SPARK, player.getX(), player.getY() + 1, player.getZ(), 80, 1.2, 1.2, 1.2, 0.2);
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(), 40, 0.8, 0.8, 0.8, 0.15);
        player.hurtServer(level, level.damageSources().explosion(player, player), Float.MAX_VALUE);
    }

    /** From ShulkerEssence when the essence goes: death, logging out, or absorbing another one. */
    public static void forget(ServerPlayer player) {
        if (CHARGING.containsKey(player.getUUID())) discharge(player);
        if (FUSING.remove(player.getUUID()) != null) {
            MobLook.set(player, MobLook.SELF_FUSE, false);
            MobLook.set(player, MobLook.EXPLOSIVE, false);
        }
    }

    private static void tick(MinecraftServer server) {
        if (!FUSING.isEmpty()) {
            int now = server.getTickCount();
            for (Map.Entry<UUID, Integer> entry : Map.copyOf(FUSING).entrySet()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) FUSING.remove(entry.getKey());
                else if (now >= entry.getValue()) goOff(player);
            }
        }
        if (CHARGING.isEmpty()) return;
        for (Map.Entry<UUID, Charging> entry : Map.copyOf(CHARGING).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                CHARGING.remove(entry.getKey());
                continue;
            }
            if (player.level().getGameTime() > entry.getValue().until()
                    || !CREEPER.equals(player.getAttached(MobAttachments.ESSENCE))) {
                discharge(player);
                player.level().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.6f);
                KitUtil.actionBar(player, "The charge fizzled out.");
                continue;
            }
            if (player.tickCount % 3 == 0 && player.getMainHandItem().has(CHARGE)) sparks(player, 3);
        }
    }

    /** Orange sparks crackling round the hand that holds the charged item. */
    private static void sparks(ServerPlayer player, int count) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 hand = player.getEyePosition().add(look.scale(0.55)).add(right.scale(0.38)).add(0, -0.45, 0);
        ServerLevel level = player.level();
        level.sendParticles(SPARK, hand.x, hand.y, hand.z, count, 0.12, 0.12, 0.12, 0.02);
        if (count > 5 || player.getRandom().nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.SMALL_FLAME, hand.x, hand.y, hand.z, 1, 0.08, 0.08, 0.08, 0.01);
        }
    }

    /** The thrown item: glowing orange (client/MobCraftClient), it blows up wherever it lands. */
    public static final class Thrown extends ThrowableItemProjectile {
        public Thrown(EntityType<? extends Thrown> type, Level level) {
            super(type, level);
        }

        Thrown(LivingEntity owner, Level level, ItemStack stack) {
            super(ENTITY, owner, level, stack);
        }

        @Override
        protected Item getDefaultItem() {
            return Items.TNT;
        }

        @Override
        public void tick() {
            super.tick();
            if (level().isClientSide()) {
                level().addParticle(SPARK, getX(), getY() + 0.1, getZ(), 0, 0, 0);
                if (tickCount % 2 == 0) level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.1, getZ(), 0, 0.01, 0);
                if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.02, 0);
            } else if (tickCount > 600) {
                discard();
            }
        }

        @Override
        protected void onHit(HitResult hit) {
            super.onHit(hit);
            if (level() instanceof ServerLevel && !isRemoved()) {
                // How hard depends on how dense the thrown item is (Density): packed ice is a creeper's blast.
                // It breaks blocks like TNT: each block's blast resistance decides whether this power is enough.
                double density = Density.of(getItem());
                level().explode(this, null, CONCENTRATED, getX(), getY(), getZ(), Density.power(density, POWER, MIN_POWER, MAX_POWER),
                        false, Level.ExplosionInteraction.TNT);
                discard();
            }
        }
    }
}
