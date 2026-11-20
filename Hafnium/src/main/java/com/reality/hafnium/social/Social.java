// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reality.hafnium.Hafnium;
import com.reality.hafnium.HafniumConfig;
import com.reality.hafnium.host.Hosting;
import com.reality.hafnium.cosmetics.Cosmetics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.services.ProfileResult;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.toasts.FriendToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hafnium's connection to the Hafnium server, for friends and chat.
 *
 * It signs in with the player's own Minecraft account. The server sends a
 * random challenge, and the game answers with two things Mojang has already
 * signed: the account's chat certificate and its profile with the current
 * name. The game also signs the challenge with the certificate's private key,
 * which never leaves this computer. The server checks Mojang's signatures
 * with Mojang's public keys, so it never has to call Mojang. The player's
 * Mojang token is never sent to the Hafnium server.
 *
 * Network messages arrive on a background thread. Every change to the lists
 * below is handed to the game's main thread first, so screens can read them
 * safely while drawing.
 */
public final class Social {

    public enum Status { OFF, CONNECTING, ONLINE, SIGN_IN_FAILED, NO_CONNECTION }

    /** A Hafnium friend, request or blocked player. */
    public static final class Person {
        public final String uuid;
        public String name;
        public boolean online;
        public long lastSeen;
        /** The world this friend is hosting, or null. */
        public String hostWorld;
        public String hostCode;

        Person(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    public static final List<Person> FRIENDS = new ArrayList<>();
    public static final List<Person> INCOMING = new ArrayList<>();
    public static final List<Person> OUTGOING = new ArrayList<>();
    public static final List<Person> BLOCKED = new ArrayList<>();
    /** Goes up every time anything changes, so screens know to refresh. */
    public static int version;
    public static Status status = Status.OFF;
    public static String myUuid;
    /** The Hafnium server's Sketchfab app id, or null if its owner hasn't set up Sketchfab importing. */
    public static String sketchfabClientId;
    public static String myName;
    /** The last thing the server refused, in plain words, for the friends screen. */
    public static String notice;
    /** Why the last sign-in failed, in plain words. */
    public static String signInProblem;
    /** The friend whose chat is open right now, so their messages don't pop up a toast. */
    public static String viewing;

    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Hafnium social");
        t.setDaemon(true);
        return t;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static WebSocket socket;
    private static CompletableFuture<?> sending = CompletableFuture.completedFuture(null);
    private static int attempt;
    private static boolean started;
    private static boolean signInFailed;

    private Social() {}

    // --- Connecting -------------------------------------------------------------

    /** Starts the connection once, when the title screen first opens. */
    public static void start() {
        if (started) return;
        started = true;
        connect();
    }

    public static void setEnabled(boolean on) {
        HafniumConfig.socialOn = on;
        HafniumConfig.save();
        if (on) {
            attempt = 0;
            signInFailed = false;
            started = true;
            connect();
        } else {
            WebSocket ws = socket;
            socket = null;
            if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "off");
            onMain(() -> {
                status = Status.OFF;
                clearLists();
            });
        }
    }

    private static void connect() {
        if (!HafniumConfig.socialOn || socket != null) return;
        onMain(() -> status = Status.CONNECTING);
        URI uri;
        try {
            uri = URI.create(HafniumConfig.socialServer);
        } catch (IllegalArgumentException e) {
            Hafnium.LOGGER.warn("The Hafnium server address isn't valid: {}", HafniumConfig.socialServer);
            onMain(() -> status = Status.NO_CONNECTION);
            return;
        }
        HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(uri, new Listener())
                .whenComplete((ws, error) -> {
                    if (error != null) {
                        Hafnium.LOGGER.info("Couldn't reach the Hafnium server: {}", error.toString());
                        onMain(() -> status = Status.NO_CONNECTION);
                        retryLater();
                        return;
                    }
                    socket = ws;
                    User user = Minecraft.getInstance().getUser();
                    JsonObject hello = new JsonObject();
                    hello.addProperty("type", "hello");
                    hello.addProperty("name", user.getName());
                    send(hello);
                });
    }

