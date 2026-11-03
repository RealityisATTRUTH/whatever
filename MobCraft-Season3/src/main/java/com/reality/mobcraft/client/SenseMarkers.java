package com.reality.mobcraft.client;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.net.GhostSyncPayload.Ghost;
import com.reality.mobcraft.warden.WardenEcholocation;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Scent markers: red waves rising from where each creature was 10 seconds
 * ago, drawn on top of the screen so they show through walls. Vibrations
 * are the game's own vibration particle flying to you instead (ClientGhosts).
 * The cyan version is also used by BlindOverlay. They get smaller with distance.
 *
 * Used by the Warden's Echolocation and Sniffing, and by helmets with bat
 * ears or warden tendrils. Only the player sensing them sees them.
 */
public final class SenseMarkers {

    static final Identifier VIBRATION = MobCraft.id("textures/gui/vibration.png");
    private static final Identifier SCENT = MobCraft.id("textures/gui/scent.png");
    private static final int FRAME = 18;          // each frame is 18x18
    private static final int FRAMES = 7;          // stacked top to bottom
    private static final long FRAME_MILLIS = 50;  // one frame a tick, like the original

    private SenseMarkers() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, MobCraft.id("sense_markers"), SenseMarkers::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        var ghosts = ClientGhosts.current(client);
        if (ghosts.isEmpty()) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        WorldToScreen view = new WorldToScreen(client, width, height);
        int frame = frame();

        for (Ghost ghost : ghosts) {
            // Vibrations fly to you as particles (ClientGhosts). Scent stays here.
            if (ghost.color() != WardenEcholocation.RED) continue;
            // The waves rise from the middle of where the creature stood.
            WorldToScreen.Point p = view.project(ghost.x(), ghost.y() + 1.0, ghost.z());
            if (p == null) continue;
            Identifier texture = ghost.color() == WardenEcholocation.RED ? SCENT : VIBRATION;
            draw(graphics, texture, p, width, height, frame);
        }
    }

    /** The animation frame for right now. */
    static int frame() {
        return (int) ((System.currentTimeMillis() / FRAME_MILLIS) % FRAMES);
    }

    /** One marker, centered on the point and smaller the further away it is. */
    static void draw(GuiGraphicsExtractor graphics, Identifier texture, WorldToScreen.Point p,
                     int width, int height, int frame) {
        int size = (int) Math.max(10, Math.min(36, 220 / p.depth()));
        int x = (int) p.x() - size / 2;
        int y = (int) p.y() - size / 2;
        if (x < -size || y < -size || x > width || y > height) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, frame * FRAME, size, size,
                FRAME, FRAME, FRAME, FRAME * FRAMES);
    }

    /**
     * One marker at a given size in pixels, turned so the top of the waves points
     * along the screen direction (dx, dy), the way the game turns its vibration
     * particle to face where it's going.
     */
    static void drawTurned(GuiGraphicsExtractor graphics, Identifier texture, double x, double y,
                           double dx, double dy, int size, int frame) {
        // The waves rise toward the top of the texture, so turn "up" onto (dx, dy).
        float angle = (dx == 0 && dy == 0) ? 0f : (float) Math.atan2(dx, -dy);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().rotate(angle);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, -size / 2, -size / 2, 0, frame * FRAME, size, size,
                FRAME, FRAME, FRAME, FRAME * FRAMES);
        graphics.pose().popMatrix();
    }
}
