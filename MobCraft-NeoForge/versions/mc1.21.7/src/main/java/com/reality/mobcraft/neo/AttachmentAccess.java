package com.reality.mobcraft.neo;

import com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1.AttachmentType;

final class AttachmentAccess {
    static net.neoforged.neoforge.attachment.AttachmentType<?> build(AttachmentType<?> type) { return type.build(); }
}
