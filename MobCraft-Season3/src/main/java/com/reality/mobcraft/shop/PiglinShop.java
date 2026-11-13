package com.reality.mobcraft.shop;

import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import com.reality.mobcraft.mob.MobDrops;
import com.reality.mobcraft.mob.MobItems;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.MobRoster;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Piglin Barter Shop. Right-click an adult piglin while holding gold (ingots,
 * nuggets or blocks) and a menu opens with everything a piglin barters. Click an
 * item to buy it. Anyone can use it, whatever mob they are.
 *
 * Items that belong to a MobCraft mob aren't sold to anyone while some player is that
 * mob, online or not: while Merc is the Enderman, only Merc has ender pearls, so
 * everyone trades with him. Once nobody is the Enderman, the piglin sells them again.
 * An item belongs to a mob if the mob gives it to itself or drops it on death
 * (MobItems and MobDrops). Who is which mob comes from MobRoster. Those items stay
 * in the menu, marked not for sale and naming who has them.
 *
 * Prices follow how rich you are, so gold farms don't make everything free. The
 * prices in OFFERS are for someone with a stack (64) of gold. The shop counts all
 * your gold: ingots, nuggets, blocks and raw gold, in your inventory, in shulker
 * boxes and bundles you carry, and in your ender chest. The price multiplier is the
 * square root of (gold / 64), kept between 0.5 and 3:
 *
 *   16 gold or less  x0.5      64 gold   x1       256 gold  x2
 *   576 gold or more x3
 *
 * You pay with the gold on you: ingots first, then nuggets, then blocks, with
 * change given back. Spending gold makes the next price lower.
 *
 * A piglin that's angry at you won't open its shop. Throwing gold on the ground for a
 * piglin to pick up still gives a random barter, like vanilla.
 */
public final class PiglinShop {

    private static final int ROWS = 3;
    private static final int BARTER_START = 0;   // rows 1 and 2
    private static final int PURSE_SLOT = 22;    // row 3, middle
    private static final double REACH = 8;

    private static final int NORMAL_GOLD = 64;
    private static final double MIN_MULTIPLIER = 0.5;
    private static final double MAX_MULTIPLIER = 3.0;

    /** One thing for sale: the item made fresh on each purchase, its price at 64 gold, and an extra lore line. */
    private record Offer(Function<ServerLevel, ItemStack> make, int price, String note) {}

    /** What piglins barter. */
    private static final List<Offer> BARTER = List.of(
            new Offer(l -> soulSpeed(l, new ItemStack(Items.ENCHANTED_BOOK), true), 20, "Soul Speed I to III, random"),
            new Offer(l -> soulSpeed(l, new ItemStack(Items.IRON_BOOTS), false), 18, "Soul Speed I to III, random"),
            new Offer(l -> PotionContents.createItemStack(Items.POTION, Potions.FIRE_RESISTANCE), 6, null),
            new Offer(l -> PotionContents.createItemStack(Items.SPLASH_POTION, Potions.FIRE_RESISTANCE), 7, null),
            new Offer(l -> new ItemStack(Items.ENDER_PEARL, 4), 8, null),
            new Offer(l -> new ItemStack(Items.SPECTRAL_ARROW, 8), 4, null),
            new Offer(l -> new ItemStack(Items.OBSIDIAN, 1), 3, null),
            new Offer(l -> new ItemStack(Items.CRYING_OBSIDIAN, 2), 3, null),
            new Offer(l -> new ItemStack(Items.QUARTZ, 8), 3, null),
            new Offer(l -> new ItemStack(Items.IRON_NUGGET, 16), 2, null),
            new Offer(l -> new ItemStack(Items.STRING, 8), 2, null),
            new Offer(l -> new ItemStack(Items.LEATHER, 4), 2, null),
            new Offer(l -> new ItemStack(Items.FIRE_CHARGE, 1), 1, null),
            new Offer(l -> new ItemStack(Items.SOUL_SAND, 8), 1, null),
            new Offer(l -> new ItemStack(Items.NETHER_BRICK, 8), 1, null),
            new Offer(l -> new ItemStack(Items.GRAVEL, 16), 1, null),
            new Offer(l -> new ItemStack(Items.BLACKSTONE, 16), 1, null),
            new Offer(l -> PotionContents.createItemStack(Items.POTION, Potions.WATER), 1, null));

