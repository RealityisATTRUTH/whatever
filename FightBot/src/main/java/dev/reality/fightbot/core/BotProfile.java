package dev.reality.fightbot.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * The bot's fighting style as numbers. Defaults are a careful sword player. A profile can be
 * calibrated from a recorded player with the calibrate script, so the bot fights like them.
 */
public final class BotProfile {
    public String name = "default";
    /** Distance it tries to hold while trading hits. */
    public double preferredDistance = 2.7;
    /** Closer than this it S-taps back out. */
    public double tooClose = 1.8;
    /** Attack reach it trusts. Vanilla reach is 3 blocks from the eyes. */
    public double reach = 3.0;
    /** Only swing when the attack bar is at least this full. */
    public double chargeThreshold = 0.94;
    /** Only swing when aimed within this many degrees of the opponent. */
    public double maxAimError = 25;
    public double wTapChance = 0.8;
    public double sTapChance = 0.6;
    public double jumpResetChance = 0.5;
    /**
     * Chance to jump in for a crit when closing in with the bar nearly full. Off by default: in the
     * duel simulator, jumping in lost the first-hit race to a W-tapping opponent almost every time.
     */
    public double critJumpChance = 0.0;
    /** With an axe as the main weapon: chance to jump in place for a crit as the bar fills. */
    public double axeCritChance = 0.8;
    /** Attack bar level where the axe crit jump starts, so the bar is full on the way down. */
    public double axeCritJumpAt = 0.5;
    public int strafeSwitchMin = 8;
    public int strafeSwitchMax = 24;
    /** Fastest turn per tick, so aim looks like a player instead of a snap. */
    public double maxTurnPerTick = 40;
    /** Fastest turn per tick for sword and axe fights, where coming round slowly gets it hit from the side. */
    public double swordTurnPerTick = 55;
    public double aimJitter = 1.5;
    /** Follow a target who towers up: 1 on, 0 off. */
    public int followTowers = 1;
    /** How far above the bot the target has to stand, in blocks, before it follows. */
    public double towerTrigger = 1.6;
    /** Farthest the target can be, sideways, for it to follow up a tower. */
    public double towerFollowRange = 10;
    /** Pillars once it is this close sideways to the target, or once walking closer stops working, and walks up to the tower before that. */
    public double pillarFrom = 1.5;
    /** Throws a pearl up instead of building when the target is at least this high. */
    public double pearlUpHeight = 4;
    /** Finds a way around walls, through doors, and through blocks: 1 on, 0 off. */
    public int navigate = 1;
    /** Ticks between new route plans. */
    public int replanTicks = 20;
    /** How close a route has to end to the target. */
    public double pathReach = 2.5;
    /** Most cells a route plan looks at. */
    public int pathNodes = 4000;
    /** Blows walls open with a crystal when no tool mines them fast: 1 on, 0 off. */
    public int blastWalls = 1;
    /** Health plus absorption needed before it sets off a crystal at a wall. */
    public double blastMinHealth = 12;
    /** How far it steps back from its own wall crystal before hitting it. */
    public double blastBackOff = 2.6;
    /** Throws a pearl to close in on a far target: 1 on, 0 off. */
    public int pearlChase = 1;
    /** Pearls in once the target is this far away. */
    public double pearlChaseBeyond = 14;
    /** With an elytra and rockets, it still pearls in from this far, since a pearl gets there first. */
    public double pearlFlyerBeyond = 22;
    /** With no clear throw onto the target, it pearls to open ground this close to them. */
    public double pearlNearRadius = 5;
    /** Farthest it pearls. */
    public double pearlMaxRange = 50;
    /** 1 to throw a pearl most of the way to a target out of pearl range, up to twice the range. */
    public int pearlCloser = 1;
    /** Health plus absorption needed before a pearl, since each one costs 5 health. */
    public double pearlMinHealth = 10;
    /** Wind mace pearl catch: 1 on, 0 off. */
    public int pearlCatch = 1;
    /** Closest it pearl catches from. Closer than this, a plain wind launch is faster. */
    public double pearlCatchFrom = 7;
    /** Ticks before the pearl lands that the wind charge goes off. */
    public int pearlCatchLead = 3;
    /** Saves itself from falls into the void or long drops: 1 on, 0 off. */
    public int fallRescue = 1;
    /** A drop this long counts as dangerous whatever its health. */
    public double dangerDrop = 20;
    /** Share of the raw fall damage it expects to take. 1 is no armor help, which is the safe guess. */
    public double fallDamageScale = 1.0;
    /** Wind mace: throw a pearl up and catch it on the way up for a higher launch: 1 on, 0 off. */
    public int ownPearlCatch = 1;
    /** Fewest blocks of extra height that make catching its own pearl worth it. */
    public double ownCatchMinGain = 0.8;
    /** Pearls away from the target to get room for a restock: 1 on, 0 off. */
    public int pearlToRestock = 1;
    /** How far from the target it tries to land when pearling away to restock. */
    public double restockPearlDistance = 20;
    /** Free-for-all: how far away it looks for players to fight. */
    public double everyoneRange = 32;
    /** Fights back against mobs that hit it: 1 on, 0 off. */
    public int fightMobs = 1;
    /** Comes up for air when this many ticks of breath are left, out of 300. */
    public int surfaceAirAt = 90;
    /** Trident: ticks to hold right click before letting go, which the game needs for a throw or a Riptide dash. */
    public int tridentChargeTicks = 10;
    /** Trident: farthest it throws a Loyalty trident. */
    public double tridentRange = 30;
    /** Riptide: closest and farthest it dashes at the target. */
    public double riptideMin = 4;
    public double riptideMax = 18;
    /** UHC: ticks the lava stays down before it takes it back. */
    public int lavaHoldTicks = 40;
    /** UHC: ticks between lava pours. */
    public int lavaCooldown = 100;
    /** UHC: closest it pours lava, so the lava does not reach it. */
    public double lavaMinDistance = 2.2;
    /** UHC: farthest it webs the target. */
    public double webRange = 4.0;
    /** UHC: ticks between webs on the target. */
    public int webCooldown = 80;
    /** UHC: health plus absorption at or under which it webs itself to stop a combo. */
    public double selfWebHealth = 8;
    /** UHC: ticks it stays in its own web before cutting out. */
    public int selfWebHoldTicks = 40;
    /** Bow and crossbow: switches to the sword when the target is this close. */
    public double rangedMeleeWithin = 5;
    /** Farthest it shoots a bow. */
    public double bowRange = 40;
    /** Farthest it fires a loaded crossbow. */
    public double crossbowRange = 30;
    /** Ticks to fully draw a bow. */
    public int bowDrawTicks = 20;
    /** Ticks to load the crossbow: 25 minus 5 for each Quick Charge level. The kit has Quick Charge III. */
    public int crossbowChargeTicks = 10;
    /** Repairs Mending gear with bottles o' enchanting: 1 on, 0 off. */
    public int repairWithXp = 1;
    /** Starts repairing when the most worn Mending piece has this share of its durability left. */
    public double repairAt = 0.35;
    /** Keeps throwing until everything is back to this share. */
    public double repairTo = 0.9;
    /** Below this share it repairs even with the opponent 5 blocks away, since the piece is about to break. */
    public double repairCritical = 0.12;
    /** Opponent at least this far away to stop and repair. */
    public double repairSafeDistance = 12;
    /** Ticks between bottles. */
    public int repairThrowTicks = 2;
    /** Puts out fire with a water bucket: 1 on, 0 off. */
    public int useWater = 1;
    /** Ticks before it picks the water back up. */
    public int waterPickupTicks = 4;
    /** Shulker restock when totems drop to this many. */
    public int restockTotemsAt = 1;
    /** Shulker restock when crystals, obsidian, pearls, and the like drop to this share of what it started with. */
    public double restockShare = 0.2;
    /** Look like a person at a mouse and keyboard: 1 on, 0 off. */
    public int humanize = defaultHumanize;
    /**
     * Elytra mace: the follow-up starts at once when its smash knocked the target off the ground this many ticks
     * ago. Off by default: in the simulator, chasing every small knock-up lost fights. A target really launched
     * into the air still gets the follow-up.
     */
    public int followAfterSmashTicks = -1;
    /** Starting value for humanize. The decision tests turn it off, since they check single ticks. */
    public static int defaultHumanize = 1;
    /** Fastest turn in one tick, in degrees. A fast human flick is about this. */
    public double maxFlickPerTick = 70;
    /** A click that needs aim waits until the crosshair is within this many degrees of where it should be. */
    public double clickAimTolerance = 4;
    /** Longest a click waits for the crosshair, in ticks. */
    public int holdClickTicks = 3;
    /** Clicks per second, left and right each. */
    public double maxClicksPerSecond = 16;
    /** Mouse sensitivity, 0 to 1, where 0.5 is the game's 100%. Turns move in that mouse's steps. */
    public double mouseSensitivity = 0.5;
    /** Ticks before a popped totem goes back in the offhand. */
    public int retotemTicks = 3;
    /** Fewest ticks between inventory moves. */
    public int inventoryMoveTicks = 4;
    /** Share of the remaining turn made each tick when not clicking. Lower is smoother, 0 turns it off. */
    public double smoothTurn = 0.5;
    /** Share of the turn taken each tick within fightTurnRange blocks of the target, so it comes round fast up close. */
    public double fightSmoothTurn = 0.85;
    public double fightTurnRange = 6;
    /** Ticks a strafe direction is held before it can reverse. */
    public int strafeHoldTicks = 4;
    /** Eat when at or below this health and there is room. */
    public double healAt = 9;
    /** Distance needed before it starts eating. */
    public double safeEatDistance = 5.5;
    /** Back off when at or below this health with no healing left. */
    public double disengageAt = 4;
    /** Raise the shield when the opponent is this close and their attack bar is at least blockAtOppCharge. */
    public double blockRange = 5.0;
    public double blockAtOppCharge = 0.5;
    /** Raise the shield against a bow or crossbow being drawn from farther than this. */
    public double blockBowsBeyond = 4;
    /**
     * When both bars are full, keep the shield up for up to this many ticks so the opponent swings
     * into it first, then hit back while they are on cooldown. 0 turns this off.
     */
    public int baitPatience = 15;
    /** Elytra mace: fewest fireworks needed to pick the style. */
    public int minFireworks = 2;
    /** Elytra mace: height above the opponent to climb to before dropping. */
    public double maceHeight = 16;
    /**
     * Fast elytra mace: climb only about fastClimbHeight blocks over the target, swap to the chestplate for a
     * moment, put the elytra back on and open it, rocket straight down at the target, swap to the chestplate a
     * few blocks out, and smash. 1 on, 0 for the old high climb.
     */
    public int fastElytra = 1;
    /** Fast dive: skip the rocket down when it would leave fewer ticks than this for the attack bar to refill. 0 always fires it. */
    public int fastChargeTicks = 12;
    /** Go for crit smashes: no sprint while falling, and wait for a full bar if the target stays in reach. 1 on, 0 off. */
    public int critSmash = 1;
    public double fastClimbHeight = 12;
    /** Fast mode: ticks of chestplate free fall at the top before the elytra goes back on. */
    public int fastDropTicks = 0;
    /** Fast mode: the chestplate goes on for the smash when the target is this close, or this far below. */
    public double fastDropDistance = 4.0, fastDropHeight = 2.5;
    /** Fast mode: reopen the elytra when a drop is about to miss: 1 on, 0 off. */
    public int fastBailout = 0;
    /** Fast mode: a follow-up from below climbs this high over the target before it turns and dives. */
    public double fastFollowHeight = 5;
    /** Fast mode: ticks before reaching the target that the chestplate goes on, at the dive's current speed. */
    public double fastDropTicksAhead = 3;
    /** Elytra mace: below this height above the opponent it rockets again instead of dropping. */
    public double maceMinHeight = 9;
    /** Wind charge mace: distance to the opponent at which it launches itself. */
    public double windLaunchDistance = 5.0;
    /** 1 to hold the smash until the last tick in reach, for a longer fall and more damage. */
    public int waitForDeeperSmash = 1;
    /** 1 to swap to an axe mid-fall to disable a raised shield, then smash with the mace. */
    public int stunSlam = 1;
    /**
     * 1 to use attribute swaps: switching items inside the tick of a hit, so the hit keeps the first item's
     * attack bar and damage while the second item adds its own effect. Mojang calls this an unsupported
     * exploit and may fix it. When it stops working the bot still lands the hits, just without the bonus.
     */
    public int attributeSwap = 1;
    /** 1 to dash with a Lunge spear: to close the gap on the ground, and to fix drops that will miss. */
    public int useLunge = 1;
    /** Ground lunge: distance band where it lunges in. */
    public double lungeMin = 3.8, lungeMax = 7;
    /** 1 to restock from shulker boxes when nobody is this close. */
    public int shulkerRestock = 1;
    public double shulkerSafeDistance = 14;
    /** 1 to put a totem back in the offhand after one pops. */
    public int retotem = 1;
    /** 1 to use potions: buff up with strength, speed, and fire resistance, and splash healing when low. */
    public int usePotions = 1;
    /** Splash a healing potion at or below this much health plus absorption. */
    public double potHealAt = 8;
    /** Pots only: this many healing pots or more turns on pot play. */
    public int potsModeAt = 6;
    /** Pots only: heals at this health, since a pot heals 8 and anything above 12 wastes some. */
    public double potsHealAt = 12;
    /** Pots only: at or under this it pots right away, twice if needed, wherever the opponent is. */
    public double potsCritical = 6;
    /** Pots only: an opponent closer than this shares the splash, so it backs off first. */
    public double potStealRange = 4;
    /** Pots only: longest it backs off before potting anyway. */
    public int potRetreatTicks = 15;
    /** Pots only: healing pots it keeps in the hotbar. */
    public int potsHotbarMin = 3;
    /** Only buff up when the opponent is at least this far, or eating. */
    public double potSafeDistance = 7;
    /** Buff again when an effect has fewer than this many ticks left: 200 is 10 seconds. Never earlier, so no potion is wasted. */
    public int rebuffTicks = 200;
    /**
     * 1 to use the elytra, chestplate, elytra, chestplate, axe, mace combo for elytra drops: a re-glide in
     * the middle of the drop to steer back over the target while the fall distance keeps building.
     */
    public int combo = 1;
    /** Ticks to wait for a swap or glide to show up before retrying, and how many retries. */
    public int comboConfirmTicks = 3, comboRetries = 1;
    /** Fewest blocks of fall for a smash. Below this it abandons instead of swinging. */
    public double comboMinFall = 3;
    /** Re-glide when the first drop would miss by more than this, with at least this much height left. */
    public double comboRegripMiss = 2.0, comboRegripHeight = 6;
    /** Ticks of falling after the first swap before a re-glide is considered. */
    public int comboFirstDropTicks = 4;
    /** Ticks an axe needs to fully charge after being selected. */
    public int axeChargeTicks = 20;
    /** Dive pitch during the re-glide. Steep enough to keep falling faster than half a block per tick. */
    public double comboDivePitch = 60;
    /** Swap to the chestplate for the final drop at this height above the target at the latest. */
    public double comboFinalDropHeight = 5;
    /** Abandon when the target gets this far away, or health drops this low. */
    public double comboAbortDistance = 14, comboAbortHealth = 6;
    /** 1 to get out of blast range and heal when low against someone using crystals or anchors. Mace styles only. */
    public int blastGuard = 1;
    /** Start getting away at this much health, absorption included, and come back at this much. */
    public double blastRetreatHealth = 10, blastResumeHealth = 17;
    /** Distance from them that counts as out of blast range, for eating. A blast reaches about 12 blocks. */
    public double blastSafeDistance = 20;
    /** How long an opponent counts as a crystal player after last holding crystals or anchors. */
    public int blastMemoryTicks = 400;
    /** Give up healing and fight again after this long. */
    public int blastMaxRetreatTicks = 400;
    /** Gliding away, climb while lower than this above the ground, and rocket no more often than this. */
    public double blastCruiseHeight = 12;
    public int blastRocketTicks = 25;
    /** 1 to take off again instead of fighting a crystal player on foot. */
    public int blastNoGroundFight = 1;
    /** 1 to chase a target that is up in the air with a quick elytra, rocket, chestplate, axe, mace follow-up. */
    public int followUp = 1;
    /** Only follow up on targets this close. */
    public double followRange = 10;
    /** Follow-up rockets aim this many blocks above the target. */
    public double followAbove = 2.5;
    /** Elytra mace follow-up: how high over the target it gets before dropping onto them. */
    public double followDropHeight = 1.0;
    /** Ticks a roof over the target is remembered while they stay near where it covered them, so stepping out to the edge does not start a takeoff. */
    public int coverMemoryTicks = 100;
    /** How far from the covered spot the target can step and still count as covered. */
    public double coverMemoryRadius = 6;
    /** Fewest blocks of fall for a follow-up smash. Short on purpose, since the follow-up is about speed. */
    public double followMinFall = 1.6;
    /** Ticks a hit player is protected from smaller hits. A follow-up smash waits this long after the last hit. */
    public int hurtCooldownTicks = 10;
    /**
     * Open space needed above the opponent's head for a mace drop. With less, a roof is in the way, so the
     * bot fights on foot with the sword and knocks them out from under it instead.
     */
    public double minDropHeadroom = 6;
    /** Random spread in climb height for each elytra launch, so drops are harder to time. */
    public double heightJitter = 4;
    /** Elytra mace fights on foot, chestplate on, when the target is on the ground this close. */
    public double elytraGroundFight = 3.5;
    /** Raise the shield only with at least this many ticks before its own next swing. It needs five to block. */
    public double shieldUsefulTicks = 5;
    /** Leave axe mode this many ticks after the opponent's shield went down. */
    public int axeDropTicks = 8;
    /** Crystal PvP: block reach for clicking faces, and entity reach for hitting crystals. */
    public double placeReach = 4.5, breakReach = 3.0;
    /** Smallest damage to the target worth a crystal or anchor, and the most the bot will take from it. */
    public double crystalMinDamage = 4, crystalMaxSelf = 16;
    /** Damage to the target minus weighted self damage must be at least this. */
    public double crystalMinNet = 4;
    /** Self damage counts this much against target damage when choosing. */
    public double selfDamageWeight = 1.3;
    /** Never take a blast that would leave less than this much health plus absorption. */
    public double crystalSafetyHealth = 2;
    /** Ticks ahead to predict where the target will be for placements. */
    public int crystalLead = 2;
    /** Wait on the ground for someone flying with an elytra instead of chasing them: 1 on, 0 off. */
    public int flyerWait = 1;
    /** Ninja bridge over gaps instead of sneaking the whole way: 1 on, 0 off. */
    public int speedBridge = 1;
    /** Use a trident in any style, not only the trident style: Riptide dashes when wet, Loyalty throws from range. 1 on, 0 off. */
    public int tridentAddOn = 1;
    /** Other styles only throw a Loyalty trident at someone farther than this, in blocks. */
    public double tridentThrowBeyond = 8;
    /** Ticks the route keeps control after the way ahead looks open again, so steering does not flip back and forth. */
    /** How many path cells ahead it may steer at, over flat or rough ground. */
    public int navLookAhead = 12;
    public int navHoldTicks = 10;
    /** Never let a move carry it off an edge into the void, lava, or a long fall: 1 on, 0 off. */
    public int edgeGuard = 1;
    /** Highest drop, in blocks, the edge guard lets it walk off. Falls of 3 or less do no damage. */
    public int edgeSafeDrop = 3;
    /** How far inside the 0.3 lean limit the edge guard keeps its middle, in blocks. */
    public double edgeMargin = 0.15;
    /** How far short of the 0.3 block lean limit it sneaks, in blocks. More is safer if the server corrects it, and slower. */
    public double bridgeMargin = 0.05;
    /** Degrees off straight back it walks while speed bridging. 45 is the ninja bridge, 0 plain backward. */
    public double bridgeAngle = 45;
    /** Sidestep a dive when the predicted path passes this close, in blocks, within dodgeLeadTicks ticks. */
    public double dodgeWithin = 2.6;
    public int dodgeLeadTicks = 1;
    /** Sprint in a circle while a flyer is overhead, instead of standing: 1 on, 0 off. */
    public int flyerCircle = 1;
    /** Stops circling this many ticks before a predicted pass within 4 blocks. */
    public int flyerPlantTicks = 0;
    /** Raise the shield this many ticks before a flyer's predicted pass. A shield takes 5 ticks to come up. */
    public int flyerShieldTicks = 8;
    /** How many ticks of a flyer's predicted path the crystal style scores crystals along. */
    public int flyerCrystalHorizon = 14;
    /** Obsidian bases it keeps ready beside itself while a flyer circles. */
    public int crystalPrepBases = 2;
    /** Distance it tries to keep from the target while crystalling. */
    public double crystalRange = 3.0;
    /** 1 to eat golden apples in the crystal style: at this much health and absorption, or with no absorption
     *  left and health this low while it has nothing to click. An enchanted apple below the last number. */
    public int crystalEat = 1;
    public double crystalEatHealth = 12, crystalTopUpHealth = 16, crystalEgapHealth = 6;
    /** In the crystal style, pearl or run out of blast range to heal at this much health, and come back at the second. */
    public double crystalRetreatHealth = 5, crystalResumeHealth = 14;
    /** 1 to use respawn anchors where they explode. */
    public int useAnchors = 1;
    /** Ticks to wait for a placement to show up before counting it as failed. */
    public int placeConfirmTicks = 3;
    /**
     * 1 to keep fighting: when the target dies it waits for them to respawn and starts a new round, and when
     * the bot dies it comes back where it died with its kit and keeps going, until stopped.
     */
    public int persistent = 1;
    /** 1 to sprint jump when far away, which moves faster than plain sprinting. */
    public int sprintJump = 1;
    /** Sprint jump only when farther than this, so it never jumps into a hit trade. */
    public double sprintJumpBeyond = 4.0;
    /** 1 to pick the fighting style from the inventory, 0 to always use the sword style. */
    public int autoStyle = 1;
    /** 1 to use the shield, 0 to never block. */
    public int useShield = 1;
    /** 1 to swap to an axe to break a raised shield. */
    public int breakShields = 1;
    /** Eat an enchanted golden apple at or below this health. Regular golden apples are used above it. */
    public double egapAt = 6;
    /** 1 to move apples from the inventory to the hotbar when nobody is close. */
    public int restock = 1;
    /** Ticks it waits before reacting to a new situation. */
    public int reactionTicks = 2;
    public long seed = 1;

