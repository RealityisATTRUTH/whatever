package com.reality.mobcraft.item;

import com.mojang.serialization.Codec;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.mob.kit.DeathOnce;
import com.reality.mobcraft.net.ShadowCrushPayload;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Witch's Shadow, a Re:Zero reference.
 *
 * Kill a normal player (one who isn't playing as a mob) and they drop a Shadow Essence. Combine it with an amethyst
 * shard to make a Shadow Shard, and right-click the shard to absorb it. Shadow sits in its own slot, so it works
 * alongside a mob essence and doesn't replace it.
 *
 * With Shadow you can see Unseen Hands (other people's and your own), which nobody else can. And you get Shadow
 * Crush, on its own key: a shadowy hand reaches into whoever you're looking at, closes round their heart and slowly
 * squeezes until they're down to two hearts. It never kills. Everyone can see that one.
 */
public final class Shadow {

    private Shadow() {}

    private static Item item(String name, Rarity rarity, boolean glint, String... lore) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MobCraft.id(name));
        List<Component> lines = new java.util.ArrayList<>();
        for (String l : lore) lines.add(Component.literal(l).withStyle(ChatFormatting.DARK_PURPLE));
        Item.Properties props = new Item.Properties().setId(key).rarity(rarity).component(DataComponents.LORE, new ItemLore(lines));
        if (glint) props = props.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(props));
    }

    public static final Item ESSENCE = item("shadow_essence", Rarity.RARE, false,
            "Left behind by a player who fell to another", "Combine it with an amethyst shard");
    public static final Item SHARD = item("shadow_shard", Rarity.EPIC, true,
            "The Witch's Shadow, sealed in amethyst", "Right-click to absorb it", "Lets you see Unseen Hands and use Shadow Crush");

    /** On a player: they've absorbed a Shadow Shard. Saved, kept through death, and only their own client knows. */
    public static final AttachmentType<Boolean> SHADOW = AttachmentRegistry.create(MobCraft.id("shadow"),
            b -> b.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

    /** On someone being crushed: the game time it started. Everyone sees it, so the hand and heart show. */
    public static final AttachmentType<Long> CRUSH = AttachmentRegistry.create(MobCraft.id("shadow_crush"),
            b -> b.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

    public static final int CRUSH_TICKS = 100, PULSES = 10, COOLDOWN = 20 * 180, RANGE = 16;
    public static final float FLOOR = 4f;   // two hearts

    private record Crushing(LivingEntity target, ServerPlayer by, long start) {}
    private static final Map<Integer, Crushing> CRUSHING = new HashMap<>();
    private static final Map<UUID, Long> READY = new HashMap<>();

    public static boolean has(Player p) {
        return Boolean.TRUE.equals(p.getAttached(SHADOW));
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ShadowCrushPayload.TYPE, ShadowCrushPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ShadowCrushPayload.TYPE, (payload, context) ->
                context.server().execute(() -> crush(context.player())));

        // A normal player killed by another player drops a Shadow Essence, once per death.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer victim) || PlayerMob.get(victim) != null) return;
            if (!(source.getEntity() instanceof ServerPlayer killer) || killer == victim) return;
            if (!DeathOnce.first(victim, "shadow_essence")) return;
            ArmorUpgrades.dropAt(victim, ESSENCE);
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.is(SHARD)) return InteractionResult.PASS;
            if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            if (has(sp)) {
                sp.sendOverlayMessage(Component.literal("You already carry the Witch's Shadow").withStyle(ChatFormatting.DARK_PURPLE));
                return InteractionResult.FAIL;
            }
            sp.setAttached(SHADOW, true);
            if (!sp.hasInfiniteMaterials()) stack.shrink(1);
            server.sendParticles(new DustParticleOptions(0x6A2FB0, 1.4f), sp.getX(), sp.getY() + 1, sp.getZ(), 40, 0.4, 0.7, 0.4, 0);
            server.sendParticles(ParticleTypes.SCULK_SOUL, sp.getX(), sp.getY() + 1, sp.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            server.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.6f);
            server.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.5f);
            sp.sendOverlayMessage(Component.literal("The Witch's Shadow is with you now").withStyle(ChatFormatting.DARK_PURPLE));
            return InteractionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    /** The living thing this player is looking straight at, within range and not behind a wall. */
    public static LivingEntity lookTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        HitResult block = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.5),
                e -> e != p && e.isAlive() && !e.isSpectator())) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.4).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = hit.get().distanceToSqr(eye);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    private static void crush(ServerPlayer p) {
        if (!has(p) || !(p.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        long ready = READY.getOrDefault(p.getUUID(), 0L);
        if (now < ready) {
            p.sendOverlayMessage(Component.literal("Shadow Crush is ready in " + ((ready - now) / 20 + 1) + "s").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        LivingEntity target = lookTarget(p, RANGE);
        if (target == null) {
            p.sendOverlayMessage(Component.literal("No one to reach").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        if (CRUSHING.containsKey(target.getId())) return;
        READY.put(p.getUUID(), now + COOLDOWN);
        CRUSHING.put(target.getId(), new Crushing(target, p, now));
        target.setAttached(CRUSH, now);
        level.playSound(null, target.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.7f);
        level.playSound(null, p.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.6f, 0.5f);
        if (target instanceof ServerPlayer tp) tp.sendOverlayMessage(Component.literal("Something has your heart").withStyle(ChatFormatting.DARK_RED));
    }

    private static void tick() {
        CRUSHING.values().removeIf(c -> {
            LivingEntity t = c.target();
            if (!(t.level() instanceof ServerLevel level)) return true;
            long age = level.getGameTime() - c.start();
            boolean done = !t.isAlive() || age >= CRUSH_TICKS;
            if (done) {
                t.removeAttached(CRUSH);
                return true;
            }
            // Held in place, barely able to move, while the hand squeezes.
            t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 3, false, false, false));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 15, 1, false, false, false));
            if (age > 0 && age % (CRUSH_TICKS / PULSES) == 0) {
                int left = (int) Math.max(1, (CRUSH_TICKS - age) / (CRUSH_TICKS / PULSES));
                float hp = t.getHealth();
                float amount = Math.max(0f, (hp - FLOOR) / Math.max(1, left));
                if (amount > 0.01f) {
                    t.setInvulnerableTime(0);
                    t.hurtServer(level, level.damageSources().magic(), amount);   // no knockback: they stay held
                    if (t.getHealth() < FLOOR && t.isAlive()) t.setHealth(FLOOR);
                }
                level.playSound(null, t.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 0.6f + 0.05f * (PULSES - left));
                level.sendParticles(new DustParticleOptions(0x8A0F1E, 1.0f), t.getX(), t.getY() + t.getBbHeight() * 0.65, t.getZ(),
                        4, 0.1, 0.1, 0.1, 0);
            }
            return false;
        });
    }

    public static boolean crushing(Entity e) {
        return e.getAttached(CRUSH) != null;
    }
}
