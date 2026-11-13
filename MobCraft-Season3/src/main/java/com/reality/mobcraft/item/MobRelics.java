package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.mob.kit.DeathOnce;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mob items: Merc's idea of a custom item for every mob that other players can use
 * to make their own mob stronger.
 *
 * Each one drops when another player kills that mob's player, one per death. Dying
 * any other way drops nothing, so nobody can farm their own. The Bat, Warden,
 * Enderman and Wither already had theirs: the Bat Ear, Warden Tendril, Ender Pearl
 * Shard and Star Fragment, which drop the way they always did.
 *
 * Three kinds:
 *   Armor   attach to armor like the other upgrades and work while worn
 *           (ArmorUpgrades): Rabbit Ears, Breeze Core, Iron Golem Head, Creeper Fuse, Vex Wing,
 *           Skull Visor, Illusion Lens, Elder Spike
 *   Essence absorbed by right-clicking, used with the Essence Power key:
 *           Shulker Essence (ShulkerEssence), Dragon Essence (DragonEssence),
 *           Broken Totem (EvokerEssence)
 *   Eaten   effects that wear off (ModItems.registerFood): Piglin Truffle,
 *           Silverfish Grub, Villager Bread, Axolotl Roe
 *   Brewed  Awkward Potion + item makes a potion, and gunpowder and dragon's
 *           breath make splash and lingering ones like any potion. Ghast Wisp,
 *           Blaze Ember. Recipes are JSON in
 *           data/mobcraft/recipe/brewing.
 *
 * The Potion of Fangs and its Fang Guard came from the Evoker Fang, which the Broken Totem
 * replaced. They stay registered so old worlds with the potion still load, but can't be
 * brewed and aren't in the creative tab.
 */
public final class MobRelics {

    /** Each mob's item, for the ones that didn't already have one. */
    public static final Map<MobType, Item> DROPS = new EnumMap<>(MobType.class);

    static {
        DROPS.put(MobType.RABBIT, ModItems.LUCKY_PAW);
        DROPS.put(MobType.BREEZE, ModItems.BREEZE_CORE);
        DROPS.put(MobType.IRON_GOLEM, ModItems.IRON_HEART);
        DROPS.put(MobType.CREEPER, ModItems.CREEPER_FUSE);
        DROPS.put(MobType.SHULKER, ModItems.SHULKER_ESSENCE);
        DROPS.put(MobType.SKELETON, ModItems.SKULL_VISOR);
        DROPS.put(MobType.ILLUSIONER, ModItems.ILLUSION_LENS);
        DROPS.put(MobType.ELDER_GUARDIAN, ModItems.ELDER_SCALE);
        DROPS.put(MobType.PIGLIN, ModItems.PIGLIN_TRUFFLE);
        DROPS.put(MobType.SILVERFISH, ModItems.SILVERFISH_GRUB);
        DROPS.put(MobType.VILLAGER, ModItems.VILLAGER_BREAD);
        DROPS.put(MobType.AXOLOTL, ModItems.AXOLOTL_ROE);
        DROPS.put(MobType.ENDER_DRAGON, ModItems.DRAGON_ESSENCE);
        DROPS.put(MobType.GHAST, ModItems.GHAST_WISP);
        DROPS.put(MobType.BLAZE, ModItems.BLAZE_EMBER);
        DROPS.put(MobType.VEX, ModItems.VEX_WING);
        DROPS.put(MobType.EVOKER, ModItems.BROKEN_TOTEM);
    }

    // --- The Evoker's Fang Guard ---------------------------------------------------------

    public static final Holder<MobEffect> FANG_GUARD = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
            MobCraft.id("fang_guard"), new MobEffect(MobEffectCategory.BENEFICIAL, 0x959B9B) {});

    private static final int FANG_COOLDOWN_TICKS = 20;
    private static final double FANG_RANGE = 8;
    private static final Map<UUID, Integer> LAST_FANGS = new HashMap<>();

    // --- Potions ---------------------------------------------------------------------------

    public static final Holder<Potion> DRIFTING = potion("drifting",
            new MobEffectInstance(MobEffects.SLOW_FALLING, 3600, 0), new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 3600, 0));
    public static final Holder<Potion> BLAZING = potion("blazing",
            new MobEffectInstance(MobEffects.STRENGTH, 1800, 0), new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1800, 0));
    public static final Holder<Potion> VEX = potion("vex",
            new MobEffectInstance(MobEffects.SPEED, 1800, 1), new MobEffectInstance(MobEffects.SLOW_FALLING, 1800, 0));
    public static final Holder<Potion> FANGS = potion("fangs", new MobEffectInstance(FANG_GUARD, 1800, 0));

    public static final List<Holder<Potion>> POTIONS = List.of(DRIFTING, BLAZING);

    private MobRelics() {}

    private static Holder<Potion> potion(String name, MobEffectInstance... effects) {
        return Registry.registerForHolder(BuiltInRegistries.POTION, MobCraft.id(name), new Potion(name, effects));
    }

    public static void register() {
        // A mob's item drops when another player kills them.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer victim)) return;
            if (!(source.getEntity() instanceof ServerPlayer killer) || killer == victim) return;
            MobType type = PlayerMob.get(victim);
            Item item = type == null ? null : DROPS.get(type);
            if (item == null || !DeathOnce.first(victim, "mob_item")) return;
            ArmorUpgrades.dropAt(victim, item);
        });

        // Fang Guard: fangs bite whoever hits you up close.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, baseDamage, damage, blocked) -> {
            if (!victim.hasEffect(FANG_GUARD) || !(source.getEntity() instanceof LivingEntity attacker)) return;
            if (attacker == victim || source.getDirectEntity() != attacker) return; // melee only
            if (attacker.distanceTo(victim) > FANG_RANGE || !(victim.level() instanceof ServerLevel level)) return;
            int now = victim.tickCount;
            Integer last = LAST_FANGS.get(victim.getUUID());
            if (last != null && now - last < FANG_COOLDOWN_TICKS && now >= last) return;
            LAST_FANGS.put(victim.getUUID(), now);
            float yaw = (float) (Mth.atan2(attacker.getZ() - victim.getZ(), attacker.getX() - victim.getX()) * Mth.RAD_TO_DEG);
            level.addFreshEntity(new EvokerFangs(level, attacker.getX(), attacker.getY(), attacker.getZ(), yaw, 0, victim));
            level.playSound(null, attacker.blockPosition(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 0.8f, 1.2f);
        });
    }
}
