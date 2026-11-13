package com.reality.mobcraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.client.rts.RtsScreen;
import com.reality.mobcraft.mob.MobKit;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.net.AbilityPayload;
import com.reality.mobcraft.net.ArmorPowerPayload;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The three MobCraft ability keys, default R, G and V, the Ultimate on U,
 * plus Move Energy Bar on H and RTS mode on J. All rebindable in Controls under the MobCraft category. Sneak while
 * pressing an ability key for alternate versions, like the Evoker's Fang Surround.
 */
public final class AbilityKeys {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MobCraft.id("abilities"));

    private static final KeyMapping[] KEYS = new KeyMapping[3];

    /** Opens the energy bar editor. */
    private static KeyMapping moveHud;

    /** Opens and closes RTS mode. */
    private static KeyMapping rts;

    /** Uses the power of upgraded armor, like the Ender Pearl Shard's teleport. */
    private static KeyMapping armorPower;

    /** Fires your mob's ultimate once it's charged. */
    private static KeyMapping ultimate;

    /** Opens Team & Pets. */
    private static KeyMapping team;

    /** Uses an absorbed mob essence, like the Shulker Essence's shell. */
    private static KeyMapping essence;

    /** Shadow Crush, for players who absorbed a Shadow Shard. */
    private static KeyMapping shadowCrush;

    public static KeyMapping rtsKey() {
        return rts;
    }

    private AbilityKeys() {}

    public static void register() {
        KEYS[MobKit.ABILITY_1] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.ability_1", InputConstants.KEY_R, CATEGORY));
        KEYS[MobKit.ABILITY_2] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.ability_2", InputConstants.KEY_G, CATEGORY));
        KEYS[MobKit.ABILITY_3] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.ability_3", InputConstants.KEY_V, CATEGORY));
        moveHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.move_hud", InputConstants.KEY_H, CATEGORY));
        rts = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.rts", InputConstants.KEY_J, CATEGORY));
        armorPower = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.armor_power", InputConstants.KEY_Z, CATEGORY));
        ultimate = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.ultimate", InputConstants.KEY_U, CATEGORY));
        team = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.team", InputConstants.KEY_Y, CATEGORY));
        essence = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.essence", InputConstants.KEY_B, CATEGORY));
        shadowCrush = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.mobcraft.shadow_crush", InputConstants.KEY_X, CATEGORY));
    }

    /** Called every client tick. Sends one packet per press. */
    public static void tick(Minecraft client) {
        while (moveHud.consumeClick()) {
            if (client.player != null) client.gui.setScreen(new HudEditorScreen());
        }
        while (team.consumeClick()) {
            if (client.player != null) client.gui.setScreen(new TeamScreen(null));
        }
        while (rts.consumeClick()) {
            if (client.player != null) client.gui.setScreen(new RtsScreen());
        }

        // Armor powers work for everyone, with or without a mob.
        while (essence.consumeClick()) {
            if (client.player != null) ClientPlayNetworking.send(new com.reality.mobcraft.net.EssencePayload());
        }
        while (shadowCrush.consumeClick()) {
            if (client.player != null && com.reality.mobcraft.item.Shadow.has(client.player))
                ClientPlayNetworking.send(new com.reality.mobcraft.net.ShadowCrushPayload());
        }
        while (armorPower.consumeClick()) {
            if (client.player != null) ClientPlayNetworking.send(new ArmorPowerPayload(client.player.isShiftKeyDown()));
        }

        while (ultimate.consumeClick()) {
            if (client.player == null || PlayerMob.get(client.player) == null) continue;
            ClientPlayNetworking.send(new AbilityPayload(com.reality.mobcraft.mob.Ultimate.SLOT, client.player.isShiftKeyDown()));
        }

        for (int slot = 0; slot < KEYS.length; slot++) {
            while (KEYS[slot].consumeClick()) {
                if (client.player == null || PlayerMob.get(client.player) == null) continue;
                ClientPlayNetworking.send(new AbilityPayload(slot, client.player.isShiftKeyDown()));
            }
        }
    }
}
