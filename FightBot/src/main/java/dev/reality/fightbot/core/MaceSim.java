package dev.reality.fightbot.core;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Mace duel simulator on a flat floor, with real height. It copies vanilla's rules for walking,
 * falling, elytra gliding, firework boosts, mace smash damage, crits, shields, the axe disabling
 * a shield, attack cooldowns, and diamond armor. Protection is a flat cut. Wind charge launches
 * and knockback are simplified.
 */
public final class MaceSim {
    /** Anything that can fight in the simulator: gets an observation, returns inputs. */
    public interface Brain {
        BotAction act(Observation o);
    }

    static final double PROTECTION = 0.68;
    /** Prints every tick of every duel, for debugging. */
    public static boolean trace;
    /**
     * Whether attribute swaps work: a hit in the same tick as a hotbar switch keeps the attack bar and
     * damage of the item held before. False models the game after Mojang fixes it.
     */
    public static boolean swapsWork = true;
    /**
     * Lunge push per level, in blocks per tick. The real 26.3 number was not available, so this is a guess.
     * Lunge also costs one hunger point per level.
     */
    public static double lungePerLevel = 0.4;
    /** Ticks before a chest swap takes effect, to test waiting on slow servers. */
    public static int equipDelay = 0;
    /** Chest swaps that never happen, and glides that never open, for testing the combo's safeguards. */
    public static boolean equipFails = false, glideFails = false;
    /** Start the elytra kit already wearing the elytra, with the chestplate in the hotbar. */
    public static boolean startWearingElytra = false;

    static final class Body {
        double x, y, z, vx, vy, vz;
        float yaw, pitch;
        boolean onGround = true, gliding, sprinting, blocking, usingShield;
        double hp = 20, fallDistance;
        String chest = "diamond_chestplate";
        String offhand = "air";
        final String[] hotbar = new String[9];
        final int[] counts = new int[9];
        int selected;
        int attackTicker = 100, invuln, rocketTicks, windCooldown, shieldDisabled, blockTicks;
        boolean noFallDamage;
        boolean sprintSpent;
        int food = 20;
        String pendingEquip;
        int pendingTicks;
        int lungeSlot = -1, lungeLevel;

        String held() {
            return hotbar[selected] == null ? "air" : hotbar[selected];
        }

        double cooldownTicks() {
            String h = held();
            if (h.equals("mace")) return 20 / 0.6;
            if (h.endsWith("_axe")) return 20;
            if (h.endsWith("_sword")) return 20 / 1.6;
            return 5;
        }

        double charge() {
            return Math.min(1, (attackTicker + 0.5) / cooldownTicks());
        }

        int armor() {
            return 3 + 6 + 3 + (chest.endsWith("_chestplate") ? 8 : 0);
        }

        Loadout loadout() {
            String[] h = new String[9];
            for (int i = 0; i < 9; i++) h[i] = hotbar[i] == null ? "air" : hotbar[i];
            Map<String, Integer> totals = new TreeMap<>();
            for (int i = 0; i < 9; i++) if (!h[i].equals("air")) totals.merge(h[i], counts[i], Integer::sum);
            if (!chest.equals("air")) totals.merge(chest, 1, Integer::sum);
            if (!offhand.equals("air")) totals.merge(offhand, 1, Integer::sum);
            Loadout l = new Loadout(h, counts, totals, chest, offhand);
            l.lungeSlot = lungeSlot;
            l.lungeLevel = lungeLevel;
            l.food = food;
            return l;
        }

        void give(int slot, String item, int count) {
            hotbar[slot] = item;
            counts[slot] = count;
        }

        void use(int slot) {
            if (--counts[slot] <= 0) {
                hotbar[slot] = null;
                counts[slot] = 0;
            }
        }
    }

    public static final class Result {
        public int duels, wins, losses, draws;
        public int smashes, stunSlams, missedDrops, rockets, windLaunches;
        /** Smashes that came within 4 seconds of the one before, and the ticks between them. */
        public int followGaps;
        public double followGapTicks;
        /** Hits landed and damage dealt by the bot and by its opponent. */
        public int botHits, enemyHits, botCrits, enemyCrits;
        public double botDamage, enemyDamage;
        public double smashDamage, biggestSmash, ticks;
        /** Attack bar charge summed over the bot's smashes, and how many of them were crits. */
        public double smashChargeSum;
        public int smashCrits;
        public final Map<String, Integer> techniques = new TreeMap<>();

