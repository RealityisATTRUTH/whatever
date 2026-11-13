package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mixin.ServerPlayerGameModeAccessor;
import com.reality.mobcraft.mob.kit.Cooldowns;
import com.reality.mobcraft.mob.kit.KitUtil;
import com.reality.mobcraft.net.HushResetPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Unseenhush, a Dr. Stone reference: the hush of the world going silent.
 *
 * A single block of ore deep in the deepslate, in about 1 chunk out of 1000. It's shy: look straight at it for a
 * second and it slips into a nearby deepslate block. It can't notice you while you wear a carved pumpkin or stand in
 * Darkness. It takes a very long time to mine, and any sound within 16 blocks (a mob, a footstep, a door) undoes the
 * mining, so you have to make the world around it silent first.
 *
 * It drops The Hush. In theory the Hush is what a Medusa would be made of, but nobody was ever advanced enough to
 * build one. What you can make is a helmet: put the Hush on one, and the Armor Power key silences the player you look
 * at, so they can't hear anything for 15 seconds.
 *
 * DEV ITEM: all of this is coded but switched off. It doesn't generate, isn't in the creative tab, and does nothing
 * until a moderator runs /mobcraftdev unseenhush on (which lasts until the server restarts). It waits on the
 * players' vote before it's released. To release it, set ENABLED_BY_DEFAULT and turn the worldgen line back on.
 */
public final class Hush {

    private Hush() {}

    public static final String UPGRADE = "hush";

    /** Off until the players vote it in. */
    public static final boolean ENABLED_BY_DEFAULT = false;
    private static boolean enabled = ENABLED_BY_DEFAULT;

    public static boolean enabled() { return enabled; }

    public static final int STARE_TICKS = 20;          // a second of looking and it slips away
    public static final double STARE_RANGE = 24;
    public static final int SLIP_RADIUS = 3;
    public static final double NOISE_RANGE = 16;
    public static final int SILENCE_TICKS = 20 * 15;   // the helmet
    public static final int SILENCE_COOLDOWN = 20 * 120;
    public static final double SILENCE_RANGE = 16;
    public static final int BREAK_SILENCE_TICKS = 20 * 5;
    public static final double BREAK_SILENCE_RANGE = 48;
    private static final String COOLDOWN_KEY = "hush_silence";

    // --- Registration --------------------------------------------------------------------------

