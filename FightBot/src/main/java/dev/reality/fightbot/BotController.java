package dev.reality.fightbot;

import dev.reality.fightbot.core.ActionValidator;
import dev.reality.fightbot.core.BotAction;
import dev.reality.fightbot.core.BotProfile;
import dev.reality.fightbot.core.LearningProfile;
import dev.reality.fightbot.core.LearningScore;
import dev.reality.fightbot.core.Episode;
import dev.reality.fightbot.core.Loadout;
import dev.reality.fightbot.core.Observation;
import dev.reality.fightbot.core.StyleBrain;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Drives one Carpet fake player against one target. Every tick it reads both players on the server,
 * picks a fighting style from the bot's inventory, asks that style what to do, checks it with the validator, and sends the inputs.
 */
final class BotController {
    final String botName;
    /** The player it is fighting now. In free-for-all mode this changes to whoever is closest and in sight. */
    String targetName;
    /** Free-for-all: fight anyone it sees, not one named player. */
    final boolean everyone;
    private int kills;
    final BotProfile profile;
    private StyleBrain policy;
    private ActionValidator validator;
    private final CarpetInputs inputs;
    private Episode episode;
    private final MinecraftServer server;
    /** Round state: fighting, or waiting for the target to come back or the bot to respawn. */
    private boolean roundActive;
    private int round, wins, losses;
    private long respawnAt = -1, lastNoClickNote;
    private double lastBotX = Double.NaN, lastBotY, lastBotZ;
    private net.minecraft.server.level.ServerLevel lastBotLevel;
    private String pendingKit;
    private boolean peacefulWarned;
    /** Right after a death Carpet still lists the fake player for a tick or two. Ignore it until then. */
    private long ignoreUntil = -1;
    private boolean creativeWarned;
    private final Path root;
    private final Consumer<String> announce;
    private long tick;
    private int missingTicks;
    private double lastTx = Double.NaN, lastTy, lastTz;
    private ShulkerRestock shulker;
    private boolean walkingToBox;
    private String restockBlocked;
    boolean finished;

    BotController(MinecraftServer server, String botName, String targetName, BotProfile profile, Path root, Consumer<String> announce) {
        this.botName = botName;
        this.everyone = targetName.equalsIgnoreCase("everyone");
        this.targetName = everyone ? "" : targetName;
        this.profile = profile;
        this.server = server;
        this.inputs = new CarpetInputs(server, botName);
        this.root = root;
        this.announce = announce;
        newRound();
    }

    private void newRound() {
        this.policy = new StyleBrain(profile, msg -> announce.accept(botName + ": " + msg));
        this.validator = new ActionValidator(profile);
        this.episode = new Episode(botName, targetName, profile.name, "auto");
        this.lastTx = Double.NaN;
        this.shulker = null;
    }

