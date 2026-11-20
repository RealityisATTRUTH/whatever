package com.reality.mobcraft.client;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.net.TamperPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The mod's own blindness. When the server says a player is blind or in
 * Darkness (BlindSync), their screen is darkened here, on top of the world,
 * with a little light left in the middle. It doesn't come from the vanilla
 * effect, so mods that remove Blindness or Darkness don't clear it.
 *
 * Anything glowing on your screen, like a cursed Bat's echolocation, sends a
 * vibration flying to you over the darkness, so you can tell where it is.
 *
 * It also checks that the Blindness or Darkness the server gave you still
 * counts. Mods like No Blind make the game pretend you don't have it; that
 * gets reported to the server, which kicks the player (ClientCheck).
 */
public final class BlindOverlay {

    private static final Identifier BLIND = MobCraft.id("textures/gui/blind_overlay.png");
    private static final Identifier DARKNESS = MobCraft.id("textures/gui/darkness_overlay.png");
    private static final int TEXTURE_SIZE = 256;
    private static final double MARKER_RANGE = 32.0;

    private BlindOverlay() {}

    /**
     * Drawn first, under the whole HUD, so the hotbar, health and energy bar
     * still show on top. Added with addFirst, which nothing hides: not F1, not
     * third person, not mods that switch off the vanilla screen overlays.
     */
    public static void register() {
        HudElementRegistry.addFirst(MobCraft.id("blind_overlay"), (graphics, tickCounter) -> extract(graphics));
    }

    /** What the server says: 2 blind, 1 darkness, 0 neither. */
    public static int serverLevel(Minecraft client) {
        if (client.player == null) return 0;
        Integer synced = client.player.getAttached(MobAttachments.BLIND_LEVEL);
        return synced == null ? 0 : synced;
    }

    /**
     * 2 blind, 1 darkness, 0 neither. Whichever is stronger of what the server
     * says and what's in this game's own effect list. The list is read directly,
     * so mods that make the game pretend the effect isn't there don't change it.
     */
    public static int level(Minecraft client) {
        if (client.player == null || client.player.isSpectator() || client.player.isCreative()) return 0;
        Integer synced = client.player.getAttached(MobAttachments.BLIND_LEVEL);
        int level = synced == null ? 0 : synced;
        var effects = client.player.getActiveEffectsMap();
        if (effects.containsKey(MobEffects.BLINDNESS)) level = Math.max(level, 2);
        else if (effects.containsKey(MobEffects.DARKNESS)) level = Math.max(level, 1);
        // A cursed Bat is blind, full stop. This game knows its own mob and curse
        // without asking about effects at all, so no effect-hiding mod changes it.
        if (PlayerMob.has(client.player, MobType.BAT) && PlayerMob.isCursed(client.player)) level = 2;
        return level;
    }

    private static int suspicious = 0;
    private static boolean reported = false;

    /**
     * Called every client tick: the tamper check. Catches both ways those mods work.
     *
     *   No Blind            makes "do I have Blindness?" answer no, while the
     *                       effect is still in this game's effect list.
     *   No Darkness Effect  deletes Darkness from this game's effect list every
     *                       tick, while the server still says you have it.
     *
     * Either way the server is told and kicks the player. Checked once a second,
     * three times in a row, so lag can't set it off.
     */
    public static void tick(Minecraft client) {
        if (client.player == null) {
            suspicious = 0;
            reported = false;
            return;
        }
        if (client.player.tickCount % 20 != 0) return;
        if (client.player.isSpectator() || client.player.isCreative()) {
            suspicious = 0;
            return;
        }
        Integer synced = client.player.getAttached(MobAttachments.BLIND_LEVEL);
        int serverLevel = synced == null ? 0 : synced;
        if (serverLevel == 0) {
            suspicious = 0;
            return;
        }
        Holder<MobEffect> effect = serverLevel >= 2 ? MobEffects.BLINDNESS : MobEffects.DARKNESS;
        boolean inList = client.player.getActiveEffectsMap().containsKey(effect);
        boolean answersNo = !client.player.hasEffect(effect) || client.player.getEffect(effect) == null;
        boolean hooked = inList && answersNo;   // No Blind
        boolean deleted = !inList;               // No Darkness Effect
        suspicious = hooked || deleted ? suspicious + 1 : 0;
        if (suspicious >= 3 && !reported && ClientPlayNetworking.canSend(TamperPayload.TYPE)) {
            reported = true;
            ClientPlayNetworking.send(new TamperPayload(serverLevel >= 2 ? "Blindness" : "Darkness"));
        }
    }