    /** Tries again after a while: 5 seconds, then longer, up to 5 minutes. */
    private static void retryLater() {
        socket = null;
        if (!HafniumConfig.socialOn) return;
        long wait = signInFailed ? 300 : Math.min(300, 5L << Math.min(attempt, 6));
        attempt++;
        TIMER.schedule(Social::connect, wait, TimeUnit.SECONDS);
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String text = partial.toString();
                partial.setLength(0);
                try {
                    handle(ws, JsonParser.parseString(text).getAsJsonObject());
                } catch (Exception e) {
                    Hafnium.LOGGER.warn("Bad message from the Hafnium server", e);
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            closed(ws);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            Hafnium.LOGGER.info("Hafnium server connection dropped: {}", error.toString());
            closed(ws);
        }

        private void closed(WebSocket ws) {
            if (socket != ws) return;
            onMain(() -> {
                if (status != Status.SIGN_IN_FAILED) status = Status.NO_CONNECTION;
                for (Person p : FRIENDS) p.online = false;
                version++;
            });
            retryLater();
        }
    }

    // --- Messages from the server ---------------------------------------------------------

    private static void handle(WebSocket ws, JsonObject m) {
        String type = str(m, "type");
        switch (type) {
            case "challenge" -> signIn(m.has("challenge") ? str(m, "challenge") : str(m, "serverId"));
            case "welcome" -> onMain(() -> {
                attempt = 0;
                signInFailed = false;
                myUuid = str(m, "uuid");
                myName = str(m, "name");
                sketchfabClientId = m.has("sketchfab") && !m.get("sketchfab").isJsonNull() && !str(m, "sketchfab").isBlank() ? str(m, "sketchfab") : null;
                status = Status.ONLINE;
                version++;
            });
            case "friends" -> onMain(() -> {
                fill(FRIENDS, m.getAsJsonArray("friends"));
                fill(INCOMING, m.getAsJsonArray("incoming"));
                fill(OUTGOING, m.getAsJsonArray("outgoing"));
                fill(BLOCKED, m.getAsJsonArray("blocked"));
                version++;
            });
            case "presence" -> onMain(() -> {
                Person p = find(FRIENDS, str(m, "uuid"));
                if (p == null) return;
                boolean online = m.get("online").getAsBoolean();
                if (online && !p.online) toast(p.uuid, Component.literal(p.name + " is online"));
                p.online = online;
                if (!online) p.lastSeen = System.currentTimeMillis();
                version++;
            });
            case "friend_request" -> onMain(() -> {
                if (find(INCOMING, str(m, "uuid")) == null) INCOMING.add(new Person(str(m, "uuid"), str(m, "name")));
                toast(str(m, "uuid"), Component.literal(str(m, "name") + " sent you a Hafnium friend request"));
                version++;
            });
            case "friend_added" -> onMain(() -> {
                String uuid = str(m, "uuid");
                INCOMING.removeIf(p -> p.uuid.equals(uuid));
                OUTGOING.removeIf(p -> p.uuid.equals(uuid));
                if (find(FRIENDS, uuid) == null) {
                    Person p = new Person(uuid, str(m, "name"));
                    p.online = m.has("online") && m.get("online").getAsBoolean();
                    FRIENDS.add(p);
                    FRIENDS.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                }
                toast(uuid, Component.literal("You're now Hafnium friends with " + str(m, "name")));
                version++;
            });
            case "friend_removed" -> onMain(() -> {
                FRIENDS.removeIf(p -> p.uuid.equals(str(m, "uuid")));
                version++;
            });
            case "message" -> onMain(() -> {
                String from = str(m, "from");
                ChatLog.Line line = new ChatLog.Line(from, str(m, "name"), str(m, "text"),
                        m.get("sentAt").getAsLong(), m.has("waited") && m.get("waited").getAsBoolean());
                ChatLog.add(from, line);
                if (!from.equals(viewing)) {
                    ChatLog.markUnread(from);
                    toast(from, Component.literal(str(m, "name") + ": ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(str(m, "text")).withStyle(ChatFormatting.WHITE)));
                }
                version++;
            });
            case "hosting" -> onMain(() -> {
                Hosting.started(str(m, "code"));
                version++;
            });
            case "host_update" -> onMain(() -> {
                Person p = find(FRIENDS, str(m, "uuid"));
                if (p == null) return;
                boolean wasHosting = p.hostWorld != null;
                setHosting(p, m.get("hosting"));
                if (!wasHosting && p.hostWorld != null) toast(p.uuid, Component.literal(p.name + " is hosting " + p.hostWorld));
                version++;
            });
            case "join_notice" -> Hosting.coming(str(m, "uuid"));
            case "equipped" -> onMain(() -> {
                Cosmetics.myCape = m.get("cape") == null || m.get("cape").isJsonNull() ? null : str(m, "cape");
                JsonElement skin = m.get("skin");
                JsonElement models = m.get("models");
                if (models != null && models.isJsonObject()) {
                    JsonObject mo = models.getAsJsonObject();
                    Cosmetics.myHeadModel = mo.has("head") && !mo.get("head").isJsonNull() ? mo.get("head").getAsString() : null;
                    Cosmetics.myBackModel = mo.has("back") && !mo.get("back").isJsonNull() ? mo.get("back").getAsString() : null;
                }
                if (skin != null) {
                    Cosmetics.mySkin = skin.isJsonNull() ? null : str(skin.getAsJsonObject(), "hash");
                    Cosmetics.mySkinSlim = !skin.isJsonNull() && "slim".equals(str(skin.getAsJsonObject(), "variant"));
                }
                if (m.has("admin")) Cosmetics.admin = m.get("admin").getAsBoolean();
                Cosmetics.version++;
            });
            case "wearing" -> {
                Map<String, String> capes = new java.util.HashMap<>();
                for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("capes").entrySet()) {
                    capes.put(e.getKey(), e.getValue().isJsonNull() ? null : e.getValue().getAsString());
                }
                Map<String, String> skins = new java.util.HashMap<>();
                if (m.has("skins")) {
                    for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("skins").entrySet()) {
                        if (e.getValue().isJsonNull()) continue;
                        JsonObject s = e.getValue().getAsJsonObject();
                        skins.put(e.getKey(), ("slim".equals(str(s, "variant")) ? "slim:" : "") + str(s, "hash"));
                    }
                }
                Map<String, String[]> models = new java.util.HashMap<>();
                if (m.has("models")) {
                    for (Map.Entry<String, JsonElement> e : m.getAsJsonObject("models").entrySet()) {
                        JsonObject mo = e.getValue().getAsJsonObject();
                        models.put(e.getKey(), new String[]{
                                mo.has("head") && !mo.get("head").isJsonNull() ? mo.get("head").getAsString() : null,
                                mo.has("back") && !mo.get("back").isJsonNull() ? mo.get("back").getAsString() : null});
                    }
                }
                onMain(() -> Cosmetics.onWearing(capes, skins, models));
            }
            case "gallery" -> {
                List<Cosmetics.Item> items = items(m.getAsJsonArray("items"));
                boolean more = m.get("more").getAsBoolean();
                int page = m.get("page").getAsInt();
                boolean skins = "skin".equals(str(m, "kind"));
                boolean models = "model".equals(str(m, "kind"));
                onMain(() -> {
                    if (models) {
                        Cosmetics.modelGallery = items;
                        Cosmetics.modelGalleryMore = more;
                        Cosmetics.modelGalleryPage = page;
                    } else if (skins) {
                        Cosmetics.skinGallery = items;
                        Cosmetics.skinGalleryMore = more;
                        Cosmetics.skinGalleryPage = page;
                    } else {
                        Cosmetics.gallery = items;
                        Cosmetics.galleryMore = more;
                        Cosmetics.galleryPage = page;
                    }
                    Cosmetics.version++;
                });
            }
            case "mine" -> {
                List<Cosmetics.Item> items = items(m.getAsJsonArray("items"));
                onMain(() -> {
                    Cosmetics.mine = items;
                    Cosmetics.version++;
                });
            }
            case "admin_reports" -> {
                List<Cosmetics.Item> items = items(m.getAsJsonArray("items"));
                onMain(() -> {
                    Cosmetics.reports = items;
                    Cosmetics.version++;
                });
            }
            case "uploaded" -> {
                onMain(() -> {
                    Cosmetics.lastUploaded = str(m, "hash");
                    Cosmetics.version++;
                });
                askGallery(str(m, "kind").isEmpty() ? "cape" : str(m, "kind"), 0);
                askMine();
            }
            case "join_info" -> onMain(() -> connectTo(str(m, "address"), str(m, "host"), str(m, "world")));
            case "ok" -> {
                if ("friend_request".equals(str(m, "for"))) send(simple("friends"));
                String forWhat = str(m, "for");
                if (forWhat.equals("delete_cosmetic") || forWhat.equals("admin_unhide") || forWhat.equals("report")) {
                    askGallery("cape", Cosmetics.galleryPage);
                    askGallery("skin", Cosmetics.skinGalleryPage);
                    askGallery("model", Cosmetics.modelGalleryPage);
                    askMine();
                    if (Cosmetics.admin) askReports();
                }
                onMain(() -> {
                    notice = null;
                    version++;
                });
            }
            case "error" -> {
                String reason = str(m, "reason");
                if ("login_failed".equals(reason)) {
                    signInFailed = true;
                    String detail = str(m, "detail");
                    Hafnium.LOGGER.info("The Hafnium server refused the sign-in: {}", detail);
                    onMain(() -> {
                        status = Status.SIGN_IN_FAILED;
                        signInProblem = explainSignIn(detail);
                    });
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "sign-in failed");
                    return;
                }
                onMain(() -> {
                    notice = explain(reason);
                    version++;
                });
            }
            default -> { }
        }
    }

