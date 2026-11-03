package dev.reality.fightbot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Moves that have to happen inside one tick, sent through the same packet handlers a real client
 * uses, so the server applies exactly the rules it applies to players. If Mojang fixes attribute
 * swapping in the slot change handler, these moves get the fix too.
 */
final class ServerMoves {
    private ServerMoves() {
    }

    /** Null until known, then whether a slot change left the old item's attributes in place. */
    static Boolean swapsWork;
    private static ServerboundPlayerActionPacket.Action stabAction;
    private static boolean stabLooked;

    static void select(ServerPlayer bot, int slot) {
        if (slot < 0 || slot > 8) return;
        bot.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(slot));
        // A fake player has no client behind it, and the handler can ignore it. Set the slot directly then,
        // the way Carpet's own hotbar command does.
        if (bot.getInventory().getSelectedSlot() != slot) bot.getInventory().setSelectedSlot(slot);
    }

    /**
     * Switches to each slot and hits the target, all in this tick, then ends on swapBack.
     * Returns a note the first time it learns whether attribute swaps work on this server.
     */
    static String swapAttack(ServerPlayer bot, Entity target, int[] slots, int swapBack) {
        String note = null;
        double speedBefore = bot.getAttributeValue(Attributes.ATTACK_SPEED);
        String before = BotController.id(bot.getMainHandItem());
        for (int slot : slots) {
            select(bot, slot);
            String now = BotController.id(bot.getMainHandItem());
            if (swapsWork == null && !now.equals(before) && kind(now) != kind(before) && kind(now) != 0 && kind(before) != 0) {
                swapsWork = Math.abs(bot.getAttributeValue(Attributes.ATTACK_SPEED) - speedBefore) < 1e-6;
                note = swapsWork ? "Attribute swaps work on this server." : "Attribute swaps are fixed on this server, so swap hits only get the normal damage.";
            }
            bot.attack(target);
            bot.resetAttackStrengthTicker();
        }
        if (swapBack >= 0) select(bot, swapBack);
        return note;
    }

    /** Lunge spear jab: switch to the spear, jab, switch back, inside one tick. False if this game has no jab action. */
    static boolean jab(ServerPlayer bot, int spearSlot, int swapBack) {
        ServerboundPlayerActionPacket.Action action = stab();
        if (action == null) return false;
        select(bot, spearSlot);
        bot.connection.handlePlayerAction(new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN));
        if (swapBack >= 0) select(bot, swapBack);
        return true;
    }

    /** Swap hands key, for putting a totem from the hotbar into the offhand. */
    static void swapHands(ServerPlayer bot) {
        bot.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
    }

    /** The spear jab is a player action packet. Its name is looked up so a rename does not break the build. */
    private static ServerboundPlayerActionPacket.Action stab() {
        if (!stabLooked) {
            stabLooked = true;
            for (ServerboundPlayerActionPacket.Action a : ServerboundPlayerActionPacket.Action.values()) {
                String n = a.name();
                if (n.contains("STAB") || n.contains("JAB") || n.contains("SPEAR")) stabAction = a;
            }
        }
        return stabAction;
    }

    private static int kind(String id) {
        if (id.endsWith("_sword")) return 1;
        if (id.endsWith("_axe")) return 2;
        if (id.equals("mace")) return 3;
        if (id.endsWith("_spear")) return 4;
        return 0;
    }
}
