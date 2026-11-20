package com.reality.mobcraft.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reality.mobcraft.MobCraft;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tiers, in the spirit of the Tiered mod. Weapons, tools, armor, bows and crossbows roll a quality the first time
 * they land in a player's inventory, and the tier shows in front of the item's name.
 *
 * Each kind has its own list. Plain changes nothing and is the most common. Each list also has four rare troll tiers
 * that do something silly (TrollTiers has their effects).
 *
 * Rerolling: an item at full durability plus one of its repair material in any anvil (string for bows, a stick for
 * crossbows) rerolls the tier for a few levels. Gloamite tools also roll two of their traits (GloamiteTraits); the
 * Duskvein Anvil rerolls those with a Dusk Binding and keeps the tier.
 *
 * Every roll comes from a seed stored on the item, so the anvil preview always matches what you get.
 */
public final class Tiers {

    private Tiers() {}

    public enum Kind { TOOL, ARMOR, BOW }

    /** One tier. desc is a tooltip line, for tiers whose effect isn't an attribute (bows and troll tiers). */
    public record Tier(String id, String name, ChatFormatting color, int weight, boolean troll, String desc) {
        Tier(String id, String name, ChatFormatting color, int weight) { this(id, name, color, weight, false, null); }
    }

    private static Tier troll(String id, String name, String desc) {
        return new Tier(id, name, ChatFormatting.LIGHT_PURPLE, 1, true, desc);
    }

    public static final Map<Kind, List<Tier>> LISTS = Map.of(
            Kind.TOOL, List.of(
                    new Tier("broken", "Broken", ChatFormatting.DARK_GRAY, 6),
                    new Tier("dull", "Dull", ChatFormatting.GRAY, 14),
                    new Tier("plain", "Plain", ChatFormatting.WHITE, 30),
                    new Tier("keen", "Keen", ChatFormatting.GREEN, 14),
                    new Tier("swift", "Swift", ChatFormatting.AQUA, 12),
                    new Tier("heavy", "Heavy", ChatFormatting.BLUE, 8),
                    new Tier("balanced", "Balanced", ChatFormatting.BLUE, 8),
                    new Tier("far_reaching", "Far-Reaching", ChatFormatting.LIGHT_PURPLE, 5),
                    new Tier("legendary", "Legendary", ChatFormatting.GOLD, 3),
                    troll("squeaky", "Squeaky", "Every hit squeaks and knocks mobs way back"),
                    troll("butterfingers", "Butterfingers", "Sometimes slips out of your hand"),
                    troll("confetti", "Confetti", "Kills burst into confetti. A little weaker"),
                    troll("backwards", "Backwards", "You hold it upside down")),
            Kind.ARMOR, List.of(
                    new Tier("cracked", "Cracked", ChatFormatting.DARK_GRAY, 6),
                    new Tier("worn", "Worn", ChatFormatting.GRAY, 14),
                    new Tier("plain", "Plain", ChatFormatting.WHITE, 30),
                    new Tier("sturdy", "Sturdy", ChatFormatting.GREEN, 14),
                    new Tier("tough", "Tough", ChatFormatting.AQUA, 12),
                    new Tier("light", "Light", ChatFormatting.BLUE, 8),
                    new Tier("steadfast", "Steadfast", ChatFormatting.BLUE, 8),
                    new Tier("hearty", "Hearty", ChatFormatting.LIGHT_PURPLE, 5),
                    new Tier("legendary", "Legendary", ChatFormatting.GOLD, 3),
                    troll("squeaky", "Squeaky", "Your steps squeak, even sneaking"),
                    troll("itchy", "Itchy", "Now and then you have to scratch"),
                    troll("drippy", "Drippy", "You drip water everywhere, even in the Nether"),
                    troll("bouncy", "Bouncy", "Less fall damage, but you bounce")),
            Kind.BOW, List.of(
                    new Tier("warped", "Warped", ChatFormatting.DARK_GRAY, 8, false, "Shots spread a little more"),
                    new Tier("slack", "Slack", ChatFormatting.GRAY, 14, false, "8% less arrow damage"),
                    new Tier("plain", "Plain", ChatFormatting.WHITE, 30),
                    new Tier("taut", "Taut", ChatFormatting.GREEN, 14, false, "10% more arrow damage"),
                    new Tier("quick", "Quick", ChatFormatting.AQUA, 12, false, "Draws 15% faster"),
                    new Tier("far_shot", "Far-Shot", ChatFormatting.BLUE, 8, false, "Arrows fly 20% faster and farther"),
                    new Tier("legendary", "Legendary", ChatFormatting.GOLD, 3, false, "15% more arrow damage, draws 10% faster"),
                    troll("boomerang", "Boomerang", "Some arrows turn around and come back"),
                    troll("party", "Party", "Arrows trail confetti and pop"),
                    troll("wobbly", "Wobbly", "Arrows wobble in the air"),
                    troll("chicken", "Chicken Shot", "Some arrows land as chickens")));

