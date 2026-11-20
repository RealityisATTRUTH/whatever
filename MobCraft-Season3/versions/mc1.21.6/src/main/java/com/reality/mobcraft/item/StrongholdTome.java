package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Stronghold Tome: a rare book found only in stronghold libraries, about 1 chest in 10.
 * Hold right-click to open it: the covers swing open, the pages flip, and glowing enchanting
 * letters stream into it, like the enchanting table's book (client/TomeRenderer). Let go and it
 * shuts. Everyone nearby sees and hears it. What it does once it's open isn't decided yet.
 */
public final class StrongholdTome extends Item {

    public static final String NAME = "stronghold_tome";
    /** How many times its holder has died with it: 1 to 4 is more and more burnt, the 5th burns it to ash. */
    public static final net.minecraft.core.component.DataComponentType<Integer> BURNS = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE, MobCraft.id("tome_burns"),
            net.minecraft.core.component.DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    public static final int MAX_BURNS = 5;
    private static final float LIBRARY_CHANCE = 0.1f;

    public StrongholdTome(Properties properties) {
        super(properties);
    }

    static Item create() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id(NAME));
        ItemLore lore = lore(0);
        return Registry.register(BuiltInRegistries.ITEM, key, new StrongholdTome(new Properties().setId(key)
                .stacksTo(1).rarity(Rarity.EPIC).component(net.minecraft.core.component.DataComponents.LORE, lore)));
    }

    /** Puts it in the stronghold library chests, and burns it a little more every time its holder dies. */
    public static void register() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) onHolderDied(player);
        });
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            if (!source.isBuiltin() || !key.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)) return;
            table.withPool(LootPool.lootPool()
                    .when(LootItemRandomChanceCondition.randomChance(LIBRARY_CHANCE))
                    .add(LootItem.lootTableItem(ModItems.STRONGHOLD_TOME)));
        });
    }

    public static int burns(ItemStack stack) {
        return stack.getOrDefault(BURNS, 0);
    }

    /**
     * Its holder died. Every Tome they had, whether it dropped on the ground or stayed with them (keepInventory),
     * gets one burn darker. The fifth time it catches fire, burns to ash and blows away on the wind.
     */
    private static void onHolderDied(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.level();
        java.util.List<ItemStack> tomes = new java.util.ArrayList<>();
        java.util.List<net.minecraft.world.entity.item.ItemEntity> dropped = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(4),
                e -> e.getAge() <= 1 && e.getItem().is(ModItems.STRONGHOLD_TOME));
        for (var e : dropped) tomes.add(e.getItem());
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.STRONGHOLD_TOME)) tomes.add(stack);
        }
        for (ItemStack tome : tomes) {
            int burns = burns(tome) + 1;
            if (burns >= MAX_BURNS) {
                tome.setCount(0);
                ashes(level, player.position());
                player.sendSystemMessage(Component.literal("The Stronghold Tome caught fire and burned to ash.")
                        .withStyle(ChatFormatting.DARK_RED));
            } else {
                tome.set(BURNS, burns);
                tome.set(net.minecraft.core.component.DataComponents.LORE, lore(burns, TomePowers.power(tome)));
                level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 1.3f);
            }
        }
        for (var e : dropped) if (e.getItem().isEmpty()) e.discard();
    }

    /** The book bursting into flame, then a cloud of ash drifting away on the wind. */
    private static void ashes(ServerLevel level, Vec3 at) {
        double x = at.x, y = at.y + 0.8, z = at.z;
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 30, 0.25, 0.3, 0.25, 0.04);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 20, 0.3, 0.4, 0.3, 0.03);
        level.sendParticles(ParticleTypes.ENCHANT, x, y, z, 40, 0.4, 0.4, 0.4, 0.6);
        double windX = 0.9, windZ = 0.4;
        for (int i = 0; i < 40; i++) {
            // count 0: the offsets are the particle's velocity, so the ash blows away downwind
            level.sendParticles(ParticleTypes.WHITE_ASH, x, y + i * 0.02, z, 0, windX, 0.15, windZ, 0.3 + level.getRandom().nextDouble() * 0.4);
            level.sendParticles(ParticleTypes.ASH, x, y, z, 0, windX, 0.2, windZ, 0.2 + level.getRandom().nextDouble() * 0.4);
        }
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.7f);
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    static ItemLore lore(int burns) { return lore(burns, 0); }

    static ItemLore lore(int burns, int power) {
        java.util.List<Component> lines = new java.util.ArrayList<>(List.of(
                Component.literal("Found in stronghold libraries").withStyle(ChatFormatting.GRAY),
                Component.literal("Hold right-click to open it, sneak + right-click to turn the page").withStyle(ChatFormatting.DARK_PURPLE),
                Component.literal(TomePowers.NAMES[power] + ": " + TomePowers.HOW[power]).withStyle(ChatFormatting.LIGHT_PURPLE)));
        if (burns > 0) {
            String[] words = {"", "Singed", "Scorched", "Charred", "Barely holding together"};
            int left = MAX_BURNS - burns;
            lines.add(Component.literal(words[Math.min(burns, 4)] + ". " + (left == 1 ? "One more death and it turns to ash"
                    : left + " more deaths and it turns to ash")).withStyle(ChatFormatting.RED));
        }
        return new ItemLore(lines);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player.isShiftKeyDown()) {
            // Turn to the next page: the next power.
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) TomePowers.nextPage(sp, player.getItemInHand(hand));
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        if (!level.isClientSide()) {
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.8f);
            level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.4f, 1.4f);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    /**
     * While it's open: enchanting letters pour into the book, like into an enchanting table, and now and then a
     * page turns. Everyone else gets them from the server; the reader's own game makes its own, a little further
     * out in front, so they don't fly into the reader's face.
     */
    /** A badly burnt Tome smoulders in your hands, and now and then an ember drifts off it. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, net.minecraft.world.entity.Entity owner,
                              net.minecraft.world.entity.EquipmentSlot slot) {
        int burns = burns(stack);
        if (burns < 2 || slot == null || (slot != net.minecraft.world.entity.EquipmentSlot.MAINHAND && slot != net.minecraft.world.entity.EquipmentSlot.OFFHAND) || level.getGameTime() % (burns >= 4 ? 6 : 14) != 0) return;
        Vec3 look = owner.getLookAngle();
        level.sendParticles(ParticleTypes.SMOKE, owner.getX() + look.x * 0.8, owner.getY() + 0.85, owner.getZ() + look.z * 0.8,
                1, 0.08, 0.05, 0.08, 0.005);
        if (burns >= 3 && level.getRandom().nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.SMALL_FLAME, owner.getX() + look.x * 0.8, owner.getY() + 0.85, owner.getZ() + look.z * 0.8,
                    1, 0.05, 0.05, 0.05, 0.002);
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int used = getUseDuration(stack, user) - remaining;
        if (used % 2 != 0) return;
        Vec3 look = user.getLookAngle();
        if (level.isClientSide()) {
            if (!(user instanceof Player reader) || !reader.isLocalPlayer()) return;
            Vec3 book = user.getEyePosition().add(look.scale(1.6)).add(0, -0.5, 0);
            for (int i = 0; i < 2; i++) letter(level, user, book, 1.2);
            return;
        }
        ServerLevel server = (ServerLevel) level;
        // Once the book is fully open, the page it's turned to goes off (TomePowers).
        if (used >= 30 && user instanceof net.minecraft.server.level.ServerPlayer reader) TomePowers.read(reader, stack);
        Vec3 book = user.getEyePosition().add(look.x * 0.5, -0.55, look.z * 0.5);
        for (net.minecraft.server.level.ServerPlayer watcher : server.players()) {
            if (watcher == user || watcher.distanceToSqr(user) > 48 * 48) continue;
            for (int i = 0; i < 3; i++) {
                double dx = (user.getRandom().nextDouble() - 0.5) * 2.4, dy = user.getRandom().nextDouble() * 1.4,
                        dz = (user.getRandom().nextDouble() - 0.5) * 2.4;
                server.sendParticles(watcher, ParticleTypes.ENCHANT, false, false, book.x, book.y + 0.3, book.z, 0, dx, dy, dz, 1.0);
            }
        }
        if (used > 0 && used % 30 == 0) {
            level.playSound(null, user.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6f, 0.9f + user.getRandom().nextFloat() * 0.3f);
        }
    }

    /** One enchanting letter flying into the book from somewhere around it. */
    private static void letter(Level level, LivingEntity user, Vec3 book, double spread) {
        double dx = (user.getRandom().nextDouble() - 0.5) * spread * 2, dy = user.getRandom().nextDouble() * spread,
                dz = (user.getRandom().nextDouble() - 0.5) * spread * 2;
        level.addParticle(ParticleTypes.ENCHANT, book.x, book.y + 0.3, book.z, dx, dy, dz);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (!level.isClientSide()) {
            level.playSound(null, user.blockPosition(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        return true;
    }
}
