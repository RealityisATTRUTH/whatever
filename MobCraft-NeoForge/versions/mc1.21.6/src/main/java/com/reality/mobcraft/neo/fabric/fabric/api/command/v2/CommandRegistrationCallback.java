package com.reality.mobcraft.neo.fabric.fabric.api.command.v2;

import com.mojang.brigadier.CommandDispatcher;
import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public interface CommandRegistrationCallback {
    Event<CommandRegistrationCallback> EVENT = new Event<>(l -> (d, a, e) -> { for (CommandRegistrationCallback c : l) c.register(d, a, e); });
    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment);
}