        public double winRate() {
            return duels == 0 ? 0 : (double) wins / duels;
        }

        public double avgSmash() {
            return smashes == 0 ? 0 : smashDamage / smashes;
        }

        @Override
        public String toString() {
            return String.format("won %d of %d, lost %d, drew %d. Smashes %d, average %.1f, biggest %.1f, stun slams %d, missed drops %d, rockets %d, wind launches %d, average fight %.1f s",
                    wins, duels, losses, draws, smashes, avgSmash(), biggestSmash, stunSlams, missedDrops, rockets, windLaunches, ticks / Math.max(1, duels) / 20);
        }
    }

    public enum Kit { ELYTRA, WIND, SWORD, ELYTRA_LUNGE, WIND_LUNGE }

    private final Random rng;

    public MaceSim(long seed) {
        this.rng = new Random(seed);
    }

    // ---------------------------------------------------------------- brains

    public static Brain bot(BotProfile profile) {
        StyleBrain brain = new StyleBrain(profile, null);
        ActionValidator v = new ActionValidator(profile);
        return o -> v.check(o, brain.step(o));
    }

    /** Ground sword player that chases and swings at full charge. */
    public static Brain chaser() {
        return o -> {
            BotAction a = new BotAction();
            a.yaw = o.yawToOpponent();
            a.pitch = o.pitchToOpponent();
            a.forward = o.distance() > 2.0 ? 1 : 0;
            a.sprint = true;
            a.attack = o.charge() >= 0.95 && MacePolicy.reachFrom(o, o.x(), o.y(), o.z()) <= 3.0;
            return a;
        };
    }

    /** Runs out from under a player above it, otherwise chases. */
    public static Brain dodger() {
        Brain chase = chaser();
        return o -> {
            if (o.oy() - o.y() > 3 && o.distance() < 8) {
                BotAction a = new BotAction();
                // Sprint sideways relative to the attacker so the drop misses.
                a.yaw = o.yawToOpponent() + 90;
                a.pitch = -60;
                a.forward = 1;
                a.sprint = true;
                return a;
            }
            return chase.act(o);
        };
    }

    /**
     * Launches itself with a wind charge when a player is diving at it from close by, otherwise chases.
     * Needs enemyWind on, so it has charges.
     */
    public static Brain windEscaper() {
        Brain chase = chaser();
        return o -> {
            boolean diving = o.oy() - o.y() > 2 && o.distance() < 6;
            if (diving && o.onGround() && o.windReady() && o.loadout().slot("wind_charge") >= 0) {
                BotAction a = new BotAction();
                a.yaw = o.yawToOpponent();
                a.pitch = 90;
                a.hotbar = o.loadout().slot("wind_charge");
                a.useOnce = true;
                a.jump = true;
                return a;
            }
            BotAction a = chase.act(o);
            a.hotbar = 0;
            return a;
        };
    }

    /**
     * A flat roof, 1 block thick, from 3 to 4 blocks up, over x -3 to 3 and z 6 to 14. Players can stand under
     * it or on it, and nobody can hit or see through it.
     */
    public static boolean roof = false;
    static final double ROOF_BOTTOM = 3, ROOF_TOP = 4;

    static boolean overRoofArea(double x, double z) {
        return roof && x >= -3 && x <= 3 && z >= 6 && z <= 14;
    }

    static boolean underRoof(Body b) {
        return overRoofArea(b.x, b.z) && b.y < ROOF_BOTTOM;
    }

    static double floorAt(Body b) {
        return overRoofArea(b.x, b.z) && b.y >= ROOF_TOP - 0.01 ? ROOF_TOP : 0;
    }

    static double headroom(Body b) {
        return underRoof(b) ? Math.max(0, ROOF_BOTTOM - (b.y + 1.8)) : 24;
    }

