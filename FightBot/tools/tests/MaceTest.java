import java.util.*;
import dev.reality.fightbot.core.*;

/** Checks style picking from the inventory, the mace inputs, and simulated mace duels. */
public class MaceTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "PASS " : "FAIL ") + what); if (!ok) fails++; }

    static Style.Choice pick(Loadout l) { return Style.pick(l, new BotProfile()); }

    static Observation obs(Loadout l, boolean onGround, boolean gliding, double fall, double y, double vy, String held, boolean windReady) {
        return new Observation(1, true, 0, y, 0, 0, vy, 0, 0, 90, onGround, false, false, 20, 0, held, 1.0, 1, -1, 0,
                0, 0, 0.5, 0, 0, 0, 180, true, false, false, false, 20, 0, "diamond_sword", true, 0,
                false, 2, -1, 0, 0, 0, 0.2, false, 0, 0, l, fall, gliding, windReady);
    }

    /** Opponent straight ahead at a distance, moving away (positive speed) or toward the bot (negative). */
    static Observation running(long t, Loadout l, double dist, double speed) {
        return new Observation(t, true, 0, 0, 0, 0, 0, 0, 0, 0, true, true, false, 20, 0, "diamond_sword", 1.0, 0, -1, 0,
                0, 0, dist, 0, 0, speed, 180, true, true, false, false, 20, 0, "diamond_sword", true, 0,
                l.offhand.equals("shield"), -1, -1, 0, 0, 0, 0.2, false, 0, 0, l, 0, false, true);
    }

    public static void main(String[] args) {
        // These tests check single decisions, so the human-like layer, which spreads clicks over ticks, is off.
        BotProfile.defaultHumanize = 0;
        Loadout elytraKit = Loadout.of("diamond_chestplate", "totem_of_undying", "mace", "diamond_sword", "diamond_axe", "firework_rocket*16", "elytra");
        check(pick(elytraKit).use() == Style.ELYTRA_MACE, "mace, elytra and fireworks pick elytra mace");
        Loadout wearing = Loadout.of("elytra", "air", "mace", "firework_rocket*16", "diamond_chestplate");
        check(pick(wearing).use() == Style.ELYTRA_MACE, "also works while already wearing the elytra");
        Loadout noChest = Loadout.of("elytra", "air", "mace", "firework_rocket*16", "wind_charge*8");
        check(pick(noChest).use() == Style.WIND_MACE, "no chestplate to swap to falls back to wind charges, since gliding blocks the smash");
        Loadout oneRocket = Loadout.of("diamond_chestplate", "air", "mace", "diamond_sword", "firework_rocket*1", "elytra");
        check(pick(oneRocket).use() == Style.SWORD, "one firework is not enough, so it uses the sword");
        Loadout wind = Loadout.of("diamond_chestplate", "shield", "mace", "diamond_sword", "wind_charge*12");
        check(pick(wind).use() == Style.WIND_MACE, "mace and wind charges pick wind charge mace");
        Loadout crystal = Loadout.of("diamond_chestplate", "totem_of_undying", "diamond_sword", "end_crystal*64", "obsidian*64");
        Style.Choice c = pick(crystal);
        check(c.kitIsFor() == Style.CRYSTAL && c.use() == Style.CRYSTAL, "crystal kit is recognized and fought with crystals: " + c.describe());
        check(pick(Loadout.of("diamond_chestplate", "shield", "diamond_sword", "diamond_axe")).use() == Style.SWORD_SHIELD, "sword and shield");
        Style.Choice spear = pick(Loadout.of("diamond_chestplate", "air", "diamond_spear"));
        check(spear.kitIsFor() == Style.SPEAR, "spear kit is recognized: " + spear.describe());

        BotProfile p = new BotProfile();
        ActionValidator v = new ActionValidator(p);
        BotAction glideSmash = new BotAction(); glideSmash.attack = true; glideSmash.technique = "smash"; glideSmash.yaw = 0;
        check(!v.check(obs(wearing, false, true, 10, 3, -1, "mace", true), glideSmash).attack, "no smash while gliding");
        BotAction groundGlide = new BotAction(); groundGlide.startGlide = true;
        check(!v.check(obs(wearing, true, false, 0, 0, 0, "mace", true), groundGlide).startGlide, "cannot open the elytra on the ground");
        BotAction rocket = new BotAction(); rocket.useOnce = true; rocket.hotbar = 1;
        check(!v.check(obs(wearing, false, false, 0, 2, 0.2, "mace", true), rocket).useOnce, "no firework unless gliding");
        BotAction windLaunch = new BotAction(); windLaunch.useOnce = true; windLaunch.hotbar = 4;
        check(!v.check(obs(wind, true, false, 0, 0, 0, "mace", false), windLaunch).useOnce, "no wind charge while on cooldown");

        // The brain switches style when the kit changes.
        StyleBrain brain = new StyleBrain(p, null);
        brain.step(obs(elytraKit, true, false, 0, 0, 0, "mace", true));
        Style first = brain.choice().use();
        brain.step(obs(Loadout.of("diamond_chestplate", "air", "mace", "diamond_sword", "diamond_axe", "air", "elytra"), true, false, 0, 0, 0, "mace", true));
        check(first == Style.ELYTRA_MACE && brain.choice().use() == Style.SWORD, "switches to the sword when the fireworks run out");

        MaceSim.Result e = new MaceSim(1).run(100, s -> MaceSim.bot(p), s -> MaceSim.chaser(), MaceSim.Kit.ELYTRA, false);
        System.out.println("  elytra mace vs chaser: " + e);
        check(e.winRate() >= 0.9 && e.smashes > 0, "elytra mace beats a ground sword player");
        MaceSim.Result d = new MaceSim(2).run(100, s -> MaceSim.bot(p), s -> MaceSim.dodger(), MaceSim.Kit.ELYTRA, false);
        System.out.println("  elytra mace vs dodger: " + d);
        check(d.winRate() >= 0.8, "elytra mace still lands on a player who runs out from under it");
        MaceSim.Result sh = new MaceSim(3).run(100, s -> MaceSim.bot(p), s -> MaceSim.shieldUp(), MaceSim.Kit.ELYTRA, true);
        System.out.println("  elytra mace vs shield: " + sh);
        check(sh.stunSlams > 0 && sh.winRate() >= 0.6, "axe then mace gets through a raised shield");
        BotProfile noStun = new BotProfile(); noStun.stunSlam = 0;
        MaceSim.Result shNo = new MaceSim(3).run(100, s -> MaceSim.bot(noStun), s -> MaceSim.shieldUp(), MaceSim.Kit.ELYTRA, true);
        System.out.println("  elytra mace vs shield without the axe: " + shNo);
        // With fast turning on the ground both can win every time, so the axe swap shows as a quicker win.
        check(sh.winRate() > shNo.winRate() || (sh.winRate() >= shNo.winRate() && sh.ticks < shNo.ticks * 0.8), "the axe swap is what beats the shield");
        MaceSim.Result w = new MaceSim(4).run(100, s -> MaceSim.bot(p), s -> MaceSim.chaser(), MaceSim.Kit.WIND, false);
        System.out.println("  wind mace vs chaser: " + w);
        check(w.winRate() >= 0.9 && w.windLaunches > 0, "wind charge mace beats a ground sword player");
        MaceSim.Result wd = new MaceSim(5).run(100, s -> MaceSim.bot(p), s -> MaceSim.dodger(), MaceSim.Kit.WIND, false);
        System.out.println("  wind mace vs dodger: " + wd);

        // Attribute swaps and one-tick stun slams.
        BotProfile noSwap = new BotProfile(); noSwap.attributeSwap = 0;
        MaceSim.Result ws = new MaceSim(6).run(100, s -> MaceSim.bot(p), s -> MaceSim.chaser(), MaceSim.Kit.WIND, false);
        MaceSim.Result wn = new MaceSim(6).run(100, s -> MaceSim.bot(noSwap), s -> MaceSim.chaser(), MaceSim.Kit.WIND, false);
        System.out.printf("  wind mace smash damage: %.1f with swaps, %.1f holding the mace%n", ws.avgSmash(), wn.avgSmash());
        check(ws.avgSmash() > wn.avgSmash(), "falling with a charged sword and swapping to the mace hits harder");
        check(ws.techniques.getOrDefault("swap_smash", 0) > 0, "uses swap smashes");
        MaceSim.swapsWork = false;
        MaceSim.Result patched = new MaceSim(6).run(100, s -> MaceSim.bot(p), s -> MaceSim.chaser(), MaceSim.Kit.WIND, false);
        MaceSim.Result patchedShield = new MaceSim(3).run(100, s -> MaceSim.bot(p), s -> MaceSim.shieldUp(), MaceSim.Kit.ELYTRA, true);
        MaceSim.swapsWork = true;
        System.out.println("  if Mojang fixes attribute swaps, wind mace: " + patched);
        System.out.println("  if Mojang fixes attribute swaps, elytra mace vs shield: " + patchedShield);
        check(patched.winRate() >= 0.9 && patchedShield.winRate() >= 0.9, "still wins when attribute swaps stop working");
        check(sh.winRate() >= 0.95, "the axe and mace in one tick beats a raised shield almost every time");

        // Lunge.
        // Launching from farther out misses more often, which is where the air lunge helps.
        BotProfile farLaunch = new BotProfile(); farLaunch.windLaunchDistance = 3.5;
        // This compares drops in the air, so ground turning stays at the old speed and adds no noise.
        farLaunch.fightSmoothTurn = farLaunch.smoothTurn;
        MaceSim.Result noLunge = new MaceSim(5).run(100, s -> MaceSim.bot(farLaunch), s -> MaceSim.dodger(), MaceSim.Kit.WIND, false);
        MaceSim.Result lunge = new MaceSim(5).run(100, s -> MaceSim.bot(farLaunch), s -> MaceSim.dodger(), MaceSim.Kit.WIND_LUNGE, false);
        System.out.println("  wind mace launching at 3.5 blocks vs dodger, no spear: " + noLunge);
        System.out.println("  same with a Lunge spear: " + lunge);
        check(lunge.missedDrops < noLunge.missedDrops && lunge.techniques.getOrDefault("air_lunge", 0) > 0, "air lunges fix drops that would miss");
        Loadout spearKit = Loadout.of("diamond_chestplate", "shield", "diamond_sword", "diamond_spear", "golden_apple*6");
        spearKit.lungeSlot = 1; spearKit.lungeLevel = 3;
        SwordPolicy sp = new SwordPolicy(p);
        boolean lunged = false;
        for (int t = 0; t < 4; t++) { BotAction x = sp.step(running(t, spearKit, 5.5, 0.25)); lunged |= x.jabSlot == 1 && x.technique.equals("lunge_in"); }
        check(lunged, "lunges in at a player running away");
        SwordPolicy sp2 = new SwordPolicy(p);
        boolean lunged2 = false;
        for (int t = 0; t < 4; t++) lunged2 |= sp2.step(running(t, spearKit, 5.5, -0.25)).jabSlot >= 0;
        check(!lunged2, "does not lunge into a player walking in to trade");
        spearKit.food = 6;
        SwordPolicy sp3 = new SwordPolicy(p);
        boolean lunged3 = false;
        for (int t = 0; t < 4; t++) lunged3 |= sp3.step(running(t, spearKit, 5.5, 0.25)).jabSlot >= 0;
        check(!lunged3, "no lunge at 6 hunger or less");

        // Totems and shulker boxes.
        StyleBrain tb = new StyleBrain(p, null);
        Loadout popped = Loadout.of("diamond_chestplate", "air", "diamond_sword", "golden_apple*6", "totem_of_undying*2");
        BotAction rt = tb.step(running(0, popped, 3.5, 0));
        check(rt.offhandTotem, "puts a totem back in the offhand");
        Loadout dry = Loadout.of("diamond_chestplate", "shield", "diamond_sword", "shulker_box", "air");
        BotAction far = new StyleBrain(p, null).step(running(500, dry, 20, 0));
        check(far.shulkerRestock, "restocks from a shulker box when out of apples and nobody is near");
        BotAction near = new StyleBrain(p, null).step(running(500, dry, 6, 0));
        check(!near.shulkerRestock, "never opens a shulker box with the opponent close");

        // Potions.
        Loadout pots = Loadout.of("netherite_chestplate", "shield", "netherite_sword", "golden_apple*64",
                "splash_potion:strong_healing", "splash_potion:strong_strength", "splash_potion:strong_swiftness");
        BotAction buff = new StyleBrain(p, null).step(running(10, pots, 12, 0));
        check(buff.useOnce && buff.pitch == 90 && buff.technique.contains("strength") && buff.hotbar == 3, "splashes strength at its feet");
        // A new round pots up first, even with the target right there, before it fights.
        StyleBrain fresh = new StyleBrain(p, null);
        List<String> potted = new ArrayList<>();
        for (int t = 0; t < 12; t++) {
            BotAction x = fresh.step(running(t, pots, 2.0, 0));
            if (x.useOnce && x.technique.startsWith("pot_up_")) potted.add(x.technique);
        }
        check(potted.contains("pot_up_strength") && potted.contains("pot_up_speed"), "pots up with every buff at the start of a round: " + potted);
        Loadout buffed = Loadout.of("netherite_chestplate", "shield", "netherite_sword", "golden_apple*64",
                "splash_potion:strong_healing", "splash_potion:strong_strength", "splash_potion:strong_swiftness");
        buffed.effects = java.util.Map.of("strength", 3000, "speed", 3000, "fire_resistance", 3000);
        check(!new StyleBrain(p, null).step(running(10, buffed, 12, 0)).useOnce, "does not waste pots while buffed");
        Observation low = new Observation(10, true, 0, 0, 0, 0, 0, 0, 0, 0, true, true, false, 6, 0, "netherite_sword", 0.3, 0, -1, 0,
                0, 0, 3, 0, 0, 0, 180, true, true, false, false, 20, 0, "netherite_sword", true, 0,
                true, -1, -1, 0, 0, 0, 0.2, false, 0, 0, buffed, 0, false, true);
        BotAction heal = new StyleBrain(p, null).step(low);
        check(heal.useOnce && heal.technique.equals("pot_heal") && heal.hotbar == 2, "splashes healing when low, even up close");
        Loadout drink = Loadout.of("netherite_chestplate", "shield", "netherite_sword", "golden_apple*64", "potion:long_fire_resistance");
        drink.effects = java.util.Map.of("strength", 3000, "speed", 3000);
        StyleBrain db = new StyleBrain(p, null);
        BotAction d1 = db.step(running(10, drink, 15, 0));
        BotAction d2 = db.step(running(11, drink, 15, 0));
        BotAction d3 = db.step(running(12, drink, 2.5, 0));
        check(d1.use && d2.use && d2.technique.equals("drink_potion") && !"drink_potion".equals(d3.technique), "drinks with room and stops when the opponent closes in");
        check(new ActionValidator(p).check(running(10, drink, 15, 0), d1).use, "the validator allows drinking a potion");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        System.exit(fails == 0 ? 0 : 1);
    }
}
