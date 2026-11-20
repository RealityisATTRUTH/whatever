// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import com.reality.hafnium.Hafnium;
import com.reality.hafnium.HafniumConfig;
import com.reality.hafnium.social.Social;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Capes and skins from the Hafnium Wardrobe: who wears which, and the pictures.
 * They're Hafnium-only. Players without Hafnium see the normal skin and cape.
 *
 * Each cape is named by its SHA-256 fingerprint. The first time a cape is
 * needed, it's downloaded once, checked against its fingerprint and kept in
 * config/hafnium/cosmetics. After that it loads from this computer. If that
 * folder grows past about 200 MB, the capes not used for the longest time
 * are removed, and they'd simply download again if needed.
 *
 * Everything here runs on the game's main thread, apart from downloads.
 */
public final class Cosmetics {

    /** One cape or skin in the gallery or in your uploads. */
    public record Item(String hash, String name, String creator, String creatorName, long createdAt, boolean hidden,
                       int reports, String reasons, String kind, boolean slim, String variant,
                       String credit, String creditUrl, String license, String licenseUrl, String sourceUrl) {
        /** Where to read more about who made it: the Sketchfab page, or the maker's own link. */
        public String creditLink() { return !sourceUrl.isEmpty() ? sourceUrl : creditUrl; }
        public boolean isSkin() { return "skin".equals(kind); }
        public boolean isModel() { return "model".equals(kind); }
        /** Where a model goes: "head" or "back". */
        public String slot() { return "back".equals(variant) ? "back" : "head"; }
    }

    /** A worn skin's picture and whether it has slim arms. */
    public record Skin(Identifier texture, boolean slim) { }

    /** A 3D model ready to draw, with its texture loaded. */
    public record LoadedModel(Model3d model, Identifier texture) { }

    private static final long LOOKUP_AGAIN_MS = 5 * 60 * 1000;
    private static final long CACHE_LIMIT_BYTES = 200L * 1024 * 1024;
    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("hafnium").resolve("cosmetics");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** The cape you're wearing, or null. */
    public static String myCape;
    /** The skin you're wearing, or null for your normal one. */
    public static String mySkin;
    public static boolean mySkinSlim;
    public static boolean admin;
    public static List<Item> gallery = new ArrayList<>();
    public static boolean galleryMore;
    public static int galleryPage;
    /** The 3D models you're wearing: on the head and on the back. Either can be null. */
    public static String myHeadModel;
    public static String myBackModel;
    public static List<Item> modelGallery = new ArrayList<>();
    public static boolean modelGalleryMore;
    public static int modelGalleryPage;
    public static List<Item> skinGallery = new ArrayList<>();
    public static boolean skinGalleryMore;
    public static int skinGalleryPage;
    public static List<Item> mine = new ArrayList<>();
    public static List<Item> reports = new ArrayList<>();
    public static String lastUploaded;
    public static int version;

    private record Wearing(String cape, String skin, boolean slim, String head, String back, long at) { }

    private static final Map<String, Wearing> WEARING = new HashMap<>();
    private static final Set<String> TO_LOOK_UP = new LinkedHashSet<>();
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static final Set<String> LOADING = new LinkedHashSet<>();
    private static final Set<String> FAILED = new LinkedHashSet<>();

    private Cosmetics() {}

    // --- Who wears what -------------------------------------------------------------------

    /** The cape picture to draw on this player, or null to leave their cape alone. */
    public static Identifier capeFor(AbstractClientPlayer player) {
        if (!HafniumConfig.socialOn) return null;
        String id = undashed(player.getUUID());
        String cape;
        if (id.equals(Social.myUuid)) {
            cape = myCape;
        } else {
            Wearing w = WEARING.get(id);
            if (w == null || System.currentTimeMillis() - w.at() > LOOKUP_AGAIN_MS) TO_LOOK_UP.add(id);
            cape = w == null ? null : w.cape();
        }
        return cape == null ? null : texture(cape);
    }

