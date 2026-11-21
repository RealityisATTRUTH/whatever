package com.reality.mobcraft.neo.fabric.fabric.api.loot.v3;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;

public final class LootTableEvents {
    public static final Event<Modify> MODIFY = new Event<>(l -> (k, t, s, r) -> { for (Modify c : l) c.modifyLootTable(k, t, s, r); });
    public interface Modify { void modifyLootTable(ResourceKey<LootTable> key, TableBuilder tableBuilder, LootTableSource source, HolderLookup.Provider registries); }

    /** Stands in for the LootTable.Builder Fabric hands out: pools go straight onto the loaded table. */
    public static final class TableBuilder {
        private final LootTable table;
        public TableBuilder(LootTable table) { this.table = table; }
        public TableBuilder withPool(LootPool.Builder pool) { table.addPool(pool.build()); return this; }
        public TableBuilder pool(LootPool pool) { table.addPool(pool); return this; }
    }
}
