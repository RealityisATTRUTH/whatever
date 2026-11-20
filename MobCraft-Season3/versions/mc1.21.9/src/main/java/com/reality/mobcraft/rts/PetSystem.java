package com.reality.mobcraft.rts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.kit.KitUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pets. Any player with a mob can claim mobs of their own kind and keep them like a dog:
 * a Shulker claims shulkers, a Piglin claims piglins. Variants count too (VARIANTS):
 * a Skeleton also claims strays, bogged and parched, a Piglin piglin brutes and
 * zombified piglins, a Ghast happy ghasts, an Elder Guardian guardians.
 *
 *   Claim       sneak and right-click a mob of your kind with an empty hand. Players
 *               without a mob can't claim, and neither can anyone claim bosses or
 *               someone else's pet.
 *   Sit / stand sneak and right-click your own pet with an empty hand. A sitting
 *               pet stays where it is and doesn't follow or join your fights.
 *               Giving it an RTS order stands it up.
 *   Follow      pets follow you (Mob Behavior's Follow Me), and a pet more than
 *               20 blocks away in the same world teleports to you, like a dog.
 *   Fights      pets defend you and attack what you hit (Defend Me, Attack What
 *               I Hit), never hurt you, and can be commanded in RTS.
 *   Release     /pets release lets go of the pet you're looking at.
 *   Switching   changing to another mob frees every pet the new mob couldn't
 *               claim (onMobSwitch). They count as released.
 *   My Pets     the Team & Pets screen's My Pets button lists every pet with Sit or
 *               Stand and Release buttons (/pets sit <uuid>, /pets release <uuid>).
 *   /pets       your counts and every pet you have now.
 *
 * Pets never despawn and don't burn in sunlight (PetSunMixin). Each player has a file, world/mobcraft_pets/<uuid>.json, with
 * how many pets they've claimed, lost and released, and a line for each pet: what it
 * is, its name, when it was claimed, its kills, and if it's gone, when and how. The
 * file is updated every time one of those changes. Pets that only get an owner from
 * an ability or /rtstest claim aren't in the file.
 */
public final class PetSystem {

    private static final String DIR = "mobcraft_pets";
    private static final int TELEPORT_DISTANCE = 20;
    private static final int MAX_HISTORY = 300;
    private static final String SIT_MODIFIER = "pet_sitting";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final String ALIVE = "alive";
    public static final String DIED = "died";
    public static final String RELEASED = "released";

    /** One player's pet file. Field names are what's written to the JSON. */
    static final class PetBook {
        String player = "";
        int claimed;
        int lost;
        int released;
        List<PetRecord> pets = new ArrayList<>();
    }

    /** One pet's line in the file. */
    static final class PetRecord {
        String uuid;
        String type;
        String name;
        String claimedAt;
        int kills;
        String status = ALIVE;
        String endedAt;
        String cause;
    }

    /** Other mobs each kind can claim besides its own, by vanilla id. */
    private static final Map<MobType, List<String>> VARIANTS = Map.of(
            MobType.SKELETON, List.of("minecraft:stray", "minecraft:bogged", "minecraft:parched"),
            MobType.PIGLIN, List.of("minecraft:piglin_brute", "minecraft:zombified_piglin"),
            MobType.GHAST, List.of("minecraft:happy_ghast"),
            MobType.ELDER_GUARDIAN, List.of("minecraft:guardian"));

    private static final Map<UUID, PetBook> BOOKS = new HashMap<>();
    private static Path folder;

    private PetSystem() {}

    // --- Setup --------------------------------------------------------------------

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            folder = server.getWorldPath(LevelResource.ROOT).resolve(DIR);
            BOOKS.clear();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BOOKS.clear());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PetBook book = book(handler.player.getUUID());
            book.player = handler.player.getName().getString();
            syncCounts(handler.player, book);
        });

        UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer owner)) return InteractionResult.PASS;
            return interact(owner, hand, target);
        });

        // A pet that left (mob switch, release) while its chunk wasn't loaded goes wild when it loads.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof Mob mob)) return;
            UUID owner = ownerOf(mob);
            if (owner == null) return;
            PetRecord record = find(book(owner), mob.getUUID());
            if (record != null && !ALIVE.equals(record.status)) free(mob);
            else if (record == null && summonerChanged(mob, owner)) free(mob);
        });

        // A pet dying, and a pet getting a kill.
        ServerLivingEntityEvents.AFTER_DEATH.register(PetSystem::onDeath);

        // A pet turning into another mob, like a zombie drowning: the new mob is still your pet.
        ServerLivingEntityEvents.MOB_CONVERSION.register((from, to, params) -> converted(from, to));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 5) tick(server);
        });
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pets")
                .executes(PetSystem::list)
                .then(Commands.literal("release").executes(PetSystem::releaseLooked)
                        .then(Commands.argument("pet", StringArgumentType.word()).executes(ctx -> byId(ctx, false))))
                .then(Commands.literal("sit")
                        .then(Commands.argument("pet", StringArgumentType.word()).executes(ctx -> byId(ctx, true)))));
    }

    /**
     * /pets sit <uuid> sits or stands that pet, /pets release <uuid> lets it go. Used by
     * the My Pets screen's buttons. The pet has to be yours and loaded near you.
     */
    private static int byId(CommandContext<CommandSourceStack> ctx, boolean sit) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "pet");
        PetRecord record;
        Entity entity;
        try {
            record = find(book(player.getUUID()), UUID.fromString(id));
            entity = player.level().getEntity(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            record = null;
            entity = null;
        }
        if (record == null || !ALIVE.equals(record.status)) {
            ctx.getSource().sendFailure(Component.literal("That's not one of your pets."));
            return 0;
        }
        if (!(entity instanceof Mob pet) || !pet.isAlive()) {
            ctx.getSource().sendFailure(Component.literal("Your " + record.name + " is too far away. Go to it first."));
            return 0;
        }
        String name = nameOf(pet);
        if (sit) {
            setSitting(pet, !isSitting(pet));
            KitUtil.actionBar(player, isSitting(pet) ? "Your " + name + " is sitting." : "Your " + name + " is following you again.");
        } else {
            release(player, pet);
            KitUtil.actionBar(player, "You released your " + name + ". It's wild again.");
        }
        syncList(player, book(player.getUUID()));
        return 1;
    }

    // --- Claiming, sitting -------------------------------------------------------------

    private static InteractionResult interact(ServerPlayer player, InteractionHand hand, Entity target) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!player.getMainHandItem().isEmpty() || !(target instanceof Mob mob) || !mob.isAlive()) return InteractionResult.PASS;
        String owner = mob.getAttached(MobAttachments.OWNER);
        if (owner != null && owner.equals(player.getStringUUID())) {
            if (!hasRecord(player.getUUID(), mob)) return InteractionResult.PASS; // a summon, not a pet
            setSitting(mob, !isSitting(mob));
            KitUtil.actionBar(player, isSitting(mob)
                    ? "Your " + nameOf(mob) + " is sitting. It stays here."
                    : "Your " + nameOf(mob) + " is following you again.");
            return InteractionResult.SUCCESS;
        }
        if (owner != null) {
            KitUtil.actionBar(player, "That " + nameOf(mob) + " is someone else's pet.");
            return InteractionResult.FAIL;
        }
        if (mob instanceof EnderDragon || mob instanceof WitherBoss) {
            KitUtil.actionBar(player, "Bosses can't be claimed.");
            return InteractionResult.FAIL;
        }
        // Only your own kind: a Shulker claims shulkers.
        MobType type = PlayerMob.get(player);
        EntityType<?> kind = type == null ? null : RtsControl.kindOf(type);
        if (kind == null) {
            KitUtil.actionBar(player, "You need a mob to claim pets.");
            return InteractionResult.FAIL;
        }
        if (!canClaim(type, mob)) {
            KitUtil.actionBar(player, "As a " + type.displayName + " you can only claim "
                    + kind.getDescription().getString().toLowerCase(java.util.Locale.ROOT) + "s"
                    + (VARIANTS.containsKey(type) ? " and their variants." : "."));
            return InteractionResult.FAIL;
        }
        claim(player, mob);
        return InteractionResult.SUCCESS;
    }

    /** Makes the mob the player's pet and adds it to their file. Server only. */
    public static void claim(ServerPlayer player, Mob mob) {
        MobOwnership.setOwner(mob, player);
        mob.setPersistenceRequired();
        KitUtil.calm(mob, player);
        PetBook book = book(player.getUUID());
        book.player = player.getName().getString();
        PetRecord record = find(book, mob.getUUID());
        if (record == null || !ALIVE.equals(record.status)) {
            record = new PetRecord();
            record.uuid = mob.getUUID().toString();
            book.pets.add(record);
            book.claimed++;
        }
        record.type = typeId(mob);
        record.name = nameOf(mob);
        record.claimedAt = now();
        record.status = ALIVE;
        save(player.getUUID(), book);
        syncCounts(player, book);

        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(),
                    6, 0.4, 0.3, 0.4, 0.0);
            level.playSound(null, mob.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
        }
        KitUtil.actionBar(player, "You claimed a " + nameOf(mob) + ". Sneak + right-click it to make it sit.");
    }

    /** Lets go of a pet: it's wild again. Server only. */
    public static void release(ServerPlayer player, Mob mob) {
        setSitting(mob, false);
        MobOwnership.clearOwner(mob);
        PetBook book = book(player.getUUID());
        PetRecord record = find(book, mob.getUUID());
        if (record != null && ALIVE.equals(record.status)) {
            record.status = RELEASED;
            record.endedAt = now();
            book.released++;
            save(player.getUUID(), book);
            syncCounts(player, book);
        }
    }

    /** Whether a player who is this mob can have this mob as a pet: their own kind or one of its variants. */
    public static boolean canClaim(MobType type, Entity mob) {
        if (type == null) return false;
        EntityType<?> kind = RtsControl.kindOf(type);
        return (kind != null && mob.getType() == kind) || VARIANTS.getOrDefault(type, List.of()).contains(typeId(mob));
    }

    private static boolean canClaim(MobType type, String typeId) {
        if (type == null || typeId == null) return false;
        EntityType<?> kind = RtsControl.kindOf(type);
        return (kind != null && typeId.equals(typeId(kind))) || VARIANTS.getOrDefault(type, List.of()).contains(typeId);
    }

    /**
     * Called when a player's mob changes. Every pet the new mob couldn't claim leaves:
     * a Shulker's shulkers stop being pets when he becomes a Creeper. They count as
     * released, and the file says why. Pets of a variant the new mob shares stay.
     * Pets loaded anywhere are freed now; ones in unloaded chunks are freed when they
     * load (see register, ENTITY_LOAD).
     */
    public static void onMobSwitch(ServerPlayer player, MobType old, MobType next) {
        if (old == next) return;
        PetBook book = book(player.getUUID());
        int left = 0;
        for (PetRecord record : book.pets) {
            if (!ALIVE.equals(record.status) || canClaim(next, record.type)) continue;
            record.status = RELEASED;
            record.endedAt = now();
            record.cause = "left when you stopped being " + (old == null ? "its kind" : "the " + old.displayName);
            book.released++;
            left++;
            Entity pet = findLoaded(player.level().getServer(), UUID.fromString(record.uuid));
            if (pet instanceof Mob mob) free(mob);
        }
        freeSummons(player, book);
        if (left == 0) return;
        save(player.getUUID(), book);
        syncCounts(player, book);
        player.sendSystemMessage(Component.literal(left == 1
                ? "Your pet left you when you changed mobs."
                : "Your " + left + " pets left you when you changed mobs.").withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Mobs an ability summoned (an Axolotl's pod, an Evoker's vexes) belong to the mob that summoned them,
     * not to the player. When the player changes mobs, every loaded one stops being theirs, so the new mob
     * can't select or command them. Ones in unloaded chunks go when they load (see register, ENTITY_LOAD).
     */
    private static void freeSummons(ServerPlayer player, PetBook book) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            List<Mob> summons = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob && MobOwnership.isOwnedBy(mob, player) && find(book, mob.getUUID()) == null) {
                    summons.add(mob);
                }
            }
            for (Mob mob : summons) free(mob);
        }
    }

    /** A summoned mob whose summoner is online and is no longer the mob that summoned it. */
    private static boolean summonerChanged(Mob mob, UUID owner) {
        String as = mob.getAttached(MobAttachments.SUMMONED_AS);
        if (as == null || mob.level().getServer() == null) return false;
        ServerPlayer player = mob.level().getServer().getPlayerList().getPlayer(owner);
        if (player == null) return false;
        MobType now = PlayerMob.get(player);
        return now == null || !as.equals(now.id());
    }

    /** Takes a mob back to the wild: no owner, not sitting, no RTS orders. */
    private static void free(Mob mob) {
        setSitting(mob, false);
        MobOwnership.clearOwner(mob);
        mob.removeAttached(MobAttachments.SUMMONED_AS);
        RtsMobOrders.stop(mob);
    }

    private static Entity findLoaded(MinecraftServer server, UUID id) {
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static String typeId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    public static boolean isSitting(Entity mob) {
        return Boolean.TRUE.equals(mob.getAttached(MobAttachments.PET_SITTING));
    }

    /** Sits a pet down or stands it up. A sitting pet can't walk: its speed is taken away, and that's saved with it. */
    public static void setSitting(Mob mob, boolean sit) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (sit) {
            mob.setAttached(MobAttachments.PET_SITTING, true);
            mob.getNavigation().stop();
            mob.setTarget(null);
            if (speed != null) speed.addOrReplacePermanentModifier(new AttributeModifier(MobCraft.id(SIT_MODIFIER), -1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            mob.removeAttached(MobAttachments.PET_SITTING);
            if (speed != null) speed.removeModifier(MobCraft.id(SIT_MODIFIER));
        }
    }

    // --- Death, kills, conversion --------------------------------------------------------

    private static void onDeath(LivingEntity entity, DamageSource source) {
        // A kill for the pet that did it.
        if (source.getEntity() instanceof Mob killer) {
            UUID owner = ownerOf(killer);
            if (owner != null) {
                PetBook book = book(owner);
                PetRecord record = find(book, killer.getUUID());
                if (record != null && ALIVE.equals(record.status)) {
                    record.kills++;
                    save(owner, book);
                }
            }
        }
        // A pet lost.
        if (!(entity instanceof Mob pet)) return;
        UUID owner = ownerOf(pet);
        if (owner == null) return;
        PetBook book = book(owner);
        PetRecord record = find(book, pet.getUUID());
        if (record == null || !ALIVE.equals(record.status)) return;
        record.status = DIED;
        record.endedAt = now();
        record.cause = source.getLocalizedDeathMessage(pet).getString();
        book.lost++;
        save(owner, book);
        ServerPlayer online = pet.level().getServer() == null ? null : pet.level().getServer().getPlayerList().getPlayer(owner);
        if (online != null) {
            syncCounts(online, book);
            online.sendSystemMessage(Component.literal("Your pet died: " + record.cause + ". Pets now: "
                    + alive(book) + ", lost so far: " + book.lost + ".").withStyle(ChatFormatting.RED));
        }
    }

    private static void converted(Mob from, Mob to) {
        UUID owner = ownerOf(from);
        if (owner == null) return;
        to.setAttached(MobAttachments.OWNER, owner.toString());
        to.setPersistenceRequired();
        if (isSitting(from)) setSitting(to, true);
        PetBook book = book(owner);
        PetRecord record = find(book, from.getUUID());
        if (record == null) return;
        record.uuid = to.getUUID().toString();
        record.type = typeId(to);
        record.name = nameOf(to);
        save(owner, book);
    }

    // --- Every second: sitting pets stay, far pets come to you ---------------------------------

    private static void tick(MinecraftServer server) {
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            PetBook book = book(owner.getUUID());
            syncList(owner, book);
            for (PetRecord record : book.pets) {
                if (!ALIVE.equals(record.status)) continue;
                Entity entity = owner.level().getEntity(UUID.fromString(record.uuid));
                if (!(entity instanceof Mob pet) || !pet.isAlive()) continue; // another world, or not loaded
                if (isSitting(pet)) {
                    pet.getNavigation().stop();
                    continue;
                }
                if (!MobBehavior.isOn(owner, MobBehavior.FOLLOW) || RtsMobOrders.isBusy(pet)) continue;
                if (pet.getTarget() != null && pet.getTarget().isAlive()) continue;
                if (pet.distanceTo(owner) > TELEPORT_DISTANCE && !owner.isSpectator()) teleportToOwner(pet, owner);
            }
        }
    }

    private static void teleportToOwner(Mob pet, ServerPlayer owner) {
        ServerLevel level = owner.level();
        BlockPos spot = KitUtil.nearestFreeSpot(level, owner.blockPosition().offset(
                owner.getRandom().nextInt(5) - 2, 0, owner.getRandom().nextInt(5) - 2), 3);
        if (spot == null) return;
        pet.getNavigation().stop();
        pet.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
    }

    // --- /pets ---------------------------------------------------------------------

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        PetBook book = book(player.getUUID());
        MutableComponent text = Component.literal("Your pets").withStyle(ChatFormatting.BOLD);
        text.append(Component.literal("\nNow: " + alive(book) + "   Claimed: " + book.claimed
                + "   Lost: " + book.lost + "   Released: " + book.released).withStyle(ChatFormatting.GRAY));
        for (PetRecord record : book.pets) {
            if (!ALIVE.equals(record.status)) continue;
            Entity pet = player.level().getEntity(UUID.fromString(record.uuid));
            String where = pet == null ? "far away"
                    : (isSitting(pet) ? "sitting, " : "") + Math.round(pet.distanceTo(player)) + " blocks away";
            text.append(Component.literal("\n  " + record.name).withStyle(ChatFormatting.GREEN));
            text.append(Component.literal(" - " + where + ", " + record.kills
                    + (record.kills == 1 ? " kill" : " kills")).withStyle(ChatFormatting.GRAY));
        }
        if (alive(book) == 0) {
            text.append(Component.literal("\nSneak + right-click a mob of your own kind with an empty hand to claim it.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        ctx.getSource().sendSuccess(() -> text, false);
        return alive(book);
    }

    private static int releaseLooked(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Mob pet = lookedAtPet(player, 10);
        if (pet == null) {
            ctx.getSource().sendFailure(Component.literal("Look at one of your pets, then run /pets release."));
            return 0;
        }
        String name = nameOf(pet);
        release(player, pet);
        ctx.getSource().sendSuccess(() -> Component.literal("You released your " + name + ". It's wild again.")
                .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    /** The player's own pet they're looking at, within range. */
    private static Mob lookedAtPet(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        AABB area = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1);
        EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end, area,
                e -> e instanceof Mob mob && player.getStringUUID().equals(mob.getAttached(MobAttachments.OWNER)), range * range);
        return hit == null ? null : (Mob) hit.getEntity();
    }

    // --- The files --------------------------------------------------------------------

    private static PetBook book(UUID player) {
        return BOOKS.computeIfAbsent(player, PetSystem::load);
    }

    private static boolean hasRecord(UUID player, Mob mob) {
        PetRecord record = find(book(player), mob.getUUID());
        return record != null && ALIVE.equals(record.status);
    }

    private static PetRecord find(PetBook book, UUID mob) {
        String id = mob.toString();
        for (PetRecord record : book.pets) if (id.equals(record.uuid)) return record;
        return null;
    }

    private static int alive(PetBook book) {
        int count = 0;
        for (PetRecord record : book.pets) if (ALIVE.equals(record.status)) count++;
        return count;
    }

    private static PetBook load(UUID player) {
        if (folder != null) {
            Path file = folder.resolve(player + ".json");
            if (Files.exists(file)) {
                try {
                    PetBook book = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), PetBook.class);
                    if (book != null) {
                        if (book.pets == null) book.pets = new ArrayList<>();
                        return book;
                    }
                } catch (Exception e) {
                    MobCraft.LOGGER.warn("Couldn't read the pet file for {}", player, e);
                }
            }
        }
        return new PetBook();
    }

    private static void save(UUID player, PetBook book) {
        // Long histories drop their oldest gone pets first. Pets you have now are always kept.
        while (book.pets.size() > MAX_HISTORY) {
            int oldest = -1;
            for (int i = 0; i < book.pets.size(); i++) {
                if (!ALIVE.equals(book.pets.get(i).status)) {
                    oldest = i;
                    break;
                }
            }
            if (oldest < 0) break;
            book.pets.remove(oldest);
        }
        if (folder == null) return;
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(player + ".json"), GSON.toJson(book), StandardCharsets.UTF_8);
        } catch (Exception e) {
            MobCraft.LOGGER.warn("Couldn't save the pet file for {}", player, e);
        }
    }

    /** Sends the player their pet list for the My Pets screen, if it changed. */
    private static void syncList(ServerPlayer player, PetBook book) {
        List<String> lines = new ArrayList<>();
        for (PetRecord record : book.pets) {
            if (!ALIVE.equals(record.status)) continue;
            Entity pet = player.level().getEntity(UUID.fromString(record.uuid));
            boolean loaded = pet != null && pet.isAlive();
            int sitting = loaded ? (isSitting(pet) ? 1 : 0) : -1;
            int distance = loaded ? (int) Math.round(pet.distanceTo(player)) : -1;
            String name = record.name == null ? "Pet" : record.name.replace('|', '/');
            lines.add(record.uuid + "|" + name + "|" + record.kills + "|" + sitting + "|" + distance);
            if (lines.size() >= 300) break;
        }
        if (!lines.equals(player.getAttached(MobAttachments.PET_LIST))) player.setAttached(MobAttachments.PET_LIST, lines);
    }

    private static void syncCounts(ServerPlayer player, PetBook book) {
        syncList(player, book);
        player.setAttached(MobAttachments.PET_COUNTS, List.of(alive(book), book.claimed, book.lost, book.released));
    }

    // --- Small helpers -------------------------------------------------------------------

    private static UUID ownerOf(Entity mob) {
        String owner = mob.getAttached(MobAttachments.OWNER);
        if (owner == null) return null;
        try {
            return UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nameOf(Entity mob) {
        return mob.getName().getString();
    }

    private static String typeId(Entity mob) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
    }

    private static String now() {
        return LocalDateTime.now().format(WHEN);
    }
}
