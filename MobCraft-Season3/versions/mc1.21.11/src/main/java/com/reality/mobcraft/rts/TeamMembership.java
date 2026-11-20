package com.reality.mobcraft.rts;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reality.mobcraft.MobCraft;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Players making and running their own teams, without an admin.
 *
 *   /myteam create <name>     makes a team with any name and puts you on it.
 *                             You're its leader. You can't be on another team.
 *   /myteam invite <player>   anyone on the team can invite. The invite lasts
 *                             5 minutes. The player clicks it or types accept.
 *   /myteam accept            joins the team you were last invited to,
 *                             leaving your old one.
 *   /myteam leave             leaves your team. A team players made goes away
 *                             once its last player leaves.
 *   /myteam kick <player>     only the team's leader or its RTS king, and the
 *                             king can't be kicked.
 *
 * If the leader leaves, leadership passes to a teammate who's online. Teams
 * made with /team by admins have no leader and are never deleted by this:
 * only their king can kick, and admins keep full control with /team.
 *
 * Team ids for player-made teams start with "mc_", made from the name.
 * Leaders are saved in config/mobcraft-team-leaders.txt.
 */
public final class TeamMembership {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mobcraft-team-leaders.txt");
    private static final String PREFIX = "mc_";
    private static final int INVITE_TICKS = 6000; // 5 minutes
    private static final int MAX_NAME = 32;

    private record Invite(String team, String from, int expires) {}

    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    private static final Map<String, UUID> LEADERS = new HashMap<>();
    private static boolean loaded;

    private TeamMembership() {}