    public static BotProfile load(Path file) {
        BotProfile p = new BotProfile();
        if (!Files.exists(file)) return p;
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(file)) {
            props.load(r);
        } catch (IOException e) {
            return p;
        }
        for (Field f : BotProfile.class.getFields()) {
            String v = props.getProperty(f.getName());
            if (v == null) continue;
            try {
                if (f.getType() == double.class) f.setDouble(p, Double.parseDouble(v.trim()));
                else if (f.getType() == int.class) f.setInt(p, Integer.parseInt(v.trim()));
                else if (f.getType() == long.class) f.setLong(p, Long.parseLong(v.trim()));
                else if (f.getType() == String.class) f.set(p, v.trim());
            } catch (ReflectiveOperationException | NumberFormatException ignored) {
            }
        }
        return p;
    }

    public void save(Path file) throws IOException {
        Properties props = new Properties();
        for (Field f : BotProfile.class.getFields()) {
            try {
                props.setProperty(f.getName(), String.valueOf(f.get(this)));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file)) {
            props.store(w, "Fight bot style profile");
        }
    }

    public List<String> describe() {
        List<String> l = new ArrayList<>();
        for (Field f : BotProfile.class.getFields()) {
            try {
                l.add(f.getName() + " = " + f.get(this));
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return l;
    }
}
