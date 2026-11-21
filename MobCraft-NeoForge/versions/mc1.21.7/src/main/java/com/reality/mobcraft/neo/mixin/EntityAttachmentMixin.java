package com.reality.mobcraft.neo.mixin;

import com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/** Gives every entity Fabric's attachment methods; the data lives in NeoForge's attachments. */
@Mixin(Entity.class)
public abstract class EntityAttachmentMixin implements AttachmentTarget {}
