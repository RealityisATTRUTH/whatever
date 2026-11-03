package dev.reality.fightbot.core;

import java.util.function.Consumer;

/**
 * Picks the fighting style from what the bot carries, and picks again whenever the kit changes,
 * for example when the last firework is used or the elytra is swapped away for good.
 * Mace styles hand ground fighting to the sword style when there is nothing to launch with.
 */
public final class StyleBrain implements Policy {
    private final BotProfile p;
    private final SwordPolicy sword;
    private final MacePolicy elytraMace, windMace;
    private final RiptideLungeMacePolicy riptideLungeMace;
    private final CrystalPolicy crystal;
    private final Consumer<String> announce;
    private Style.Choice choice;
    private String lastKitKey = "";
    private Policy current;
    private long lastShulker = -1000;
    private int startTotems = -1;
    /** What it started the round with, for the items a shulker restock tops up. */
    private final java.util.Map<String, Integer> startCounts = new java.util.HashMap<>();
    private static final String[] RESTOCKED = {"end_crystal", "obsidian", "respawn_anchor", "glowstone", "ender_pearl",
            "firework_rocket", "wind_charge", "golden_apple", "cobblestone"};
    private boolean everElytra, everWind;
    private long drinkUntil = -1;
    private int drinkSlot = -1;
    private long lastPot = -1000;
    private int retreatTicks;
    private long lastChestFix = -1000;
    /** Ticks of potting up left at the start of a round, before it starts fighting. */
    private int prepTicks = 60;
    private final java.util.Map<String, Long> prepThrown = new java.util.HashMap<>();
    /** Buff effect name, and the word in the potion name that gives it. */
    private static final String[][] BUFFS = {{"strength", "strength"}, {"speed", "swiftness"}, {"fire_resistance", "fire_resistance"}};

    public StyleBrain(BotProfile profile, Consumer<String> announce) {
        this.p = profile;
        this.smoother = new MotionSmoother(profile);
        this.humanizer = new Humanizer(profile);
        this.mobility = new Mobility(profile);
        this.navigator = new dev.reality.fightbot.core.nav.Navigator(profile);
        this.sword = new SwordPolicy(profile);
        this.elytraMace = new MacePolicy(profile, true);
        this.windMace = new MacePolicy(profile, false);
        this.riptideLungeMace = new RiptideLungeMacePolicy(profile);
        this.crystal = new CrystalPolicy(profile);
        this.ranged = new RangedPolicy(profile);
        this.trident = new TridentPolicy(profile);
        this.uhc = new UhcTools(profile);
        this.repair = new Repair(profile);
        this.airDefense = new AirDefense(profile);
        this.blastGuard = new BlastGuard(profile);
        this.blastGuard.pearlAway = (o, a) -> mobility.pearlAway(o, a);
        this.edgeGuard = new EdgeGuard(profile);
        this.announce = announce == null ? s -> { } : announce;
        this.current = sword;
    }

    /** The world around the bot, for styles that place blocks. Set before each step. */
    public void setWorld(dev.reality.fightbot.core.world.WorldView w) {
        crystal.setWorld(w);
        riptideLungeMace.setWorld(w);
        mobility.setWorld(w);
        navigator.setWorld(w);
        uhc.setWorld(w);
        predictor.setWorld(w);
        airDefense.setWorld(w);
        blastGuard.setWorld(w);
        edgeGuard.setWorld(w);
        world = w;
    }

    public Repair repair() {
        return repair;
    }

    public UhcTools uhc() {
        return uhc;
    }

    public dev.reality.fightbot.core.nav.Navigator navigator() {
        return navigator;
    }

    public Mobility mobility() {
        return mobility;
    }

    public CrystalPolicy crystal() {
        return crystal;
    }

    /** The elytra combo's step log, one JSON line per step. */
    public java.util.List<String> comboLog() {
        return elytraMace.combo.logLines();
    }

    public ElytraCombo combo() {
        return elytraMace.combo;
    }

