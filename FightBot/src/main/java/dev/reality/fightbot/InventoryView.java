package dev.reality.fightbot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * A live, read-only view of a bot's inventory, in a chest screen that updates every tick while it is open.
 *
 * Rows from the top: the hotbar, the three rows of the main inventory, armor and offhand, and a row of
 * notes: health, totems, healing potions, the selected slot, and what the bot is doing. The screen shows
 * copies, and every click in it is ignored, so nothing can be taken out or put in.
 */
final class InventoryView {
    private static final Map<UUID, View> OPEN = new HashMap<>();

    private record View(String bot, SimpleContainer box, ReadOnlyMenu menu) { }

    /** Opens the view for this player. Returns a message for chat. */
    static String open(ServerPlayer viewer, String botName) {
        ServerPlayer bot = viewer.level().getServer().getPlayerList().getPlayerByName(botName);
        if (bot == null) return "No player named " + botName + " is online.";
        SimpleContainer box = new SimpleContainer(54);
        ReadOnlyMenu[] made = new ReadOnlyMenu[1];
        viewer.openMenu(new SimpleMenuProvider((id, inv, p) -> {
            made[0] = new ReadOnlyMenu(id, inv, box);
            return made[0];
        }, Component.literal(botName + " (live)")));
        if (made[0] == null) return "Could not open the view.";
        OPEN.put(viewer.getUUID(), new View(botName, box, made[0]));
        fill(bot, box, null);
        return "Showing " + botName + "'s inventory. It updates while the screen is open.";
    }

    /** Copies each open view's bot inventory into its screen. Called every server tick. */
    static void tick(MinecraftServer server, Function<String, String> statusOf) {
        if (OPEN.isEmpty()) return;
        for (var it = OPEN.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer viewer = server.getPlayerList().getPlayer(e.getKey());
            View v = e.getValue();
            // Closed, or another screen opened over it.
            if (viewer == null || viewer.containerMenu != v.menu()) {
                it.remove();
                continue;
            }
            ServerPlayer bot = server.getPlayerList().getPlayerByName(v.bot());
            if (bot == null) {
                v.box().setItem(49, note(Items.BARRIER, v.bot() + " is not online", List.of()));
                continue;
            }
            fill(bot, v.box(), statusOf.apply(v.bot()));
        }
    }

    /** Text version, for the console or chat. */
    static List<String> text(ServerPlayer bot) {
        List<String> out = new ArrayList<>();
        var inv = bot.getInventory();
        StringBuilder hot = new StringBuilder("Hotbar:");
        for (int i = 0; i < 9; i++) hot.append(i == inv.getSelectedSlot() ? " [" : " ").append(name(inv.getItem(i))).append(i == inv.getSelectedSlot() ? "]" : "");
        out.add(hot.toString());
        StringBuilder main = new StringBuilder("Backpack:");
        for (int i = 9; i < 36; i++) if (!inv.getItem(i).isEmpty()) main.append(" ").append(name(inv.getItem(i)));
        out.add(main.toString());
        out.add("Armor: " + name(bot.getItemBySlot(EquipmentSlot.HEAD)) + ", " + name(bot.getItemBySlot(EquipmentSlot.CHEST)) + ", "
                + name(bot.getItemBySlot(EquipmentSlot.LEGS)) + ", " + name(bot.getItemBySlot(EquipmentSlot.FEET))
                + ". Offhand: " + name(bot.getOffhandItem()));
        out.add(summary(bot));
        return out;
    }

