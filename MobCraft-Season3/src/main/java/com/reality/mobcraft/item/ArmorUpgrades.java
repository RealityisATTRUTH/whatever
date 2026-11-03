package com.reality.mobcraft.item;

import com.reality.mobcraft.glow.MobGlow;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.mob.kit.Cooldowns;
import com.reality.mobcraft.mob.kit.KitUtil;
import com.reality.mobcraft.warden.Senses;
import com.reality.mobcraft.warden.WardenEcholocation;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Mob drops that attach to armor and give powers to whoever wears it.
 *
 * Attaching is a crafting recipe, see ArmorUpgradeRecipe. One upgrade per
 * piece; a new one replaces the old, except a second ear or tendril joins the
 * first. The piece gets a line in its tooltip saying what's on it.
 *
 *   Helmet + one Bat Ear     echolocation: white ghosts of where nearby
 *                            players and mobs were 1s ago, and living things
 *                            within 16 blocks glow blue (24 with both)
 *   Helmet + both Bat Ears (left and right, one at a time)
 *                            the same, with the bigger glow range
 *   Helmet + one Warden Tendril   echolocation and the blue glow, like an ear
 *   Helmet + both Warden Tendrils (left and right, one at a time)
 *                            echolocation and sniffing (red ghosts from 10s
 *                            ago), and every warden within 64 blocks glows
 *   Chestplate + Ender Pearl Shard  Armor Power key (Z): teleport to a random
 *                            spot within 10 blocks. 10s cooldown.
 *   Helmet or chestplate + Stalker Shard  Armor Power key (Z): teleport behind
 *                            the nearest player within 24 blocks who isn't
 *                            looking at you. Never mobs or teammates. 20s cooldown.
 *                            Sneak+Z: a rough random teleport within 10 blocks.
 *                            It can drop you in the air, in water or in lava,
 *                            hurts 2.5 hearts, and 20% of the time an
 *                            endermite comes out where you land. 10s cooldown.
 *                            With an Ender Pearl Shard on the chestplate too,
 *                            sneak+Z uses its safe teleport instead.
 *
 * Helmet senses don't apply to the Warden player, who has his own.
 */
public final class ArmorUpgrades {

    public static final String EAR_LEFT = "bat_ear_left";
    public static final String EAR_RIGHT = "bat_ear_right";
    /** Stored on a helmet that has both bat ears. */
    public static final String EAR_BOTH = "bat_ears";
    public static final String TENDRIL_LEFT = "warden_tendril_left";
    public static final String TENDRIL_RIGHT = "warden_tendril_right";
    /** Stored on a helmet that has both tendrils. */
    public static final String TENDRIL_BOTH = "warden_tendrils";
    public static final String ENDER_SHARD = "ender_pearl_shard";
    public static final String STALKER_SHARD = "stalker_shard";
    /** Goes on a netherite sword: see StarBlade. */
    public static final String STAR_SHARD = "nether_star_shard";

    private static final int WARDEN_COLOR = 0x0FA3A3;
    private static final int BAT_COLOR = 0x20FFFF;
    private static final double GLOW_RANGE_ONE = 16;   // one ear or tendril
    private static final double GLOW_RANGE_BOTH = 24;  // both
    private static final String RANDOM_COOLDOWN = "armor_random_teleport";
    private static final String STALK_COOLDOWN = "armor_stalk";
    private static final String ROUGH_COOLDOWN = "armor_rough_teleport";
    private static final float ROUGH_DAMAGE = 5.0f;      // 2.5 hearts, like an ender pearl
    private static final float ENDERMITE_CHANCE = 0.2f;

    /** Players whose helmet is currently giving them ghosts. */
    private static final Set<UUID> SENSING = new HashSet<>();

    private ArmorUpgrades() {}

