// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.cosmetics;

import com.mojang.blaze3d.platform.InputConstants;
import com.reality.hafnium.social.Social;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.Blaze3D;
import com.reality.hafnium.Hafnium;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The Hafnium Wardrobe: browse the capes and skins players have uploaded,
 * wear them, and upload your own. To upload, put the picture in the
 * hafnium-uploads folder and pick it from the list, or drag it onto the window.
 * They're Hafnium-only, so players without Hafnium see you normally.
 */
public class WardrobeScreen extends Screen {

    private enum Tab { CAPES, SKINS, MODELS, MINE, REPORTS }

    private static final int CELL_W = 44;
    private static final int CELL_H = 66;
    private static final int PANEL_W = 140;
    private static final int TOP = 58;

    private final Screen parent;
    private Tab tab = Tab.CAPES;
    private int offset;
    private String selected;
    private int seenVersion = -1;
    private int seenSocial = -1;
    private boolean confirmReport;
    private boolean confirmDelete;
    private Path dropped;
    private String droppedKind;
    private boolean droppedSlim;
    private String droppedSlot = "head";
    private String uploadName = "";
    private String uploadCredit = "";
    private String uploadCreditUrl = "";
    private int droppedSize = 1;
    private String problem;
    private EditBox nameBox;
    /** True while choosing a picture from the hafnium-uploads folder. */
    private boolean picking;
    private List<Path> files = new ArrayList<>();