    /** Stays under the roof, and fights anyone who comes in. */
    public static Brain hider() {
        Brain chase = chaser();
        return o -> {
            double dx = 0 - o.x(), dz = 10 - o.z();
            boolean botUnder = o.ox() >= -3 && o.ox() <= 3 && o.oz() >= 6 && o.oz() <= 14 && o.oy() < ROOF_BOTTOM;
            if (botUnder && o.distance() < 4) return chase.act(o);
            BotAction a = new BotAction();
            a.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            a.pitch = 0;
            a.forward = Math.hypot(dx, dz) > 1.0 ? 1 : 0;
            a.sprint = true;
            if (o.distance() < 3.2 && o.charge() >= 0.95) {
                a.yaw = o.yawToOpponent();
                a.attack = true;
            }
            return a;
        };
    }

    /** The opponent's starting health. Above 20 stands in for totems and absorption, so fights last past one smash. */
    public static double enemyHp = 20;

    /** The bot's mace has Wind Burst, which throws it back up after each smash. */
    public static boolean windBurst = false;
    private long now, lastBotSmash = -1;

    /** Gives the opponent wind charges, for windEscaper. */
    public static boolean enemyWind = false;

    /** Holds a shield toward any player above it, otherwise chases. */
    public static Brain shieldUp() {
        Brain chase = chaser();
        return o -> {
            if (o.oy() - o.y() > 2 && o.distance() < 10) {
                BotAction a = new BotAction();
                a.yaw = o.yawToOpponent();
                a.pitch = o.pitchToOpponent();
                a.block = true;
                return a;
            }
            return chase.act(o);
        };
    }

    // ---------------------------------------------------------------- running duels

    public Result run(int duels, Function<Long, Brain> bot, Function<Long, Brain> enemy, Kit kit, boolean enemyShield) {
        Result r = new Result();
        for (int d = 0; d < duels; d++) {
            long seed = rng.nextLong();
            duel(bot.apply(seed), enemy.apply(seed + 1), kit, enemyShield, r);
        }
        return r;
    }

    private void duel(Brain botBrain, Brain enemyBrain, Kit kit, boolean enemyShield, Result r) {
        Body a = new Body(), b = new Body();
        a.x = 0; a.z = 0;
        b.x = rng.nextDouble() * 4 - 2; b.z = 8 + rng.nextDouble() * 4;
        a.give(0, "mace", 1);
        a.give(1, "diamond_sword", 1);
        a.give(2, "diamond_axe", 1);
        switch (kit) {
            case ELYTRA, ELYTRA_LUNGE -> {
                a.give(3, "firework_rocket", 32);
                a.give(4, "elytra", 1);
                if (startWearingElytra) {
                    a.chest = "elytra";
                    a.give(4, "diamond_chestplate", 1);
                }
            }
            case WIND, WIND_LUNGE -> a.give(3, "wind_charge", 32);
            case SWORD -> { }
        }
        if (kit == Kit.ELYTRA_LUNGE || kit == Kit.WIND_LUNGE) {
            a.give(5, "diamond_spear", 1);
            a.lungeSlot = 5;
            a.lungeLevel = 3;
        }
        b.give(0, "diamond_sword", 1);
        b.hp = enemyHp;
        if (enemyWind) b.give(1, "wind_charge", 64);
        if (enemyShield) b.offhand = "shield";
        r.duels++;
        long t = 0;
        lastBotSmash = -1;
        for (; t < 1200; t++) {
            now = t;
            Observation oa = observe(t, a, b), ob = observe(t, b, a);
            BotAction xa = botBrain.act(oa), xb = enemyBrain.act(ob);
            if (!xa.technique.isEmpty()) r.techniques.merge(xa.technique, 1, Integer::sum);
            if (xa.technique.equals("missed_drop")) r.missedDrops++;
            if (trace) System.out.printf("t%d bot %.1f,%.1f,%.1f v%.2f,%.2f,%.2f hp%.1f fd%.1f gl%s ch=%s held=%s c%.2f | %s %s att%s hb%d eq%s sg%s uo%s | foe %.1f,%.1f hp%.1f blk%s att%s%n",
                    t, a.x, a.y, a.z, a.vx, a.vy, a.vz, a.hp, a.fallDistance, a.gliding, a.chest, a.held(), a.charge(), xa.state, xa.technique, xa.attack, xa.hotbar, xa.equip, xa.startGlide, xa.useOnce,
                    b.x, b.z, b.hp, b.blocking, xb.attack);
            apply(a, b, xa, r, true);
            apply(b, a, xb, r, false);
            physics(a);
            physics(b);
            if (a.hp <= 0 || b.hp <= 0) break;
        }
        r.ticks += t;
        if (b.hp <= 0 && a.hp > 0) r.wins++;
        else if (a.hp <= 0 && b.hp > 0) r.losses++;
        else r.draws++;
    }

