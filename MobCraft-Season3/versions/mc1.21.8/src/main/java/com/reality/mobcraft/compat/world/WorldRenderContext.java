package com.reality.mobcraft.compat.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reality.mobcraft.compat.SubmitNodeCollector;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;

/** What a world-drawing listener gets just before entities are drawn. */
public record WorldRenderContext(SubmitNodeCollector commandQueue, PoseStack matrices, DeltaTracker tickCounter, ClientLevel world) {}
