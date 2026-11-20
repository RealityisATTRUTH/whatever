package com.reality.mobcraft.mob;

import com.mojang.serialization.Codec;
import com.reality.mobcraft.MobCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;

import java.util.List;

/**
 * Data stored on each player. Saved with the world, kept through death,
 * and synced to clients so the HUD can show it.
 */
public final class MobAttachments {

    /** The player's mob id, like "rabbit". Every client knows everyone's mob. */
    public static final AttachmentType<String> MOB = AttachmentRegistry.create(
            MobCraft.id("mob"),
            builder -> builder
                    .persistent(Codec.STRING)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all())
    );

    /** Current energy. Only the player themselves needs to see it. */
    public static final AttachmentType<Float> ENERGY = AttachmentRegistry.create(
            MobCraft.id("energy"),
            builder -> builder
                    .persistent(Codec.FLOAT)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
    );

    /**
     * Ultimate charge, 0 to 100. Only set for players whose mob has an
     * ultimate, so the client knows to draw the ultimate bar. See Ultimate.
     */
    public static final AttachmentType<Float> ULTIMATE = AttachmentRegistry.create(
            MobCraft.id("ultimate"),
            builder -> builder
                    .persistent(Codec.FLOAT)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly())
    );

    /** Ticks of curse left. 0 means not cursed. Survives death so dying doesn't clear it. */
    public static final AttachmentType<Integer> CURSE_TICKS = AttachmentRegistry.create(
            MobCraft.id("curse_ticks"),
            builder -> builder
                    .persistent(Codec.INT)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /** The mob this player was before their current one. Its kind treats them as an enemy. */
    public static final AttachmentType<String> FORMER_MOB = AttachmentRegistry.create(
            MobCraft.id("former_mob"),
            builder -> builder
                    .persistent(Codec.STRING)
                    .copyOnDeath()
    );

    /**
     * On the Overworld: the UUID of the player who is the Ender Dragon. The
     * End rules (no dragons, open gateways) only apply while this is set.
     */
    public static final AttachmentType<String> DRAGON_PLAYER = AttachmentRegistry.create(
            MobCraft.id("dragon_player"),
            builder -> builder.persistent(Codec.STRING)
    );

    /** On the Overworld: which of the 20 End gateways MobCraft opened, so they can be closed again. */
    public static final AttachmentType<List<Integer>> OPENED_GATEWAYS = AttachmentRegistry.create(
            MobCraft.id("opened_gateways"),
            builder -> builder.persistent(Codec.INT.listOf())
    );

    /** On the Overworld: set once the gateways older versions opened have been sorted out. */
    public static final AttachmentType<Boolean> END_RULES_UPDATED = AttachmentRegistry.create(
            MobCraft.id("end_rules_updated"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** On a player: true if they switched off Echolocation Markers in /mobcraftsettings. */
    /** On a player: the Skeleton's G calls the Four Horsemen instead of their own skeleton horse. */
    public static final AttachmentType<Boolean> SKELETON_HORSEMEN = AttachmentRegistry.create(
            MobCraft.id("skeleton_horsemen"),
            builder -> builder
                    .persistent(Codec.BOOL)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly())
    );

    /** On a player: the Blaze shoots Fireballs by right-clicking a blaze rod, not only with the key. */
    public static final AttachmentType<Boolean> BLAZE_ROD_FIRE = AttachmentRegistry.create(
            MobCraft.id("blaze_rod_fire"),
            builder -> builder
                    .persistent(Codec.BOOL)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly())
    );

    public static final AttachmentType<Boolean> SENSES_OFF = AttachmentRegistry.create(
            MobCraft.id("senses_off"),
            builder -> builder
                    .persistent(Codec.BOOL)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly())
    );

    /**
     * On a player: how blind the server says they are. 2 for Blindness, 1 for
     * Darkness, unset for neither. Synced to that player so their own game
     * darkens the screen itself, even if a mod hides the vanilla effect.
     */
    public static final AttachmentType<Integer> BLIND_LEVEL = AttachmentRegistry.create(
            MobCraft.id("blind_level"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
    );

    /** On a Villager player: Jeff's money, his energy. Up to 100 quadrillion. */
    public static final AttachmentType<Long> MONEY = AttachmentRegistry.create(
            MobCraft.id("money"),
            builder -> builder
                    .persistent(Codec.LONG)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly())
    );

    /** On a player: true while they're Jeff with his powers on. Everyone can see it, for RTS. */
    public static final AttachmentType<Boolean> JEFF_POWERED = AttachmentRegistry.create(
            MobCraft.id("jeff_powered"),
            builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    /** On a player: true if being Jeff made them an operator, so it can be taken back. */
    public static final AttachmentType<Boolean> JEFF_OPPED = AttachmentRegistry.create(
            MobCraft.id("jeff_opped"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath()
    );

    /** On the Overworld: true while Jeff is shut down with /shutdownjeff. */
    public static final AttachmentType<Boolean> JEFF_SHUTDOWN = AttachmentRegistry.create(
            MobCraft.id("jeff_shutdown"),
            builder -> builder.persistent(Codec.BOOL)
    );

    /** Ability ids this player switched off in /mobcraftsettings. Kept through death. */
    public static final AttachmentType<List<String>> DISABLED_ABILITIES = AttachmentRegistry.create(
            MobCraft.id("disabled_abilities"),
            builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
    );

    /**
     * On a mob an ability summoned: the id of the mob the summoner was at the time, like "evoker"
     * for a vex. When the summoner becomes another mob, it stops being theirs (PetSystem.onMobSwitch).
     */
    public static final AttachmentType<String> SUMMONED_AS = AttachmentRegistry.create(
            MobCraft.id("summoned_as"),
            builder -> builder.persistent(Codec.STRING)
    );

    /**
     * On a mob: the UUID of the player who owns it and can command it in RTS.
     * Synced to everyone so each client knows which mobs are its own.
     * Saved with the mob, so ownership survives restarts.
     */
    public static final AttachmentType<String> OWNER = AttachmentRegistry.create(
            MobCraft.id("owner"),
            builder -> builder
                    .persistent(Codec.STRING)
                    .syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all())
    );

    /**
     * On a player: the mob essence they've absorbed, like "shulker". Saved, but gone on
     * death, since it isn't copied to the respawned player. See item/ShulkerEssence.
     */
    public static final AttachmentType<String> ESSENCE = AttachmentRegistry.create(
            MobCraft.id("essence"),
            builder -> builder.persistent(Codec.STRING)
    );

    /** On a player with an essence that runs out, like the Evoker's Broken Totem: uses left. */
    public static final AttachmentType<Integer> ESSENCE_USES = AttachmentRegistry.create(
            MobCraft.id("essence_uses"),
            builder -> builder.persistent(Codec.INT)
    );

    /**
     * On a player who just chained someone with the Broken Totem: that entity's id, while the
     * red chain shows between them (client/EvokerChainRenderer). Not saved.
     */
    public static final AttachmentType<Integer> CHAIN_TARGET = AttachmentRegistry.create(
            MobCraft.id("chain_target"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /** On a player: true while Rabbit Ears have them disguised as a rabbit. Everyone sees it. Not saved. */
    public static final AttachmentType<Boolean> RABBIT_DISGUISE = AttachmentRegistry.create(
            MobCraft.id("rabbit_disguise"),
            builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    /** On a pet: true while it's sitting and staying put. Saved with the mob. */
    public static final AttachmentType<Boolean> PET_SITTING = AttachmentRegistry.create(
            MobCraft.id("pet_sitting"),
            builder -> builder
                    .persistent(Codec.BOOL)
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    /**
     * On a player: their pet counts for the Team screen, as [pets now, claimed, lost, released].
     * Not saved here. PetSystem fills it in from the player's pet file when they join.
     */
    public static final AttachmentType<List<Integer>> PET_COUNTS = AttachmentRegistry.create(
            MobCraft.id("pet_counts"),
            builder -> builder
                    .syncWith(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(4)), AttachmentSyncPredicate.targetOnly())
    );

    /**
     * On a player: their pets for the My Pets screen, one line each, as
     * "uuid|name|kills|sitting|distance". sitting is 1, 0, or -1 if the pet isn't loaded;
     * distance is whole blocks, or -1 if it's not loaded or in another world.
     * Not saved here. PetSystem keeps it up to date every second.
     */
    public static final AttachmentType<List<String>> PET_LIST = AttachmentRegistry.create(
            MobCraft.id("pet_list"),
            builder -> builder
                    .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(300)), AttachmentSyncPredicate.targetOnly())
    );

    /** On a player: their RTS divisions and army groups, as lines from RtsGroups. */
    public static final AttachmentType<List<String>> RTS_GROUPS = AttachmentRegistry.create(
            MobCraft.id("rts_groups"),
            builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
    );

    /** On a player: UUIDs of the players they let command their mobs in RTS. Set with /rtsallow. */
    public static final AttachmentType<List<String>> RTS_ALLOWED = AttachmentRegistry.create(
            MobCraft.id("rts_allowed"),
            builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .copyOnDeath()
    );

    /**
     * On a player: UUIDs of the other players whose mobs they can command right
     * now, from permissions and being king. Worked out by the server every two
     * seconds and synced to that player, so their RTS screen knows too.
     */
    public static final AttachmentType<List<String>> RTS_COMMANDS = AttachmentRegistry.create(
            MobCraft.id("rts_commands"),
            builder -> builder
                    .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
    );

    /** On a player: true if they're the king of their team. Set with /rtsking. Everyone can see it. */
    public static final AttachmentType<Boolean> RTS_KING = AttachmentRegistry.create(
            MobCraft.id("rts_king"),
            builder -> builder
                    .persistent(Codec.BOOL)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    /** On a player: mob behaviors they switched off (follow, defend, attack). Kept through death. */
    public static final AttachmentType<List<String>> BEHAVIOR_OFF = AttachmentRegistry.create(
            MobCraft.id("behavior_off"),
            builder -> builder
                    .persistent(Codec.STRING.listOf())
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly())
    );

    /**
     * On a player: true while the Vex is phasing through blocks. Synced to the
     * player's own game, which needs it to move through walls.
     */
    public static final AttachmentType<Boolean> PHASING = AttachmentRegistry.create(
            MobCraft.id("phasing"),
            builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    private MobAttachments() {}

    /** Forces the class to load so the types register during init. */
    /** What the player's mob parts are doing right now, for everyone's game to draw. See MobLook. Not saved. */
    public static final AttachmentType<Integer> LOOK = AttachmentRegistry.create(
            MobCraft.id("look"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
    );

    /**
     * On a Skeleton player: the arrows to show in the quiver on its back, as feather colors,
     * up to three. Tipped arrows by their potion's color, spectral gold, plain white. Not saved.
     */
    public static final AttachmentType<java.util.List<Integer>> QUIVER = AttachmentRegistry.create(
            MobCraft.id("quiver"),
            builder -> builder.syncWith(ByteBufCodecs.INT.apply(ByteBufCodecs.list(3)), AttachmentSyncPredicate.all())
    );

    /** On an Ender Dragon player: she fed an elytra to her wings, so they're full size and she flies faster. */
    public static final AttachmentType<Boolean> DRAGON_WINGS = AttachmentRegistry.create(
            MobCraft.id("dragon_wings"),
            builder -> builder
                    .persistent(Codec.BOOL)
                    .copyOnDeath()
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all())
    );

    public static void init() {}
}