    /** Must match the server's CHALLENGE_PREFIX, so a signature for Hafnium can't be reused anywhere else. */
    private static final String CHALLENGE_PREFIX = "hafnium-login:";

    /** Answers the server's challenge with the Mojang-signed certificate and profile, and signs it. */
    private static void signIn(String challenge) {
        CompletableFuture.runAsync(() -> {
            JsonObject login = simple("login");
            if (Boolean.getBoolean("hafnium.devLogin")) {
                // Only for testing against a server on your own computer that allows test logins.
                login.addProperty("dev", true);
                login.addProperty("uuid", Minecraft.getInstance().getUser().getProfileId().toString().replace("-", ""));
            } else {
                try {
                    fillSignIn(login, challenge);
                } catch (Exception e) {
                    Hafnium.LOGGER.info("Couldn't sign in to the Hafnium server: {}", e.toString());
                    signInFailed = true;
                    String problem = e.getMessage() != null && e.getMessage().startsWith("hafnium:")
                            ? e.getMessage().substring(8) : "Couldn't reach Mojang to prove your account.";
                    onMain(() -> {
                        status = Status.SIGN_IN_FAILED;
                        signInProblem = problem;
                    });
                    WebSocket ws = socket;
                    if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "sign-in failed");
                    return;
                }
            }
            send(login);
        }, TIMER);
    }

    private static void fillSignIn(JsonObject login, String challenge) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        Optional<ProfileKeyPair> pair = Optional.empty();
        try {
            pair = mc.getProfileKeyPairManager().prepareKeyPair().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Hafnium.LOGGER.info("No chat certificate for Hafnium's sign-in: {}", e.toString());
        }
        if (pair.isEmpty()) {
            // No chat certificate: UK accounts without Microsoft's age check, or mods that turn chat signing off.
            joinSignIn(login, challenge);
            return;
        }
        ProfilePublicKey.Data cert = pair.get().publicKey().data();
        ProfileResult result = mc.services().sessionService().fetchProfile(user.getProfileId(), true);
        if (result == null) throw new IllegalStateException("hafnium:Mojang didn't send your profile.");
        Property textures = result.profile().properties().get("textures").stream()
                .filter(Property::hasSignature).findFirst()
                .orElseThrow(() -> new IllegalStateException("hafnium:Mojang's profile had no signature."));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.get().privateKey());
        signer.update((CHALLENGE_PREFIX + challenge).getBytes(StandardCharsets.UTF_8));
        Base64.Encoder b64 = Base64.getEncoder();
        login.addProperty("uuid", user.getProfileId().toString().replace("-", ""));
        login.addProperty("publicKey", b64.encodeToString(cert.key().getEncoded()));
        login.addProperty("expiresAt", cert.expiresAt().toEpochMilli());
        login.addProperty("keySignature", b64.encodeToString(cert.keySignature()));
        login.addProperty("signature", b64.encodeToString(signer.sign()));
        JsonObject t = new JsonObject();
        t.addProperty("value", textures.value());
        t.addProperty("signature", textures.signature());
        login.add("textures", t);
    }

    /**
     * Signs in the way joining any Minecraft server works: the game tells Mojang it's
     * joining a server named after the challenge, and the Hafnium server asks Mojang
     * whether it did. Works without a chat certificate. The Mojang login stays here.
     */
    private static void joinSignIn(JsonObject login, String challenge) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        String serverId = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1")
                .digest((CHALLENGE_PREFIX + challenge).getBytes(StandardCharsets.UTF_8)));
        try {
            mc.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
        } catch (com.mojang.authlib.exceptions.InsufficientPrivilegesException e) {
            throw new IllegalStateException("hafnium:Your Microsoft account isn't allowed to play online right now, so Hafnium can't prove it's you."
                    + " In the UK this can mean Microsoft's age check isn't done yet.");
        } catch (com.mojang.authlib.exceptions.UserBannedException e) {
            throw new IllegalStateException("hafnium:This account is banned from playing online.");
        } catch (com.mojang.authlib.exceptions.AuthenticationUnavailableException e) {
            throw new IllegalStateException("hafnium:Mojang's sign-in isn't answering. Try again later.");
        } catch (com.mojang.authlib.exceptions.AuthenticationException e) {
            throw new IllegalStateException("hafnium:Mojang refused the sign-in" + (e.getMessage() == null ? "." : ": " + e.getMessage()));
        }
        login.addProperty("method", "join");
        login.addProperty("uuid", user.getProfileId().toString().replace("-", ""));
    }

    private static String explainSignIn(String detail) {
        return switch (detail) {
            case "not_joined" -> "Mojang didn't confirm the sign-in. Try again.";
            case "no_verifier" -> "Your account has no chat certificate, and the Hafnium server's sign-in helper isn't running. Ask its owner to start it.";
            case "verifier_failed" -> "The sign-in helper couldn't reach Mojang. Try again later.";
            case "profile_mismatch" -> "Mojang's answer was for a different account.";
            case "certificate_expired" -> "Your chat certificate expired. Restart the game.";
            case "profile_old" -> "Your computer's clock may be wrong.";
            case "certificate_invalid", "profile_invalid" -> "Mojang's keys may have changed. The server needs an update.";
            default -> "The server refused the sign-in (" + detail + ").";
        };
    }

    // --- Things the player does ---------------------------------------------------------

    public static void addFriend(String name) {
        JsonObject m = simple("friend_request");
        m.addProperty("name", name.trim());
        send(m);
    }

    public static void accept(String uuid) { sendFor("friend_accept", uuid); }

    public static void decline(String uuid) {
        sendFor("friend_decline", uuid);
        INCOMING.removeIf(p -> p.uuid.equals(uuid));
        version++;
    }

    public static void remove(String uuid) {
        sendFor("friend_remove", uuid);
        FRIENDS.removeIf(p -> p.uuid.equals(uuid));
        version++;
    }

    public static void block(String uuid) {
        sendFor("block", uuid);
        Person p = find(FRIENDS, uuid);
        if (p == null) p = find(INCOMING, uuid);
        FRIENDS.removeIf(x -> x.uuid.equals(uuid));
        INCOMING.removeIf(x -> x.uuid.equals(uuid));
        if (p != null && find(BLOCKED, uuid) == null) BLOCKED.add(p);
        version++;
    }

    public static void unblock(String uuid) {
        sendFor("unblock", uuid);
        BLOCKED.removeIf(x -> x.uuid.equals(uuid));
        version++;
    }

    /** Sends a chat message and adds it to the chat straight away. */
    public static void message(String toUuid, String text) {
        String clean = text.strip();
        if (clean.isEmpty()) return;
        if (clean.length() > 500) clean = clean.substring(0, 500);
        JsonObject m = simple("message");
        m.addProperty("to", toUuid);
        m.addProperty("text", clean);
        send(m);
        ChatLog.add(toUuid, new ChatLog.Line(myUuid, myName, clean, System.currentTimeMillis(), false));
        version++;
    }

    // --- Hosting -------------------------------------------------------------------------

    public static void hostStart(String world, String visibility, String address) {
        JsonObject m = simple("host_start");
        m.addProperty("world", world);
        m.addProperty("visibility", visibility);
        m.addProperty("address", address);
        send(m);
    }

    public static void hostStop() {
        send(simple("host_stop"));
    }

    /** Asks to join a world by its code. */
    public static void joinCode(String code) {
        JsonObject m = simple("join");
        m.addProperty("code", code.trim().toLowerCase());
        send(m);
    }

    /** Asks to join the world a friend is hosting. */
    public static void joinFriend(String uuid) {
        sendFor("join", uuid);
    }

    // --- Capes -------------------------------------------------------------------------------

    public static void askWearing(List<String> uuids) {
        JsonObject m = simple("wearing");
        JsonArray a = new JsonArray();
        uuids.forEach(a::add);
        m.add("uuids", a);
        send(m);
    }

    public static void uploadCape(String name, String pngBase64, String credit, String creditUrl) {
        JsonObject m = simple("upload_cape");
        m.addProperty("name", name);
        m.addProperty("png", pngBase64);
        addCredit(m, credit, creditUrl);
        send(m);
    }

    private static void addCredit(JsonObject m, String credit, String creditUrl) {
        if (credit != null && !credit.isBlank()) m.addProperty("credit", credit.trim());
        if (creditUrl != null && !creditUrl.isBlank()) m.addProperty("creditUrl", creditUrl.trim());
    }

    public static void askGallery(String kind, int page) {
        JsonObject m = simple("gallery");
        m.addProperty("kind", kind);
        m.addProperty("page", page);
        send(m);
    }

    public static void uploadSkin(String name, String pngBase64, boolean slim, String credit, String creditUrl) {
        JsonObject m = simple("upload_skin");
        m.addProperty("name", name);
        m.addProperty("png", pngBase64);
        m.addProperty("variant", slim ? "slim" : "classic");
        addCredit(m, credit, creditUrl);
        send(m);
    }

    /** Uploads a model. `source` names the Sketchfab model it came from, or is null. */
    public static void uploadModel(String name, String slot, String packedBase64, String credit, String creditUrl, JsonObject source) {
        JsonObject m = simple("upload_model");
        m.addProperty("name", name);
        m.addProperty("slot", slot);
        m.addProperty("data", packedBase64);
        if (source != null) m.add("source", source);
        else addCredit(m, credit, creditUrl);
        send(m);
    }

    public static void equipModel(String hash, String slot) {
        JsonObject m = simple("equip");
        if (hash == null) m.add("model", com.google.gson.JsonNull.INSTANCE);
        else m.addProperty("model", hash);
        m.addProperty("slot", slot);
        send(m);
    }

    public static void equipSkin(String hash) {
        JsonObject m = simple("equip");
        if (hash == null) m.add("skin", com.google.gson.JsonNull.INSTANCE);
        else m.addProperty("skin", hash);
        send(m);
    }

    public static void askMine() { send(simple("my_cosmetics")); }

    public static void askReports() { send(simple("admin_reports")); }

    public static void equip(String hash) {
        JsonObject m = simple("equip");
        if (hash == null) m.add("cape", com.google.gson.JsonNull.INSTANCE);
        else m.addProperty("cape", hash);
        send(m);
    }

    public static void report(String hash, String reason) {
        JsonObject m = simple("report");
        m.addProperty("hash", hash);
        m.addProperty("reason", reason);
        send(m);
    }

    public static void deleteCosmetic(String hash) {
        JsonObject m = simple("delete_cosmetic");
        m.addProperty("hash", hash);
        send(m);
    }

    public static void adminUnhide(String hash) {
        JsonObject m = simple("admin_unhide");
        m.addProperty("hash", hash);
        send(m);
    }

    private static List<Cosmetics.Item> items(JsonArray array) {
        List<Cosmetics.Item> out = new ArrayList<>();
        if (array == null) return out;
        for (JsonElement e : array) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Cosmetics.Item(str(o, "hash"), str(o, "name"), str(o, "creator"), str(o, "creatorName"),
                    o.has("createdAt") ? o.get("createdAt").getAsLong() : 0,
                    o.has("hidden") && o.get("hidden").getAsBoolean(),
                    o.has("reports") ? o.get("reports").getAsInt() : 0, str(o, "reasons"),
                    o.has("kind") ? str(o, "kind") : "cape", "slim".equals(str(o, "variant")), str(o, "variant"),
                    str(o, "credit"), str(o, "creditUrl"), str(o, "license"), str(o, "licenseUrl"), str(o, "sourceUrl")));
        }
        return out;
    }

    private static void connectTo(String address, String host, String world) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            notice = "Leave your world first, then join.";
            version++;
            return;
        }
        ServerData data = new ServerData(host + "'s world" + (world.isEmpty() ? "" : ": " + world), address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), data, false, null);
    }

    private static void setHosting(Person p, JsonElement hosting) {
        if (hosting == null || hosting.isJsonNull()) {
            p.hostWorld = null;
            p.hostCode = null;
        } else {
            JsonObject h = hosting.getAsJsonObject();
            p.hostWorld = str(h, "world");
            p.hostCode = str(h, "code");
        }
    }

    public static int unreadTotal() {
        return ChatLog.unreadTotal() + INCOMING.size();
    }

    // --- Helpers ------------------------------------------------------------------------

    private static void sendFor(String type, String uuid) {
        JsonObject m = simple(type);
        m.addProperty("uuid", uuid);
        send(m);
    }

    private static JsonObject simple(String type) {
        JsonObject m = new JsonObject();
        m.addProperty("type", type);
        return m;
    }

    /** Sends one message. The next one waits for this one to finish, as WebSockets need. */
    private static synchronized void send(JsonObject m) {
        WebSocket ws = socket;
        if (ws == null) return;
        String text = m.toString();
        sending = sending.handle((ok, err) -> null).thenCompose(x -> ws.sendText(text, true));
    }

    private static void fill(List<Person> list, JsonArray array) {
        list.clear();
        if (array == null) return;
        for (JsonElement e : array) {
            JsonObject o = e.getAsJsonObject();
            Person p = new Person(str(o, "uuid"), str(o, "name"));
            p.online = o.has("online") && o.get("online").getAsBoolean();
            p.lastSeen = o.has("lastSeen") && !o.get("lastSeen").isJsonNull() ? o.get("lastSeen").getAsLong() : 0;
            setHosting(p, o.get("hosting"));
            list.add(p);
        }
    }

    private static Person find(List<Person> list, String uuid) {
        for (Person p : list) if (p.uuid.equals(uuid)) return p;
        return null;
    }

    private static void clearLists() {
        FRIENDS.clear();
        INCOMING.clear();
        OUTGOING.clear();
        BLOCKED.clear();
        version++;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static void onMain(Runnable r) {
        Minecraft.getInstance().execute(r);
    }

    private static void toast(String uuid, Component text) {
        if (!HafniumConfig.socialToasts) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            FriendToast.add(mc.gui.toastManager(), mc.font, ResolvableProfile.createUnresolved(dashed(uuid)), text);
        } catch (Exception e) {
            Hafnium.LOGGER.debug("Couldn't show a toast", e);
        }
    }

    static UUID dashed(String undashed) {
        return UUID.fromString(undashed.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
    }

    private static final Map<String, String> REASONS = new LinkedHashMap<>();

    static {
        REASONS.put("not_found", "Nobody by that name has used Hafnium yet.");
        REASONS.put("thats_you", "That's you!");
        REASONS.put("already_friends", "You're already friends.");
        REASONS.put("you_blocked_them", "You blocked that player. Unblock them first.");
        REASONS.put("too_many_friends", "You've reached the friend limit.");
        REASONS.put("not_friends", "You can only message friends.");
        REASONS.put("mailbox_full", "Their inbox is full until they log in.");
        REASONS.put("slow_down", "Slow down a little.");
        REASONS.put("bad_name", "That isn't a valid Minecraft name.");
        REASONS.put("bad_text", "Messages can be up to 500 characters.");
        REASONS.put("no_such_world", "That world isn't open, or you can't join it.");
        REASONS.put("wrong_cape_size", "A cape picture must be 64 by 32, or a bigger version of that shape.");
        REASONS.put("wrong_skin_size", "A skin must be 64 by 64, or a bigger square version.");
        REASONS.put("not_allowed", "The safety check didn't allow that. Everything in Hafnium must be fine for all ages, with nothing suggestive.");
        REASONS.put("check_unsure", "The safety check wasn't sure about that upload. Try a different name or picture.");
        REASONS.put("check_unavailable", "The safety check isn't working right now. Try again later.");
        REASONS.put("too_many_cubes", "The model has too many cubes. The most is 256.");
        REASONS.put("model_too_large", "The model reaches too far from the player.");
        REASONS.put("model_too_big", "The model is too big. Try a smaller texture or fewer cubes.");
        REASONS.put("bad_model", "The server couldn't read that model.");
        REASONS.put("bad_model_texture", "The model's texture must be a PNG of at most 256 by 256.");
        REASONS.put("too_big", "That's too big to send.");
        REASONS.put("bad_slot", "That model goes in a different spot.");
        REASONS.put("too_many_triangles", "The model is too detailed. The most is 8000 triangles.");
        REASONS.put("bad_credit", "The credit link must start with https://");
        REASONS.put("bad_source", "That Sketchfab model couldn't be used.");
        REASONS.put("source_not_found", "That model isn't on Sketchfab any more.");
        REASONS.put("license_not_allowed", "That model's licence doesn't allow Hafnium to use it.");
        REASONS.put("not_downloadable", "That model's maker doesn't allow downloads.");
        REASONS.put("not_a_png", "That isn't a PNG picture.");
        REASONS.put("cape_too_big", "That picture is too big. Capes can be up to 64 KB.");
        REASONS.put("too_many_uploads", "You can upload 10 capes a day. Try again tomorrow.");
        REASONS.put("cape_removed", "That cape was removed.");
        REASONS.put("not_yours", "You can only delete your own capes.");
        REASONS.put("bad_cape_name", "Give the cape a name.");
        REASONS.put("thats_your_world", "That's your own world.");
        REASONS.put("bad_address", "Hosting needs an address friends can reach.");
    }

    private static String explain(String reason) {
        return REASONS.getOrDefault(reason, "The server said: " + reason);
    }
}