    public Style.Choice choice() {
        return choice;
    }

    @Override
    public String stateName() {
        return current.stateName();
    }

    private final MotionSmoother smoother;
    private final Humanizer humanizer;
    private long offhandEmptySince = -1, lastInventoryMove = -1000;
    private final RangedPolicy ranged;
    private final TridentPolicy trident;
    private final UhcTools uhc;
    private final Repair repair;
    private final Mobility mobility;
    private final dev.reality.fightbot.core.nav.Navigator navigator;
    private dev.reality.fightbot.core.world.WorldView world;

    private final Predictor predictor = new Predictor();
    private final AirDefense airDefense;
    private final BlastGuard blastGuard;
    private final EdgeGuard edgeGuard;

    public EdgeGuard edgeGuard() {
        return edgeGuard;
    }

    public Predictor predictor() {
        return predictor;
    }

    public AirDefense airDefense() {
        return airDefense;
    }

    @Override
    public BotAction step(Observation o) {
        // Every style can ask where the target is going while this bot is stepped.
        if (o.hasOpponent() && !o.stale()) predictor.observe(o);
        Predictor.setCurrent(predictor);
        try {
            return stepInner(o);
        } finally {
            Predictor.setCurrent(null);
        }
    }

    private BotAction stepInner(Observation o) {
        if (p.autoStyle == 1 && o.loadout() != Loadout.EMPTY) {
            String key = kitKey(o.loadout());
            // Do not switch styles in the middle of a fall: the chestplate swap would read as a new kit.
            boolean midAir = (current instanceof MacePolicy && !o.onGround())
                    || (current instanceof RiptideLungeMacePolicy r && (!o.onGround() || r.preComboActive()));
            if (!key.equals(lastKitKey) && !midAir) {
                lastKitKey = key;
                Style.Choice next = Style.pick(o.loadout(), p);
                if (choice == null || next.use() != choice.use()) {
                    choice = next;
                    announce.accept(next.describe());
                }
                everElytra |= choice.use() == Style.ELYTRA_MACE;
                everWind |= choice.use() == Style.WIND_MACE || choice.use() == Style.RIPTIDE_LUNGE_MACE;
                current = switch (choice.use()) {
                    case ELYTRA_MACE -> elytraMace;
                    case RIPTIDE_LUNGE_MACE -> riptideLungeMace;
                    case WIND_MACE -> windMace;
                    case CRYSTAL, ANCHOR -> crystal;
                    case RANGED -> ranged;
                    case TRIDENT -> trident;
                    default -> sword;
                };
            }
        } else if (choice == null) {
            choice = new Style.Choice(Style.SWORD, Style.SWORD, "style picking is off");
        }
        if (current == elytraMace && elytraMace.gaveUp() && o.onGround()) {
            current = sword;
            choice = new Style.Choice(Style.SWORD, Style.ELYTRA_MACE, "chest swaps are not working on this server");
            announce.accept(choice.describe());
        }
        boolean ready = o.hasOpponent() && !o.stale() && o.loadout() != Loadout.EMPTY;
        boolean scriptedRiptideCombo = current instanceof RiptideLungeMacePolicy r && r.controlsMovement(o.onGround());
        // Potions take the whole tick, so they go before the style. Never in the middle of a combo.
        BotAction a = null;
        String potRestock = null;
        // Falling to its death comes before everything else.
        if (ready) {
            BotAction save = BotAction.idle(o.yaw(), o.pitch());
            if (mobility.rescue(o, save)) a = save;
        }
        // At the start of a round it pots up first: every buff it carries, back to back, wherever the target is.
        if (a == null && ready && prepTicks > 0 && p.usePotions == 1) {
            prepTicks--;
            BotAction pot = BotAction.idle(o.yaw(), o.pitch());
            pot.state = "POT_UP";
            if (prepare(o, pot)) a = pot;
            else prepTicks = 0;
        }
        // A combo only counts while the elytra style is the one running it. Switching styles mid-combo ends it,
        // or it would read as flying forever and shut off routing, towering, and pearls.
        if (current != elytraMace && elytraMace.combo.active()) elytraMace.combo.cancel("switched to " + choice.use().label);
        if (elytraMace.combo.active() && o.onGround() && elytraMace.combo.step() == ElytraCombo.Step.RECOVER) {
            elytraMace.combo.cancel("landed");
        }
        boolean flying = elytraMace.combo.active() || scriptedRiptideCombo || o.gliding();
        // The target is flying with an elytra: no pearls, towers, or routes after them. It waits on the ground.
        boolean oFlying = ready && p.flyerWait == 1 && predictor.flying(o);
        if (a == null && ready && !flying) {
            BotAction fire = BotAction.idle(o.yaw(), o.pitch());
            if (mobility.fire(o, fire)) a = fire;
        }
        if (a == null && ready && p.usePotions == 1 && !elytraMace.combo.active() && !scriptedRiptideCombo) {
            BotAction pot = BotAction.idle(o.yaw(), o.pitch());
            pot.state = "POTION";
            if (potions(o, pot)) a = pot;
            else potRestock = pot.restock;
        }
        // In water: swim. Gliding, rockets, wind charges, pearls, and mace drops do not work there.
        if (o.loadout().inWater && (!o.onGround() || o.loadout().underWater) && !scriptedRiptideCombo) {
            if (elytraMace.combo.active()) elytraMace.combo.cancel("in water");
            if (a == null && ready) a = swim(o);
        }
        // Lava and cobwebs, whatever the style, when it carries them.
        if (a == null && ready && !flying) {
            BotAction tool = BotAction.idle(o.yaw(), o.pitch());
            if (uhc.step(o, tool)) a = tool;
        }
        // A target up on a tower: follow them up, unless the elytra can fly over and smash down instead.
        boolean canFly = current == elytraMace && o.loadout().hasElytra() && o.loadout().fireworks() >= p.minFireworks
                && !o.opponentCovered(p.minDropHeadroom);
        if (a == null && ready && !flying && !canFly && !oFlying) {
            BotAction up = BotAction.idle(o.yaw(), o.pitch());
            if (mobility.tower(o, up)) a = up;
        }
        // Wind mace: a pearl straight up, caught on the way up for a higher launch.
        if (a == null && ready && !flying && !oFlying && current == windMace) {
            BotAction own = BotAction.idle(o.yaw(), o.pitch());
            if (mobility.ownCatch(o, own, true)) a = own;
        }
        // Low against a crystal or anchor player, in a mace style: out of blast range to heal before anything else.
        // It runs mid-flight too, and ends a combo unless the smash is about to land.
        // The crystal style does the same at lower health, since it trades blasts on purpose and eats in place first.
        if (ready && (current instanceof MacePolicy || current == crystal)) {
            blastGuard.observe(o);
            boolean crystalStyle = current == crystal;
            blastGuard.retreatAt = crystalStyle ? p.crystalRetreatHealth : p.blastRetreatHealth;
            blastGuard.resumeAt = crystalStyle ? p.crystalResumeHealth : p.blastResumeHealth;
            if (current instanceof MacePolicy mp) mp.setBlaster(p.blastNoGroundFight == 1 && blastGuard.blaster(o.tick()));
            boolean smashing = elytraMace.combo.active() && elytraMace.combo.step() == ElytraCombo.Step.SMASH;
            if (a == null && !smashing) {
                BotAction away = BotAction.idle(o.yaw(), o.pitch());
                if (blastGuard.step(o, away)) {
                    if (elytraMace.combo.active()) elytraMace.combo.cancel("low health against crystals");
                    a = away;
                }
            }
        }
        boolean holding = a == null && mobility.holdingForCatch(o.tick());
        if (holding) {
            // Waiting on the spot for the pearl to come down: it can still hit, but does not move or launch early.
            a = current.step(o);
            a.forward = 0;
            a.strafe = 0;
            a.jump = false;
            a.sprint = false;
            if (a.useOnce && a.hotbar == o.loadout().slot("wind_charge")) a.useOnce = false;
            a.state = "PEARL";
        }
        // Worn gear: bottles o' enchanting at its feet while nobody is close.
        if (a == null && ready && !flying) {
            BotAction fix = BotAction.idle(o.yaw(), o.pitch());
            if (repair.step(o, fix)) a = fix;
        }
        // Worn gear but the target is close: pearl away to repair, the same as for a restock.
        if (a == null && ready && !flying && repair.wanted(o) && !repair.safe(o) && o.distance() < p.repairSafeDistance) {
            BotAction away = BotAction.idle(o.yaw(), o.pitch());
            if (mobility.pearlAway(o, away)) {
                away.technique = "pearl_away_to_repair";
                a = away;
            }
        }
        // Needs a restock but the target is too close to open a box: pearl away first.
        if (a == null && ready && !flying && p.shulkerRestock == 1 && hasRestock(o.loadout()) && o.distance() < p.shulkerSafeDistance
                && o.tick() - lastShulker > 200 && needsRestock(o.loadout())) {
            BotAction away = BotAction.idle(o.yaw(), o.pitch());
            if (mobility.pearlAway(o, away)) a = away;
        }
        // Far away: pearl in, or pearl catch with the wind mace.
        // Even with an elytra, a pearl gets there first from far enough away.
        if (a == null && ready && !flying && !oFlying && (!canFly || o.distance() >= p.pearlFlyerBeyond)) {
            BotAction pearl = BotAction.idle(o.yaw(), o.pitch());
            // Not right after pearling away to restock: the restock comes first.
            boolean restockFirst = p.shulkerRestock == 1 && hasRestock(o.loadout()) && o.tick() - lastShulker > 200 && needsRestock(o.loadout());
            if (!restockFirst && mobility.pearlIn(o, pearl, current == windMace)) a = pearl;
        }
        // A trident on top of whatever the style is: a Riptide dash in rain or water, or a Loyalty throw at someone
        // out of reach. The trident style used to be the only one that touched it, so with a sword, crystals or
        // anything else in the kit the trident just sat there, rain or not. Mace styles keep their own launches.
        if (a == null && ready && !flying && !oFlying && p.tridentAddOn == 1 && current != trident && !(current instanceof MacePolicy)) {
            BotAction rt = trident.riptide(o);
            if (rt == null && o.distance() > p.tridentThrowBeyond) rt = trident.throwLoyal(o, p.tridentThrowBeyond);
            if (rt != null) a = rt;
        }
        // Walls, houses, and steps between it and the target: route around, open doors, or break through.
        if (a == null && ready && !flying && !canFly && !oFlying) {
            BotAction nav = BotAction.idle(o.yaw(), o.pitch());
            if (navigator.step(o, nav)) a = nav;
        }
        if (a == null) {
            // Against a flyer the mace styles do not launch after them: the sword waits for the pass or the landing.
            Policy style = oFlying && !flying && current instanceof MacePolicy ? sword : current;
            a = style.step(o);
            if (a.restock == null) a.restock = potRestock;
            if (oFlying && !flying) {
                boolean ownAim = style == crystal || style == ranged || style == trident;
                airDefense.adjust(o, a, predictor, ownAim, false);
            }
        }
        if (ready && !scriptedRiptideCombo) upkeep(o, a);
        if (!scriptedRiptideCombo && a.place == null && a.mine == null) dev.reality.fightbot.core.nav.Navigator.autoJump(world, o, a);
        if (!"escape_lava".equals(a.technique)) uhc.avoidLava(o, a);
        smoother.apply(o, a);
        humanize(o, a);
        // Last: whatever the style chose, never off an edge into the void or lava. It runs on the final yaw,
        // since the keys move the bot relative to where it ends up facing.
        if (ready && !flying) edgeGuard.apply(o, a);
        return a;
    }

