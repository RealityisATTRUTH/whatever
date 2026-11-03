package com.reality.mobcraft.glow;

import com.reality.mobcraft.net.GlowPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server-side API for client-side glowing.
 *
 * The glow is drawn only on the viewer's screen. Nobody else sees it, and the
 * entity itself is never changed, so it can't leak to other players.
 *
 * Uses: RTS selection glow, the Enderman's per-attacker purple glow, the
 * Evoker's bell glow seen by enemies, and anything else that needs one
 * player to see an outline the others don't.
 */
public final class MobGlow {

    public static final int FOREVER = -1;

    private MobGlow() {}

    /** Make target glow in color for viewer only. ticks = FOREVER to keep it until cleared. */
    public static void glow(ServerPlayer viewer, Entity target, int color, int ticks) {
        int rgb = color & 0xFFFFFF;
        if (rgb == 0) rgb = 0x000001; // 0 is reserved for "clear"
        ServerPlayNetworking.send(viewer, new GlowPayload(target.getId(), rgb, ticks));
    }

    /** Stop target glowing for viewer. */
    public static void clear(ServerPlayer viewer, Entity target) {
        ServerPlayNetworking.send(viewer, new GlowPayload(target.getId(), 0, 0));
    }
}
