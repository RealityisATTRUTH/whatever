package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.biome.Biomes;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Axolotl (TheJKSeal).
 *
 *   Passive          Water Breathing, Dolphin's Grace, instant respawn.
 *                    Regeneration II only in water or rain.
 *   Drying out       after 5 minutes dry, takes half a heart every 2 seconds until wet
 *   Play Dead        R, in water: 10s frozen, can't attack, mobs lose interest,
 *                    Regeneration III. 25 energy, 45s cooldown.
 *   Call the Pod     G, in water: 3 axolotls you can command in RTS.
 *                    30 energy, 30s cooldown.
 *   Hunting Partner  a teammate who kills something you hurt in the last 10s
 *                    gets Regeneration I for 5s and loses Mining Fatigue
 *   Allies           axolotls
 *   Home             Lush Caves: double energy regen
 *   Curse            dry everywhere, no Regeneration, drying runs underwater, no pod
 */
public final class AxolotlKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.AXOLOTL);

    private static final String PLAY_DEAD = "play_dead";
    private static final String POD = "call_the_pod";
    private static final String HUNTING_PARTNER = "hunting_partner";
    private static final int DRY_LIMIT = 6000;       // 5 minutes
    private static final int PLAY_DEAD_TICKS = 200;  // 10 seconds
    private static final int PARTNER_WINDOW = 200;   // a hit counts for 10 seconds

    private static final Map<UUID, Integer> DRY_TICKS = new HashMap<>();
    private static final KitUtil.Timers PLAYING_DEAD = new KitUtil.Timers();

    /** Victim UUID -> the axolotl who last hurt it, and when. */
    private record Hit(UUID axolotl, int tick) {}
    private static final Map<UUID, Hit> RECENT_HITS = new HashMap<>();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        boolean cursed = PlayerMob.isCursed(player);
        boolean wet = !cursed && player.isInWaterOrRain();

        KitUtil.passive(player, MobEffects.WATER_BREATHING, 0, true);
        KitUtil.passive(player, MobEffects.DOLPHINS_GRACE, 0, true);
        KitUtil.passive(player, MobEffects.REGENERATION, 1, wet);

        // Drying out. The curse keeps the timer running even underwater.
        UUID id = player.getUUID();
        int dry = wet ? 0 : DRY_TICKS.getOrDefault(id, 0) + 1;
        DRY_TICKS.put(id, dry);
        if (dry > DRY_LIMIT && dry % 40 == 0) {
            player.hurtServer(player.level(), player.damageSources().dryOut(), 1.0f);
        }
        if (dry == DRY_LIMIT) KitUtil.actionBar(player, "You're drying out. Get wet!");

        // Play Dead.
        boolean dead = PLAYING_DEAD.active(player);
        KitUtil.modifier(player, Attributes.MOVEMENT_SPEED, "play_dead_still", -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, dead);
        KitUtil.modifier(player, Attributes.JUMP_STRENGTH, "play_dead_jump", -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, dead);
        if (dead) {
            KitUtil.calmAllies(player, 32, mob -> true); // every mob loses interest
        }

        KitUtil.calmAllies(player, 32, ALLIES);

        if (player.tickCount % 200 == 0) cleanupHits(player);
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.passive(player, MobEffects.WATER_BREATHING, 0, false);
        KitUtil.passive(player, MobEffects.DOLPHINS_GRACE, 0, false);
        KitUtil.passive(player, MobEffects.REGENERATION, 1, false);
        KitUtil.modifier(player, Attributes.MOVEMENT_SPEED, "play_dead_still", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        KitUtil.modifier(player, Attributes.JUMP_STRENGTH, "play_dead_jump", 0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        forget(player);
    }

    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        return KitUtil.doubledAtHome(KitUtil.inBiome(player, Biomes.LUSH_CAVES), type);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot == ABILITY_1) {
            if (!player.isInWater()) {
                KitUtil.actionBar(player, "You can only Play Dead in water.");
                return;
            }
            if (!KitUtil.tryUse(player, PLAY_DEAD, "Play Dead", 25, 900)) return;
            PLAYING_DEAD.start(player, PLAY_DEAD_TICKS);
            KitUtil.effect(player, MobEffects.REGENERATION, PLAY_DEAD_TICKS, 2);
            player.level().playSound(null, player.blockPosition(), SoundEvents.AXOLOTL_DEATH, SoundSource.PLAYERS, 0.8f, 1.1f);
            KitUtil.actionBar(player, "Playing dead for 10 seconds.");
        } else if (slot == ABILITY_2) {
            if (PlayerMob.isCursed(player)) {
                KitUtil.actionBar(player, "You can't call your pod while cursed.");
                return;
            }
            if (!player.isInWater()) {
                KitUtil.actionBar(player, "You can only call your pod in water.");
                return;
            }
            if (!KitUtil.tryUse(player, POD, "Call the Pod", 30, 600)) return;
            for (int i = 0; i < 3; i++) {
                double angle = (Math.PI * 2 / 3) * i;
                KitUtil.summon(player, "minecraft:axolotl",
                        player.getX() + Math.cos(angle), player.getY(), player.getZ() + Math.sin(angle), "");
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.AXOLOTL_SPLASH, SoundSource.PLAYERS, 1.0f, 1.0f);
            KitUtil.actionBar(player, "Your pod is here.");
        } else {
            KitUtil.actionBar(player, "The Axolotl has no third ability.");
        }
    }

    @Override
    public boolean allowAttack(ServerPlayer player, LivingEntity victim, DamageSource source) {
        return !PLAYING_DEAD.active(player); // no attacking while playing dead
    }

    @Override
    public void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        RECENT_HITS.put(victim.getUUID(), new Hit(player.getUUID(), KitUtil.now(player)));
    }

    @Override
    public boolean instantRespawn() {
        return true;
    }

    /** Hunting Partner. Registered by KitEvents for every death. */
    static void onDeath(LivingEntity victim, DamageSource source) {
        Hit hit = RECENT_HITS.remove(victim.getUUID());
        if (hit == null) return;
        if (!(source.getEntity() instanceof ServerPlayer killer)) return;
        if (killer.getUUID().equals(hit.axolotl())) return; // the Axolotl's own kill doesn't count
        if (KitUtil.now(killer) - hit.tick() > PARTNER_WINDOW) return;

        ServerPlayer axolotl = killer.level().getServer().getPlayerList().getPlayer(hit.axolotl());
        if (axolotl == null || !PlayerMob.has(axolotl, MobType.AXOLOTL)) return;
        if (!MobAbilities.isEnabled(axolotl, HUNTING_PARTNER)) return;
        if (!killer.isAlliedTo(axolotl)) return;

        KitUtil.effect(killer, MobEffects.REGENERATION, 100, 0);
        killer.removeEffect(MobEffects.MINING_FATIGUE);
    }

    private static void cleanupHits(ServerPlayer player) {
        int now = KitUtil.now(player);
        RECENT_HITS.values().removeIf(hit -> now - hit.tick() > PARTNER_WINDOW);
    }

    static void forget(ServerPlayer player) {
        DRY_TICKS.remove(player.getUUID());
        PLAYING_DEAD.forget(player.getUUID());
    }

    // --- Ultimate: Tidal Surge ---------------------------------------------------------------
    /**
     * You and teammates within 10 blocks get Regeneration III and Resistance I
     * for 10s. Enemies within 10 blocks are washed back and slowed for 8s.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Tidal Surge";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        ServerLevel level = player.level();
        for (ServerPlayer friend : UltimateFx.allies(player, 10)) {
            KitUtil.effect(friend, MobEffects.REGENERATION, 200, 2);
            KitUtil.effect(friend, MobEffects.RESISTANCE, 200, 0);
        }
        for (LivingEntity enemy : UltimateFx.enemies(player, 10)) {
            UltimateFx.throwFrom(enemy, player.position(), 1.0, 0.4);
            KitUtil.effect(enemy, MobEffects.SLOWNESS, 160, 1);
        }
        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1, player.getZ(), 150, 4, 1, 4, 0.3);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.5f, 0.8f);
        KitUtil.actionBar(player, "Tidal Surge!");
        return true;
    }
}
