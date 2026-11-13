package com.reality.mobcraft.item;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.kit.KitUtil;
import com.reality.mobcraft.net.EssenceOrbPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Broken Totem: the Evoker's mob item. A totem of undying gone grey and cracked, with red
 * eyes and red fog leaking out of it. It drops when another player kills the Evoker (see
 * MobRelics).
 *
 * Right-click it to absorb it, like the other essences (ShulkerEssence.EssenceItem). It has
 * two uses, spent on either power. When both are used it crumbles away, and a red essence
 * orb shaped like a thrown wind charge floats up out of you and fades (client/EssenceOrbs).
 *
 *   B             swap places with the player you're looking at, up to 32 blocks away.
 *                 Anyone: enemies or teammates.
 *   Sneak + B     ready the chain. Your next melee hit throws a red chain onto whoever you
 *                 hit and pulls them to you. Sneak + B again puts it away without using it.
 *                 It goes away on its own after 30 seconds.
 *
 * The chain only comes from a melee weapon: a sword, axe, spear or trident. A mace counts
 * only without Density or Wind Burst. The chain is drawn by client/EvokerChainRenderer.
 *
 * Gone when you die or absorb a different essence. Not for the Evoker player.
 */
public final class EvokerEssence {

    public static final String EVOKER = "evoker";
    public static final int USES = 2;
    private static final double SWAP_RANGE = 32;
    private static final int READY_TICKS = 600;
    private static final int CHAIN_SHOWS = 35;
    private static final int RED = 0xB0161C;

    /** Players whose chain is ready, until this server tick. */
    private static final Map<UUID, Integer> READY_UNTIL = new HashMap<>();
    /** Players showing a chain, until this server tick. */
    private static final Map<UUID, Integer> CHAIN_UNTIL = new HashMap<>();

