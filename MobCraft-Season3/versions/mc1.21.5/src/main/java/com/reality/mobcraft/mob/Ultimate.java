package com.reality.mobcraft.mob;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Ultimate abilities: one big move per mob, on its own key (U by default).
 *
 *   Charge    0 to 100, earned only by fighting other players: 2.5 per point
 *             of damage you deal to a player, 1.5 per point a player deals
 *             to you, and 25 for killing a player. Killing or fighting
 *             normal mobs gives nothing, and neither do teammates or players
 *             in creative or spectator. Kept through death.
 *   Use       only at 100, and not while cursed. It empties when it fires.
 *   Bar       a gold bar under the energy bar, for mobs that have one.
 *
 *   Refill    nothing charges it for 10 seconds after it fires, so an
 *             ultimate's own hits can't refill it.
 *
 * Every mob has one. Each kit's onUltimate says what it does, and the
 * update log lists them all.
 */
public final class Ultimate {

    /** The ability slot the client sends for the ultimate key. */
    public static final int SLOT = 3;
    public static final float MAX = 100.0f;
    private static final float PER_DAMAGE_DEALT = 2.5f;
    private static final float PER_DAMAGE_TAKEN = 1.5f;
    private static final float PER_KILL = 25.0f;
    private static final int NO_REFILL_TICKS = 200;
    /** When each player last fired, by server tick. */
    private static final java.util.Map<java.util.UUID, Integer> FIRED = new java.util.HashMap<>();

    private Ultimate() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, damage, blocked) -> {
            if (blocked || damage <= 0) return;
            if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim && worthCharge(victim, attacker)) {
                add(attacker, damage * PER_DAMAGE_DEALT);
            }
            if (victim instanceof ServerPlayer hurt && source.getEntity() instanceof LivingEntity from
                    && from != hurt && worthCharge(from, hurt)) {
                add(hurt, damage * PER_DAMAGE_TAKEN);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (source.getEntity() instanceof ServerPlayer killer && killer != victim && worthCharge(victim, killer)) {
                add(killer, PER_KILL);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Ultimate::tick);
    }

    /** Only fights with other players count: not mobs, teammates, or players in creative or spectator. */
    private static boolean worthCharge(LivingEntity other, ServerPlayer player) {
        return other instanceof Player p && !p.isCreative() && !p.isSpectator() && !p.isAlliedTo(player);
    }

    /** Once a second: players whose mob has an ultimate get a charge bar, others lose it. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MobType type = PlayerMob.get(player);
            boolean has = type != null && MobKits.get(type).hasUltimate();
            Float charge = player.getAttached(MobAttachments.ULTIMATE);
            if (has && charge == null) player.setAttached(MobAttachments.ULTIMATE, 0.0f);
            if (!has && charge != null) player.removeAttached(MobAttachments.ULTIMATE);
        }
    }

    public static float charge(Player player) {
        Float charge = player.getAttached(MobAttachments.ULTIMATE);
        return charge == null ? 0.0f : charge;
    }

    private static void add(ServerPlayer player, float amount) {
        MobType type = PlayerMob.get(player);
        if (type == null || !MobKits.get(type).hasUltimate()) return;
        Integer fired = FIRED.get(player.getUUID());
        if (fired != null && player.level().getServer().getTickCount() - fired < NO_REFILL_TICKS) return;
        float before = charge(player);
        if (before >= MAX) return;
        float after = Math.min(MAX, before + amount);
        player.setAttached(MobAttachments.ULTIMATE, after);
        if (after >= MAX) {
            player.sendSystemMessage(Component.literal(MobKits.get(type).ultimateName() + " is ready!")
                    .withStyle(ChatFormatting.GOLD), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
        }
    }

    /** The ultimate key was pressed. */
    public static void use(ServerPlayer player) {
        MobType type = PlayerMob.get(player);
        if (type == null) return;
        MobKit kit = MobKits.get(type);
        if (!kit.hasUltimate()) {
            player.sendSystemMessage(Component.literal("Your mob doesn't have an ultimate yet."), true);
            return;
        }
        if (PlayerMob.isCursed(player)) {
            player.sendSystemMessage(Component.literal("You can't use your ultimate while cursed."), true);
            return;
        }
        float charge = charge(player);
        if (charge < MAX) {
            player.sendSystemMessage(Component.literal(kit.ultimateName() + ": " + Math.round(charge)
                    + "% charged. Fight other players to fill it."), true);
            return;
        }
        if (kit.onUltimate(player)) {
            player.setAttached(MobAttachments.ULTIMATE, 0.0f);
            FIRED.put(player.getUUID(), player.level().getServer().getTickCount());
        }
    }
}
