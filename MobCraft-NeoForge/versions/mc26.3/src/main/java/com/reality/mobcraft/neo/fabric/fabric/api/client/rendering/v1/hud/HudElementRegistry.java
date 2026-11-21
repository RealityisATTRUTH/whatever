package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

/** Collects HUD elements; MobCraft's NeoForge client hooks hand them to NeoForge's GUI layers. */
public final class HudElementRegistry {
    public enum Where { FIRST, LAST, BEFORE, AFTER }
    public record Entry(Where where, Identifier anchor, Identifier id, HudElement element) {}
    public static final List<Entry> ENTRIES = new ArrayList<>();
    public static final Map<Identifier, Function<HudElement, HudElement>> REPLACED = new HashMap<>();

    public static void addFirst(Identifier id, HudElement element) { ENTRIES.add(new Entry(Where.FIRST, null, id, element)); }
    public static void addLast(Identifier id, HudElement element) { ENTRIES.add(new Entry(Where.LAST, null, id, element)); }
    public static void attachElementBefore(Identifier before, Identifier id, HudElement element) { ENTRIES.add(new Entry(Where.BEFORE, before, id, element)); }
    public static void attachElementAfter(Identifier after, Identifier id, HudElement element) { ENTRIES.add(new Entry(Where.AFTER, after, id, element)); }
    public static void replaceElement(Identifier id, Function<HudElement, HudElement> replacer) { REPLACED.put(id, replacer); }
}
