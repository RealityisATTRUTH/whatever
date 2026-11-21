package com.reality.mobcraft.neo.mixin;

import com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class LevelAttachmentMixin implements AttachmentTarget {}
