package dev.reality.fightbot;

import dev.reality.fightbot.flight.FlightSample;
import dev.reality.fightbot.flight.FlightSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * Scripted elytra practice for a Carpet fake player, for checking the flight module against the real
 * game. Each trial starts on the ground, jumps, opens the elytra, fires one rocket at a set pitch, and
 * glides at that pitch until it lands. Trials cycle through pitches and with or without a rocket, and
 * each trial is saved as its own episode with its starting conditions in the label.
 */
final class FlightPractice {
    enum Step { PREP, JUMP, OPEN, GLIDE, REST, DONE }

    private static final float[] PITCHES = {-60, -40, -20, 0, 20};

    final String bot;
    private final int trials;
    private final MinecraftServer server;
    private final Consumer<String> announce;
    private int trial;
    private Step step = Step.PREP;
    private int wait;
    private int stepTicks;
    private FlightSession session;
    private final double[] last = {Double.NaN, 0, 0};
    private float pitch, yaw;
    private boolean rocket;
    boolean finished;

    FlightPractice(MinecraftServer server, String bot, int trials, Consumer<String> announce) {
        this.server = server;
        this.bot = bot;
        this.trials = trials;
        this.announce = announce;
    }

    void tick() {
        if (finished) return;
        ServerPlayer p = server.getPlayerList().getPlayerByName(bot);
        long tick = server.getTickCount();
        if (p == null) {
            if (session != null) {
                // Missing ticks go in as missing samples, so a disconnect is recorded the same way as in a watch.
                if (!session.add(FlightSample.missing(tick, System.currentTimeMillis()))) {
                    announce.accept(FlightWatchers.save(session));
                    finished = true;
                }
            } else {
                announce.accept("Flight practice stopped: " + bot + " is not online.");
                finished = true;
            }
            return;
        }
        stepTicks++;
        if (wait > 0) {
            wait--;
            if (session != null) session.add(FlightWatchers.sampleFor(p, tick, last));
            return;
        }
        switch (step) {
            case PREP -> {
                if (trial >= trials) {
                    announce.accept("Flight practice finished: " + trials + " trials.");
                    finished = true;
                    step = Step.DONE;
                    return;
                }
                if (!p.onGround()) return;
                if (!BotController.id(p.getItemBySlot(EquipmentSlot.CHEST)).equals("elytra") && !wearElytra(p)) {
                    announce.accept("Flight practice stopped: " + bot + " has no elytra.");
                    finished = true;
                    return;
                }
                pitch = PITCHES[trial % PITCHES.length];
                rocket = (trial / PITCHES.length) % 2 == 0;
                yaw = p.getYRot();
                last[0] = Double.NaN;
                session = FlightWatchers.practiceSession(bot, "pitch" + (int) pitch + (rocket ? "_rocket" : "_norocket") + "_trial" + (trial + 1));
                session.add(FlightWatchers.sampleFor(p, tick, last));
                step = Step.JUMP;
                stepTicks = 0;
            }
            case JUMP -> {
                look(p, 0);
                session.commanded("jump", tick);
                run("player " + bot + " jump once");
                session.add(FlightWatchers.sampleFor(p, tick, last));
                step = Step.OPEN;
                stepTicks = 0;
            }
            case OPEN -> {
                look(p, pitch);
                if (!p.onGround() && !p.isFallFlying()) {
                    session.commanded("glide_start", tick);
                    p.tryToStartFallFlying();
                    if (rocket) fireRocket(p, tick);
                    step = Step.GLIDE;
                    stepTicks = 0;
                } else if (stepTicks > 20) {
                    // The jump never left the ground.
                    end(p, tick, "jump failed");
                    return;
                }
                session.add(FlightWatchers.sampleFor(p, tick, last));
            }
            case GLIDE -> {
                look(p, pitch);
                session.add(FlightWatchers.sampleFor(p, tick, last));
                if ((p.onGround() && stepTicks > 3) || stepTicks > 600) end(p, tick, p.onGround() ? "landed" : "time limit");
            }
            case REST -> {
                session.add(FlightWatchers.sampleFor(p, tick, last));
                session.end("trial over");
                announce.accept(FlightWatchers.save(session));
                session = null;
                trial++;
                step = Step.PREP;
                wait = 20;
            }
            case DONE -> finished = true;
        }
    }

    void stop() {
        if (session != null) {
            session.end("stopped");
            announce.accept(FlightWatchers.save(session));
        }
        finished = true;
    }

    private void end(ServerPlayer p, long tick, String why) {
        step = Step.REST;
        wait = 10;
        announce.accept(bot + " trial " + (trial + 1) + ": " + why + ".");
    }

    private void fireRocket(ServerPlayer p, long tick) {
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            if (BotController.id(inv.getItem(i)).equals("firework_rocket")) {
                ServerMoves.select(p, i);
                session.commanded("firework", tick);
                run("player " + bot + " use once");
                return;
            }
        }
        announce.accept(bot + " has no fireworks in the hotbar, so this trial glides without a rocket.");
    }

    private boolean wearElytra(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (BotController.id(inv.getItem(i)).equals("elytra")) {
                ItemStack worn = p.getItemBySlot(EquipmentSlot.CHEST).copy();
                p.setItemSlot(EquipmentSlot.CHEST, inv.getItem(i).copy());
                inv.setItem(i, worn);
                return true;
            }
        }
        return false;
    }

    private void look(ServerPlayer p, float pitchNow) {
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setXRot(pitchNow);
    }

    private void run(String cmd) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }
}