    /**
     * Fighting in water. It hits with the sword style's timing, swims straight at the target with sprint
     * underwater, holds jump to go up and sneak to go down, comes up for air before it runs out, and climbs
     * out after a target who is on land.
     */
    private BotAction swim(Observation o) {
        Loadout l = o.loadout();
        // A Riptide trident is the fastest way through water, and the dash hits.
        BotAction dash = trident.riptide(o);
        if (dash != null) return dash;
        BotAction a = sword.step(o);
        a.state = "SWIM";
        a.jump = false;
        a.startGlide = false;
        a.strafe = 0;
        a.equip = l.wearingElytra() && l.count(Loadout::isChestplate) > 0 ? "chestplate" : null;
        double dy = o.oy() - o.y();
        double dist = o.distance();
        boolean targetWet = world != null && world.water((int) Math.floor(o.ox()), (int) Math.floor(o.oy() + 0.2), (int) Math.floor(o.oz()));
        if (l.underWater && l.air < p.surfaceAirAt) {
            a.pitch = -70;
            a.forward = 1;
            a.sprint = false;
            a.sneak = false;
            a.holdJump = true;
            a.technique = "surface_for_air";
            return a;
        }
        if (dist > 2.6) {
            a.forward = 1;
            // Sprinting underwater is the fast swim, and it goes where the bot looks.
            a.sprint = l.underWater;
            if (l.underWater) a.pitch = o.pitchToOpponent();
        }
        if (!targetWet) {
            // They are on land: up to the surface, and jump out at the edge.
            a.holdJump = true;
            a.sneak = false;
            a.technique = "swim_out";
        } else if (dy > 0.4) {
            a.holdJump = true;
            a.sneak = false;
            if (a.technique.isEmpty()) a.technique = "swim_up";
        } else if (dy < -1.2 && !a.sprint) {
            a.sneak = true;
            if (a.technique.isEmpty()) a.technique = "swim_down";
        } else if (a.technique.isEmpty()) {
            a.technique = "swim";
        }
        return a;
    }

