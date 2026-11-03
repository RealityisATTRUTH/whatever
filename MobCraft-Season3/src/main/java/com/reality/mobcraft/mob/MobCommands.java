package com.reality.mobcraft.mob;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reality.mobcraft.net.OpenSettingsPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Admin commands.
 *
 *   /setmob <players> <mob>          give players a mob ("none" removes it)
 *   /curse <players> [seconds]       curse players, default 3:00
 *   /uncurse <players>               lift a curse early
 *   /mobenergy <players> <amount>    set energy, for testing
 *   /mobcraftsettings                switch your mob's abilities on or off (anyone)
 */
public final class MobCommands {

    private static final int DEFAULT_CURSE_SECONDS = 180;



    private MobCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        List<String> mobIds = new ArrayList<>();
        mobIds.add("none");
        for (MobType type : MobType.values()) mobIds.add(type.id());

        dispatcher.register(Commands.literal("setmob")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("mob", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(mobIds, builder))
                                .executes(MobCommands::setMob))));

        dispatcher.register(Commands.literal("curse")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> curse(ctx, DEFAULT_CURSE_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                .executes(ctx -> curse(ctx, IntegerArgumentType.getInteger(ctx, "seconds"))))));

        dispatcher.register(Commands.literal("uncurse")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(MobCommands::uncurse)));

        // Anyone can open their own settings. Three spellings so it works however it's typed.
        for (String name : List.of("mobcraftsettings", "MobCraftSettings", "MobcraftSetting")) {
            dispatcher.register(Commands.literal(name).executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                ServerPlayNetworking.send(player, OpenSettingsPayload.INSTANCE);
                return 1;
            }));
        }

        dispatcher.register(Commands.literal("mobenergy")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0))
                                .executes(MobCommands::setEnergy))));
    }

    private static int setMob(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "mob");
        MobType type = MobType.byId(id);
        if (type == null && !id.equalsIgnoreCase("none")) {
            ctx.getSource().sendFailure(Component.literal("Unknown mob: " + id));
            return 0;
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            MobTicker.assign(player, type);
            player.sendSystemMessage(Component.literal(type == null
                    ? "You no longer have a mob."
                    : "You are now the " + type.displayName + "."));
        }
        String label = type == null ? "no mob" : type.displayName;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set " + targets.size() + " player(s) to " + label + "."), true);
        return targets.size();
    }

    private static int curse(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        int count = 0;
        for (ServerPlayer player : targets) {
            if (PlayerMob.get(player) == null) continue;
            MobTicker.curse(player, seconds * 20);
            player.sendSystemMessage(Component.literal("You have been cursed for " + format(seconds) + "."));
            count++;
        }
        int cursed = count;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Cursed " + cursed + " player(s) for " + format(seconds) + "."), true);
        return cursed;
    }

    private static int uncurse(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            MobTicker.uncurse(player);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Lifted the curse on " + targets.size() + " player(s)."), true);
        return targets.size();
    }

    private static int setEnergy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        float amount = FloatArgumentType.getFloat(ctx, "amount");
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        for (ServerPlayer player : targets) {
            PlayerMob.setEnergy(player, amount);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Set energy for " + targets.size() + " player(s)."), false);
        return targets.size();
    }

    private static String format(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