    private Observation observe(long t, Body me, Body them) {
        Loadout l = me.loadout();
        int weapon = l.slot(Loadout::isSword);
        if (weapon < 0) weapon = l.slot(Loadout::isAxe);
        return new Observation(t, true, me.x, me.y, me.z, me.vx, me.vy, me.vz, me.yaw, me.pitch, me.onGround, me.sprinting,
                me.usingShield, (float) me.hp, 0, me.held(), me.charge(), weapon, -1, 0,
                them.x, them.y, them.z, them.vx, them.vy, them.vz, them.yaw, them.onGround, them.sprinting,
                them.blocking, them.usingShield, (float) them.hp, 0, them.held(), lineOfSight(me, them), 0,
                me.offhand.equals("shield"), l.slot(Loadout::isAxe), -1, 0, 0, 0, them.charge(), them.offhand.equals("shield"), 0, 0,
                l, me.fallDistance, me.gliding, me.windCooldown == 0, headroom(me), headroom(them));
    }

    private void apply(Body me, Body them, BotAction x, Result r, boolean isBot) {
        me.yaw = x.yaw;
        me.pitch = x.pitch;
        String startItem = me.held();
        double startCooldown = me.cooldownTicks();
        if (x.hotbar >= 0 && x.hotbar != me.selected) {
            String before = me.held();
            me.selected = x.hotbar;
            if (!before.equals(me.held())) me.attackTicker = 0;
        }
        if (x.equip != null && !(isBot && equipFails)) {
            if (isBot && equipDelay > 0) {
                // A repeat of the same request does not restart the wait, like a server queue.
                if (!x.equip.equals(me.pendingEquip)) {
                    me.pendingEquip = x.equip;
                    me.pendingTicks = equipDelay;
                }
            } else {
                equip(me, x.equip);
            }
        }
        if (me.pendingEquip != null && --me.pendingTicks <= 0) {
            equip(me, me.pendingEquip);
            me.pendingEquip = null;
        }
        if (x.startGlide && !me.onGround && !me.gliding && me.chest.equals("elytra") && !(isBot && glideFails)) {
            me.gliding = true;
        }
        if (x.useOnce) {
            String h = me.held();
            if (h.equals("firework_rocket") && me.gliding) {
                me.rocketTicks = 20 + rng.nextInt(6) + rng.nextInt(7);
                me.use(me.selected);
                if (isBot) r.rockets++;
            } else if (h.equals("wind_charge") && me.windCooldown == 0) {
                me.use(me.selected);
                me.windCooldown = 10;
                // Thrown at its own feet: the burst throws it up. Only works close to the floor.
                if (me.y < 1.5 && me.pitch > 60) {
                    me.vy = 1.1;
                    me.onGround = false;
                    me.fallDistance = 0;
                    me.noFallDamage = true;
                    if (isBot) r.windLaunches++;
                }
            }
        }
        boolean wantsBlock = (x.block || x.use) && me.offhand.equals("shield") && me.shieldDisabled == 0 && !x.attack;
        me.blockTicks = wantsBlock ? me.blockTicks + 1 : 0;
        me.usingShield = wantsBlock;
        me.blocking = wantsBlock && me.blockTicks >= 5;
        // After a sprint hit the sprint stops, and it only comes back once forward or sprint is let go,
        // which is what W-tapping is for. Same rule as the sword duel simulator.
        boolean wantSprint = x.sprint && x.forward > 0 && !wantsBlock;
        if (!wantSprint) me.sprintSpent = false;
        me.sprinting = wantSprint && !me.sprintSpent;
        if (x.jump && me.onGround) {
            me.vy = 0.42;
            if (me.sprinting) {
                double yr = Math.toRadians(me.yaw);
                me.vx += -Math.sin(yr) * 0.2;
                me.vz += Math.cos(yr) * 0.2;
            }
            me.onGround = false;
        }
        if (x.attack) attack(me, them, x, r, isBot);
        if (x.swapAttack != null || x.jabSlot >= 0) {
            // Everything below happens inside one tick, before the game refreshes the held item's attributes.
            String attrItem = startItem;
            for (int slot : x.swapAttack == null ? new int[0] : x.swapAttack) {
                me.selected = slot;
                double cd = swapsWork ? startCooldown : me.cooldownTicks();
                double charge = Math.min(1, (me.attackTicker + 0.5) / cd);
                hit(me, them, me.held(), swapsWork ? attrItem : me.held(), charge, x.technique, r, isBot);
                me.attackTicker = 0;
            }
            if (x.jabSlot >= 0) {
                me.selected = x.jabSlot;
                double cd = swapsWork ? startCooldown : me.cooldownTicks();
                double charge = Math.min(1, (me.attackTicker + 0.5) / cd);
                if (charge >= 1 && me.food > 6 && !me.gliding && me.lungeLevel > 0) {
                    double yr = Math.toRadians(me.yaw);
                    me.vx += -Math.sin(yr) * lungePerLevel * me.lungeLevel;
                    me.vz += Math.cos(yr) * lungePerLevel * me.lungeLevel;
                    me.food -= me.lungeLevel;
                    me.attackTicker = 0;
                    if (isBot) r.techniques.merge("lunge_done", 1, Integer::sum);
                }
            }
            if (x.swapBack >= 0) me.selected = x.swapBack;
            // The next tick sees the held item changed and empties the bar.
            if (!me.held().equals(startItem)) me.attackTicker = 0;
        }
        me.attackTicker++;
        move(me, x);
        if (me.invuln > 0) me.invuln--;
        if (me.windCooldown > 0) me.windCooldown--;
        if (me.shieldDisabled > 0) me.shieldDisabled--;
    }

