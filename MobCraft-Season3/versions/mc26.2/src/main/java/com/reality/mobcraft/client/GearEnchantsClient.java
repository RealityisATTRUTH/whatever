package com.reality.mobcraft.client;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.item.GearEnchants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/**
 * The gear enchants that happen on your own screen.
 *
 *   Duskvision    (helmet) while you sneak, living things within 16 blocks glow through walls, only for you
 *   Danger Sense  (helmet) the edge of your screen pulses red on the side a hostile mob is coming from
 *   Double Jump   (boots)  pressing jump in mid-air asks the server for a second jump
 */
public final class GearEnchantsClient {

    private GearEnchantsClient() {}

    private static boolean jumpWasDown;
    /** Which edges are pulsing: left, right, behind. Faded each tick. */
    private static final float[] DANGER = new float[3];

    public static int level(ItemStack stack, ResourceKey<Enchantment> key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || stack.isEmpty()) return 0;
        return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }

    /** Duskvision: called from the outline check, so only this player sees the glow. */
    public static boolean duskvision(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        Player me = mc.player;
        if (me == null || e == me || !(e instanceof LivingEntity) || !me.isShiftKeyDown()) return false;
        if (e.distanceToSqr(me) > 16 * 16) return false;
        return level(me.getItemBySlot(EquipmentSlot.HEAD), GearEnchants.DUSKVISION) > 0;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            var p = mc.player;
            if (p == null) return;
            // Double Jump: a fresh press of jump while already in the air.
            boolean jump = mc.options.keyJump.isDown();
            if (jump && !jumpWasDown && !p.onGround() && !p.isInWater() && !p.getAbilities().flying && !p.isPassenger()
                    && level(p.getItemBySlot(EquipmentSlot.FEET), GearEnchants.DOUBLE_JUMP) > 0) {
                ClientPlayNetworking.send(new com.reality.mobcraft.net.BootsJumpPayload());
            }
            jumpWasDown = jump;
            // Danger Sense: hostile mobs coming at you from outside your view light up that edge.
            for (int i = 0; i < 3; i++) DANGER[i] = Math.max(0, DANGER[i] - 0.08f);
            if (mc.level != null && level(p.getItemBySlot(EquipmentSlot.HEAD), GearEnchants.DANGER_SENSE) > 0) {
                Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 right = new Vec3(-look.z, 0, look.x);
                for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(12), e -> e instanceof Enemy && e.isAlive())) {
                    Vec3 to = e.position().subtract(p.position()).multiply(1, 0, 1);
                    double d = to.length();
                    if (d < 0.5) continue;
                    Vec3 dir = to.scale(1 / d);
                    double front = dir.dot(look);
                    if (front > 0.5) continue;                       // you can already see it
                    Vec3 v = e.getDeltaMovement().multiply(1, 0, 1);
                    boolean coming = v.lengthSqr() < 1e-4 ? d < 6 : v.normalize().dot(dir.scale(-1)) > 0.3;
                    if (!coming) continue;
                    float strength = (float) Mth.clamp(1.2 - d / 12, 0.3, 1.0);
                    int side = front < -0.5 ? 2 : dir.dot(right) > 0 ? 1 : 0;
                    DANGER[side] = Math.max(DANGER[side], strength);
                }
            }
        });
        HudElementRegistry.addLast(MobCraft.id("danger_sense"), (graphics, tick) -> drawDanger(graphics));
    }

    private static void drawDanger(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float pulse = 0.65f + 0.35f * Mth.sin((mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) * 0.5f);
        int band = Math.max(12, w / 14);
        for (int i = 0; i < band; i++) {
            float fall = 1f - i / (float) band;
            if (DANGER[0] > 0) g.fill(i, 0, i + 1, h, red(DANGER[0] * pulse * fall));
            if (DANGER[1] > 0) g.fill(w - i - 1, 0, w - i, h, red(DANGER[1] * pulse * fall));
            if (DANGER[2] > 0) g.fill(0, h - i - 1, w, h - i, red(DANGER[2] * pulse * fall));
        }
    }

    private static int red(float a) {
        int alpha = (int) (Mth.clamp(a, 0f, 1f) * 150);
        return alpha << 24 | 0xD0101A;
    }
}
