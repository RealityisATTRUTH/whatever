package com.reality.mobcraft.mob.kit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Spell particles at a player's hands while they cast, the way real illagers show
 * theirs: two swirls of colored spell particles, one at each hand, every tick of the
 * cast. Everyone nearby sees them. The colors are the illagers' own.
 */
public final class SpellFx {

    /** The evoker's fangs spell. */
    public static final int FANGS = rgb(0.4f, 0.3f, 0.35f);
    /** The evoker's summon-vex spell. */
    public static final int SUMMON_VEX = rgb(0.7f, 0.7f, 0.8f);
    /** The illusioner's vanishing spell. */
    public static final int DISAPPEAR = rgb(0.3f, 0.3f, 0.8f);
    /** The illusioner's blindness spell. */
    public static final int BLINDNESS = rgb(0.1f, 0.1f, 0.2f);

    private record Cast(int until, int color) {}

    private static final Map<UUID, Cast> CASTS = new HashMap<>();

    private SpellFx() { }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SpellFx::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CASTS.clear());
    }

    /** Shows the spell at the player's hands for the given ticks. */
    public static void cast(ServerPlayer player, int ticks, int color) {
        CASTS.put(player.getUUID(), new Cast(KitUtil.now(player) + ticks, color));
    }

    public static void stop(ServerPlayer player) {
        CASTS.remove(player.getUUID());
    }

    /** A one-off puff of spell particles all round a spot, for a spell landing. */
    public static void burst(ServerPlayer player, double x, double y, double z, int color, int count) {
        player.level().sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, color), x, y, z, count, 0.3, 0.4, 0.3, 0.0);
    }

    private static void tick(MinecraftServer server) {
        if (CASTS.isEmpty()) return;
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Cast>> it = CASTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Cast> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.isAlive() || now >= e.getValue().until()) {
                it.remove();
                continue;
            }
            hands(player, e.getValue().color());
        }
    }

    /** Like SpellcasterIllager's: one particle by each hand, swaying a little side to side. */
    private static void hands(ServerPlayer player, int color) {
        float s = player.getScale();
        float f = player.yBodyRot * Mth.DEG_TO_RAD + Mth.cos(player.tickCount * 0.6662f) * 0.25f;
        double g = Mth.cos(f) * 0.45 * s, h = Mth.sin(f) * 0.45 * s;
        double y = player.getY() + 1.0 * s;
        var particle = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, color);
        player.level().sendParticles(particle, player.getX() + g, y, player.getZ() + h, 1, 0, 0, 0, 0);
        player.level().sendParticles(particle, player.getX() - g, y, player.getZ() - h, 1, 0, 0, 0, 0);
    }

    private static int rgb(float r, float g, float b) {
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }
}
