package com.reality.mobcraft.mob;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.reality.mobcraft.MobCraft;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who is which mob, for everyone, online or not. Saved in the world folder as
 * mobcraft_roster.json, one line per player: their uuid, name and mob.
 *
 * A player's mob lives on the player, so it can only be read while they're online.
 * The roster remembers it while they're away, for things that care who has a mob
 * even when that player is offline, like the Piglin Barter Shop not selling ender
 * pearls while anyone is the Enderman.
 *
 * Updated whenever a mob is set or removed, and when a player joins.
 */
public final class MobRoster {

    private static final String FILE = "mobcraft_roster.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One player's line in the file. */
    public record Entry(String name, String mob) {}

    private static final Map<String, Entry> ROSTER = new LinkedHashMap<>();
    private static Path file;

    private MobRoster() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            file = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
            load();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ROSTER.clear();
            file = null;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // A mob freed with /freemob while its player was away: they join with no mob.
            Entry line = ROSTER.get(handler.player.getStringUUID());
            if (line != null && FREED.equals(line.mob()) && PlayerMob.get(handler.player) != null) {
                MobTicker.assign(handler.player, null);
                handler.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Your mob was given up while you were away, so someone else can have it."));
            }
            update(handler.player);
        });
    }

    /** Records the player's current mob, or removes them if they have none. Server only. */
    public static void update(Player player) {
        if (!(player instanceof ServerPlayer) || file == null) return;
        String uuid = player.getStringUUID();
        MobType type = PlayerMob.get(player);
        Entry now = type == null ? null : new Entry(player.getName().getString(), type.id());
        Entry before = ROSTER.get(uuid);
        if (now == null ? before == null : now.equals(before)) return;
        if (now == null) ROSTER.remove(uuid);
        else ROSTER.put(uuid, now);
        save();
    }

    /** The roster's mark for a player whose mob was freed while they were offline. */
    private static final String FREED = "none";

    /**
     * Frees a mob from whoever has it. An online player loses it now; an offline one is marked
     * and loses it when they next join. Returns that player's name, or null if nobody had it.
     */
    public static String free(net.minecraft.server.MinecraftServer server, String mobId) {
        for (Map.Entry<String, Entry> e : ROSTER.entrySet()) {
            if (!e.getValue().mob().equals(mobId)) continue;
            String name = e.getValue().name();
            ServerPlayer online = server.getPlayerList().getPlayer(java.util.UUID.fromString(e.getKey()));
            if (online != null) MobTicker.assign(online, null);
            else {
                e.setValue(new Entry(name, FREED));
                save();
            }
            return name;
        }
        return null;
    }

    /** Someone other than this player who is this mob, online or not, or null if the mob is free. */
    public static Entry holder(String mobId, String exceptUuid) {
        for (Map.Entry<String, Entry> e : ROSTER.entrySet()) {
            if (!e.getKey().equals(exceptUuid) && e.getValue().mob().equals(mobId)) return e.getValue();
        }
        return null;
    }

    /** Everyone who has a mob, online or not, by uuid. */
    public static Map<String, Entry> all() {
        return ROSTER;
    }

    private static void load() {
        ROSTER.clear();
        if (file == null || !Files.exists(file)) return;
        try {
            Map<String, Entry> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Entry>>() {}.getType());
            if (read != null) ROSTER.putAll(read);
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't read {}", FILE, e);
        }
    }

    private static void save() {
        if (file == null) return;
        try {
            Files.writeString(file, GSON.toJson(ROSTER), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }
}
