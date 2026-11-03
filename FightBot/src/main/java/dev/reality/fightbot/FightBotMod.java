package dev.reality.fightbot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.reality.fightbot.core.BotProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server mod that turns a Carpet fake player into a fighting bot that picks its style from its inventory. It needs Carpet or
 * Carpet PvP Practice on the same server or singleplayer world. Only use it on servers you run.
 */
public final class FightBotMod implements ModInitializer {
    private static final Map<String, BotController> BOTS = new LinkedHashMap<>();
    private static final Map<String, FlightPractice> PRACTICE = new LinkedHashMap<>();
    private static Path root;
    private static MinecraftServer currentServer;

    @Override
    public void onInitialize() {
        root = FabricLoader.getInstance().getGameDir().resolve("fightbot");
        FlightWatchers.root = root;
        try {
            Path def = root.resolve("profiles").resolve("default.properties");
            if (!Files.exists(def)) new BotProfile().save(def);
        } catch (Exception ignored) {
        }
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            currentServer = server;
            for (BotController c : new ArrayList<>(BOTS.values())) {
                c.tick(server);
                if (c.finished) BOTS.remove(c.botName);
            }
            // Flight samples are taken after the bots act, so each sample shows the result of this tick.
            for (FlightPractice f : new ArrayList<>(PRACTICE.values())) {
                f.tick();
                if (f.finished) PRACTICE.remove(f.bot);
            }
            FlightWatchers.tick(server, msg -> broadcast(server, msg));
            InventoryView.tick(server, name -> {
                BotController c = BOTS.get(name);
                return c == null ? null : c.status();
            });
            Kits.tick(server, msg -> broadcast(server, msg));
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lit(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> word(String name) {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.word());
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(lit("fightbot")
                .then(lit("spawn").then(word("bot").then(word("target")
                        .executes(ctx -> spawn(ctx, "default"))
                        .then(word("profile").executes(ctx -> spawn(ctx, StringArgumentType.getString(ctx, "profile")))))))
                .then(lit("fight").then(word("bot").then(word("target")
                        .executes(ctx -> fight(ctx, "default"))
                        .then(word("profile").executes(ctx -> fight(ctx, StringArgumentType.getString(ctx, "profile")))))))
                .then(lit("stop").then(word("bot").executes(ctx -> {
                    BotController c = BOTS.remove(StringArgumentType.getString(ctx, "bot"));
                    if (c != null) c.finish("stopped");
                    say(ctx, c == null ? "No bot with that name." : "Stopped.");
                    return 1;
                })))
                .then(lit("flight")
                        .then(lit("start").then(word("player")
                                .executes(ctx -> say(ctx, FlightWatchers.start(StringArgumentType.getString(ctx, "player"), "")))
                                .then(word("label").executes(ctx -> say(ctx, FlightWatchers.start(StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "label")))))))
                        .then(lit("stop").then(word("player").executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            FlightPractice f = PRACTICE.remove(name);
                            if (f != null) f.stop();
                            return say(ctx, f != null ? "Stopped flight practice for " + name + "." : FlightWatchers.stop(name));
                        })))
                        .then(lit("why").then(word("bot").executes(ctx -> {
                    BotController c = BOTS.get(StringArgumentType.getString(ctx, "bot"));
                    return say(ctx, c == null ? "No bot with that name is fighting." : c.why());
                })))
                .then(lit("list").executes(ctx -> {
                            List<String> lines = FlightWatchers.list();
                            if (lines.isEmpty() && PRACTICE.isEmpty()) return say(ctx, "Nothing is being recorded.");
                            for (String l : lines) say(ctx, l);
                            for (FlightPractice f : PRACTICE.values()) say(ctx, f.bot + " is running flight practice.");
                            return 1;
                        }))
                        .then(lit("reload").executes(ctx -> {
                            FlightWatchers.reloadConfig();
                            return say(ctx, "Reloaded the mechanics file for Minecraft " + FlightWatchers.config().gameVersion
                                    + (FlightWatchers.config().verified ? ", marked verified." : ", not verified yet."));
                        }))
                        .then(lit("practice").then(word("bot")
                                .executes(ctx -> practice(ctx, 10))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("trials", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> practice(ctx, IntegerArgumentType.getInteger(ctx, "trials")))))))
                .then(lit("kit").then(word("bot").then(word("kit").executes(ctx -> {
                    String msg = Kits.give(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "bot"),
                            StringArgumentType.getString(ctx, "kit"));
                    say(ctx, msg);
                    return 1;
                }))))
                .then(lit("why").then(word("bot").executes(ctx -> {
                    BotController c = BOTS.get(StringArgumentType.getString(ctx, "bot"));
                    return say(ctx, c == null ? "No bot with that name is fighting." : c.why());
                })))
                .then(lit("inv").then(word("bot")
                        // A live screen for a player, or a text list from the console.
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "bot");
                            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer viewer) {
                                return say(ctx, InventoryView.open(viewer, name));
                            }
                            return invText(ctx, name);
                        })
                        .then(lit("text").executes(ctx -> invText(ctx, StringArgumentType.getString(ctx, "bot"))))))
                .then(lit("learning").executes(ctx -> {
                    if (!Build.LEARNING) return say(ctx, "This is the plain build of Fight Bot, which keeps no learning data. Use the learning build for /fightbot learning.");
                    for (String line : dev.reality.fightbot.core.LearningProfile.load(root.resolve("learning")).describe()) say(ctx, line);
                    return 1;
                }))
                .then(lit("list").executes(ctx -> {
                    if (BOTS.isEmpty()) say(ctx, "No bots running.");
                    for (BotController c : BOTS.values()) say(ctx, c.status());
                    return 1;
                }))
                .then(lit("profile").then(word("profile").executes(ctx -> {
                    BotProfile p = loadProfile(StringArgumentType.getString(ctx, "profile"));
                    for (String line : p.describe()) say(ctx, line);
                    return 1;
                })))
                .then(lit("results").executes(ctx -> {
                    try {
                        List<String> lines = Files.readAllLines(root.resolve("results.csv"));
                        for (String l : lines.subList(Math.max(1, lines.size() - 5), lines.size())) say(ctx, l);
                    } catch (Exception e) {
                        say(ctx, "No results yet.");
                    }
                    return 1;
                })));
    }

    /** Spawns a Carpet fake player where you stand, gives it the sword kit, and starts it. */
    private static int spawn(CommandContext<CommandSourceStack> ctx, String profile) {
        String bot = StringArgumentType.getString(ctx, "bot");
        CommandSourceStack src = ctx.getSource();
        MinecraftServer server = src.getServer();
        server.getCommands().performPrefixedCommand(src, "player " + bot + " spawn in survival");
        // Carpet PvP Practice's kit command. If your Carpet has no kits, give the bot items by hand.
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "bot kit give " + bot + " sword");
        return start(ctx, bot, profile);
    }

    /** Takes control of a fake player that already exists. */
    private static int fight(CommandContext<CommandSourceStack> ctx, String profile) {
        return start(ctx, StringArgumentType.getString(ctx, "bot"), profile);
    }

    private static int start(CommandContext<CommandSourceStack> ctx, String bot, String profileName) {
        String target = StringArgumentType.getString(ctx, "target");
        BotProfile profile = loadProfile(profileName);
        MinecraftServer server = ctx.getSource().getServer();
        BotController old = BOTS.remove(bot);
        if (old != null) old.finish("replaced");
        BOTS.put(bot, new BotController(server, bot, target, profile, root, msg -> broadcast(server, msg)));
        say(ctx, target.equalsIgnoreCase("everyone")
                ? bot + " will fight anyone it sees, in survival or adventure, with the " + profile.name + " profile."
                : bot + " will fight " + target + " with the " + profile.name + " profile.");
        return 1;
    }

    private static int invText(CommandContext<CommandSourceStack> ctx, String name) {
        var bot = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (bot == null) return say(ctx, "No player named " + name + " is online.");
        for (String line : InventoryView.text(bot)) say(ctx, line);
        return 1;
    }

    private static BotProfile loadProfile(String name) {
        BotProfile p = BotProfile.load(root.resolve("profiles").resolve(name + ".properties"));
        if (p.name.equals("default") && !name.equals("default")) p.name = name;
        return p;
    }

    private static int say(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSystemMessage(Component.literal(msg));
        return 1;
    }

    /** Scripted elytra trials on a fake player. Not while that bot is fighting. */
    private static int practice(CommandContext<CommandSourceStack> ctx, int trials) {
        String bot = StringArgumentType.getString(ctx, "bot");
        if (BOTS.containsKey(bot)) return say(ctx, bot + " is fighting. Stop the fight first.");
        if (PRACTICE.containsKey(bot)) return say(ctx, bot + " is already practicing.");
        MinecraftServer server = ctx.getSource().getServer();
        PRACTICE.put(bot, new FlightPractice(server, bot, trials, msg -> broadcast(server, msg)));
        return say(ctx, bot + " will do " + trials + " elytra trials at different pitches, with and without a rocket. Each trial is saved on its own.");
    }

    private static void broadcast(MinecraftServer server, String msg) {
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Fight Bot] " + msg), false);
    }
}
