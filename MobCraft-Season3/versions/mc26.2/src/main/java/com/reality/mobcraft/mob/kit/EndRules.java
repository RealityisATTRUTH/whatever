package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.MobAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Server rules that come with the Ender Dragon mob. They only apply while
 * someone is the Ender Dragon. The world remembers who, even while they're
 * offline. Switching off the Ender Dragon makes the mod forget them, and the
 * End goes back to normal.
 *
 *   No Ender Dragon spawns. When the game makes one, it counts as killed
 *   right away, so the exit portal opens and the boss bar goes. The egg the
 *   first kill would leave on the portal is removed, since the Ender Dragon
 *   player has the only egg.
 *
 *   All 20 End gateways open the first time anyone is in the End, at the
 *   spots the game would put them after 20 dragon kills.
 *
 *   Switching off the Ender Dragon closes every gateway the mod opened, so
 *   a quick test doesn't leave them open. Gateways from real dragon kills
 *   stay. The dragon itself doesn't come back on its own, since the game
 *   counts it as killed, but 4 End Crystals on the exit portal respawn it.
 */
public final class EndRules {

    private static final int GATEWAY_COUNT = 20;
    private static final List<EnderDragon> TO_REMOVE = new ArrayList<>();
    /** Set once the gateways have been opened since the server started or the Ender Dragon changed. */
    private static boolean gatewaysDone = false;

