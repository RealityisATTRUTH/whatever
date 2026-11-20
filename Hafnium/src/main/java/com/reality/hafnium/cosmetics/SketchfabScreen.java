// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.cosmetics;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.reality.hafnium.Hafnium;
import com.reality.hafnium.social.Social;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Importing a model from Sketchfab: search, pick one, choose where it goes and
 * how big, and import it. The model is downloaded with the player's own
 * Sketchfab account, fitted onto the player, and uploaded to the Hafnium
 * server, which looks up its maker and licence on Sketchfab and runs the
 * safety check before anyone can see it.
 *
 * Results are listed as text only. Sketchfab's pictures never show in the game.
 */
public class SketchfabScreen extends Screen {

    private static final int PANEL_W = 160;
    private static final int TOP = 58;

    private final Screen parent;
    private EditBox searchBox;
    private EditBox nameBox;
    private String words = "";
    private List<Sketchfab.Result> results = new ArrayList<>();
    private final List<String> pages = new ArrayList<>(); // the address of each page seen, null for the first
    private String next;
    private int hidden;
    private Sketchfab.Result picked;
    private String importName = "";
    private GltfImport.Fit fit = new GltfImport.Fit("head", 1, false, 0);
    private String status;
    private boolean statusGood;
    private boolean busy;
    private String waitingFor;
    private int seenCosmetics = -1;
    private int seenSocial = -1;

    public SketchfabScreen(Screen parent) {
        super(Component.literal("Import from Sketchfab"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        seenCosmetics = Cosmetics.version;
        seenSocial = Social.version;
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose()).bounds(this.width - 58, 6, 50, 20).build());

        searchBox = new EditBox(this.font, 8, 32, Math.min(220, this.width - PANEL_W - 90), 20, Component.literal("Search"));
        searchBox.setMaxLength(80);
        searchBox.setValue(words);
        searchBox.setHint(Component.literal("Search Sketchfab, like \"crown\""));
        searchBox.setResponder(s -> words = s);
        this.addRenderableWidget(searchBox);
        Button go = Button.builder(Component.literal("Search"), b -> search(null, true)).bounds(searchBox.getX() + searchBox.getWidth() + 4, 32, 56, 20).build();
        go.active = !busy;
        this.addRenderableWidget(go);

        int px = this.width - PANEL_W - 8;
        boolean signed = Sketchfab.signedIn();
        Button account = Button.builder(Component.literal(signed ? "Sign out of Sketchfab" : "Sign in to Sketchfab"), b -> {
            if (Sketchfab.signedIn()) {
                Sketchfab.signOut();
                rebuildWidgets();
            } else {
                signIn();
            }
        }).bounds(px, 32, PANEL_W, 20).build();
        account.setTooltip(Tooltip.create(Component.literal(signed
                ? "Signed in. Hafnium keeps the sign-in on this computer only."
                : "Downloading needs your own free Sketchfab account. Searching doesn't.")));
        this.addRenderableWidget(account);

        // The results, as text.
        int rows = Math.max(1, (this.height - TOP - 50) / 22);
        int lw = Math.max(120, this.width - PANEL_W - 24);
        for (int i = 0; i < Math.min(results.size(), rows); i++) {
            Sketchfab.Result r = results.get(i);
            String label = this.font.plainSubstrByWidth(r.name() + "  by " + r.author(), lw - 8);
            Button b = Button.builder(Component.literal(label), btn -> pick(r)).bounds(8, TOP + i * 22, lw, 20).build();
            b.active = picked == null || !picked.uid().equals(r.uid());
            this.addRenderableWidget(b);
        }
        int by = this.height - 26;
        Button prev = Button.builder(Component.literal("<"), b -> {
            pages.remove(pages.size() - 1);
            search(pages.remove(pages.size() - 1), false);
        }).bounds(8, by, 20, 20).build();
        prev.active = !busy && pages.size() > 1;
        Button nxt = Button.builder(Component.literal(">"), b -> search(next, false)).bounds(32, by, 20, 20).build();
        nxt.active = !busy && next != null;
        this.addRenderableWidget(prev);
        this.addRenderableWidget(nxt);

        if (picked != null) buildPanel(px);
    }