    public WardrobeScreen(Screen parent) {
        super(Component.literal("Hafnium Wardrobe"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        boolean first = seenVersion < 0;
        seenVersion = Cosmetics.version;
        seenSocial = Social.version;
        if (first) {
            Social.askGallery("cape", 0);
            Social.askGallery("skin", 0);
            Social.askGallery("model", 0);
            Social.askMine();
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(this.width - 58, 6, 50, 20).build());
        tabButton("Capes", Tab.CAPES, 8, 48);
        tabButton("Skins", Tab.SKINS, 58, 48);
        tabButton("3D", Tab.MODELS, 108, 36);
        tabButton("My uploads", Tab.MINE, 146, 66);
        if (Cosmetics.admin) tabButton("Reports", Tab.REPORTS, 214, 52);

        if (picking) {
            buildPicker();
            return;
        }

        // The grid on this page.
        List<Cosmetics.Item> list = list();
        int per = perPage();
        if (offset >= list.size() && offset > 0) offset = Math.max(0, (list.size() - 1) / per * per);
        int cols = columns();
        for (int i = offset; i < Math.min(list.size(), offset + per); i++) {
            Cosmetics.Item item = list.get(i);
            int n = i - offset;
            int x = 8 + (n % cols) * CELL_W, y = TOP + (n / cols) * CELL_H;
            this.addRenderableWidget(Button.builder(Component.empty(), b -> select(item.hash())).bounds(x, y, CELL_W - 4, CELL_H - 4).build());
        }
        int by = this.height - 26;
        Button prev = Button.builder(Component.literal("<"), b -> page(-1)).bounds(8, by, 20, 20).build();
        Button next = Button.builder(Component.literal(">"), b -> page(1)).bounds(32, by, 20, 20).build();
        prev.active = offset > 0 || serverPage() > 0;
        next.active = offset + per < list.size() || serverMore();
        this.addRenderableWidget(prev);
        this.addRenderableWidget(next);

        buildPanel();

        nameBox = null;
        if (dropped == null) {
            this.addRenderableWidget(Button.builder(Component.literal("Upload..."), b -> {
                picking = true;
                refreshFiles();
                rebuildWidgets();
            }).bounds(58, by, 70, 20).build());
            if (tab == Tab.MODELS) {
                Button sf = Button.builder(Component.literal("Sketchfab..."), b -> this.minecraft.gui.setScreen(new SketchfabScreen(this)))
                        .bounds(132, by, 74, 20).build();
                sf.active = Social.sketchfabClientId != null;
                sf.setTooltip(Tooltip.create(Component.literal(sf.active
                        ? "Import a free model from Sketchfab, credited to its maker."
                        : "This Hafnium server hasn't set up Sketchfab importing yet.")));
                this.addRenderableWidget(sf);
            }
        }
        if (dropped != null) {
            // Who really made it, if that's someone else. Both are optional.
            EditBox creditBox = new EditBox(this.font, 58, by - 24, 110, 20, Component.literal("Made by"));
            creditBox.setMaxLength(48);
            creditBox.setValue(uploadCredit);
            creditBox.setHint(Component.literal("Made by (optional)"));
            creditBox.setResponder(s -> uploadCredit = s);
            creditBox.setTooltip(Tooltip.create(Component.literal("If someone else made it, put their name here so they get the credit.")));
            this.addRenderableWidget(creditBox);
            EditBox linkBox = new EditBox(this.font, 172, by - 24, Math.max(80, Math.min(200, this.width - PANEL_W - 190)), 20, Component.literal("Link"));
            linkBox.setMaxLength(200);
            linkBox.setValue(uploadCreditUrl);
            linkBox.setHint(Component.literal("Their link (optional)"));
            linkBox.setResponder(s -> uploadCreditUrl = s);
            linkBox.setTooltip(Tooltip.create(Component.literal("A link to their page, starting with https://")));
            this.addRenderableWidget(linkBox);
            int ux = 58;
            nameBox = new EditBox(this.font, ux, by, 110, 20, Component.literal("Name"));
            nameBox.setMaxLength(32);
            nameBox.setValue(uploadName);
            nameBox.setResponder(s -> uploadName = s);
            this.addRenderableWidget(nameBox);
            int bx = ux + 114;
            if ("skin".equals(droppedKind)) {
                this.addRenderableWidget(Button.builder(Component.literal(droppedSlim ? "Arms: Slim" : "Arms: Classic"), b -> {
                    droppedSlim = !droppedSlim;
                    rebuildWidgets();
                }).bounds(bx, by, 74, 20).build());
                bx += 78;
            }
            if ("model".equals(droppedKind)) {
                this.addRenderableWidget(Button.builder(Component.literal(droppedSlot.equals("head") ? "Goes on: Head" : "Goes on: Back"), b -> {
                    droppedSlot = droppedSlot.equals("head") ? "back" : "head";
                    rebuildWidgets();
                }).bounds(bx, by, 80, 20).build());
                bx += 84;
                if (Cosmetics.isMeshFile(dropped)) {
                    this.addRenderableWidget(Button.builder(Component.literal(new String[]{"Small", "Medium", "Large"}[droppedSize]), b -> {
                        droppedSize = (droppedSize + 1) % 3;
                        rebuildWidgets();
                    }).bounds(bx, by, 56, 20).tooltip(Tooltip.create(Component.literal("How big it is on the player."))).build());
                    bx += 60;
                }
            }
            this.addRenderableWidget(Button.builder(Component.literal("Upload"), b -> upload()).bounds(bx, by, 48, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
                dropped = null;
                rebuildWidgets();
            }).bounds(bx + 52, by, 48, 20).build());
        }
    }

    // --- Choosing a picture from the uploads folder --------------------------------------------

    /** The folder players put pictures in to upload them, next to the game's other folders. */
    private Path uploadsFolder() {
        return this.minecraft.gameDirectory.toPath().resolve("hafnium-uploads");
    }

    private void refreshFiles() {
        files = new ArrayList<>();
        try {
            Files.createDirectories(uploadsFolder());
            try (Stream<Path> s = Files.list(uploadsFolder())) {
                s.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase();
                    return n.endsWith(".png") || Cosmetics.isModelFile(p);
                }).sorted().limit(30).forEach(files::add);
            }
        } catch (Exception e) {
            Hafnium.LOGGER.warn("Couldn't read the hafnium-uploads folder", e);
        }
    }

    private void buildPicker() {
        int y = TOP;
        int rows = Math.max(1, (this.height - TOP - 32) / 22);
        for (int i = 0; i < Math.min(files.size(), rows); i++) {
            Path p = files.get(i);
            String kind = Cosmetics.kindOf(p);
            String label = p.getFileName().toString() + (kind == null ? "  (wrong size)" : "  (" + kind + ")");
            Button b = Button.builder(Component.literal(label), btn -> {
                picking = false;
                onFilesDrop(List.of(p));
            }).bounds(8, y, Math.min(260, this.width - PANEL_W - 24), 20).build();
            b.active = kind != null;
            this.addRenderableWidget(b);
            y += 22;
        }
        int by = this.height - 26;
        this.addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> Blaze3D.openPath(uploadsFolder()))
                .bounds(8, by, 80, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> {
            refreshFiles();
            rebuildWidgets();
        }).bounds(92, by, 60, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            picking = false;
            rebuildWidgets();
        }).bounds(156, by, 50, 20).build());
    }

    private void tabButton(String label, Tab which, int x, int w) {
        Button b = Button.builder(Component.literal(label), btn -> {
            tab = which;
            offset = 0;
            selected = null;
            picking = false;
            if (which == Tab.REPORTS) Social.askReports();
            if (which == Tab.MINE) Social.askMine();
            rebuildWidgets();
        }).bounds(x, 32, w, 20).build();
        b.active = tab != which;
        this.addRenderableWidget(b);
    }

    private void buildPanel() {
        Cosmetics.Item item = find(selected);
        if (item == null) return;
        int px = this.width - PANEL_W - 8, y = TOP + 110 + creditLines(item) * 10;
        boolean wearing = worn(item);
        // Wear, and the maker's link beside it when there is one, share a row to save room.
        boolean hasLink = !item.creditLink().isEmpty();
        boolean canWear = tab != Tab.REPORTS && !item.hidden();
        int half = (PANEL_W - 4) / 2;
        if (canWear) {
            this.addRenderableWidget(Button.builder(Component.literal(wearing ? "Take off" : "Wear"), b -> {
                String hash = wearing ? null : item.hash();
                if (item.isModel()) Social.equipModel(hash, item.slot());
                else if (item.isSkin()) Social.equipSkin(hash);
                else Social.equip(hash);
            }).bounds(px, y, hasLink ? half : PANEL_W, 20).build());
        }
        if (hasLink) {
            String link = item.creditLink();
            boolean sketchfab = link.startsWith("https://sketchfab.com/");
            this.addRenderableWidget(Button.builder(Component.literal(sketchfab ? "Sketchfab" : "Maker's page"), b -> {
                try {
                    ConfirmLinkScreen.confirmLinkNow(this, java.net.URI.create(link));
                } catch (IllegalArgumentException e) {
                    problem = "That link doesn't work.";
                }
            }).bounds(canWear ? px + half + 4 : px, y, canWear ? half : PANEL_W, 20)
                    .tooltip(Tooltip.create(Component.literal(sketchfab ? "See the original on Sketchfab." : "Visit the page of whoever made it."))).build());
        }
        if (canWear || hasLink) y += 24;
        boolean mine = Social.myUuid != null && Social.myUuid.equals(item.creator());
        if ((tab == Tab.CAPES || tab == Tab.SKINS || tab == Tab.MODELS) && !mine) {
            this.addRenderableWidget(Button.builder(Component.literal(confirmReport ? "Report it? Click again" : "Report"), b -> {
                if (!confirmReport) {
                    confirmReport = true;
                    rebuildWidgets();
                    return;
                }
                Social.report(item.hash(), "Reported from the Wardrobe");
                confirmReport = false;
                problem = "Thanks. It was reported.";
                rebuildWidgets();
            }).bounds(px, y, PANEL_W, 20).build());
            y += 24;
        }
        if (tab == Tab.REPORTS) {
            this.addRenderableWidget(Button.builder(Component.literal("It's fine, bring it back"), b -> {
                Social.adminUnhide(item.hash());
                selected = null;
            }).bounds(px, y, PANEL_W, 20).build());
            y += 24;
        }
        if (mine || Cosmetics.admin) {
            this.addRenderableWidget(Button.builder(Component.literal(confirmDelete ? "Delete for good? Click again" : "Delete").withStyle(ChatFormatting.RED), b -> {
                if (!confirmDelete) {
                    confirmDelete = true;
                    rebuildWidgets();
                    return;
                }
                Social.deleteCosmetic(item.hash());
                confirmDelete = false;
                selected = null;
            }).bounds(px, y, PANEL_W, 20).build());
        }
    }

    // --- What's shown ------------------------------------------------------------------------

    private List<Cosmetics.Item> list() {
        return switch (tab) {
            case CAPES -> Cosmetics.gallery;
            case SKINS -> Cosmetics.skinGallery;
            case MODELS -> Cosmetics.modelGallery;
            case MINE -> Cosmetics.mine;
            case REPORTS -> Cosmetics.reports;
        };
    }

    private int serverPage() {
        return switch (tab) {
            case CAPES -> Cosmetics.galleryPage;
            case SKINS -> Cosmetics.skinGalleryPage;
            case MODELS -> Cosmetics.modelGalleryPage;
            default -> 0;
        };
    }

    private boolean serverMore() {
        return switch (tab) {
            case CAPES -> Cosmetics.galleryMore;
            case SKINS -> Cosmetics.skinGalleryMore;
            case MODELS -> Cosmetics.modelGalleryMore;
            default -> false;
        };
    }

    private static boolean worn(Cosmetics.Item item) {
        if (item.isModel()) return item.hash().equals(item.slot().equals("head") ? Cosmetics.myHeadModel : Cosmetics.myBackModel);
        return item.hash().equals(item.isSkin() ? Cosmetics.mySkin : Cosmetics.myCape);
    }

    /** How many lines of credit an item shows under its name. */
    private static int creditLines(Cosmetics.Item item) {
        int n = 0;
        if (!item.credit().isEmpty()) n++;
        if (!item.license().isEmpty()) n++;
        if (item.sourceUrl().startsWith("https://sketchfab.com/")) n++;
        return n;
    }

    private Cosmetics.Item find(String hash) {
        if (hash == null) return null;
        for (Cosmetics.Item i : list()) if (i.hash().equals(hash)) return i;
        return null;
    }

    private int columns() {
        return Math.max(1, (this.width - PANEL_W - 24) / CELL_W);
    }

    private int perPage() {
        // While uploading, the upload boxes take more room at the bottom.
        return columns() * Math.max(1, (this.height - TOP - (dropped != null ? 66 : 32)) / CELL_H);
    }

    private void page(int dir) {
        int per = perPage();
        String kind = tab == Tab.SKINS ? "skin" : tab == Tab.MODELS ? "model" : "cape";
        if (dir > 0) {
            if (offset + per < list().size()) offset += per;
            else if (serverMore()) {
                Social.askGallery(kind, serverPage() + 1);
                offset = 0;
            }
        } else {
            if (offset > 0) offset = Math.max(0, offset - per);
            else if (serverPage() > 0) {
                Social.askGallery(kind, serverPage() - 1);
                offset = 0;
            }
        }
        rebuildWidgets();
    }

    private void select(String hash) {
        selected = hash;
        confirmReport = false;
        confirmDelete = false;
        rebuildWidgets();
    }

    // --- Uploading by dragging a picture onto the window ---------------------------------------

    @Override
    public void onFilesDrop(List<Path> files) {
        Hafnium.LOGGER.info("Files dropped on the Wardrobe: {}", files);
        picking = false;
        for (Path p : files) {
            String lower = p.getFileName().toString().toLowerCase();
            if (!lower.endsWith(".png") && !Cosmetics.isModelFile(p)) continue;
            String kind = Cosmetics.kindOf(p);
            if (kind == null) {
                problem = "Capes are 64 by 32 pictures and skins are 64 by 64.";
                return;
            }
            dropped = p;
            droppedKind = kind;
            droppedSlim = false;
            droppedSlot = "head";
            droppedSize = 1;
            uploadCredit = "";
            uploadCreditUrl = "";
            String file = p.getFileName().toString();
            uploadName = file.substring(0, file.lastIndexOf('.'));
            problem = null;
            rebuildWidgets();
            return;
        }
        problem = "Drop a .png picture, a Blockbench .bbmodel file or a .glb model.";
    }

    private void upload() {
        if (dropped == null) return;
        problem = Cosmetics.upload(dropped, uploadName, droppedSlim, droppedSlot, uploadCredit, uploadCreditUrl,
                new GltfImport.Fit(droppedSlot, droppedSize, false, 0));
        if (problem == null) {
            problem = "Uploading...";
            dropped = null;
        }
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
        if (enter && nameBox != null && getFocused() instanceof EditBox) {
            upload();
            return true;
        }
        return super.keyPressed(event);
    }

    // --- Drawing -------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (seenVersion != Cosmetics.version || seenSocial != Social.version) {
            if (Cosmetics.lastUploaded != null && "Uploading...".equals(problem)) problem = "Uploaded! It's in the Wardrobe now.";
            if (Social.notice != null) {
                problem = Social.notice;
                Social.notice = null;
            }
            rebuildWidgets();
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(this.font, "Hafnium Wardrobe", 8, 12, 0xFFFFFFFF);
        if (picking) {
            int px = this.width - PANEL_W - 8;
            String[] help = files.isEmpty()
                    ? new String[]{"The folder is empty.", "", "Click Open folder, put", "your PNG in it, then", "click Refresh."}
                    : new String[]{"Pick a file to upload.", "", "Capes are 64 by 32.", "Skins are 64 by 64.", "3D models are", "Blockbench .bbmodel", "files, or .glb models.", "", "Open folder shows where", "to put new ones."};
            for (int i = 0; i < help.length; i++) {
                g.text(this.font, this.font.plainSubstrByWidth(help[i], PANEL_W), px, TOP + 4 + i * 10, i == 0 ? 0xFFAAAAB4 : 0xFF8A8A92);
            }
            g.text(this.font, this.font.plainSubstrByWidth("Folder: " + uploadsFolder(), this.width - 16), 8, this.height - 38, 0xFF77777F);
            return;
        }
        if (Social.status != Social.Status.ONLINE) g.text(this.font, "Not connected to the Hafnium server.", 110, 12, 0xFFFF8080);

        List<Cosmetics.Item> list = list();
        int cols = columns();
        for (int i = offset; i < Math.min(list.size(), offset + perPage()); i++) {
            Cosmetics.Item item = list.get(i);
            int n = i - offset;
            int x = 8 + (n % cols) * CELL_W, y = TOP + (n / cols) * CELL_H;
            if (item.isModel()) drawModel(g, item.hash(), x + 2, y + 2, 36);
            else if (item.isSkin()) drawSkin(g, item, x + 8, y + 3, 1.5f);
            else drawCape(g, item.hash(), x + 5, y + 3, 3);
            boolean worn = worn(item);
            g.text(this.font, this.font.plainSubstrByWidth(item.name(), CELL_W - 8), x + 3, y + CELL_H - 14, worn ? 0xFF7CE07C : 0xFFE0E0E6);
            if (item.hash().equals(selected)) g.outline(x - 1, y - 1, CELL_W - 2, CELL_H - 2, 0xFFFFFFFF);
        }
        if (list.isEmpty()) {
            String empty = switch (tab) {
                case CAPES -> "No capes yet. Click Upload... to add the first one!";
                case SKINS -> "No skins yet. Click Upload... to add the first one!";
                case MODELS -> "No 3D models yet. Upload a Blockbench model, or import one from Sketchfab!";
                case MINE -> "You haven't uploaded anything yet.";
                case REPORTS -> "Nothing has been reported.";
            };
            g.text(this.font, this.font.plainSubstrByWidth(empty, this.width - PANEL_W - 24), 8, TOP + 4, 0xFFAAAAB4);
        }

        int px = this.width - PANEL_W - 8;
        Cosmetics.Item item = find(selected);
        if (item != null) {
            g.fill(px, TOP, px + PANEL_W, TOP + 104, 0x80101014);
            if (item.isModel()) drawModel(g, item.hash(), px + PANEL_W / 2 - 40, TOP + 2, 80);
            else if (item.isSkin()) drawSkin(g, item, px + PANEL_W / 2 - 20, TOP + 4, 2.5f);
            else drawCape(g, item.hash(), px + PANEL_W / 2 - 25, TOP + 4, 5);
            g.centeredText(this.font, this.font.plainSubstrByWidth(item.name(), PANEL_W - 4), px + PANEL_W / 2, TOP + 86, 0xFFFFFFFF);
            String by = tab == Tab.MINE ? (item.hidden() ? "Hidden" : item.credit().isEmpty() ? "By you" : "Uploaded by you")
                    : (item.credit().isEmpty() ? "by " : "Uploaded by ") + item.creatorName();
            if (item.isSkin()) by += item.slim() ? ", slim arms" : "";
            if (item.isModel()) by += item.slot().equals("head") ? ", on the head" : ", on the back";
            g.centeredText(this.font, this.font.plainSubstrByWidth(by, PANEL_W - 4), px + PANEL_W / 2, TOP + 96, 0xFFAAAAB4);
            // Credit for whoever really made it, and its licence.
            int cy = TOP + 108;
            if (!item.credit().isEmpty()) {
                g.centeredText(this.font, this.font.plainSubstrByWidth("Made by " + item.credit(), PANEL_W - 4), px + PANEL_W / 2, cy, 0xFFE8D58A);
                cy += 10;
            }
            if (!item.license().isEmpty()) {
                g.centeredText(this.font, this.font.plainSubstrByWidth(item.license(), PANEL_W - 4), px + PANEL_W / 2, cy, 0xFF8A8A92);
                cy += 10;
            }
            if (item.sourceUrl().startsWith("https://sketchfab.com/")) {
                g.centeredText(this.font, "From Sketchfab", px + PANEL_W / 2, cy, 0xFF8A8A92);
            }
            if (tab == Tab.REPORTS) {
                g.text(this.font, this.font.plainSubstrByWidth(item.reports() + " reports: " + item.reasons(), PANEL_W), px, TOP + 160, 0xFFFF9C9C);
            }
        } else {
            String[] help = {"Pick one to see it.", "", "To upload your own,", "click Upload... below.", "Capes are 64 by 32,", "skins are 64 by 64.",
                    "Only Hafnium players", "see them on you."};
            for (int i = 0; i < help.length; i++) {
                g.text(this.font, this.font.plainSubstrByWidth(help[i], PANEL_W), px, TOP + 4 + i * 10, i == 0 ? 0xFFAAAAB4 : 0xFF8A8A92);
            }
        }

        int by = this.height - 20;
        if (dropped != null) {
            String what = "skin".equals(droppedKind) ? "skin" : "model".equals(droppedKind) ? "3D model" : "cape";
            g.text(this.font, "Name this " + what + ". Made by someone else? Credit them:", 58, by - 36, 0xFFAAAAB4);
        } else if (problem != null) {
            boolean good = problem.startsWith("Uploaded") || problem.startsWith("Thanks") || problem.startsWith("Uploading");
            g.text(this.font, this.font.plainSubstrByWidth(problem, this.width - 140), 134, by, good ? 0xFF7CE07C : 0xFFFF9C9C);
        }
    }

    /** Draws the front of a cape: the 10 by 16 piece near the top left of the picture. */
    private void drawCape(GuiGraphicsExtractor g, String hash, int x, int y, int scale) {
        Identifier tex = Cosmetics.texture(hash);
        if (tex == null) {
            g.fill(x, y, x + 10 * scale, y + 16 * scale, 0x40FFFFFF);
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 1f, 1f, 10 * scale, 16 * scale, 10, 16, 64, 32);
    }

    /** Draws the front view from the server's preview picture of a model. */
    private void drawModel(GuiGraphicsExtractor g, String hash, int x, int y, int size) {
        Identifier tex = Cosmetics.preview(hash);
        if (tex == null) {
            g.fill(x, y, x + size, y + size, 0x40FFFFFF);
            return;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, size, size, 96, 96, 384, 96);
    }

    /** Draws a skin from the front, flat: head, body, arms and legs, with the outer layer on top. */
    private void drawSkin(GuiGraphicsExtractor g, Cosmetics.Item item, int x, int y, float s) {
        Identifier tex = Cosmetics.texture(item.hash());
        int w = Math.round(16 * s), h = Math.round(32 * s);
        if (tex == null) {
            g.fill(x, y, x + w, y + h, 0x40FFFFFF);
            return;
        }
        int arm = item.slim() ? 3 : 4;
        // Each part: where it sits in the figure, where it is in the picture, and its outer layer.
        part(g, tex, x, y, s, 4, 0, 8, 8, 8, 8, 40, 8);            // head
        part(g, tex, x, y, s, 4, 8, 8, 12, 20, 20, 20, 36);        // body
        part(g, tex, x, y, s, 4 - arm, 8, arm, 12, 44, 20, 44, 36); // right arm
        part(g, tex, x, y, s, 12, 8, arm, 12, 36, 52, 52, 52);      // left arm
        part(g, tex, x, y, s, 4, 20, 4, 12, 4, 20, 4, 36);          // right leg
        part(g, tex, x, y, s, 8, 20, 4, 12, 20, 52, 4, 52);         // left leg
    }

    private void part(GuiGraphicsExtractor g, Identifier tex, int x, int y, float s, int fx, int fy, int w, int h,
                      int u, int v, int ou, int ov) {
        int px = x + Math.round(fx * s), py = y + Math.round(fy * s), pw = Math.round(w * s), ph = Math.round(h * s);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, px, py, u, v, pw, ph, w, h, 64, 64);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, px, py, ou, ov, pw, ph, w, h, 64, 64);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
