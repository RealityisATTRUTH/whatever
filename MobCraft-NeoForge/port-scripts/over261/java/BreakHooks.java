package com.reality.mobcraft.neo;

import com.reality.mobcraft.neo.fabric.fabric.api.event.player.PlayerBlockBreakEvents;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.IEventBus;

/**
 * Block breaking. NeoForge 26.1.2 calls the event BreakBlockEvent; the NeoForge betas for 26.1 and 26.1.1
 * still have the older BlockEvent.BreakEvent, which is reached by reflection so one jar runs on all three.
 */
final class BreakHooks {
    private BreakHooks() {}

    /** True if the break should be cancelled. */
    static boolean handle(LevelAccessor accessor, Player player, BlockPos pos, BlockState state) {
        if (!(accessor instanceof Level level)) return false;
        var be = level.getBlockEntity(pos);
        if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, be)) return true;
        if (level instanceof ServerLevel server) {
            BlockPos at = pos.immutable();
            server.getServer().schedule(new TickTask(server.getServer().getTickCount(), () ->
                    PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, at, state, be)));
        }
        return false;
    }

    static void register(IEventBus bus) {
        try {
            Class.forName("net.neoforged.neoforge.event.level.block.BreakBlockEvent");
            NewApi.register(bus);
        } catch (ClassNotFoundException | LinkageError e) {
            oldApi(bus);
        }
    }

    private static final class NewApi {
        static void register(IEventBus bus) {
            bus.addListener(EventPriority.LOWEST, (net.neoforged.neoforge.event.level.block.BreakBlockEvent e) -> {
                if (!e.isCanceled() && handle(e.getLevel(), e.getPlayer(), e.getPos(), e.getState())) e.setCanceled(true);
            });
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void oldApi(IEventBus bus) {
        try {
            Class type = Class.forName("net.neoforged.neoforge.event.level.BlockEvent$BreakEvent");
            Method level = type.getMethod("getLevel"), pos = type.getMethod("getPos"), state = type.getMethod("getState"), player = type.getMethod("getPlayer");
            bus.addListener(EventPriority.LOWEST, false, type, e -> {
                try {
                    ICancellableEvent c = (ICancellableEvent) e;
                    if (!c.isCanceled() && handle((LevelAccessor) level.invoke(e), (Player) player.invoke(e), (BlockPos) pos.invoke(e), (BlockState) state.invoke(e)))
                        c.setCanceled(true);
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (ReflectiveOperationException ex) {
            com.reality.mobcraft.MobCraft.LOGGER.warn("MobCraft: no block break event found on this NeoForge", ex);
        }
    }
}
