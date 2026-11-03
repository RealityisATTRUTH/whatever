package com.reality.mobcraft;

import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.rts.RtsState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side cheat checks. They watch what players actually do, so they work
 * whatever mods the player has installed. ESP and X-ray are allowed: seeing
 * through walls isn't checked at all.
 *
 *   Flight       Hovering in the air, or staying up for 3 seconds without
 *                coming down, while your mob can't fly. Pulls you back to
 *                where you last stood. Only flying mobs (Bat, Ghast, Vex,
 *                Wither, Blaze, Ender Dragon) and players the game lets fly
 *                (creative, or a mob power like the Silverfish in stone) are
 *                left alone. Elytra, water, ladders, levitation, slow
 *                falling, riptide, riding, slime and bed bounces, getting
 *                hit and mob abilities that launch you are all allowed. Every
 *                Rabbit double jump counts as a launch, and the Rabbit gets
 *                twice as long in the air before the check looks at it.
 *   Auto totem   A totem in your offhand pops, and a new one is pulled in
 *                from your inventory (not your hotbar) within 4 ticks.
 *                Opening the inventory and moving it by hand takes longer.
 *   Crystal      Breaking end crystals faster than 12 a second, or breaking
 *   macro        crystals the tick they appear 5 times in 10 seconds.
 *   Anchor       Charging a respawn anchor and blowing it up within 2 ticks,
 *   macro        3 times in 30 seconds.
 *
 * Creative and spectator players aren't checked at all, so testing mobs in
 * creative is safe. Going back to survival gives 3 seconds to land first.
 *
 * It never kicks or bans. Each catch goes in the server log, warns operators
 * in chat, and tells the player they've been logged and to ask an admin if
 * they want permission to use hacks. Flying still gets you pulled back down.
 *
 *   /mobcraftanticheat                     what's on, and who's allowed
 *   /mobcraftanticheat <check> on|off      flight, auto_totem, crystal_macro, anchor_macro
 *   /mobcraftanticheat allow <player>      takes them off the anti-cheat, so they can use hacks
 *   /mobcraftanticheat disallow <player>   puts them back on it
 *
 * Operators only. Settings and the allowed list are saved in
 * config/mobcraft-anticheat.txt. Singleplayer and LAN worlds aren't checked.
 */
public final class AntiCheat {

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mobcraft-anticheat.txt");

    /** Mobs that fly, so they're never checked for flying. */
    private static final Set<MobType> FLYING_MOBS = EnumSet.of(MobType.BAT, MobType.GHAST, MobType.VEX,
            MobType.WITHER, MobType.BLAZE, MobType.ENDER_DRAGON);

    // Settings, from the config file.
    private static boolean checkFlight = true;
    private static boolean checkAutoTotem = true;
    private static boolean checkCrystals = true;
    private static boolean checkAnchors = true;
    private static final int WINDOW_TICKS = 20 * 60 * 10;
    private static final int TELL_PLAYER_EVERY = 20 * 60; // the player hears about it at most once a minute per kind
    /** Players taken off the anti-cheat by an admin: UUID -> name, for the list. */
    private static final Map<UUID, String> ALLOWED = new java.util.TreeMap<>();

    private static final int HOVER_TICKS = 10;   // airborne without moving up or down
    private static final int FLOAT_TICKS = 60;   // airborne without coming down below where you left the ground
    private static final int EXEMPT_AFTER_LAUNCH = 60;
    private static final int EXEMPT_AFTER_JOIN = 100;

    private static final class Watch {
        // Flight
        Vec3 lastGround;
        double leftGroundY;
        int airTicks;
        int hoverTicks;
        double lastY = Double.NaN;
        Vec3 lastPos;
        int exemptUntil;
        // Auto totem
        boolean offhandTotem;
        int popTick = -1000;
        int mainTotemsAtPop;
        // Crystals
        final Deque<Integer> crystalHits = new ArrayDeque<>();
        final Deque<Integer> instantBreaks = new ArrayDeque<>();
        // Anchors
        final Map<BlockPos, Integer> anchorCharged = new HashMap<>();
        final Deque<Integer> fastAnchors = new ArrayDeque<>();
        // Catches by kind
        final Map<String, Deque<Integer>> caught = new HashMap<>();
        final Map<String, Integer> toldAt = new HashMap<>();
        boolean wasCreative;
    }

