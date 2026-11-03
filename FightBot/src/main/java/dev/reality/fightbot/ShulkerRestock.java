package dev.reality.fightbot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Restocking from a shulker box the way a player does it: place the box next to you, open it,
 * take the supplies one stack per tick, close it, mine it back up, and walk over the dropped item until
 * it is back in the inventory. A shulker box comes up with a pickaxe from anywhere in the inventory or by
 * hand. An ender chest only comes back with Silk Touch, so it is only placed when there is a Silk Touch
 * pickaxe. If the opponent comes close it stops taking items but still gets the box back.
 */
final class ShulkerRestock {
    enum Step { PLACE, OPEN, TAKE, CLOSE, BREAK, PICKUP, DONE }

    /** What is worth taking, most important first. */
    private static final List<String> WANTED = List.of("totem_of_undying", "enchanted_golden_apple", "golden_apple",
            "firework_rocket", "wind_charge", "ender_pearl", "end_crystal", "obsidian", "respawn_anchor", "glowstone",
            "cobblestone", "water_bucket", "arrow", "experience_bottle",
            "splash_potion", "potion", "arrow", "cobweb", "experience_bottle");

    Step step = Step.PLACE;
    /** Restocking from an ender chest instead, when it has no shulker box left. */
    private final boolean ender;
    private final String what;

