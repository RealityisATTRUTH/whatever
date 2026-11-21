package com.reality.mobcraft.item;

import com.reality.mobcraft.mob.MobTicker;
import com.reality.mobcraft.mob.PlayerMob;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Curse Antidote: a bowl of milk, golden apples, mushrooms and a golden
 * carrot. Eating it lifts any curse right away, including one from the Curse
 * potion. Milk alone doesn't. Leaves the empty bowl.
 *
 * Recipe (shapeless): milk bucket, enchanted golden apple, golden apple,
 * brown mushroom, red mushroom, golden carrot, bowl. The bucket comes back.
 */
public final class CurseAntidoteItem extends Item {

    public CurseAntidoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            boolean wasCursed = PlayerMob.isCursed(player);
            MobTicker.uncurse(player); // also clears the Curse effect, mob or not
            if (wasCursed) {
                player.sendSystemMessage(Component.literal("The antidote lifts your curse.").withStyle(ChatFormatting.GREEN));
            }
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