    private EvokerEssence() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damage, blocked) -> afterHit(victim, source, blocked));
        ServerTickEvents.END_SERVER_TICK.register(EvokerEssence::tick);
    }

    /** The Essence Power key with a Broken Totem absorbed. */
    public static void use(ServerPlayer player) {
        if (player.isShiftKeyDown()) {
            toggleChain(player);
        } else {
            swap(player);
        }
    }

    /** Called when the essence leaves the player (death, a different essence). */
    public static void forget(ServerPlayer player) {
        READY_UNTIL.remove(player.getUUID());
        player.removeAttached(MobAttachments.ESSENCE_USES);
    }

    private static int uses(ServerPlayer player) {
        Integer uses = player.getAttached(MobAttachments.ESSENCE_USES);
        return uses == null ? USES : uses;
    }

    /** Spends one use, and when none are left the totem crumbles away. */
    private static void spend(ServerPlayer player) {
        int left = uses(player) - 1;
        if (left > 0) {
            player.setAttached(MobAttachments.ESSENCE_USES, left);
            KitUtil.actionBar(player, "Broken Totem: " + left + (left == 1 ? " use" : " uses") + " left.");
            return;
        }
        player.removeAttached(MobAttachments.ESSENCE);
        forget(player);
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5f, 0.5f);
        level.sendParticles(new DustParticleOptions(RED, 1.4f), player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.7, 0.5, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.4f, 0.6f);
        // Its red essence floats up out of you and fades away (client/EssenceOrbs).
        EssenceOrbPayload orb = new EssenceOrbPayload(player.getX(), player.getY() + 1.1, player.getZ());
        ServerPlayNetworking.send(player, orb);
        for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
            if (watcher != player) ServerPlayNetworking.send(watcher, orb);
        }
        KitUtil.actionBar(player, "The Broken Totem crumbles away.");
    }

    // --- Swapping places -----------------------------------------------------------------------

    private static void swap(ServerPlayer player) {
        ServerPlayer other = lookedAt(player);
        if (other == null) {
            KitUtil.actionBar(player, "Look at a player within " + (int) SWAP_RANGE + " blocks to swap places.");
            return;
        }
        ServerLevel level = player.level();
        Vec3 mine = player.position(), theirs = other.position();
        fog(level, mine);
        fog(level, theirs);
        player.teleportTo(theirs.x, theirs.y, theirs.z);
        other.teleportTo(mine.x, mine.y, mine.z);
        player.resetFallDistance();
        other.resetFallDistance();
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 1.2f);
        level.playSound(null, other.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 1.2f);
        KitUtil.actionBar(other, player.getName().getString() + " swapped places with you.");
        spend(player);
    }

    /** The nearest player in your line of sight, along where you're looking. */
    private static ServerPlayer lookedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(SWAP_RANGE));
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer other : player.level().players()) {
            if (other == player || other.isSpectator() || !other.isAlive()) continue;
            AABB box = other.getBoundingBox().inflate(0.4);
            var hit = box.clip(eye, end);
            if (hit.isEmpty() && !box.contains(eye)) continue;
            double distance = hit.map(eye::distanceTo).orElse(0.0);
            if (distance < bestDistance && player.hasLineOfSight(other)) {
                best = other;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void fog(ServerLevel level, Vec3 at) {
        level.sendParticles(new DustParticleOptions(RED, 1.6f), at.x, at.y + 1, at.z, 40, 0.4, 0.8, 0.4, 0.02);
    }

    // --- The chain -------------------------------------------------------------------------------

    private static void toggleChain(ServerPlayer player) {
        int now = player.level().getServer().getTickCount();
        if (READY_UNTIL.remove(player.getUUID()) != null) {
            KitUtil.actionBar(player, "Chain put away.");
            return;
        }
        READY_UNTIL.put(player.getUUID(), now + READY_TICKS);
        player.level().playSound(null, player.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
        KitUtil.actionBar(player, "Chain ready: hit someone with a melee weapon to pull them in.");
    }

    /** A sword, axe, spear or trident, or a mace without Density or Wind Burst. */
    static boolean meleeWeapon(ItemStack held) {
        if (held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES) || held.is(ItemTags.SPEARS) || held.is(Items.TRIDENT)) return true;
        if (!held.is(Items.MACE)) return false;
        ItemEnchantments enchantments = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(Enchantments.DENSITY) || enchantment.is(Enchantments.WIND_BURST)) return false;
        }
        return true;
    }

    private static void afterHit(LivingEntity victim, DamageSource source, boolean blocked) {
        if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker) return;
        if (blocked || victim == attacker || !READY_UNTIL.containsKey(attacker.getUUID())) return;
        if (!EVOKER.equals(attacker.getAttached(MobAttachments.ESSENCE))) return;
        if (!meleeWeapon(attacker.getMainHandItem())) {
            KitUtil.actionBar(attacker, "The chain needs a sword, axe, spear or trident, or a mace without Density or Wind Burst.");
            return;
        }
        READY_UNTIL.remove(attacker.getUUID());
        pull(attacker, victim);
        int now = attacker.level().getServer().getTickCount();
        attacker.setAttached(MobAttachments.CHAIN_TARGET, victim.getId());
        CHAIN_UNTIL.put(attacker.getUUID(), now + CHAIN_SHOWS);
        ServerLevel level = attacker.level();
        level.playSound(null, victim.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, attacker.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.7f, 0.8f);
        spend(attacker);
    }

    /** Throws the victim toward the attacker, to land just in front of them. */
    private static void pull(ServerPlayer attacker, Entity victim) {
        Vec3 toward = attacker.position().subtract(victim.position());
        double flat = Math.sqrt(toward.x * toward.x + toward.z * toward.z);
        if (flat < 0.01) return;
        // Thrown up at 0.38 it's in the air about ten ticks, slowing by 9% a tick, so it travels
        // about 6.5 times its starting speed. Aim to land just over a block in front of you.
        double speed = Math.min(1.6, Math.max(0.2, (flat - 1.3) / 6.5));
        victim.setDeltaMovement(toward.x / flat * speed, 0.38, toward.z / flat * speed);
        victim.needsSync = true;
        KitUtil.syncMotion(victim);
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        READY_UNTIL.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) KitUtil.actionBar(player, "The chain slips away.");
            return true;
        });
        CHAIN_UNTIL.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) player.removeAttached(MobAttachments.CHAIN_TARGET);
            return true;
        });
    }
}
