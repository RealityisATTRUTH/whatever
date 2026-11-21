package com.reality.mobcraft.client;

import com.reality.mobcraft.mob.MobAttachments;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Team & Pets: everything /myteam does, as buttons, plus your pet counts.
 * Opened with Y, or from MobCraft Settings.
 *
 * Every button just runs the matching /myteam command, so the server still decides
 * who may do what and explains in chat when something isn't allowed. The screen
 * redraws itself when your team changes.
 *
 *   No team    a name box and Create, and Accept Invite
 *   On a team  your team's name, color and players; rename it; pick one of the
 *              16 colors; pick a player to invite or kick; leave (click twice);
 *              accept an invite to another team
 *   Pets       how many pets you have now, have claimed, lost and released,
 *              how to claim one, and My Pets..., which opens PetsScreen
 */
public class TeamScreen extends Screen {

    private static final int WIDTH = 260;
    private static final int ROW = 24;
    private static final int SWATCH = 14;
    private static final int LEAVE_CONFIRM_TICKS = 60;

    private final Screen parent;
    private EditBox nameBox;
    private String typedName = "";
    private int pick;
    private int leaveArmed;
    private String shownState = "";

    /** Where things were drawn in init(), for the text drawn in extractRenderState. */
    private int membersTop;
    private int petsTop;
    private final List<Button> swatches = new ArrayList<>();

