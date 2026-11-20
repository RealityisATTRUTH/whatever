package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The Stronghold Tome's first power, Binding Word. Read the open book at a player or mob within 16 blocks and the
 * enchanting letters pour onto them and form lavender chains round their legs, pinning them to the ground
 * (client/TomeChainRenderer). They can't walk or jump. To break free, look down at your feet and hit the chains:
 * 3 to 7 hits, different every time. Mobs can't do that, so theirs crumble on their own after a few seconds.
 * The book needs 30 seconds before it can bind again.
 *
 * The Unbinding Severance, on a sword or axe, cuts them faster: each hit counts as 2 at I, 3 at II and 4 at III.
 */
public final class TomeBinding {

    /** On a bound entity: hits still needed, and the total, packed as total * 16 + left. Everyone sees it. */
    public static final AttachmentType<Integer> BOUND = AttachmentRegistry.create(MobCraft.id("tome_bound"),
            b -> b.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    private static final double RANGE = 16;
    private static final int COOLDOWN = 600;          // 30 seconds
    private static final int MOB_TICKS = 140;         // mobs break out on their own after 7 seconds
    private static final int MAX_TICKS = 400;         // nobody stays chained more than 20 seconds
    private static final float LOOK_DOWN = 40f;       // degrees below level to aim at your feet
    private static final String STILL = "tome_bound_still";
    private static final String NO_JUMP = "tome_bound_jump";
    public static final DustParticleOptions LAVENDER = new DustParticleOptions(0xC9A7F5, 1.0f);

    private static final Map<UUID, Integer> READY_AT = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HIT = new HashMap<>();
    public static final float LOOK_DOWN_ANGLE = LOOK_DOWN;
    /** Every bound entity, and the server tick it was bound on. */
    private static final Map<Entity, Integer> BOUND_SINCE = new HashMap<>();

    private TomeBinding() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(TomeBinding::tick);
        // Breaking free: the bound player's game sends a hit when they look down and attack (client/TomeChainHits),
        // instead of starting to break the block. The server checks they really are bound and looking down.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(
                com.reality.mobcraft.net.TomeChainHitPayload.TYPE, com.reality.mobcraft.net.TomeChainHitPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                com.reality.mobcraft.net.TomeChainHitPayload.TYPE, (payload, context) -> {
                    ServerPlayer player = context.player();
                    if (!isBound(player) || player.getXRot() < LOOK_DOWN - 10) return;
                    int now = player.level().getServer().getTickCount();
                    if (LAST_HIT.getOrDefault(player.getUUID(), -100) > now - 5) return;   // no faster than 4 a second
                    LAST_HIT.put(player.getUUID(), now);
                    hit(player);
                });
        // And if a bound player's attack reaches the server some other way, it never breaks the block under them.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                !level.isClientSide() && isBound(player) && player.getXRot() >= LOOK_DOWN ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    public static boolean isBound(Entity entity) {
        return entity.hasAttached(BOUND);
    }

    /** Called while the reader holds the book open. Binds whatever they're looking at, if the book is ready. */
    static void tryBind(ServerPlayer reader) {
        int now = reader.level().getServer().getTickCount();
        if (READY_AT.getOrDefault(reader.getUUID(), 0) > now) return;
        Vec3 eye = reader.getEyePosition();
        Vec3 end = eye.add(reader.getLookAngle().scale(RANGE));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(reader.level(), reader, eye, end,
                new AABB(eye, end).inflate(1), e -> e instanceof LivingEntity l && l.isAlive() && !isBound(e), 0f);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) return;
        READY_AT.put(reader.getUUID(), now + COOLDOWN);
        bind(target, now);
        KitUtil.actionBar(reader, "Binding Word: " + target.getName().getString() + " is chained down.");
    }

    /** /tomebind: chains them without a book or cooldown. */
    public static boolean bindForTest(LivingEntity target) {
        if (isBound(target) || target.level().getServer() == null) return false;
        bind(target, target.level().getServer().getTickCount());
        return true;
    }

    private static void bind(LivingEntity target, int now) {
        int total = 3 + target.getRandom().nextInt(5);   // 3 to 7 hits
        target.setAttached(BOUND, total * 16 + total);
        BOUND_SINCE.put(target, now);
        hold(target, true);
        ServerLevel level = (ServerLevel) target.level();
        level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + 1.6, target.getZ(), 60, 0.2, 0.3, 0.2, 1.2);
        level.playSound(null, target.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, target.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.6f);
        if (target instanceof ServerPlayer p) {
            p.sendSystemMessage(Component.literal("Chains of letters bind your legs. Look down and hit them to break free.")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** How many hits one swing counts for. Later, a new sword and axe enchant will raise this. */
    static int hitsPerSwing(Player player) {
        int level = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .get(SEVERANCE).map(h -> net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(h, player.getMainHandItem()))
                .orElse(0);
        return 1 + level;
    }

    /** The Unbinding Severance: a sword and axe enchant that cuts Binding Word chains faster. */
    public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> SEVERANCE =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, MobCraft.id("unbinding_severance"));

    private static void hit(ServerPlayer player) {
        Integer packed = player.getAttached(BOUND);
        if (packed == null) return;
        int total = packed >> 4, left = (packed & 15) - hitsPerSwing(player);
        ServerLevel level = player.level();
        level.sendParticles(LAVENDER, player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.3, 0.15, 0.3, 0.02);
        if (left <= 0) {
            free(player, true);
            return;
        }
        player.setAttached(BOUND, total * 16 + left);
        level.playSound(null, player.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0f, 0.8f + 0.1f * (total - left));
        KitUtil.actionBar(player, "The chains crack. " + left + (left == 1 ? " more hit." : " more hits."));
    }

    private static void free(LivingEntity entity, boolean broken) {
        entity.removeAttached(BOUND);
        BOUND_SINCE.remove(entity);
        hold(entity, false);
        ServerLevel level = (ServerLevel) entity.level();
        level.sendParticles(LAVENDER, entity.getX(), entity.getY() + 0.4, entity.getZ(), 30, 0.4, 0.3, 0.4, 0.05);
        level.sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + 0.4, entity.getZ(), 30, 0.5, 0.3, 0.5, 0.4);
        level.playSound(null, entity.blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.2f, 1.0f);
        if (broken && entity instanceof ServerPlayer p) KitUtil.actionBar(p, "You broke free!");
    }

    private static void hold(LivingEntity entity, boolean on) {
        KitUtil.modifier(entity, Attributes.MOVEMENT_SPEED, STILL, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
        KitUtil.modifier(entity, Attributes.JUMP_STRENGTH, NO_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
    }

    private static void tick(MinecraftServer server) {
        if (BOUND_SINCE.isEmpty()) return;
        int now = server.getTickCount();
        Iterator<Map.Entry<Entity, Integer>> it = BOUND_SINCE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Integer> e = it.next();
            Entity entity = e.getKey();
            if (!entity.isAlive() || entity.isRemoved() || !entity.hasAttached(BOUND)) {
                if (entity instanceof LivingEntity l) { l.removeAttached(BOUND); hold(l, false); }
                it.remove();
                continue;
            }
            int held = now - e.getValue();
            if (held > MAX_TICKS || (!(entity instanceof Player) && held > MOB_TICKS)) {
                it.remove();
                free((LivingEntity) entity, false);
                continue;
            }
            // Pinned: no sliding, no being pushed along, and mobs stop walking.
            Vec3 v = entity.getDeltaMovement();
            if (v.x != 0 || v.z != 0 || v.y > 0) {
                entity.setDeltaMovement(0, Math.min(0, v.y), 0);
                KitUtil.syncMotion(entity);
            }
            if (entity instanceof Mob mob) mob.getNavigation().stop();
            if (held % 10 == 0) {
                ((ServerLevel) entity.level()).sendParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + 0.3,
                        entity.getZ(), 3, 0.3, 0.1, 0.3, 0.3);
            }
        }
    }
}