    ShulkerRestock(ServerPlayer bot) {
        boolean box = false, chest = false;
        var inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            String id = BotController.id(inv.getItem(i));
            box |= id.endsWith("shulker_box");
            chest |= id.equals("ender_chest");
        }
        ender = !box && chest;
        what = ender ? "ender chest" : "shulker box";
    }

    private boolean isBox(BlockState st) {
        return ender ? st.is(net.minecraft.world.level.block.Blocks.ENDER_CHEST) : st.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isItem(String id) {
        return ender ? id.equals("ender_chest") : id.endsWith("shulker_box");
    }
    private BlockPos box;
    private int wait, taken, ticks;
    private int tool = -1;
    private boolean interrupted;
    private float breakProgress;
    private int pickupTicks, countBefore;
    private boolean countSet;
    /** Where to walk to pick the dropped box up, or null to stand still. */
    Vec3 walkTo;
    String result = "";

    /**
     * Why a restock should not start, or null when it can. An ender chest without Silk Touch breaks into
     * obsidian, so with only one ender chest and no Silk Touch pickaxe it would be lost.
     */
    static String blocker(ServerPlayer bot) {
        ShulkerRestock r = new ShulkerRestock(bot);
        if (!r.ender) return null;
        if (silkSlot(bot) >= 0) return null;
        return "it has no Silk Touch pickaxe, so an ender chest would break into obsidian and be lost";
    }

    private static boolean silk(ItemStack stack) {
        if (!BotController.id(stack).endsWith("_pickaxe")) return false;
        var ench = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        if (ench == null) return false;
        for (var h : ench.keySet()) if (h.getRegisteredName().endsWith("silk_touch")) return true;
        return false;
    }

    /** Inventory slot 0 to 35 with a Silk Touch pickaxe, or -1. */
    private static int silkSlot(ServerPlayer bot) {
        var inv = bot.getInventory();
        for (int i = 0; i < 36; i++) if (silk(inv.getItem(i))) return i;
        return -1;
    }

    private int carried(ServerPlayer bot) {
        var inv = bot.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) if (isItem(BotController.id(inv.getItem(i)))) n += inv.getItem(i).getCount();
        return n;
    }

    /** Runs one tick. Returns true when finished or aborted. */
    boolean tick(ServerPlayer bot, ServerPlayer target) {
        ticks++;
        ServerLevel level = bot.level();
        boolean placed = box != null && isBox(level.getBlockState(box));
        walkTo = null;
        if (target != null && bot.distanceTo(target) < 6 && step != Step.BREAK && step != Step.PICKUP && step != Step.DONE) {
            bot.closeContainer();
            if (!placed) {
                result = "Restock stopped before placing the " + what + ": the opponent came close.";
                step = Step.DONE;
                return true;
            }
            // The box is down: stop taking, and get the box back before fighting.
            interrupted = true;
            step = Step.BREAK;
            wait = 0;
        }
        if (ticks > 400 && step != Step.BREAK && step != Step.PICKUP) {
            bot.closeContainer();
            if (!placed) {
                result = "Restock timed out.";
                step = Step.DONE;
                return true;
            }
            // Taking too long, but the box is down: still get it back.
            step = Step.BREAK;
            wait = 0;
        }
        if (ticks > 1200) {
            bot.closeContainer();
            result = "Restock timed out, and the " + what + " was left behind.";
            step = Step.DONE;
            return true;
        }
        if (wait > 0) {
            wait--;
            return false;
        }
        switch (step) {
            case PLACE -> {
                int slot = hotbarSlot(bot, this::isItem);
                if (slot < 0) {
                    slot = toHotbar(bot, this::isItem);
                    if (slot < 0) return done("No " + what + " to place.");
                }
                String why = blocker(bot);
                if (why != null) return done("Did not place the ender chest: " + why + ".");
                box = spot(bot);
                if (box == null) return done("No room to place a " + what + ".");
                ServerMoves.select(bot, slot);
                look(bot, box);
                BlockPos below = box.below();
                bot.gameMode.useItemOn(bot, level, bot.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false));
                if (!isBox(level.getBlockState(box))) return done("The " + what + " did not place.");
                step = Step.OPEN;
                wait = 2;
            }
            case OPEN -> {
                look(bot, box);
                bot.gameMode.useItemOn(bot, level, bot.getMainHandItem(), InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(box).add(0, 0.5, 0), Direction.UP, box, false));
                step = Step.TAKE;
                wait = 4;
            }
            case TAKE -> {
                if (!isBox(level.getBlockState(box))) return done("The " + what + " is gone.");
                // An ender chest holds the player's own ender chest items, the same from any ender chest.
                Container c = ender ? bot.getEnderChestInventory() : level.getBlockEntity(box) instanceof Container bc ? bc : null;
                if (c == null) return done("The " + what + " is gone.");
                int from = best(c);
                if (from < 0) {
                    step = Step.CLOSE;
                    return false;
                }
                ItemStack stack = c.getItem(from).copy();
                int before = stack.getCount();
                bot.getInventory().add(stack);
                if (stack.getCount() == before) {
                    step = Step.CLOSE;
                    return false;
                }
                c.setItem(from, stack);
                c.setChanged();
                taken += before - stack.getCount();
            }
            case CLOSE -> {
                bot.closeContainer();
                step = Step.BREAK;
                wait = 2;
            }
            case BREAK -> {
                BlockState state = level.getBlockState(box);
                if (!isBox(state)) {
                    // Someone else broke it: the item may be lying there, so go get it.
                    startPickup(bot);
                    return false;
                }
                // A pickaxe from anywhere in the inventory. Without one it mines the box by hand, which is
                // slower, and a shulker box still drops whatever breaks it.
                if (tool < 0 && ender) {
                    // Only Silk Touch brings an ender chest back.
                    int silk = silkSlot(bot);
                    if (silk >= 9) {
                        var inv = bot.getInventory();
                        int to = emptyHotbar(bot);
                        if (to < 0) to = inv.getSelectedSlot();
                        ItemStack moving = inv.getItem(silk);
                        inv.setItem(silk, inv.getItem(to));
                        inv.setItem(to, moving);
                        silk = to;
                    }
                    tool = silk;
                    if (tool < 0) return done("Took " + taken + " items from the ender chest and left it, since there is no Silk Touch pickaxe.");
                }
                if (tool < 0) {
                    tool = hotbarSlot(bot, s2 -> s2.endsWith("_pickaxe"));
                    if (tool < 0) tool = toHotbar(bot, s2 -> s2.endsWith("_pickaxe"));
                    // An ender chest takes about two minutes by hand, so without a pickaxe it stays.
                    if (tool < 0 && ender) return done("Took " + taken + " items from the ender chest and left it, since there is no pickaxe.");
                    if (tool < 0) tool = emptyHotbar(bot);
                    if (tool < 0) tool = bot.getInventory().getSelectedSlot();
                }
                ServerMoves.select(bot, tool);
                look(bot, box);
                // Same mining speed a player gets: the game's own progress per tick for this block and tool.
                breakProgress += state.getDestroyProgress(bot, level, box);
                bot.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
                level.destroyBlockProgress(bot.getId(), box, Math.min(9, (int) (breakProgress * 10)));
                if (breakProgress >= 1) {
                    level.destroyBlockProgress(bot.getId(), box, -1);
                    countBefore = carried(bot);
                    countSet = true;
                    bot.gameMode.destroyBlock(box);
                    startPickup(bot);
                }
            }
            case PICKUP -> {
                // Broken: the box drops as an item. Walk over it until it is back in the inventory.
                pickupTicks++;
                if (carried(bot) > countBefore) {
                    return done("Took " + taken + " items from a " + what + " and picked it back up"
                            + (id(bot, tool).endsWith("_pickaxe") ? "" : " by hand") + (interrupted ? ", cut short since the opponent came close." : "."));
                }
                var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(box).inflate(6), e -> isItem(BotController.id(e.getItem())));
                if (drops.isEmpty()) {
                    // Nothing on the ground: it went straight into the inventory, or burned or fell away.
                    if (pickupTicks > 5) return done("Took " + taken + " items from a " + what + ", but the " + what + " did not drop to pick up.");
                    return false;
                }
                if (pickupTicks > 80) return done("Took " + taken + " items from a " + what + ", but could not reach it to pick it back up.");
                var drop = drops.get(0);
                for (var e : drops) if (e.distanceTo(bot) < drop.distanceTo(bot)) drop = e;
                // The game picks the item up once the bot touches it. Standing on it is enough.
                if (Math.hypot(drop.getX() - bot.getX(), drop.getZ() - bot.getZ()) > 0.4) walkTo = drop.position();
            }
            case DONE -> {
                return true;
            }
        }
        return false;
    }

    private void startPickup(ServerPlayer bot) {
        if (step != Step.PICKUP) {
            if (!countSet) {
                countBefore = carried(bot);
                countSet = true;
            }
            step = Step.PICKUP;
            pickupTicks = 0;
        }
    }

    private boolean done(String why) {
        result = why;
        step = Step.DONE;
        return true;
    }

    /** Slot in the box holding the most wanted item, or -1. */
    private static int best(Container c) {
        int bestSlot = -1, bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            String id = BotController.id(s);
            // Packed shulker boxes from an ender chest come last, after the loose supplies.
            int rank = id.endsWith("shulker_box") ? WANTED.size() : WANTED.indexOf(id);
            if (rank >= 0 && rank < bestRank) {
                bestRank = rank;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    /**
     * A spot for the box within easy reach: air with room above for the lid, a solid top under it, and clear
     * of the bot itself. Works on slopes and steps, not only on flat ground next to the bot.
     */
    private static BlockPos spot(ServerPlayer bot) {
        ServerLevel level = bot.level();
        BlockPos feet = bot.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = feet.offset(dx, dy, dz);
                    if (!level.getBlockState(p).canBeReplaced() || !level.getBlockState(p.above()).isAir()) continue;
                    if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
                    if (bot.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(p))) continue;
                    double d = bot.getEyePosition().distanceTo(Vec3.atCenterOf(p));
                    if (d > 4.0) continue;
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private static String id(ServerPlayer bot, int slot) {
        return slot < 0 ? "air" : BotController.id(bot.getInventory().getItem(slot));
    }

    private static int emptyHotbar(ServerPlayer bot) {
        for (int i = 0; i < 9; i++) if (bot.getInventory().getItem(i).isEmpty()) return i;
        return -1;
    }

    /** Brings a matching stack into the hotbar: an empty slot, or a trade with a slot that is not a weapon, totem, or food. */
    private static int toHotbar(ServerPlayer bot, java.util.function.Predicate<String> match) {
        int slot = moveToHotbar(bot, match);
        if (slot >= 0) return slot;
        var inv = bot.getInventory();
        int from = -1;
        for (int i = 9; i < 36 && from < 0; i++) if (match.test(BotController.id(inv.getItem(i)))) from = i;
        if (from < 0) return -1;
        for (int i = 0; i < 9; i++) {
            String id = BotController.id(inv.getItem(i));
            if (id.endsWith("_sword") || id.endsWith("_axe") || id.equals("mace") || id.equals("totem_of_undying")
                    || id.contains("golden_apple") || id.endsWith("shulker_box")) continue;
            ItemStack moving = inv.getItem(from);
            inv.setItem(from, inv.getItem(i));
            inv.setItem(i, moving);
            return i;
        }
        return -1;
    }

    private static void look(ServerPlayer bot, BlockPos p) {
        double dx = p.getX() + 0.5 - bot.getX(), dy = p.getY() + 0.5 - bot.getEyeY(), dz = p.getZ() + 0.5 - bot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        bot.setYRot(yaw);
        bot.setXRot(pitch);
        bot.setYHeadRot(yaw);
    }

    private static int hotbarSlot(ServerPlayer bot, java.util.function.Predicate<String> match) {
        for (int i = 0; i < 9; i++) if (match.test(BotController.id(bot.getInventory().getItem(i)))) return i;
        return -1;
    }

    /** Moves a matching stack from the main inventory into an empty hotbar slot. */
    private static int moveToHotbar(ServerPlayer bot, java.util.function.Predicate<String> match) {
        var inv = bot.getInventory();
        int from = -1, to = -1;
        for (int i = 9; i < 36 && from < 0; i++) if (match.test(BotController.id(inv.getItem(i)))) from = i;
        for (int i = 0; i < 9 && to < 0; i++) if (inv.getItem(i).isEmpty()) to = i;
        if (from < 0 || to < 0) return -1;
        inv.setItem(to, inv.getItem(from));
        inv.setItem(from, ItemStack.EMPTY);
        return to;
    }
}