    public TeamScreen(Screen parent) {
        super(Component.literal("Team & Pets"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        swatches.clear();
        shownState = state();
        int left = this.width / 2 - WIDTH / 2;
        int y = 30;
        PlayerTeam team = team();

        nameBox = new EditBox(this.font, left, 0, WIDTH - 80, 20, Component.literal("Team name"));
        nameBox.setMaxLength(32);
        nameBox.setValue(typedName);
        nameBox.setResponder(text -> typedName = text);

        if (team == null) {
            y += 14; // "You're not on a team."
            nameBox.setY(y);
            nameBox.setHint(Component.literal("Name your new team"));
            this.addRenderableWidget(nameBox);
            this.addRenderableWidget(Button.builder(Component.literal("Create"), b -> {
                String name = typedName.trim();
                if (!name.isEmpty()) run("myteam create " + name);
                typedName = "";
            }).bounds(left + WIDTH - 76, y, 76, 20).build());
            y += ROW;
            this.addRenderableWidget(Button.builder(Component.literal("Accept Invite"), b -> run("myteam accept"))
                    .bounds(left, y, WIDTH, 20).build());
            y += ROW + 6;
        } else {
            // The member list is drawn as text; leave room for it.
            membersTop = y + 14;
            int rows = (Math.max(1, team.getPlayers().size()) + 1) / 2;
            y = membersTop + rows * 11 + 6;

            nameBox.setY(y);
            nameBox.setHint(Component.literal("New team name"));
            this.addRenderableWidget(nameBox);
            this.addRenderableWidget(Button.builder(Component.literal("Rename"), b -> {
                String name = typedName.trim();
                if (!name.isEmpty()) run("myteam name " + name);
                typedName = "";
            }).bounds(left + WIDTH - 76, y, 76, 20).build());
            y += ROW;

            // The 16 colors, each a small square.
            int step = (WIDTH + 2) / TeamColor.VALUES.size();
            for (int i = 0; i < TeamColor.VALUES.size(); i++) {
                TeamColor color = TeamColor.VALUES.get(i);
                Button swatch = Button.builder(Component.empty(), b -> run("myteam color " + color.getSerializedName()))
                        .bounds(left + i * step, y, SWATCH, SWATCH).build();
                swatch.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.literal(color.getSerializedName().replace('_', ' '))));
                swatches.add(this.addRenderableWidget(swatch));
            }
            y += SWATCH + 8;

            // Pick a player, then invite or kick them.
            List<String> people = people(team);
            if (pick >= people.size()) pick = 0;
            String picked = people.isEmpty() ? null : people.get(pick);
            boolean onTeam = picked != null && team.getPlayers().contains(picked);
            this.addRenderableWidget(Button.builder(Component.literal(picked == null ? "Nobody else is online"
                    : "< " + picked + (onTeam ? " (teammate)" : "") + " >"), b -> {
                pick++;
                this.rebuildWidgets();
            }).bounds(left, y, WIDTH - 80, 20).build()).active = people.size() > 1;
            Button act = this.addRenderableWidget(Button.builder(Component.literal(onTeam ? "Kick" : "Invite"), b -> {
                if (picked != null) run("myteam " + (onTeam ? "kick " : "invite ") + picked);
            }).bounds(left + WIDTH - 76, y, 76, 20).build());
            act.active = picked != null;
            y += ROW;

            int half = (WIDTH - 4) / 2;
            this.addRenderableWidget(Button.builder(Component.literal(leaveArmed > 0 ? "Click again to leave" : "Leave Team"), b -> {
                if (leaveArmed > 0) {
                    leaveArmed = 0;
                    run("myteam leave");
                } else {
                    leaveArmed = LEAVE_CONFIRM_TICKS;
                    this.rebuildWidgets();
                }
            }).bounds(left, y, half, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Accept Invite"), b -> run("myteam accept"))
                    .bounds(left + WIDTH - half, y, half, 20).build());
            y += ROW + 6;
        }

        // Pets.
        petsTop = y;
        y += 26;
        this.addRenderableWidget(Button.builder(Component.literal("My Pets..."),
                b -> this.minecraft.gui.setScreen(new PetsScreen(this))).bounds(left, y, WIDTH, 20).build());
        y += ROW + 6;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(left, y, WIDTH, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        if (leaveArmed > 0 && --leaveArmed == 0) this.rebuildWidgets();
        // The team or the players online changed: show the new state.
        if (!state().equals(shownState)) this.rebuildWidgets();
    }

    /** Sends a command as if typed, without the slash. */
    private void run(String command) {
        if (this.minecraft.player == null) return;
        this.minecraft.player.connection.sendCommand(command);
    }

    private PlayerTeam team() {
        return this.minecraft.player == null ? null : this.minecraft.player.getTeam();
    }

    /** Everyone online but you, then teammates who are offline, for the invite/kick picker. */
    private List<String> people(PlayerTeam team) {
        List<String> names = new ArrayList<>();
        String me = this.minecraft.player.getScoreboardName();
        for (PlayerInfo info : this.minecraft.player.connection.getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (!name.equals(me)) names.add(name);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (team != null) {
            List<String> offline = new ArrayList<>();
            for (String member : team.getPlayers()) if (!member.equals(me) && !names.contains(member)) offline.add(member);
            offline.sort(String.CASE_INSENSITIVE_ORDER);
            names.addAll(offline);
        }
        return names;
    }

    /** A summary of everything shown, to notice changes. */
    private String state() {
        if (this.minecraft == null || this.minecraft.player == null) return "";
        PlayerTeam team = team();
        StringBuilder s = new StringBuilder();
        if (team != null) {
            s.append(team.getName()).append('|').append(team.getDisplayName().getString()).append('|')
                    .append(team.getColor().map(TeamColor::getSerializedName).orElse("")).append('|')
                    .append(new java.util.TreeSet<>(team.getPlayers()));
        }
        s.append('|').append(people(team));
        return s.toString();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int center = this.width / 2;
        int left = center - WIDTH / 2;
        graphics.centeredText(this.font, "Team & Pets", center, 12, 0xFFFFFFFF);

        PlayerTeam team = team();
        if (team == null) {
            graphics.centeredText(this.font, "You're not on a team. Make one, or accept an invite.", center, 30, 0xFFAAAAAA);
        } else {
            Optional<TeamColor> color = team.getColor();
            int rgb = 0xFF000000 | (color.isPresent() ? color.get().rgb() : 0xFFFFFF);
            String name = team.getDisplayName().getString();
            if (name.isBlank()) name = team.getName();
            int count = team.getPlayers().size();
            graphics.text(this.font, name, left, 30, rgb, true);
            graphics.text(this.font, count + (count == 1 ? " player" : " players"),
                    left + this.font.width(name) + 8, 30, 0xFFAAAAAA, true);

            List<String> members = new ArrayList<>(team.getPlayers());
            members.sort(String.CASE_INSENSITIVE_ORDER);
            String me = this.minecraft.player.getScoreboardName();
            List<String> online = new ArrayList<>();
            for (PlayerInfo info : this.minecraft.player.connection.getOnlinePlayers()) online.add(info.getProfile().name());
            for (int i = 0; i < members.size(); i++) {
                String member = members.get(i);
                int x = left + 8 + (i % 2) * (WIDTH / 2);
                int y = membersTop + (i / 2) * 11;
                String line = member + (member.equals(me) ? " (you)" : online.contains(member) ? "" : " - offline");
                graphics.text(this.font, line, x, y, online.contains(member) ? 0xFFE0E0E0 : 0xFF707070, false);
            }

            // Paint the color squares, and ring the team's current color.
            for (int i = 0; i < swatches.size(); i++) {
                Button swatch = swatches.get(i);
                TeamColor c = TeamColor.VALUES.get(i);
                int x = swatch.getX(), y = swatch.getY();
                if (color.isPresent() && color.get() == c) graphics.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1, 0xFFFFFFFF);
                graphics.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, 0xFF000000 | c.rgb());
            }
        }

        List<Integer> pets = this.minecraft.player == null ? null : this.minecraft.player.getAttached(MobAttachments.PET_COUNTS);
        if (pets == null || pets.size() < 4) pets = List.of(0, 0, 0, 0);
        graphics.text(this.font, "Pets", left, petsTop, 0xFFFFFFFF, true);
        graphics.text(this.font, pets.get(0) + " now   " + pets.get(1) + " claimed   " + pets.get(2) + " lost   "
                + pets.get(3) + " released", left + 30, petsTop, 0xFF55FF55, false);
        graphics.text(this.font, "Claim your own kind: sneak + right-click, empty hand.", left, petsTop + 12, 0xFF888888, false);
    }
}
