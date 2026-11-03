package com.reality.mobcraft.mob;

import com.reality.mobcraft.mob.kit.AxolotlKit;
import com.reality.mobcraft.mob.kit.BatKit;
import com.reality.mobcraft.mob.kit.BlazeKit;
import com.reality.mobcraft.mob.kit.BreezeKit;
import com.reality.mobcraft.mob.kit.CreeperKit;
import com.reality.mobcraft.mob.kit.ElderGuardianKit;
import com.reality.mobcraft.mob.kit.EnderDragonKit;
import com.reality.mobcraft.mob.kit.EndermanKit;
import com.reality.mobcraft.mob.kit.EvokerKit;
import com.reality.mobcraft.mob.kit.VillagerKit;
import com.reality.mobcraft.mob.kit.GhastKit;
import com.reality.mobcraft.mob.kit.IllusionerKit;
import com.reality.mobcraft.mob.kit.IronGolemKit;
import com.reality.mobcraft.mob.kit.PiglinKit;
import com.reality.mobcraft.mob.kit.RabbitKit;
import com.reality.mobcraft.mob.kit.ShulkerKit;
import com.reality.mobcraft.mob.kit.SilverfishKit;
import com.reality.mobcraft.mob.kit.SkeletonKit;
import com.reality.mobcraft.mob.kit.VexKit;
import com.reality.mobcraft.mob.kit.WardenKit;
import com.reality.mobcraft.mob.kit.WitherKit;

import java.util.EnumMap;
import java.util.Map;

/** Which kit each mob uses. Mobs without a kit yet get an empty one. */
public final class MobKits {

    private static final MobKit EMPTY = new MobKit() {};
    private static final Map<MobType, MobKit> KITS = new EnumMap<>(MobType.class);

    private MobKits() {}

    public static void register(MobType type, MobKit kit) {
        KITS.put(type, kit);
    }

    public static MobKit get(MobType type) {
        return type == null ? EMPTY : KITS.getOrDefault(type, EMPTY);
    }

    /** Called once at startup. Each finished mob adds a line here. */
    public static void init() {
        register(MobType.RABBIT, new RabbitKit());
        register(MobType.IRON_GOLEM, new IronGolemKit());
        register(MobType.SKELETON, new SkeletonKit());
        register(MobType.AXOLOTL, new AxolotlKit());
        register(MobType.PIGLIN, new PiglinKit());
        register(MobType.BAT, new BatKit());
        register(MobType.GHAST, new GhastKit());
        register(MobType.EVOKER, new EvokerKit());
        register(MobType.VILLAGER, new VillagerKit());
        register(MobType.WARDEN, new WardenKit());
        register(MobType.VEX, new VexKit());
        register(MobType.SHULKER, new ShulkerKit());
        register(MobType.CREEPER, new CreeperKit());
        register(MobType.SILVERFISH, new SilverfishKit());
        register(MobType.ENDERMAN, new EndermanKit());
        register(MobType.WITHER, new WitherKit());
        register(MobType.ILLUSIONER, new IllusionerKit());
        register(MobType.BLAZE, new BlazeKit());
        register(MobType.ENDER_DRAGON, new EnderDragonKit());
        register(MobType.ELDER_GUARDIAN, new ElderGuardianKit());
        register(MobType.BREEZE, new BreezeKit());
    }
}
