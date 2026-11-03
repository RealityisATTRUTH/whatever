package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.rts.RtsControl;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.reality.mobcraft.MobCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Warden's ultimate, Sculk Eruption.
 *
 *   Slam      the Warden hits the ground. The ground in an 8 block radius
 *             turns to sculk, with a sonic boom.
 *   Launch    every enemy on it is thrown about 7 blocks straight up.
 *   Slam down at the top they're driven back down, much faster than a fall.
 *   Impact    6 hearts of sonic damage. Armor, enchantments and Feather
 *             Falling don't help, and the hit takes 35% of the boots'
 *             durability. Then a 3 second stun: players can't move, jump or
 *             attack, mobs are slowed to a crawl and weakened.
 *   Fade      after 30 seconds the sculk turns back into what was there,
 *             including blocks broken in the meantime. If the server stops
 *             first, it's put back right away. If the server crashes, the
 *             blocks are saved in mobcraft_sculk.txt in the world folder and
 *             put back the next time the server starts.
 *
 * Teammates, the Warden's allies (wardens) and mobs the Warden player
 * commands aren't thrown. Block entities like chests, and unbreakable blocks,
 * are never turned to sculk.
 */
public final class SculkEruption {

    private static final int RADIUS = 8;
    private static final double LAUNCH_SPEED = 1.4;
    private static final double SLAM_SPEED = -3.5;
    private static final int APEX_TICKS = 12;
    private static final int GIVE_UP_TICKS = 40;
    private static final float DAMAGE = 12.0f;          // 6 hearts
    private static final float BOOT_WEAR = 0.35f;       // of the boots' durability
    private static final int STUN_TICKS = 60;
    private static final int FADE_TICKS = 600;          // 30 seconds

    private record Patch(ServerLevel level, BlockPos pos, BlockState original, int fadeAt) {}

    private static final class Thrown {
        final ServerPlayer warden;
        final int start;
        boolean slamming;

        Thrown(ServerPlayer warden, int start) {
            this.warden = warden;
            this.start = start;
        }
    }

    private static final List<Patch> SCULK = new ArrayList<>();
    private static final Map<LivingEntity, Thrown> THROWN = new LinkedHashMap<>();

    private SculkEruption() {}

