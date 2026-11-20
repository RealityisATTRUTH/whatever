package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.compat.RenderStateDataKey;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives every entity render state a place for MobCraft's data (compat.RenderStateDataKey). */
@Mixin(EntityRenderState.class)
public abstract class RenderStateDataMixin implements RenderStateDataKey.Holder {
    @Unique private final Map<RenderStateDataKey<?>, Object> mobcraft$data = new HashMap<>();

    @Override
    public Map<RenderStateDataKey<?>, Object> mobcraft$data() { return mobcraft$data; }
}
