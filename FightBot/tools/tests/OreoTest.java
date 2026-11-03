import dev.reality.fightbot.core.*;
import dev.reality.fightbot.core.world.WorldView;

import java.util.List;

/** Deterministic checks for the Riptide -> Lunge -> Mace policy's observable transitions. */
public final class OreoTest {
    private static int fails;

    private static void check(boolean ok, String description) {
        System.out.println((ok ? "PASS " : "FAIL ") + description);
        if (!ok) fails++;
    }

    private static final class FlatWorld implements WorldView {
        boolean sourceWater;

        @Override public Kind kind(int x, int y, int z) {
            if (y <= 0) return Kind.SOLID;
            if (sourceWater && x == 0 && y == 1 && z == 0) return Kind.REPLACEABLE;
            return Kind.AIR;
        }
        @Override public boolean water(int x, int y, int z) {
            return sourceWater && x == 0 && y == 1 && z == 0;
        }
        @Override public int anchorCharge(int x, int y, int z) { return -1; }
        @Override public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) { return false; }
        @Override public List<Crystal> crystals() { return List.of(); }
        @Override public String dimension() { return "overworld"; }
        @Override public String difficulty() { return "normal"; }
        @Override public Armor armor(boolean self) { return new Armor(20, 12, 16); }
    }

    private static Loadout kit(boolean wet) {
        return kitWithBucket(wet, "water_bucket");
    }

    private static Loadout kitWithBucket(boolean wet, String bucketItem) {
        Loadout l = Loadout.of("netherite_chestplate", "totem_of_undying",
                "netherite_sword", "mace", "netherite_spear", "trident:riptide",
                bucketItem, "wind_charge*64", "golden_apple*64");
        l.lungeSlot = 2;
        l.lungeLevel = 3;
        l.windBurstSlot = 1;
        l.windBurstLevel = 1;
        l.wet = wet;
        return l;
    }

    private static Observation obs(long tick, double y, double vy, boolean onGround,
                                   boolean usingItem, String mainHand, float yaw, float pitch, Loadout l) {
        return new Observation(tick, true,
                0.5, y, 0.5, 0.0, vy, 0.0, yaw, pitch, onGround, false, usingItem,
                20.0f, 0.0f, mainHand, 1.0, -1, -1, 0,
                0.5, 0.0, 4.5, 0.0, 0.0, 0.0, 180.0f,
                true, false, false, false, 20.0f, 0.0f, "netherite_sword", true, 0,
                false, -1, -1, 0, 0, 0, 1.0, false, 0, 0,
                l, 0.0, false, true, 20.0, 20.0);
    }

    public static void main(String[] args) {
        BotProfile.defaultHumanize = 0;
        BotProfile p = new BotProfile();
        p.usePotions = 0;
        p.useLunge = 1;

        Loadout l = kit(true);
        Style.Choice picked = Style.pick(l, p);
        check(picked.use() == Style.RIPTIDE_LUNGE_MACE,
                "auto-style recognizes Riptide, Lunge III, and Wind Burst I gear");

        StyleBrain brain = new StyleBrain(p, null);
        BotAction integrated = brain.step(obs(0, 1.0, 0.0, true, false, "netherite_sword", 0, 0, l));
        check(brain.choice().use() == Style.RIPTIDE_LUNGE_MACE
                        && integrated.technique.equals("oreo_select_riptide"),
                "StyleBrain routes the normal combat tick into the new policy");
        Loadout emptyBucketLoadout = kitWithBucket(true, "bucket");
        BotAction maintained = brain.step(obs(1, 1.0, 0.0, true, false, "bucket", 0, 90, emptyBucketLoadout));
        check(brain.choice().use() == Style.RIPTIDE_LUNGE_MACE
                        && maintained.technique.equals("oreo_select_riptide"),
                "StyleBrain does not switch away when placing water changes the held bucket item");

        RiptideLungeMacePolicy policy = new RiptideLungeMacePolicy(p);
        BotAction select = policy.step(obs(0, 1.0, 0.0, true, false, "netherite_sword", 0, 0, l));
        check(select.hotbar == 3 && select.technique.equals("oreo_select_riptide"),
                "selects the Riptide trident before charging");

        BotAction start = policy.step(obs(1, 1.0, 0.0, true, false, "trident:riptide", 0, -90, l));
        check(start.use && start.pitch == -90.0f && start.technique.equals("oreo_riptide_charge_up"),
                "charges Riptide while looking vertically upward");

        BotAction release = null;
        for (long t = 2; t <= 11; t++) {
            release = policy.step(obs(t, 1.0, 0.0, true, true, "trident:riptide", 0, -90, l));
        }
        check(release != null && !release.use && release.exactAim
                        && release.technique.equals("oreo_riptide_launch_up")
                        && policy.phase() == RiptideLungeMacePolicy.Phase.PICK_UP_WATER,
                "releases Riptide after the observed charge window");

        // From here onward, model the actual bucket state change: water bucket -> empty bucket -> water bucket.
        FlatWorld world = new FlatWorld();
        Loadout dry = kit(false);
        RiptideLungeMacePolicy placedPolicy = new RiptideLungeMacePolicy(p);
        placedPolicy.setWorld(world);
        BotAction placeWater = placedPolicy.step(obs(30, 1.0, 0.0, true, false, "netherite_sword", 0, 0, dry));
        check("water_bucket".equals(placeWater.useItem)
                        && placedPolicy.phase() == RiptideLungeMacePolicy.Phase.WAIT_FOR_WATER,
                "places water only when a solid floor and open placement cell are available");

        world.sourceWater = true;
        Loadout wetBucket = kitWithBucket(true, "bucket");
        BotAction selectAfterWet = placedPolicy.step(obs(31, 1.0, 0.0, true, false, "bucket", 0, 90, wetBucket));
        check(selectAfterWet.hotbar == 3 && placedPolicy.phase() == RiptideLungeMacePolicy.Phase.CHARGE_RIPTIDE_UP,
                "waits for wet-state confirmation before selecting Riptide");

        // Fast-forward a second charge with the same policy instance, using the bucket-after-place loadout.
        placedPolicy.step(obs(32, 1.0, 0.0, true, false, "trident:riptide", 0, -90, wetBucket));
        for (long t = 33; t <= 42; t++) {
            release = placedPolicy.step(obs(t, 1.0, 0.0, true, true, "trident:riptide", 0, -90, wetBucket));
        }
        check(release != null && release.technique.equals("oreo_riptide_launch_up"),
                "launches after the water setup rather than skipping the Riptide charge");
        Loadout risingBucket = kitWithBucket(true, "bucket");
        BotAction pickup = placedPolicy.step(obs(43, 2.5, 1.2, false, false, "trident:riptide", 0, -90, risingBucket));
        check("bucket".equals(pickup.useItem) && pickup.technique.equals("oreo_water_pickup"),
                "tries to retrieve its water source while it is still within bucket reach");

        world.sourceWater = false;
        Loadout recovered = kitWithBucket(false, "water_bucket");
        BotAction rising = placedPolicy.step(obs(44, 3.6, 1.1, false, false, "trident:riptide", 0, 65, recovered));
        check(placedPolicy.phase() == RiptideLungeMacePolicy.Phase.AIM_DOWN
                        && rising.technique.equals("oreo_align_downward"),
                "after water pickup, starts rotating toward the opponent on the way up");

        boolean lungeIssued = false;
        double y = 3.6;
        double vy = 1.1;
        float yaw = rising.yaw;
        float pitch = rising.pitch;
        for (long t = 45; t < 75 && !lungeIssued; t++) {
            y += vy;
            vy = (vy - 0.08) * 0.98;
            BotAction action = placedPolicy.step(obs(t, y, vy, false, false, "trident:riptide", yaw, pitch, recovered));
            if ("oreo_lunge_swap".equals(action.technique)) {
                lungeIssued = action.jabSlot == 2 && action.swapBack == 1;
                break;
            }
            yaw = action.yaw;
            pitch = action.pitch;
        }
        check(lungeIssued,
                "issues the existing one-tick Lunge spear jab and switches back to the Wind Burst I mace");

        float[] downwardAim = RiptideLungeMacePolicy.targetAngles(
                obs(80, 8.0, 0.0, false, false, "mace", 0, 0, recovered));
        check(downwardAim[1] > 0.0f,
                "aim calculation uses positive Minecraft pitch for a target below the bot");

        // A dry start without a known world must fail safely into the existing mace policy.
        RiptideLungeMacePolicy fallback = new RiptideLungeMacePolicy(p);
        BotAction fail = fallback.step(obs(90, 1.0, 0.0, true, false, "netherite_sword", 0, 0, dry));
        check(fallback.phase() == RiptideLungeMacePolicy.Phase.FAILED
                        && fail.state.startsWith("OREO_FALLBACK_"),
                "falls back to the existing mace policy if water setup cannot be checked");

        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }
}