    private void buildPanel(int px) {
        int y = TOP + 56;
        String link = !picked.viewerUrl().isEmpty() ? picked.viewerUrl() : "https://sketchfab.com/3d-models/" + picked.uid();
        this.addRenderableWidget(Button.builder(Component.literal("See it on Sketchfab"), b -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(link)))
                .bounds(px, y, PANEL_W, 20).tooltip(Tooltip.create(Component.literal("Opens the model's page in your browser."))).build());
        y += 22;
        boolean usable = picked.problem() == null && !picked.license().isEmpty();
        if (!usable) return;
        nameBox = new EditBox(this.font, px, y, PANEL_W, 20, Component.literal("Name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(importName);
        nameBox.setHint(Component.literal("Name in the Wardrobe"));
        nameBox.setResponder(s -> importName = s);
        this.addRenderableWidget(nameBox);
        y += 22;
        int half = (PANEL_W - 4) / 2;
        this.addRenderableWidget(Button.builder(Component.literal(fit.slot().equals("head") ? "On: Head" : "On: Back"), b -> change("slot"))
                .bounds(px, y, half, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Size: " + new String[]{"Small", "Medium", "Large"}[fit.size()]), b -> change("size"))
                .bounds(px + half + 4, y, half, 20).build());
        y += 22;
        if (fit.slot().equals("head")) {
            this.addRenderableWidget(Button.builder(Component.literal(fit.around() ? "Around head" : "On top"), b -> change("around"))
                    .bounds(px, y, half, 20).tooltip(Tooltip.create(Component.literal("On top for hats and crowns. Around the head for masks and helmets."))).build());
        }
        this.addRenderableWidget(Button.builder(Component.literal("Turn: " + fit.turns() * 90 + "°"), b -> change("turn"))
                .bounds(px + half + 4, y, half, 20).tooltip(Tooltip.create(Component.literal("If it faces the wrong way, turn it a quarter at a time."))).build());
        y += 22;
        Button imp = Button.builder(Component.literal(Sketchfab.signedIn() ? "Import" : "Sign in to import"), b -> importIt()).bounds(px, y, PANEL_W, 20).build();
        imp.active = !busy && Social.status == Social.Status.ONLINE;
        this.addRenderableWidget(imp);
    }

    private void change(String what) {
        fit = fit.next(what);
        rebuildWidgets();
    }

    // --- Doing things ------------------------------------------------------------------------------

    private void search(String page, boolean fresh) {
        if (page == null && words.isBlank()) {
            say("Type what to look for first.", false);
            return;
        }
        if (fresh) pages.clear();
        busy = true;
        say("Searching...", true);
        rebuildWidgets();
        Sketchfab.search(words, page).whenComplete((p, err) -> this.minecraft.execute(() -> {
            busy = false;
            if (err != null) {
                say(message(err), false);
            } else {
                pages.add(page);
                results = p.results();
                next = p.next();
                hidden = p.hidden();
                picked = null;
                say(results.isEmpty() ? "Nothing found. Try other words." : null, false);
            }
            rebuildWidgets();
        }));
    }

    private void pick(Sketchfab.Result r) {
        picked = r;
        importName = r.name().length() > 32 ? r.name().substring(0, 32).trim() : r.name();
        say("Checking its licence...", true);
        rebuildWidgets();
        // Search results don't always say the licence, so the model's own page is asked.
        Sketchfab.details(r.uid()).whenComplete((d, err) -> this.minecraft.execute(() -> {
            if (picked == null || !picked.uid().equals(r.uid())) return;
            if (err != null) {
                say(message(err), false);
            } else {
                picked = d;
                say(d.problem() != null ? d.problem() : null, false);
            }
            rebuildWidgets();
        }));
    }

    private void signIn() {
        try {
            URI uri = Sketchfab.startSignIn(problem -> {
                say(problem == null ? "Signed in to Sketchfab." : problem, problem == null);
                if (this.minecraft.gui.screen() == this) rebuildWidgets();
            });
            say("Sign in on the Sketchfab page in your browser, then come back.", true);
            ConfirmLinkScreen.confirmLinkNow(this, uri);
        } catch (java.net.BindException e) {
            say("Another sign-in is already waiting. Close other games and try again.", false);
        } catch (Exception e) {
            say(e.getMessage(), false);
        }
    }

    private void importIt() {
        if (picked == null || busy) return;
        if (!Sketchfab.signedIn()) {
            signIn();
            return;
        }
        Sketchfab.Result r = picked;
        String name = importName.isBlank() ? r.name() : importName.trim();
        if (Sketchfab.blocked(name)) {
            say("The safety check won't allow that name.", false);
            return;
        }
        GltfImport.Fit f = fit;
        busy = true;
        say("Downloading from Sketchfab...", true);
        rebuildWidgets();
        Sketchfab.download(r.uid()).thenApply(zip -> {
            this.minecraft.execute(() -> say("Fitting it onto the player...", true));
            try {
                Model3d model = GltfImport.fromZip(zip, f);
                return model.pack();
            } catch (java.io.IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((packed, err) -> this.minecraft.execute(() -> {
            busy = false;
            if (err != null) {
                Hafnium.LOGGER.warn("A Sketchfab import didn't work", err);
                say(message(err), false);
                rebuildWidgets();
                return;
            }
            if (Social.status != Social.Status.ONLINE) {
                say("Hafnium isn't connected to its server right now.", false);
                rebuildWidgets();
                return;
            }
            JsonObject source = new JsonObject();
            source.addProperty("site", "sketchfab");
            source.addProperty("uid", r.uid());
            // Only used if the server can't reach Sketchfab to look these up itself.
            source.addProperty("title", r.name());
            source.addProperty("author", r.author());
            source.addProperty("authorUrl", r.authorUrl());
            source.addProperty("license", r.license());
            source.addProperty("licenseLabel", r.licenseLabel());
            source.addProperty("licenseUrl", r.licenseUrl());
            waitingFor = Cosmetics.lastUploaded == null ? "" : Cosmetics.lastUploaded;
            busy = true;
            Social.uploadModel(name, f.slot(), Base64.getEncoder().encodeToString(packed), null, null, source);
            say("Uploading. The safety check takes a few seconds...", true);
            rebuildWidgets();
        }));
    }

    private static String message(Throwable err) {
        Throwable t = err;
        while (t.getCause() != null && (t.getMessage() == null || t.getMessage().contains("Exception"))) t = t.getCause();
        String m = t.getMessage();
        return m == null || m.isBlank() ? "Something went wrong." : m;
    }

    private void say(String text, boolean good) {
        status = text;
        statusGood = good;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
        if (enter && getFocused() == searchBox) {
            search(null, true);
            return true;
        }
        return super.keyPressed(event);
    }

    // --- Drawing ---------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (waitingFor != null && (seenCosmetics != Cosmetics.version || seenSocial != Social.version)) {
            if (Cosmetics.lastUploaded != null && !Cosmetics.lastUploaded.equals(waitingFor)) {
                say("Imported! It's in the Wardrobe's 3D tab, with credit to its maker.", true);
                waitingFor = null;
                busy = false;
            } else if (Social.notice != null) {
                say(Social.notice, false);
                Social.notice = null;
                waitingFor = null;
                busy = false;
            }
            rebuildWidgets();
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(this.font, "Import from Sketchfab", 8, 12, 0xFFFFFFFF);
        g.text(this.font, this.font.plainSubstrByWidth("Models from Sketchfab, credited to their makers.", this.width - 196), 130, 12, 0xFF8A8A92);

        int px = this.width - PANEL_W - 8;
        if (picked != null) {
            g.fill(px, TOP, px + PANEL_W, TOP + 52, 0x80101014);
            String[] lines = {
                    picked.name(),
                    "by " + picked.author(),
                    picked.licenseLabel().isEmpty() ? "Licence: checking..." : picked.licenseLabel(),
                    picked.faces() > 0 ? String.format("%,d faces", picked.faces()) : "",
                };
            int y = TOP + 4;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                int color = i == 0 ? 0xFFFFFFFF : 0xFFAAAAB4;
                for (var part : this.font.split(Component.literal(lines[i]), PANEL_W - 8)) {
                    if (y > TOP + 42) break;
                    g.text(this.font, part, px + 4, y, color);
                    y += 10;
                }
            }
        } else {
            String[] help = {"Search, then pick a model.", "", "Only models whose licence", "lets anyone use them with", "credit can be imported.",
                    "", "Every import goes through", "the safety check before", "anyone can see it.", "", "Its maker is credited in", "the Wardrobe."};
            for (int i = 0; i < help.length; i++) {
                g.text(this.font, this.font.plainSubstrByWidth(help[i], PANEL_W), px, TOP + 4 + i * 10, i == 0 ? 0xFFAAAAB4 : 0xFF8A8A92);
            }
        }
        int left = px - 66;
        if (hidden > 0 && !results.isEmpty()) {
            g.text(this.font, this.font.plainSubstrByWidth(hidden + " hidden by the safety filter.", left), 58, this.height - 44, 0xFF77777F);
        }
        if (status != null) {
            // Up to two lines, kept left of the panel.
            var parts = this.font.split(Component.literal(status), left);
            int y = this.height - (parts.size() > 1 ? 30 : 20);
            for (int i = 0; i < Math.min(2, parts.size()); i++) {
                g.text(this.font, parts.get(i), 58, y + i * 10, statusGood ? 0xFF7CE07C : 0xFFFF9C9C);
            }
        }
    }

    @Override
    public void onClose() {
        Sketchfab.stopWaiting();
        this.minecraft.gui.setScreen(parent);
    }
}
