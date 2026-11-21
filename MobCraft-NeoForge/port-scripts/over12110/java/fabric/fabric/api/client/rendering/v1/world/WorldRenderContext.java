package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.world;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;

/** What a world-drawing listener gets just before entities are submitted. Stands in for Fabric API's, which 1.21.9 lacks. */
public record WorldRenderContext(SubmitNodeCollector commandQueue, PoseStack matrices, DeltaTracker tickCounter,
                                 ClientLevel world, LevelRenderState worldState) {}