    /**
     * Creative and spectator players are never checked, for anything, so
     * testing mobs in creative can't get anyone caught. Switching back to
     * survival gives 3 seconds before the checks start again, to land.
     */
    private static boolean ignored(ServerPlayer player, Watch watch, int now) {
        boolean creative = player.isCreative() || player.isSpectator() || ALLOWED.containsKey(player.getUUID());
        if (watch.wasCreative && !creative) watch.exemptUntil = Math.max(watch.exemptUntil, now + 60);
        watch.wasCreative = creative;
        return creative;
    }

    private static final Map<UUID, Watch> WATCH = new HashMap<>();

    private AntiCheat() {}

    public static void register() {
        loadConfig();
        ServerTickEvents.END_SERVER_TICK.register(AntiCheat::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Watch watch = watch(handler.player);
            watch.exemptUntil = server.getTickCount() + EXEMPT_AFTER_JOIN;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> WATCH.remove(handler.player.getUUID()));

        // Getting hit can knock you into the air.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayer player) exempt(player, EXEMPT_AFTER_LAUNCH);
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (checkCrystals && player instanceof ServerPlayer serverPlayer && entity.getType() == EntityTypes.END_CRYSTAL) {
                onCrystalHit(serverPlayer, entity.tickCount);
            }
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (checkAnchors && player instanceof ServerPlayer serverPlayer) {
                onUseBlock(serverPlayer, level, hit.getBlockPos(), player.getItemInHand(hand).is(Items.GLOWSTONE));
            }
            return InteractionResult.PASS;
        });
    }

    /** Mob abilities that throw a player call this, so the flight check lets them fly through the air. */
    public static void exempt(ServerPlayer player, int ticks) {
        Watch watch = watch(player);
        watch.exemptUntil = Math.max(watch.exemptUntil, now(player) + ticks);
        watch.airTicks = 0;
        watch.hoverTicks = 0;
    }

    private static Watch watch(ServerPlayer player) {
        return WATCH.computeIfAbsent(player.getUUID(), id -> new Watch());
    }

    private static int now(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }

    private static boolean checked(MinecraftServer server) {
        return server.isDedicatedServer();
    }

    // --- Every tick ---------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        if (!checked(server)) return;
        int now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Watch watch = watch(player);
            if (ignored(player, watch, now)) continue;
            if (checkFlight) tickFlight(player, watch, now);
            if (checkAutoTotem) tickTotem(player, watch, now);
        }
    }

    private static void tickFlight(ServerPlayer player, Watch watch, int now) {
        Vec3 pos = player.position();
        // A big jump in position is a teleport (ender pearl, command, portal): start over.
        if (watch.lastPos != null && watch.lastPos.distanceToSqr(pos) > 64) watch.exemptUntil = Math.max(watch.exemptUntil, now + 20);
        watch.lastPos = pos;

        // Standing on something, checked here on the server. The client's own "on the
        // ground" flag isn't trusted, since fly hacks fake it.
        boolean grounded = !player.level().noCollision(player, player.getBoundingBox().move(0, -0.08, 0));
        if (grounded) {
            watch.lastGround = pos;
            watch.leftGroundY = pos.y;
            watch.airTicks = 0;
            watch.hoverTicks = 0;
            watch.lastY = pos.y;
            // Bouncing off slime or a bed can send you high: give the bounce time.
            BlockState below = player.level().getBlockState(player.blockPosition().below());
            if (below.is(Blocks.SLIME_BLOCK) || below.is(BlockTags.BEDS)) watch.exemptUntil = Math.max(watch.exemptUntil, now + 80);
            return;
        }
        if (isAllowedInAir(player) || now < watch.exemptUntil) {
            watch.airTicks = 0;
            watch.hoverTicks = 0;
            watch.leftGroundY = pos.y;
            watch.lastY = pos.y;
            return;
        }

        watch.airTicks++;
        double dy = Double.isNaN(watch.lastY) ? -1 : pos.y - watch.lastY;
        watch.lastY = pos.y;
        watch.hoverTicks = Math.abs(dy) < 0.005 ? watch.hoverTicks + 1 : 0;

        boolean hovering = watch.hoverTicks >= HOVER_TICKS;
        // The Rabbit jumps high with Jump Boost and chains midair jumps: it gets twice as long.
        int floatLimit = PlayerMob.has(player, MobType.RABBIT) ? FLOAT_TICKS * 2 : FLOAT_TICKS;
        boolean floating = watch.airTicks >= floatLimit && pos.y >= watch.leftGroundY - 1.0;
        if (hovering || floating) {
            if (watch.lastGround != null) player.teleportTo(watch.lastGround.x, watch.lastGround.y, watch.lastGround.z);
            watch.airTicks = 0;
            watch.hoverTicks = 0;
            watch.lastY = Double.NaN;
            caught(player, watch, "flight", "flying without a flying mob");
        }
    }

    /** Anything that makes being in the air fine: flying mobs, game-given flight, elytra, water and so on. */
    private static boolean isAllowedInAir(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) return true;
        if (player.getAbilities().mayfly) return true; // creative flight or a mob power, like the Silverfish in stone
        MobType type = PlayerMob.get(player);
        if (type != null && FLYING_MOBS.contains(type)) return true;
        if (RtsState.isActive(player)) return true;
        return player.isFallFlying() || player.isPassenger() || player.onClimbable()
                || player.isInWater() || player.isInLava() || player.isAutoSpinAttack()
                || player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING)
                || player.isSleeping() || inSlowBlock(player);
    }

    /** Cobwebs, powder snow and berry bushes slow falling to almost nothing. */
    private static boolean inSlowBlock(ServerPlayer player) {
        BlockState feet = player.level().getBlockState(player.blockPosition());
        BlockState head = player.level().getBlockState(player.blockPosition().above());
        return feet.is(Blocks.COBWEB) || head.is(Blocks.COBWEB) || feet.is(Blocks.POWDER_SNOW)
                || feet.is(Blocks.SWEET_BERRY_BUSH) || feet.is(Blocks.SCAFFOLDING);
    }

    private static void tickTotem(ServerPlayer player, Watch watch, int now) {
        Inventory inventory = player.getInventory();
        boolean holding = player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        if (watch.offhandTotem && !holding && player.isAlive()
                && player.hasEffect(MobEffects.ABSORPTION) && player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            // The offhand totem just went, and its effects just landed: it popped.
            watch.popTick = now;
            watch.mainTotemsAtPop = totemsOutsideHotbar(inventory);
        }
        if (!watch.offhandTotem && holding && now - watch.popTick <= 4) {
            // Refilled within 4 ticks, and from the inventory rather than the hotbar.
            if (totemsOutsideHotbar(inventory) < watch.mainTotemsAtPop) {
                caught(player, watch, "auto_totem", "putting a totem back in their offhand too fast");
            }
            watch.popTick = -1000;
        }
        watch.offhandTotem = holding;
    }

    private static int totemsOutsideHotbar(Inventory inventory) {
        int count = 0;
        for (int slot = 9; slot < 36; slot++) {
            if (inventory.getItem(slot).is(Items.TOTEM_OF_UNDYING)) count += inventory.getItem(slot).getCount();
        }
        return count;
    }

    // --- Crystals and anchors ----------------------------------------------------------

    private static void onCrystalHit(ServerPlayer player, int crystalAge) {
        if (!checked(player.level().getServer()) || player.isCreative() || player.isSpectator()
                || ALLOWED.containsKey(player.getUUID())) return;
        Watch watch = watch(player);
        int now = now(player);
        trim(watch.crystalHits, now, 20);
        watch.crystalHits.add(now);
        if (watch.crystalHits.size() > 12) {
            watch.crystalHits.clear();
            caught(player, watch, "crystal_macro", "breaking end crystals faster than anyone can click");
            return;
        }
        if (crystalAge <= 1) {
            trim(watch.instantBreaks, now, 200);
            watch.instantBreaks.add(now);
            if (watch.instantBreaks.size() >= 5) {
                watch.instantBreaks.clear();
                caught(player, watch, "crystal_macro", "breaking end crystals the tick they're placed");
            }
        }
    }

    private static void onUseBlock(ServerPlayer player, Level level, BlockPos pos, boolean glowstone) {
        if (!checked(player.level().getServer()) || player.isCreative() || player.isSpectator()
                || ALLOWED.containsKey(player.getUUID())) return;
        if (!level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) return;
        if (level.dimension() == Level.NETHER) return; // anchors don't blow up there
        Watch watch = watch(player);
        int now = now(player);
        if (glowstone) {
            watch.anchorCharged.put(pos.immutable(), now);
            return;
        }
        Integer charged = watch.anchorCharged.remove(pos);
        if (charged == null || now - charged > 2) return;
        trim(watch.fastAnchors, now, 600);
        watch.fastAnchors.add(now);
        if (watch.fastAnchors.size() >= 3) {
            watch.fastAnchors.clear();
            caught(player, watch, "anchor_macro", "charging and blowing up respawn anchors too fast");
        }
        if (watch.anchorCharged.size() > 64) watch.anchorCharged.clear();
    }

    private static void trim(Deque<Integer> ticks, int now, int window) {
        while (!ticks.isEmpty() && now - ticks.peekFirst() > window) ticks.pollFirst();
    }

    // --- Catching ---------------------------------------------------------------

    /**
     * Logs it, warns operators, and tells the player they've been logged. No
     * kick and no ban: what to do about it is up to the admins.
     */
    private static void caught(ServerPlayer player, Watch watch, String kind, String what) {
        int now = now(player);
        Deque<Integer> times = watch.caught.computeIfAbsent(kind, k -> new ArrayDeque<>());
        trim(times, now, WINDOW_TICKS);
        times.add(now);
        String name = player.getName().getString();
        String line = "[Anti-cheat] " + name + " caught " + what + " (" + times.size() + " in 10 minutes)";
        MobCraft.LOGGER.warn(line);
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player && isStaff(other)) other.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.RED));
        }
        Integer told = watch.toldAt.get(kind);
        if (told == null || now - told >= TELL_PLAYER_EVERY) {
            watch.toldAt.put(kind, now);
            player.sendSystemMessage(Component.literal("[Anti-cheat] You've been logged for " + what
                    + ". Hacks aren't allowed here unless an admin gives you permission.").withStyle(ChatFormatting.RED));
        }
    }

    private static boolean isStaff(ServerPlayer player) {
        return player.createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
    }

    // --- Settings ---------------------------------------------------------------

    private static boolean get(String check) {
        return switch (check) {
            case "flight" -> checkFlight;
            case "auto_totem" -> checkAutoTotem;
            case "crystal_macro" -> checkCrystals;
            case "anchor_macro" -> checkAnchors;
            default -> false;
        };
    }

    private static void set(String check, boolean on) {
        switch (check) {
            case "flight" -> checkFlight = on;
            case "auto_totem" -> checkAutoTotem = on;
            case "crystal_macro" -> checkCrystals = on;
            case "anchor_macro" -> checkAnchors = on;
            default -> { }
        }
    }

    private static final List<String> CHECKS = List.of("flight", "auto_totem", "crystal_macro", "anchor_macro");

    private static void loadConfig() {
        try {
            if (!Files.exists(FILE)) {
                saveConfig();
                return;
            }
            for (String raw : Files.readAllLines(FILE)) {
                String line = raw.trim();
                if (line.startsWith("#") || !line.contains("=")) continue;
                String key = line.substring(0, line.indexOf('=')).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(line.indexOf('=') + 1).trim();
                if (key.equals("allowed")) {
                    // "allowed=<uuid> <name>"
                    String[] parts = value.split(" ", 2);
                    try {
                        ALLOWED.put(UUID.fromString(parts[0]), parts.length > 1 ? parts[1] : parts[0]);
                    } catch (IllegalArgumentException ignored) {
                        // not a UUID: skip it
                    }
                } else if (CHECKS.contains(key)) {
                    String v = value.toLowerCase(Locale.ROOT);
                    set(key, !v.equals("off") && !v.equals("false"));
                }
            }
        } catch (IOException e) {
            MobCraft.LOGGER.warn("Couldn't read {}, using the default anti-cheat settings.", FILE, e);
        }
    }

    private static void saveConfig() {
        List<String> lines = new ArrayList<>();
        lines.add("# MobCraft anti-cheat. Each check is on or off. ESP and X-ray are allowed and never checked.");
        lines.add("# It never kicks or bans: it logs, warns operators and tells the player.");
        lines.add("# allowed= lines are players taken off the anti-cheat. Easiest to change with /mobcraftanticheat.");
        for (String check : CHECKS) lines.add(check + "=" + (get(check) ? "on" : "off"));
        ALLOWED.forEach((id, name) -> lines.add("allowed=" + id + " " + name));
        try {
            Files.write(FILE, lines);
        } catch (IOException e) {
            MobCraft.LOGGER.warn("Couldn't save {}.", FILE, e);
        }
    }

    // --- The command -------------------------------------------------------------------

    public static void registerCommand(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        var root = net.minecraft.commands.Commands.literal("mobcraftanticheat")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                .executes(ctx -> {
                    StringBuilder text = new StringBuilder("Anti-cheat:");
                    for (String check : CHECKS) text.append("\n  ").append(check).append(": ").append(get(check) ? "on" : "off");
                    text.append("\n  Allowed to use hacks: ")
                            .append(ALLOWED.isEmpty() ? "nobody" : String.join(", ", ALLOWED.values()));
                    ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
                    return 1;
                })
                .then(net.minecraft.commands.Commands.literal("allow")
                        .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> allow(ctx, true))))
                .then(net.minecraft.commands.Commands.literal("disallow")
                        .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> allow(ctx, false))));
        for (String check : CHECKS) {
            root.then(net.minecraft.commands.Commands.literal(check)
                    .then(net.minecraft.commands.Commands.literal("on").executes(ctx -> toggle(ctx, check, true)))
                    .then(net.minecraft.commands.Commands.literal("off").executes(ctx -> toggle(ctx, check, false))));
        }
        dispatcher.register(root);
    }

    private static int toggle(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                              String check, boolean on) {
        set(check, on);
        saveConfig();
        ctx.getSource().sendSuccess(() -> Component.literal("Anti-cheat " + check + " is now " + (on ? "on." : "off.")), true);
        return 1;
    }

    private static int allow(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
                             boolean allow) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
        String name = target.getName().getString();
        if (allow) {
            ALLOWED.put(target.getUUID(), name);
        } else {
            ALLOWED.remove(target.getUUID());
        }
        saveConfig();
        ctx.getSource().sendSuccess(() -> Component.literal(allow
                ? name + " is off the anti-cheat and can use hacks."
                : name + " is back on the anti-cheat."), true);
        target.sendSystemMessage(Component.literal(allow
                ? "An admin gave you permission to use hacks on this server."
                : "Your permission to use hacks on this server was taken back.").withStyle(ChatFormatting.YELLOW));
        return 1;
    }
}