    static void attach(LiteralArgumentBuilder<CommandSourceStack> myteam) {
        myteam.then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(TeamMembership::create)))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(TeamMembership::invite)))
                .then(Commands.literal("accept").executes(TeamMembership::accept))
                .then(Commands.literal("leave").executes(TeamMembership::leave))
                .then(Commands.literal("kick")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    PlayerTeam team = player == null ? null : player.getTeam();
                                    return SharedSuggestionProvider.suggest(
                                            team == null ? List.of() : team.getPlayers(), builder);
                                })
                                .executes(TeamMembership::kick)));
    }

    // --- Commands -----------------------------------------------------------------

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (player.getTeam() != null) {
            source.sendFailure(Component.literal("You're already on " + TeamList.nameOf(player.getTeam())
                    + ". Leave it first with /myteam leave."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name").trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            source.sendFailure(Component.literal("Team names can be 1 to " + MAX_NAME + " letters."));
            return 0;
        }
        ServerScoreboard scoreboard = source.getServer().getScoreboard();
        for (PlayerTeam other : scoreboard.getPlayerTeams()) {
            if (TeamList.nameOf(other).equalsIgnoreCase(name)) {
                source.sendFailure(Component.literal("There's already a team called " + name + "."));
                return 0;
            }
        }
        PlayerTeam team = scoreboard.addPlayerTeam(newId(scoreboard, name));
        team.setDisplayName(Component.literal(name));
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        setLeader(team, player.getUUID());
        source.sendSuccess(() -> Component.literal("You made " + name + ". Invite players with /myteam invite <player>,"
                + " and pick a color with /myteam color.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerTeam team = player.getTeam();
        if (team == null) {
            source.sendFailure(Component.literal("You're not on a team. Make one with /myteam create <name>."));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        if (target == player) {
            source.sendFailure(Component.literal("You're already on your team."));
            return 0;
        }
        if (target.getTeam() == team) {
            source.sendFailure(Component.literal(target.getName().getString() + " is already on your team."));
            return 0;
        }
        String from = player.getName().getString();
        INVITES.put(target.getUUID(), new Invite(team.getName(), from, source.getServer().getTickCount() + INVITE_TICKS));
        MutableComponent message = Component.literal(from + " invited you to " + TeamList.nameOf(team) + ". ")
                .withStyle(ChatFormatting.YELLOW);
        message.append(Component.literal("[Click to join]").withStyle(style -> style
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent.RunCommand("/myteam accept"))));
        message.append(Component.literal(" or type /myteam accept within 5 minutes.").withStyle(ChatFormatting.GRAY));
        target.sendSystemMessage(message);
        source.sendSuccess(() -> Component.literal("Invited " + target.getName().getString() + ".")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        Invite invite = INVITES.remove(player.getUUID());
        if (invite == null || server.getTickCount() > invite.expires()) {
            source.sendFailure(Component.literal("You don't have an invite right now. Invites last 5 minutes."));
            return 0;
        }
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(invite.team());
        if (team == null) {
            source.sendFailure(Component.literal("That team doesn't exist anymore."));
            return 0;
        }
        if (player.getTeam() == team) {
            source.sendFailure(Component.literal("You're already on " + TeamList.nameOf(team) + "."));
            return 0;
        }
        PlayerTeam old = player.getTeam();
        if (old != null) {
            removeFrom(server, player.getScoreboardName(), player.getUUID(), old);
            TeamList.tellTeam(server, old, player.getName().getString() + " left to join " + TeamList.nameOf(team) + ".");
        }
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        TeamList.tellTeam(server, team, player.getName().getString() + " joined the team.");
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        PlayerTeam team = player.getTeam();
        if (team == null) {
            source.sendFailure(Component.literal("You're not on a team."));
            return 0;
        }
        String name = TeamList.nameOf(team);
        removeFrom(source.getServer(), player.getScoreboardName(), player.getUUID(), team);
        TeamList.tellTeam(source.getServer(), team, player.getName().getString() + " left the team.");
        source.sendSuccess(() -> Component.literal("You left " + name + ".").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        PlayerTeam team = player.getTeam();
        if (team == null) {
            source.sendFailure(Component.literal("You're not on a team."));
            return 0;
        }
        if (!RtsControl.isKing(player) && !player.getUUID().equals(leader(team))) {
            source.sendFailure(Component.literal("Only your team's leader or king can kick players."));
            return 0;
        }
        String typed = StringArgumentType.getString(ctx, "player");
        String member = null;
        for (String name : team.getPlayers()) if (name.equalsIgnoreCase(typed)) member = name;
        if (member == null) {
            source.sendFailure(Component.literal(typed + " isn't on your team."));
            return 0;
        }
        if (member.equals(player.getScoreboardName())) {
            source.sendFailure(Component.literal("Use /myteam leave to leave your team."));
            return 0;
        }
        ServerPlayer target = server.getPlayerList().getPlayerByName(member);
        if (target != null && RtsControl.isKing(target)) {
            source.sendFailure(Component.literal("You can't kick the king."));
            return 0;
        }
        String teamName = TeamList.nameOf(team);
        removeFrom(server, member, target == null ? null : target.getUUID(), team);
        if (target != null) {
            target.sendSystemMessage(Component.literal("You were kicked from " + teamName + ".").withStyle(ChatFormatting.RED));
        }
        TeamList.tellTeam(server, team, member + " was kicked by " + player.getName().getString() + ".");
        return 1;
    }

    // --- Leaving, leaders ------------------------------------------------------------

    /**
     * Takes someone off a team. An empty player-made team is removed. If they
     * led it, a teammate who's online takes over.
     */
    private static void removeFrom(MinecraftServer server, String name, UUID id, PlayerTeam team) {
        ServerScoreboard scoreboard = server.getScoreboard();
        scoreboard.removePlayerFromTeam(name);
        if (team.getPlayers().isEmpty() && team.getName().startsWith(PREFIX)) {
            scoreboard.removePlayerTeam(team);
            setLeader(team, null);
            return;
        }
        if (id == null || !id.equals(leader(team))) return;
        ServerPlayer next = null;
        for (String member : team.getPlayers()) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(member);
            if (online != null) {
                next = online;
                break;
            }
        }
        setLeader(team, next == null ? null : next.getUUID());
        if (next != null) TeamList.tellTeam(server, team, next.getName().getString() + " is the team's leader now.");
    }

    /** The team's leader, or null for admin-made teams and teams whose leader left with nobody online. */
    static UUID leader(PlayerTeam team) {
        load();
        return LEADERS.get(team.getName());
    }

    /** The leader's name if they're online, for /myteam. */
    static String leaderName(MinecraftServer server, PlayerTeam team) {
        UUID id = leader(team);
        if (id == null) return null;
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        return online == null ? "offline" : online.getName().getString();
    }

    private static void setLeader(PlayerTeam team, UUID id) {
        load();
        if (id == null) LEADERS.remove(team.getName());
        else LEADERS.put(team.getName(), id);
        save();
    }

    /** A free id from the name: mc_ and up to 12 letters, digits or underscores. */
    private static String newId(ServerScoreboard scoreboard, String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (base.length() > 12) base = base.substring(0, 12);
        if (base.isEmpty()) base = "team";
        String id = PREFIX + base;
        for (int n = 2; scoreboard.getPlayerTeam(id) != null; n++) id = PREFIX + base + "_" + n;
        return id;
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;
        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) continue;
                try {
                    LEADERS.put(parts[0].trim(), UUID.fromString(parts[1].trim()));
                } catch (IllegalArgumentException ignored) {
                    // a broken line, skip it
                }
            }
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't read team leaders", e);
        }
    }

    private static void save() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : LEADERS.entrySet()) lines.add(entry.getKey() + "=" + entry.getValue());
        try {
            Files.createDirectories(FILE.getParent());
            Files.write(FILE, lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't save team leaders", e);
        }
    }
}
