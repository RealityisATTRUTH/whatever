package com.reality.mobcraft.item;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobLook;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mob essences (Shulker Essence, Dragon Essence, the Evoker's Broken Totem, Creeper Essence), and the Shulker Essence: the Shulker's mob item. It drops when another player kills the
 * Shulker (see MobRelics).
 *
 * Right-click it to absorb it. Then the Essence Power key (B) closes a shulker shell
 * around you, like the Shulker's Blocking: you can't move, and nothing hurts you. It's
 * a weaker shell, though. Two hits from any pickaxe, sword or axe break it: the first
 * cracks it (you see the cracks), the second breaks it open and leaves you with
 * Slowness II for 5 seconds. Hits less than half a second apart count once, so
 * spam-clicking doesn't break it any faster. Arrows, fists, explosions and everything else just
 * bounce off.
 *
 *   Lasts       up to 10 seconds; press B again to open it sooner
 *   Cooldown    30 seconds after it opens or breaks
 *   Keeps       until you die, or until you absorb a different essence
 *   Not for     the Shulker player, who has the real shell
 *
 * The shell drawn is the Shulker's, sized to fit around a player (MobLookLayer), with
 * a crack texture over it once it's cracked (textures/entity/shell_cracks.png).
 */
public final class ShulkerEssence {

    public static final String SHULKER = "shulker";
    private static final int SHELL_TICKS = 200;
    private static final int COOLDOWN_TICKS = 600;
    private static final int BREAK_HITS = 2;
    private static final int SLOWNESS_TICKS = 100;
    private static final int HIT_GAP = 10;         // like vanilla's half second of hurt immunity
    private static final int CRACKS_LINGER = 15;   // the cracks stay on while the broken shell opens
    private static final String STILL = "essence_shell_still";

    /** A shell that's closed around a player. */
    private static final class Shell {
        final int closedAt;
        int hits;
        int lastHit = Integer.MIN_VALUE / 2;
        Shell(int closedAt) { this.closedAt = closedAt; }
    }

    private static final Map<UUID, Shell> SHELLS = new HashMap<>();
    private static final Map<UUID, Integer> READY_AT = new HashMap<>();
    private static final Map<UUID, Integer> CRACKS_UNTIL = new HashMap<>();

    private ShulkerEssence() {}

    /**
     * An essence item: right-click to absorb it. Each player holds one essence at a time, so
     * absorbing a different one replaces the one you had. The essence says which:
     * "shulker" (this class) or "dragon" (DragonEssence).
     */
    public static final class EssenceItem extends Item {
        private final String essence;

        public EssenceItem(Properties properties, String essence) {
            super(properties);
            this.essence = essence;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (SHULKER.equals(essence) && PlayerMob.has(sp, MobType.SHULKER)) {
                KitUtil.actionBar(sp, "You're the Shulker. You already have the real shell.");
                return InteractionResult.FAIL;
            }
            if (DragonEssence.DRAGON.equals(essence) && PlayerMob.has(sp, MobType.ENDER_DRAGON)) {
                KitUtil.actionBar(sp, "You're the Ender Dragon. This came out of your own egg.");
                return InteractionResult.FAIL;
            }
            if (CreeperEssence.CREEPER.equals(essence) && PlayerMob.has(sp, MobType.CREEPER)) {
                KitUtil.actionBar(sp, "You're the Creeper. You've got your own blast.");
                return InteractionResult.FAIL;
            }
            if (EvokerEssence.EVOKER.equals(essence) && PlayerMob.has(sp, MobType.EVOKER)) {
                KitUtil.actionBar(sp, "You're the Evoker. Your own totems are whole.");
                return InteractionResult.FAIL;
            }
            String had = sp.getAttached(MobAttachments.ESSENCE);
            if (essence.equals(had)) {
                KitUtil.actionBar(sp, "You already have a " + name(essence) + " in you.");
                return InteractionResult.FAIL;
            }
            if (had != null) forget(sp);      // close out the old one, like an open shell
            ItemStack stack = sp.getItemInHand(hand);
            stack.shrink(1);                  // gone from your inventory, even in creative
            sp.setAttached(MobAttachments.ESSENCE, essence);
            if (EvokerEssence.EVOKER.equals(essence)) sp.setAttached(MobAttachments.ESSENCE_USES, EvokerEssence.USES);
            ServerLevel server = sp.level();
            server.playSound(null, sp.blockPosition(), DragonEssence.DRAGON.equals(essence) ? SoundEvents.ENDER_DRAGON_AMBIENT
                    : EvokerEssence.EVOKER.equals(essence) ? SoundEvents.EVOKER_PREPARE_WOLOLO
                    : CreeperEssence.CREEPER.equals(essence) ? SoundEvents.CREEPER_PRIMED
                    : SoundEvents.SHULKER_AMBIENT, SoundSource.PLAYERS, 0.8f, 1.2f);
            server.sendParticles(ParticleTypes.PORTAL, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.4, 0.6, 0.4, 0.3);
            KitUtil.actionBar(sp, name(essence) + " absorbed" + (had == null ? "" : ", replacing your " + name(had))
                    + ". Press B to use it.");
            return InteractionResult.SUCCESS;
        }
    }

    public static String name(String essence) {
        if (DragonEssence.DRAGON.equals(essence)) return "Dragon Essence";
        if (EvokerEssence.EVOKER.equals(essence)) return "Broken Totem";
        if (CreeperEssence.CREEPER.equals(essence)) return "Creeper Essence";
        return "Shulker Essence";
    }

    public static boolean has(Player player) {
        return SHULKER.equals(player.getAttached(MobAttachments.ESSENCE));
    }

    public static boolean inShell(Player player) {
        return SHELLS.containsKey(player.getUUID());
    }

    /** The Essence Power key: uses whichever essence you have. */
    public static void usePower(ServerPlayer player) {
        String essence = player.getAttached(MobAttachments.ESSENCE);
        if (DragonEssence.DRAGON.equals(essence)) {
            DragonEssence.use(player);
            return;
        }
        if (EvokerEssence.EVOKER.equals(essence)) {
            EvokerEssence.use(player);
            return;
        }
        if (CreeperEssence.CREEPER.equals(essence)) {
            CreeperEssence.use(player);
            return;
        }
        if (!has(player)) {
            KitUtil.actionBar(player, "You have no essence. Right-click one to absorb it.");
            return;
        }
        if (inShell(player)) {
            open(player, false);
            return;
        }
        int now = player.level().getServer().getTickCount();
        Integer ready = READY_AT.get(player.getUUID());
        if (ready != null && now < ready) {
            KitUtil.actionBar(player, "Shell ready in " + ((ready - now + 19) / 20) + "s.");
            return;
        }
        SHELLS.put(player.getUUID(), new Shell(now));
        // Settle into the middle of the block so the shell sits square on it.
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        player.teleportTo(Math.floor(player.getX()) + 0.5, player.getY(), Math.floor(player.getZ()) + 0.5);
        KitUtil.syncMotion(player);
        still(player, true);
        MobLook.set(player, MobLook.SHELL, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHULKER_CLOSE, SoundSource.PLAYERS, 1f, 1f);
    }

    /** Opens the shell: on its own (time up, or B pressed) or broken by hits. */
    private static void open(ServerPlayer player, boolean broken) {
        if (SHELLS.remove(player.getUUID()) == null) return;
        still(player, false);
        MobLook.set(player, MobLook.SHELL, false);
        ServerLevel level = player.level();
        int now = level.getServer().getTickCount();
        READY_AT.put(player.getUUID(), now + COOLDOWN_TICKS);
        if (broken) {
            CRACKS_UNTIL.put(player.getUUID(), now + CRACKS_LINGER);
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_TICKS, 1));
            level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_BOX_CLOSE, SoundSource.PLAYERS, 1f, 0.6f);
            level.playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1f, 0.7f);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.SHULKER_SHELL),
                    player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.8, 0.6, 0.15);
            KitUtil.actionBar(player, "Your shell broke!");
        } else {
            MobLook.set(player, MobLook.SHELL_CRACKED, false);
            level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_OPEN, SoundSource.PLAYERS, 1f, 1f);
        }
    }

    private static void still(ServerPlayer player, boolean on) {
        KitUtil.modifier(player, Attributes.MOVEMENT_SPEED, STILL, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
        KitUtil.modifier(player, Attributes.JUMP_STRENGTH, STILL, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, on);
        KitUtil.modifier(player, Attributes.KNOCKBACK_RESISTANCE, STILL, 1.0, AttributeModifier.Operation.ADD_VALUE, on);
    }

    /** True if a melee hit comes from someone holding a pickaxe, sword or axe. */
    private static boolean breaksShell(DamageSource source) {
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) return false;
        ItemStack held = attacker.getMainHandItem();
        return held.is(ItemTags.PICKAXES) || held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES);
    }

    /** Damage to someone in a shell: blocked. Pickaxe, sword and axe hits wear the shell down. */
    private static boolean allowDamage(LivingEntity victim, DamageSource source) {
        if (!(victim instanceof ServerPlayer player)) return true;
        Shell shell = SHELLS.get(player.getUUID());
        if (shell == null) return true;
        ServerLevel level = player.level();
        int now = level.getServer().getTickCount();
        if (breaksShell(source) && now - shell.lastHit >= HIT_GAP) {
            shell.lastHit = now;
            shell.hits++;
            if (shell.hits >= BREAK_HITS) {
                open(player, true);
            } else {
                MobLook.set(player, MobLook.SHELL_CRACKED, true);
                level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_HURT_CLOSED, SoundSource.PLAYERS, 1f, 0.8f);
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.SHULKER_SHELL),
                        player.getX(), player.getY() + 1, player.getZ(), 12, 0.5, 0.6, 0.5, 0.08);
            }
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_HURT_CLOSED, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Shell shell = SHELLS.get(player.getUUID());
            if (shell != null) {
                still(player, true);
                if (now - shell.closedAt >= SHELL_TICKS) open(player, false);
            }
            Integer cracks = CRACKS_UNTIL.get(player.getUUID());
            if (cracks != null && now >= cracks) {
                CRACKS_UNTIL.remove(player.getUUID());
                MobLook.set(player, MobLook.SHELL_CRACKED, false);
            }
        }
    }

    private static void forget(ServerPlayer player) {
        EvokerEssence.forget(player);
        CreeperEssence.forget(player);
        if (SHELLS.containsKey(player.getUUID())) open(player, false);
        SHELLS.remove(player.getUUID());
        CRACKS_UNTIL.remove(player.getUUID());
        still(player, false);
        if (!PlayerMob.has(player, MobType.SHULKER)) MobLook.set(player, MobLook.SHELL, false);
        MobLook.set(player, MobLook.SHELL_CRACKED, false);
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> allowDamage(victim, source));
        ServerTickEvents.END_SERVER_TICK.register(ShulkerEssence::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) forget(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
    }
}
