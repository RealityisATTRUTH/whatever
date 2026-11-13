package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Stronghold Tome's powers. Sneak + right-click turns to the next page (the next power); hold right-click to
 * read it, and once the book is open the power goes off at whatever you're looking at.
 *
 *  0 Binding Word     lavender chains lock the target's legs (TomeBinding)
 *  1 Word of Silence  a band of glowing runes wraps the target's mouth, crossed with chains, under a turning rune
 *                     halo, and their mob abilities stop working for 10 seconds; a mob is left weak and slow instead
 *  2 Unwriting        the words peel off the target's gear and dissolve: their worn and held enchantments stop
 *                     working for 15 seconds, then the words fly back and write themselves in again
 *  3 Page Storm       burning pages spray out of the book, hurting and setting fire to what's in front of you
 */
public final class TomePowers {

    public static final String[] NAMES = {"Binding Word", "Word of Silence", "Unwriting", "Page Storm"};
    public static final String[] HOW = {
            "Read it at someone to chain their legs down",
            "Read it at someone to silence their mob abilities",
            "Read it at someone to erase their enchantments for a while",
            "Read it to spray burning pages in front of you"};
    private static final int[] COOLDOWN = {0, 600, 800, 200};   // Binding Word keeps its own
    private static final double RANGE = 16;

