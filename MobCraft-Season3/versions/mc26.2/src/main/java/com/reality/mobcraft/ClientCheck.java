package com.reality.mobcraft;

import com.reality.mobcraft.net.ModListPayload;
import com.reality.mobcraft.net.TamperPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Keeps players from getting around the mod's blindness and darkness.
 *
 *   MobCraft required   every player's game has to say hello with its mod list
 *                       within 10 seconds of joining, or they're kicked. A
 *                       game without MobCraft can't draw the mod's blindness,
 *                       so it isn't allowed in.
 *   Banned mods         anyone with a mod on the banned list is kicked, with
 *                       the mod named. The list is config/mobcraft-banned-mods.txt,
 *                       one mod id per line, and starts with the known
 *                       anti-blindness and anti-darkness mods.
 *   Tamper check        while you're blind, your game checks that the effect
 *                       it was given still counts. Mods that hide Blindness
 *                       or Darkness that way get caught even if they aren't
 *                       on the list, and the player is kicked.
 *
 * Only dedicated servers check. Singleplayer and LAN worlds are left alone.
 *
 * Like any check that runs on the player's own game, someone who rebuilds
 * MobCraft to lie could get past it. It stops ordinary downloaded mods.
 */
public final class ClientCheck {

    private static final int HELLO_TICKS = 200; // 10 seconds
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mobcraft-banned-mods.txt");
    private static final List<String> DEFAULT_BANNED = List.of(
            "noblind",              // No Blind
            "removewardeneffect",   // No Darkness Effect
            "no-darkness-effect",
            "nodarkness",
            "removedarkness",
            "rde",                  // Remove Darkness Effect
            "nobadeffects",
            // Hacked clients and cheat mods with flight, auto totem or crystal and anchor macros.
            // ESP and X-ray mods aren't listed: they're allowed.
            "meteor-client",
            "wurst",
            "aristois",
            "bleachhack",
            "liquidbounce",
            "thunderhack",
            "autototem",
            "auto-totem",
            "crystaloptimizer",
            "marlows-crystal-optimizer"
    );

    /** Players who haven't said hello yet -> the tick they joined. */
    private static final Map<UUID, Integer> WAITING = new HashMap<>();
    private static Set<String> banned = new TreeSet<>(DEFAULT_BANNED);

    private ClientCheck() {}

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ModListPayload.TYPE, ModListPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TamperPayload.TYPE, TamperPayload.CODEC);

        loadBannedList();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (isHost(server, handler.player)) return;
            WAITING.put(handler.player.getUUID(), server.getTickCount());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WAITING.remove(handler.player.getUUID()));

        ServerPlayNetworking.registerGlobalReceiver(ModListPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            WAITING.remove(player.getUUID());
            if (isHost(player.level().getServer(), player)) return;
            for (String id : payload.modIds()) {
                if (banned.contains(id.toLowerCase(Locale.ROOT))) {
                    kick(player, "The mod \"" + id + "\" isn't allowed on this server. Remove it to join.");
                    return;
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(TamperPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (isHost(player.level().getServer(), player)) return;
            kick(player, "A mod is hiding " + payload.what() + ". Remove it to play on this server.");
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (WAITING.isEmpty() || server.getTickCount() % 20 != 0) return;
            int now = server.getTickCount();
            for (UUID id : new ArrayList<>(WAITING.keySet())) {
                if (now - WAITING.get(id) < HELLO_TICKS) continue;
                WAITING.remove(id);
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) kick(player, "You need the MobCraft mod installed to play on this server.");
            }
        });
    }

    /** Only real servers check. A singleplayer or LAN world runs inside someone's own game. */
    private static boolean isHost(MinecraftServer server, ServerPlayer player) {
        return !server.isDedicatedServer();
    }

    private static void kick(ServerPlayer player, String reason) {
        MobCraft.LOGGER.info("Kicked {}: {}", player.getName().getString(), reason);
        player.connection.disconnect(Component.literal(reason));
    }

    /** Reads the banned list, writing the default one the first time. */
    private static void loadBannedList() {
        try {
            if (!Files.exists(FILE)) {
                List<String> lines = new ArrayList<>();
                lines.add("# Mods that aren't allowed on this server, one mod id per line.");
                lines.add("# A player with any of these is kicked when they join. Lines starting with # are ignored.");
                lines.add("# To allow a mod from the built-in list, write it with a ! in front, like !wurst.");
                lines.addAll(DEFAULT_BANNED);
                Files.write(FILE, lines);
            }
            Set<String> read = new TreeSet<>();
            Set<String> allowed = new TreeSet<>();
            for (String line : Files.readAllLines(FILE)) {
                String id = line.trim().toLowerCase(Locale.ROOT);
                if (id.isEmpty() || id.startsWith("#")) continue;
                if (id.startsWith("!")) {
                    allowed.add(id.substring(1).trim()); // "!modid" means never ban it
                } else {
                    read.add(id);
                }
            }
            // New mods on the built-in list get added to an older file, unless it says to allow them.
            List<String> added = new ArrayList<>();
            for (String id : DEFAULT_BANNED) {
                if (!read.contains(id) && !allowed.contains(id)) added.add(id);
            }
            if (!added.isEmpty()) {
                List<String> lines = new ArrayList<>(Files.readAllLines(FILE));
                lines.addAll(added);
                Files.write(FILE, lines);
                read.addAll(added);
            }
            read.removeAll(allowed);
            banned = read;
        } catch (IOException e) {
            MobCraft.LOGGER.warn("Couldn't read {}, using the default banned mods.", FILE, e);
        }
    }
}
