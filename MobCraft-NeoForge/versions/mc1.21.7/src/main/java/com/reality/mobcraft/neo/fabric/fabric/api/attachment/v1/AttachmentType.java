package com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** A Fabric-style attachment, stored with NeoForge's data attachments. */
public final class AttachmentType<A> {
    final ResourceLocation id;
    Codec<A> codec;
    boolean copyOnDeath;
    StreamCodec<? super RegistryFriendlyByteBuf, A> syncCodec;
    AttachmentSyncPredicate syncPredicate;
    Supplier<A> initializer;
    net.neoforged.neoforge.attachment.AttachmentType<A> neo;

    AttachmentType(ResourceLocation id) { this.id = id; }
    public ResourceLocation identifier() { return id; }
    public boolean isPersistent() { return codec != null; }
    public net.neoforged.neoforge.attachment.AttachmentType<A> neo() {
        if (neo == null) throw new IllegalStateException("Attachment " + id + " used before NeoForge registered it");
        return neo;
    }
    public StreamCodec<? super RegistryFriendlyByteBuf, A> syncCodec() { return syncCodec; }
    public AttachmentSyncPredicate syncPredicate() { return syncPredicate; }
    public Supplier<A> initializer() { return initializer; }

    @SuppressWarnings("unchecked")
    public net.neoforged.neoforge.attachment.AttachmentType<A> build() {
        return build1216();
    }

    private net.neoforged.neoforge.attachment.AttachmentType<A> build1216() {
        var b = net.neoforged.neoforge.attachment.AttachmentType.<A>builder(() -> initializer != null ? initializer.get() : null);
        if (codec != null) {
            b.serialize(codec.fieldOf("value"));
        } else if (copyOnDeath) {
            // Not saved to disk, but NeoForge needs a serializer before it will copy a value on death.
            b.serialize(new net.neoforged.neoforge.attachment.IAttachmentSerializer<A>() {
                public A read(net.neoforged.neoforge.attachment.IAttachmentHolder holder, net.minecraft.world.level.storage.ValueInput input) { return null; }
                public boolean write(A value, net.minecraft.world.level.storage.ValueOutput output) { return false; }
            });
        }
        if (copyOnDeath) {
            if (codec != null) b.copyOnDeath();
            else { b.copyOnDeath(); b.copyHandler((value, holder, provider) -> value); }
        }
        neo = b.build();
        return neo;
    }
}
