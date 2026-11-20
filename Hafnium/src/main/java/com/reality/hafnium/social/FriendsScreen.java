// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Reality Studios
package com.reality.hafnium.social;

import com.mojang.blaze3d.platform.InputConstants;
import com.reality.hafnium.HafniumConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Hafnium's friends screen: your friends on the left, with requests and
 * blocked players, and a chat with whoever you pick on the right.
 */
public class FriendsScreen extends Screen {

    private static final int LEFT = 8;
    private static final int LIST_W = 150;
    private static final int ROW = 22;
    private static final int TOP = 62;
    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter OLDER = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    /** One row of the list on the left. */
    private record Entry(String header, Social.Person person, String kind) { }

    private final Screen parent;
    private String selected;
    private int page;
    private int scroll;
    private int seenVersion = -1;
    private String addText = "";
    private String chatText = "";
    private boolean confirmRemove;
    private boolean confirmBlock;
    /** Puts the cursor in the chat box on the next frame, after the click that picked the friend is done. */
    private boolean focusChat;
    private EditBox addBox;
    private EditBox chatBox;
    private EditBox codeBox;
    private String codeText = "";
    private final List<int[]> headers = new ArrayList<>(); // y positions of the list's headings
    private final List<String> headerText = new ArrayList<>();

    public FriendsScreen(Screen parent) {
        super(Component.literal("Hafnium Friends"));
        this.parent = parent;
    }

    private int rightX() { return LEFT + LIST_W + 8; }
    private int rightW() { return this.width - rightX() - 8; }
    private int rowsPerPage() { return Math.max(1, (this.height - TOP - 30) / ROW); }

