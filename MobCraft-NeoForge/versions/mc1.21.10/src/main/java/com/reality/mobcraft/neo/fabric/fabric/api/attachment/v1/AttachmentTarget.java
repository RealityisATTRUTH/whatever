package com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1;

import com.reality.mobcraft.neo.AttachmentSync;
import java.util.function.UnaryOperator;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/** Fabric's attachment methods, added to every entity. */
public interface AttachmentTarget {
    private IAttachmentHolder mobcraft$holder() { return (IAttachmentHolder) this; }

    default <A> A getAttached(AttachmentType<A> type) {
        return mobcraft$holder().getExistingData(type.neo()).orElse(null);
    }
    default <A> A getAttachedOrElse(AttachmentType<A> type, A fallback) {
        A v = getAttached(type);
        return v != null ? v : fallback;
    }
    default <A> A getAttachedOrThrow(AttachmentType<A> type) {
        A v = getAttached(type);
        if (v == null) throw new NullPointerException("No " + type.identifier());
        return v;
    }
    default <A> A getAttachedOrCreate(AttachmentType<A> type) {
        A v = getAttached(type);
        if (v == null && type.initializer() != null) { v = type.initializer().get(); setAttached(type, v); }
        return v;
    }
    default <A> A getAttachedOrSet(AttachmentType<A> type, A value) {
        A v = getAttached(type);
        if (v == null) { setAttached(type, value); v = value; }
        return v;
    }
    default boolean hasAttached(AttachmentType<?> type) {
        return mobcraft$holder().hasData(type.neo());
    }
    default <A> A setAttached(AttachmentType<A> type, A value) {
        A old = value == null ? mobcraft$holder().removeData(type.neo()) : mobcraft$holder().setData(type.neo(), value);
        if (this instanceof Entity e) AttachmentSync.changed(e, type, value);
        return old;
    }
    default <A> A removeAttached(AttachmentType<A> type) {
        return setAttached(type, null);
    }
    default <A> A modifyAttached(AttachmentType<A> type, UnaryOperator<A> modifier) {
        return setAttached(type, modifier.apply(getAttached(type)));
    }
}
