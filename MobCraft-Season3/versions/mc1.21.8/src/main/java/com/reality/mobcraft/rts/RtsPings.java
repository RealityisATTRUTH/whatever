package com.reality.mobcraft.rts;

import com.reality.mobcraft.net.PingPayload;
import com.reality.mobcraft.net.TeamPingPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of RTS pings. A ping goes to everyone on the pinger's team,
 * the pinger included. Without a team, only the pinger sees it. One ping a
 * second at most, and only near the pinger, so nobody can spam or spy.
 */
public final class RtsPings {

    private static final int COOLDOWN_TICKS = 20;
    private static final double MAX_RANGE = 160.0;

    private static final Map<UUID, Integer> LAST_PING = new HashMap<>();

    private RtsPings() {}

    public static void ping(ServerPlayer player, PingPayload ping) {
        int now = player.level().getServer().getTickCount();
        Integer last = LAST_PING.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) return;
        double dx = ping.x() - player.getX();
        double dz = ping.z() - player.getZ();
        if (dx * dx + dz * dz > MAX_RANGE * MAX_RANGE) return;
        LAST_PING.put(player.getUUID(), now);

        TeamPingPayload message = new TeamPingPayload(player.getStringUUID(), player.getName().getString(),
                ping.x(), ping.y(), ping.z());
        PlayerTeam team = player.getTeam();
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            boolean sameTeam = other == player || (team != null && other.getTeam() == team);
            if (sameTeam && other.level() == player.level()) ServerPlayNetworking.send(other, message);
        }
    }

    public static void forget(ServerPlayer player) {
        LAST_PING.remove(player.getUUID());
    }
}
