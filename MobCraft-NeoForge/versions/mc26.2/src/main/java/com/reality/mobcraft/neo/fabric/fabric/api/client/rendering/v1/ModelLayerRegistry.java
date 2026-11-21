package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public final class ModelLayerRegistry {
    public static final Map<ModelLayerLocation, TexturedModelDataProvider> LAYERS = new LinkedHashMap<>();
    public static void registerModelLayer(ModelLayerLocation location, TexturedModelDataProvider provider) { LAYERS.put(location, provider); }
    public interface TexturedModelDataProvider { LayerDefinition createModelData(); }
}