    /** The Hafnium skin to draw on this player, or null to leave their skin alone. */
    public static Skin skinFor(AbstractClientPlayer player) {
        if (!HafniumConfig.socialOn) return null;
        String id = undashed(player.getUUID());
        String skin;
        boolean slim;
        if (id.equals(Social.myUuid)) {
            skin = mySkin;
            slim = mySkinSlim;
        } else {
            Wearing w = WEARING.get(id);
            if (w == null || System.currentTimeMillis() - w.at() > LOOKUP_AGAIN_MS) TO_LOOK_UP.add(id);
            skin = w == null ? null : w.skin();
            slim = w != null && w.slim();
        }
        if (skin == null) return null;
        Identifier tex = texture(skin);
        return tex == null ? null : new Skin(tex, slim);
    }

    /** Asks the server about the players seen recently. Called about twice a second. */
    public static void tick() {
        if (TO_LOOK_UP.isEmpty() || Social.status != Social.Status.ONLINE) return;
        List<String> batch = new ArrayList<>();
        for (String id : TO_LOOK_UP) {
            batch.add(id);
            if (batch.size() == 100) break;
        }
        TO_LOOK_UP.removeAll(batch);
        // Marked as looked up now, so they aren't asked about again while the answer is on its way.
        long now = System.currentTimeMillis();
        for (String id : batch) {
            Wearing old = WEARING.get(id);
            WEARING.put(id, old == null ? new Wearing(null, null, false, null, null, now)
                    : new Wearing(old.cape(), old.skin(), old.slim(), old.head(), old.back(), now));
        }
        Social.askWearing(batch);
    }

    /** What the server said these players wear. A skin is its fingerprint, with "slim:" in front for slim arms. */
    public static void onWearing(Map<String, String> capes, Map<String, String> skins, Map<String, String[]> models) {
        long now = System.currentTimeMillis();
        for (String id : capes.keySet()) {
            String skin = skins.get(id);
            boolean slim = skin != null && skin.startsWith("slim:");
            if (slim) skin = skin.substring(5);
            String[] m = models.getOrDefault(id, new String[2]);
            WEARING.put(id, new Wearing(capes.get(id), skin, slim, m[0], m[1], now));
        }
    }

    /** The 3D model this player wears in a slot ("head" or "back"), or null. */
    public static LoadedModel modelFor(AbstractClientPlayer player, String slot) {
        if (!HafniumConfig.socialOn) return null;
        String id = undashed(player.getUUID());
        String hash;
        if (id.equals(Social.myUuid)) {
            hash = slot.equals("head") ? myHeadModel : myBackModel;
        } else {
            Wearing w = WEARING.get(id);
            if (w == null || System.currentTimeMillis() - w.at() > LOOKUP_AGAIN_MS) TO_LOOK_UP.add(id);
            hash = w == null ? null : slot.equals("head") ? w.head() : w.back();
        }
        return hash == null ? null : model(hash);
    }

    private static final Map<String, LoadedModel> MODELS = new HashMap<>();
    private static final Set<String> MODELS_LOADING = new LinkedHashSet<>();
    private static final Map<String, Identifier> PREVIEWS = new HashMap<>();
    private static final Set<String> PREVIEWS_LOADING = new LinkedHashSet<>();

    /** A model ready to draw, or null while it downloads. */
    public static LoadedModel model(String hash) {
        LoadedModel m = MODELS.get(hash);
        if (m != null || FAILED.contains(hash) || !MODELS_LOADING.add(hash)) return m;
        CompletableFuture.supplyAsync(() -> {
            byte[] packed = fetchFile(hash, ".bin", 256 * 1024, true);
            if (packed == null) return null;
            try {
                return Model3d.unpack(packed);
            } catch (IOException e) {
                Hafnium.LOGGER.warn("A model couldn't be read: {}", hash);
                return null;
            }
        }).thenAccept(model -> Minecraft.getInstance().execute(() -> {
            MODELS_LOADING.remove(hash);
            if (model == null) {
                FAILED.add(hash);
                return;
            }
            try {
                NativeImage image = NativeImage.read(model.texture);
                Identifier key = Identifier.fromNamespaceAndPath("hafnium", "model/" + hash);
                Minecraft.getInstance().getTextureManager().register(key, new DynamicTexture(() -> "Hafnium model " + hash, image));
                MODELS.put(hash, new LoadedModel(model, key));
            } catch (IOException e) {
                FAILED.add(hash);
            }
        }));
        return null;
    }

