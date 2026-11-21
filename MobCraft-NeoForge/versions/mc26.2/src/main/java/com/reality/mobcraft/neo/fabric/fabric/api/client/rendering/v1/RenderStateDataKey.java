package com.reality.mobcraft.neo.fabric.fabric.api.client.rendering.v1;

import java.util.function.Supplier;

/** A key for extra data on a render state, like Fabric API's. */
public final class RenderStateDataKey<T> {
    private final Supplier<String> name;
    private RenderStateDataKey(Supplier<String> name) { this.name = name; }
    public static <T> RenderStateDataKey<T> create(Supplier<String> name) { return new RenderStateDataKey<>(name); }
    public static <T> RenderStateDataKey<T> create() { return new RenderStateDataKey<>(() -> "unnamed"); }
    @Override public String toString() { return name.get(); }
}
