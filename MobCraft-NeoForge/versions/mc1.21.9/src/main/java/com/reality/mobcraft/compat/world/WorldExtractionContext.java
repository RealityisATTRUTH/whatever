package com.reality.mobcraft.compat.world;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.LevelRenderState;

/** What a listener gets once the world has been gathered up to draw. Stands in for Fabric API's, which 1.21.9 lacks. */
public record WorldExtractionContext(DeltaTracker tickCounter, ClientLevel world, LevelRenderState worldState) {}