    private static void fill(ServerPlayer bot, SimpleContainer box, String status) {
        var inv = bot.getInventory();
        for (int i = 0; i < 36; i++) box.setItem(i, inv.getItem(i).copy());
        box.setItem(36, bot.getItemBySlot(EquipmentSlot.HEAD).copy());
        box.setItem(37, bot.getItemBySlot(EquipmentSlot.CHEST).copy());
        box.setItem(38, bot.getItemBySlot(EquipmentSlot.LEGS).copy());
        box.setItem(39, bot.getItemBySlot(EquipmentSlot.FEET).copy());
        box.setItem(40, pane());
        box.setItem(41, bot.getOffhandItem().copy());
        for (int i = 42; i < 45; i++) box.setItem(i, pane());
        int heal = count(bot, s -> potionWith(s, "healing"));
        int healHot = 0;
        for (int i = 0; i < 9; i++) if (potionWith(inv.getItem(i), "healing")) healHot += inv.getItem(i).getCount();
        int totems = count(bot, s -> s.is(Items.TOTEM_OF_UNDYING));
        box.setItem(45, note(Items.REDSTONE, String.format("Health %.1f + %.1f absorption", bot.getHealth(), bot.getAbsorptionAmount()), List.of()));
        box.setItem(46, note(Items.TOTEM_OF_UNDYING, "Totems: " + totems, List.of()));
        box.setItem(47, note(Items.SPLASH_POTION, "Healing potions: " + heal, List.of(healHot + " in the hotbar")));
        box.setItem(48, note(Items.ARROW, "Selected slot: " + (inv.getSelectedSlot() + 1), List.of("Holding " + name(bot.getMainHandItem()))));
        box.setItem(49, note(Items.COMPASS, "Doing", status == null ? List.of("not fighting") : wrap(status)));
        box.setItem(50, note(Items.GOLDEN_APPLE, "Apples: " + count(bot, s -> s.is(Items.GOLDEN_APPLE)) + ", enchanted " + count(bot, s -> s.is(Items.ENCHANTED_GOLDEN_APPLE)), List.of()));
        box.setItem(51, note(Items.ENDER_PEARL, "Pearls: " + count(bot, s -> s.is(Items.ENDER_PEARL)), List.of()));
        box.setItem(52, note(Items.END_CRYSTAL, "Crystals: " + count(bot, s -> s.is(Items.END_CRYSTAL)) + ", obsidian " + count(bot, s -> s.is(Items.OBSIDIAN)), List.of()));
        box.setItem(53, note(Items.COBBLESTONE, "Blocks: " + count(bot, s -> s.is(Items.COBBLESTONE) || s.is(Items.OAK_PLANKS) || s.is(Items.DIRT)), List.of()));
    }

    private static String summary(ServerPlayer bot) {
        return String.format("Health %.1f + %.1f, totems %d, healing potions %d", bot.getHealth(), bot.getAbsorptionAmount(),
                count(bot, s -> s.is(Items.TOTEM_OF_UNDYING)), count(bot, s -> potionWith(s, "healing")));
    }

    private static boolean potionWith(ItemStack s, String word) {
        return (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION) || s.is(Items.POTION)) && BotController.fullId(s).contains(word);
    }

    private static int count(ServerPlayer bot, java.util.function.Predicate<ItemStack> match) {
        var inv = bot.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) if (match.test(inv.getItem(i))) n += inv.getItem(i).getCount();
        if (match.test(bot.getOffhandItem())) n += bot.getOffhandItem().getCount();
        return n;
    }

    private static String name(ItemStack s) {
        if (s.isEmpty()) return "empty";
        return (s.getCount() > 1 ? s.getCount() + " " : "") + BotController.fullId(s);
    }

    private static ItemStack pane() {
        return note(Items.GLASS_PANE, " ", List.of());
    }

    private static ItemStack note(Item item, String title, List<String> lines) {
        ItemStack s = new ItemStack(item);
        s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(title));
        if (!lines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String l : lines) lore.add(Component.literal(l));
            s.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        }
        return s;
    }

    /** Splits a long status into lines that fit a tooltip. */
    private static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            if (line.length() + w.length() > 40) {
                out.add(line.toString().trim());
                line.setLength(0);
            }
            line.append(w).append(' ');
        }
        if (line.length() > 0) out.add(line.toString().trim());
        return out;
    }

    /**
     * A six row chest screen where nothing in the bot's rows can be taken or put in. It is built from plain slots
     * that refuse both, rather than by overriding the click handler, whose types change between versions. The
     * viewer's own inventory below works as normal. Shift clicks move nothing.
     */
    static final class ReadOnlyMenu extends AbstractContainerMenu {
        ReadOnlyMenu(int id, Inventory playerInv, SimpleContainer box) {
            super(MenuType.GENERIC_9x6, id);
            // Same slot order the client's chest screen expects: 54 chest slots, then the player's 27 and 9.
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 9; col++) addSlot(new LockedSlot(box, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    /** A slot that shows its item but can never be picked up from or placed into. */
    static final class LockedSlot extends Slot {
        LockedSlot(SimpleContainer box, int index, int x, int y) {
            super(box, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
