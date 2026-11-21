package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import java.util.Map;

/** Fabric's extra-data methods, added to entity render states. */
public interface FabricRenderState {
    default Map<RenderStateDataKey<?>, Object> mobcraft$renderData() { throw new IllegalStateException("render data mixin missing"); }
    @SuppressWarnings("unchecked")
    default <T> T getData(RenderStateDataKey<T> key) { return (T) mobcraft$renderData().get(key); }
    @SuppressWarnings("unchecked")
    default <T> T getDataOrDefault(RenderStateDataKey<T> key, T fallback) { return (T) mobcraft$renderData().getOrDefault(key, fallback); }
    default <T> void setData(RenderStateDataKey<T> key, T value) {
        if (value == null) mobcraft$renderData().remove(key);
        else mobcraft$renderData().put(key, value);
    }
    default void clearExtraData() { mobcraft$renderData().clear(); }
}
