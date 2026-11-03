package com.reality.mobcraft;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Hands out MobCraft's own advancements, the ones vanilla triggers can't see,
 * like becoming a mob or opening RTS mode. They live in
 * data/mobcraft/advancement as JSON files with an "impossible" criterion
 * called "done", which only this code completes. The others (picking up a
 * Star Fragment, eating the Curse Antidote, crafting an armor upgrade) use
 * vanilla triggers in their JSON and need no code.
 *
 * They're split into four tabs in the Advancements screen, each with its own
 * root: MobCraft: Mobs (root), MobCraft: RTS (rts_root), MobCraft: Gear
 * (gear_root) and MobCraft: The Fracture Star (star_root). The Mobs tab has a
 * Roster with one advancement for every mob, unlocked by playing it.
 *
 * Vanilla tabs only show once their first advancement is earned, so on join
 * every player is given the five vanilla tab roots (Minecraft, Nether, The
 * End, Adventure, Husbandry). That makes every tab visible from the start.
 * Roots don't announce in chat, and nothing else in vanilla depends on them.
 */
public final class MobAdvancements {

    private static final String[] VANILLA_ROOTS = {
            "story/root", "nether/root", "end/root", "adventure/root", "husbandry/root"};

    private MobAdvancements() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> grantVanillaRoots(handler.player));
    }

    /** Completes each vanilla tab's root, so the tab shows. */
    private static void grantVanillaRoots(ServerPlayer player) {
        for (String path : VANILLA_ROOTS) {
            AdvancementHolder holder = player.level().getServer().getAdvancements().get(Identifier.withDefaultNamespace(path));
            if (holder == null) continue;
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            if (progress.isDone()) continue;
            List<String> left = new ArrayList<>();
            for (String criterion : progress.getRemainingCriteria()) left.add(criterion);
            for (String criterion : left) player.getAdvancements().award(holder, criterion);
        }
    }

    /** Gives the player this advancement, if it exists and they don't have it yet. */
    public static void award(ServerPlayer player, String name) {
        AdvancementHolder holder = player.level().getServer().getAdvancements().get(MobCraft.id(name));
        if (holder != null) player.getAdvancements().award(holder, "done");
    }
}
