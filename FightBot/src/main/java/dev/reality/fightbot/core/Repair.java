package dev.reality.fightbot.core;

/**
 * Mending repairs with bottles o' enchanting. Experience from a bottle repairs damaged Mending gear that is
 * worn or held, two durability per point, so it throws bottles at its own feet while standing still. It starts
 * when the most worn Mending armor piece gets low, keeps going until the gear is nearly full, and only does it
 * with nobody close, unless the gear is about to break. A worn elytra is put on first, since only equipped
 * gear gets repaired, and the chestplate goes back on after.
 */
public final class Repair {
    private final BotProfile p;
    private boolean active, elytraOn;
    private long lastThrow = -1000;
    private String state = "";
    /** Counts for the round's results. */
    public int bottles;

    public Repair(BotProfile p) {
        this.p = p;
    }

    public String stateName() {
        return state;
    }

    /** True when the gear is worn enough to be worth stopping to repair. */
    public boolean wanted(Observation o) {
        Loadout l = o.loadout();
        if (p.repairWithXp != 1 || !l.has("experience_bottle")) return false;
        return l.armorWorst <= p.repairAt || (l.hasElytra() && l.elytraLeft <= p.repairAt);
    }

    /** True when no one is close enough to punish standing still for it, or the gear is about to break. */
    public boolean safe(Observation o) {
        Loadout l = o.loadout();
        boolean critical = l.armorWorst <= p.repairCritical || (l.hasElytra() && l.elytraLeft <= p.repairCritical);
        return !o.hasOpponent() || o.distance() >= p.repairSafeDistance || (critical && o.distance() >= 5);
    }

    /** Returns true when it used the tick to repair. */
    public boolean step(Observation o, BotAction a) {
        Loadout l = o.loadout();
        long t = o.tick();
        boolean armorLow = l.armorWorst < (active ? p.repairTo : p.repairAt + 1e-9);
        boolean elytraLow = l.hasElytra() && l.elytraLeft < (active ? p.repairTo : p.repairAt + 1e-9);
        boolean go = p.repairWithXp == 1 && l.has("experience_bottle") && (armorLow || elytraLow)
                && o.onGround() && !o.gliding() && !l.inWater && safe(o);
        if (!go) {
            if (active) finish(l, a);
            return false;
        }
        active = true;
        state = "REPAIR";
        a.state = "REPAIR";
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
        a.jump = false;
        a.attack = false;
        a.use = false;
        a.block = false;
        // A worn elytra only takes repairs while worn, once the armor is done.
        if (elytraLow && !armorLow && !l.wearingElytra()) {
            a.equip = "elytra";
            elytraOn = true;
            a.technique = "repair_elytra_on";
            return true;
        }
        int slot = l.slot("experience_bottle");
        if (slot < 0) {
            a.restock = "experience_bottle";
            a.technique = "repair_restock";
            return true;
        }
        if (t - lastThrow < p.repairThrowTicks) {
            a.pitch = 90;
            a.technique = "repair_wait";
            return true;
        }
        lastThrow = t;
        a.hotbar = slot;
        a.useOnce = true;
        a.pitch = 90;
        a.technique = "repair_xp";
        bottles++;
        return true;
    }

    private void finish(Loadout l, BotAction a) {
        active = false;
        state = "";
        // The elytra went on only for the repair: the chestplate goes back on.
        if (elytraOn && l.wearingElytra() && l.count(Loadout::isChestplate) > 0 && a.equip == null) a.equip = "chestplate";
        elytraOn = false;
    }
}
