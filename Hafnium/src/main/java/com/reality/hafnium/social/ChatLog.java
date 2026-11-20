// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.reality.hafnium.Hafnium;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat history with each friend, kept on this computer in
 * config/hafnium/chats so it's still there next time, in a folder for each
 * of your Minecraft accounts. The newest 200 messages per friend are kept.
 */
public final class ChatLog {

    /** One chat message. */
    public static final class Line {
        public String from;
        public String name;
        public String text;
        public long sentAt;
        /** True if it waited on the server while you were offline. */
        public boolean waited;

        public Line(String from, String name, String text, long sentAt, boolean waited) {
            this.from = from;
            this.name = name;
            this.text = text;
            this.sentAt = sentAt;
            this.waited = waited;
        }
    }

    private static final int KEEP = 200;
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("hafnium").resolve("chats");
    private static final Map<String, List<Line>> CHATS = new HashMap<>();
    private static final Map<String, Integer> UNREAD = new HashMap<>();

    private ChatLog() {}

    public static List<Line> get(String friend) {
        return CHATS.computeIfAbsent(me() + "/" + friend, k -> load(friend));
    }

    public static void add(String friend, Line line) {
        List<Line> lines = get(friend);
        lines.add(line);
        while (lines.size() > KEEP) lines.remove(0);
        save(friend, lines);
    }

    public static void markUnread(String friend) {
        UNREAD.merge(friend, 1, Integer::sum);
    }

    public static void markRead(String friend) {
        UNREAD.remove(friend);
    }

    public static int unread(String friend) {
        return UNREAD.getOrDefault(friend, 0);
    }

    public static int unreadTotal() {
        int total = 0;
        for (int n : UNREAD.values()) total += n;
        return total;
    }

    private static String me() {
        return Social.myUuid == null || Social.myUuid.isEmpty() ? "unknown" : Social.myUuid.replaceAll("[^0-9a-f]", "");
    }

    private static Path file(String friend) {
        return DIR.resolve(me()).resolve(friend.replaceAll("[^0-9a-f]", "") + ".json");
    }

    private static List<Line> load(String friend) {
        Path f = file(friend);
        if (!Files.exists(f)) return new ArrayList<>();
        try (Reader in = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            List<Line> lines = GSON.fromJson(in, new TypeToken<List<Line>>() { }.getType());
            return lines == null ? new ArrayList<>() : new ArrayList<>(lines);
        } catch (Exception e) {
            Hafnium.LOGGER.warn("Couldn't read the chat history in {}", f, e);
            return new ArrayList<>();
        }
    }

    private static void save(String friend, List<Line> lines) {
        try {
            Files.createDirectories(file(friend).getParent());
            try (Writer out = Files.newBufferedWriter(file(friend), StandardCharsets.UTF_8)) {
                GSON.toJson(lines, out);
            }
        } catch (Exception e) {
            Hafnium.LOGGER.warn("Couldn't save the chat history", e);
        }
    }
}
