package dev.reality.fightbot.flight;

import dev.reality.fightbot.core.Json;

/**
 * One tick of what the server reported about one player. Anything the server did not report is
 * left missing: NaN for numbers, null for yes or no values, -1 for counts and slots, null for item names.
 * Nothing here is guessed.
 */
public record FlightSample(
        long tick,
        long timeMs,
        /** False when the player was not found this tick. Every other field is then missing. */
        boolean present,
        double x, double y, double z,
        float yaw, float pitch,
        double vx, double vy, double vz,
        /** "server" when the server's own velocity is trusted, "position" when worked out from positions, or "none". */
        String velocitySource,
        Boolean onGround,
        Boolean gliding,
        double fallDistance,
        Boolean horizontalCollision,
        Boolean verticalCollision,
        int selectedSlot,
        String mainHand,
        String chest,
        int fireworks,
        int windCharges,
        /** Uses left on a worn elytra, or -1. */
        int elytraDurability,
        /** Blocks between the feet and the first solid surface below, or NaN if none within the scan. */
        double groundClearance,
        /** Solid blocks in the 3 by 3 by 3 space around the player, or -1. */
        int solidNearby,
        float health
) {
    public static FlightSample missing(long tick, long timeMs) {
        return new FlightSample(tick, timeMs, false, Double.NaN, Double.NaN, Double.NaN, Float.NaN, Float.NaN,
                Double.NaN, Double.NaN, Double.NaN, "none", null, null, Double.NaN, null, null, -1, null, null,
                -1, -1, -1, Double.NaN, -1, Float.NaN);
    }

    public boolean hasPosition() {
        return present && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public boolean hasVelocity() {
        return present && Double.isFinite(vx) && Double.isFinite(vy) && Double.isFinite(vz);
    }

    public double horizontalSpeed() {
        return hasVelocity() ? Math.hypot(vx, vz) : Double.NaN;
    }

    public double speed() {
        return hasVelocity() ? Math.sqrt(vx * vx + vy * vy + vz * vz) : Double.NaN;
    }

    public String toJson(MovementState state) {
        return new Json().put("tick", tick).put("timeMs", timeMs).put("present", present).put("state", state.name())
                .put("x", x, 4).put("y", y, 4).put("z", z, 4).put("yaw", yaw, 2).put("pitch", pitch, 2)
                .put("vx", vx, 5).put("vy", vy, 5).put("vz", vz, 5).put("velocitySource", velocitySource)
                .put("onGround", onGround).put("gliding", gliding).put("fallDistance", fallDistance, 3)
                .put("horizontalCollision", horizontalCollision).put("verticalCollision", verticalCollision)
                .put("selectedSlot", selectedSlot).put("mainHand", mainHand).put("chest", chest)
                .put("fireworks", fireworks).put("windCharges", windCharges).put("elytraDurability", elytraDurability)
                .put("groundClearance", groundClearance, 3).put("solidNearby", solidNearby).put("health", health, 2).build();
    }
}
