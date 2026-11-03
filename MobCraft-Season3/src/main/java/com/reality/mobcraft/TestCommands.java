package com.reality.mobcraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reality.mobcraft.glow.MobGlow;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import com.reality.mobcraft.rts.MobOwnership;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Collection;
import java.util.Map;

/**
 * Alpha test commands. These go away once the real mob system exists.
 *
 *   /glowtest <targets> <color> [seconds]   targets glow, only on your screen
 *   /glowtest clear <targets>              stop them glowing
 *   /scaletest <scale>                     resize yourself to test the dual hitbox
 *   /rtstest claim [radius]                make nearby mobs yours for RTS testing
 *   /rtstest release                       give them back
 */
public final class TestCommands {

    private static final Map<String, Integer> COLORS = Map.of(
            "white", 0xFFFFFF,
            "red", 0xFF2020,
            "green", 0x20FF20,
            "blue", 0x3070FF,
            "yellow", 0xFFFF20,
            "purple", 0xB040FF,
            "aqua", 0x20FFFF,
            "orange", 0xFF9020
    );

    private TestCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("glowtest")
                .then(Commands.literal("clear")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(TestCommands::clearGlow)))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("color", StringArgumentType.word())
                                .executes(ctx -> glow(ctx, MobGlow.FOREVER))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> glow(ctx, IntegerArgumentType.getInteger(ctx, "seconds") * 20))))));

        dispatcher.register(Commands.literal("scaletest")
                .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.1, 3.0))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            double scale = DoubleArgumentType.getDouble(ctx, "scale");
                            AttributeInstance attr = player.getAttribute(Attributes.SCALE);
                            if (attr == null) return 0;
                            attr.setBaseValue(scale);
                            boolean small = SmallMobHitbox.isSmall(player);
                            ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                                    "Scale %.2f. Dual hitbox %s.", scale, small ? "on" : "off")), false);
                            return 1;
                        })));

        dispatcher.register(Commands.literal("rtstest")
                .then(Commands.literal("claim")
                        .executes(ctx -> claim(ctx, 10))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> claim(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("release").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    int count = 0;
                    for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64),
                            m -> MobOwnership.isOwnedBy(m, player))) {
                        MobOwnership.clearOwner(mob);
                        count++;
                    }
                    int released = count;
                    ctx.getSource().sendSuccess(() -> Component.literal("Released " + released + " mobs."), false);
                    return released;
                })));

    }

    /** Testing only: makes every mob within radius yours, so RTS has something to command. */
    private static int claim(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int count = 0;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius), m -> true)) {
            MobOwnership.setOwner(mob, player);
            count++;
        }
        int claimed = count;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Claimed " + claimed + " mobs. Press J to command them."), false);
        return claimed;
    }

    private static int glow(CommandContext<CommandSourceStack> ctx, int ticks) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "color");
        Integer color = COLORS.get(name.toLowerCase());
        if (color == null) {
            ctx.getSource().sendFailure(Component.literal("Colors: " + String.join(", ", COLORS.keySet())));
            return 0;
        }
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        for (Entity target : targets) {
            MobGlow.glow(viewer, target, color, ticks);
        }
        int count = targets.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                count + " entities glowing " + name + " on your screen only."), false);
        return count;
    }

    private static int clearGlow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer viewer = ctx.getSource().getPlayerOrException();
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        for (Entity target : targets) {
            MobGlow.clear(viewer, target);
        }
        int count = targets.size();
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared glow on " + count + " entities."), false);
        return count;
    }

}