    /** Things every style does: put a totem back in the offhand, take the elytra off for ground fights, and restock. */
    private void upkeep(Observation o, BotAction a) {
        Loadout l = o.loadout();
        if (startTotems < 0) {
            startTotems = l.totems();
            for (String id : RESTOCKED) if (l.count(id) > 0) startCounts.put(id, l.count(id));
        }
        boolean busy = a.attack || a.use || a.useOnce || a.swapAttack != null || a.jabSlot >= 0 || a.equip != null || a.startGlide;
        // A popped totem goes straight back from wherever the next one is. It does not cost a tick.
        if (p.retotem == 1 && l.offhand.equals("air") && l.totems() > 0) {
            a.offhandTotem = true;
            if (a.technique.isEmpty()) a.technique = "retotem";
        }
        // No melee weapon in the hotbar but one in the backpack: it fought whole rounds with a potion in hand
        // that way, so the weapon comes down first, before anything else is restocked.
        if ((a.restock == null || a.restock.startsWith("splash_potion")) && !busy && l.slot(Loadout::isMelee) < 0) {
            String weapon = l.ownedId(Loadout::isSword);
            if (weapon == null) weapon = l.ownedId(Loadout::isAxe);
            if (weapon == null) weapon = l.ownedId("mace"::equals);
            if (weapon != null) {
                a.restock = weapon;
                if (a.technique.isEmpty()) a.technique = "weapon_to_hotbar";
            }
        }
        // A trident left in the backpack: bring the Riptide one down in rain or water, or the Loyalty one when the
        // target is out of reach, while nobody is close.
        if (a.restock == null && !busy && o.distance() >= 6 && p.tridentAddOn == 1) {
            if (l.wet && l.slot("trident:riptide") < 0 && l.has("trident:riptide")) a.restock = "trident:riptide";
            else if (o.distance() > p.tridentThrowBeyond && l.slot("trident") < 0 && l.has("trident")) a.restock = "trident";
        }
        // Fighting on foot in an elytra means fighting without chest armor.
        if (o.onGround() && l.wearingElytra() && current != elytraMace && l.count(Loadout::isChestplate) > 0
                && a.equip == null && o.tick() - lastChestFix > 10) {
            a.equip = "chestplate";
            lastChestFix = o.tick();
            if (a.technique.isEmpty()) a.technique = "chestplate_on";
        }
        if (p.shulkerRestock == 1 && hasRestock(l) && o.onGround() && o.distance() >= p.shulkerSafeDistance
                && o.tick() - lastShulker > 200 && !busy && needsRestock(l)) {
            lastShulker = o.tick();
            BotAction r = BotAction.idle(a.yaw, a.pitch);
            a.forward = 0;
            a.strafe = 0;
            a.sprint = false;
            a.jump = false;
            a.block = false;
            a.hotbar = -1;
            a.restock = null;
            a.shulkerRestock = true;
            a.technique = "shulker_restock";
            a.state = r.state;
        }
    }

