package com.reality.mobcraft.mixin.client;

import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface ModelPartChildrenAccess {
    @Accessor("children") Map<String, ModelPart> mobcraft$children();
}
