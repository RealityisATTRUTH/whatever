package com.reality.mobcraft.item;

import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.kit.Cooldowns;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Rabbit Ears: the Rabbit's mob item, crafted onto a helmet like the bat ears. The id is still
 * lucky_paw, from when it was the Lucky Paw on boots; boots that already have one keep that.
 *
 * Press the Armor Power key (Z) to turn into a rabbit, and again to turn back:
 *   Everyone sees a plain rabbit: no player, no name tag, no armor (client/RabbitDisguise).
 *   You shrink to a rabbit's size and hop as you move.
 *   Mobs ignore you: none of them will target you while you're a rabbit (SkullVisorTargetMixin).
 * Hitting anything or getting hurt turns you back, and then you can't hide again for 10 seconds.
 * Taking the helmet off turns you back too.
 */
public final class RabbitEars {

    private static final String COOLDOWN = "rabbit_ears";
    private static final int BROKEN_COOLDOWN = 200;     // 10 seconds after being found out
    private static final int TOGGLE_COOLDOWN = 40;      // 2 seconds after turning back yourself
    private static final double SIZE = -0.72;           // a rabbit's size: about half a block tall
    private static final String SIZE_MODIFIER = "rabbit_ears_disguise";

    private RabbitEars() {}

    public static void register() {
        // Getting hurt gives you away.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, damage, blocked) -> {
            if (victim instanceof ServerPlayer player && hidden(player) && damage > 0) {
                reveal(player, BROKEN_COOLDOWN, "You got hurt and lost your disguise.");
            }
            if (source.getEntity() instanceof ServerPlayer attacker && hidden(attacker)) {
                reveal(attacker, BROKEN_COOLDOWN, "You attacked and gave yourself away.");
            }
        });
        // So does swinging at anything.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player instanceof ServerPlayer sp && hidden(sp)) {
                reveal(sp, BROKEN_COOLDOWN, "You attacked and gave yourself away.");
            }
            return InteractionResult.PASS;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (hidden(handler.player)) reveal(handler.player, 0, null);
        });
    }

    /** Disguised as a rabbit right now. */
    public static boolean hidden(LivingEntity entity) {
        return Boolean.TRUE.equals(entity.getAttached(MobAttachments.RABBIT_DISGUISE));
    }

    private static boolean wearing(ServerPlayer player) {
        return ArmorUpgrades.LUCKY_PAW.equals(player.getItemBySlot(EquipmentSlot.HEAD).get(ModItems.ARMOR_UPGRADE));
    }

    /** The Armor Power key with Rabbit Ears on the helmet. */
    public static void toggle(ServerPlayer player) {
        if (hidden(player)) {
            reveal(player, TOGGLE_COOLDOWN, "You turn back.");
            return;
        }
        int left = Cooldowns.remaining(player, COOLDOWN);
        if (left > 0) {
            KitUtil.actionBar(player, "You can hide again in " + KitUtil.seconds(left) + ".");
            return;
        }
        player.setAttached(MobAttachments.RABBIT_DISGUISE, true);
        KitUtil.modifier(player, Attributes.SCALE, SIZE_MODIFIER, SIZE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, true);
        poof(player);
        // Whatever was after you loses track of you.
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48), m -> m.getTarget() == player)) {
            KitUtil.calm(mob, player);
        }
        KitUtil.actionBar(player, "You're a rabbit. Mobs ignore you. Press the Armor Power key to turn back.");
    }

    /** Back to yourself, with a cooldown before you can hide again. */
    public static void reveal(ServerPlayer player, int cooldown, String message) {
        player.removeAttached(MobAttachments.RABBIT_DISGUISE);
        KitUtil.modifier(player, Attributes.SCALE, SIZE_MODIFIER, SIZE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        if (cooldown > 0) Cooldowns.start(player, COOLDOWN, cooldown);
        poof(player);
        if (message != null) KitUtil.actionBar(player, message);
    }

    /** Every tick, from ArmorUpgrades: took the ears off, so the disguise goes. */
    public static void tick(ServerPlayer player) {
        if (hidden(player) && (!wearing(player) || !player.isAlive() || player.isSpectator())) {
            reveal(player, TOGGLE_COOLDOWN, "Without the ears you can't stay a rabbit.");
        } else if (!hidden(player) && player.tickCount % 20 == 0) {
            // Respawning or relogging drops the disguise; make sure the size goes with it.
            KitUtil.modifier(player, Attributes.SCALE, SIZE_MODIFIER, SIZE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, false);
        }
    }

    private static void poof(ServerPlayer player) {
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.3, player.getZ(), 14, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.RABBIT_JUMP, SoundSource.PLAYERS, 1f, 1.2f);
    }
}