    private PiglinShop() {}

    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !(target instanceof Piglin piglin)) return InteractionResult.PASS;
            if (!isGold(player.getMainHandItem()) || piglin.isBaby()) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer buyer) || !piglin.isAlive()) return InteractionResult.PASS;
            if (piglin.getTarget() == buyer) {
                KitUtil.actionBar(buyer, "This piglin is angry at you. No trading.");
                return InteractionResult.FAIL;
            }
            open(buyer, piglin);
            return InteractionResult.SUCCESS;
        });
    }

    private static boolean isGold(ItemStack stack) {
        return stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLD_NUGGET) || stack.is(Items.GOLD_BLOCK);
    }

    private static void open(ServerPlayer player, Piglin piglin) {
        piglin.getNavigation().stop();
        player.level().playSound(null, piglin.blockPosition(), SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.NEUTRAL, 1f, 1f);
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new ShopMenu(id, inventory, piglin),
                Component.literal("Piglin Barter Shop")));
    }

    // --- Prices ------------------------------------------------------------------------

    /** How much prices are scaled for this much gold. */
    static double multiplier(int gold) {
        double m = Math.sqrt(gold / (double) NORMAL_GOLD);
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, m));
    }

    static int priceFor(Offer offer, double multiplier) {
        return Math.max(1, (int) Math.round(offer.price() * multiplier));
    }

    // --- The menu -----------------------------------------------------------------------

    /** A chest menu you can only buy from. Nothing in it can be taken or moved. */
    private static final class ShopMenu extends ChestMenu {
        private final SimpleContainer shelf;
        private final Piglin piglin;

        ShopMenu(int id, Inventory inventory, Piglin piglin) {
            this(id, inventory, piglin, new SimpleContainer(9 * ROWS));
        }

        private ShopMenu(int id, Inventory inventory, Piglin piglin, SimpleContainer shelf) {
            super(MenuType.GENERIC_9x3, id, inventory, shelf, ROWS);
            this.shelf = shelf;
            this.piglin = piglin;
            if (inventory.player instanceof ServerPlayer player) fill(player);
        }

        /** The offer in a slot, or null. */
        private static Offer offerAt(int slot) {
            if (slot >= BARTER_START && slot < BARTER_START + BARTER.size()) return BARTER.get(slot - BARTER_START);
            return null;
        }

        /** Puts every offer on the shelf at today's price for this player. */
        private void fill(ServerPlayer player) {
            int owned = goldOwned(player);
            int onYou = goldOnYou(player) / 9;
            double multiplier = multiplier(owned);
            ServerLevel level = player.level();
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                Offer offer = offerAt(slot);
                if (offer == null) continue;
                int price = priceFor(offer, multiplier);
                ItemStack shown = offer.make().apply(level);
                List<Component> lore = new ArrayList<>();
                if (offer.note() != null) lore.add(Component.literal(offer.note()).withStyle(ChatFormatting.GRAY));
                String owner = ownerOf(player, shown);
                if (owner != null) {
                    lore.add(Component.literal("Not for sale: " + owner).withStyle(ChatFormatting.RED));
                    lore.add(Component.literal(owner.startsWith("you ") ? "Give it to yourself instead" : "Get it from them instead")
                            .withStyle(ChatFormatting.DARK_GRAY));
                } else {
                    lore.add(Component.literal("Price: " + price + " gold")
                            .withStyle(onYou >= price ? ChatFormatting.GREEN : ChatFormatting.RED));
                    lore.add(Component.literal("Click to buy").withStyle(ChatFormatting.DARK_GRAY));
                }
                shown.set(DataComponents.LORE, new ItemLore(lore));
                shelf.setItem(slot, shown);
            }

            ItemStack purse = new ItemStack(Items.GOLD_INGOT, Math.max(1, Math.min(onYou, 64)));
            purse.set(DataComponents.CUSTOM_NAME, Component.literal("Gold on you: " + onYou).withStyle(ChatFormatting.GOLD));
            String mood = multiplier > 1.05 ? "Lots of gold: higher prices"
                    : multiplier < 0.95 ? "Low on gold: cheaper prices"
                    : "Normal prices";
            purse.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("All your gold: " + owned).withStyle(ChatFormatting.GRAY),
                    Component.literal("Prices x" + String.format("%.1f", multiplier)).withStyle(ChatFormatting.YELLOW),
                    Component.literal(mood).withStyle(ChatFormatting.DARK_GRAY))));
            shelf.setItem(PURSE_SLOT, purse);
        }

        @Override
        public void clicked(int slot, int button, ContainerInput input, Player clicker) {
            // Nothing moves while the shop is open. A click on an offer buys it.
            if (clicker instanceof ServerPlayer player && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
                Offer offer = slot >= 0 && slot < shelf.getContainerSize() ? offerAt(slot) : null;
                if (offer != null) {
                    buy(player, offer);
                    fill(player);
                }
            }
            this.sendAllDataToRemote();
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return piglin.isAlive() && piglin.getTarget() != player && piglin.distanceTo(player) <= REACH;
        }

        @Override
        public void removed(Player player) {
            // The shelf's display items never go back to the player.
            shelf.clearContent();
            super.removed(player);
        }

        private void buy(ServerPlayer player, Offer offer) {
            String owner = ownerOf(player, offer.make().apply(player.level()));
            if (owner != null) {
                player.level().playSound(null, piglin.blockPosition(), SoundEvents.PIGLIN_JEALOUS, SoundSource.NEUTRAL, 1f, 1f);
                KitUtil.actionBar(player, "Not for sale: " + owner + ".");
                return;
            }
            int price = priceFor(offer, multiplier(goldOwned(player)));
            if (goldOnYou(player) < price * 9) {
                player.level().playSound(null, piglin.blockPosition(), SoundEvents.PIGLIN_JEALOUS, SoundSource.NEUTRAL, 1f, 1f);
                KitUtil.actionBar(player, "Not enough gold on you. That costs " + price + ".");
                return;
            }
            pay(player, price * 9);
            ItemStack bought = offer.make().apply(player.level());
            player.getInventory().placeItemBackInInventory(bought, Prediction.SERVER_ONLY); // drops it if you're full
            player.level().playSound(null, piglin.blockPosition(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        }
    }

    // --- Mob items ---------------------------------------------------------------------

    /**
     * Who has this item as their mob's own, like "Merc is the Enderman", or null if
     * nobody does. Checks every player's mob, online or not, for its self-give items
     * and death drops.
     */
    private static String ownerOf(ServerPlayer buyer, ItemStack item) {
        String id = BuiltInRegistries.ITEM.getKey(item.getItem()).getPath();
        MobRoster.update(buyer);
        for (Map.Entry<String, MobRoster.Entry> line : MobRoster.all().entrySet()) {
            MobType type = MobType.byId(line.getValue().mob());
            if (type == null) continue;
            boolean theirs = MobItems.get(type).contains(id)
                    || MobDrops.get(type).stream().anyMatch(drop -> drop.itemId().equals(id));
            if (!theirs) continue;
            boolean self = line.getKey().equals(buyer.getStringUUID());
            return (self ? "you are" : line.getValue().name() + " is") + " the " + type.displayName;
        }
        return null;
    }

    // --- Gold --------------------------------------------------------------------------
    // Counted in nuggets so ingots, nuggets and blocks add up exactly: 1 ingot = 9 nuggets.

    /** Gold value of one stack, in nuggets, not looking inside it. */
    private static int nuggetsIn(ItemStack stack) {
        if (stack.is(Items.GOLD_NUGGET)) return stack.getCount();
        if (stack.is(Items.GOLD_INGOT) || stack.is(Items.RAW_GOLD)) return stack.getCount() * 9;
        if (stack.is(Items.GOLD_BLOCK) || stack.is(Items.RAW_GOLD_BLOCK)) return stack.getCount() * 81;
        return 0;
    }

    /** Gold value of a stack in nuggets, including what's inside it if it's a shulker box or bundle. */
    private static int nuggetsDeep(ItemStack stack) {
        int total = nuggetsIn(stack);
        ItemContainerContents box = stack.get(DataComponents.CONTAINER);
        if (box != null) total += box.nonEmptyItemCopyStream().mapToInt(PiglinShop::nuggetsDeep).sum();
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) total += bundle.itemCopies().mapToInt(PiglinShop::nuggetsDeep).sum();
        return total;
    }

    private static int nuggetsDeep(Container container) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) total += nuggetsDeep(container.getItem(i));
        return total;
    }

    /** All the gold this player has, in ingots: inventory, boxes and bundles in it, and the ender chest. Sets the prices. */
    static int goldOwned(ServerPlayer player) {
        return (nuggetsDeep(player.getInventory()) + nuggetsDeep(player.getEnderChestInventory())) / 9;
    }

    /** Gold loose in the inventory, in nuggets, that can pay right now. Raw gold doesn't pay. */
    private static int goldOnYou(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.GOLD_NUGGET) || stack.is(Items.GOLD_INGOT) || stack.is(Items.GOLD_BLOCK)) total += nuggetsIn(stack);
        }
        return total;
    }

    /** Takes this many nuggets' worth: ingots first, then nuggets, then blocks, and gives change back. */
    private static void pay(ServerPlayer player, int nuggets) {
        Inventory inventory = player.getInventory();
        nuggets -= take(inventory, Items.GOLD_INGOT.getDefaultInstance(), nuggets / 9) * 9;
        nuggets -= take(inventory, Items.GOLD_NUGGET.getDefaultInstance(), nuggets);
        while (nuggets > 0 && take(inventory, Items.GOLD_INGOT.getDefaultInstance(), 1) == 1) nuggets -= 9;
        while (nuggets > 0 && take(inventory, Items.GOLD_BLOCK.getDefaultInstance(), 1) == 1) nuggets -= 81;
        int change = -nuggets;
        if (change >= 9) inventory.placeItemBackInInventory(new ItemStack(Items.GOLD_INGOT, change / 9), Prediction.SERVER_ONLY);
        if (change % 9 > 0) inventory.placeItemBackInInventory(new ItemStack(Items.GOLD_NUGGET, change % 9), Prediction.SERVER_ONLY);
        inventory.setChanged();
    }

    /** Removes up to count of this item from the inventory. Returns how many came out. */
    private static int take(Inventory inventory, ItemStack kind, int count) {
        int taken = 0;
        for (int i = 0; i < inventory.getContainerSize() && taken < count; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(kind.getItem())) continue;
            int n = Math.min(count - taken, stack.getCount());
            stack.shrink(n);
            taken += n;
        }
        return taken;
    }

    // --- Items ---------------------------------------------------------------------------

    /** A book or boots with Soul Speed at a random level from I to III, like a real barter. */
    private static ItemStack soulSpeed(ServerLevel level, ItemStack stack, boolean book) {
        Holder<Enchantment> soulSpeed = KitUtil.enchantment(level, Enchantments.SOUL_SPEED);
        int lvl = 1 + level.getRandom().nextInt(3);
        if (book) return EnchantmentHelper.createBook(new EnchantmentInstance(soulSpeed, lvl));
        stack.enchant(soulSpeed, lvl);
        return stack;
    }
}
