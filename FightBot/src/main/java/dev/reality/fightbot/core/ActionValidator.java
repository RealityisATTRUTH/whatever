package dev.reality.fightbot.core;

/**
 * Last check before inputs go to the game. It drops anything the bot cannot do right now and
 * counts each drop, so a policy that keeps proposing impossible actions shows up in the results.
 */
public final class ActionValidator {
    public long rejectedAttacks, rejectedUses, rejectedJumps, rejectedBlocks, staleTicks;
    private final BotProfile profile;

    public ActionValidator(BotProfile profile) {
        this.profile = profile;
    }

    /**
     * True when the opponent's hitbox is within reach of the bot's eyes, measured to the closest point of the
     * box for both players' current poses, against the smaller of the profile's reach and the game's own.
     */
    public boolean inReach(Observation o) {
        return o.reachToTarget() <= Math.min(profile.reach, o.loadout().entityReach);
    }

    public BotAction check(Observation o, BotAction a) {
        if (o.stale() || !o.hasOpponent()) {
            staleTicks++;
            BotAction idle = BotAction.idle(o.yaw(), o.pitch());
            idle.state = o.hasOpponent() ? "STALE" : "IDLE";
            return idle;
        }
        if (a.attack && (a.technique.equals("smash") || a.technique.startsWith("stun_slam"))) {
            // Mace smashes add their bonus at any charge. The fall must be real and the elytra closed.
            boolean smashOk = a.technique.equals("stun_slam_axe") || (o.fallDistance() > 1.5 && !o.gliding());
            boolean aimed = o.distance() < 1.0 || Angles.diff(a.yaw, o.yawToOpponent()) <= profile.maxAimError * 1.5;
            if (!(smashOk && MacePolicy.inReachSoon(o) && aimed && o.lineOfSight())) {
                a.attack = false;
                rejectedAttacks++;
            }
            return finishChecks(o, a);
        }
        if (a.attack && a.technique.equals("shield_break")) {
            // An axe disables a raised shield at any charge, so only reach and aim matter.
            if (!(inReach(o) && Angles.diff(a.yaw, o.yawToOpponent()) <= profile.maxAimError && o.lineOfSight())) {
                a.attack = false;
                rejectedAttacks++;
            }
            return finishChecks(o, a);
        }
        if (a.attack) {
            boolean ok = o.charge() >= 0 && o.charge() + 1e-9 >= Math.min(a.technique.equals("finish") ? 0.85 : 1, profile.chargeThreshold)
                    && inReach(o) && Angles.diff(a.yaw, o.yawToOpponent()) <= profile.maxAimError && o.lineOfSight()
                    && !o.usingItem() && !a.use;
            if (!ok) {
                a.attack = false;
                rejectedAttacks++;
            }
        }
        return finishChecks(o, a);
    }

    private BotAction finishChecks(Observation o, BotAction a) {
        String selected = a.hotbar >= 0 ? o.loadout().hotbar(a.hotbar) : o.mainHand();
        boolean drinking = selected.startsWith("potion");
        boolean shooting = ((selected.equals("bow") || selected.equals("crossbow"))
                && o.loadout().count(id -> id.equals("arrow") || id.equals("spectral_arrow") || id.startsWith("tipped_arrow")) > 0)
                || selected.equals("trident") || (selected.equals("trident:riptide") && o.loadout().wet);
        if (a.use && !drinking && !shooting && (o.healSlot() < 0 || o.healCount() <= 0) && (o.egapSlot() < 0 || o.egapCount() <= 0)) {
            a.use = false;
            rejectedUses++;
        }
        if (a.block && (!o.hasShield() || a.use || a.attack)) {
            a.block = false;
            rejectedBlocks++;
        }
        Loadout l = o.loadout();
        if (a.equip != null) {
            boolean ok = a.equip.equals("elytra") ? !l.wearingElytra() && l.has("elytra")
                    : a.equip.equals("chestplate") && l.wearingElytra() && l.count(Loadout::isChestplate) > 0;
            if (!ok) a.equip = null;
        }
        boolean elytraOn = a.equip != null ? a.equip.equals("elytra") : l.wearingElytra();
        // Water: no gliding, so no rockets, and wind charges and pearls barely move. None of them work there.
        if (l.inWater) {
            if (a.useOnce) {
                String held = a.hotbar >= 0 ? l.hotbar(a.hotbar) : o.mainHand();
                if (held.equals("firework_rocket") || held.equals("wind_charge")) {
                    a.useOnce = false;
                    rejectedUses++;
                }
            }
            if ("ender_pearl".equals(a.useItem)) {
                a.useItem = null;
                rejectedUses++;
            }
            if (a.startGlide) {
                a.startGlide = false;
                rejectedJumps++;
            }
        }
        if (a.startGlide && (o.onGround() || o.gliding() || !elytraOn)) {
            a.startGlide = false;
            rejectedJumps++;
        }
        if (a.useOnce) {
            String held = a.hotbar >= 0 ? l.hotbar(a.hotbar) : o.mainHand();
            boolean ok = held.equals("firework_rocket") ? (o.gliding() || a.startGlide)
                    : held.equals("wind_charge") ? o.windReady()
                    : held.equals("crossbow:charged") || held.equals("experience_bottle") ? true
                    : held.startsWith("splash_potion") || held.startsWith("lingering_potion");
            if (!ok || a.use || a.block) {
                a.useOnce = false;
                rejectedUses++;
            }
        }
        // Pearls and buckets: only when the item is in the hotbar to switch to, and not while holding right click.
        if (a.useItem != null && (l.slot(a.useItem) < 0 || a.use || a.block || o.usingItem())) {
            a.useItem = null;
            rejectedUses++;
        }
        if (a.swapAttack != null) {
            boolean ok = a.swapAttack.length > 0 && inReach(o) && o.lineOfSight() && !o.gliding() && !a.use && !o.usingItem();
            for (int slot : a.swapAttack) ok &= slot >= 0 && slot < 9 && !l.hotbar(slot).equals("air");
            if (!ok) {
                a.swapAttack = null;
                rejectedAttacks++;
            }
        }
        if (a.jabSlot >= 0 && (!l.canLunge() || a.jabSlot != l.lungeSlot || o.gliding() || o.usingItem())) {
            a.jabSlot = -1;
            rejectedAttacks++;
        }
        if (a.offhandTotem && (!l.offhand.equals("air") || l.totems() <= 0)) a.offhandTotem = false;
        if (a.swapHands && (a.hotbar < 0 || !l.hotbar(a.hotbar).equals("totem_of_undying") || !l.offhand.equals("air"))) {
            a.swapHands = false;
            rejectedUses++;
        }
        if (a.jump && !o.onGround()) {
            a.jump = false;
            rejectedJumps++;
        }
        if (a.use || a.block || o.usingItem()) a.sprint = false;
        if (a.forward <= 0) a.sprint = false;
        return a;
    }
}