    private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, MobCraft.id("hush"));
    public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY, new Item(new Item.Properties()
            .setId(ITEM_KEY).rarity(Rarity.EPIC).stacksTo(16)
            .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            .component(DataComponents.LORE, new ItemLore(List.of(
                    line("The hush of the world going silent", ChatFormatting.GREEN),
                    line("In theory, the stuff a Medusa is made of.", ChatFormatting.GRAY),
                    line("Nobody was ever advanced enough to build one.", ChatFormatting.GRAY),
                    line("Craft it with a helmet to silence others", ChatFormatting.DARK_GREEN),
                    line("Dev item: not turned on yet", ChatFormatting.DARK_GRAY))))));

    public static final ResourceKey<Block> ORE_KEY = ResourceKey.create(Registries.BLOCK, MobCraft.id("unseenhush"));
    /** Hardness 600: about 18 seconds with Efficiency V netherite and Haste II, 26 without Haste. Blast proof. */
    public static final Block ORE = Registry.register(BuiltInRegistries.BLOCK, ORE_KEY, new Block(BlockBehaviour.Properties.of()
            .setId(ORE_KEY).mapColor(MapColor.DEEPSLATE).requiresCorrectToolForDrops()
            .strength(600.0f, 3600000.0f).sound(SoundType.DEEPSLATE)));
    private static final ResourceKey<Item> ORE_ITEM_KEY = ResourceKey.create(Registries.ITEM, MobCraft.id("unseenhush"));
    public static final Item ORE_ITEM = Registry.register(BuiltInRegistries.ITEM, ORE_ITEM_KEY,
            new BlockItem(ORE, new Item.Properties().setId(ORE_ITEM_KEY).rarity(Rarity.EPIC).useBlockDescriptionPrefix()));

    private static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(Registries.PLACED_FEATURE, MobCraft.id("unseenhush"));

    /** On a player: the game time their silence ends. Only their own client needs it. */
    public static final AttachmentType<Long> HUSHED = AttachmentRegistry.create(MobCraft.id("hushed"),
            b -> b.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withColor(color).withItalic(false));
    }

    public static void register() {
        // Worldgen stays off while it's a dev item. On release, turn this back on:
        if (ENABLED_BY_DEFAULT) net.fabricmc.fabric.api.biome.v1.BiomeModifications.addFeature(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.foundInOverworld(),
                net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES, PLACED);
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("mobcraftdev")
                        .requires(src -> src.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                        .then(net.minecraft.commands.Commands.literal("unseenhush")
                                .executes(ctx -> devStatus(ctx.getSource()))
                                .then(net.minecraft.commands.Commands.literal("on").executes(ctx -> devSet(ctx.getSource(), true)))
                                .then(net.minecraft.commands.Commands.literal("off").executes(ctx -> devSet(ctx.getSource(), false))))));
        PayloadTypeRegistry.clientboundPlay().register(HushResetPayload.TYPE, HushResetPayload.CODEC);
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (enabled && state.is(ORE) && level instanceof ServerLevel server && player instanceof ServerPlayer sp) mined(server, sp, pos);
        });
        ServerTickEvents.END_SERVER_TICK.register(Hush::tick);
    }

    private static int devStatus(net.minecraft.commands.CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("The Unseenhush is " + (enabled ? "on" : "off") + " (dev item)"), false);
        return enabled ? 1 : 0;
    }

    private static int devSet(net.minecraft.commands.CommandSourceStack src, boolean on) {
        enabled = on;
        src.sendSuccess(() -> Component.literal("The Unseenhush is now " + (on ? "on until the server restarts. It still won't generate in new chunks; use /give or /setblock to test it." : "off")), true);
        return 1;
    }

    // --- The shy ore ---------------------------------------------------------------------------

    /** Who's looking at which Unseenhush, and for how long. */
    private record Stare(BlockPos pos, int ticks) {}
    private static final Map<UUID, Stare> STARES = new HashMap<>();

    /** A carved pumpkin on your head, or Darkness, and it can't tell you're there. */
    public static boolean unnoticed(Player p) {
        return p.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN) || p.hasEffect(MobEffects.DARKNESS);
    }

    private static void tick(MinecraftServer server) {
        if (!enabled) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator() || unnoticed(p)) { STARES.remove(p.getUUID()); continue; }
            HitResult hit = p.pick(STARE_RANGE, 1.0f, false);
            if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK
                    || !p.level().getBlockState(bh.getBlockPos()).is(ORE)) { STARES.remove(p.getUUID()); continue; }
            BlockPos pos = bh.getBlockPos();
            Stare s = STARES.get(p.getUUID());
            int ticks = s != null && s.pos().equals(pos) ? s.ticks() + 1 : 1;
            if (ticks >= STARE_TICKS) {
                STARES.remove(p.getUUID());
                slip(p.level(), pos);
            } else STARES.put(p.getUUID(), new Stare(pos, ticks));
        }
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Long until = p.getAttached(HUSHED);
                if (until != null && p.level().getGameTime() >= until) p.removeAttached(HUSHED);
            }
        }
    }

    /** Seen: it trades places with a deepslate block close by. */
    public static void slip(ServerLevel level, BlockPos from) {
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(from.offset(-SLIP_RADIUS, -SLIP_RADIUS, -SLIP_RADIUS),
                from.offset(SLIP_RADIUS, SLIP_RADIUS, SLIP_RADIUS))) {
            if (!q.equals(from) && level.getBlockState(q).is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) spots.add(q.immutable());
        }
        if (spots.isEmpty()) return;
        BlockPos to = spots.get(level.getRandom().nextInt(spots.size()));
        BlockState there = level.getBlockState(to);
        level.setBlock(to, ORE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(from, there, Block.UPDATE_ALL);
        quiet(() -> {
            level.playSound(null, from, SoundEvents.SCULK_CLICKING_STOP, SoundSource.BLOCKS, 0.6f, 0.5f);
            level.playSound(null, to, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.25f, 0.5f);
        });
        level.sendParticles(new DustParticleOptions(0x9BE59B, 0.8f), from.getX() + 0.5, from.getY() + 0.5, from.getZ() + 0.5,
                10, 0.3, 0.3, 0.3, 0);
    }

    // --- Silence to mine -----------------------------------------------------------------------

    private static boolean quiet;

    /** Plays our own sounds without them counting as noise. */
    private static void quiet(Runnable r) {
        quiet = true;
        try { r.run(); } finally { quiet = false; }
    }

    /**
     * A sound was played somewhere (ServerLevelSoundMixin). Anyone mining an Unseenhush within 16 blocks of it loses
     * their progress, unless they made the sound themselves.
     */
    public static void noise(ServerLevel level, double x, double y, double z, Entity except) {
        if (quiet || !enabled) return;
        for (ServerPlayer p : level.players()) {
            if (p == except) continue;
            var gm = (ServerPlayerGameModeAccessor) p.gameMode;
            BlockPos pos = gm.mobcraft$isDestroying() ? gm.mobcraft$destroyPos()
                    : gm.mobcraft$hasDelayedDestroy() ? gm.mobcraft$delayedDestroyPos() : null;
            if (pos == null || !level.getBlockState(pos).is(ORE)) continue;
            if (pos.distToCenterSqr(x, y, z) > NOISE_RANGE * NOISE_RANGE) continue;
            int now = gm.mobcraft$gameTicks();
            gm.mobcraft$setDestroyProgressStart(now);
            gm.mobcraft$setDelayedTickStart(now);
            ServerPlayNetworking.send(p, new HushResetPayload(pos));
            KitUtil.actionBar(p,"A sound broke the hush. It has to be silent.");
        }
    }

    /** Mined at last: the world goes silent around it for a few seconds. */
    private static void mined(ServerLevel level, ServerPlayer miner, BlockPos pos) {
        long until = level.getGameTime() + BREAK_SILENCE_TICKS;
        for (ServerPlayer p : level.players()) {
            if (pos.distToCenterSqr(p.getX(), p.getY(), p.getZ()) > BREAK_SILENCE_RANGE * BREAK_SILENCE_RANGE) continue;
            p.setAttached(HUSHED, until);
            p.sendOverlayMessage(Component.literal("The world went silent").withStyle(ChatFormatting.GREEN));
        }
        level.sendParticles(new DustParticleOptions(0x9BE59B, 1.2f), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                40, 0.6, 0.6, 0.6, 0);
        miner.sendSystemMessage(Component.literal("Ten billion percent. Now we rebuild civilization.").withStyle(ChatFormatting.GREEN));
    }

    // --- The helmet ----------------------------------------------------------------------------

    /** Armor Power key with the Hush on your helmet: the player you look at can't hear a thing for 15 seconds. */
    public static void silence(ServerPlayer p) {
        if (!enabled) {
            KitUtil.actionBar(p, "The Hush is still in development.");
            return;
        }
        int left = Cooldowns.remaining(p, COOLDOWN_KEY);
        if (left > 0) {
            KitUtil.actionBar(p, "The Hush is ready in " + KitUtil.seconds(left) + ".");
            return;
        }
        LivingEntity target = Shadow.lookTarget(p, SILENCE_RANGE);
        if (!(target instanceof ServerPlayer tp)) {
            KitUtil.actionBar(p, "Look at a player to silence them.");
            return;
        }
        Cooldowns.start(p, COOLDOWN_KEY, SILENCE_COOLDOWN);
        ServerLevel level = p.level();
        tp.setAttached(HUSHED, level.getGameTime() + SILENCE_TICKS);
        tp.sendOverlayMessage(Component.literal("The world has gone silent").withStyle(ChatFormatting.GREEN));
        KitUtil.actionBar(p, "You silenced " + tp.getName().getString() + ".");
        level.sendParticles(new DustParticleOptions(0x9BE59B, 1.0f), tp.getX(), tp.getEyeY(), tp.getZ(), 16, 0.3, 0.2, 0.3, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, tp.getX(), tp.getEyeY(), tp.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
    }

    /** Whether this player is silenced right now. */
    public static boolean hushed(Player p) {
        Long until = p.getAttached(HUSHED);
        return until != null && p.level().getGameTime() < until;
    }
}