    void tick(MinecraftServer server) {
        if (finished) return;
        tick++;
        ServerPlayer bot = server.getPlayerList().getPlayerByName(botName);
        if (everyone && bot != null && tick >= ignoreUntil) retarget(server, bot);
        ServerPlayer target = targetName.isEmpty() ? null : server.getPlayerList().getPlayerByName(targetName);
        boolean persistent = profile.persistent == 1;
        if (bot == null) {
            // Carpet removes a fake player when it dies. In persistent mode it comes back where it was and keeps going.
            if (persistent && respawnAt >= 0 && tick >= respawnAt) {
                respawn();
                respawnAt = -1;
                missingTicks = 0;
                return;
            }
            if (respawnAt < 0 && ++missingTicks > 100) {
                if (persistent && !Double.isNaN(lastBotX)) {
                    respawnAt = tick + 20;
                } else {
                    finish("bot missing");
                }
            }
            return;
        }
        missingTicks = 0;
        if (tick < ignoreUntil) return;
        // Still listed after a death and the respawn time has come: it came back on its own, so it needs its kit.
        if (respawnAt >= 0 && tick >= respawnAt) {
            respawnAt = -1;
            pendingKit = Kits.lastKit(botName);
        }
        if (pendingKit != null) {
            announce.accept(Kits.give(server, botName, pendingKit));
            pendingKit = null;
            return;
        }
        lastBotX = bot.getX();
        lastBotY = bot.getY();
        lastBotZ = bot.getZ();
        lastBotLevel = bot.level();
        if (bot.isDeadOrDying()) {
            endRound("loss");
            if (persistent) {
                respawnAt = tick + 60;
                ignoreUntil = tick + 40;
            } else {
                finish("loss");
            }
            return;
        }
        boolean targetGone = target == null || target.isDeadOrDying() || target.isSpectator();
        if (targetGone && everyone) {
            // Free-for-all: a kill, or nobody in sight. Fight back against mobs meanwhile, and look for the next one.
            if (roundActive) {
                if (target != null && target.isDeadOrDying()) kills++;
                endRound(target != null && target.isDeadOrDying() ? "win" : "target lost");
            }
            targetName = "";
            BotAction idle = BotAction.idle(bot.getYRot(), bot.getXRot());
            if (swatMobs(bot, null, idle)) {
                bot.setYRot(idle.yaw);
                bot.setYHeadRot(idle.yaw);
                bot.setXRot(idle.pitch);
                inputs.apply(idle, bot.isSprinting());
            } else {
                inputs.stopAll();
            }
            return;
        }
        if (targetGone) {
            if (roundActive) endRound(target == null ? "target left" : "win");
            if (!persistent) {
                finish(target == null ? "target left" : "win");
                return;
            }
            // Wait for the target to come back. Stand still and keep the hotbar tidy.
            inputs.stopAll();
            return;
        }
        if (!roundActive) {
            roundActive = true;
            round++;
            if (round > 1) {
                newRound();
                announce.accept(botName + ": round " + round + " against " + targetName + ". Potting up, then fighting.");
            }
            // A round with no weapon anywhere means the kit never came back after a death. Give it again.
            String kit = Kits.lastKit(botName);
            if (kit != null && !hasMelee(bot)) {
                announce.accept(botName + ": started a round with no weapon, so it takes the " + kit + " kit again.");
                pendingKit = kit;
                return;
            }
        }

        // A target in creative takes no damage, so the fight can never end. Say so once instead of swinging forever.
        if (target.isCreative() && !creativeWarned) {
            creativeWarned = true;
            announce.accept(botName + ": " + targetName + " is in creative mode, where hits do nothing. Switch to survival to fight it.");
        } else if (!target.isCreative()) {
            creativeWarned = false;
        }
        Observation o = observe(bot, target);
        if (shulker != null) {
            // Restocking takes over for a few seconds. The bot stands still and works the box.
            BotAction idle = BotAction.idle(bot.getYRot(), bot.getXRot());
            idle.state = "RESTOCK";
            idle.technique = "shulker_" + shulker.step.name().toLowerCase();
            episode.add(o, idle);
            boolean over = shulker.tick(bot, target);
            if (shulker.walkTo != null) {
                // Walk onto the dropped box: the game picks it up once the bot touches it.
                double dx = shulker.walkTo.x - bot.getX(), dz = shulker.walkTo.z - bot.getZ();
                idle.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                idle.pitch = 30;
                idle.forward = 1;
                idle.sprint = false;
                idle.jump = bot.horizontalCollision && bot.onGround();
                bot.setYRot(idle.yaw);
                bot.setYHeadRot(idle.yaw);
                inputs.apply(idle);
                walkingToBox = true;
            } else if (walkingToBox) {
                inputs.stopAll();
                walkingToBox = false;
            }
            if (over) {
                announce.accept(botName + ": " + shulker.result);
                shulker = null;
                if (walkingToBox) inputs.stopAll();
                walkingToBox = false;
            }
            return;
        }
        policy.setWorld(new ServerWorldView(bot, target));
        BotAction a = validator.check(o, policy.step(o));
        swatMobs(bot, target, a);
        // Blasts do nothing to players on Peaceful, so say so once instead of leaving it a mystery.
        if (!peacefulWarned && bot.level().getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL && policy.choice() != null
                && (policy.choice().use() == dev.reality.fightbot.core.Style.CRYSTAL || policy.choice().use() == dev.reality.fightbot.core.Style.ANCHOR)) {
            peacefulWarned = true;
            announce.accept(botName + ": this world is on Peaceful, where crystals and anchors do not hurt players, so it can only use the sword. Run /difficulty normal or /difficulty hard to fight with blasts.");
        }
        // In the crystal style, say why once in a while if it has gone a while without a single click.
        if (policy.choice() != null && (policy.choice().use() == dev.reality.fightbot.core.Style.CRYSTAL
                || policy.choice().use() == dev.reality.fightbot.core.Style.ANCHOR)
                && policy.crystal().ticksWithoutOption() >= 100 && tick - lastNoClickNote > 200) {
            lastNoClickNote = tick;
            announce.accept(botName + ": " + policy.crystal().lastWhy());
        }
        episode.add(o, a);
        if (a.shulkerRestock) {
            String why = ShulkerRestock.blocker(bot);
            if (why != null) {
                // Say it once, then keep fighting with what it has.
                if (!why.equals(restockBlocked)) announce.accept(botName + ": skipping the ender chest restock, since " + why + ".");
                restockBlocked = why;
            } else {
                restockBlocked = null;
                inputs.stopAll();
                shulker = new ShulkerRestock(bot);
                return;
            }
        }
        bot.setYRot(a.yaw);
        bot.setXRot(a.pitch);
        bot.setYHeadRot(a.yaw);
        if (FlightWatchers.watching(botName)) reportActions(o, a, server.getTickCount());
        if (a.restock != null) restock(bot, a.restock);
        // Swapping the chest slot is the same as right-clicking armor in the hotbar, done here so it lands this tick.
        if (a.equip != null) equip(bot, a.equip);
        if (a.startGlide && !bot.onGround() && !bot.isFallFlying()) bot.tryToStartFallFlying();
        if (a.offhandTotem && bot.getOffhandItem().isEmpty()) {
            var inv = bot.getInventory();
            int from = -1;
            // Main inventory first, so totems kept in the hotbar stay there.
            for (int i = 9; i < 36 && from < 0; i++) if (id(inv.getItem(i)).equals("totem_of_undying")) from = i;
            for (int i = 0; i < 9 && from < 0; i++) if (id(inv.getItem(i)).equals("totem_of_undying")) from = i;
            if (from >= 0) {
                ItemStack one = inv.getItem(from).split(1);
                bot.setItemSlot(EquipmentSlot.OFFHAND, one);
                inv.setChanged();
            }
        }
        if (a.swapHands) {
            ServerMoves.select(bot, a.hotbar);
            ServerMoves.swapHands(bot);
        }
        // Swap hits and lunge jabs happen inside this tick, so they go straight to the server, not through Carpet.
        if (a.swapAttack != null) {
            String note = ServerMoves.swapAttack(bot, target, a.swapAttack, a.swapBack);
            if (note != null) announce.accept(note);
            inputs.forgetHotbar();
        }
        if (a.jabSlot >= 0) {
            if (!ServerMoves.jab(bot, a.jabSlot, a.swapBack) && profile.useLunge == 1) {
                profile.useLunge = 0;
                announce.accept(botName + ": this game version has no spear jab action the bot can find, so lunges are off.");
            }
            inputs.forgetHotbar();
        }
        // A block click runs the game's own placement code for this player, the same call Carpet makes for a
        // fake player's right click, after a reach check with the same 4.5 blocks and 1 block of slack.
        if (a.place != null) {
            var r = a.place;
            ServerMoves.select(bot, r.slot());
            net.minecraft.core.Direction dir = net.minecraft.core.Direction.byName(r.face());
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(r.cx(), r.cy(), r.cz());
            var at = new net.minecraft.world.phys.Vec3(r.hx(), r.hy(), r.hz());
            // Survival block reach is 4.5 blocks, and the server allows 1 block of slack, as it does for players.
            if (dir != null && bot.getEyePosition().distanceTo(at) <= 5.5) {
                var hit = new net.minecraft.world.phys.BlockHitResult(at, dir, pos, false);
                var result = bot.gameMode.useItemOn(bot, bot.level(), bot.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND, hit);
                if (result.consumesAction()) {
                    bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                }
            }
            inputs.forgetHotbar();
            a.hotbar = -1;
        }
        // Throwing a pearl or emptying or filling a bucket: the game's own right click into the air, along the bot's aim.
        if (a.useItem != null) {
            int slot = -1;
            for (int i = 0; i < 9 && slot < 0; i++) if (id(bot.getInventory().getItem(i)).equals(a.useItem)) slot = i;
            if (slot >= 0) {
                ServerMoves.select(bot, slot);
                var result = bot.gameMode.useItem(bot, bot.level(), bot.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND);
                if (result.consumesAction()) {
                    bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                }
                inputs.forgetHotbar();
                a.hotbar = -1;
            }
        }
        // Mining a block in the way, at the game's own speed for this block, tool, and the bot's state.
        if (a.mine != null) {
            var level = bot.level();
            var pos = new net.minecraft.core.BlockPos(a.mine[0], a.mine[1], a.mine[2]);
            if (a.mineSlot >= 0) ServerMoves.select(bot, a.mineSlot);
            if (!pos.equals(minePos)) {
                if (minePos != null) level.destroyBlockProgress(bot.getId(), minePos, -1);
                minePos = pos;
                mineProgress = 0;
            }
            var st = level.getBlockState(pos);
            if (!st.isAir() && bot.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= 5.5) {
                mineProgress += st.getDestroyProgress(bot, level, pos);
                bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                level.destroyBlockProgress(bot.getId(), pos, Math.min(9, (int) (mineProgress * 10)));
                if (mineProgress >= 1) {
                    level.destroyBlockProgress(bot.getId(), pos, -1);
                    if (bot.gameMode.destroyBlock(pos)) policy.navigator().mined();
                    minePos = null;
                }
            }
            inputs.forgetHotbar();
            a.hotbar = -1;
        } else if (minePos != null && tick - lastMineTick > 5) {
            // A tick or two away from mining keeps the progress, the way a held click does.
            bot.level().destroyBlockProgress(bot.getId(), minePos, -1);
            minePos = null;
        }
        if (a.mine != null) lastMineTick = tick;
        // Single right clicks with something thrown or fired go straight into the air. Through Carpet's right
        // click, a block under the crosshair gets the click first: a rocket lands on the ground instead of
        // boosting, and a pot or wind charge thrown at the feet opens a chest or door that happens to be there.
        if (a.useOnce) {
            int slot = a.hotbar >= 0 ? a.hotbar : bot.getInventory().getSelectedSlot();
            String thrown = fullId(bot.getInventory().getItem(slot));
            if (thrown.equals("firework_rocket") || thrown.equals("wind_charge") || thrown.startsWith("splash_potion")
                    || thrown.startsWith("lingering_potion") || thrown.equals("crossbow:charged")
                    || thrown.equals("snowball") || thrown.equals("egg") || thrown.equals("experience_bottle")) {
                ServerMoves.select(bot, slot);
                var result = bot.gameMode.useItem(bot, bot.level(), bot.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND);
                if (result.consumesAction()) {
                    bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
                }
                a.useOnce = false;
                a.hotbar = -1;
                inputs.forgetHotbar();
            }
        }
        // Hitting a crystal: only an end crystal, and only within a player's reach, measured from the eyes to its box.
        if (a.attackEntity >= 0) {
            var e = bot.level().getEntity(a.attackEntity);
            if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal && reachTo(bot, e) <= 3.3) {
                bot.attack(e);
                bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            }
        }
        // A hit on the target goes to the game this tick. Through Carpet it would land next tick along the
        // crosshair, and a player knocked up or moving fast is no longer there by then, so the swing misses.
        // A hit that also switches items still goes through Carpet, so the new item's attributes apply first.
        if (a.attack && target != null && (a.hotbar < 0 || a.hotbar == bot.getInventory().getSelectedSlot())
                && reachTo(bot, target) <= 3.0 && bot.hasLineOfSight(target)) {
            bot.attack(target);
            bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            a.attack = false;
        }
        if (a.swapAttack != null || a.jabSlot >= 0) a.hotbar = -1;
        inputs.apply(a, bot.isSprinting());
    }

    /** True when the item has Mending, so experience repairs it. */
    static boolean hasMending(ItemStack s) {
        var ench = s.get(DataComponents.ENCHANTMENTS);
        if (ench == null) return false;
        for (var h : ench.keySet()) if (h.getRegisteredName().endsWith("mending")) return true;
        return false;
    }

    /** Distance from the eyes to the closest point of the entity's box, the way a player's reach is measured. */
    static double reachTo(ServerPlayer bot, net.minecraft.world.entity.Entity e) {
        var eye = bot.getEyePosition();
        var box = e.getBoundingBox();
        double x = Math.max(box.minX, Math.min(eye.x, box.maxX));
        double y = Math.max(box.minY, Math.min(eye.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        return eye.distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
    }

    // ---------------------------------------------------------------- free-for-all and mobs

    /**
     * Free-for-all: keep the current target while they are alive, near, and in sight now and then. Switch
     * to someone else only when they are a lot closer, so it does not flip back and forth.
     */
    private void retarget(MinecraftServer server, ServerPlayer bot) {
        ServerPlayer cur = targetName.isEmpty() ? null : server.getPlayerList().getPlayerByName(targetName);
        boolean curOk = cur != null && fair(bot, cur) && bot.distanceTo(cur) <= profile.everyoneRange + 16;
        ServerPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p == bot || !fair(bot, p) || !bot.hasLineOfSight(p)) continue;
            double d = bot.distanceTo(p);
            if (d <= profile.everyoneRange && d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        if (best == null || best == cur) return;
        if (curOk && bestDist + 6 >= bot.distanceTo(cur)) return;
        if (roundActive) endRound("switched target");
        targetName = best.getScoreboardName();
        announce.accept(botName + ": going after " + targetName + ".");
    }

    /** Someone it can fight: alive, in the same world, and in survival or adventure. */
    private static boolean fair(ServerPlayer bot, ServerPlayer p) {
        return p.isAlive() && !p.isDeadOrDying() && !p.isSpectator() && !p.isCreative() && p.level() == bot.level();
    }

    private net.minecraft.world.entity.Mob mobFoe;

    /**
     * A mob that hit the bot gets hit back. It only swings at the mob when the player target is not in
     * reach, so a zombie never costs a hit on the player, and only walks over to it when no player is near.
     * Returns true when it did something about a mob this tick.
     */
    private boolean swatMobs(ServerPlayer bot, ServerPlayer target, BotAction a) {
        if (profile.fightMobs != 1) return false;
        if (bot.getLastHurtByMob() instanceof net.minecraft.world.entity.Mob m && m.isAlive()) mobFoe = m;
        if (mobFoe == null || !mobFoe.isAlive() || mobFoe.level() != bot.level() || bot.distanceTo(mobFoe) > 16) {
            mobFoe = null;
            return false;
        }
        double toPlayer = target == null ? 99 : bot.distanceTo(target);
        double toMob = bot.distanceTo(mobFoe);
        if (toPlayer < 3.6 && toMob > 2.0) return false;
        boolean busy = a.place != null || a.mine != null || a.useItem != null || a.useOnce || a.swapAttack != null
                || a.jabSlot >= 0 || a.attackEntity >= 0 || a.equip != null || a.use;
        double dx = mobFoe.getX() - bot.getX(), dz = mobFoe.getZ() - bot.getZ();
        double dy = mobFoe.getY() + mobFoe.getBbHeight() * 0.5 - bot.getEyeY();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        if (reachTo(bot, mobFoe) <= 3.0 && !busy && bot.hasLineOfSight(mobFoe) && bot.getAttackStrengthScale(0.5f) >= 0.9f) {
            int weapon = -1;
            for (int i = 0; i < 9 && weapon < 0; i++) if (id(bot.getInventory().getItem(i)).endsWith("_sword")) weapon = i;
            for (int i = 0; i < 9 && weapon < 0; i++) if (id(bot.getInventory().getItem(i)).endsWith("_axe")) weapon = i;
            if (weapon >= 0) ServerMoves.select(bot, weapon);
            a.yaw = yaw;
            a.pitch = pitch;
            bot.setYRot(yaw);
            bot.setXRot(pitch);
            bot.attack(mobFoe);
            bot.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
            bot.resetAttackStrengthTicker();
            a.attack = false;
            a.hotbar = -1;
            inputs.forgetHotbar();
            a.technique = "hit_mob";
            return true;
        }
        if (toPlayer > 10 && toMob > 2.5 && !busy) {
            a.yaw = yaw;
            a.pitch = pitch;
            a.forward = 1;
            a.sprint = true;
            a.strafe = 0;
            a.technique = "chase_mob";
            return true;
        }
        return false;
    }

    private net.minecraft.core.BlockPos minePos;
    private float mineProgress;
    private long lastMineTick;

    private Observation observe(ServerPlayer bot, ServerPlayer target) {
        // A real player's velocity on the server is not reliable, so it is worked out from positions.
        double tvx = 0, tvy = 0, tvz = 0;
        if (!Double.isNaN(lastTx)) {
            tvx = target.getX() - lastTx;
            tvy = target.getY() - lastTy;
            tvz = target.getZ() - lastTz;
        }
        lastTx = target.getX();
        lastTy = target.getY();
        lastTz = target.getZ();

        var inv = bot.getInventory();
        int swordSlot = -1, axeSlot = -1, healSlot = -1, healCount = 0, egapSlot = -1, egapCount = 0;
        for (int i = 0; i < 9; i++) {
            String id = id(inv.getItem(i));
            if (swordSlot < 0 && id.endsWith("_sword")) swordSlot = i;
            if (axeSlot < 0 && id.endsWith("_axe")) axeSlot = i;
            if (healSlot < 0 && id.equals("golden_apple")) {
                healSlot = i;
                healCount = inv.getItem(i).getCount();
            }
            if (egapSlot < 0 && id.equals("enchanted_golden_apple")) {
                egapSlot = i;
                egapCount = inv.getItem(i).getCount();
            }
        }
        // The sword is the main weapon. The axe is only the main weapon when there is no sword.
        int weaponSlot = swordSlot >= 0 ? swordSlot : axeSlot;
        int[] mine = apples(bot);
        int[] theirs = apples(target);
        var v = bot.getDeltaMovement();
        Loadout botLoadout = loadout(bot);
        botLoadout.oHeight = target.getBbHeight();
        botLoadout.oWidth = target.getBbWidth();
        botLoadout.oGliding = target.isFallFlying();
        botLoadout.oPitch = target.getXRot();
        return new Observation(tick, true,
                bot.getX(), bot.getY(), bot.getZ(), v.x, v.y, v.z, bot.getYRot(), bot.getXRot(),
                bot.onGround(), bot.isSprinting(), bot.isUsingItem(), bot.getHealth(), bot.getAbsorptionAmount(),
                fullId(bot.getMainHandItem()), bot.getAttackStrengthScale(0.0f), weaponSlot, healSlot, healCount,
                target.getX(), target.getY(), target.getZ(), tvx, tvy, tvz, target.getYRot(),
                target.onGround(), target.isSprinting(), target.isBlocking(), target.isUsingItem(),
                target.getHealth(), target.getAbsorptionAmount(), id(target.getMainHandItem()),
                bot.hasLineOfSight(target), 0,
                id(bot.getOffhandItem()).equals("shield"), axeSlot, egapSlot, egapCount, mine[0], mine[1],
                target.getAttackStrengthScale(0.0f), id(target.getOffhandItem()).equals("shield"), theirs[0], theirs[1],
                botLoadout, bot.fallDistance, bot.isFallFlying(),
                !bot.getCooldowns().isOnCooldown(new ItemStack(Items.WIND_CHARGE)),
                headroom(bot, 24), headroom(target, 24));
    }

    /**
     * Open space above a player's head, up to max blocks. It checks the block columns under all four corners
     * of the player's hitbox, so a roof edge just over a shoulder still counts.
     */
    static double headroom(ServerPlayer p, int max) {
        var level = p.level();
        double head = p.getY() + p.getBbHeight();
        double best = max;
        double[][] corners = {{-0.3, -0.3}, {0.3, -0.3}, {-0.3, 0.3}, {0.3, 0.3}};
        for (double[] c : corners) {
            int bx = (int) Math.floor(p.getX() + c[0]), bz = (int) Math.floor(p.getZ() + c[1]);
            for (int dy = 0; dy <= max; dy++) {
                net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(bx, (int) Math.floor(head) + dy, bz);
                var shape = level.getBlockState(pos).getCollisionShape(level, pos);
                if (shape.isEmpty()) continue;
                double bottom = pos.getY() + shape.min(net.minecraft.core.Direction.Axis.Y);
                if (bottom + 1e-6 < head) continue;
                best = Math.min(best, bottom - head);
                break;
            }
        }
        return best;
    }

    /** Tells the flight recorder which movement actions the bot sent this tick. */
    private void reportActions(Observation o, BotAction a, long t) {
        if (a.useOnce) {
            String held = a.hotbar >= 0 ? o.loadout().hotbar(a.hotbar) : o.mainHand();
            if (held.equals("firework_rocket")) FlightWatchers.commanded(botName, "firework", t);
            if (held.equals("wind_charge")) FlightWatchers.commanded(botName, "wind_charge", t);
        }
        if (a.startGlide) FlightWatchers.commanded(botName, "glide_start", t);
        if (a.equip != null) FlightWatchers.commanded(botName, "equip_" + a.equip, t);
        if (a.jump) FlightWatchers.commanded(botName, "jump", t);
    }

    /** Hotbar, item totals across the inventory and armor, chest slot, and offhand. */
    static Loadout loadout(ServerPlayer p) {
        var inv = p.getInventory();
        String[] hotbar = new String[9];
        int[] counts = new int[9];
        Map<String, Integer> totals = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            String id = fullId(s);
            if (i < 9) {
                hotbar[i] = id;
                counts[i] = s.getCount();
            }
            if (!s.isEmpty()) totals.merge(id, s.getCount(), Integer::sum);
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            ItemStack s = p.getItemBySlot(slot);
            if (!s.isEmpty()) totals.merge(id(s), s.getCount(), Integer::sum);
        }
        Loadout l = new Loadout(hotbar, counts, totals, id(p.getItemBySlot(EquipmentSlot.CHEST)), id(p.getOffhandItem()));
        // The spear with the highest Lunge level.
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (!id(s).endsWith("_spear")) continue;
            for (var e : s.getEnchantments().entrySet()) {
                if (e.getKey().getRegisteredName().endsWith("lunge") && e.getIntValue() > l.lungeLevel) {
                    l.lungeLevel = e.getIntValue();
                    l.lungeSlot = i;
                }
            }
        }
        // The mace with the highest Wind Burst level; the Riptide/Lunge combo must not accidentally
        // swap back to a plain mace when a kit carries more than one.
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (!id(s).equals("mace")) continue;
            for (var e : s.getEnchantments().entrySet()) {
                if (e.getKey().getRegisteredName().endsWith("wind_burst") && e.getIntValue() > l.windBurstLevel) {
                    l.windBurstLevel = e.getIntValue();
                    l.windBurstSlot = i;
                }
            }
        }
        l.food = p.getFoodData().getFoodLevel();
        l.onFire = p.isOnFire();
        // Pose and reach, from the game: the eye height and hitbox change with sneaking, gliding, and swimming,
        // and the reach attributes can be changed by the server.
        l.eyeHeight = p.getEyeHeight();
        l.height = p.getBbHeight();
        l.entityReach = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
        l.blockReach = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_INTERACTION_RANGE);
        l.attackSpeed = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        l.inWater = p.isInWater();
        // Durability of what Mending can repair: worn armor, and the elytra wherever it is.
        double worst = 1, elytra = 1;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = p.getItemBySlot(slot);
            if (!s.isDamageableItem() || !hasMending(s)) continue;
            double left = 1 - (double) s.getDamageValue() / s.getMaxDamage();
            if (id(s).equals("elytra")) elytra = Math.min(elytra, left);
            else worst = Math.min(worst, left);
        }
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (id(s).equals("elytra") && s.isDamageableItem() && hasMending(s)) elytra = Math.min(elytra, 1 - (double) s.getDamageValue() / s.getMaxDamage());
        }
        l.armorWorst = worst;
        l.elytraLeft = elytra;
        l.inLava = p.isInLava();
        l.wet = p.isInWaterOrRain();
        int stock = 0;
        for (int i = 0; i < p.getEnderChestInventory().getContainerSize(); i++) if (!p.getEnderChestInventory().getItem(i).isEmpty()) stock++;
        l.enderStock = stock;
        l.underWater = p.isUnderWater();
        l.air = p.getAirSupply();
        l.maxAir = p.getMaxAirSupply();
        Map<String, Integer> effects = new HashMap<>();
        for (MobEffectInstance e : p.getActiveEffects()) {
            effects.put(strip(e.getEffect().getRegisteredName()), e.isInfiniteDuration() ? -1 : e.getDuration());
        }
        l.effects = effects;
        return l;
    }

    /** Swaps the chest slot with an elytra or a chestplate from the inventory, hotbar first. */
    private static void equip(ServerPlayer bot, String what) {
        var inv = bot.getInventory();
        for (int i = 0; i < 36; i++) {
            String id = id(inv.getItem(i));
            boolean match = what.equals("elytra") ? id.equals("elytra") : id.endsWith("_chestplate");
            if (!match) continue;
            ItemStack wearing = bot.getItemBySlot(EquipmentSlot.CHEST).copy();
            bot.setItemSlot(EquipmentSlot.CHEST, inv.getItem(i).copy());
            inv.setItem(i, wearing);
            return;
        }
    }

    /** Golden apples and enchanted golden apples in the whole inventory, offhand included. */
    private static int[] apples(ServerPlayer p) {
        int gaps = 0, egaps = 0;
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            String id = id(s);
            if (id.equals("golden_apple")) gaps += s.getCount();
            if (id.equals("enchanted_golden_apple")) egaps += s.getCount();
        }
        String off = id(p.getOffhandItem());
        if (off.equals("golden_apple")) gaps += p.getOffhandItem().getCount();
        if (off.equals("enchanted_golden_apple")) egaps += p.getOffhandItem().getCount();
        return new int[] {gaps, egaps};
    }

    /**
     * Moves a stack of the item from the main inventory into the hotbar. It picks an empty hotbar
     * slot first, then any slot that is not a weapon or apples. The server sees the whole inventory,
     * so this is the same move a player makes in the inventory screen, done in one tick.
     */
    private static void restock(ServerPlayer bot, String item) {
        var inv = bot.getInventory();
        // "splash_potion:swiftness" means any splash potion whose contents mention swiftness.
        String kind = item.contains(":") ? item.substring(0, item.indexOf(':') + 1) : null;
        String word = item.contains(":") ? item.substring(item.indexOf(':') + 1) : null;
        int from = -1;
        for (int i = 9; i < 36 && from < 0; i++) {
            String id = fullId(inv.getItem(i));
            if (kind == null ? id.equals(item) : id.startsWith(kind) && id.contains(word)) from = i;
        }
        if (from < 0) return;
        int to = -1;
        for (int i = 0; i < 9 && to < 0; i++) if (inv.getItem(i).isEmpty()) to = i;
        for (int i = 0; i < 9 && to < 0; i++) {
            if (i != inv.getSelectedSlot() && !keepInHotbar(fullId(inv.getItem(i)))) to = i;
        }
        // A full fighting hotbar: trade places with a support item instead, which goes to the main inventory, not away.
        for (int i = 0; i < 9 && to < 0; i++) {
            String id = fullId(inv.getItem(i));
            if (i != inv.getSelectedSlot() && !id.endsWith("_sword") && !id.endsWith("_axe") && !id.equals("mace")
                    && !id.contains("golden_apple") && !id.equals("totem_of_undying") && !id.equals("end_crystal")
                    && !id.equals("firework_rocket") && !id.equals("elytra") && !id.equals("wind_charge")
                    && !id.equals(item) && !id.startsWith("splash_potion:strong_healing")) to = i;
        }
        if (to < 0) return;
        ItemStack moving = inv.getItem(from);
        inv.setItem(from, inv.getItem(to));
        inv.setItem(to, moving);
    }

    /** True when a sword, axe, mace or spear is anywhere in the inventory, offhand included. */
    private static boolean hasMelee(ServerPlayer bot) {
        var inv = bot.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (dev.reality.fightbot.core.Loadout.isMelee(id(inv.getItem(i)))) return true;
        }
        return false;
    }

    /** Items the bot fights with, which a restock never pushes out of the hotbar. */
    private static boolean keepInHotbar(String id) {
        return id.endsWith("_sword") || id.endsWith("_axe") || id.equals("mace") || id.endsWith("_spear")
                || id.contains("golden_apple") || id.equals("totem_of_undying") || id.contains("potion")
                || id.equals("wind_charge") || id.equals("firework_rocket") || id.equals("elytra")
                || id.endsWith("_chestplate") || id.endsWith("shulker_box") || id.endsWith("_pickaxe")
                || id.equals("obsidian") || id.equals("end_crystal") || id.equals("respawn_anchor") || id.equals("glowstone")
                || id.equals("water_bucket") || id.equals("ender_pearl") || id.equals("cobblestone")
                || id.equals("bow") || id.startsWith("crossbow") || id.startsWith("trident")
                || id.equals("lava_bucket") || id.equals("cobweb") || id.equals("oak_planks") || id.equals("bucket")
                || id.equals("experience_bottle");
    }

    /** Item id, plus the potion inside for potions, like "splash_potion:strong_strength". */
    static String fullId(ItemStack stack) {
        String id = id(stack);
        // A Riptide trident is told apart from a throwing one, since it only works in water or rain.
        if (id.equals("trident")) {
            var ench = stack.get(DataComponents.ENCHANTMENTS);
            if (ench != null) for (var h : ench.keySet()) if (h.getRegisteredName().endsWith("riptide")) return "trident:riptide";
            return id;
        }
        // A loaded crossbow is told apart from an empty one, since loading takes time and firing does not.
        if (id.equals("crossbow")) {
            var charged = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (charged != null && !charged.isEmpty()) return "crossbow:charged";
        }
        if (id.endsWith("potion")) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents != null && contents.potion().isPresent()) id += ":" + strip(contents.potion().get().getRegisteredName());
        }
        return id;
    }

    static String strip(String name) {
        return name.startsWith("minecraft:") ? name.substring(10) : name;
    }

    static String id(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "air";
        String s = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return s.startsWith("minecraft:") ? s.substring(10) : s;
    }

    /** Saves the round that just ended and reports it. */
    private void endRound(String outcome) {
        if (!roundActive && round > 0) return;
        roundActive = false;
        if (outcome.equals("win")) wins++;
        if (outcome.equals("loss")) losses++;
        inputs.stopAll();
        episode.outcome = outcome;
        try {
            episode.save(root, validator);
            // Keep learning statistics separate from the established results.csv format.
            // The plain build (no learning) skips this entirely and keeps no learning data.
            if (Build.LEARNING) try {
                LearningScore learningScore = LearningScore.calculate(episode, validator);
                LearningProfile learning = LearningProfile.load(root.resolve("learning"));
                learning.record(learningScore, episode.outcome);
                learning.save(root.resolve("learning"));
                learning.appendHistory(root.resolve("learning"), episode.id, botName,
                        targetName, episode.outcome, learningScore);
                announce.accept(learningScore.summary(botName));
            } catch (Exception learningError) {
                announce.accept(botName + ": could not save learning statistics: " + learningError.getMessage());
            }
            var combo = policy.combo();
            java.util.List<String> crystalLines = policy.crystal().log.flush();
            if (!crystalLines.isEmpty()) {
                java.nio.file.Path dir = root.resolve("crystals");
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Files.write(dir.resolve(episode.id + ".jsonl"), crystalLines);
                var cl = policy.crystal().log;
                announce.accept(String.format("%s: %d crystals hit, %d clicks, %d failed. Blasts dealt %.0f and took %.0f. Saved in fightbot/crystals.",
                        botName, cl.breaks(), cl.placements(), cl.failedPlacements(), cl.totalDealt(), cl.totalTaken()));
            }
            if (!policy.comboLog().isEmpty()) {
                java.nio.file.Path dir = root.resolve("combos");
                java.nio.file.Files.createDirectories(dir);
                java.nio.file.Files.write(dir.resolve(episode.id + ".jsonl"), policy.comboLog());
                announce.accept(botName + ": " + combo.combos() + " elytra combos, " + combo.landedCombos() + " landed, "
                        + combo.abandonedCombos() + " abandoned. Steps saved in fightbot/combos.");
            }
        } catch (Exception e) {
            announce.accept("Could not save the fight: " + e.getMessage());
        }
        announce.accept(episode.summaryLine() + " Score: " + wins + " wins, " + losses + " losses.");
    }

    /** Brings the bot back where it died, gives it its kit again, and goes on. */
    private void respawn() {
        var src = server.createCommandSourceStack().withSuppressedOutput();
        if (lastBotLevel != null) src = src.withLevel(lastBotLevel).withPosition(new net.minecraft.world.phys.Vec3(lastBotX, lastBotY, lastBotZ));
        server.getCommands().performPrefixedCommand(src, "player " + botName + " spawn in survival");
        String kit = Kits.lastKit(botName);
        // The fake player can take a moment to appear, so the kit goes in once it is there.
        pendingKit = kit;
        announce.accept(botName + ": back for round " + (round + 1) + (kit == null ? ", with whatever it has." : " with the " + kit + " kit."));
    }

    void finish(String outcome) {
        if (finished) return;
        if (roundActive) endRound(outcome);
        finished = true;
        inputs.stopAll();
        announce.accept(botName + ": stopped after " + round + " rounds. " + wins + " wins, " + losses + " losses.");
    }

    String why() {
        String style = policy.choice() == null ? "no style yet" : policy.choice().use().label;
        String extra = policy.choice() != null && (policy.choice().use() == dev.reality.fightbot.core.Style.CRYSTAL
                || policy.choice().use() == dev.reality.fightbot.core.Style.ANCHOR) ? " " + policy.crystal().diagnose() : "";
        var m = policy.mobility();
        return botName + ": style " + style + ", state " + policy.stateName() + (policy.preparing() ? ", potting up" : "")
                + (everyone ? ", free-for-all, " + (targetName.isEmpty() ? "nobody in sight" : "after " + targetName) + ", " + kills + " kills" : "")
                + (roundActive || everyone ? "" : ", waiting for " + targetName) + "." + extra
                + " Getting there: " + policy.navigator().describe() + "."
                + " Pearls " + m.pearls + ", pearl catches " + m.pearlCatches + ", own pearl catches " + m.ownCatches
                + ", fall saves " + m.rescues + ", pillar blocks " + m.pillarBlocks + ".";
    }

    String status() {
        return botName + " fighting " + targetName + " with profile " + profile.name + (policy.choice() == null ? "" : ", style " + policy.choice().use().label) + ", state " + policy.stateName()
                + ", " + episode.ticks() / 20 + " s so far";
    }
}
