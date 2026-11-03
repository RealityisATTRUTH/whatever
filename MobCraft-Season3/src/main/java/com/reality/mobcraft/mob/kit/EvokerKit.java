package com.reality.mobcraft.mob.kit;

import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Evoker (Rotonz).
 *
 *   Fangs         R with a totem in your main hand: a line of fangs where you
 *                 look. Sneak+R: a ring of fangs around you. 5 energy, 1s cooldown.
 *   Summon Vexes  G: 2 second charge, then 3 vexes that attack whoever you hit
 *                 and can be commanded in RTS. They fade after 60s.
 *                 30 energy, 20s cooldown, 9 alive at most.
 *   Raid Captain  DRAFT effect. Put an ominous banner on your head (right-click
 *                 it, only the Evoker can) and you become a raid captain for
 *                 1:00. Pillagers, vindicators, evokers, illusioners, ravagers
 *                 and witches within 32 blocks won't attack you, go after
 *                 whatever you hit or whatever hits you, and get Strength I
 *                 and Speed I within 16 blocks. Whoever kills you while you're
 *                 captain gets Bad Omen. Taking the banner off ends it.
 *                 35 energy, 2:30 cooldown after the minute.
 *   Self-give     totems only out of combat (15s since you last hit or were hit),
 *                 emeralds any time
 *   Allies        evokers and vexes. No vex can ever hurt you.
 *   Bells         within 50 blocks of a bell you glow
 *   Home          Woodland Mansion: take 10% less damage
 *   Curse         no totem self-give
 */
public final class EvokerKit implements MobKit {

    /** Mobs that never attack you. */
    private static final Set<EntityType<?>> ALLIES = Set.of(EntityTypes.EVOKER, EntityTypes.VEX);

    private static final String FANG_LINE = "fang_attack";
    private static final String FANG_RING = "fang_surround";
    private static final String VEXES = "summon_vexes";
    private static final int VEX_CHARGE = 40;
    private static final int MAX_VEXES = 9;
    private static final int OUT_OF_COMBAT = 300;  // 15 seconds
    private static final String CAPTAIN_ID = "raid_captain";
    private static final int CAPTAIN_TICKS = 1200;               // 1:00
    private static final int CAPTAIN_COOLDOWN = 1200 + 3000;     // the minute, then 2:30
    private static final float CAPTAIN_ENERGY = 35;
    private static final Set<EntityType<?>> RAIDERS = Set.of(EntityTypes.PILLAGER, EntityTypes.VINDICATOR,
            EntityTypes.EVOKER, EntityTypes.ILLUSIONER, EntityTypes.RAVAGER, EntityTypes.WITCH);

    private static final KitUtil.Timers CHARGING = new KitUtil.Timers();
    private static final KitUtil.Flags CHARGE_PENDING = new KitUtil.Flags();
    private static final KitUtil.Flags NEAR_BELL = new KitUtil.Flags();
    private static final KitUtil.Flags IN_MANSION = new KitUtil.Flags();
    private static final KitUtil.Timers CAPTAIN = new KitUtil.Timers();
    private static final Set<UUID> WEARING_BANNER = new HashSet<>();
    private static final Set<UUID> CAPTAIN_SHOWN = new HashSet<>();

    @Override
    public Set<EntityType<?>> allies() {
        return ALLIES;
    }

    @Override
    public void tick(ServerPlayer player) {
        if (player.tickCount % 40 == 0) {
            NEAR_BELL.set(player, nearBell(player));
            IN_MANSION.set(player, KitUtil.inStructure(player, BuiltinStructures.WOODLAND_MANSION));
        }
        KitUtil.passive(player, MobEffects.GLOWING, 0, NEAR_BELL.is(player));

        tickCaptain(player);
        boolean captain = CAPTAIN.active(player);
        KitUtil.calmAllies(player, 32, mob -> ALLIES.contains(mob.getType())
                || (captain && RAIDERS.contains(mob.getType())));

        // Vex charge finished.
        if (CHARGE_PENDING.is(player) && !CHARGING.active(player)) {
            CHARGE_PENDING.forget(player);
            spawnVexes(player);
        }
    }

    @Override
    public void onRemoved(ServerPlayer player) {
        KitUtil.passive(player, MobEffects.GLOWING, 0, false);
        forget(player);
    }