    /** The server's preview picture of a model, or null while it downloads. */
    public static Identifier preview(String hash) {
        Identifier id = PREVIEWS.get(hash);
        if (id != null || !PREVIEWS_LOADING.add(hash)) return id;
        CompletableFuture.supplyAsync(() -> fetchFile(hash, ".preview.png", 512 * 1024, false)).thenAccept(bytes -> Minecraft.getInstance().execute(() -> {
            if (bytes == null) return;
            try {
                NativeImage image = NativeImage.read(bytes);
                Identifier key = Identifier.fromNamespaceAndPath("hafnium", "preview/" + hash);
                Minecraft.getInstance().getTextureManager().register(key, new DynamicTexture(() -> "Hafnium preview " + hash, image));
                PREVIEWS.put(hash, key);
            } catch (IOException e) {
                Hafnium.LOGGER.debug("Couldn't read a preview", e);
            }
        }));
        return null;
    }

    // --- Pictures ---------------------------------------------------------------------------

    /** The loaded picture for a cape, or null while it's still loading. */
    public static Identifier texture(String hash) {
        Identifier id = TEXTURES.get(hash);
        if (id != null || FAILED.contains(hash) || !LOADING.add(hash)) return id;
        CompletableFuture.supplyAsync(() -> fetch(hash)).thenAccept(bytes -> Minecraft.getInstance().execute(() -> {
            LOADING.remove(hash);
            if (bytes == null) {
                FAILED.add(hash);
                return;
            }
            try {
                NativeImage image = NativeImage.read(bytes);
                Identifier key = Identifier.fromNamespaceAndPath("hafnium", "cosmetic/" + hash);
                Minecraft.getInstance().getTextureManager().register(key, new DynamicTexture(() -> "Hafnium cosmetic " + hash, image));
                TEXTURES.put(hash, key);
            } catch (IOException e) {
                Hafnium.LOGGER.warn("A cape picture couldn't be read: {}", hash);
                FAILED.add(hash);
            }
        }));
        return null;
    }

    /** The cape or skin picture's bytes, from this computer if it's been downloaded before. */
    private static byte[] fetch(String hash) {
        return fetchFile(hash, ".png", 64 * 1024, true);
    }

