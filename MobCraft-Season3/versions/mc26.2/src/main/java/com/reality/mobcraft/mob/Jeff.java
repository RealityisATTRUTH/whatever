package com.reality.mobcraft.mob;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.reality.mobcraft.MobCraft;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Jeff, the Villager, Merc's admin mob.
 *
 * Powered up (normal)  money instead of energy, up to 100 quadrillion, commands
 *                      him all mobs in RTS, Warden bodyguards, his own menu of
 *                      every item, and he's made a server operator so he can
 *                      use commands.
 * Shut down            a plain Villager: normal energy, gives himself emeralds
 *                      only near a crafting table or a villager job block,
 *                      takes extra damage from raid mobs, and his operator
 *                      status is taken back.
 *
 *   /shutdownjeff <password>   switches between the two. Only the players named
 *                              in config/mobcraft-jeff.txt can use it, and only
 *                              with the right password. There's no password in
 *                              the code: the server owner sets one in that file
 *                              with password=..., and on the next start it's
 *                              replaced by its SHA-256 scramble, so the real
 *                              password is never left written down. Until a
 *                              password is set, /shutdownjeff is off.
 *
 * Only operator status that Jeff gave is taken back. Someone who was already an
 * operator before becoming Jeff keeps it.
 */
public final class Jeff {

    public static final long MAX_MONEY = 100_000_000_000_000_000L; // 100 quadrillion

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mobcraft-jeff.txt");
    private static String passwordHash = null;
    private static List<String> allowed = List.of("reality_456", "bosscanaan");

    /** Wrong passwords per player, so it can't be guessed by trying over and over. */
    private static final Map<UUID, Integer> WRONG = new HashMap<>();
    private static final Map<UUID, Long> LOCKED_UNTIL = new HashMap<>();
    private static final int MAX_WRONG = 3;
    private static final long LOCK_MILLIS = 60_000;

    private Jeff() {}

    // --- State ----------------------------------------------------------------------

    /** Works on both sides: the client uses it for RTS and the money bar. */
    public static boolean isPowered(Player player) {
        return Boolean.TRUE.equals(player.getAttached(MobAttachments.JEFF_POWERED));
    }

    public static boolean isShutDown(MinecraftServer server) {
        return Boolean.TRUE.equals(server.overworld().getAttached(MobAttachments.JEFF_SHUTDOWN));
    }

    public static long money(Player player) {
        Long money = player.getAttached(MobAttachments.MONEY);
        return money == null ? 0 : money;
    }

    public static void setMoney(Player player, long money) {
        player.setAttached(MobAttachments.MONEY, Math.max(0, Math.min(MAX_MONEY, money)));
    }

    /** Spends money if there's enough. */
    public static boolean spend(ServerPlayer player, long amount) {
        long money = money(player);
        if (money < amount) return false;
        setMoney(player, money - amount);
        return true;
    }

