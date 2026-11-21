package com.reality.mobcraft.neo;

/** Kept separate so a dedicated server never loads client classes. */
final class ClientBoot {
    static void start() {
        new com.reality.mobcraft.client.MobCraftClient().onInitializeClient();
    }
}