    /**
     * A file from the server, kept on this computer after the first download.
     * When `checked`, the file must match its fingerprint, so it can't have been changed on the way.
     */
    private static byte[] fetchFile(String hash, String suffix, int limit, boolean checked) {
        Path file = DIR.resolve(hash + suffix);
        try {
            if (Files.exists(file)) {
                byte[] bytes = Files.readAllBytes(file);
                if (!checked || hash.equals(sha256(bytes))) {
                    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
                    return bytes;
                }
            }
            HttpResponse<byte[]> res = HTTP.send(HttpRequest.newBuilder(URI.create(downloadBase() + "/cosmetic/" + hash + suffix))
                    .timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = res.body();
            if (res.statusCode() != 200 || bytes.length > limit || (checked && !hash.equals(sha256(bytes)))) return null;
            Files.createDirectories(DIR);
            Files.write(file, bytes);
            trimCache();
            return bytes;
        } catch (Exception e) {
            Hafnium.LOGGER.info("Couldn't download cape {}: {}", hash, e.toString());
            return null;
        }
    }

    /** The server's web address, from the address the game connects to. */
    private static String downloadBase() {
        String s = HafniumConfig.socialServer;
        if (s.endsWith("/connect")) s = s.substring(0, s.length() - "/connect".length());
        return s.replaceFirst("^wss://", "https://").replaceFirst("^ws://", "http://");
    }

    private static void trimCache() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(DIR)) {
            files = new ArrayList<>(s.filter(Files::isRegularFile).toList());
        }
        long total = 0;
        for (Path p : files) total += Files.size(p);
        if (total <= CACHE_LIMIT_BYTES) return;
        files.sort(Comparator.comparing(p -> {
            try {
                return Files.getLastModifiedTime(p);
            } catch (IOException e) {
                return FileTime.fromMillis(0);
            }
        }));
        for (Path p : files) {
            if (total <= CACHE_LIMIT_BYTES * 3 / 4) break;
            total -= Files.size(p);
            Files.deleteIfExists(p);
        }
    }

    // --- Uploading ----------------------------------------------------------------------------

    /** Whether a file is a skin (square picture), a cape (twice as wide as tall) or a 3D model, or null. */
    public static String kindOf(Path file) {
        if (isModelFile(file)) return "model";
        try (NativeImage image = NativeImage.read(Files.readAllBytes(file))) {
            int w = image.getWidth(), h = image.getHeight();
            for (int n : new int[]{1, 2, 4, 8}) {
                if (w == 64 * n && h == 64 * n) return "skin";
                if (w == 64 * n && h == 32 * n) return "cape";
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /** Blockbench projects, and glTF models as a .glb, a .gltf or a .zip holding one. */
    public static boolean isModelFile(Path file) {
        String n = file.getFileName().toString().toLowerCase();
        return n.endsWith(".bbmodel") || n.endsWith(".glb") || n.endsWith(".gltf") || n.endsWith(".zip");
    }

    public static boolean isMeshFile(Path file) {
        return isModelFile(file) && !file.getFileName().toString().toLowerCase().endsWith(".bbmodel");
    }

    /**
     * Checks a picture or model and uploads it. `credit` and `creditUrl` say who
     * really made it, if that's someone else. `fit` is how a glTF model is fitted
     * onto the player. Returns a problem in plain words, or null if it was sent.
     */
    public static String upload(Path file, String name, boolean slim, String slot, String credit, String creditUrl, GltfImport.Fit fit) {
        if (!creditUrl.isBlank() && !creditUrl.trim().startsWith("https://")) return "The credit link must start with https://";
        if (isModelFile(file)) {
            try {
                Model3d model = isMeshFile(file) ? GltfImport.fromFile(file, fit) : Model3d.fromBlockbench(file);
                byte[] packed = model.pack();
                int limit = model.hasMesh() ? 250 * 1024 : 64 * 1024;
                if (packed.length > limit) return "The model is too big, even packed. Try a smaller texture or fewer cubes.";
                if (Social.status != Social.Status.ONLINE) return "Hafnium isn't connected to its server right now.";
                Social.uploadModel(name.isBlank() ? "Model" : name.trim(), model.hasMesh() ? fit.slot() : slot,
                        java.util.Base64.getEncoder().encodeToString(packed), credit, creditUrl, null);
                return null;
            } catch (IOException e) {
                return e.getMessage();
            } catch (RuntimeException e) {
                Hafnium.LOGGER.warn("A model couldn't be read", e);
                return "That model couldn't be read.";
            }
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            return "Couldn't read that file.";
        }
        if (bytes.length > 64 * 1024) return "That picture is too big. Pictures can be up to 64 KB.";
        String kind = kindOf(file);
        if (kind == null) return "Capes are 64 by 32 pictures and skins are 64 by 64, or sharper versions of those shapes.";
        if (Social.status != Social.Status.ONLINE) return "Hafnium isn't connected to its server right now.";
        String b64 = java.util.Base64.getEncoder().encodeToString(bytes);
        String clean = name.isBlank() ? (kind.equals("skin") ? "Skin" : "Cape") : name.trim();
        if (kind.equals("skin")) Social.uploadSkin(clean, b64, slim, credit, creditUrl);
        else Social.uploadCape(clean, b64, credit, creditUrl);
        return null;
    }

    // --- Helpers -------------------------------------------------------------------------------

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static String undashed(UUID id) {
        return id.toString().replace("-", "");
    }
}