    private EndRules() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.getType() == EntityTypes.ENDER_DRAGON && entity instanceof EnderDragon dragon
                    && active(level.getServer())) {
                TO_REMOVE.add(dragon); // removed next tick, not while the world is adding it
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!TO_REMOVE.isEmpty()) {
                for (EnderDragon dragon : List.copyOf(TO_REMOVE)) removeDragon(dragon);
                TO_REMOVE.clear();
            }
            if (!gatewaysDone && server.getTickCount() % 40 == 0 && active(server)) {
                ServerLevel end = server.getLevel(Level.END);
                if (end != null && !end.players().isEmpty()) {
                    openGateways(end);
                    gatewaysDone = true;
                }
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            gatewaysDone = false;
            updateOldWorld(server);
        });
    }

    // --- Who is the Ender Dragon ----------------------------------------------------

    /** Whether someone is the Ender Dragon right now, online or not. */
    public static boolean active(MinecraftServer server) {
        return server.overworld().getAttached(MobAttachments.DRAGON_PLAYER) != null;
    }

    /** Called when a player becomes the Ender Dragon, and as a check while they play it. */
    public static void setDragonPlayer(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        String id = player.getStringUUID();
        if (id.equals(overworld.getAttached(MobAttachments.DRAGON_PLAYER))) return;
        overworld.setAttached(MobAttachments.DRAGON_PLAYER, id);
        gatewaysDone = false;
        MobCraft.LOGGER.info("{} is the Ender Dragon: End rules are on.", player.getName().getString());
    }

    /**
     * Called when a player stops being the Ender Dragon. If they were the one
     * the world remembers, it forgets them and closes the gateways it opened.
     */
    public static void clearDragonPlayer(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel overworld = server.overworld();
        if (!player.getStringUUID().equals(overworld.getAttached(MobAttachments.DRAGON_PLAYER))) return;
        overworld.removeAttached(MobAttachments.DRAGON_PLAYER);
        gatewaysDone = false;
        TO_REMOVE.clear();
        ServerLevel end = server.getLevel(Level.END);
        int closed = end == null ? 0 : closeGateways(end, overworld);
        MobCraft.LOGGER.info("{} is no longer the Ender Dragon: End rules are off, {} gateways closed.",
                player.getName().getString(), closed);
    }

    /**
     * Older versions opened all 20 gateways on every start without noting it.
     * The first time this version runs, those count as opened by the mod, and
     * if nobody is the Ender Dragon they're closed right away.
     */
    private static void updateOldWorld(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (Boolean.TRUE.equals(overworld.getAttached(MobAttachments.END_RULES_UPDATED))) return;
        overworld.setAttached(MobAttachments.END_RULES_UPDATED, true);
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) return;
        for (int i : openGatewayIndexes(end)) remember(overworld, i);
        if (!active(server)) {
            int closed = closeGateways(end, overworld);
            MobCraft.LOGGER.info("Nobody is the Ender Dragon: closed {} gateways an older version opened.", closed);
        }
    }

    // --- The dragon -----------------------------------------------------------------

    private static void removeDragon(EnderDragon dragon) {
        if (!(dragon.level() instanceof ServerLevel level) || dragon.isRemoved()) return;
        var fight = level.getDragonFight();
        if (fight != null) {
            // Counting it as killed opens one gateway. Note it, so it closes with the rest.
            List<Integer> before = openGatewayIndexes(level);
            fight.setDragonKilled(dragon);
            for (int i : openGatewayIndexes(level)) {
                if (!before.contains(i)) remember(level.getServer().overworld(), i);
            }
        }
        dragon.discard();
        removePortalEgg(level);
        MobCraft.LOGGER.info("Removed an Ender Dragon: someone is the Ender Dragon, so none spawn.");
    }

    /** The first kill puts the egg on top of the exit portal, near 0,0. */
    private static void removePortalEgg(ServerLevel level) {
        for (BlockPos pos : BlockPos.betweenClosed(-3, level.getMinY(), -3, 3, level.getMaxY(), 3)) {
            if (level.getBlockState(pos).is(Blocks.DRAGON_EGG)) level.removeBlock(pos, false);
        }
    }

    // --- Gateways -------------------------------------------------------------------

    /** Where gateway number i goes: the same ring the game uses, 96 blocks out at y 75. */
    private static BlockPos gatewayPos(int i) {
        int x = Mth.floor(96.0 * Math.cos(2.0 * (-Math.PI + (Math.PI / GATEWAY_COUNT) * i)));
        int z = Mth.floor(96.0 * Math.sin(2.0 * (-Math.PI + (Math.PI / GATEWAY_COUNT) * i)));
        return new BlockPos(x, 75, z);
    }

    private static boolean isOpen(ServerLevel end, int i) {
        BlockPos pos = gatewayPos(i);
        end.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // make sure the chunk is loaded
        return end.getBlockState(pos).is(Blocks.END_GATEWAY);
    }

    private static List<Integer> openGatewayIndexes(ServerLevel end) {
        List<Integer> open = new ArrayList<>();
        for (int i = 0; i < GATEWAY_COUNT; i++) {
            if (isOpen(end, i)) open.add(i);
        }
        return open;
    }

    private static void remember(ServerLevel overworld, int i) {
        List<Integer> opened = new ArrayList<>(overworld.getAttachedOrElse(MobAttachments.OPENED_GATEWAYS, List.of()));
        if (opened.contains(i)) return;
        opened.add(i);
        overworld.setAttached(MobAttachments.OPENED_GATEWAYS, opened);
    }

    /**
     * Places any of the 20 gateways that aren't there yet. Built block by block
     * in the same shape as the game's gateways, with no exit set, so the game
     * makes the far end the first time someone goes through, like normal.
     */
    private static void openGateways(ServerLevel end) {
        ServerLevel overworld = end.getServer().overworld();
        int placed = 0;
        for (int i = 0; i < GATEWAY_COUNT; i++) {
            if (isOpen(end, i)) continue;
            buildGateway(end, gatewayPos(i));
            remember(overworld, i);
            placed++;
        }
        MobCraft.LOGGER.info("End gateways: {} opened now, {} were already open.", placed, GATEWAY_COUNT - placed);
    }

    /** Removes every gateway the mod opened. Returns how many. */
    private static int closeGateways(ServerLevel end, ServerLevel overworld) {
        List<Integer> opened = overworld.getAttachedOrElse(MobAttachments.OPENED_GATEWAYS, List.of());
        int closed = 0;
        for (int i : opened) {
            BlockPos center = gatewayPos(i);
            end.getChunk(center.getX() >> 4, center.getZ() >> 4);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -2, -1), center.offset(1, 2, 1))) {
                end.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            closed++;
        }
        overworld.removeAttached(MobAttachments.OPENED_GATEWAYS);
        return closed;
    }

    /**
     * A gateway: the portal block in the middle, an open ring around it, bedrock
     * in a plus shape just above and below, and a bedrock cap two above and below.
     */
    private static void buildGateway(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -2, -1), center.offset(1, 2, 1))) {
            boolean sameX = pos.getX() == center.getX();
            boolean sameZ = pos.getZ() == center.getZ();
            int dy = Math.abs(pos.getY() - center.getY());
            BlockState state;
            if (sameX && sameZ && dy == 0) state = Blocks.END_GATEWAY.defaultBlockState();
            else if (dy == 0) state = Blocks.AIR.defaultBlockState();
            else if (dy == 2) state = sameX && sameZ ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
            else state = sameX || sameZ ? Blocks.BEDROCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
            level.setBlock(pos, state, 3);
        }
    }
}
