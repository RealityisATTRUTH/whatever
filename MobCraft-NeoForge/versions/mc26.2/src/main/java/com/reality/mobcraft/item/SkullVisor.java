package com.reality.mobcraft.item;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.kit.KitUtil;
import com.reality.mobcraft.mob.kit.SkeletonKit;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;
import java.util.Set;

/**
 * Skull Visor: the Skeleton's mob item, a helmet made from a skeleton's skull. It drops when
 * another player kills the Skeleton (see MobRelics). Anyone can wear it.
 *
 *   Armor        like a leather helmet: 1 armor, 55 durability. Repaired with bones.
 *   Skeletons    skeletons, strays, bogged, parched and wither skeletons leave you alone,
 *                and nothing they do can hurt you
 *   Projectiles  40% less damage from arrows, tridents, fireballs and every other projectile
 *   Bows         draw 25% faster
 *   Sunlight     you burn in the sun like a skeleton, unless it's raining or you're in water
 *   View         black, seen out through the snow goggles' two slits (textures/misc/skull_visor_overlay)
 *
 * Worn, it's the same shattered skull with its goggle pieces fitted to your face and blue
 * energy holding the rest on (client/SkullVisorLayer). Its equippable has no armor layer. Before 0.2.29 it went on
 * a helmet in a crafting grid; helmets that already have one keep the old effect.
 */
public final class SkullVisor {

    private static final double CALM_RANGE = 24;
    private static final float PROJECTILE_TAKEN = 0.6f;   // 40% less
    /** Bows draw one extra tick every this many ticks: 5 ticks of draw in 4 is 25% faster. */
    public static final int BOW_EXTRA_EVERY = 4;
    private static final int SUN_FIRE_SECONDS = 8;

    private static final Set<EntityType<?>> SKELETONS = Set.of(EntityTypes.SKELETON, EntityTypes.STRAY,
            EntityTypes.BOGGED, EntityTypes.PARCHED, EntityTypes.WITHER_SKELETON);

    public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, MobCraft.id("skull_visor"));
    private static final TagKey<Item> REPAIRS = TagKey.create(Registries.ITEM, MobCraft.id("repairs_skull_visor"));
    /** Leather's numbers. The asset isn't drawn: the equippable on the item has none. */
    public static final ArmorMaterial MATERIAL = new ArmorMaterial(5,
            Map.of(ArmorType.HELMET, 1, ArmorType.CHESTPLATE, 3, ArmorType.LEGGINGS, 2, ArmorType.BOOTS, 1, ArmorType.BODY, 3),
            15, SoundEvents.ARMOR_EQUIP_GENERIC, 0f, 0f, REPAIRS, ASSET);

    private SkullVisor() {}

    public static boolean wearing(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.SKULL_VISOR);
    }

    /** Every tick, from ArmorUpgrades. */
    public static void tick(ServerPlayer player) {
        if (!wearing(player)) return;
        if (player.tickCount % 10 == 0) {
            KitUtil.calmAllies(player, CALM_RANGE, SKELETONS);
        }
        if (player.tickCount % 20 == 0 && !player.isCreative() && !player.isSpectator()
                && SkeletonKit.inSunlight(player)) {
            player.igniteForSeconds(SUN_FIRE_SECONDS);
        }
    }

    /** A skeleton that must leave this target alone: they never aim at the wearer (SkullVisorTargetMixin). */
    public static boolean ignores(net.minecraft.world.entity.Mob mob, LivingEntity target) {
        return target != null && SKELETONS.contains(mob.getType()) && wearing(target);
    }

    /** Skeletons can't hurt you, not even with their arrows. */
    public static boolean blocksDamage(LivingEntity victim, DamageSource source) {
        Entity attacker = source.getEntity();
        return attacker != null && SKELETONS.contains(attacker.getType()) && wearing(victim);
    }

    public static float modifyIncoming(LivingEntity victim, DamageSource source, float amount) {
        if (wearing(victim) && source.is(DamageTypeTags.IS_PROJECTILE)) return amount * PROJECTILE_TAKEN;
        return amount;
    }

    /** From the bow mixin, on both sides: drawing a bow while wearing it. */
    public static boolean drawsFaster(LivingEntity entity, ItemStack using) {
        return entity instanceof Player && using.is(Items.BOW) && wearing(entity)
                && entity.tickCount % BOW_EXTRA_EVERY == 0;
    }
}
