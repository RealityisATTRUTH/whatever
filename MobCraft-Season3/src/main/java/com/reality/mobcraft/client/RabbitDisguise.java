package com.reality.mobcraft.client;

import com.reality.mobcraft.item.RabbitEars;
import com.reality.mobcraft.mixin.client.RabbitAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rabbit Ears on the client (item/RabbitEars). Each disguised player gets a rabbit of their own,
 * never added to the world, that copies where they are and which way they face, and is drawn
 * instead of them: it replaces their entry in the list of things to draw each frame. Its fur matches the biome, like wild rabbits:
 * white in the snow, gold in the desert, brown elsewhere. It does a rabbit's hop whenever its
 * player leaves the ground, and your own disguise hops along as you move.
 */
public final class RabbitDisguise {

    private static final double HOP = 0.3;
    private static final Map<UUID, Rabbit> RABBITS = new HashMap<>();
    private static Level rabbitsLevel;

    private RabbitDisguise() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(RabbitDisguise::tick);
        // Once the world's been gathered up to draw, swap each disguised player for their rabbit.
        LevelExtractionEvents.END_EXTRACTION.register(RabbitDisguise::swap);
    }

    private static void swap(LevelExtractionContext context) {
        List<EntityRenderState> states = context.levelState().entityRenderStates;
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        float partial = context.deltaTracker().getGameTimeDeltaPartialTick(false);
        for (int i = 0; i < states.size(); i++) {
            if (!(states.get(i) instanceof AvatarRenderState avatarState)) continue;
            if (!(context.level().getEntity(avatarState.id) instanceof Avatar avatar)) continue;
            Rabbit rabbit = rabbitFor(avatar);
            if (rabbit != null) states.set(i, dispatcher.extractEntity(rabbit, partial));
        }
    }

    /** The rabbit to draw instead of this player, placed where they are, or null if they aren't disguised. */
    public static Rabbit rabbitFor(Avatar player) {
        if (!RabbitEars.hidden(player)) return null;
        if (player.level() != rabbitsLevel) {
            RABBITS.clear();
            rabbitsLevel = player.level();
        }
        Rabbit rabbit = RABBITS.computeIfAbsent(player.getUUID(), id -> create(player));
        rabbit.setPos(player.getX(), player.getY(), player.getZ());
        rabbit.xo = player.xo; rabbit.yo = player.yo; rabbit.zo = player.zo;
        rabbit.xOld = player.xOld; rabbit.yOld = player.yOld; rabbit.zOld = player.zOld;
        rabbit.setYRot(player.getYRot()); rabbit.yRotO = player.yRotO;
        rabbit.setXRot(player.getXRot()); rabbit.xRotO = player.xRotO;
        rabbit.yBodyRot = player.yBodyRot; rabbit.yBodyRotO = player.yBodyRotO;
        rabbit.yHeadRot = player.yHeadRot; rabbit.yHeadRotO = player.yHeadRotO;
        rabbit.tickCount = player.tickCount;
        rabbit.setOnGround(player.onGround());
        rabbit.setInvisible(player.isInvisible());
        return rabbit;
    }

    private static Rabbit create(Avatar player) {
        Rabbit rabbit = new Rabbit(EntityTypes.RABBIT, player.level());
        rabbit.setId(-1_000_000 - player.getId());   // its own id, so nothing mixes it up with a real entity
        String biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.identifier().getPath()).orElse("");
        Rabbit.Variant fur = biome.contains("snow") || biome.contains("frozen") || biome.contains("ice") ? Rabbit.Variant.WHITE
                : biome.contains("desert") ? Rabbit.Variant.GOLD : Rabbit.Variant.BROWN;
        ((RabbitAccessor) rabbit).mobcraft$setVariant(fur);
        return rabbit;
    }

    /** When each disguised player's rabbit next tilts its head while standing still, in that player's ticks. */
    private static final Map<UUID, Integer> NEXT_TILT = new HashMap<>();

    /**
     * Plays the rabbit's own animations. A rabbit in this version moves its model with keyframe animations,
     * the hop and the idle head tilt, that a real rabbit starts in its own tick. This rabbit is never ticked,
     * so they're started here: the hop for every jump, whether that's hopping along or a real jump, and now
     * and then a curious head tilt while it sits still. Timed off the player's ticks, which the rabbit copies.
     */
    private static void animate(Rabbit rabbit, Player player, boolean hopping) {
        int now = player.tickCount;
        if (hopping) {
            rabbit.hopAnimationState.startIfStopped(now);
            rabbit.idleHeadTiltAnimationState.stop();
            NEXT_TILT.put(player.getUUID(), now + 40 + player.getRandom().nextInt(60));
            return;
        }
        rabbit.hopAnimationState.stop();
        double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
        boolean still = player.onGround() && dx * dx + dz * dz < 1.0E-4;
        int next = NEXT_TILT.computeIfAbsent(player.getUUID(), k -> now + 40 + player.getRandom().nextInt(60));
        if (still && now >= next) {
            rabbit.idleHeadTiltAnimationState.start(now);
            NEXT_TILT.put(player.getUUID(), now + 80 + player.getRandom().nextInt(120));
        }
    }

    private static void tick(Minecraft client) {
        if (client.level == null) {
            RABBITS.clear();
            NEXT_TILT.clear();
            return;
        }
        // Your own disguise hops as you move, like a rabbit.
        LocalPlayer me = client.player;
        if (me != null && RabbitEars.hidden(me) && me.onGround() && (me.xxa != 0 || me.zza != 0)) {
            Vec3 motion = me.getDeltaMovement();
            me.setDeltaMovement(motion.x, HOP, motion.z);
        }
        // Each rabbit hops when its player leaves the ground.
        RABBITS.entrySet().removeIf(entry -> {
            Player player = client.level.getPlayerByUUID(entry.getKey());
            if (player == null || !RabbitEars.hidden(player)) {
                NEXT_TILT.remove(entry.getKey());
                return true;
            }
            Rabbit body = entry.getValue();
            RabbitAccessor rabbit = (RabbitAccessor) body;
            int duration = rabbit.mobcraft$jumpDuration();
            if (duration == 0) {
                if (player.getY() - player.yo > 0.05) {
                    rabbit.mobcraft$setJumpDuration(10);
                    rabbit.mobcraft$setJumpTicks(0);
                }
            } else if (rabbit.mobcraft$jumpTicks() + 1 >= duration && player.onGround()) {
                rabbit.mobcraft$setJumpDuration(0);
                rabbit.mobcraft$setJumpTicks(0);
            } else {
                rabbit.mobcraft$setJumpTicks(rabbit.mobcraft$jumpTicks() + 1);
            }
            animate(body, player, rabbit.mobcraft$jumpDuration() > 0);
            return false;
        });
    }
}
