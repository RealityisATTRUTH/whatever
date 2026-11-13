package com.reality.mobcraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.PlayerTeam;

import java.util.List;

/**
 * Totems the Evoker gives himself are bound to him. A bound totem saves the Evoker
 * and players on his team, so he can still gear his team with them. Anyone else
 * holding one dies as if it weren't there, so an enemy who takes them off him or
 * gets them traded on can't use them. The totems the Evoker drops on death aren't
 * bound.
 *
 * The binding is the Evoker's uuid and name in the item's custom data, shown on
 * the item as a lore line. Team is checked when the holder would die, using the
 * Evoker's name on the scoreboard, so it works while he's offline.
 * See mixin/BoundTotemMixin for where it's checked.
 */
public final class BoundTotem {

    private static final String UUID_KEY = "mobcraft_bound_to";
    private static final String NAME_KEY = "mobcraft_bound_name";

    private BoundTotem() {}

    /** Binds a totem to this player and labels it. Server only. */
    public static void bind(ItemStack stack, ServerPlayer owner) {
        String name = owner.getName().getString();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(UUID_KEY, owner.getStringUUID());
            tag.putString(NAME_KEY, name);
        });
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Bound to " + name + " and their team").withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.literal("Doesn't save anyone else").withStyle(ChatFormatting.DARK_GRAY))));
    }

    /** Whether this totem may save this entity. Unbound totems save anyone. */
    public static boolean canSave(LivingEntity holder, ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return true;
        CompoundTag tag = data.copyTag();
        String owner = tag.getStringOr(UUID_KEY, "");
        if (owner.isEmpty()) return true;
        if (!(holder instanceof Player player)) return false;
        if (player.getStringUUID().equals(owner)) return true;
        PlayerTeam team = player.getTeam();
        if (team == null) return false;
        String ownerName = tag.getStringOr(NAME_KEY, "");
        return team.getPlayers().contains(ownerName);
    }
}
