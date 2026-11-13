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
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> update(handler.player));
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
