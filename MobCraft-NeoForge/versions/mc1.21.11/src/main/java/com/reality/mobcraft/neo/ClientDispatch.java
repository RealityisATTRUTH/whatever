package com.reality.mobcraft.neo;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Only ever called on the client; the client classes load when it is. */
final class ClientDispatch {
    static void dispatch(CustomPacketPayload p) {
        com.reality.mobcraft.neo.fabric.fabric.api.client.networking.v1.ClientPlayNetworking.dispatch(p);
    }
    static void attachment(AttachmentSync.Payload p) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        var e = level.getEntity(p.entity());
        if (e != null) AttachmentSync.apply(e, p);
    }
}
