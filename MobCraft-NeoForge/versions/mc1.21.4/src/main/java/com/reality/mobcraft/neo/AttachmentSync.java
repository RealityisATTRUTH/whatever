package com.reality.mobcraft.neo;

import io.netty.buffer.Unpooled;
import java.util.Optional;
import com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1.AttachmentRegistry;
import com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1.AttachmentSyncPredicate;
import com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends synced attachments to players, like Fabric's attachment sync. */
public final class AttachmentSync {
    private AttachmentSync() {}

    public record Payload(int entity, ResourceLocation typeId, Optional<byte[]> value) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mobcraft", "attachment_sync"));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Payload> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Payload::entity,
                ResourceLocation.STREAM_CODEC, Payload::typeId,
                ByteBufCodecs.optional(ByteBufCodecs.BYTE_ARRAY), Payload::value,
                Payload::new);
        @Override public Type<Payload> type() { return TYPE; }
    }

    static AttachmentType<?> byId(ResourceLocation id) {
        for (AttachmentType<?> t : AttachmentRegistry.ALL) if (t.identifier().equals(id)) return t;
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Payload payload(Entity entity, AttachmentType<?> type, Object value) {
        if (value == null) return new Payload(entity.getId(), type.identifier(), Optional.empty());
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), entity.registryAccess());
        ((StreamCodec) type.syncCodec()).encode(buf, value);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return new Payload(entity.getId(), type.identifier(), Optional.of(bytes));
    }

    private static void send(ServerPlayer to, Payload p) {
        if (to.connection != null && to.connection.hasChannel(Payload.TYPE)) PacketDistributor.sendToPlayer(to, p);
    }

    /** Called whenever an attachment is set or removed. */
    public static void changed(Entity entity, AttachmentType<?> type, Object value) {
        if (type.syncCodec() == null || entity.level().isClientSide()) return;
        Payload p = payload(entity, type, value);
        if (entity instanceof ServerPlayer self) send(self, p);
        if (type.syncPredicate() == AttachmentSyncPredicate.ALL) {
            for (ServerPlayer watcher : com.reality.mobcraft.neo.fabric.fabric.api.networking.v1.PlayerLookup.tracking(entity)) send(watcher, p);
        }
    }

    /** Sends everything about target that to may see. */
    public static void sendAll(ServerPlayer to, Entity target) {
        boolean self = to == target;
        for (AttachmentType<?> type : AttachmentRegistry.ALL) {
            if (type.syncCodec() == null) continue;
            if (!self && type.syncPredicate() != AttachmentSyncPredicate.ALL) continue;
            Object v = ((net.neoforged.neoforge.attachment.IAttachmentHolder) target).getExistingData(type.neo()).orElse(null);
            if (v != null) send(to, payload(target, type, v));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void apply(Entity entity, Payload p) {
        AttachmentType type = byId(p.typeId());
        if (type == null) return;
        var holder = (net.neoforged.neoforge.attachment.IAttachmentHolder) entity;
        if (p.value().isEmpty()) {
            holder.removeData(type.neo());
            return;
        }
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(p.value().get()), entity.registryAccess());
        holder.setData(type.neo(), ((StreamCodec) type.syncCodec()).decode(buf));
    }
}
