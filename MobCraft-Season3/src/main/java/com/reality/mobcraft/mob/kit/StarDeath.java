package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.item.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Wither's death, when a player kills it.
 *
 *   Rise      a glowing nether star lifts out of the body and climbs about
 *             5 blocks over 2 seconds, trailing star light.
 *   Strain    it hangs at the top for half a second, crackling.
 *   Burst     it explodes in a flash of light. The explosion is only light
 *             and sound: it breaks nothing and hurts no one.
 *   Splinter  the star splinters. 2 Star Fragments fly out to land nearby
 *             for anyone to pick up, along with shards that glint and vanish.
 *
 * Nobody can grab the star on the way up. If its chunk unloads or the server
 * stops partway, the fragments drop straight away, so they're never lost.
 */
public final class StarDeath {

    private static final String TAG = "mobcraft_dying_star";
    private static final int RISE_TICKS = 40;
    private static final int BURST_AT = 50;
    private static final double RISE_SPEED = 0.12;
    private static final int FRAGMENTS = 2;
    private static final int SHARDS = 8;
    private static final int SHARD_TICKS = 30;

    private static final Map<ItemEntity, Integer> RISING = new LinkedHashMap<>();
    /** Stars that came back with their chunk, burst on the next tick. */
    private static final List<ItemEntity> RELOADED = new ArrayList<>();

    private StarDeath() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(StarDeath::tick);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item && item.entityTags().contains(TAG) && !RISING.containsKey(item)) {
                RELOADED.add(item);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ItemEntity star : new ArrayList<>(RISING.keySet())) {
                if (star.level() instanceof ServerLevel level) burst(level, star, false);
            }
            RISING.clear();
            RELOADED.clear();
        });
    }

    /** Starts the animation where the Wither died. */
    public static void start(ServerLevel level, Vec3 at) {
        ItemEntity star = new ItemEntity(level, at.x, at.y + 1.0, at.z, new ItemStack(Items.NETHER_STAR));
        star.setNeverPickUp();
        star.setNoGravity(true);
        star.setGlowingTag(true);
        star.setDeltaMovement(0, RISE_SPEED, 0);
        star.addTag(TAG);
        level.addFreshEntity(star);
        RISING.put(star, 0);
        level.playSound(null, star.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.5f, 1.2f);
        level.playSound(null, star.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.4f);
    }

    private static void tick(MinecraftServer server) {
        if (!RELOADED.isEmpty()) {
            for (ItemEntity star : RELOADED) {
                if (!star.isRemoved() && star.level() instanceof ServerLevel level) burst(level, star, true);
            }
            RELOADED.clear();
        }
        if (RISING.isEmpty()) return;
        Iterator<Map.Entry<ItemEntity, Integer>> it = RISING.entrySet().iterator();
        List<ItemEntity> bursting = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<ItemEntity, Integer> entry = it.next();
            ItemEntity star = entry.getKey();
            if (star.isRemoved() || !(star.level() instanceof ServerLevel level)) {
                // Unloaded with its chunk: it bursts when it loads again (ENTITY_LOAD above).
                it.remove();
                continue;
            }
            int age = entry.getValue() + 1;
            entry.setValue(age);
            if (age <= RISE_TICKS) {
                star.setDeltaMovement(0, RISE_SPEED, 0);
                if (age % 2 == 0) {
                    level.sendParticles(ParticleTypes.END_ROD, star.getX(), star.getY() + 0.2, star.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
                }
                if (age % 10 == 0) {
                    float pitch = 0.8f + age / (float) RISE_TICKS;
                    level.playSound(null, star.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, pitch);
                }
            } else {
                star.setDeltaMovement(Vec3.ZERO);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, star.getX(), star.getY() + 0.2, star.getZ(), 6, 0.25, 0.25, 0.25, 0.1);
                if (age == RISE_TICKS + 1) {
                    level.playSound(null, star.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.6f);
                }
            }
            KitUtil.syncMotion(star);
            if (age >= BURST_AT) {
                it.remove();
                bursting.add(star);
            }
        }
        for (ItemEntity star : bursting) {
            if (star.level() instanceof ServerLevel level) burst(level, star, true);
        }
    }

    /** The flash, the sound and the splinters. With show off, only the fragments. */
    private static void burst(ServerLevel level, ItemEntity star, boolean show) {
        Vec3 at = star.position().add(0, 0.2, 0);
        star.discard();
        RandomSource random = level.getRandom();
        if (show) {
            BlockPos pos = BlockPos.containing(at);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 80, 0.2, 0.2, 0.2, 0.35);
            level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 60, 0.3, 0.3, 0.3, 0.25);
            level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 3.0f, 0.7f);
            level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 2.0f, 1.0f);
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2.0f, 0.5f);
            // Shards that glint and fade. Only for show: nobody can pick them up.
            for (int i = 0; i < SHARDS; i++) {
                ItemEntity shard = fling(level, at, random, new ItemStack(ModItems.STAR_FRAGMENT), 0.35);
                shard.setNeverPickUp();
                UltimateFx.temporary(shard, SHARD_TICKS);
            }
        }
        // The real fragments, for whoever gets to them.
        for (int i = 0; i < FRAGMENTS; i++) {
            ItemEntity fragment = fling(level, at, random, new ItemStack(ModItems.STAR_FRAGMENT), 0.25);
            fragment.setPickUpDelay(20);
            fragment.setGlowingTag(true);
        }
    }

    private static ItemEntity fling(ServerLevel level, Vec3 at, RandomSource random, ItemStack stack, double speed) {
        double angle = random.nextDouble() * Math.PI * 2;
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack,
                Math.cos(angle) * speed, 0.25 + random.nextDouble() * 0.2, Math.sin(angle) * speed);
        level.addFreshEntity(item);
        return item;
    }
}