    private static void equip(Body me, String what) {
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            String h = me.hotbar[i] == null ? "air" : me.hotbar[i];
            if (what.equals("elytra") ? h.equals("elytra") : h.endsWith("_chestplate")) { slot = i; break; }
        }
        if (slot < 0) return;
        String old = me.chest;
        me.chest = me.hotbar[slot];
        me.hotbar[slot] = old;
        me.counts[slot] = 1;
        if (!me.chest.equals("elytra")) me.gliding = false;
    }

    private void attack(Body me, Body them, BotAction x, Result r, boolean isBot) {
        double charge = me.charge();
        me.attackTicker = 0;
        hit(me, them, me.held(), me.held(), charge, x.technique, r, isBot);
    }

    /**
     * One hit. weapon is the item in hand, which decides the mace smash bonus and the axe shield disable.
     * attrItem is the item whose attributes the game still has, which decides the base damage.
     */
    private void hit(Body me, Body them, String weapon, String attrItem, double charge, String technique, Result r, boolean isBot) {
        double reach = reach(me, them);
        if (reach > 3.0) return;
        String w = weapon;
        boolean shieldFacing = them.blocking && facing(them, me);
        if (shieldFacing) {
            if (w.endsWith("_axe")) {
                them.shieldDisabled = 100;
                them.blocking = false;
                them.blockTicks = 0;
                if (isBot) r.techniques.merge("shield_disabled_hit", 1, Integer::sum);
            }
            return;
        }
        if (them.invuln > 0) return;
        double base = attrItem.equals("mace") ? 6 : attrItem.endsWith("_sword") ? 7 : attrItem.endsWith("_axe") ? 9 : 1;
        double dmg = base * (0.2 + charge * charge * 0.8);
        boolean crit = charge > 0.9 && !me.onGround && me.vy < 0 && !me.gliding && !me.sprinting;
        boolean smash = w.equals("mace") && me.fallDistance > 1.5 && !me.gliding;
        if (smash && isBot) {
            r.smashChargeSum += charge;
            if (crit) r.smashCrits++;
        }
        if (smash) {
            double fd = me.fallDistance;
            dmg += fd <= 3 ? 4 * fd : fd <= 8 ? 12 + 2 * (fd - 3) : 22 + (fd - 8);
        }
        // The game adds the mace's fall bonus first and applies the crit after, so a crit smash is 1.5 times all of it.
        if (crit) dmg *= 1.5;
        double taken = afterArmor(dmg, them.armor(), 8);
        them.hp -= taken;
        if (isBot) { r.botHits++; r.botDamage += taken; if (crit) r.botCrits++; }
        else { r.enemyHits++; r.enemyDamage += taken; if (crit) r.enemyCrits++; }
        them.invuln = 10;
        // Knockback away from the attacker.
        double dx = them.x - me.x, dz = them.z - me.z, len = Math.max(0.01, Math.hypot(dx, dz));
        double kb = 0.4 + (me.sprinting ? 0.5 : 0);
        if (me.sprinting) {
            me.sprinting = false;
            me.sprintSpent = true;
        }
        them.vx = them.vx / 2 + dx / len * kb;
        them.vz = them.vz / 2 + dz / len * kb;
        // Knocked up off the ground. The ground flag has to go too, or the next tick puts them straight back.
        if (them.onGround) {
            them.vy = 0.4;
            them.onGround = false;
        }
        if (smash) {
            me.vy = windBurst ? 1.2 : 0.01;
            if (isBot && lastBotSmash >= 0 && now - lastBotSmash <= 80) {
                r.followGaps++;
                r.followGapTicks += now - lastBotSmash;
            }
            if (isBot) lastBotSmash = now;
            me.fallDistance = 0;
            me.noFallDamage = true;
            if (isBot) {
                r.smashes++;
                r.smashDamage += taken;
                r.biggestSmash = Math.max(r.biggestSmash, taken);
                if (technique.equals("stun_slam")) r.stunSlams++;
            }
        }
    }

    static double afterArmor(double dmg, int armor, int toughness) {
        double cut = Math.min(20, Math.max(armor / 5.0, armor - 4 * dmg / (toughness + 8))) / 25;
        return dmg * (1 - cut) * PROTECTION;
    }

    private static boolean facing(Body defender, Body attacker) {
        double yr = Math.toRadians(defender.yaw);
        double lx = -Math.sin(yr), lz = Math.cos(yr);
        double dx = attacker.x - defender.x, dz = attacker.z - defender.z, len = Math.hypot(dx, dz);
        if (len < 1e-6) return true;
        return (lx * dx + lz * dz) / len > 0;
    }

    /** The roof blocks sight and hits between someone under it and someone at or above its height. */
    static boolean lineOfSight(Body a, Body b) {
        if (!roof) return true;
        return !((underRoof(a) && b.y + 1.6 >= ROOF_BOTTOM && !underRoof(b)) || (underRoof(b) && a.y + 1.6 >= ROOF_BOTTOM && !underRoof(a)));
    }

    private static double reach(Body me, Body them) {
        if (!lineOfSight(me, them)) return 99;
        double hx = Math.max(0, Math.abs(them.x - me.x) - 0.3);
        double hz = Math.max(0, Math.abs(them.z - me.z) - 0.3);
        double eye = me.y + 1.62;
        double dy = eye < them.y ? them.y - eye : eye > them.y + 1.8 ? eye - (them.y + 1.8) : 0;
        return Math.sqrt(hx * hx + dy * dy + hz * hz);
    }

    /** Horizontal input for walking and air control, the same way vanilla turns key presses into motion. */
    private static void move(Body me, BotAction x) {
        if (me.gliding) return;
        double f = x.forward * 0.98, s = x.strafe * 0.98;
        double len = Math.hypot(f, s);
        if (len > 1) { f /= len; s /= len; }
        double speed = me.onGround ? 0.1 * (me.sprinting ? 1.3 : 1) * (me.usingShield ? 0.2 : 1) : me.sprinting ? 0.026 : 0.02;
        double yr = Math.toRadians(me.yaw);
        me.vx += (s * Math.cos(yr) - f * Math.sin(yr)) * speed;
        me.vz += (f * Math.cos(yr) + s * Math.sin(yr)) * speed;
    }

    private void physics(Body me) {
        double oldY = me.y;
        if (me.gliding) {
            double pr = Math.toRadians(me.pitch), yr = Math.toRadians(me.yaw);
            double lx = -Math.sin(yr) * Math.cos(pr), ly = -Math.sin(pr), lz = Math.cos(yr) * Math.cos(pr);
            if (me.rocketTicks > 0) {
                me.vx += lx * 0.1 + (lx * 1.5 - me.vx) * 0.5;
                me.vy += ly * 0.1 + (ly * 1.5 - me.vy) * 0.5;
                me.vz += lz * 0.1 + (lz * 1.5 - me.vz) * 0.5;
                me.rocketTicks--;
            }
            double horiz = Math.hypot(lx, lz);
            double hSpeed = Math.hypot(me.vx, me.vz);
            double cos = Math.cos(pr);
            cos = cos * cos;
            me.vy += 0.08 * (-1 + cos * 0.75);
            if (me.vy < 0 && horiz > 0) {
                double d = me.vy * -0.1 * cos;
                me.vx += lx * d / horiz;
                me.vy += d;
                me.vz += lz * d / horiz;
            }
            if (pr < 0 && horiz > 0) {
                double d = hSpeed * -Math.sin(pr) * 0.04;
                me.vx += -lx * d / horiz;
                me.vy += d * 3.2;
                me.vz += -lz * d / horiz;
            }
            if (horiz > 0) {
                me.vx += (lx / horiz * hSpeed - me.vx) * 0.1;
                me.vz += (lz / horiz * hSpeed - me.vz) * 0.1;
            }
            me.x += me.vx;
            me.y += me.vy;
            me.z += me.vz;
            me.vx *= 0.99;
            me.vy *= 0.98;
            me.vz *= 0.99;
            if (me.vy > -0.5) me.fallDistance = 1;
            else me.fallDistance += Math.max(0, oldY - me.y);
        } else if (!me.onGround) {
            me.x += me.vx;
            me.y += me.vy;
            me.z += me.vz;
            if (me.y < oldY) me.fallDistance += oldY - me.y;
            me.vy = (me.vy - 0.08) * 0.98;
            me.vx *= 0.91;
            me.vz *= 0.91;
        } else {
            me.x += me.vx;
            me.z += me.vz;
            me.vx *= 0.546;
            me.vz *= 0.546;
            me.vy = 0;
            // Walked off the roof's edge.
            if (me.y > 0 && floorAt(me) < me.y - 0.01) me.onGround = false;
        }
        // The roof: a ceiling for anyone under it, a floor for anyone on top.
        if (roof && overRoofArea(me.x, me.z)) {
            if (oldY + 1.8 <= ROOF_BOTTOM + 1e-6 && me.y + 1.8 > ROOF_BOTTOM) {
                me.y = ROOF_BOTTOM - 1.8;
                if (me.vy > 0) me.vy = 0;
            }
        }
        double floor = floorAt(me);
        if (me.onGround && !me.gliding && me.y > floor + 0.01) me.onGround = false;
        if (me.y <= floor && (!me.onGround || me.gliding)) {
            if (!me.noFallDamage && me.fallDistance > 3) me.hp -= Math.ceil(me.fallDistance - 3) * PROTECTION;
            me.y = floor;
            me.vy = 0;
            me.onGround = true;
            me.gliding = false;
            me.rocketTicks = 0;
            me.fallDistance = 0;
            me.noFallDamage = false;
        }
    }

    public static void main(String[] args) {
        BotProfile p = new BotProfile();
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        for (Kit kit : Kit.values()) {
            System.out.println(kit + " vs chaser: " + new MaceSim(1).run(n, s -> bot(p), s -> chaser(), kit, false));
            System.out.println(kit + " vs dodger: " + new MaceSim(2).run(n, s -> bot(p), s -> dodger(), kit, false));
            System.out.println(kit + " vs shield: " + new MaceSim(3).run(n, s -> bot(p), s -> shieldUp(), kit, true));
        }
    }
}
