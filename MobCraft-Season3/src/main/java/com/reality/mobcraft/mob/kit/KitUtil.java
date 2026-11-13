package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.rts.MobOwnership;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Shared helpers for mob kits: passives, cooldowns, allies, homes and summons. */
public final class KitUtil {

    private KitUtil() {}

    // --- Passive effects ------------------------------------------------------

    /**
     * Keeps a hidden, endless effect on while "on" is true and takes it off
     * when false. Only removes effects this method added, so potions the
     * player drank are left alone. A stronger potion wins while it lasts.
     */
    public static void passive(LivingEntity entity, Holder<MobEffect> effect, int amplifier, boolean on) {
        MobEffectInstance current = entity.getEffect(effect);
        boolean ours = current != null && current.isAmbient() && current.isInfiniteDuration();
        if (on) {
            boolean needed = current == null
                    || (ours && current.getAmplifier() != amplifier)
                    || current.getAmplifier() < amplifier;
            if (!needed) return;
            if (ours) entity.removeEffect(effect);
            entity.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, true, false, true));
        } else if (ours) {
            entity.removeEffect(effect);
        }
    }

    /** A normal timed effect, in ticks. */
    public static void effect(LivingEntity entity, Holder<MobEffect> effect, int ticks, int amplifier) {
        entity.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true, true));
    }

    /**
     * Sends an entity's new motion to everyone who can see it, including the
     * entity itself if it's a player. Needed after setDeltaMovement on players.
     */
    public static void syncMotion(Entity entity) {
        // A player thrown by a mob power isn't flying: the anti-cheat lets them sail.
        if (entity instanceof ServerPlayer player) com.reality.mobcraft.AntiCheat.exempt(player, 60);
        if (entity.level() instanceof ServerLevel level) {
            level.getChunkSource().sendToTrackingPlayersAndSelf(entity, new ClientboundSetEntityMotionPacket(entity));
        }
    }

    // --- Flight -------------------------------------------------------------------

    /** Vanilla's default flying speed. */
    public static final float DEFAULT_FLY_SPEED = 0.05f;

    /**
     * Turns creative-style flight on or off for a survival player. Creative and
     * spectator players are left alone.
     */
    public static void flight(ServerPlayer player, boolean on, float speed) {
        if (player.isCreative() || player.isSpectator()) return;
        Abilities abilities = player.getAbilities();
        float wantedSpeed = on ? speed : DEFAULT_FLY_SPEED;
        boolean changed = false;
        if (abilities.mayfly != on) {
            abilities.mayfly = on;
            if (!on) abilities.flying = false;
            changed = true;
        }
        if (abilities.getFlyingSpeed() != wantedSpeed) {
            abilities.setFlyingSpeed(wantedSpeed);
            changed = true;
        }
        if (changed) player.onUpdateAbilities();
    }

    // --- Attributes -----------------------------------------------------------

    /** Adds or removes a named attribute modifier. Not saved, so kits re-apply it every tick. */
    public static void modifier(LivingEntity entity, Holder<Attribute> attribute, String name, double amount,
                                AttributeModifier.Operation operation, boolean on) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        Identifier id = MobCraft.id(name);
        boolean has = instance.hasModifier(id);
        if (on && !has) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        } else if (!on && has) {
            instance.removeModifier(id);
        }
    }

    // --- Ability checks ---------------------------------------------------------

    /**
     * The usual checks before an ability fires: switched on in settings, off
     * cooldown, enough energy. Spends the energy and starts the cooldown.
     * Tells the player on their action bar why it didn't fire.
     */
    public static boolean tryUse(ServerPlayer player, String abilityId, String name, float energy, int cooldownTicks) {
        if (com.reality.mobcraft.item.TomePowers.silenced(player)) {
            actionBar(player, "You've been silenced by the Word of Silence.");
            return false;
        }
        if (!MobAbilities.isEnabled(player, abilityId)) {
            actionBar(player, name + " is switched off in /mobcraftsettings.");
            return false;
        }
        int left = Cooldowns.remaining(player, abilityId);
        if (left > 0) {
            actionBar(player, name + " ready in " + seconds(left) + ".");
            return false;
        }
        if (!PlayerMob.spend(player, energy)) {
            actionBar(player, name + " needs " + (int) energy + " energy.");
            return false;
        }
        Cooldowns.start(player, abilityId, cooldownTicks);
        return true;
    }

    public static void actionBar(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message), true);
    }

    public static String seconds(int ticks) {
        return String.format(Locale.ROOT, "%.1fs", ticks / 20.0);
    }

    // --- Where the player is ----------------------------------------------------

    @SafeVarargs
    public static boolean inBiome(Entity entity, ResourceKey<Biome>... biomes) {
        Holder<Biome> here = entity.level().getBiome(entity.blockPosition());
        for (ResourceKey<Biome> biome : biomes) {
            if (here.is(biome)) return true;
        }
        return false;
    }

    public static boolean inStructure(ServerPlayer player, ResourceKey<Structure> structure) {
        return player.level().structureManager().getStructureWithPieceAt(player.blockPosition(),
                (Holder<Structure> holder) -> holder.is(structure)).isValid();
    }

    public static boolean inStructure(ServerPlayer player, TagKey<Structure> structures) {
        return player.level().structureManager().getStructureWithPieceAt(player.blockPosition(), structures).isValid();
    }

    // --- Allies ---------------------------------------------------------------------

    /**
     * Makes nearby allied mobs drop the player as a target and forget any
     * anger at them. Covers goal mobs (skeletons), neutral mobs (iron golems)
     * and brain mobs (piglins).
     */
    public static void calmAllies(ServerPlayer player, double radius, Predicate<Mob> isAlly) {
        AABB area = player.getBoundingBox().inflate(radius);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area, isAlly)) {
            calm(mob, player);
        }
    }

    /** The same, for a fixed list of mob types. */
    public static void calmAllies(ServerPlayer player, double radius, Set<EntityType<?>> allies) {
        calmAllies(player, radius, mob -> allies.contains(mob.getType()));
    }

    public static void calm(Mob mob, ServerPlayer player) {
        if (mob.getTarget() == player) {
            mob.setTarget(null);
            if (mob instanceof NeutralMob neutral) neutral.stopBeingAngry();
        }
        Brain<?> brain = mob.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            Optional<LivingEntity> target = brain.getMemory(MemoryModuleType.ATTACK_TARGET);
            if (target.isPresent() && target.get() == player) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        if (brain.hasMemoryValue(MemoryModuleType.ROAR_TARGET)) {
            Optional<LivingEntity> roar = brain.getMemory(MemoryModuleType.ROAR_TARGET);
            if (roar.isPresent() && roar.get() == player) brain.eraseMemory(MemoryModuleType.ROAR_TARGET);
        }
        // Wardens keep their own anger list, so a hit doesn't make them hold a grudge.
        if (mob instanceof Warden warden) warden.clearAnger(player);
        if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)) {
            Optional<UUID> angryAt = brain.getMemory(MemoryModuleType.ANGRY_AT);
            if (angryAt.isPresent() && angryAt.get().equals(player.getUUID())) brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        }
    }

    /**
     * Runs a command as the server in this world, with no chat output. The
     * command is built like String.format, always with a dot for decimals.
     */
    public static void command(ServerLevel level, String format, Object... args) {
        MinecraftServer server = level.getServer();
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withLevel(level).withSuppressedOutput(),
                String.format(Locale.ROOT, format, args));
    }

    /** Energy regen, doubled while at home. */
    public static float doubledAtHome(boolean home, MobType type) {
        return home ? type.regenPerSecond * 2 : type.regenPerSecond;
    }

    /** Makes a mob go after a target, whichever way that mob picks its targets. */
    public static void provoke(Mob mob, LivingEntity target) {
        if (mob.getTarget() != target) mob.setTarget(target);
        Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)) {
            brain.setMemory(MemoryModuleType.ANGRY_AT, target.getUUID());
        }
        if (mob instanceof Warden warden) warden.increaseAngerAt(target, 80, false);
    }

    /** Mobs owned by this player near them. */
    public static List<Mob> ownedMobs(ServerPlayer player, double radius, Predicate<Mob> filter) {
        AABB area = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(Mob.class, area,
                mob -> mob.isAlive() && MobOwnership.isOwnedBy(mob, player) && filter.test(mob));
    }

    // --- Summoning ------------------------------------------------------------------

    private static final String NEW_TAG = "mobcraft_new";

    /**
     * Summons a mob with the summon command, so variants and gear can be set
     * with plain NBT, then makes the player its owner for RTS. The mob never
     * despawns. Returns null if nothing spawned.
     */
    public static Mob summon(ServerPlayer owner, String entityId, double x, double y, double z, String extraNbt) {
        ServerLevel level = owner.level();
        String nbt = "{Tags:[\"" + NEW_TAG + "\"],PersistenceRequired:1b"
                + (extraNbt == null || extraNbt.isEmpty() ? "" : "," + extraNbt) + "}";
        command(level, "summon %s %.3f %.3f %.3f %s", entityId, x, y, z, nbt);

        AABB area = new AABB(x - 2, y - 2, z - 2, x + 2, y + 2, z + 2);
        List<Mob> found = new ArrayList<>(level.getEntitiesOfClass(Mob.class, area, m -> m.entityTags().contains(NEW_TAG)));
        Mob result = null;
        for (Mob mob : found) {
            mob.removeTag(NEW_TAG);
            MobOwnership.setOwner(mob, owner);
            MobType as = PlayerMob.get(owner);
            if (as != null) mob.setAttached(com.reality.mobcraft.mob.MobAttachments.SUMMONED_AS, as.id());
            result = mob;
        }
        return result;
    }

    /** A UUID in NBT form, [I;a,b,c,d], for summon commands. */
    public static String uuidNbt(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return "[I;" + (int) (most >> 32) + "," + (int) most + "," + (int) (least >> 32) + "," + (int) least + "]";
    }

    /**
     * The y to stand something on near (x, z), searching from a bit above
     * startY down a few blocks. Returns NaN if there's no floor.
     */
    public static double groundY(ServerLevel level, double x, double startY, double z) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int top = Mth.floor(startY) + 2;
        for (int y = top; y >= top - 6; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockPos below = pos.below();
            boolean floor = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
            boolean space = level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
            if (floor && space) return y;
        }
        return Double.NaN;
    }

    /** True if a player fits standing at feet (two free blocks, solid floor). */
    public static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    /**
     * A random spot a player can stand on within radius blocks, or null after
     * a few tries. Used by the Shulker's teleport.
     */
    public static BlockPos randomSafeSpot(ServerPlayer player, int radius) {
        ServerLevel level = player.level();
        BlockPos center = player.blockPosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            int dx = player.getRandom().nextInt(radius * 2 + 1) - radius;
            int dz = player.getRandom().nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz < 4) continue; // somewhere new
            for (int dy = radius; dy >= -radius; dy--) {
                BlockPos feet = center.offset(dx, dy, dz);
                if (standable(level, feet)) return feet;
            }
        }
        return null;
    }

    /**
     * The closest spot to pos with two free blocks for a player, searching
     * outward up to radius. Used to push a Vex out of a wall.
     */
    public static BlockPos nearestFreeSpot(ServerLevel level, BlockPos pos, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dy = 0; dy <= r; dy++) {
                for (int sign : new int[]{1, -1}) {
                    int y = dy * sign;
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) continue;
                            BlockPos feet = pos.offset(dx, y, dz);
                            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                                return feet;
                            }
                        }
                    }
                    if (dy == 0) break;
                }
            }
        }
        return null;
    }

    // --- Items ----------------------------------------------------------------------

    public static void enchant(ServerPlayer player, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
        Holder<Enchantment> holder = player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
        stack.enchant(holder, level);
    }

    public static Holder<Enchantment> enchantment(ServerLevel level, ResourceKey<Enchantment> enchantment) {
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
    }

    // --- Per-player state -----------------------------------------------------------

    /** Small per-player timers, like "playing dead until tick N". Cleared on logout. */
    /**
     * A yes or no for each player, like "is in their home structure right now".
     * Anyone never set counts as no.
     */
    public static final class Flags {
        private final Set<UUID> yes = new HashSet<>();

        public void set(Entity entity, boolean value) {
            if (value) yes.add(entity.getUUID()); else yes.remove(entity.getUUID());
        }

        public boolean is(Entity entity) {
            return yes.contains(entity.getUUID());
        }

        public void forget(Entity entity) {
            yes.remove(entity.getUUID());
        }
    }

    public static final class Timers {
        private final Map<UUID, Integer> ends = new HashMap<>();

        public void start(ServerPlayer player, int ticks) {
            ends.put(player.getUUID(), now(player) + ticks);
        }

        public boolean active(ServerPlayer player) {
            Integer end = ends.get(player.getUUID());
            if (end == null) return false;
            if (now(player) >= end) {
                ends.remove(player.getUUID());
                return false;
            }
            return true;
        }

        public void stop(ServerPlayer player) {
            ends.remove(player.getUUID());
        }

        public void forget(UUID uuid) {
            ends.remove(uuid);
        }
    }

    public static int now(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }
}
