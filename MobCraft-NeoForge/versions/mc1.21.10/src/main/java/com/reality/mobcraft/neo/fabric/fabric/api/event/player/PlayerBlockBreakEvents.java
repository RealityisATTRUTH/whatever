package com.reality.mobcraft.neo.fabric.fabric.api.event.player;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class PlayerBlockBreakEvents {
    public static final Event<Before> BEFORE = new Event<>(l -> (w, p, pos, s, be) -> { for (Before c : l) if (!c.beforeBlockBreak(w, p, pos, s, be)) return false; return true; });
    public static final Event<After> AFTER = new Event<>(l -> (w, p, pos, s, be) -> { for (After c : l) c.afterBlockBreak(w, p, pos, s, be); });
    public interface Before { boolean beforeBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity); }
    public interface After { void afterBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity); }
}