    /** Which page the Tome is turned to. */
    public static final DataComponentType<Integer> POWER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            MobCraft.id("tome_power"), DataComponentType.<Integer>builder().persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());

    /** An item whose enchantments were unwritten: what it had, and the game time they come back. */
    public record Unwritten(ItemEnchantments enchants, long until) {
        static final com.mojang.serialization.Codec<Unwritten> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                ItemEnchantments.CODEC.fieldOf("enchants").forGetter(Unwritten::enchants),
                com.mojang.serialization.Codec.LONG.fieldOf("until").forGetter(Unwritten::until)).apply(i, Unwritten::new));
    }
    public static final DataComponentType<Unwritten> UNWRITTEN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            MobCraft.id("unwritten"), DataComponentType.<Unwritten>builder().persistent(Unwritten.CODEC).build());

    /** On a silenced player or mob: the game time it ends. Everyone sees it, so the seal shows (TomeWordRenderer). */
    public static final AttachmentType<Long> SILENCED = AttachmentRegistry.create(MobCraft.id("tome_silenced"),
            b -> b.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));
    /** On someone whose enchantments were unwritten: the game time they come back. Everyone sees it. */
    public static final AttachmentType<Long> UNWRITING = AttachmentRegistry.create(MobCraft.id("tome_unwriting"),
            b -> b.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));
    public static final int SILENCE_TICKS = 200, UNWRITE_TICKS = 300;

    private static final Map<UUID, int[]> READY_AT = new HashMap<>();

    private TomePowers() {}

    public static int power(ItemStack tome) {
        return Math.floorMod(tome.getOrDefault(POWER, 0), NAMES.length);
    }

    public static boolean silenced(Player player) {
        Long until = player.getAttached(SILENCED);
        return until != null && until > player.level().getGameTime();
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTickCount();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.hasAttached(SILENCED)) {
                    if (!silenced(p)) {
                        p.removeAttached(SILENCED);
                        KitUtil.actionBar(p, "Your voice comes back. Your abilities work again.");
                    }
                }
                if (tick % 20 == 0) restoreAll(p);
            }
        });
    }

    /** Sneak + right-click: turn to the next page. */
    public static void nextPage(ServerPlayer player, ItemStack tome) {
        int next = (power(tome) + 1) % NAMES.length;
        tome.set(POWER, next);
        tome.set(DataComponents.LORE, StrongholdTome.lore(StrongholdTome.burns(tome), next));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.3f);
        player.sendSystemMessage(Component.literal(NAMES[next]).withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("  " + HOW[next]).withStyle(ChatFormatting.GRAY)), true);
    }

    /** Called every other tick while the book is open. Fires the current page's power once it's ready. */
    static void read(ServerPlayer reader, ItemStack tome) {
        int power = power(tome);
        if (power == 0) { TomeBinding.tryBind(reader); return; }
        int now = reader.level().getServer().getTickCount();
        int[] ready = READY_AT.computeIfAbsent(reader.getUUID(), k -> new int[NAMES.length]);
        if (ready[power] > now) return;
        boolean fired = switch (power) {
            case 1 -> silence(reader);
            case 2 -> unwrite(reader);
            case 3 -> pageStorm(reader);
            default -> false;
        };
        if (fired) ready[power] = now + COOLDOWN[power];
    }

    private static LivingEntity aimed(ServerPlayer reader) {
        Vec3 eye = reader.getEyePosition(), end = eye.add(reader.getLookAngle().scale(RANGE));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(reader.level(), reader, eye, end, new AABB(eye, end).inflate(1),
                e -> e instanceof LivingEntity l && l.isAlive() && !l.isSpectator(), 0f);
        return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
    }

    // ---- Word of Silence ----

    private static boolean silence(ServerPlayer reader) {
        LivingEntity target = aimed(reader);
        if (target == null) return false;
        applySilence(target, reader);
        KitUtil.actionBar(reader, "Word of Silence: " + target.getName().getString() + " is silenced.");
        return true;
    }

    /** Seals the target's mouth with runes. A player's mob abilities stop; a mob is left weak and slow. */
    public static void applySilence(LivingEntity target, LivingEntity reader) {
        ServerLevel level = (ServerLevel) target.level();
        target.setAttached(SILENCED, level.getGameTime() + SILENCE_TICKS);
        if (target instanceof ServerPlayer p) {
            KitUtil.actionBar(p, "Word of Silence: your mob abilities won't work for 10 seconds.");
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SILENCE_TICKS, 2), reader);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SILENCE_TICKS, 1), reader);
            MaceEnchants.later(level, SILENCE_TICKS, () -> target.removeAttached(SILENCED));
        }
        level.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, target.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    // ---- Unwriting ----

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private static boolean unwrite(ServerPlayer reader) {
        LivingEntity target = aimed(reader);
        if (target == null) return false;
        int erased = applyUnwrite(target);
        KitUtil.actionBar(reader, erased == 0 ? "Unwriting: " + target.getName().getString() + " had nothing to erase."
                : "Unwriting: erased the enchantments on " + erased + " of " + target.getName().getString() + "'s items.");
        return true;
    }

    /** Erases the enchantments on what the target wears and holds for a while. Returns how many items it erased. */
    public static int applyUnwrite(LivingEntity target) {
        ServerLevel level = (ServerLevel) target.level();
        long until = level.getGameTime() + UNWRITE_TICKS;
        int erased = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty() || stack.has(UNWRITTEN)) continue;
            ItemEnchantments e = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (e.isEmpty()) continue;
            stack.set(UNWRITTEN, new Unwritten(e, until));
            stack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            erased++;
        }
        level.playSound(null, target.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, target.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.4f);
        if (erased == 0) return 0;
        // The words peel off their gear and dissolve, and fly back when the time is up (TomeWordRenderer).
        target.setAttached(UNWRITING, until);
        MaceEnchants.later(level, UNWRITE_TICKS + 2, () -> {
            target.removeAttached(UNWRITING);
            if (target instanceof net.minecraft.world.entity.Mob mob) for (EquipmentSlot s : SLOTS) restore(mob.getItemBySlot(s), level.getGameTime());
        });
        if (target instanceof ServerPlayer p) KitUtil.actionBar(p, "Unwriting: your enchantments are gone for 15 seconds.");
        return erased;
    }

    /** Puts unwritten enchantments back once their time is up. Checks every item a player carries each second. */
    private static void restoreAll(ServerPlayer p) {
        long now = p.level().getGameTime();
        boolean any = false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) any |= restore(p.getInventory().getItem(i), now);
        if (any) {
            p.level().playSound(null, p.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
            KitUtil.actionBar(p, "Your enchantments wrote themselves back.");
        }
    }

    private static boolean restore(ItemStack stack, long now) {
        Unwritten u = stack.get(UNWRITTEN);
        if (u == null || u.until() > now) return false;
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(u.enchants());
        for (var entry : current.entrySet()) merged.upgrade(entry.getKey(), entry.getIntValue());
        stack.set(DataComponents.ENCHANTMENTS, merged.toImmutable());
        stack.remove(UNWRITTEN);
        return true;
    }

    // ---- Page Storm ----

    private static boolean pageStorm(ServerPlayer reader) {
        ServerLevel level = (ServerLevel) reader.level();
        for (int volley = 0; volley < 3; volley++) {
            MaceEnchants.later(level, volley * 4, () -> {
                if (!reader.isAlive()) return;
                Vec3 eye = reader.getEyePosition(), look = reader.getLookAngle();
                for (int i = 0; i < 6; i++) flyingPage(level, reader, eye, look);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reader.getBoundingBox().inflate(10),
                        e -> e != reader && e.isAlive() && !e.isAlliedTo(reader))) {
                    Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
                    if (to.length() > 10 || to.normalize().dot(look) < Math.cos(Math.toRadians(30)) || !reader.hasLineOfSight(e)) continue;
                    e.setInvulnerableTime(0);
                    e.hurtServer(level, level.damageSources().indirectMagic(reader, reader), 2.5f);
                    e.igniteForSeconds(4);
                }
                level.playSound(null, reader.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2f, 1.4f);
                level.playSound(null, reader.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 1.5f);
            });
        }
        KitUtil.actionBar(reader, "Page Storm!");
        return true;
    }

    /** A burning page fluttering out of the book: a paper display that flies forward, trailing flame. */
    private static void flyingPage(ServerLevel level, ServerPlayer reader, Vec3 eye, Vec3 look) {
        var rng = level.getRandom();
        Vec3 dir = look.add((rng.nextDouble() - 0.5) * 0.45, (rng.nextDouble() - 0.5) * 0.3, (rng.nextDouble() - 0.5) * 0.45).normalize();
        Vec3 start = eye.add(look.scale(0.7)).add(0, -0.3, 0);
        Display.ItemDisplay page = EntityTypes.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (page == null) return;
        page.setItemStack(new ItemStack(Items.PAPER));
        page.setItemTransform(ItemDisplayContext.FIXED);
        page.setPos(start.x, start.y, start.z);
        page.setPosRotInterpolationDuration(2);
        page.setYRot(rng.nextFloat() * 360f);
        page.setXRot(rng.nextFloat() * 180f - 90f);
        level.addFreshEntity(page);
        double speed = 0.7 + rng.nextDouble() * 0.3;
        for (int t = 1; t <= 14; t++) {
            int tt = t;
            MaceEnchants.later(level, t, () -> {
                Vec3 p = start.add(dir.scale(speed * tt)).add(0, Math.sin(tt * 0.9) * 0.08, 0);
                page.setPos(p.x, p.y, p.z);
                page.setYRot(page.getYRot() + 25f);
                level.sendParticles(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.005);
                if (tt % 3 == 0) level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
            });
        }
        MaceEnchants.later(level, 15, () -> {
            level.sendParticles(ParticleTypes.FLAME, page.getX(), page.getY(), page.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
            page.discard();
        });
    }
}
