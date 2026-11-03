package com.reality.mobcraft.rts;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TeamColorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * /teams: every team on the server at a glance, for anyone.
 *
 *   Each team by its chosen name in its own color, how many players it has,
 *   then each member with their mob, a crown for the team's RTS king, and
 *   "offline" for anyone not on. Players without a team are listed at the end.
 *
 * /myteam: your own team, and changing it.
 *
 *   /myteam                  your team's name, color, player count and players
 *   /myteam name <anything>  renames it, any words at all, up to 32 letters
 *   /myteam color <color>    any of the 16 chat colors, like red or dark_aqua
 *
 * Making, joining and leaving teams is in TeamMembership.
 *
 * If your team's RTS king is online, only the king can rename or recolor it.
 * Otherwise anyone on the team can. The name shown is the team's display
 * name, so the team's id used by /team stays the same and admin commands
 * keep working.
 */
public final class TeamList {

    private static final int MAX_NAME = 32;
    private static final int WHITE = 0xFFFFFF;
    private static final int GRAY = 0xAAAAAA;

    private TeamList() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("teams").executes(TeamList::show));
        LiteralArgumentBuilder<CommandSourceStack> myteam = Commands.literal("myteam")
                .executes(TeamList::mine)
                .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(TeamList::rename)))
                .then(Commands.literal("color")
                        .then(Commands.argument("color", TeamColorArgument.teamColor())
                                .executes(TeamList::recolor)));
        TeamMembership.attach(myteam);
        dispatcher.register(myteam);
    }

    // --- /teams -------------------------------------------------------------------

    private static int show(CommandContext<CommandSourceStack> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        List<PlayerTeam> teams = new ArrayList<>(server.getScoreboard().getPlayerTeams());
        teams.sort(Comparator.comparing(TeamList::nameOf, String.CASE_INSENSITIVE_ORDER));

        MutableComponent text = Component.literal("Teams (" + teams.size() + ")").withStyle(ChatFormatting.BOLD);
        if (teams.isEmpty()) text.append(Component.literal("\nNo teams yet. Make one with /myteam create <name>.")
                .withStyle(ChatFormatting.GRAY));

        for (PlayerTeam team : teams) {
            int color = rgbOf(team);
            List<String> members = new ArrayList<>(team.getPlayers());
            members.sort(String.CASE_INSENSITIVE_ORDER);
            text.append(colored("\n" + nameOf(team), color, true));
            text.append(Component.literal("  " + count(members.size())).withStyle(ChatFormatting.GRAY));
            if (members.isEmpty()) text.append(Component.literal("\n  nobody").withStyle(ChatFormatting.DARK_GRAY));
            for (String name : members) text.append(memberLine(server, name, color));
        }

        List<String> loners = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() == null) loners.add(player.getName().getString());
        }
        if (!loners.isEmpty()) {
            loners.sort(String.CASE_INSENSITIVE_ORDER);
            text.append(Component.literal("\nNo team").withStyle(ChatFormatting.GRAY, ChatFormatting.BOLD));
            text.append(Component.literal("  " + count(loners.size())).withStyle(ChatFormatting.GRAY));
            for (String name : loners) text.append(memberLine(server, name, GRAY));
        }
        ctx.getSource().sendSuccess(() -> text, false);
        return teams.size();
    }

    // --- /myteam ------------------------------------------------------------------

    private static int mine(CommandContext<CommandSourceStack> ctx) {
        PlayerTeam team = teamOf(ctx.getSource());
        if (team == null) return 0;
        MinecraftServer server = ctx.getSource().getServer();
        int color = rgbOf(team);
        List<String> members = new ArrayList<>(team.getPlayers());
        members.sort(String.CASE_INSENSITIVE_ORDER);

        MutableComponent text = colored(nameOf(team), color, true);
        text.append(Component.literal("  " + count(members.size()) + ", color " + colorName(team))
                .withStyle(ChatFormatting.GRAY));
        String leader = TeamMembership.leaderName(server, team);
        if (leader != null) text.append(Component.literal("\nLeader: " + leader).withStyle(ChatFormatting.GRAY));
        for (String name : members) text.append(memberLine(server, name, color));
        text.append(Component.literal("\nChange it with /myteam name <anything> or /myteam color <color>."
                + " Bring players in with /myteam invite <player>.")
                .withStyle(ChatFormatting.DARK_GRAY));
        ctx.getSource().sendSuccess(() -> text, false);
        return members.size();
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        PlayerTeam team = teamOf(source);
        if (team == null || !mayChange(source, team)) return 0;
        String name = StringArgumentType.getString(ctx, "name").trim();
        if (name.isEmpty()) {
            source.sendFailure(Component.literal("The name can't be empty."));
            return 0;
        }
        if (name.length() > MAX_NAME) {
            source.sendFailure(Component.literal("That's too long. Team names can be up to " + MAX_NAME + " letters."));
            return 0;
        }
        team.setDisplayName(Component.literal(name));
        tellTeam(source.getServer(), team, source.getPlayer().getName().getString() + " renamed the team to " + name + ".");
        return 1;
    }

    private static int recolor(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        PlayerTeam team = teamOf(source);
        if (team == null || !mayChange(source, team)) return 0;
        TeamColor color = TeamColorArgument.getTeamColor(ctx, "color");
        team.setColor(Optional.of(color));
        tellTeam(source.getServer(), team, source.getPlayer().getName().getString() + " made the team "
                + color.getSerializedName() + ".");
        return 1;
    }

    /** Your team, or a message saying why there isn't one. */
    private static PlayerTeam teamOf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players are on teams."));
            return null;
        }
        PlayerTeam team = player.getTeam();
        if (team == null) source.sendFailure(Component.literal("You're not on a team. Make one with /myteam create <name>, or get a team to invite you."));
        return team;
    }

    /** The king decides if the team's king is online. Otherwise anyone on the team can. */
    private static boolean mayChange(CommandSourceStack source, PlayerTeam team) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return false;
        if (RtsControl.isKing(player)) return true;
        for (ServerPlayer other : source.getServer().getPlayerList().getPlayers()) {
            if (other != player && other.getTeam() == team && RtsControl.isKing(other)) {
                source.sendFailure(Component.literal("Only your king, " + other.getName().getString()
                        + ", can change the team."));
                return false;
            }
        }
        return true;
    }

    /** A message to everyone on the team who's online, in the team's color. */
    static void tellTeam(MinecraftServer server, PlayerTeam team, String message) {
        Component line = colored(message, rgbOf(team), false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() == team) player.sendSystemMessage(line);
        }
    }

    // --- Shared -------------------------------------------------------------------

    /** The team's color as RGB, white if it has none. */
    private static int rgbOf(PlayerTeam team) {
        Optional<TeamColor> color = team.getColor();
        return color.isPresent() ? color.get().rgb() : WHITE;
    }

    private static String colorName(PlayerTeam team) {
        Optional<TeamColor> color = team.getColor();
        return color.isPresent() ? color.get().getSerializedName() : "none";
    }

    private static MutableComponent colored(String text, int rgb, boolean bold) {
        return Component.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(bold));
    }

    /** The team's chosen name, or its id if it never got one. */
    static String nameOf(PlayerTeam team) {
        String shown = team.getDisplayName().getString();
        return shown.isBlank() ? team.getName() : shown;
    }

    private static String count(int players) {
        return players + (players == 1 ? " player" : " players");
    }

    /** "  Name - Silverfish (king)", or "  Name - offline". */
    private static MutableComponent memberLine(MinecraftServer server, String name, int color) {
        MutableComponent line = colored("\n  " + name, color, false);
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        if (player == null) {
            return line.append(Component.literal(" - offline").withStyle(ChatFormatting.DARK_GRAY));
        }
        MobType type = PlayerMob.get(player);
        line.append(Component.literal(" - " + (type == null ? "no mob" : type.displayName)).withStyle(ChatFormatting.GRAY));
        if (RtsControl.isKing(player)) line.append(Component.literal(" (king)").withStyle(ChatFormatting.GOLD));
        return line;
    }
}
