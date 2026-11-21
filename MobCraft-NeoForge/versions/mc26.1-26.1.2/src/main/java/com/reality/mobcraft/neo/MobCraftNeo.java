package com.reality.mobcraft.neo;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1.AttachmentRegistry;
import com.reality.mobcraft.neo.fabric.fabric.api.attachment.v1.AttachmentType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/** NeoForge entry point. Runs MobCraft's Fabric-style setup on NeoForge's events. */
@Mod("mobcraft")
public final class MobCraftNeo {
    private static boolean started;
    private final Dist dist;

    public MobCraftNeo(IEventBus modBus, Dist dist) {
        this.dist = dist;
        modBus.addListener(EventPriority.HIGHEST, RegisterEvent.class, this::onRegister);
        modBus.addListener(NeoNetwork::register);
        NeoHooks.init();
        if (dist.isClient()) NeoClientHooks.init(modBus);
    }

    private void onRegister(RegisterEvent event) {
        if (!started) {
            started = true;
            new MobCraft().onInitialize();
            if (dist.isClient()) ClientBoot.start();
        }
        if (event.getRegistryKey().equals(NeoForgeRegistries.Keys.ATTACHMENT_TYPES)) {
            for (AttachmentType<?> type : AttachmentRegistry.ALL) {
                event.register(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, type.identifier(), () -> buildOf(type));
            }
        }
    }

    private static net.neoforged.neoforge.attachment.AttachmentType<?> buildOf(AttachmentType<?> type) {
        return AttachmentAccess.build(type);
    }
}
