package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.kit.KitUtil;
import com.reality.mobcraft.net.UnseenHandPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unseen Hand, a chestplate enchant (I to III) and a Re:Zero reference.
 *
 * Sneak and right-click with an empty hand: shadow hands rise out of your back (two, four or six by level) and one
 * reaches out to whoever you're looking at, 8, 12 or 16 blocks away, and drags them to you. At II and III it then
 * lifts them off the ground and squeezes.
 *
 * Nobody can see the hands except the person using them and anyone who has absorbed a Shadow Shard. Everyone else
 * just sees the target get dragged and lifted by nothing. The drawing is client/ShadowHandRenderer.
 */
public final class UnseenHand {

    private UnseenHand() {}

    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT, MobCraft.id("unseen_hand"));

    /**
     * On someone using the hands: when it started, their level, and who's held (0 for nobody), packed together.
     * Everyone gets it; each client decides whether it can see the hands.
     */
    public static final AttachmentType<Long> HAND = AttachmentRegistry.create(MobCraft.id("unseen_hand"),
            b -> b.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

    public static final int DRAG = 20, HOLD = 45, MISS = 16, COOLDOWN = 20 * 15, MISS_COOLDOWN = 40;

    public static long pack(long start, int level, int targetId) {
        return start << 28 | (long) (level & 15) << 24 | ((targetId + 1) & 0xFFFFFF);
    }
    public static long start(long packed) { return packed >>> 28; }
    public static int level(long packed) { return (int) (packed >>> 24) & 15; }
    /** The held entity's id, or -1 when the hands caught nothing. */
    public static int target(long packed) { return (int) (packed & 0xFFFFFF) - 1; }

    public static int duration(int level, boolean caught) {
        if (!caught) return MISS;
        return level >= 2 ? DRAG + HOLD : DRAG + 8;
    }

    private record Grab(ServerPlayer by, LivingEntity target, long start, int level) {}
    private static final Map<UUID, Grab> GRABS = new HashMap<>();
    private static final Map<UUID, Long> READY = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(UnseenHandPayload.TYPE, UnseenHandPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UnseenHandPayload.TYPE, (payload, context) ->
                context.server().execute(() -> use(context.player())));
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    private static void use(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty()) return;
        int lv = MaceEnchants.level(p.getItemBySlot(EquipmentSlot.CHEST), KEY, level);
        if (lv <= 0 || GRABS.containsKey(p.getUUID())) return;
        long now = level.getGameTime();
        long ready = READY.getOrDefault(p.getUUID(), 0L);
        if (now < ready) {
            p.sendOverlayMessage(Component.literal("Unseen Hand is ready in " + ((ready - now) / 20 + 1) + "s").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        LivingEntity target = Shadow.lookTarget(p, 4 + 4 * Math.min(lv, 3));
        level.playSound(null, p.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.0f, 0.5f);
        level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.5f, 1.8f);
        if (target == null) {
            READY.put(p.getUUID(), now + MISS_COOLDOWN);
            p.setAttached(HAND, pack(now, lv, -1));
            GRABS.put(p.getUUID(), new Grab(p, null, now, lv));
            return;
        }
        READY.put(p.getUUID(), now + COOLDOWN);
        p.setAttached(HAND, pack(now, lv, target.getId()));
        GRABS.put(p.getUUID(), new Grab(p, target, now, lv));
    }

    private static void tick() {
        GRABS.values().removeIf(g -> {
            ServerPlayer p = g.by();
            if (!(p.level() instanceof ServerLevel level) || p.isRemoved() || !p.isAlive()) return end(g);
            long age = level.getGameTime() - g.start();
            LivingEntity t = g.target();
            if (age >= duration(g.level(), t != null)) return end(g);
            if (t == null) return false;
            if (!t.isAlive() || t.level() != p.level() || t.distanceTo(p) > 24) return end(g);
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize();
            // Drag them to just in front of you; at II and III, lift them up and squeeze.
            boolean lifting = g.level() >= 2 && age >= DRAG;
            Vec3 hold = p.position().add(flat.scale(2.2)).add(0, lifting ? 1.6 : 0.2, 0);
            Vec3 pull = hold.subtract(t.position());
            double max = lifting ? 0.5 : 1.3;
            Vec3 v = pull.scale(0.35);
            if (v.length() > max) v = v.normalize().scale(max);
            t.setDeltaMovement(v);
            t.fallDistance = 0;
            KitUtil.syncMotion(t);
            if (lifting && age % 10 == 0) {
                float dmg = g.level() >= 3 ? 2.5f : 1.5f;
                t.setInvulnerableTime(0);
                t.hurtServer(level, level.damageSources().indirectMagic(p, p), dmg);
                level.playSound(null, t.blockPosition(), SoundEvents.SCULK_BLOCK_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
            }
            return false;
        });
    }

    private static boolean end(Grab g) {
        g.by().removeAttached(HAND);
        return true;
    }
}
