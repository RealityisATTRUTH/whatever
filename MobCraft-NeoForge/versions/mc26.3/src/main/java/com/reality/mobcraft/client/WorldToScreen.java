package com.reality.mobcraft.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Finds where a spot in the world shows on the HUD, from the camera's position
 * and facing. Used to draw markers over things on top of everything else,
 * through walls and through the blindness overlay.
 */
public final class WorldToScreen {

    private final double eyeX, eyeY, eyeZ;
    private final double upX, upY, upZ;
    private final double leftX, leftY, leftZ;
    private final double forwardX, forwardY, forwardZ;
    private final double focal;
    private final int width, height;

    /** Screen position and how far in front of the camera it is. */
    public record Point(double x, double y, double depth) {}

    public WorldToScreen(Minecraft client, int guiWidth, int guiHeight) {
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        upX = up.x();
        upY = up.y();
        upZ = up.z();
        leftX = left.x();
        leftY = left.y();
        leftZ = left.z();
        // Forward is left x up.
        forwardX = leftY * upZ - leftZ * upY;
        forwardY = leftZ * upX - leftX * upZ;
        forwardZ = leftX * upY - leftY * upX;
        width = guiWidth;
        height = guiHeight;
        focal = (guiHeight / 2.0) / Math.tan(Math.toRadians(client.options.fov().get()) / 2.0);
    }

    /** How many screen pixels one block covers at this depth. */
    public double pixelsPerBlock(double depth) {
        return focal / Math.max(0.5, depth);
    }

    public Vec3 eye() {
        return new Vec3(eyeX, eyeY, eyeZ);
    }

    /** The direction through a screen point, the reverse of project. Not normalized. */
    public Vec3 ray(double screenX, double screenY) {
        double right = (screenX - width / 2.0) / focal;
        double upward = (height / 2.0 - screenY) / focal;
        return new Vec3(forwardX - leftX * right + upX * upward,
                forwardY - leftY * right + upY * upward,
                forwardZ - leftZ * right + upZ * upward);
    }

    /** Null when the spot is behind the camera. */
    public Point project(double x, double y, double z) {
        double dx = x - eyeX;
        double dy = y - eyeY;
        double dz = z - eyeZ;
        double depth = dx * forwardX + dy * forwardY + dz * forwardZ;
        if (depth < 0.5) return null;
        double right = -(dx * leftX + dy * leftY + dz * leftZ);
        double upward = dx * upX + dy * upY + dz * upZ;
        return new Point(width / 2.0 + right / depth * focal, height / 2.0 - upward / depth * focal, depth);
    }
}
