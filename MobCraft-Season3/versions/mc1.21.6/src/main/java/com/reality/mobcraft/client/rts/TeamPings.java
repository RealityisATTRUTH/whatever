package com.reality.mobcraft.client.rts;

import com.reality.mobcraft.net.TeamPingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Pings from your team. Each one is a beam of particles in the pinger's color
 * for 10 seconds, with a chime when it arrives. They show in normal view too,
 * and RtsScreen marks them on screen and on the minimap with the pinger's name.
 */
public final class TeamPings {

    private static final int LIFETIME_TICKS = 200; // 10 seconds
    private static final int MAX_PINGS = 12;

    public static final class Ping {
        public final String fromName;
        public final int color;
        public final double x, y, z;
        int ticksLeft = LIFETIME_TICKS;

        Ping(TeamPingPayload payload) {
            this.fromName = payload.fromName();
            this.color = AllyColors.of(payload.fromUuid());
            this.x = payload.x();
            this.y = payload.y();
            this.z = payload.z();
        }

        /** Blinks in its last two seconds. */
        public boolean visible() {
            return ticksLeft > 40 || (ticksLeft / 5) % 2 == 0;
        }
    }

    private static final List<Ping> PINGS = new ArrayList<>();

    private TeamPings() {}

    public static List<Ping> all() {
        return PINGS;
    }

    public static void receive(TeamPingPayload payload) {
        if (PINGS.size() >= MAX_PINGS) PINGS.remove(0);
        PINGS.add(new Ping(payload));
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
    }

    /** Called every client tick. */
    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) {
            PINGS.clear();
            return;
        }
        Iterator<Ping> it = PINGS.iterator();
        while (it.hasNext()) {
            Ping ping = it.next();
            if (--ping.ticksLeft <= 0) {
                it.remove();
                continue;
            }
            if (ping.ticksLeft % 3 != 0) continue;
            DustParticleOptions dust = new DustParticleOptions(ping.color, 1.5f);
            for (int i = 0; i < 8; i++) {
                level.addAlwaysVisibleParticle(dust, ping.x, ping.y + 0.3 + i * 0.6, ping.z, 0, 0, 0);
            }
        }
    }
}
