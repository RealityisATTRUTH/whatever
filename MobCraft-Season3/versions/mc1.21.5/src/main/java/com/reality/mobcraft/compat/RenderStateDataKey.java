package com.reality.mobcraft.compat;

import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * A piece of MobCraft data carried on an entity's render state, from where the entity is read to where it's drawn.
 * Fabric API has this from its 1.21.8 builds; for 1.21.7 MobCraft keeps the data itself (RenderStateDataMixin).
 */
public final class RenderStateDataKey<T> {
    private final Supplier<String> name;

    private RenderStateDataKey(Supplier<String> name) { this.name = name; }

    public static <T> RenderStateDataKey<T> create(Supplier<String> name) { return new RenderStateDataKey<>(name); }

    @Override public String toString() { return name.get(); }

    /** The render state's own data, added to every EntityRenderState by RenderStateDataMixin. */
    public interface Holder { Map<RenderStateDataKey<?>, Object> mobcraft$data(); }

    @SuppressWarnings("unchecked")
    public static <T> T get(EntityRenderState state, RenderStateDataKey<T> key) {
        return (T) ((Holder) state).mobcraft$data().get(key);
    }

    public static <T> void set(EntityRenderState state, RenderStateDataKey<T> key, T value) {
        Map<RenderStateDataKey<?>, Object> data = ((Holder) state).mobcraft$data();
        if (value == null) data.remove(key); else data.put(key, value);
    }
}
