package com.reality.mobcraft.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Before 1.21.9, two key binds on the same key don't both work: the game hands each key to just one of them. MobCraft's
 * Ability 2 is on G, where 1.21.8 also puts Quick Actions, so G only ever did Quick Actions. From 1.21.9 every bind
 * on a key gets it, and this does the same here for MobCraft's binds.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingShareMixin {
    @Shadow @Final private static Map<String, KeyMapping> ALL;
    @Shadow @Final private static Map<InputConstants.Key, KeyMapping> MAP;
    @Shadow private InputConstants.Key key;
    @Shadow private int clickCount;

    @Inject(method = "click", at = @At("TAIL"))
    private static void mobcraft$clickShared(InputConstants.Key key, CallbackInfo ci) {
        for (KeyMapping mapping : ALL.values()) {
            if (mobcraft$shares(mapping, key)) ((KeyMappingShareMixin) (Object) mapping).clickCount++;
        }
    }

    @Inject(method = "set", at = @At("TAIL"))
    private static void mobcraft$setShared(InputConstants.Key key, boolean down, CallbackInfo ci) {
        for (KeyMapping mapping : ALL.values()) {
            if (mobcraft$shares(mapping, key)) mapping.setDown(down);
        }
    }

    /** A MobCraft bind on this key that the game didn't pick for it. */
    @Unique
    private static boolean mobcraft$shares(KeyMapping mapping, InputConstants.Key key) {
        return mapping.getName().startsWith("key.mobcraft.") && key.equals(((KeyMappingShareMixin) (Object) mapping).key)
                && MAP.get(key) != mapping;
    }
}
