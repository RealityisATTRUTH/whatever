// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reality.hafnium.Hafnium;
import com.reality.hafnium.social.Social;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Searching Sketchfab and downloading models from it.
 *
 * Anyone can search. Downloading needs the player's own Sketchfab account, as
 * Sketchfab's rules ask: the player signs in once in their browser, and the
 * sign-in lasts about a month. The sign-in is kept in config/hafnium/sketchfab.properties
 * on this computer only. It never goes to the Hafnium server.
 *
 * Search results are shown as text only, never with Sketchfab's pictures, so
 * nothing appears in the game before the safety check has seen it.
 */
public final class Sketchfab {

    public static final int PORT = 47821;
    public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/sketchfab";
    private static final String API = System.getProperty("hafnium.sketchfabApi", "https://api.sketchfab.com/v3");
    private static final String AUTHORIZE = System.getProperty("hafnium.sketchfabAuth", "https://sketchfab.com/oauth2/authorize/");
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hafnium").resolve("sketchfab.properties");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    /** The most faces a model can have before it isn't worth downloading. Bigger ones are simplified after. */
    public static final int MAX_FACES = 300_000;
    private static final long MAX_DOWNLOAD = 120L * 1024 * 1024;
    /** Licences Hafnium can use, the same list the server checks. */
    public static final List<String> LICENSES = List.of("cc0", "by", "by-sa", "by-nc", "by-nc-sa");

    // The same kind of word list the server uses, so results with these words are never listed.
    private static final Pattern BLOCKED = Pattern.compile("(^|[^a-z0-9])(nsfw|nude|nudes|nudity|naked|topless|sexy|sex|sexual|lewd|porn|porno|hentai|ecchi|"
            + "boob|boobs|tits|titty|breasts|booty|butt|thicc|bikini|lingerie|underwear|panties|bra|thong|stripper|fetish|bdsm|bondage|"
            + "kinky|erotic|seductive|onlyfans|18\\+|r18|rule34|futa|yiff|waifu|milf|gore|gory|nazi|swastika|kkk|weed|cocaine|meth|drugs)(?=$|[^a-z0-9])",
            Pattern.CASE_INSENSITIVE);

    private static String token;
    private static long expiresAt;
    private static boolean loaded;
    private static volatile ServerSocket waiting;

    /** One search result. `license` is a short form like "by-sa", with `licenseLabel` its full name. */
    public record Result(String uid, String name, String author, String authorUrl, String license, String licenseLabel,
                         String licenseUrl, int faces, boolean ageRestricted, boolean downloadable, String viewerUrl) {
        /** Why this model can't be imported, in plain words, or null if it can. */
        public String problem() {
            if (ageRestricted) return "Sketchfab marks it as adults only.";
            if (!downloadable) return "Its maker doesn't allow downloads.";
            if (license.isEmpty()) return null; // filled in once it's picked
            if (!LICENSES.contains(license)) return license.contains("nd")
                    ? "Its licence doesn't allow changes, and fitting it on a player changes it."
                    : "Its licence doesn't allow Hafnium to use it.";
            if (faces > MAX_FACES) return "It's far too detailed to wear.";
            return null;
        }
    }

    public record Page(List<Result> results, String next, int hidden) { }

    private Sketchfab() { }

    // --- Signing in ------------------------------------------------------------------------------

    public static synchronized boolean signedIn() {
        load();
        return token != null && System.currentTimeMillis() < expiresAt;
    }