    private static final String SAVE_FILE = "mobcraft_sculk.txt";
    private static Path saveFile;
    private static boolean dirty;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SculkEruption::tick);
        // Sculk left over from a crash goes back first thing.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            saveFile = server.getWorldPath(LevelResource.ROOT).resolve(SAVE_FILE);
            restoreSaved(server);
        });
        // Never leave sculk behind when the server shuts down.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Patch patch : SCULK) restore(patch);
            SCULK.clear();
            save();
        });
    }

    // --- Crash safety ---------------------------------------------------------------

    /** Writes every patch still out: dimension, position, the original block. Empty list deletes the file. */
    private static void save() {
        dirty = false;
        if (saveFile == null) return;
        try {
            if (SCULK.isEmpty()) {
                Files.deleteIfExists(saveFile);
                return;
            }
            List<String> lines = new ArrayList<>();
            for (Patch patch : SCULK) {
                BlockPos pos = patch.pos();
                lines.add(patch.level().dimension() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ()
                        + "|" + Block.getId(patch.original()));
            }
            Files.write(saveFile, lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't save Sculk Eruption blocks", e);
        }
    }

    /** Puts back sculk a crash left behind, then forgets it. */
    private static void restoreSaved(MinecraftServer server) {
        if (saveFile == null || !Files.exists(saveFile)) return;
        int restored = 0;
        try {
            for (String line : Files.readAllLines(saveFile, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\\|");
                if (parts.length != 5) continue;
                ServerLevel level = null;
                for (ServerLevel candidate : server.getAllLevels()) {
                    if (candidate.dimension().toString().equals(parts[0])) level = candidate;
                }
                if (level == null) continue;
                BlockPos pos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                restore(new Patch(level, pos, Block.stateById(Integer.parseInt(parts[4])), 0));
                restored++;
            }
            Files.deleteIfExists(saveFile);
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't put back Sculk Eruption blocks from the last run", e);
        }
        if (restored > 0) MobCraft.LOGGER.info("Put back {} blocks of Sculk Eruption sculk left from the last run.", restored);
    }

    /** Fires it. Always goes off, so the charge is used. */
    public static boolean erupt(ServerPlayer warden) {
        ServerLevel level = warden.level();
        int now = KitUtil.now(warden);
        BlockPos center = warden.blockPosition();

        level.playSound(null, center, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 3.0f, 0.7f);
        level.playSound(null, center, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 2.0f, 0.8f);
        level.playSound(null, center, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 2.0f, 0.9f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, warden.getX(), warden.getY() + 0.5, warden.getZ(), 1, 0, 0, 0, 0);

        // The ground turns to sculk.
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
                BlockPos ground = surface(level, center.offset(dx, 0, dz));
                if (ground == null) continue;
                BlockState original = level.getBlockState(ground);
                SCULK.add(new Patch(level, ground.immutable(), original, now + FADE_TICKS));
                level.setBlock(ground, Blocks.SCULK.defaultBlockState(), Block.UPDATE_CLIENTS);
                if ((dx + dz) % 3 == 0) {
                    level.sendParticles(ParticleTypes.SCULK_SOUL, ground.getX() + 0.5, ground.getY() + 1.1,
                            ground.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.02);
                }
            }
        }

        // Everyone on it goes up.
        AABB area = new AABB(center).inflate(RADIUS, 4, RADIUS);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> isEnemy(warden, e))) {
            double dx = target.getX() - warden.getX();
            double dz = target.getZ() - warden.getZ();
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue;
            target.setDeltaMovement(0, LAUNCH_SPEED, 0);
            target.resetFallDistance();
            KitUtil.syncMotion(target);
            THROWN.put(target, new Thrown(warden, now));
        }
        save(); // straight away, in case the server crashes before it fades
        KitUtil.actionBar(warden, "Sculk Eruption!");
        return true;
    }

    private static boolean isEnemy(ServerPlayer warden, LivingEntity entity) {
        if (entity == warden || !entity.isAlive() || entity.isAlliedTo(warden)) return false;
        if (entity.getType() == EntityTypes.WARDEN || entity.getType() == EntityTypes.ARMOR_STAND) return false;
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        return !RtsControl.canCommand(entity, warden);
    }

    /**
     * The top solid block of the ground near this column, within 4 blocks up
     * or down: solid, with open space above, not a block entity and not
     * unbreakable. Null if there isn't one.
     */
    private static BlockPos surface(ServerLevel level, BlockPos column) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos pos = column.above(dy);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(Blocks.SCULK)) continue;
            BlockState above = level.getBlockState(pos.above());
            if (!above.getCollisionShape(level, pos.above()).isEmpty()) continue;
            if (state.getCollisionShape(level, pos).isEmpty()) continue;
            if (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) return null;
            return pos;
        }
        return null;
    }

    // --- Every tick ---------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        tickThrown(now);
        if (!SCULK.isEmpty()) {
            Iterator<Patch> it = SCULK.iterator();
            while (it.hasNext()) {
                Patch patch = it.next();
                if (now < patch.fadeAt()) continue;
                restore(patch);
                it.remove();
                dirty = true;
            }
            if (dirty) save();
        }
    }

    private static void tickThrown(int now) {
        Iterator<Map.Entry<LivingEntity, Thrown>> it = THROWN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Thrown> entry = it.next();
            LivingEntity target = entry.getKey();
            Thrown thrown = entry.getValue();
            if (target.isRemoved() || !target.isAlive() || !(target.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            int age = now - thrown.start;
            if (!thrown.slamming) {
                if (age >= APEX_TICKS) {
                    // The top of the throw: down hard.
                    thrown.slamming = true;
                    target.setDeltaMovement(0, SLAM_SPEED, 0);
                    KitUtil.syncMotion(target);
                    level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.5f, 0.6f);
                }
                continue;
            }
            // No ordinary fall damage on top of the slam.
            target.resetFallDistance();
            boolean landed = !level.noCollision(target, target.getBoundingBox().move(0, -0.2, 0));
            if (landed || age >= GIVE_UP_TICKS) {
                impact(level, target, thrown.warden, now);
                it.remove();
            } else {
                target.setDeltaMovement(0, SLAM_SPEED, 0); // keep driving down
                KitUtil.syncMotion(target);
            }
        }
    }

    private static void impact(ServerLevel level, LivingEntity target, ServerPlayer warden, int now) {
        // Sonic damage: armor, enchantments and Feather Falling don't reduce it.
        target.hurtServer(level, warden.damageSources().sonicBoom(warden), DAMAGE);
        ItemStack boots = target.getItemBySlot(EquipmentSlot.FEET);
        if (!boots.isEmpty() && boots.isDamageableItem()) {
            boots.hurtAndBreak(Math.max(1, Math.round(boots.getMaxDamage() * BOOT_WEAR)), target, EquipmentSlot.FEET);
        }
        if (target instanceof ServerPlayer) {
            Confusion.apply(target, STUN_TICKS, now);
        } else {
            KitUtil.effect(target, MobEffects.SLOWNESS, STUN_TICKS, 9);
            KitUtil.effect(target, MobEffects.WEAKNESS, STUN_TICKS, 4);
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 0.2, target.getZ(), 12, 0.6, 0.1, 0.6, 0.05);
        level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.4f);
    }

    /** Puts the original block back, if the sculk is still there or the spot was broken. */
    private static void restore(Patch patch) {
        BlockState now = patch.level().getBlockState(patch.pos());
        if (now.is(Blocks.SCULK) || now.isAir()) {
            patch.level().setBlock(patch.pos(), patch.original(), Block.UPDATE_ALL);
        }
    }
}
