package com.reality.mobcraft.neo;

import com.reality.mobcraft.neo.fabric.fabric.api.command.v2.CommandRegistrationCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.event.player.AttackBlockCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.event.player.AttackEntityCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.event.player.PlayerBlockBreakEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.event.player.UseBlockCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.event.player.UseEntityCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.event.player.UseItemCallback;
import com.reality.mobcraft.neo.fabric.fabric.api.loot.v3.LootTableEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.loot.v3.LootTableSource;
import com.reality.mobcraft.neo.fabric.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.reality.mobcraft.neo.fabric.fabric.api.networking.v1.ServerPlayNetworking;
import com.reality.mobcraft.neo.fabric.fabric.api.registry.FabricPotionBrewingBuilder;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Turns NeoForge's game events into the Fabric-style events MobCraft listens to. */
final class NeoHooks {
    static void init() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Pre e) -> ServerTickEvents.START_SERVER_TICK.invoker().onStartTick(e.getServer()));
        bus.addListener((ServerTickEvent.Post e) -> ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(e.getServer()));
        bus.addListener((LevelTickEvent.Pre e) -> { if (e.getLevel() instanceof ServerLevel l) ServerTickEvents.START_WORLD_TICK.invoker().onStartTick(l); });
        bus.addListener((LevelTickEvent.Post e) -> { if (e.getLevel() instanceof ServerLevel l) ServerTickEvents.END_WORLD_TICK.invoker().onEndTick(l); });

        bus.addListener((ServerStartingEvent e) -> ServerLifecycleEvents.SERVER_STARTING.invoker().onServerStarting(e.getServer()));
        bus.addListener((ServerStartedEvent e) -> ServerLifecycleEvents.SERVER_STARTED.invoker().onServerStarted(e.getServer()));
        bus.addListener((ServerStoppingEvent e) -> ServerLifecycleEvents.SERVER_STOPPING.invoker().onServerStopping(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> ServerLifecycleEvents.SERVER_STOPPED.invoker().onServerStopped(e.getServer()));

        bus.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent e) -> {
            if (!e.isCanceled() && e.getLevel() instanceof ServerLevel l) ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(e.getEntity(), l);
        });
        bus.addListener((EntityLeaveLevelEvent e) -> {
            if (e.getLevel() instanceof ServerLevel l) ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(e.getEntity(), l);
        });

        bus.addListener((LivingIncomingDamageEvent e) -> {
            if (e.getEntity().level().isClientSide()) return;
            if (!ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(e.getEntity(), e.getSource(), e.getAmount())) e.setCanceled(true);
        });
        bus.addListener((LivingDamageEvent.Post e) -> {
            if (e.getEntity().level().isClientSide()) return;
            ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(e.getEntity(), e.getSource(), e.getOriginalDamage(), DamageCompat.dealt(e), e.getBlockedDamage() > 0);
        });
        bus.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> {
            if (e.isCanceled() || e.getEntity().level().isClientSide()) return;
            if (!ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(e.getEntity(), e.getSource(), 0f)) {
                e.setCanceled(true);
                return;
            }
            ServerLivingEntityEvents.AFTER_DEATH.invoker().afterDeath(e.getEntity(), e.getSource());
        });
        bus.addListener((LivingConversionEvent.Post e) -> {
            if (e.getEntity() instanceof Mob from && e.getOutcome() instanceof Mob to)
                ServerLivingEntityEvents.MOB_CONVERSION.invoker().onConversion(from, to, null);
        });

        bus.addListener((PlayerInteractEvent.RightClickItem e) -> cancelUnlessPass(e, UseItemCallback.EVENT.invoker().interact(e.getEntity(), e.getLevel(), e.getHand())));
        bus.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            InteractionResult r = UseBlockCallback.EVENT.invoker().interact(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec());
            if (r != InteractionResult.PASS) { e.setCanceled(true); e.setCancellationResult(r); }
        });
        bus.addListener((PlayerInteractEvent.EntityInteract e) -> cancelUnlessPass(e, UseEntityCallback.EVENT.invoker().interact(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget(), null)));
        bus.addListener((AttackEntityEvent e) -> {
            InteractionResult r = AttackEntityCallback.EVENT.invoker().interact(e.getEntity(), e.getEntity().level(), InteractionHand.MAIN_HAND, e.getTarget(), null);
            if (r != InteractionResult.PASS) e.setCanceled(true);
        });
        bus.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
            InteractionResult r = AttackBlockCallback.EVENT.invoker().interact(e.getEntity(), e.getLevel(), e.getHand(), e.getPos(), e.getFace());
            if (r != InteractionResult.PASS) e.setCanceled(true);
        });
        BreakHooks.register(bus);

        bus.addListener((LootTableLoadEvent e) -> LootTableEvents.MODIFY.invoker().modifyLootTable(
                e.getKey(), new LootTableEvents.TableBuilder(e.getTable()), LootTableSource.VANILLA, e.getRegistries()));
        bus.addListener((RegisterCommandsEvent e) -> CommandRegistrationCallback.EVENT.invoker().register(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection()));
        bus.addListener((RegisterBrewingRecipesEvent e) -> FabricPotionBrewingBuilder.BUILD.invoker().build(e.getBuilder()));

        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p)) return;
            AttachmentSync.sendAll(p, p);
            ServerPlayConnectionEvents.JOIN.invoker().onPlayReady(p.connection, payload -> ServerPlayNetworking.send(p, payload), p.level().getServer());
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.connection, p.level().getServer());
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> { if (e.getEntity() instanceof ServerPlayer p) AttachmentSync.sendAll(p, p); });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> { if (e.getEntity() instanceof ServerPlayer p) AttachmentSync.sendAll(p, p); });
        bus.addListener((PlayerEvent.StartTracking e) -> { if (e.getEntity() instanceof ServerPlayer p) AttachmentSync.sendAll(p, e.getTarget()); });
    }

    private static void cancelUnlessPass(PlayerInteractEvent e, InteractionResult r) {
        if (r == InteractionResult.PASS || !(e instanceof net.neoforged.bus.api.ICancellableEvent c)) return;
        c.setCanceled(true);
        if (e instanceof PlayerInteractEvent.RightClickItem i) i.setCancellationResult(r);
        if (e instanceof PlayerInteractEvent.EntityInteract i) i.setCancellationResult(r);
    }
}
