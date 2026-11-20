// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.host;

import com.reality.hafnium.Hafnium;
import com.reality.hafnium.HafniumConfig;
import com.reality.hafnium.social.Social;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.HttpUtil;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hosting the open world for friends.
 *
 * The world runs as a small server on this computer, the same way Open to LAN
 * does. Hafnium asks the router to let friends in, then tells the Hafnium
 * server the address. The Hafnium server gives the world a short code and only
 * hands the address to players who are allowed.
 *
 * This game checks every player who joins as well. A private world only lets
 * in Hafnium friends and players the Hafnium server said are coming. Blocked
 * players are always turned away.
 */
public final class Hosting {

    public enum State { OFF, STARTING, HOSTING, FAILED }

    public static volatile State state = State.OFF;
    public static volatile String code;
    public static volatile String problem;
    public static volatile int port;

    /** Players the Hafnium server said are allowed to come. */
    private static final Set<String> COMING = ConcurrentHashMap.newKeySet();
    /** Copies of the friend and block lists, which the world's own thread can read safely. */
    private static final Set<String> FRIENDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> BLOCKED = ConcurrentHashMap.newKeySet();
    private static volatile boolean isPublic;

    private Hosting() {}

    public static boolean canHost() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    /** Opens the world, asks the router for a port and registers with the Hafnium server. */
    public static void start() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            fail("Open a world first.");
            return;
        }
        if (Social.status != Social.Status.ONLINE) {
            fail("Hafnium isn't connected to its server right now.");
            return;
        }
        if (!server.isPublished()) {
            int p = HttpUtil.getAvailablePort();
            if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, p)) {
                fail("Minecraft couldn't open the world for other players.");
                return;
            }
        }
        port = server.getPort();
        isPublic = "public".equals(HafniumConfig.hostVisibility);
        String world = server.getWorldData().getLevelName();
        state = State.STARTING;
        problem = null;
        String override = System.getProperty("hafnium.hostAddress"); // for testing without a router
        CompletableFuture.supplyAsync(() -> override != null ? new Upnp.Result(override, null) : Upnp.open(port))
                .thenAccept(result -> mc.execute(() -> {
                    if (state != State.STARTING) return;
                    if (!result.ok()) {
                        fail(result.problem());
                        return;
                    }
                    Social.hostStart(world, HafniumConfig.hostVisibility, result.externalIp() + ":" + port);
                }));
    }

    /** The Hafnium server gave the world its code. */
    public static void started(String newCode) {
        code = newCode;
        state = State.HOSTING;
        Hafnium.LOGGER.info("Hosting with code {} on port {}", newCode, port);
    }

    public static void stop() {
        if (state == State.OFF) return;
        if (state == State.HOSTING || state == State.STARTING) Social.hostStop();
        state = State.OFF;
        code = null;
        problem = null;
        COMING.clear();
        CompletableFuture.runAsync(Upnp::close);
    }

    /** Changes who can join. While hosting, the world is registered again, which gives it a new code. */
    public static void setVisibility(String visibility) {
        HafniumConfig.hostVisibility = visibility;
        HafniumConfig.save();
        isPublic = "public".equals(visibility);
        if (state == State.HOSTING) {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server != null) {
                state = State.STARTING;
                start();
            }
        }
    }

    private static void fail(String why) {
        state = State.FAILED;
        problem = why;
        code = null;
    }

    public static void coming(String uuid) {
        COMING.add(uuid);
    }

    /** Keeps the copies of the friend and block lists fresh. Runs on the game's main thread. */
    public static void syncLists() {
        FRIENDS.clear();
        for (Social.Person p : Social.FRIENDS) FRIENDS.add(p.uuid);
        BLOCKED.clear();
        for (Social.Person p : Social.BLOCKED) BLOCKED.add(p.uuid);
    }

    /** Decides whether a joining player may stay. Runs on the world's own thread. */
    public static boolean lets(ServerPlayer player, MinecraftServer server) {
        if (state != State.HOSTING) return true;
        if (server.isSingleplayerOwner(player.nameAndId())) return true;
        String id = player.getUUID().toString().replace("-", "");
        if (BLOCKED.contains(id)) return false;
        return isPublic || FRIENDS.contains(id) || COMING.contains(id);
    }
}
