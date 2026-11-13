package com.shumsic.autodrinkominousbottles;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/** The toggle key, K by default (R is MobCraft's first ability key, O opens the friends list in 26.3). */
public final class Keys {
    private Keys() {}
    public static KeyMapping create() {
        return new KeyMapping("key.auto_drink_ominous_bottles.toggle", InputConstants.KEY_K, "key.categories.misc");
    }
}
