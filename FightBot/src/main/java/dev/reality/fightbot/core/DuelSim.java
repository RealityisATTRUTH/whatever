package dev.reality.fightbot.core;

import java.util.Locale;
import java.util.Random;
import java.util.function.Function;

/**
 * A small sword duel simulator for testing policies offline, without Minecraft. It uses the
 * vanilla attack-charge damage rule, a sword cooldown, crits, sprint knockback that needs a sprint
 * reset, hit invulnerability, and golden apples. Movement and knockback are simplified.
 * Run it with: java -cp fightbot.jar dev.reality.fightbot.core.DuelSim [duels] [profile file]
 */
public final class DuelSim {

    public interface Brain {
        BotAction decide(Observation o);
    }

    static final class Agent {
        final String name;
        final Brain brain;
        double x, y = 64, z, vx, vy, vz;
        float yaw, pitch;
        boolean onGround = true, sprinting, usingItem;
        double hp = 20, absorption;
        int gapples;
        int ticker = 100, invuln;
        int slot;
        int eatTicks;
        boolean sprintSpent;
        boolean prevForwardStopped;
        boolean hasShield, hasAxe, axeOnly;
        int egaps;
        int raising;
        long disabledUntil = -1;
        boolean blocking;
        long now;
        final java.util.Map<String, Integer> tech = new java.util.TreeMap<>();
        int blockedHits, shieldsBroken;
        double dealt, taken;
        int swings, landed, crits, sprintHits;

        Agent(String name, Brain brain, int gapples) {
            this.name = name;
            this.brain = brain;
            this.gapples = gapples;
        }

        double charge() {
            return Math.min(1, (ticker + 0.5) / (axe() ? 20.0 : 12.5));
        }

        boolean axe() {
            return slot == 2 && hasAxe;
        }

        String main() {
            if (slot == 1 && gapples > 0) return "golden_apple";
            if (slot == 3 && egaps > 0) return "enchanted_golden_apple";
            if (axe()) return "diamond_axe";
            return axeOnly ? "air" : "diamond_sword";
        }
    }

    public static final class Result {
        public final java.util.Map<String, Integer> aTech = new java.util.TreeMap<>();
        public double aTaken;
        public long aBlocked, aShieldsBroken, bBlocked;
        public int aWins, bWins, draws;
        public double aDealt, bDealt;
        public long aSwings, aLanded, aCrits, aSprintHits;
        public long bSwings, bLanded;

