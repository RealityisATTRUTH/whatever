package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.Jeff;
import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import com.reality.mobcraft.net.OpenJeffMenuPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Set;

/**
 * Villager (Jeff). See Jeff for the powered-up and shut-down switch.
 *
 * Powered up
 *   Money         his energy. Up to 100 quadrillion, earning 100 trillion a
 *                 second. Shown in the energy bar.
 *   RTS           he can command every mob, not just his own.
 *   R  Warden Bodyguards   summons 2 Wardens that follow and fight for him.
 *                 1 quadrillion, 30s cooldown, up to 4 at once.
 *   G  Jeff's Menu        every item in the game, his own creative menu.
 *   Commands      he's a server operator while powered up.
 *
 * Shut down
 *   A plain Villager with normal energy. Gives himself emeralds, but only
 *   within 5 blocks of a crafting table or a villager job block. Raid mobs
 *   (pillagers, vindicators, evokers, illusioners, ravagers, witches) do half
 *   again as much damage to him.
 *
 * Allies: villagers and iron golems, either way.
 */
public final class VillagerKit implements MobKit {

    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.VILLAGER, EntityTypes.IRON_GOLEM);
    private static final Set<EntityType<?>> RAIDERS = Set.of(EntityTypes.PILLAGER, EntityTypes.VINDICATOR,
            EntityTypes.EVOKER, EntityTypes.ILLUSIONER, EntityTypes.RAVAGER, EntityTypes.WITCH);

    private static final String GUARDS = "warden_guards";
    private static final String MENU = "jeff_menu";
    private static final long INCOME_PER_SECOND = 100_000_000_000_000L;   // 100 trillion
    private static final long GUARDS_COST = 1_000_000_000_000_000L;       // 1 quadrillion
    private static final int MAX_GUARDS = 4;
    private static final int JOB_RANGE = 5;

    /** Crafting table and every villager job site block. */
    private static final Set<Block> JOB_BLOCKS = Set.of(Blocks.CRAFTING_TABLE, Blocks.COMPOSTER, Blocks.LECTERN,
            Blocks.BARREL, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE,
            Blocks.GRINDSTONE, Blocks.BREWING_STAND, Blocks.CAULDRON, Blocks.WATER_CAULDRON, Blocks.LOOM,
            Blocks.SMITHING_TABLE, Blocks.STONECUTTER);

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void onAssigned(ServerPlayer player) {
        if (player.getAttached(MobAttachments.MONEY) == null) Jeff.setMoney(player, Jeff.MAX_MONEY);
    }

    @Override
    public void tick(ServerPlayer player) {
        KitUtil.calmAllies(player, 32, ALLIES);
        if (player.tickCount % 20 == 0) keepGuards(player);
        if (Jeff.isPowered(player) && player.tickCount % 20 == 0) {
            Jeff.setMoney(player, Math.min(Jeff.MAX_MONEY, Jeff.money(player) + INCOME_PER_SECOND));
        }
    }

    /** Powered up, money is his energy, so the energy bar stays full and unused. */
    @Override
    public float energyRegenPerSecond(ServerPlayer player, MobType type) {
        return Jeff.isPowered(player) ? type.maxEnergy : type.regenPerSecond;
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (!Jeff.isPowered(player)) {
            KitUtil.actionBar(player, "You're just a Villager right now.");
            return;
        }
        if (slot == ABILITY_1 && MobAbilities.isEnabled(player, GUARDS)) {
            summonGuards(player);
        } else if (slot == ABILITY_2 && MobAbilities.isEnabled(player, MENU)) {
            ServerPlayNetworking.send(player, OpenJeffMenuPayload.INSTANCE);
        } else {
            KitUtil.actionBar(player, "Money: " + Jeff.formatMoney(Jeff.money(player)));
        }
    }

    private static void summonGuards(ServerPlayer player) {
        List<Mob> guards = KitUtil.ownedMobs(player, 64, mob -> mob.getType() == EntityTypes.WARDEN);
        if (guards.size() >= MAX_GUARDS) {
            KitUtil.actionBar(player, "You already have " + MAX_GUARDS + " Warden bodyguards.");
            return;
        }
        if (Jeff.money(player) < GUARDS_COST) {
            KitUtil.actionBar(player, "Warden Bodyguards cost " + Jeff.formatMoney(GUARDS_COST) + ".");
            return;
        }
        if (!KitUtil.tryUse(player, GUARDS, "Warden Bodyguards", 0, 600)) return;
        Jeff.spend(player, GUARDS_COST);
        int room = Math.min(2, MAX_GUARDS - guards.size());
        for (int i = 0; i < room; i++) {
            double angle = Math.PI * i;
            KitUtil.summon(player, "minecraft:warden", player.getX() + Math.cos(angle) * 2, player.getY(),
                    player.getZ() + Math.sin(angle) * 2, "");
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.5f, 1.0f);
        KitUtil.actionBar(player, "Your Warden bodyguards rise.");
        com.reality.mobcraft.MobAdvancements.award(player, "bodyguards");
    }

    /** Bodyguards never turn on Jeff and never dig back underground. */
    private static void keepGuards(ServerPlayer player) {
        for (Mob guard : KitUtil.ownedMobs(player, 96, mob -> mob.getType() == EntityTypes.WARDEN)) {
            KitUtil.calm(guard, player);
            guard.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, 1200L);
        }
    }

    /** Shut down, raid mobs hit him harder. */
    @Override
    public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
        if (Jeff.isPowered(player) || source.getEntity() == null) return amount;
        return RAIDERS.contains(source.getEntity().getType()) ? amount * 1.5f : amount;
    }

    /** Shut down, emeralds only at a crafting table or job block. Powered up, he uses his menu. */
    /** Hit, he gets angry like a real villager: a little storm cloud over his head. */
    @Override
    public void afterHurt(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float damage) {
        if (damage <= 0 || source.getEntity() == null) return;
        float s = player.getScale();
        player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
                player.getX(), player.getY() + 2.0 * s, player.getZ(), 2, 0.25 * s, 0.1, 0.25 * s, 0.0);
    }

    /** Emeralds make him happy, like a real villager after a trade: green sparkles. */
    @Override
    public void customizeSelfGive(ServerPlayer player, String itemId, net.minecraft.world.item.ItemStack stack) {
        if (!stack.is(Items.EMERALD)) return;
        float s = player.getScale();
        player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.2 * s, player.getZ(), 8, 0.4 * s, 0.6 * s, 0.4 * s, 0.0);
    }

    @Override
    public boolean canSelfGive(ServerPlayer player, String itemId) {
        if (Jeff.isPowered(player)) return true;
        if (!nearJobBlock(player)) {
            KitUtil.actionBar(player, "You need a crafting table or a job block nearby.");
            return false;
        }
        return true;
    }

    private static boolean nearJobBlock(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-JOB_RANGE, -2, -JOB_RANGE),
                center.offset(JOB_RANGE, 2, JOB_RANGE))) {
            if (JOB_BLOCKS.contains(level.getBlockState(pos).getBlock())) return true;
        }
        return false;
    }

    /**
     * Whether Jeff's menu has this item. Everything in vanilla except:
     *   MobCraft's own items: bat ears, warden tendrils, shards, the Fracture
     *   Nether Star and the Curse Antidote.
     *   Anything a player mob gives itself, like iron ingots, ender pearls,
     *   totems or nether stars.
     *   Anything a player mob drops when it dies, like rabbit's feet, blaze
     *   powder or the dragon head.
     *   Spawn eggs, every one of them.
     * So Jeff can't hand out what other players earn from their mobs. Used on
     * both sides.
     */
    public static boolean inJeffsMenu(Identifier id) {
        if (id.getNamespace().equals(com.reality.mobcraft.MobCraft.MOD_ID)) return false;
        if (id.getPath().endsWith("_spawn_egg")) return false; // no spawn eggs, from any mod
        return !(id.getNamespace().equals("minecraft") && mobItems().contains(id.getPath()));
    }

    private static Set<String> mobItems;

    /** Every self-give item and death drop of every player mob, by vanilla id. */
    private static Set<String> mobItems() {
        if (mobItems == null) {
            Set<String> ids = new java.util.HashSet<>();
            for (MobType type : MobType.values()) {
                ids.addAll(com.reality.mobcraft.mob.MobItems.get(type));
                for (com.reality.mobcraft.mob.MobDrops.Drop drop : com.reality.mobcraft.mob.MobDrops.get(type)) {
                    ids.add(drop.itemId());
                }
            }
            mobItems = Set.copyOf(ids);
        }
        return mobItems;
    }

    /** Server only. An item from Jeff's menu, any vanilla item, up to a stack. */
    public static void giveFromMenu(ServerPlayer player, String itemId, int count) {
        if (!Jeff.isPowered(player)) {
            KitUtil.actionBar(player, "Your menu is shut down.");
            return;
        }
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        if (!inJeffsMenu(id)) {
            KitUtil.actionBar(player, "Jeff's menu doesn't have mob items or MobCraft items.");
            return;
        }
        Item item = BuiltInRegistries.ITEM.getValue(id);
        ItemStack stack = new ItemStack(item, 1);
        stack.setCount(Math.max(1, Math.min(count, stack.getMaxStackSize())));
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack));
        }
    }

    // --- Ultimate: Call the Guard ---------------------------------------------------------------
    /**
     * 3 iron golems come to defend you for 1:00 and take RTS orders, powered
     * up or not.
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Call the Guard";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        for (int i = 0; i < 3; i++) {
            double angle = Math.PI * 2 * i / 3;
            UltimateFx.summonFor(player, "minecraft:iron_golem", player.getX() + Math.cos(angle) * 2.5, player.getY(),
                    player.getZ() + Math.sin(angle) * 2.5, "PlayerCreated:1b", 1200);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.PLAYERS, 1.5f, 1.0f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.5f, 1.0f);
        KitUtil.actionBar(player, "The guard is here!");
        return true;
    }
}
