package com.reality.mobcraft.rts;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reality.mobcraft.mob.MobAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import com.reality.mobcraft.mob.Jeff;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Who can command which mobs in RTS mode.
 *
 *   Your own mobs      always.
 *   Your own kind      any mob of the same kind as your mob: a Blaze player
 *                      commands every blaze, a Rabbit player every rabbit.
 *   Permission         /rtsallow <player> lets that player command your mobs,
 *                      /rtsdeny <player> takes it back, /rtsallowed lists them.
 *   King               an admin makes one player per team the king with
 *                      /rtsking <player>. The king can command the mobs of
 *                      every teammate who's online, no permission needed.
 *                      /rtsking remove <player> takes the crown away.
 *
 * Teams are the game's own /team teams. Only mobs of players who are online
 * can be commanded by someone else. Their owner still has their Follow,
 * Defend and Attack settings, but an RTS order from anyone holds a mob in
 * place for 60 seconds, the same as the owner's own orders.
 */
public final class RtsControl {

    private RtsControl() {}

    /** Works on both sides: the client uses it to pick and glow mobs, the server to check orders. */
    public static boolean canCommand(Entity mob, Player commander) {
        // Jeff, while powered up, commands every mob in the world.
        if (mob instanceof Mob && Jeff.isPowered(commander)) return true;
        return commandsByOwner(mob, commander) || isOwnKind(mob, commander);
    }

    /** Whether this mob is the same kind as the commander's own mob, like a blaze for a Blaze player. */
    public static boolean isOwnKind(Entity mob, Player commander) {
        if (!(mob instanceof Mob)) return false;
        MobType type = PlayerMob.get(commander);
        return type != null && mob.getType() == kindOf(type);
    }

    private static final Map<MobType, EntityType<?>> KINDS = new EnumMap<>(MobType.class);

    /** The vanilla mob a MobType is: MobType.IRON_GOLEM is minecraft:iron_golem, and so on. */
    public static EntityType<?> kindOf(MobType type) {
        return KINDS.computeIfAbsent(type, t -> {
            Identifier id = Identifier.withDefaultNamespace(t.name().toLowerCase(Locale.ROOT));
            return BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.getValue(id) : null;
        });
    }

    /** Mobs owned by this player or by players they command for. Jeff's override doesn't count here. */
    public static boolean commandsByOwner(Entity mob, Player commander) {
        String owner = mob.getAttached(MobAttachments.OWNER);
        if (owner == null) return false;
        if (owner.equals(commander.getStringUUID())) return true;
        return commander.getAttachedOrElse(MobAttachments.RTS_COMMANDS, List.of()).contains(owner);
    }

    /** Whether this player has another player's mobs to command too. */
    public static boolean commandsOthers(Player player) {
        return !player.getAttachedOrElse(MobAttachments.RTS_COMMANDS, List.of()).isEmpty();
    }

    public static boolean isKing(Player player) {
        return Boolean.TRUE.equals(player.getAttached(MobAttachments.RTS_KING));
    }

    // --- Server -------------------------------------------------------------------

    /** Every two seconds: works out whose mobs each online player can command. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (ServerPlayer commander : players) {
            PlayerTeam team = commander.getTeam();
            boolean king = isKing(commander) && team != null;
            String me = commander.getStringUUID();
            List<String> can = new ArrayList<>();
            for (ServerPlayer owner : players) {
                if (owner == commander) continue;
                boolean allowed = owner.getAttachedOrElse(MobAttachments.RTS_ALLOWED, List.of()).contains(me);
                boolean teammate = king && owner.getTeam() == team;
                if (allowed || teammate) can.add(owner.getStringUUID());
            }
            List<String> before = commander.getAttachedOrElse(MobAttachments.RTS_COMMANDS, List.of());
            if (before.equals(can)) continue;
            if (can.isEmpty()) {
                commander.removeAttached(MobAttachments.RTS_COMMANDS);
            } else {
                commander.setAttached(MobAttachments.RTS_COMMANDS, List.copyOf(can));
            }
        }
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rtsallow")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> allow(ctx, true))));
        dispatcher.register(Commands.literal("rtsdeny")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> allow(ctx, false))));
        dispatcher.register(Commands.literal("rtsallowed").executes(RtsControl::listAllowed));

        dispatcher.register(Commands.literal("rtsking")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> crown(ctx, true)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> crown(ctx, false)))));
    }

    private static int allow(CommandContext<CommandSourceStack> ctx, boolean allow) throws CommandSyntaxException {
        ServerPlayer owner = ctx.getSource().getPlayerOrException();
        ServerPlayer other = EntityArgument.getPlayer(ctx, "player");
        if (other == owner) {
            ctx.getSource().sendFailure(Component.literal("You can always command your own mobs."));
            return 0;
        }
        List<String> allowed = new ArrayList<>(owner.getAttachedOrElse(MobAttachments.RTS_ALLOWED, List.of()));
        String id = other.getStringUUID();
        allowed.remove(id);
        if (allow) allowed.add(id);
        if (allowed.isEmpty()) {
            owner.removeAttached(MobAttachments.RTS_ALLOWED);
        } else {
            owner.setAttached(MobAttachments.RTS_ALLOWED, List.copyOf(allowed));
        }
        String otherName = other.getName().getString();
        String ownerName = owner.getName().getString();
        if (allow) {
            owner.sendSystemMessage(Component.literal(otherName + " can now command your mobs in RTS."));
            other.sendSystemMessage(Component.literal(ownerName + " lets you command their mobs in RTS."));
        } else {
            owner.sendSystemMessage(Component.literal(otherName + " can no longer command your mobs."));
            other.sendSystemMessage(Component.literal(ownerName + " took back command of their mobs."));
        }
        return 1;
    }

    private static int listAllowed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer owner = ctx.getSource().getPlayerOrException();
        List<String> allowed = owner.getAttachedOrElse(MobAttachments.RTS_ALLOWED, List.of());
        if (allowed.isEmpty()) {
            owner.sendSystemMessage(Component.literal("Nobody else can command your mobs. Use /rtsallow <player>."));
            return 0;
        }
        List<String> names = new ArrayList<>();
        MinecraftServer server = ctx.getSource().getServer();
        for (String id : allowed) {
            ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(id));
            names.add(online != null ? online.getName().getString() : "an offline player");
        }
        owner.sendSystemMessage(Component.literal("These players can command your mobs: " + String.join(", ", names)));
        return allowed.size();
    }

    private static int crown(CommandContext<CommandSourceStack> ctx, boolean crown) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String name = player.getName().getString();
        if (!crown) {
            player.removeAttached(MobAttachments.RTS_KING);
            player.sendSystemMessage(Component.literal("You are no longer the king."));
            ctx.getSource().sendSuccess(() -> Component.literal(name + " is no longer a king."), true);
            return 1;
        }
        PlayerTeam team = player.getTeam();
        if (team == null) {
            ctx.getSource().sendFailure(Component.literal(name + " isn't on a team. Add them with /team join first."));
            return 0;
        }
        // One king per team: anyone else on the team loses the crown.
        for (ServerPlayer other : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            if (other != player && other.getTeam() == team && isKing(other)) {
                other.removeAttached(MobAttachments.RTS_KING);
                other.sendSystemMessage(Component.literal(name + " is the new king of your team."));
            }
        }
        player.setAttached(MobAttachments.RTS_KING, true);
        player.sendSystemMessage(Component.literal(
                "You are the king of " + team.getName() + ". You can command every online teammate's mobs in RTS."));
        ctx.getSource().sendSuccess(() -> Component.literal(name + " is now the king of " + team.getName() + "."), true);
        return 1;
    }
}
