package dev.reality.fightbot.flight;

/** The movement states the estimator can report. */
public enum MovementState {
    /** On the ground. */
    GROUNDED,
    /** In the air, not gliding, rising or hanging. */
    AIRBORNE,
    /** Elytra open. */
    GLIDING,
    /** In the air, not gliding, falling faster than the descending threshold. */
    DESCENDING,
    /** The first ticks after touching down from a real fall or glide. */
    LANDING,
    /** After a crash, a hard landing, or taking damage, until things settle. */
    RECOVERY,
    /** Not enough data to tell: the player is missing or key fields were not reported. */
    UNKNOWN
}
