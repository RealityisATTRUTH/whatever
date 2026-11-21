package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.level;

import com.reality.mobcraft.neo.fabric.fabric.api.event.Event;

/** Fabric's level render events, fired from NeoForge's SubmitCustomGeometryEvent and ExtractLevelRenderStateEvent. */
public final class LevelRenderEvents {
    public static final Event<CollectSubmits> COLLECT_SUBMITS = new Event<>(l -> c -> { for (CollectSubmits x : l) x.collectSubmits(c); });
    public static final Event<EndExtraction> END_EXTRACTION = new Event<>(l -> c -> { for (EndExtraction x : l) x.endExtraction(c); });
    public interface CollectSubmits { void collectSubmits(LevelRenderContext context); }
    public interface EndExtraction { void endExtraction(LevelExtractionContext context); }
}