        public double aWinRate() {
            int n = aWins + bWins + draws;
            return n == 0 ? 0 : (double) aWins / n;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "A won %d, B won %d, draws %d (A win rate %.0f%%). A landed %d of %d swings, %d crits, %d sprint hits. B landed %d of %d.",
                    aWins, bWins, draws, aWinRate() * 100, aLanded, aSwings, aCrits, aSprintHits, bLanded, bSwings);
        }
    }

    private final Random rng;

    public DuelSim(long seed) {
        this.rng = new Random(seed);
    }

    public static Brain bot(BotProfile profile) {
        SwordPolicy policy = new SwordPolicy(profile);
        ActionValidator v = new ActionValidator(profile);
        return o -> v.check(o, policy.step(o));
    }

    /** Clicks every 4 ticks when in reach, walks straight in, never strafes. */
    public static Brain spamClicker() {
        return o -> {
            BotAction a = new BotAction();
            a.yaw = o.yawToOpponent();
            a.pitch = o.pitchToOpponent();
            a.forward = o.distance() > 1.5 ? 1 : 0;
            a.sprint = true;
            a.attack = o.tick() % 4 == 0 && o.distance() < 3.2;
            return a;
        };
    }

    /** Waits for full charge but never resets sprint, strafes, or heals. */
    public static Brain plainSprinter() {
        return o -> {
            BotAction a = new BotAction();
            a.yaw = o.yawToOpponent();
            a.pitch = o.pitchToOpponent();
            a.forward = o.distance() > 2.5 ? 1 : 0;
            a.sprint = true;
            a.attack = o.charge() >= 0.95 && o.distance() < 3.2;
            return a;
        };
    }

    /** What each side carries. */
    public record Kit(int gapples, int egaps, boolean aShield, boolean aAxe, boolean bShield, boolean bAxe) {
    }

    public Result run(int duels, Function<Long, Brain> a, Function<Long, Brain> b, int gapples) {
        return run(duels, a, b, new Kit(gapples, 0, false, false, false, false));
    }

    /** Side A carries an axe and no sword, so the axe is its main weapon. */
    public boolean aAxeOnly;

    public Result run(int duels, Function<Long, Brain> a, Function<Long, Brain> b, Kit kit) {
        Result r = new Result();
        for (int i = 0; i < duels; i++) {
            Agent pa = new Agent("A", a.apply((long) i), kit.gapples());
            Agent pb = new Agent("B", b.apply((long) i + 1000), kit.gapples());
            pa.egaps = pb.egaps = kit.egaps();
            pa.hasShield = kit.aShield(); pa.hasAxe = kit.aAxe();
            pa.axeOnly = aAxeOnly && kit.aAxe();
            if (pa.axeOnly) pa.slot = 2;
            pb.hasShield = kit.bShield(); pb.hasAxe = kit.bAxe();
            double start = 6 + rng.nextDouble() * 4;
            pa.x = 0; pa.z = 0;
            pb.x = (rng.nextDouble() - 0.5) * 2; pb.z = start;
            pa.yaw = 0; pb.yaw = 180;
            int t = 0;
            for (; t < 20 * 120 && pa.hp > 0 && pb.hp > 0; t++) tick(t, pa, pb);
            if (pa.hp <= 0 && pb.hp > 0) r.bWins++;
            else if (pb.hp <= 0 && pa.hp > 0) r.aWins++;
            else r.draws++;
            r.aDealt += pa.dealt; r.bDealt += pb.dealt;
            r.aSwings += pa.swings; r.aLanded += pa.landed; r.aCrits += pa.crits; r.aSprintHits += pa.sprintHits;
            r.bSwings += pb.swings; r.bLanded += pb.landed;
            r.aTaken += pa.taken;
            r.aBlocked += pa.blockedHits; r.bBlocked += pb.blockedHits; r.aShieldsBroken += pa.shieldsBroken;
            for (var e : pa.tech.entrySet()) r.aTech.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        return r;
    }

    private void tick(int t, Agent a, Agent b) {
        a.now = t;
        b.now = t;
        BotAction aa = a.brain.decide(observe(t, a, b));
        BotAction ba = b.brain.decide(observe(t, b, a));
        apply(aa, a, b);
        apply(ba, b, a);
        physics(a);
        physics(b);
        a.ticker++;
        b.ticker++;
        if (a.invuln > 0) a.invuln--;
        if (b.invuln > 0) b.invuln--;
    }

    private Observation observe(long t, Agent me, Agent op) {
        return new Observation(t, true, me.x, me.y, me.z, me.vx, me.vy, me.vz, me.yaw, me.pitch, me.onGround, me.sprinting,
                me.usingItem, (float) me.hp, (float) me.absorption, me.main(), me.charge(), me.axeOnly ? 2 : 0, me.gapples > 0 ? 1 : -1, me.gapples,
                op.x, op.y, op.z, op.vx, op.vy, op.vz, op.yaw, op.onGround, op.sprinting, op.blocking, op.usingItem,
                (float) op.hp, (float) op.absorption, op.main(), true, 0,
                me.hasShield, me.hasAxe ? 2 : -1, me.egaps > 0 ? 3 : -1, me.egaps, me.gapples, me.egaps,
                op.charge(), op.hasShield, op.gapples, op.egaps);
    }

    private void apply(BotAction act, Agent me, Agent op) {
        me.yaw = act.yaw;
        me.pitch = act.pitch;
        if (act.technique != null && !act.technique.isEmpty()) me.tech.merge(act.technique, 1, Integer::sum);
        // Shield: takes 5 ticks to come up, cannot be held while attacking or eating, disabled by axes.
        boolean canBlock = act.block && me.hasShield && me.now >= me.disabledUntil && !act.attack && !act.use;
        me.raising = canBlock ? me.raising + 1 : 0;
        me.blocking = me.raising >= 5;
        if (act.hotbar >= 0 && act.hotbar != me.slot) {
            me.slot = act.hotbar;
            me.ticker = 0;
        }
        // Eating golden apples and enchanted golden apples.
        boolean eatingGap = act.use && me.slot == 1 && me.gapples > 0;
        boolean eatingEgap = act.use && me.slot == 3 && me.egaps > 0;
        if (eatingGap || eatingEgap) {
            me.usingItem = true;
            if (++me.eatTicks >= 32) {
                if (eatingEgap) {
                    me.egaps--;
                    me.hp = Math.min(20, me.hp + 8);
                    me.absorption = 16;
                } else {
                    me.gapples--;
                    me.hp = Math.min(20, me.hp + 4);
                    me.absorption = 4;
                }
                me.eatTicks = 0;
                me.usingItem = false;
            }
        } else {
            me.usingItem = me.raising > 0;
            me.eatTicks = 0;
        }
        // Sprint needs a reset after each sprint-knockback hit.
        boolean stopped = act.forward <= 0 || !act.sprint;
        if (stopped) me.sprintSpent = false;
        me.sprinting = act.sprint && act.forward > 0 && !me.usingItem;

        double speed = me.raising > 0 ? 0.065 : me.usingItem ? 0.04 : me.sprinting ? 0.28 : 0.215;
        double r = Math.toRadians(me.yaw);
        double fx = -Math.sin(r), fz = Math.cos(r);
        double lx = fz, lz = -fx; // left of facing
        double tx = (fx * act.forward + lx * act.strafe), tz = (fz * act.forward + lz * act.strafe);
        double len = Math.hypot(tx, tz);
        if (len > 0) { tx = tx / len * speed; tz = tz / len * speed; }
        double grip = me.onGround ? 0.5 : 0.08;
        me.vx += (tx - me.vx) * grip;
        me.vz += (tz - me.vz) * grip;
        if (act.jump && me.onGround) {
            me.vy = 0.42;
            me.onGround = false;
            if (me.sprinting) { me.vx += fx * 0.2; me.vz += fz * 0.2; }
        }

        if (act.attack) {
            me.swings++;
            double c = me.charge();
            me.ticker = 0;
            double dist = Math.hypot(op.x - me.x, op.z - me.z) - 0.3;
            boolean aimed = Angles.diff(me.yaw, (float) Math.toDegrees(Math.atan2(-(op.x - me.x), op.z - me.z))) < 35;
            // Hits are not certain: farther and fast-strafing targets get missed more, like real aim.
            double ux = (op.x - me.x), uz = (op.z - me.z), ul = Math.max(0.01, Math.hypot(ux, uz));
            double lateral = Math.abs(ux / ul * op.vz - uz / ul * op.vx);
            double pHit = Math.max(0.2, Math.min(0.97, 0.97 - 0.15 * Math.max(0, dist - 2.0) - 1.5 * lateral));
            boolean connects = rng.nextDouble() < pHit;
            // A raised shield stops a hit from the front. An axe hit disables it for 5 seconds instead.
            double ofx = -Math.sin(Math.toRadians(op.yaw)), ofz = Math.cos(Math.toRadians(op.yaw));
            boolean fromFront = ofx * (me.x - op.x) + ofz * (me.z - op.z) > 0;
            if (dist <= 3.0 && aimed && op.blocking && fromFront && !me.usingItem && connects) {
                op.blockedHits++;
                if (me.axe()) {
                    op.disabledUntil = me.now + 100;
                    op.blocking = false;
                    op.raising = 0;
                    me.shieldsBroken++;
                }
                return;
            }
            if (dist <= 3.0 && aimed && op.invuln <= 0 && !me.usingItem && connects) {
                boolean crit = c > 0.9 && !me.onGround && me.vy < 0 && !me.sprinting;
                double dmg = (me.axe() ? 9 : 7) * (0.2 + 0.8 * c * c) * (crit ? 1.5 : 1) * 0.55;
                double absorbed = Math.min(op.absorption, dmg);
                op.absorption -= absorbed;
                op.hp -= dmg - absorbed;
                op.invuln = 10;
                me.dealt += dmg;
                op.taken += dmg;
                me.landed++;
                if (crit) me.crits++;
                op.usingItem = false;
                op.eatTicks = 0;
                double kb = 0.35;
                if (me.sprinting && c > 0.9 && !me.sprintSpent) {
                    kb += 0.45;
                    me.sprintHits++;
                    me.sprintSpent = true;
                    me.vx *= 0.6;
                    me.vz *= 0.6;
                }
                double dx = op.x - me.x, dz = op.z - me.z, d = Math.max(0.01, Math.hypot(dx, dz));
                op.vx = dx / d * kb;
                op.vz = dz / d * kb;
                op.vy = Math.max(op.vy, 0.36);
                op.onGround = false;
            }
        }
    }

    private static void physics(Agent me) {
        me.x += me.vx;
        me.z += me.vz;
        if (!me.onGround) {
            me.y += me.vy;
            me.vy = (me.vy - 0.08) * 0.98;
            if (me.y <= 64) { me.y = 64; me.vy = 0; me.onGround = true; }
        }
        if (me.onGround) { me.vx *= 0.6; me.vz *= 0.6; }
        else { me.vx *= 0.91; me.vz *= 0.91; }
    }

    /** Full-charge sword player who raises a shield when the other side's bar is nearly full. */
    public static Brain shieldSwordsman() {
        return o -> {
            BotAction a = new BotAction();
            a.yaw = o.yawToOpponent();
            a.pitch = o.pitchToOpponent();
            a.forward = o.distance() > 2.5 ? 1 : 0;
            a.sprint = true;
            a.attack = o.charge() >= 0.95 && o.distance() < 3.2;
            a.block = !a.attack && o.distance() < 3.6 && !Double.isNaN(o.oCharge()) && o.oCharge() > 0.8;
            return a;
        };
    }

    /** Holds the shield up all the time and only lowers it to swing at full charge. */
    public static Brain shieldTurtle() {
        return o -> {
            BotAction a = new BotAction();
            a.yaw = o.yawToOpponent();
            a.pitch = o.pitchToOpponent();
            a.forward = o.distance() > 2.5 ? 1 : 0;
            a.attack = o.charge() >= 0.95 && o.distance() < 3.2;
            a.block = !a.attack;
            return a;
        };
    }

    /** Full-charge axe player with no shield. */
    public static Brain axeUser() {
        return o -> {
            BotAction a = new BotAction();
            a.yaw = o.yawToOpponent();
            a.pitch = o.pitchToOpponent();
            a.hotbar = 2;
            a.forward = o.distance() > 2.5 ? 1 : 0;
            a.sprint = true;
            a.attack = o.mainHand().endsWith("_axe") && o.charge() >= 0.95 && o.distance() < 3.2;
            return a;
        };
    }

    public static void main(String[] args) throws Exception {
        int duels = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        BotProfile profile = args.length > 1 ? BotProfile.load(java.nio.file.Path.of(args[1])) : new BotProfile();
        DuelSim sim = new DuelSim(42);
        System.out.println("Profile " + profile.name + " against a spam clicker: "
                + sim.run(duels, s -> bot(withSeed(profile, s)), s -> spamClicker(), 2));
        System.out.println("Profile " + profile.name + " against a plain sprinter: "
                + sim.run(duels, s -> bot(withSeed(profile, s)), s -> plainSprinter(), 2));
        System.out.println("Profile " + profile.name + " against the default bot: "
                + sim.run(duels, s -> bot(withSeed(profile, s)), s -> bot(withSeed(new BotProfile(), s + 7)), 2));
    }

    static BotProfile withSeed(BotProfile base, long seed) {
        BotProfile p = new BotProfile();
        for (java.lang.reflect.Field f : BotProfile.class.getFields()) {
            try { f.set(p, f.get(base)); } catch (ReflectiveOperationException ignored) { }
        }
        p.seed = base.seed * 31 + seed;
        return p;
    }
}
