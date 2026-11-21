package com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public final class AttachmentRegistry {
    public static final List<AttachmentType<?>> ALL = new ArrayList<>();

    public static <A> AttachmentType<A> create(Identifier id) {
        return create(id, b -> b);
    }
    public static <A> AttachmentType<A> create(Identifier id, UnaryOperator<Builder<A>> setup) {
        Builder<A> b = new Builder<>(new AttachmentType<>(id));
        setup.apply(b);
        ALL.add(b.type);
        return b.type;
    }
    public static <A> AttachmentType<A> createPersistent(Identifier id, Codec<A> codec) {
        return create(id, b -> b.persistent(codec));
    }

    public static final class Builder<A> {
        final AttachmentType<A> type;
        Builder(AttachmentType<A> type) { this.type = type; }
        public Builder<A> persistent(Codec<A> codec) { type.codec = codec; return this; }
        public Builder<A> copyOnDeath() { type.copyOnDeath = true; return this; }
        public Builder<A> initializer(Supplier<A> init) { type.initializer = init; return this; }
        public Builder<A> syncWith(StreamCodec<? super RegistryFriendlyByteBuf, A> codec, AttachmentSyncPredicate predicate) {
            type.syncCodec = codec; type.syncPredicate = predicate; return this;
        }
    }
}