    @Override
    public void onAbility(ServerPlayer player, int slot, boolean sneaking) {
        if (slot == ABILITY_1) {
            if (!player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                KitUtil.actionBar(player, "Hold a totem in your main hand to cast fangs.");
                return;
            }
            if (sneaking) {
                if (!KitUtil.tryUse(player, FANG_RING, "Fang Surround", 5, 20)) return;
                fangRing(player);
            } else {
                if (!KitUtil.tryUse(player, FANG_LINE, "Fang Attack", 5, 20)) return;
                fangLine(player);
            }
            player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.0f);
        } else if (slot == ABILITY_2) {
            if (CHARGE_PENDING.is(player)) return;
            if (vexes(player).size() >= MAX_VEXES) {
                KitUtil.actionBar(player, "You already have " + MAX_VEXES + " vexes.");
                return;
            }
            if (!KitUtil.tryUse(player, VEXES, "Summon Vexes", 30, 400)) return;
            CHARGING.start(player, VEX_CHARGE);
            CHARGE_PENDING.set(player, true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0f, 1.0f);
            KitUtil.actionBar(player, "Summoning vexes...");
        } else {
            KitUtil.actionBar(player, "Raid Captain: put an ominous banner on your head.");
        }
    }

    // --- Raid Captain ----------------------------------------------------------------

    /** Right-clicking an ominous banner puts it on your head, swapping out the helmet. */
    @Override
    public InteractionResult onUseItem(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isOminousBanner(held)) return InteractionResult.PASS;
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (held.getCount() == 1) {
            player.setItemSlot(EquipmentSlot.HEAD, held.copy());
            player.setItemInHand(hand, head);
        } else {
            player.setItemSlot(EquipmentSlot.HEAD, held.split(1));
            if (!head.isEmpty() && !player.getInventory().add(head)) {
                player.level().addFreshEntity(new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), head));
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isOminousBanner(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Component name = stack.get(DataComponents.ITEM_NAME);
        return name != null && name.getContents() instanceof TranslatableContents key
                && key.getKey().equals("block.minecraft.ominous_banner");
    }

    private static void tickCaptain(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean wearing = isOminousBanner(player.getItemBySlot(EquipmentSlot.HEAD));
        boolean justPutOn = wearing && WEARING_BANNER.add(id);
        if (!wearing) WEARING_BANNER.remove(id);

        boolean active = CAPTAIN.active(player);
        if (active && !wearing) {
            CAPTAIN.stop(player);
            active = false;
        }
        if (!active && CAPTAIN_SHOWN.remove(id)) {
            KitUtil.actionBar(player, "You are no longer the raid captain.");
        }

        if (wearing && !active) {
            if (justPutOn) {
                if (KitUtil.tryUse(player, CAPTAIN_ID, "Raid Captain", CAPTAIN_ENERGY, CAPTAIN_COOLDOWN)) startCaptain(player);
            } else if (player.tickCount % 20 == 0 && MobAbilities.isEnabled(player, CAPTAIN_ID)
                    && Cooldowns.remaining(player, CAPTAIN_ID) == 0 && PlayerMob.spend(player, CAPTAIN_ENERGY)) {
                // Still wearing it when the cooldown ends: it starts again by itself.
                Cooldowns.start(player, CAPTAIN_ID, CAPTAIN_COOLDOWN);
                startCaptain(player);
            }
        }

        if (CAPTAIN.active(player) && player.tickCount % 20 == 0) {
            for (Mob raider : raiders(player, 16)) {
                KitUtil.effect(raider, MobEffects.STRENGTH, 60, 0);
                KitUtil.effect(raider, MobEffects.SPEED, 60, 0);
            }
        }
    }

    private static void startCaptain(ServerPlayer player) {
        CAPTAIN.start(player, CAPTAIN_TICKS);
        CAPTAIN_SHOWN.add(player.getUUID());
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0f, 0.7f);
        KitUtil.actionBar(player, "You are the raid captain for 1 minute.");
    }

    private static List<Mob> raiders(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                mob -> mob.isAlive() && RAIDERS.contains(mob.getType()));
    }

    /** Sends the raiders after a target. */
    private static void rally(ServerPlayer player, LivingEntity target) {
        if (target == player || (target instanceof Mob mob && RAIDERS.contains(mob.getType()))) return;
        for (Mob raider : raiders(player, 32)) raider.setTarget(target);
    }

    @Override
    public void afterAttack(ServerPlayer player, LivingEntity victim, DamageSource source, float damage) {
        if (CAPTAIN.active(player)) rally(player, victim);
    }

    @Override
    public void afterHurt(ServerPlayer player, DamageSource source, float damage) {
        if (CAPTAIN.active(player) && source.getEntity() instanceof LivingEntity attacker) rally(player, attacker);
    }

    @Override
    public void onDeath(ServerPlayer player, DamageSource source) {
        if (CAPTAIN.active(player) && source.getEntity() instanceof ServerPlayer killer) {
            KitUtil.effect(killer, MobEffects.BAD_OMEN, 120000, 0);
        }
        CAPTAIN.stop(player);
        CAPTAIN_SHOWN.remove(player.getUUID());
    }

    // --- Fangs ---------------------------------------------------------------------

    private static void fangLine(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = Vec3.directionFromRotation(0, player.getYRot());
        flat = flat.normalize();
        float yaw = player.getYRot();
        for (int i = 0; i < 16; i++) {
            double distance = 1.25 * (i + 1);
            fang(player, player.getX() + flat.x * distance, player.getZ() + flat.z * distance, yaw, i);
        }
    }

    private static void fangRing(ServerPlayer player) {
        for (int i = 0; i < 5; i++) {
            float angle = (float) (Math.PI * 2 * i / 5);
            fang(player, player.getX() + Math.cos(angle) * 1.5, player.getZ() + Math.sin(angle) * 1.5, (float) Math.toDegrees(angle), 0);
        }
        for (int i = 0; i < 8; i++) {
            float angle = (float) (Math.PI * 2 * i / 8 + Math.PI * 2 / 5 * 1.25);
            fang(player, player.getX() + Math.cos(angle) * 2.5, player.getZ() + Math.sin(angle) * 2.5, (float) Math.toDegrees(angle), 3);
        }
    }

    /** One fang, owned by the player so it never bites them and kills count as theirs. */
    private static void fang(ServerPlayer player, double x, double z, float yaw, int warmup) {
        ServerLevel level = player.level();
        double y = KitUtil.groundY(level, x, player.getY(), z);
        if (Double.isNaN(y)) return;
        KitUtil.command(level, "summon minecraft:evoker_fangs %.3f %.3f %.3f {Warmup:%d,Owner:%s,Rotation:[%.1ff,0f]}",
                x, y, z, warmup, KitUtil.uuidNbt(player.getUUID()), yaw);
    }

    // --- Vexes ---------------------------------------------------------------------

    private static void spawnVexes(ServerPlayer player) {
        int room = Math.min(3, MAX_VEXES - vexes(player).size());
        for (int i = 0; i < room; i++) {
            double angle = Math.PI * 2 * i / 3;
            KitUtil.summon(player, "minecraft:vex",
                    player.getX() + Math.cos(angle), player.getY() + 1.0, player.getZ() + Math.sin(angle),
                    "LifeTicks:1200");
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    private static List<Mob> vexes(ServerPlayer player) {
        return KitUtil.ownedMobs(player, 64, mob -> mob.getType() == EntityTypes.VEX);
    }

    // --- Home, bells, items -------------------------------------------------------------

    /** No vex ever hurts the Evoker, his own or anyone else's. */
    @Override
    public boolean allowHurt(ServerPlayer player, DamageSource source, float amount) {
        return source.getEntity() == null || source.getEntity().getType() != EntityTypes.VEX;
    }

    @Override
    public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
        return IN_MANSION.is(player) ? amount * 0.9f : amount;
    }

    private static boolean nearBell(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return player.level().getPoiManager()
                .findClosest(type -> type.is(PoiTypes.MEETING), pos, 50, PoiManager.Occupancy.ANY)
                .isPresent();
    }

    @Override
    public boolean canSelfGive(ServerPlayer player, String itemId) {
        if (!itemId.equals("totem_of_undying")) return true;
        if (PlayerMob.isCursed(player)) return false;
        return !CombatTag.inCombat(player, OUT_OF_COMBAT);
    }

    static void forget(ServerPlayer player) {
        CHARGING.forget(player.getUUID());
        CHARGE_PENDING.forget(player);
        NEAR_BELL.forget(player);
        IN_MANSION.forget(player);
        CAPTAIN.forget(player.getUUID());
        WEARING_BANNER.remove(player.getUUID());
        CAPTAIN_SHOWN.remove(player.getUUID());
    }

    // --- Ultimate: Fang Storm ---------------------------------------------------------------
    /**
     * Three rings of fangs burst out around you one after another, 42 fangs
     * in all, and 3 vexes join you (up to the usual 9).
     */
    @Override
    public boolean hasUltimate() {
        return true;
    }

    @Override
    public String ultimateName() {
        return "Fang Storm";
    }

    @Override
    public boolean onUltimate(ServerPlayer player) {
        for (int ring = 0; ring < 3; ring++) {
            double radius = 2 + ring * 2;
            int count = 8 + ring * 6;
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * i / count;
                fang(player, player.getX() + Math.cos(angle) * radius, player.getZ() + Math.sin(angle) * radius,
                        (float) Math.toDegrees(angle), ring * 4);
            }
        }
        spawnVexes(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.5f, 0.8f);
        KitUtil.actionBar(player, "Fang Storm!");
        return true;
    }
}
