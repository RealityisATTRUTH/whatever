package com.reality.mobcraft.neo.fabric.fabric.api.client.keybinding.v1;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;

public final class KeyBindingHelper {
    public static final List<KeyMapping> KEYS = new ArrayList<>();
    public static KeyMapping registerKeyBinding(KeyMapping key) { KEYS.add(key); return key; }
}
