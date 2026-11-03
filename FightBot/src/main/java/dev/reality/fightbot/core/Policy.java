package dev.reality.fightbot.core;

/** Turns one tick of observation into inputs. Each fighting style has one. */
public interface Policy {
    BotAction step(Observation o);

    String stateName();
}
