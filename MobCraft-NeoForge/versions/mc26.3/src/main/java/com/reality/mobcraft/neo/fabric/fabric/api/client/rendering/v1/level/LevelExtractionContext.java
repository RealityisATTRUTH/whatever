package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.level;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;

public record LevelExtractionContext(DeltaTracker deltaTracker, ClientLevel level, LevelRenderState levelState) {}
