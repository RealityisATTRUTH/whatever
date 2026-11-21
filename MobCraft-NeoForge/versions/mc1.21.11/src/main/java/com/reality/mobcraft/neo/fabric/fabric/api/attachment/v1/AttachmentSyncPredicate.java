package com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1;

public enum AttachmentSyncPredicate {
    ALL, TARGET_ONLY;
    public static AttachmentSyncPredicate all() { return ALL; }
    public static AttachmentSyncPredicate targetOnly() { return TARGET_ONLY; }
}
