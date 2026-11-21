package com.reality.mobcraft.neo.fabric.fabric.api.client.keymapping.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;

public final class KeyMappingHelper {
    public static final List<KeyMapping> KEYS = new ArrayList<>();
    public static KeyMapping registerKeyMapping(KeyMapping key) { KEYS.add(key); return key; }
}
