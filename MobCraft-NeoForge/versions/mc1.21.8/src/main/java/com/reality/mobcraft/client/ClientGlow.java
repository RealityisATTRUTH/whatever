package com.reality.mobcraft.client;

import com.reality.mobcraft.net.GlowPayload;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side record of which entities this player should see glowing, and
 * in which color. Read by the two glow mixins every frame.
 *
 * Only touched on the client thread: payloads are handed over with
 * client.execute, and rendering runs on that same thread.
 */
public final class ClientGlow {

    private static final class Entry {
        final int color;
        int ticksLeft; // -1 = until cleared

        Entry(int color, int ticksLeft) {
            this.color = color;
            this.ticksLeft = ticksLeft;
        }
    }

    private static final Map<Integer, Entry> GLOWS = new HashMap<>();

    /**
     * Glows decided by this client itself, like RTS mode outlining your own
     * mobs. Replaced as a whole each frame, and takes priority over server glows.
     */
    private static Map<Integer, Integer> local = Map.of();

    private ClientGlow() {}

    public static void apply(GlowPayload payload) {
        if (payload.color() == 0) {
            GLOWS.remove(payload.entityId());
        } else {
            GLOWS.put(payload.entityId(), new Entry(payload.color(), payload.ticks()));
        }
    }

    public static boolean isGlowing(int entityId) {
        return local.containsKey(entityId) || GLOWS.containsKey(entityId);
    }

    /** Replaces the client's own glow layer: entity id to color. */
    public static void setLocal(Map<Integer, Integer> glows) {
        local = glows;
    }

    public static void clearLocal() {
        local = Map.of();
    }

    /** The glow color for this entity, or null if it isn't glowing for us. */
    public static Integer colorFor(int entityId) {
        Integer own = local.get(entityId);
        if (own != null) return own;
        Entry entry = GLOWS.get(entityId);
        return entry == null ? null : entry.color;
    }

    /** Called every client tick to expire timed glows. */
    public static void tick() {
        Iterator<Entry> it = GLOWS.values().iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.ticksLeft < 0) continue;
            if (--entry.ticksLeft <= 0) it.remove();
        }
    }

    public static void clear() {
        GLOWS.clear();
        local = Map.of();
    }
}