    /**
     * Potting up. A splash potion thrown straight down lands on the bot at once, so splash potions come first.
     * Drinking takes 1.6 seconds, so it only drinks with room, and stops if the opponent closes in.
     * Returns true when it used this tick for a potion.
     */
    private boolean potions(Observation o, BotAction a) {
        Loadout l = o.loadout();
        long t = o.tick();
        if (drinkUntil >= 0) {
            if (t < drinkUntil && o.distance() > 3.5 && o.onGround()) {
                a.hotbar = drinkSlot;
                a.use = true;
                a.attack = false;
                a.block = false;
                a.sprint = false;
                a.technique = "drink_potion";
                return true;
            }
            drinkUntil = -1;
        }
        // Low on health in the air: still splash, once it is close above the ground or about to die, since the
        // throw lands at its feet either way. It used to wait for the ground, and a jumping fight never gave it one.
        int anyHeal = l.potions("splash_potion", "healing");
        boolean nearGround = o.onGround() || (!o.gliding() && heightAboveGround(o) <= 1.6);
        boolean dying = o.effectiveHealth() <= p.potsCritical;
        if (!o.onGround() && !(anyHeal > 0 && o.effectiveHealth() <= (anyHeal >= p.potsModeAt ? p.potsHealAt : p.potHealAt) && (nearGround || dying))) return false;
        // Pots only: plenty of healing pots and nothing else to heal with. Each one heals 8, so it pots at 12
        // to waste none, steps away first so the opponent is not in the splash, and pots twice in a row when
        // it is about to die.
        int healPots = l.potions("splash_potion", "healing");
        boolean potsMode = healPots >= p.potsModeAt;
        double hp = o.effectiveHealth();
        double healAt = potsMode ? p.potsHealAt : p.potHealAt;
        // Partway through an apple: let it finish unless it is about to die. Switching to a potion cancels the bite.
        if (hp <= healAt && ((sword.eating() && current == sword) || blastGuard.eating() || (crystal.eating() && current == crystal)) && hp > p.potsCritical) return false;
        if (hp <= healAt) {
            int heal = l.potionSlot("splash_potion", "healing");
            boolean critical = hp <= p.potsCritical;
            // None left in the hotbar, more in the backpack: bring one down now, whoever is close, and splash it
            // next tick. It used to refill only in pots mode with nobody within 5 blocks, so it stopped potting.
            if (heal < 0 && healPots > 0) {
                a.restock = "splash_potion:healing";
                a.technique = "pot_refill";
                return false;
            }
            boolean shared = potsMode && !critical && o.distance() < p.potStealRange && o.onGround();
            if (shared && retreatTicks < p.potRetreatTicks) {
                // Back off out of splash range first: a splash heals everyone within 4 blocks.
                retreatTicks++;
                a.yaw = o.yawToOpponent() + 180;
                a.pitch = 0;
                a.forward = 1;
                a.sprint = true;
                a.strafe = 0;
                a.attack = false;
                a.block = false;
                a.use = false;
                a.jump = o.onGround() && retreatTicks % 6 == 1;
                a.technique = "pot_retreat";
                return true;
            }
            retreatTicks = 0;
            long gap = critical && healPots > 1 ? 1 : 4;
            if (heal >= 0 && t - lastPot >= gap) return splash(o, a, heal, critical && t - lastPot <= 2 ? "pot_double" : "pot_heal");
        } else {
            retreatTicks = 0;
        }
        if (!o.onGround()) return false;
        // Keep healing pots in the hotbar between exchanges, the way a pots player refills.
        if (potsMode && a.restock == null && o.distance() >= 5) {
            int inHotbar = 0;
            for (int i = 0; i < 9; i++) if (l.hotbar(i).startsWith("splash_potion") && l.hotbar(i).contains("healing")) inHotbar++;
            if (inHotbar < p.potsHotbarMin && healPots > inHotbar) a.restock = "splash_potion:healing";
        }
        if (t - lastPot < 4) return false;
        boolean safe = o.distance() >= p.potSafeDistance || o.oUsingItem();
        for (String[] b : BUFFS) {
            if (l.effectTicks(b[0]) >= p.rebuffTicks) continue;
            Long thrown = prepThrown.get(b[0]);
            if (thrown != null && t - thrown < 20) continue;
            int splash = l.potionSlot("splash_potion", b[1]);
            if (splash >= 0 && (safe || o.distance() > 4)) {
                prepThrown.put(b[0], t);
                return splash(o, a, splash, "pot_" + b[0]);
            }
            int drink = l.potionSlot("potion", b[1]);
            if (drink >= 0 && safe && o.distance() >= p.potSafeDistance + 3) {
                drinkSlot = drink;
                drinkUntil = t + 34;
                lastPot = t;
                a.hotbar = drink;
                a.use = true;
                a.attack = false;
                a.block = false;
                a.sprint = false;
                a.technique = "drink_potion";
                return true;
            }
            // The potion is only in the main inventory: bring it to the hotbar while there is room.
            if (safe && a.restock == null && (l.potions("splash_potion", b[1]) > 0 || l.potions("potion", b[1]) > 0)) {
                a.restock = l.potions("splash_potion", b[1]) > 0 ? "splash_potion:" + b[1] : "potion:" + b[1];
            }
        }
        if (o.effectiveHealth() <= p.potHealAt && a.restock == null && l.potions("splash_potion", "healing") > 0 && o.distance() > 5) {
            a.restock = "splash_potion:healing";
        }
        return false;
    }

