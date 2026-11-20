package com.reality.mobcraft.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Item model property "mobcraft:held_ticks": how many ticks the item has been in the hand
 * holding it, counting from when it was pulled out (switched to). 0 anywhere else, like the
 * inventory. The Axolotl Roe tin uses it to open its lid when you pull it out
 * (items/axolotl_roe.json). Registered by RangeSelectPropertiesMixin.
 */
public record HeldTicks() implements RangeSelectItemModelProperty {

    public static final MapCodec<HeldTicks> MAP_CODEC = MapCodec.unit(new HeldTicks());

    /** Per holder and hand: what was held, since when, and when it was last drawn. */
    private static final class Held {
        final String what;
        final long since;
        long seen;
        Held(String what, long since) { this.what = what; this.since = since; this.seen = since; }
    }
    private static final Map<LivingEntity, Held[]> HELD = new WeakHashMap<>();

    @Override
    public float get(ItemStack stack, ClientLevel level, ItemOwner owner, int seed) {
        if (owner == null || level == null) return 0f;
        LivingEntity holder = owner.asLivingEntity();
        if (holder == null) return 0f;
        int hand;
        if (holder.getMainHandItem() == stack || ItemStack.isSameItem(holder.getMainHandItem(), stack)) hand = 0;
        else if (holder.getOffhandItem() == stack || ItemStack.isSameItem(holder.getOffhandItem(), stack)) hand = 1;
        else return 0f;
        // The same item in the same hotbar slot counts as still held, even as eating changes the count.
        String what = stack.getItem() + (hand == 0 && holder instanceof Player player ? "@" + player.getInventory().getSelectedSlot() : "@off");
        // The holder's own tick count: it only ever goes up, unlike the world clock, which the server can set back.
        long now = holder.tickCount;
        Held[] hands = HELD.computeIfAbsent(holder, h -> new Held[2]);
        Held held = hands[hand];
        // Something else in that hand, or not drawn for a moment (put away and pulled out again): start over.
        if (held == null || !held.what.equals(what) || now - held.seen > 2 || now < held.since) {
            held = new Held(what, now);
            hands[hand] = held;
        }
        held.seen = now;
        return now - held.since;
    }

    @Override
    public MapCodec<HeldTicks> type() {
        return MAP_CODEC;
    }
}