    /** A roll on an item: what it rolled and the seed the next roll comes from. */
    public record Rolled(List<String> names, long seed) {
        public static final Codec<Rolled> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("names").forGetter(Rolled::names),
                Codec.LONG.fieldOf("seed").forGetter(Rolled::seed)).apply(i, Rolled::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Rolled> STREAM = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Rolled::names,
                ByteBufCodecs.VAR_LONG, Rolled::seed, Rolled::new);
    }

    public static final DataComponentType<Rolled> TIER = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            MobCraft.id("tier"), DataComponentType.<Rolled>builder().persistent(Rolled.CODEC).networkSynchronized(Rolled.STREAM).build());

    public static final DataComponentType<Rolled> GLOAMITE_TRAITS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            MobCraft.id("gloamite_traits"), DataComponentType.<Rolled>builder().persistent(Rolled.CODEC).networkSynchronized(Rolled.STREAM).build());

    public static final int REROLL_COST = 3, TRAIT_REROLL_COST = 5;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Inventory inv = p.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (s.isEmpty()) continue;
                    if (needsRoll(s)) rollFresh(s, p.getRandom());
                    else if (lostModifiers(s)) applyTier(s, s.get(TIER));
                }
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register(TrollTiers::onArrow);
        TrollTiers.register();
    }

    // ---------------------------------------------------------------- what gets a tier

    public static boolean melee(ItemStack s) {
        return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(ItemTags.SPEARS) || s.is(Items.MACE) || s.is(Items.TRIDENT);
    }

    public static boolean miner(ItemStack s) {
        return s.is(ItemTags.PICKAXES) || s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES);
    }

    public static boolean armor(ItemStack s) {
        return s.is(ItemTags.HEAD_ARMOR) || s.is(ItemTags.CHEST_ARMOR) || s.is(ItemTags.LEG_ARMOR) || s.is(ItemTags.FOOT_ARMOR);
    }

    public static boolean bow(ItemStack s) {
        return s.is(Items.BOW) || s.is(Items.CROSSBOW);
    }

    public static Kind kind(ItemStack s) {
        if (s.getCount() != 1) return null;
        if (melee(s) || miner(s)) return Kind.TOOL;
        if (armor(s)) return Kind.ARMOR;
        if (bow(s)) return Kind.BOW;
        return null;
    }

    public static boolean tierable(ItemStack s) {
        return kind(s) != null;
    }

    private static boolean needsRoll(ItemStack s) {
        return (tierable(s) && !s.has(TIER)) || (GloamiteTraits.kind(s) != null && !s.has(GLOAMITE_TRAITS));
    }

    private static boolean ours(Identifier id) {
        return id.getNamespace().equals(MobCraft.MOD_ID) && id.getPath().startsWith("tier_");
    }

    /** A smithing upgrade (say netherite to Gloamite) keeps the tier but replaces the attributes, so they come back here. */
    private static boolean lostModifiers(ItemStack s) {
        Tier t = tier(s);
        if (t == null || !tierable(s)) return false;
        boolean wants = !modifiersFor(s, t).isEmpty();
        if (!wants) return false;
        for (ItemAttributeModifiers.Entry e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (ours(e.modifier().id())) return false;
        }
        return true;
    }

    private static void rollFresh(ItemStack s, RandomSource random) {
        if (tierable(s) && !s.has(TIER)) applyTier(s, rollTier(kind(s), random.nextLong()));
        if (GloamiteTraits.kind(s) != null && !s.has(GLOAMITE_TRAITS)) s.set(GLOAMITE_TRAITS, rollTraits(s, random.nextLong()));
    }

    // ---------------------------------------------------------------- rolling

    /** The tier a seed gives for this kind of item, carrying the seed for the roll after it. */
    public static Rolled rollTier(Kind kind, long seed) {
        List<Tier> list = LISTS.get(kind);
        RandomSource r = RandomSource.create(seed);
        int total = list.stream().mapToInt(Tier::weight).sum();
        int pick = r.nextInt(total);
        Tier chosen = list.get(0);
        for (Tier t : list) {
            if ((pick -= t.weight()) < 0) { chosen = t; break; }
        }
        return new Rolled(List.of(chosen.id()), r.nextLong());
    }

    /** Two different traits from this Gloamite tool's list. */
    public static Rolled rollTraits(ItemStack s, long seed) {
        RandomSource r = RandomSource.create(seed);
        List<String> pool = new ArrayList<>(GloamiteTraits.TRAITS.get(GloamiteTraits.kind(s)));
        List<String> got = new ArrayList<>();
        for (int i = 0; i < 2 && !pool.isEmpty(); i++) got.add(pool.remove(r.nextInt(pool.size())));
        return new Rolled(got, r.nextLong());
    }

    public static Tier tier(ItemStack s) {
        Rolled r = s.get(TIER);
        Kind k = kind(s);
        if (r == null || r.names().isEmpty() || k == null) return null;
        for (Tier t : LISTS.get(k)) if (t.id().equals(r.names().get(0))) return t;
        return null;
    }

    /** The id of this item's tier if it's of the given kind, otherwise null. */
    public static String id(ItemStack s, Kind kind) {
        Tier t = tier(s);
        return t != null && kind(s) == kind ? t.id() : null;
    }

    public static boolean is(ItemStack s, Kind kind, String id) {
        return id.equals(id(s, kind));
    }

    // ---------------------------------------------------------------- attributes

    private record Mod(Holder<Attribute> attr, String name, double amount, AttributeModifier.Operation op) {
        Mod(Holder<Attribute> attr, String name, double amount) { this(attr, name, amount, AttributeModifier.Operation.ADD_VALUE); }
    }

    private static EquipmentSlotGroup group(ItemStack s) {
        if (kind(s) == Kind.ARMOR) {
            Equippable eq = s.get(DataComponents.EQUIPPABLE);
            return eq == null ? EquipmentSlotGroup.ARMOR : EquipmentSlotGroup.bySlot(eq.slot());
        }
        return EquipmentSlotGroup.MAINHAND;
    }

    /** Sums the item's own (non-tier) ADD_VALUE modifiers on an attribute in its slot. */
    private static double own(ItemStack s, Holder<Attribute> attr) {
        EquipmentSlotGroup g = group(s);
        double sum = 0;
        for (ItemAttributeModifiers.Entry e : s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
            if (ours(e.modifier().id()) || e.slot() != g || !e.attribute().equals(attr)) continue;
            if (e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) sum += e.modifier().amount();
        }
        return sum;
    }

    /** The attribute changes a tier makes on this item. Bow tiers work on the arrows instead, so they have none. */
    private static List<Mod> modifiersFor(ItemStack s, Tier t) {
        List<Mod> out = new ArrayList<>();
        Kind k = kind(s);
        if (k == Kind.TOOL) {
            double dmg = 0, speed = 0, reach = 0, kb = 0;
            switch (t.id()) {
                case "broken" -> { dmg = -0.15; speed = -0.15; }
                case "dull" -> dmg = -0.08;
                case "keen" -> dmg = 0.06;
                case "swift" -> speed = 0.10;
                case "heavy" -> { dmg = 0.12; speed = -0.08; }
                case "balanced" -> { dmg = 0.05; speed = 0.05; }
                case "far_reaching" -> reach = 0.5;
                case "legendary" -> { dmg = 0.15; speed = 0.10; reach = 0.5; }
                case "squeaky" -> kb = 1.5;
                case "confetti" -> dmg = -0.05;
                default -> {}
            }
            if (melee(s)) out.add(new Mod(Attributes.ATTACK_DAMAGE, "damage", dmg * (1 + own(s, Attributes.ATTACK_DAMAGE))));
            else out.add(new Mod(Attributes.BLOCK_BREAK_SPEED, "mining", dmg, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            out.add(new Mod(Attributes.ATTACK_SPEED, "speed", speed * (4 + own(s, Attributes.ATTACK_SPEED))));
            out.add(new Mod(melee(s) ? Attributes.ENTITY_INTERACTION_RANGE : Attributes.BLOCK_INTERACTION_RANGE, "reach", reach));
            out.add(new Mod(Attributes.ATTACK_KNOCKBACK, "knockback", kb));
        } else if (k == Kind.ARMOR) {
            double armor = own(s, Attributes.ARMOR);
            switch (t.id()) {
                case "cracked" -> out.add(new Mod(Attributes.ARMOR, "armor", -0.15 * armor));
                case "worn" -> out.add(new Mod(Attributes.ARMOR, "armor", -0.08 * armor));
                case "sturdy" -> out.add(new Mod(Attributes.ARMOR, "armor", 1));
                case "tough" -> out.add(new Mod(Attributes.ARMOR_TOUGHNESS, "toughness", 1));
                case "light" -> out.add(new Mod(Attributes.MOVEMENT_SPEED, "speed", 0.03, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                case "steadfast" -> out.add(new Mod(Attributes.KNOCKBACK_RESISTANCE, "knockback", 0.1));
                case "hearty" -> out.add(new Mod(Attributes.MAX_HEALTH, "health", 1));
                case "legendary" -> {
                    out.add(new Mod(Attributes.ARMOR, "armor", 1));
                    out.add(new Mod(Attributes.ARMOR_TOUGHNESS, "toughness", 1));
                    out.add(new Mod(Attributes.MAX_HEALTH, "health", 1));
                }
                default -> {}
            }
        }
        out.removeIf(m -> Math.abs(m.amount()) < 1e-4);
        return out;
    }

    /** Sets the tier on the stack and rewrites its tier attribute modifiers to match. */
    public static void applyTier(ItemStack s, Rolled rolled) {
        s.set(TIER, rolled);
        Tier t = tier(s);
        ItemAttributeModifiers old = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        boolean had = false;
        for (ItemAttributeModifiers.Entry e : old.modifiers()) {
            if (ours(e.modifier().id())) { had = true; continue; }
            b.add(e.attribute(), e.modifier(), e.slot(), e.display());
        }
        List<Mod> mods = t == null ? List.of() : modifiersFor(s, t);
        if (mods.isEmpty() && !had) return;   // bows and plain items keep their own attributes untouched
        EquipmentSlotGroup g = group(s);
        String slot = g.getSerializedName();
        for (Mod m : mods) {
            b.add(m.attr(), new AttributeModifier(MobCraft.id("tier_" + m.name() + "_" + slot),
                    Math.round(m.amount() * 100) / 100.0, m.op()), g);
        }
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, b.build());
    }

    // ---------------------------------------------------------------- anvil

    private static boolean rerollMaterial(ItemStack left, ItemStack right) {
        if (left.is(Items.BOW)) return right.is(Items.STRING);
        if (left.is(Items.CROSSBOW)) return right.is(Items.STICK);
        return left.isValidRepairItem(right);
    }

    /** The item with its tier rerolled, or EMPTY if this pair isn't a reroll. */
    public static ItemStack rerollTier(ItemStack left, ItemStack right) {
        if (!tierable(left) || !left.has(TIER) || left.isDamaged() || right.isEmpty() || !rerollMaterial(left, right)) return ItemStack.EMPTY;
        ItemStack out = left.copy();
        applyTier(out, rollTier(kind(left), left.get(TIER).seed()));
        return out;
    }

    /** The Gloamite tool with its traits rerolled, or EMPTY if this pair isn't a trait reroll. */
    public static ItemStack rerollTraits(ItemStack left, ItemStack right) {
        if (GloamiteTraits.kind(left) == null || !left.has(GLOAMITE_TRAITS) || !right.is(Gloamite.DUSK_BINDING)) return ItemStack.EMPTY;
        ItemStack out = left.copy();
        out.set(GLOAMITE_TRAITS, rollTraits(left, left.get(GLOAMITE_TRAITS).seed()));
        return out;
    }

    // ---------------------------------------------------------------- name

    /** The item's name with its tier in front, for items that haven't been renamed. */
    public static Component name(ItemStack s, Component original) {
        Tier t = tier(s);
        if (t == null || t.id().equals("plain") || s.has(DataComponents.CUSTOM_NAME)) return original;
        MutableComponent named = Component.empty().append(Component.literal(t.name() + " ").withStyle(t.color()));
        return named.append(original);
    }

    /** True if any piece of armor this entity is wearing has the given tier. */
    public static boolean wearing(net.minecraft.world.entity.LivingEntity e, String id) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (is(e.getItemBySlot(slot), Kind.ARMOR, id)) return true;
        }
        return false;
    }
}