    /** Blocks between its feet and the ground under it, up to 4. Unknown world counts as on the ground. */
    private double heightAboveGround(Observation o) {
        if (world == null) return 0;
        int bx = (int) Math.floor(o.x()), bz = (int) Math.floor(o.z());
        int by = (int) Math.floor(o.y());
        for (int k = 0; k <= 4; k++) {
            if (world.solid(bx, by - 1 - k, bz)) return o.y() - (by - k);
        }
        return 5;
    }

    private boolean splash(Observation o, BotAction a, int slot, String technique) {
        lastPot = o.tick();
        a.hotbar = slot;
        a.useOnce = true;
        a.attack = false;
        a.use = false;
        a.block = false;
        a.pitch = 90;
        // A splash lasts less the farther from its center a player is, so it stops for the throw instead of
        // running past its own potion and getting a short effect that has to be thrown again sooner.
        a.forward = 0;
        a.strafe = 0;
        a.sprint = false;
        a.jump = false;
        a.technique = technique;
        return true;
    }

    /** Starts a new round: pot up again before fighting. */
    public void startRound() {
        prepTicks = 60;
        startTotems = -1;
        startCounts.clear();
        prepThrown.clear();
    }

    public boolean preparing() {
        return prepTicks > 0;
    }

    /**
     * One step of potting up at the start of a round. Splashes a missing buff if one is in the hotbar,
     * or brings one down from the inventory. Returns false once there is nothing left to do.
     */
    private boolean prepare(Observation o, BotAction a) {
        Loadout l = o.loadout();
        if (!o.onGround()) return true;
        if (o.tick() - lastPot < 2) return true;
        for (String[] b : BUFFS) {
            if (l.effectTicks(b[0]) >= p.rebuffTicks) continue;
            // A thrown potion takes a tick or two to land, so do not throw the same one twice in a row.
            Long thrown = prepThrown.get(b[0]);
            if (thrown != null && o.tick() - thrown < 20) continue;
            int splash = l.potionSlot("splash_potion", b[1]);
            if (splash >= 0) {
                prepThrown.put(b[0], o.tick());
                return splash(o, a, splash, "pot_up_" + b[0]);
            }
            if (l.potions("splash_potion", b[1]) > 0) {
                a.restock = "splash_potion:" + b[1];
                a.technique = "pot_up_restock";
                lastPot = o.tick() - 1;
                return true;
            }
        }
        return false;
    }