    /** Every second for every player: keeps the powered flag and operator status right. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0) return;
        MinecraftServer server = player.level().getServer();
        boolean powered = PlayerMob.has(player, MobType.VILLAGER) && !isShutDown(server);

        if (powered != isPowered(player)) {
            if (powered) {
                player.setAttached(MobAttachments.JEFF_POWERED, true);
            } else {
                player.removeAttached(MobAttachments.JEFF_POWERED);
            }
        }

        // Punished for logging out cursed: no operator, so he can't /effect clear himself.
        boolean opAllowed = powered;
        boolean opped = Boolean.TRUE.equals(player.getAttached(MobAttachments.JEFF_OPPED));
        if (opAllowed && !opped && !isOperator(player)) {
            runAsServer(server, "op " + player.getName().getString());
            player.setAttached(MobAttachments.JEFF_OPPED, true);
        } else if (!opAllowed && opped) {
            runAsServer(server, "deop " + player.getName().getString());
            player.removeAttached(MobAttachments.JEFF_OPPED);
        }
    }

    private static boolean isOperator(ServerPlayer player) {
        return player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
    }

    private static void runAsServer(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    // --- The command ----------------------------------------------------------------

    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        loadConfig();
        dispatcher.register(Commands.literal("shutdownjeff")
                .then(Commands.argument("password", StringArgumentType.greedyString())
                        .executes(Jeff::shutdownCommand)));
    }

    private static int shutdownCommand(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only Reality or Canaan can do that, in game."));
            return 0;
        }
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Long locked = LOCKED_UNTIL.get(id);
        if (locked != null && now < locked) {
            source.sendFailure(Component.literal("Too many wrong passwords. Try again in a minute."));
            return 0;
        }
        boolean allowedName = allowed.contains(player.getName().getString().toLowerCase(Locale.ROOT));
        if (allowedName && passwordHash == null) {
            source.sendFailure(Component.literal("No /shutdownjeff password is set yet. Add password=... to config/mobcraft-jeff.txt and restart."));
            return 0;
        }
        String typed = StringArgumentType.getString(ctx, "password");
        if (!allowedName || passwordHash == null || !matches(typed)) {
            int wrong = WRONG.merge(id, 1, Integer::sum);
            if (wrong >= MAX_WRONG) {
                WRONG.remove(id);
                LOCKED_UNTIL.put(id, now + LOCK_MILLIS);
            }
            MobCraft.LOGGER.warn("{} tried /shutdownjeff with the wrong password or without permission.",
                    player.getName().getString());
            source.sendFailure(Component.literal("Wrong password."));
            return 0;
        }
        WRONG.remove(id);

        MinecraftServer server = source.getServer();
        boolean shutDown = !isShutDown(server);
        if (shutDown) {
            server.overworld().setAttached(MobAttachments.JEFF_SHUTDOWN, true);
        } else {
            server.overworld().removeAttached(MobAttachments.JEFF_SHUTDOWN);
        }
        // Apply straight away instead of waiting for the next second.
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (!PlayerMob.has(other, MobType.VILLAGER)) continue;
            other.sendSystemMessage(Component.literal(shutDown
                    ? "Your powers have been shut down. You're just a Villager now."
                    : "Your powers are back, Jeff."));
        }
        // Only the person who ran it sees this. Ops aren't told, so Jeff doesn't find out who.
        source.sendSuccess(() -> Component.literal(shutDown
                ? "Jeff is shut down: a normal Villager until you run this again."
                : "Jeff has his powers back."), false);
        MobCraft.LOGGER.info("{} {} Jeff.", player.getName().getString(), shutDown ? "shut down" : "restored");
        return 1;
    }

    /**
     * Reads config/mobcraft-jeff.txt, making it the first time without a
     * password. A password=... line is swapped for its scramble straight away.
     */
    private static void loadConfig() {
        try {
            if (!Files.exists(FILE)) {
                List<String> lines = new ArrayList<>();
                lines.add("# /shutdownjeff settings. Only people who can open the server's files can read this.");
                lines.add("# password: set one here, like password=yournewpassword. On the next start it's");
                lines.add("#   replaced by password_sha256, its scramble, so the real one isn't left written down.");
                lines.add("#   Until one is set, /shutdownjeff is off.");
                lines.add("# allowed: who can use it, Minecraft names separated by commas");
                lines.add("password=");
                lines.add("allowed=Reality_456,BossCanaan");
                Files.write(FILE, lines);
                MobCraft.LOGGER.info("Made {} for /shutdownjeff.", FILE);
            }
            List<String> fileLines = new ArrayList<>(Files.readAllLines(FILE));
            boolean rewrite = false;
            for (int i = 0; i < fileLines.size(); i++) {
                String trimmed = fileLines.get(i).trim();
                if (trimmed.startsWith("#") || !trimmed.contains("=")) continue;
                String key = trimmed.substring(0, trimmed.indexOf('=')).trim().toLowerCase(Locale.ROOT);
                String value = trimmed.substring(trimmed.indexOf('=') + 1).trim();
                if (key.equals("password") && !value.isEmpty()) {
                    passwordHash = sha256(value);
                    // Don't leave the real password in the file.
                    fileLines.set(i, "password_sha256=" + passwordHash);
                    rewrite = true;
                } else if (key.equals("password_sha256") && !value.isEmpty()) {
                    passwordHash = value.toLowerCase(Locale.ROOT);
                } else if (key.equals("allowed")) {
                    List<String> names = new ArrayList<>();
                    for (String name : value.split(",")) {
                        if (!name.isBlank()) names.add(name.trim().toLowerCase(Locale.ROOT));
                    }
                    allowed = List.copyOf(names);
                }
            }
            if (rewrite) {
                Files.write(FILE, fileLines);
                MobCraft.LOGGER.info("Scrambled the /shutdownjeff password in {}.", FILE);
            }
        } catch (IOException e) {
            MobCraft.LOGGER.warn("Couldn't read {}. /shutdownjeff won't work until it can.", FILE, e);
        }
    }

    private static boolean matches(String typed) {
        byte[] a = sha256(typed).getBytes(StandardCharsets.US_ASCII);
        byte[] b = passwordHash.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(a, b);
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b));
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "1.2 Quadrillion", "350 Trillion", "42". */
    public static String formatMoney(long money) {
        String[] names = {"", " Thousand", " Million", " Billion", " Trillion", " Quadrillion"};
        double value = money;
        int i = 0;
        while (value >= 1000 && i < names.length - 1) {
            value /= 1000;
            i++;
        }
        if (i == 0) return Long.toString(money);
        String number = value >= 100 ? String.format(Locale.ROOT, "%.0f", value)
                : String.format(Locale.ROOT, "%.1f", value);
        if (number.endsWith(".0")) number = number.substring(0, number.length() - 2);
        return number + names[i];
    }
}
