package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;

public record LevelRenderContext(SubmitNodeCollector submitNodeCollector, PoseStack poseStack, DeltaTracker deltaTracker,
                                 ClientLevel level, LevelRenderState levelState) {}