    /**
     * Inventory moves at a person's pace: a popped totem goes back after a few ticks, not the same tick,
     * and items move into the hotbar no more often than a person can, and never in the middle of a hit.
     */
    private void humanize(Observation o, BotAction a) {
        humanizer.apply(o, a);
        if (p.humanize != 1) return;
        long t = o.tick();
        if (!o.loadout().offhand.equals("air")) offhandEmptySince = -1;
        else if (offhandEmptySince < 0) offhandEmptySince = t;
        if (a.offhandTotem && offhandEmptySince >= 0 && t - offhandEmptySince < p.retotemTicks) a.offhandTotem = false;
        if (a.restock != null) {
            if (t - lastInventoryMove < p.inventoryMoveTicks || a.attack || a.swapAttack != null || a.attackEntity >= 0) a.restock = null;
            else lastInventoryMove = t;
        }
    }

    public Humanizer humanizer() {
        return humanizer;
    }

    /** A shulker box, or an ender chest with something in it. */
    static boolean hasRestock(Loadout l) {
        return l.shulkers() > 0 || (l.has("ender_chest") && l.enderStock > 0);
    }

    /** Out of something the style runs on. */
    public boolean needsRestock(Loadout l) {
        if (l.apples() == 0) return true;
        if (startTotems > 0 && l.totems() <= p.restockTotemsAt) return true;
        for (var e : startCounts.entrySet()) {
            if (e.getValue() >= 4 && l.count(e.getKey()) <= e.getValue() * p.restockShare) return true;
        }
        if (everElytra && l.fireworks() < p.minFireworks) return true;
        return everWind && l.windCharges() == 0;
    }

    /** What decides the style: which of the deciding items are present. Counts only matter for the fewest fireworks needed. */
    String kitKey(Loadout l) {
        boolean chestplateHandy = l.chest.endsWith("_chestplate") || l.slot(Loadout::isChestplate) >= 0;
        return (l.slot("mace") >= 0) + "," + l.hasElytra() + "," + (l.slot("firework_rocket") >= 0 && l.fireworks() >= p.minFireworks) + "," + chestplateHandy
                + "," + (l.slot("wind_charge") >= 0) + "," + l.has("end_crystal") + "," + l.has("obsidian") + ","
                + l.has("respawn_anchor") + "," + (l.slot(Loadout::isSpear) >= 0) + "," + (l.slot(Loadout::isSword) >= 0) + ","
                + (l.slot(Loadout::isAxe) >= 0) + "," + l.offhand.equals("shield") + "," + l.has("tnt_minecart") + ","
                + (l.slot("trident:riptide") >= 0) + "," + l.has("water_bucket") + "," + l.lungeLevel + "," + l.windBurstLevel;
    }
}