    @Override
    protected void init() {
        boolean chatFocused = chatBox != null && getFocused() == chatBox;
        boolean addFocused = addBox != null && getFocused() == addBox;
        seenVersion = Social.version;
        headers.clear();
        headerText.clear();

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width - 58, 6, 50, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(HafniumConfig.socialOn ? "Friends: On" : "Friends: Off"), b -> {
            Social.setEnabled(!HafniumConfig.socialOn);
            this.rebuildWidgets();
        }).bounds(this.width - 132, 6, 70, 20).build());

        if (!HafniumConfig.socialOn) return;

        addBox = new EditBox(this.font, LEFT, 36, LIST_W - 42, 20, Component.literal("Friend's name"));
        addBox.setMaxLength(16);
        addBox.setHint(Component.literal("Add by name").withStyle(ChatFormatting.DARK_GRAY));
        addBox.setValue(addText);
        addBox.setResponder(s -> addText = s);
        this.addRenderableWidget(addBox);
        Button add = Button.builder(Component.literal("Add"), b -> sendRequest())
                .bounds(LEFT + LIST_W - 38, 36, 38, 20).build();
        add.active = Social.status == Social.Status.ONLINE;
        this.addRenderableWidget(add);

        buildList();
        buildRight();

        if (chatFocused && chatBox != null) this.setFocused(chatBox);
        else if (addFocused) this.setFocused(addBox);
    }

    // --- The list on the left -------------------------------------------------------------

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        if (!Social.INCOMING.isEmpty()) {
            out.add(new Entry("Friend requests", null, null));
            for (Social.Person p : Social.INCOMING) out.add(new Entry(null, p, "incoming"));
        }
        out.add(new Entry("Friends", null, null));
        List<Social.Person> friends = new ArrayList<>(Social.FRIENDS);
        friends.sort((a, b) -> a.online != b.online ? (a.online ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
        for (Social.Person p : friends) out.add(new Entry(null, p, "friend"));
        for (Social.Person p : Social.OUTGOING) out.add(new Entry(null, p, "outgoing"));
        if (!Social.BLOCKED.isEmpty()) {
            out.add(new Entry("Blocked", null, null));
            for (Social.Person p : Social.BLOCKED) out.add(new Entry(null, p, "blocked"));
        }
        return out;
    }

    private void buildList() {
        List<Entry> all = entries();
        int per = rowsPerPage();
        int pages = Math.max(1, (all.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        int y = TOP;
        for (int i = page * per; i < Math.min(all.size(), (page + 1) * per); i++, y += ROW) {
            Entry e = all.get(i);
            if (e.header() != null) {
                headers.add(new int[]{y});
                headerText.add(e.header());
                continue;
            }
            Social.Person p = e.person();
            switch (e.kind()) {
                case "incoming" -> {
                    Button name = Button.builder(Component.literal(p.name), b -> { }).bounds(LEFT, y, LIST_W - 58, 20).build();
                    name.active = false;
                    this.addRenderableWidget(name);
                    this.addRenderableWidget(Button.builder(Component.literal("Yes").withStyle(ChatFormatting.GREEN),
                            b -> Social.accept(p.uuid)).bounds(LEFT + LIST_W - 56, y, 27, 20).build());
                    this.addRenderableWidget(Button.builder(Component.literal("No").withStyle(ChatFormatting.RED),
                            b -> Social.decline(p.uuid)).bounds(LEFT + LIST_W - 27, y, 27, 20).build());
                }
                case "outgoing" -> {
                    Button b = Button.builder(Component.literal(p.name).append(Component.literal(" (sent)")
                            .withStyle(ChatFormatting.GRAY)), x -> { }).bounds(LEFT, y, LIST_W, 20).build();
                    b.active = false;
                    this.addRenderableWidget(b);
                }
                case "blocked" -> this.addRenderableWidget(Button.builder(Component.literal(p.name).withStyle(ChatFormatting.GRAY),
                        b -> select(p.uuid)).bounds(LEFT, y, LIST_W, 20).build());
                default -> this.addRenderableWidget(Button.builder(friendLabel(p), b -> select(p.uuid))
                        .bounds(LEFT, y, LIST_W, 20).build());
            }
        }
        if (pages > 1) {
            int by = this.height - 26;
            Button prev = Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(LEFT, by, 20, 20).build();
            Button next = Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(LEFT + LIST_W - 20, by, 20, 20).build();
            prev.active = page > 0;
            next.active = page < pages - 1;
            this.addRenderableWidget(prev);
            this.addRenderableWidget(next);
        }
    }

    private static Component friendLabel(Social.Person p) {
        MutableComponent label = Component.literal("● ").withStyle(p.online ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                .append(Component.literal(p.name).withStyle(ChatFormatting.WHITE));
        if (p.hostWorld != null) label.append(Component.literal(" \u25B6").withStyle(ChatFormatting.AQUA));
        int unread = ChatLog.unread(p.uuid);
        if (unread > 0) label.append(Component.literal(" (" + unread + ")").withStyle(ChatFormatting.YELLOW));
        return label;
    }

    // --- The chat on the right -------------------------------------------------------------

    private Social.Person selectedFriend() {
        for (Social.Person p : Social.FRIENDS) if (p.uuid.equals(selected)) return p;
        return null;
    }

    private Social.Person selectedBlocked() {
        for (Social.Person p : Social.BLOCKED) if (p.uuid.equals(selected)) return p;
        return null;
    }

    private void buildRight() {
        chatBox = null;
        int rx = rightX(), rw = rightW();
        Social.Person friend = selectedFriend();
        Social.Person blocked = selectedBlocked();
        if (friend != null) {
            this.addRenderableWidget(Button.builder(Component.literal(confirmRemove ? "Sure?" : "Remove"), b -> {
                if (!confirmRemove) { confirmRemove = true; confirmBlock = false; rebuildWidgets(); return; }
                Social.remove(friend.uuid);
                selected = null;
                confirmRemove = false;
            }).bounds(rx + rw - 104, 36, 50, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal(confirmBlock ? "Sure?" : "Block").withStyle(ChatFormatting.RED), b -> {
                if (!confirmBlock) { confirmBlock = true; confirmRemove = false; rebuildWidgets(); return; }
                Social.block(friend.uuid);
                selected = null;
                confirmBlock = false;
            }).bounds(rx + rw - 50, 36, 50, 20).build());
            if (friend.hostWorld != null) {
                Button join = Button.builder(Component.literal("Join").withStyle(ChatFormatting.AQUA), b -> Social.joinFriend(friend.uuid))
                        .bounds(rx + rw - 158, 36, 50, 20).build();
                join.active = Social.status == Social.Status.ONLINE;
                this.addRenderableWidget(join);
            }
            chatBox = new EditBox(this.font, rx, this.height - 28, rw - 48, 20, Component.literal("Message"));
            chatBox.setMaxLength(500);
            chatBox.setHint(Component.literal("Message " + friend.name).withStyle(ChatFormatting.DARK_GRAY));
            chatBox.setValue(chatText);
            chatBox.setResponder(s -> chatText = s);
            this.addRenderableWidget(chatBox);
            Button send = Button.builder(Component.literal("Send"), b -> sendChat()).bounds(rx + rw - 44, this.height - 28, 44, 20).build();
            send.active = Social.status == Social.Status.ONLINE;
            this.addRenderableWidget(send);
        } else if (blocked == null && HafniumConfig.socialOn && Social.status == Social.Status.ONLINE) {
            int bx = rx + (rw - 150) / 2, by = this.height / 2 + 24;
            codeBox = new EditBox(this.font, bx, by, 104, 20, Component.literal("World code"));
            codeBox.setMaxLength(9);
            codeBox.setHint(Component.literal("hf-......").withStyle(ChatFormatting.DARK_GRAY));
            codeBox.setValue(codeText);
            codeBox.setResponder(s -> codeText = s);
            this.addRenderableWidget(codeBox);
            this.addRenderableWidget(Button.builder(Component.literal("Join"), b -> joinCode()).bounds(bx + 108, by, 42, 20).build());
        } else if (blocked != null) {
            this.addRenderableWidget(Button.builder(Component.literal("Unblock"), b -> {
                Social.unblock(blocked.uuid);
                selected = null;
            }).bounds(rx + rw - 60, 36, 60, 20).build());
        }
    }

    private void select(String uuid) {
        selected = uuid;
        scroll = 0;
        chatText = "";
        confirmRemove = false;
        confirmBlock = false;
        Social.viewing = uuid;
        ChatLog.markRead(uuid);
        rebuildWidgets();
        focusChat = true;
    }

    private void sendChat() {
        Social.Person friend = selectedFriend();
        if (friend == null || chatText.isBlank() || Social.status != Social.Status.ONLINE) return;
        Social.message(friend.uuid, chatText);
        chatText = "";
        scroll = 0;
        if (chatBox != null) chatBox.setValue("");
    }

    private void joinCode() {
        if (codeText.isBlank()) return;
        Social.joinCode(codeText);
    }

    private void sendRequest() {
        if (addText.isBlank() || Social.status != Social.Status.ONLINE) return;
        Social.addFriend(addText);
        addText = "";
        if (addBox != null) addBox.setValue("");
    }

    // --- Input -------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
        if (enter && chatBox != null && getFocused() == chatBox) {
            sendChat();
            return true;
        }
        if (enter && codeBox != null && getFocused() == codeBox) {
            joinCode();
            return true;
        }
        if (enter && addBox != null && getFocused() == addBox) {
            sendRequest();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (mx >= rightX() && selectedFriend() != null) {
            scroll = Math.max(0, scroll + (v > 0 ? 3 : -3));
            return true;
        }
        if (mx < rightX()) {
            int before = page;
            page = Math.max(0, page + (v > 0 ? -1 : 1));
            if (page != before) rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    // --- Drawing ----------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (seenVersion != Social.version) {
            if (selected != null && selectedFriend() == null && selectedBlocked() == null) selected = null;
            if (selected != null) ChatLog.markRead(selected);
            rebuildWidgets();
        }
        if (focusChat && chatBox != null) {
            this.setFocused(chatBox);
            focusChat = false;
        }
        int rx = rightX(), rw = rightW();
        g.fill(rx, 34, rx + rw, this.height - 32, 0x80101014);
        super.extractRenderState(g, mouseX, mouseY, delta);

        g.text(this.font, "Hafnium Friends", LEFT, 12, 0xFFFFFFFF);
        g.text(this.font, statusLine(), LEFT + 96, 12, statusColor());
        if (Social.notice != null) {
            g.text(this.font, this.font.plainSubstrByWidth(Social.notice, this.width - 150), LEFT, 24, 0xFFFF8080);
        }
        for (int i = 0; i < headers.size(); i++) {
            g.text(this.font, headerText.get(i), LEFT + 2, headers.get(i)[0] + 6, 0xFFAAAAB4);
        }
        drawRight(g, rx, rw);
    }

    private String statusLine() {
        return switch (Social.status) {
            case OFF -> HafniumConfig.socialOn ? "Starting..." : "Turned off";
            case CONNECTING -> "Connecting...";
            case ONLINE -> "Online as " + Social.myName;
            case SIGN_IN_FAILED -> "Couldn't sign in";
            case NO_CONNECTION -> "Can't reach the server";
        };
    }

    private int statusColor() {
        return switch (Social.status) {
            case ONLINE -> 0xFF7CE07C;
            case SIGN_IN_FAILED, NO_CONNECTION -> 0xFFFF8080;
            default -> 0xFFAAAAB4;
        };
    }

    private void drawRight(GuiGraphicsExtractor g, int rx, int rw) {
        if (!HafniumConfig.socialOn) {
            center(g, "Friends and chat are turned off.", rx, rw, this.height / 2 - 4, 0xFFAAAAB4);
            return;
        }
        if (Social.status == Social.Status.SIGN_IN_FAILED) {
            center(g, "Couldn't sign in with your Minecraft account.", rx, rw, this.height / 2 - 10, 0xFFFF8080);
            String why = Social.signInProblem != null ? Social.signInProblem : "Friends need a real Minecraft account.";
            center(g, this.font.plainSubstrByWidth(why, rw - 8), rx, rw, this.height / 2 + 2, 0xFFAAAAB4);
            return;
        }
        Social.Person friend = selectedFriend();
        Social.Person blocked = selectedBlocked();
        if (blocked != null) {
            g.text(this.font, blocked.name + " is blocked", rx + 6, 42, 0xFFFF8080);
            center(g, "They can't message you or send requests.", rx, rw, this.height / 2 - 4, 0xFFAAAAB4);
            return;
        }
        if (friend == null) {
            center(g, Social.FRIENDS.isEmpty() ? "Add a friend by their Minecraft name." : "Pick a friend to chat.", rx, rw, this.height / 2 - 10, 0xFFAAAAB4);
            center(g, "They need Hafnium too.", rx, rw, this.height / 2 + 2, 0xFF77777F);
            if (Social.status == Social.Status.ONLINE) center(g, "Or join a world with its code:", rx, rw, this.height / 2 + 14, 0xFF8A8A92);
            return;
        }
        g.text(this.font, friend.name, rx + 6, 39, 0xFFFFFFFF);
        String seen = friend.online ? "Online" : friend.lastSeen > 0 ? "Last seen " + when(friend.lastSeen) : "Offline";
        if (friend.hostWorld != null) seen = "Hosting " + friend.hostWorld;
        g.text(this.font, this.font.plainSubstrByWidth(seen, rw - 170), rx + 6, 49, friend.hostWorld != null ? 0xFF7CD8E0 : friend.online ? 0xFF7CE07C : 0xFF8A8A92);

        // The chat, newest at the bottom. Scrolling moves back through older lines.
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (ChatLog.Line line : ChatLog.get(friend.uuid)) {
            boolean mine = line.from != null && line.from.equals(Social.myUuid);
            String head = (mine ? "You" : line.name) + "  " + when(line.sentAt) + (line.waited ? "  (while you were away)" : "");
            lines.add(Component.literal(head).getVisualOrderText());
            colors.add(mine ? 0xFF8FB4FF : 0xFFC8C8D0);
            for (FormattedCharSequence part : this.font.split(Component.literal(line.text), rw - 12)) {
                lines.add(part);
                colors.add(0xFFFFFFFF);
            }
        }
        int top = 62, bottom = this.height - 36;
        int fit = Math.max(1, (bottom - top) / 10);
        scroll = Math.min(scroll, Math.max(0, lines.size() - fit));
        int end = lines.size() - scroll;
        int start = Math.max(0, end - fit);
        int y = bottom - (end - start) * 10;
        for (int i = start; i < end; i++, y += 10) {
            g.text(this.font, lines.get(i), rx + 6, y, colors.get(i));
        }
        if (lines.isEmpty()) center(g, "Say hi to " + friend.name + "!", rx, rw, (top + bottom) / 2, 0xFF8A8A92);
    }

    private void center(GuiGraphicsExtractor g, String text, int x, int w, int y, int color) {
        g.centeredText(this.font, text, x + w / 2, y, color);
    }

    private static String when(long millis) {
        ZonedDateTime t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
        return t.toLocalDate().equals(LocalDate.now()) ? TODAY.format(t) : OLDER.format(t);
    }

    @Override
    public void onClose() {
        Social.viewing = null;
        this.minecraft.gui.setScreen(parent);
    }
}