    public static void register() {
        StarBlade.register();
        // Creakings drop a splinter.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getType() == EntityTypes.CREAKING) dropAt(entity, ModItems.CREAKING_SPLINTER);
        });
    }

    /** Drops one of an item where something died. Used for the mob death drops. */
    public static void dropAt(LivingEntity entity, Item item) {
        entity.level().addFreshEntity(new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(),
                new ItemStack(item, 1)));
    }

    // --- Attaching ----------------------------------------------------------------------

    /** Whether an upgrade can go on armor worn in this slot. */
    public static boolean fits(String id, EquipmentSlot slot) {
        return switch (id) {
            case EAR_LEFT, EAR_RIGHT, TENDRIL_LEFT, TENDRIL_RIGHT -> slot == EquipmentSlot.HEAD;
            case ENDER_SHARD -> slot == EquipmentSlot.CHEST;
            case STAR_SHARD -> slot == EquipmentSlot.MAINHAND; // also needs a netherite upgrade template, see ArmorUpgradeRecipe
            case STALKER_SHARD -> slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST;
            default -> false;
        };
    }

    /**
     * The upgrade a piece ends up with after adding one, or null if it
     * already has it. A second ear or tendril joins the first.
     */
    public static String combine(String current, String added) {
        if ((added.equals(TENDRIL_LEFT) && TENDRIL_RIGHT.equals(current))
                || (added.equals(TENDRIL_RIGHT) && TENDRIL_LEFT.equals(current))) return TENDRIL_BOTH;
        if ((added.equals(EAR_LEFT) && EAR_RIGHT.equals(current))
                || (added.equals(EAR_RIGHT) && EAR_LEFT.equals(current))) return EAR_BOTH;
        if (added.equals(current) || (TENDRIL_BOTH.equals(current) && isTendril(added))
                || (EAR_BOTH.equals(current) && isEar(added))) return null;
        return added;
    }

    /** Puts the upgrade on the piece, with tooltip lines saying what it is and what it does. */
    public static void apply(ItemStack armor, String id) {
        armor.set(ModItems.ARMOR_UPGRADE, id);
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.literal("Upgrade: " + displayName(id)).withStyle(ChatFormatting.DARK_AQUA));
        for (String buff : buffs(id)) {
            lines.add(Component.literal(" " + buff).withStyle(ChatFormatting.GRAY));
        }
        armor.set(DataComponents.LORE, new ItemLore(lines));
        if (STAR_SHARD.equals(id)) StarBlade.makeChargeable(armor);
    }

    /** What each upgrade does, for the tooltip. Keep in step with tick() and usePower(). */
    private static List<String> buffs(String id) {
        return switch (id) {
            case EAR_LEFT, EAR_RIGHT, TENDRIL_LEFT, TENDRIL_RIGHT -> List.of(
                    "Echolocation: see where nearby players and mobs were a moment ago",
                    "Living things within 16 blocks glow blue for you");
            case EAR_BOTH -> List.of(
                    "Echolocation: see where nearby players and mobs were a moment ago",
                    "Living things within 24 blocks glow blue for you");
            case TENDRIL_BOTH -> List.of(
                    "Echolocation: see where nearby players and mobs were a moment ago",
                    "Living things within 24 blocks glow blue for you",
                    "Sniffing: see where they were 10 seconds ago",
                    "Wardens within 64 blocks glow");
            case ENDER_SHARD -> List.of(
                    "Armor Power key: teleport to a random safe spot within 10 blocks",
                    "Cooldown: 10 seconds");
            case STALKER_SHARD -> List.of(
                    "Armor Power key: teleport behind the nearest player",
                    "who isn't looking at you. Cooldown: 20 seconds",
                    "Sneak + Armor Power: rough random teleport. Hurts you,",
                    "and 1 in 5 times an endermite follows. Cooldown: 10 seconds");
            case STAR_SHARD -> List.of(
                    "Hold right-click for 10 seconds to charge,",
                    "then let go to fire a beam of star energy.",
                    "16 damage to everything in its path. Cooldown: 45 seconds",
                    "If you die, it shatters and is gone for good.");
            default -> List.of();
        };
    }

    /** The upgrade id an item adds, or null if it isn't an upgrade. */
    public static String upgradeId(ItemStack stack) {
        if (stack.is(ModItems.BAT_EAR_LEFT)) return EAR_LEFT;
        if (stack.is(ModItems.BAT_EAR_RIGHT)) return EAR_RIGHT;
        if (stack.is(ModItems.WARDEN_TENDRIL_LEFT)) return TENDRIL_LEFT;
        if (stack.is(ModItems.WARDEN_TENDRIL_RIGHT)) return TENDRIL_RIGHT;
        if (stack.is(ModItems.ENDER_PEARL_SHARD)) return ENDER_SHARD;
        if (stack.is(ModItems.STALKER_SHARD)) return STALKER_SHARD;
        if (stack.is(ModItems.NETHER_STAR_SHARD)) return STAR_SHARD;
        return null;
    }

    /** HEAD or CHEST for helmets and chestplates, MAINHAND for a netherite sword, otherwise null. */
    public static EquipmentSlot armorSlot(ItemStack stack) {
        if (stack.is(Items.NETHERITE_SWORD)) return EquipmentSlot.MAINHAND;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return null;
        EquipmentSlot slot = equippable.slot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST ? slot : null;
    }

    /** Goes left of the helmet when crafting. The tendrils' names are swapped from their ids. */
    public static boolean isLeftPiece(String id) {
        return EAR_LEFT.equals(id) || TENDRIL_RIGHT.equals(id);
    }

    /** Goes right of the helmet when crafting. */
    public static boolean isRightPiece(String id) {
        return EAR_RIGHT.equals(id) || TENDRIL_LEFT.equals(id);
    }

    public static boolean isEar(String id) {
        return EAR_LEFT.equals(id) || EAR_RIGHT.equals(id) || EAR_BOTH.equals(id);
    }

    public static boolean isTendril(String id) {
        return TENDRIL_LEFT.equals(id) || TENDRIL_RIGHT.equals(id) || TENDRIL_BOTH.equals(id);
    }

    private static String displayName(String id) {
        return switch (id) {
            case EAR_LEFT -> "Left Bat Ear";
            case EAR_RIGHT -> "Right Bat Ear";
            case EAR_BOTH -> "Both Bat Ears";
            case TENDRIL_LEFT -> "Right Warden Tendril";
            case TENDRIL_RIGHT -> "Left Warden Tendril";
            case TENDRIL_BOTH -> "Both Warden Tendrils";
            case ENDER_SHARD -> "Ender Pearl Shard";
            case STALKER_SHARD -> "Stalker Shard";
            case STAR_SHARD -> "Fracture Nether Star";
            default -> id;
        };
    }

    private static String upgradeOn(Player player, EquipmentSlot slot) {
        return player.getItemBySlot(slot).get(ModItems.ARMOR_UPGRADE);
    }

    // --- Helmet senses --------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            StarBlade.tick(player);
            // Advancement: wearing armor with an upgrade on it.
            if (player.tickCount % 40 == 0 && (upgradeOn(player, EquipmentSlot.HEAD) != null
                    || upgradeOn(player, EquipmentSlot.CHEST) != null)) {
                com.reality.mobcraft.MobAdvancements.award(player, "armored_up");
            }
            if (PlayerMob.has(player, MobType.WARDEN)) {
                SENSING.remove(player.getUUID()); // the Warden kit runs his own senses
                continue;
            }
            String helmet = upgradeOn(player, EquipmentSlot.HEAD);
            // Echolocation Markers switched off in /mobcraftsettings: no markers and no glow.
            boolean echo = (isEar(helmet) || isTendril(helmet)) && Senses.isOn(player);
            boolean sniff = TENDRIL_BOTH.equals(helmet);
            boolean was = SENSING.contains(player.getUUID());

            if (echo) {
                if (!was) {
                    SENSING.add(player.getUUID());
                    WardenEcholocation.enable(player);
                }
                WardenEcholocation.setLayers(player, true, sniff);
            } else if (was) {
                SENSING.remove(player.getUUID());
                WardenEcholocation.disable(player);
            }

            if (echo && player.tickCount % 20 == 0) {
                boolean both = EAR_BOTH.equals(helmet) || TENDRIL_BOTH.equals(helmet);
                senseNearby(player, both ? GLOW_RANGE_BOTH : GLOW_RANGE_ONE);
                // Wardens stand out in their own color, drawn after the blue so it wins.
                if (TENDRIL_BOTH.equals(helmet)) senseWardens(player);
            }
        }
    }

    /**
     * Any bat ear or warden tendril: living things nearby glow blue, like the
     * Bat's Echolocation. Only the wearer sees it.
     */
    private static void senseNearby(ServerPlayer player, double range) {
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(range), e -> e != player && e.isAlive())) {
            MobGlow.glow(player, entity, BAT_COLOR, 30);
        }
    }

    private static void senseWardens(ServerPlayer player) {
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(64), e -> e != player && e.isAlive())) {
            boolean warden = entity.getType() == EntityTypes.WARDEN
                    || (entity instanceof ServerPlayer other && PlayerMob.has(other, MobType.WARDEN));
            if (warden) MobGlow.glow(player, entity, WARDEN_COLOR, 30);
        }
    }

    // --- Armor Power key -------------------------------------------------------------------

    /** Called from the Armor Power key packet. */
    public static void usePower(ServerPlayer player, boolean sneaking) {
        String helmet = upgradeOn(player, EquipmentSlot.HEAD);
        String chest = upgradeOn(player, EquipmentSlot.CHEST);
        boolean stalker = STALKER_SHARD.equals(helmet) || STALKER_SHARD.equals(chest);
        boolean ender = ENDER_SHARD.equals(chest);

        if (stalker && !sneaking) {
            stalk(player);
        } else if (ender) {
            randomTeleport(player);
        } else if (stalker) {
            roughTeleport(player);
        } else {
            KitUtil.actionBar(player, "Your armor has no power to use.");
        }
    }

    private static void randomTeleport(ServerPlayer player) {
        int left = Cooldowns.remaining(player, RANDOM_COOLDOWN);
        if (left > 0) {
            KitUtil.actionBar(player, "Ender Pearl Shard ready in " + KitUtil.seconds(left) + ".");
            return;
        }
        BlockPos spot = KitUtil.randomSafeSpot(player, 10);
        if (spot == null) {
            KitUtil.actionBar(player, "No safe spot nearby.");
            return;
        }
        Cooldowns.start(player, RANDOM_COOLDOWN, 200);
        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        player.resetFallDistance();
        level.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * The Stalker Shard's random teleport. Unlike the Ender Pearl Shard it
     * doesn't look for a safe spot, only one you won't be stuck inside a wall.
     */
    private static void roughTeleport(ServerPlayer player) {
        int left = Cooldowns.remaining(player, ROUGH_COOLDOWN);
        if (left > 0) {
            KitUtil.actionBar(player, "Stalker Shard teleport ready in " + KitUtil.seconds(left) + ".");
            return;
        }
        ServerLevel level = player.level();
        BlockPos spot = null;
        for (int attempt = 0; attempt < 24 && spot == null; attempt++) {
            BlockPos test = player.blockPosition().offset(
                    player.getRandom().nextInt(21) - 10,
                    player.getRandom().nextInt(7) - 3,
                    player.getRandom().nextInt(21) - 10);
            if (level.getBlockState(test).getCollisionShape(level, test).isEmpty()
                    && level.getBlockState(test.above()).getCollisionShape(level, test.above()).isEmpty()) {
                spot = test;
            }
        }
        if (spot == null) {
            KitUtil.actionBar(player, "The shard flickers but nothing happens.");
            return;
        }
        Cooldowns.start(player, ROUGH_COOLDOWN, 200);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.7f);
        player.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        level.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.7f);
        player.hurtServer(level, player.damageSources().fall(), ROUGH_DAMAGE);
        if (player.getRandom().nextFloat() < ENDERMITE_CHANCE) {
            KitUtil.command(level, "summon minecraft:endermite %.3f %d %.3f",
                    spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        }
    }

    /** Teleports behind the nearest other player who isn't looking at you, facing them. */
    private static void stalk(ServerPlayer player) {
        int left = Cooldowns.remaining(player, STALK_COOLDOWN);
        if (left > 0) {
            KitUtil.actionBar(player, "Stalker Shard ready in " + KitUtil.seconds(left) + ".");
            return;
        }
        ServerLevel level = player.level();
        LivingEntity best = null;
        BlockPos bestSpot = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(24),
                e -> e != player && e.isAlive())) {
            // Only other players, never mobs or teammates.
            if (!(target instanceof Player other) || other.isAlliedTo(player) || other.isSpectator()) continue;
            // Not looking at you: their view points away from you.
            Vec3 toYou = player.getEyePosition().subtract(target.getEyePosition()).normalize();
            if (target.getLookAngle().normalize().dot(toYou) > 0.3) continue;
            double distance = target.distanceTo(player);
            if (distance >= bestDistance) continue;
            BlockPos spot = behind(level, target);
            if (spot == null) continue;
            best = target;
            bestSpot = spot;
            bestDistance = distance;
        }
        if (best == null) {
            KitUtil.actionBar(player, "No player nearby has their back to you.");
            return;
        }
        Cooldowns.start(player, STALK_COOLDOWN, 400);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 0.6f);
        // A teleport command so you arrive already facing their back.
        KitUtil.command(level, "tp %s %.3f %d %.3f facing entity %s eyes",
                player.getStringUUID(), bestSpot.getX() + 0.5, bestSpot.getY(), bestSpot.getZ() + 0.5, best.getStringUUID());
        player.resetFallDistance();
    }

    /** A standable spot about 1.5 blocks behind the target, or null. */
    private static BlockPos behind(ServerLevel level, LivingEntity target) {
        Vec3 look = target.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) return null;
        Vec3 back = target.position().subtract(flat.normalize().scale(1.5));
        BlockPos base = BlockPos.containing(back);
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos feet = base.offset(0, dy, 0);
            if (KitUtil.standable(level, feet)) return feet;
        }
        return null;
    }

    public static void forget(ServerPlayer player) {
        SENSING.remove(player.getUUID());
        StarBlade.forget(player);
    }
}
