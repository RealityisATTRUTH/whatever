package com.reality.mobcraft.neo.fabric.fabric.api.loot.v3;
public enum LootTableSource {
    VANILLA, MOD, DATA_PACK, REPLACED;
    public boolean isBuiltin() { return this == VANILLA || this == MOD; }
}
