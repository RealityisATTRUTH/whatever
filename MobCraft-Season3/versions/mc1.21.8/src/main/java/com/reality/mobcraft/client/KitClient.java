package com.reality.mobcraft.client;

import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.MobKits;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.net.AbilityPayload;
import com.reality.mobcraft.net.DoubleJumpPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

/** Client side of the mob kits: instant respawn and the Rabbit's left-click escorts. */
public final class KitClient {

    private static boolean respawnSent;
    private static boolean jumpWasDown;

    private KitClient() {}

    public static void register() {
        // Rabbit: left-click while holding a rabbit's foot calls escorts.
        // The server checks the cooldown, so extra clicks just show the timer.
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (clickCount > 0 && PlayerMob.has(player, MobType.RABBIT) && player.getMainHandItem().is(Items.RABBIT_FOOT)) {
                ClientPlayNetworking.send(new AbilityPayload(MobKit.ABILITY_1, player.isShiftKeyDown()));
            }
            return false; // never block the normal swing
        });
    }

    /**
     * Rabbit: a fresh press of jump while in the air asks the server for a
     * midair jump. Holding jump from the ground doesn't count. Keys read as
     * released while a menu is open, so menus never trigger it.
     */
    private static void tickDoubleJump(Minecraft client) {
        LocalPlayer player = client.player;
        boolean down = client.options.keyJump.isDown();
        boolean pressed = down && !jumpWasDown;
        jumpWasDown = down;
        if (!pressed || player == null) return;
        if (!PlayerMob.has(player, MobType.RABBIT)) return;
        if (player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying) return;
        ClientPlayNetworking.send(new DoubleJumpPayload());
    }

    /** Every client tick. Mobs with instant respawn skip the death screen. */
    public static void tick(Minecraft client) {
        tickDoubleJump(client);
        LocalPlayer player = client.player;
        if (player == null || !player.isDeadOrDying()) {
            respawnSent = false;
            return;
        }
        if (respawnSent) return;
        MobType type = PlayerMob.get(player);
        if (type != null && MobKits.get(type).instantRespawn()) {
            respawnSent = true;
            player.respawn();
        }
    }
}