    private static void extract(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        int level = level(client);
        if (level == 0) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        Identifier texture = level >= 2 ? BLIND : DARKNESS;
        // A flat darkening first, so it's dark even if the texture doesn't draw.
        if (level >= 2) graphics.fill(0, 0, width, height, 0xD0000000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, width, height,
                TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        if (level >= 2) {
            // Blind: a second pass makes it near black everywhere but the middle.
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, width, height,
                    TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        } else {
            // Darkness throbs, like the Warden's: darker and lighter every two seconds.
            double wave = (Math.sin(System.currentTimeMillis() / 2000.0 * Math.PI * 2) + 1) / 2;
            int alpha = (int) (60 + wave * 120);
            graphics.fill(0, 0, width, height, alpha << 24);
        }
        drawGlowMarkers(client, graphics, width, height);
    }

    /** One vibration flying from a creature to you. */
    private record Pulse(double x, double y, double z, long start, long duration) {}

    private static final List<Pulse> PULSES = new ArrayList<>();
    /** Entity id -> when it last sent a vibration, in milliseconds. */
    private static final Map<Integer, Long> LAST_PULSE = new HashMap<>();
    private static final long PULSE_EVERY = 1000;
    private static final double VIBRATION_SIZE = 0.6; // blocks across, like the game's vibration particle

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /**
     * Over the darkness, every glowing creature close enough to sense sends a
     * vibration flying to you once a second, like a sculk sensor's signal. It
     * starts at the creature and moves in until it reaches you, so you can tell
     * which way the sound came from.
     */
    private static void drawGlowMarkers(Minecraft client, GuiGraphicsExtractor graphics, int width, int height) {
        if (client.level == null || client.player == null) return;
        long now = System.currentTimeMillis();
        double targetX = client.player.getX();
        double targetY = client.player.getY() + client.player.getBbHeight() * 0.45;
        double targetZ = client.player.getZ();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity == client.player) continue;
            if (!ClientGlow.isGlowing(entity.getId())) continue;
            double distance = Math.sqrt(entity.distanceToSqr(client.player));
            if (distance > MARKER_RANGE) continue;
            Long last = LAST_PULSE.get(entity.getId());
            if (last != null && now - last < PULSE_EVERY) continue;
            LAST_PULSE.put(entity.getId(), now);
            // About a block a tick, like sculk, so far sounds take longer to arrive.
            long duration = (long) Math.max(400, Math.min(2000, distance * 50));
            PULSES.add(new Pulse(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(), now, duration));
        }
        if (LAST_PULSE.size() > 256) LAST_PULSE.clear();

        WorldToScreen view = new WorldToScreen(client, width, height);
        int frame = SenseMarkers.frame();
        Iterator<Pulse> it = PULSES.iterator();
        while (it.hasNext()) {
            Pulse pulse = it.next();
            double t = (now - pulse.start()) / (double) pulse.duration();
            if (t >= 1.0) {
                it.remove();
                continue;
            }
            WorldToScreen.Point p = view.project(lerp(pulse.x(), targetX, t), lerp(pulse.y(), targetY, t), lerp(pulse.z(), targetZ, t));
            if (p == null) continue;
            // A moment further along, to know which way it's heading on screen.
            double ahead = Math.min(0.999, t + 0.05);
            WorldToScreen.Point q = view.project(lerp(pulse.x(), targetX, ahead), lerp(pulse.y(), targetY, ahead), lerp(pulse.z(), targetZ, ahead));
            double dx = q == null ? 0 : q.x() - p.x();
            double dy = q == null ? 0 : q.y() - p.y();
            // The same size in the world as the game's vibration particle: about 0.6 blocks across.
            int size = (int) Math.max(2, Math.min(64, VIBRATION_SIZE * view.pixelsPerBlock(p.depth())));
            if (p.x() < -size || p.y() < -size || p.x() > width + size || p.y() > height + size) continue;
            SenseMarkers.drawTurned(graphics, SenseMarkers.VIBRATION, p.x(), p.y(), dx, dy, size, frame);
        }
    }
}