    public static synchronized void signOut() {
        token = null;
        expiresAt = 0;
        save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.exists(FILE)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
            }
            token = p.getProperty("token");
            expiresAt = Long.parseLong(p.getProperty("expires", "0"));
        } catch (Exception e) {
            token = null;
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Properties p = new Properties();
            if (token != null) {
                p.setProperty("token", token);
                p.setProperty("expires", Long.toString(expiresAt));
            }
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Your Sketchfab sign-in for Hafnium. Delete this file to sign out.");
            }
        } catch (IOException e) {
            Hafnium.LOGGER.warn("Couldn't save the Sketchfab sign-in", e);
        }
    }

    /**
     * Gets ready for a sign-in and returns the Sketchfab page to open in the
     * browser. Sketchfab sends the browser back to a small page this computer
     * serves for a few minutes, which hands the sign-in to the game.
     * `done` runs on the game's thread with null when signed in, or a problem.
     */
    public static URI startSignIn(java.util.function.Consumer<String> done) throws IOException {
        String clientId = Social.sketchfabClientId;
        if (clientId == null) throw new IOException("This Hafnium server hasn't set up Sketchfab yet.");
        stopWaiting();
        ServerSocket server = new ServerSocket(PORT, 8, InetAddress.getLoopbackAddress());
        server.setSoTimeout(5 * 60 * 1000);
        waiting = server;
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        String state = HexFormat.of().formatHex(random);
        Thread t = new Thread(() -> serve(server, state, done), "Hafnium Sketchfab sign-in");
        t.setDaemon(true);
        t.start();
        return URI.create(AUTHORIZE + "?response_type=token&approval_prompt=auto&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT) + "&state=" + state);
    }

    public static void stopWaiting() {
        ServerSocket s = waiting;
        waiting = null;
        if (s != null) try { s.close(); } catch (IOException ignored) { }
    }

    private static void serve(ServerSocket server, String state, java.util.function.Consumer<String> done) {
        try (server) {
            while (!server.isClosed()) {
                try (Socket s = server.accept()) {
                    s.setSoTimeout(10_000);
                    String line = new String(readHead(s.getInputStream()), StandardCharsets.ISO_8859_1).split("\r\n", 2)[0];
                    String[] parts = line.split(" ");
                    if (parts.length < 2 || !parts[0].equals("GET")) { reply(s, 400, "text/plain", "Bad request"); continue; }
                    URI uri = URI.create(parts[1]);
                    Map<String, String> q = query(uri.getRawQuery());
                    if (uri.getPath().equals("/sketchfab")) {
                        if (q.containsKey("error")) {
                            reply(s, 200, "text/html", page("Sign-in cancelled", "You can go back to Minecraft."));
                            finish(done, "The Sketchfab sign-in was cancelled.");
                            return;
                        }
                        // The sign-in comes after a # in the address, which only the browser sees, so this page passes it on.
                        reply(s, 200, "text/html", "<!doctype html><meta charset=utf-8><title>Hafnium</title>"
                                + "<body style='font-family:sans-serif;background:#26262b;color:#eee;text-align:center;padding-top:15vh'>"
                                + "<h2 id=t>Signing in...</h2><p id=m></p><script>"
                                + "fetch('/sketchfab/done?'+location.hash.slice(1)).then(r=>r.text()).then(x=>{"
                                + "document.getElementById('t').textContent=x;"
                                + "document.getElementById('m').textContent='You can close this tab and go back to Minecraft.'})"
                                + "</script>");
                    } else if (uri.getPath().equals("/sketchfab/done")) {
                        String got = q.get("access_token");
                        if (got == null || !state.equals(q.get("state"))) {
                            reply(s, 200, "text/plain", "That sign-in didn't work. Try again from Minecraft.");
                            finish(done, "The Sketchfab sign-in didn't work. Try again.");
                            return;
                        }
                        long seconds = 2_592_000;
                        try { seconds = Long.parseLong(q.getOrDefault("expires_in", "2592000")); } catch (NumberFormatException ignored) { }
                        synchronized (Sketchfab.class) {
                            token = got;
                            expiresAt = System.currentTimeMillis() + seconds * 1000 - 60_000;
                            save();
                        }
                        reply(s, 200, "text/plain", "Signed in to Sketchfab.");
                        finish(done, null);
                        return;
                    } else {
                        reply(s, 404, "text/plain", "Not found");
                    }
                } catch (java.net.SocketTimeoutException e) {
                    finish(done, "The Sketchfab sign-in took too long. Try again.");
                    return;
                } catch (IOException e) {
                    if (server.isClosed()) return;
                }
            }
        } catch (IOException ignored) {
            // closed
        } finally {
            if (waiting == server) waiting = null;
        }
    }

    private static void finish(java.util.function.Consumer<String> done, String problem) {
        Minecraft.getInstance().execute(() -> done.accept(problem));
    }

    private static byte[] readHead(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int b, last4 = 0;
        while ((b = in.read()) >= 0 && out.size() < 16384) {
            out.write(b);
            last4 = (last4 << 8) | b;
            if (last4 == 0x0D0A0D0A) break;
        }
        return out.toByteArray();
    }

    private static void reply(Socket s, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " OK\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: " + b.length
                + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        OutputStream out = s.getOutputStream();
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(b);
        out.flush();
    }

    private static String page(String title, String text) {
        return "<!doctype html><meta charset=utf-8><title>Hafnium</title><body style='font-family:sans-serif;background:#26262b;color:#eee;"
                + "text-align:center;padding-top:15vh'><h2>" + title + "</h2><p>" + text + "</p>";
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) return out;
        for (String kv : raw.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0) out.put(URLDecoder.decode(kv.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(kv.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // --- Searching -------------------------------------------------------------------------------

    /** Searches downloadable models. `next` is the next page's address from an earlier page, or null for the first. */
    public static CompletableFuture<Page> search(String words, String next) {
        String url = next != null ? next : API + "/search?type=models&downloadable=true&count=24&q=" + enc(words.trim());
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject o = getJson(url, false);
                List<Result> out = new ArrayList<>();
                int hidden = 0;
                JsonArray results = o.has("results") ? o.getAsJsonArray("results") : new JsonArray();
                for (JsonElement e : results) {
                    Result r = result(e.getAsJsonObject());
                    if (r.ageRestricted() || blocked(r.name()) || blocked(tags(e.getAsJsonObject()))) { hidden++; continue; }
                    out.add(r);
                }
                String n = o.has("next") && !o.get("next").isJsonNull() ? o.get("next").getAsString() : null;
                if (n != null && !n.startsWith(API)) n = null; // only ever follow pages on Sketchfab itself
                return new Page(out, n, hidden);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    /** Looks one model up, for its full licence and details. */
    public static CompletableFuture<Result> details(String uid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return result(getJson(API + "/models/" + uid, false));
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    public static boolean blocked(String text) {
        return text != null && BLOCKED.matcher(text).find();
    }

    private static String tags(JsonObject o) {
        StringBuilder sb = new StringBuilder();
        if (o.has("tags") && o.get("tags").isJsonArray()) {
            for (JsonElement t : o.getAsJsonArray("tags")) {
                if (t.isJsonObject()) sb.append(str(t.getAsJsonObject(), "name")).append(' ').append(str(t.getAsJsonObject(), "slug")).append(' ');
            }
        }
        return sb.toString();
    }

    private static Result result(JsonObject o) {
        JsonObject user = o.has("user") && o.get("user").isJsonObject() ? o.getAsJsonObject("user") : new JsonObject();
        JsonObject lic = o.has("license") && o.get("license").isJsonObject() ? o.getAsJsonObject("license") : new JsonObject();
        String label = str(lic, "label");
        String slug = slug(str(lic, "slug"), label);
        String author = str(user, "displayName").isBlank() ? str(user, "username") : str(user, "displayName");
        int faces = o.has("faceCount") && o.get("faceCount").isJsonPrimitive() ? o.get("faceCount").getAsInt() : 0;
        return new Result(str(o, "uid"), str(o, "name"), author, str(user, "profileUrl"), slug, label,
                str(lic, "url").isBlank() ? str(lic, "uri") : str(lic, "url"), faces,
                o.has("isAgeRestricted") && o.get("isAgeRestricted").getAsBoolean(),
                !o.has("isDownloadable") || o.get("isDownloadable").getAsBoolean(), str(o, "viewerUrl"));
    }

    /** Sketchfab writes licences as "cc-by-sa", "by-sa" or only a full name. This gives "by-sa" for all of them. */
    static String slug(String slug, String label) {
        String s = slug.toLowerCase(Locale.ROOT).trim();
        if (!s.isEmpty()) return s.equals("cc0") ? s : s.replaceFirst("^cc-", "");
        String l = label.toLowerCase(Locale.ROOT);
        if (l.isEmpty()) return "";
        if (l.contains("cc0") || l.contains("public domain")) return "cc0";
        if (!l.contains("attribution")) return "other";
        return "by" + (l.contains("noncommercial") ? "-nc" : "") + (l.contains("noderiv") ? "-nd" : "") + (l.contains("sharealike") ? "-sa" : "");
    }

    // --- Downloading -----------------------------------------------------------------------------

    /** Downloads a model's glTF archive. Needs a sign-in. */
    public static CompletableFuture<byte[]> download(String uid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject o = getJson(API + "/models/" + uid + "/download", true);
                JsonObject gltf = o.has("gltf") ? o.getAsJsonObject("gltf") : null;
                if (gltf == null || str(gltf, "url").isBlank()) throw new IOException("Sketchfab doesn't offer that model as glTF.");
                if (gltf.has("size") && gltf.get("size").getAsLong() > MAX_DOWNLOAD) throw new IOException("That model's files are too big to download.");
                HttpResponse<InputStream> res = HTTP.send(HttpRequest.newBuilder(URI.create(str(gltf, "url"))).timeout(Duration.ofMinutes(5)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() != 200) throw new IOException("The download didn't work (" + res.statusCode() + ").");
                try (InputStream in = res.body()) {
                    byte[] data = in.readNBytes((int) MAX_DOWNLOAD + 1);
                    if (data.length > MAX_DOWNLOAD) throw new IOException("That model's files are too big to download.");
                    return data;
                }
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("The download was stopped.");
            }
        });
    }

    private static JsonObject getJson(String url, boolean auth) throws IOException {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
        if (auth) {
            String t;
            synchronized (Sketchfab.class) {
                t = signedIn() ? token : null;
            }
            if (t == null) throw new IOException("Sign in to Sketchfab first.");
            req.header("Authorization", "Bearer " + t);
        }
        try {
            HttpResponse<String> res = HTTP.send(req.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401 && auth) {
                signOut();
                throw new IOException("Your Sketchfab sign-in ran out. Sign in again.");
            }
            if (res.statusCode() == 404) throw new IOException("That model isn't on Sketchfab any more.");
            if (res.statusCode() == 429) throw new IOException("Sketchfab says to slow down. Try again in a minute.");
            if (res.statusCode() != 200) throw new IOException("Sketchfab replied with an error (" + res.statusCode() + ").");
            return JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Stopped.");
        } catch (IllegalStateException | com.google.gson.JsonParseException e) {
            throw new IOException("Sketchfab's reply couldn't be read.");
        } catch (java.net.ConnectException | java.net.http.HttpTimeoutException e) {
            throw new IOException("Sketchfab couldn't be reached.");
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() || !e.isJsonPrimitive() ? "" : e.getAsString();
    }
}
