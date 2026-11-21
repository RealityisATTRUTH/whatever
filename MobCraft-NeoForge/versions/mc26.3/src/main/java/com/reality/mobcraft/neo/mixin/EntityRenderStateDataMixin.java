package com.reality.mobcraft.neo.mixin;

import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.FabricRenderState;
import com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1.RenderStateDataKey;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateDataMixin implements FabricRenderState {
    @Unique private final Map<RenderStateDataKey<?>, Object> mobcraft$data = new IdentityHashMap<>();
    @Override public Map<RenderStateDataKey<?>, Object> mobcraft$renderData() { return mobcraft$data; }
}
