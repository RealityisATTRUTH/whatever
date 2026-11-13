package com.reality.mobcraft;

import com.reality.mobcraft.effect.ModEffects;
import com.reality.mobcraft.item.ArmorUpgradeRecipe;
import com.reality.mobcraft.item.ArmorUpgrades;
import com.reality.mobcraft.item.ModItems;
import com.reality.mobcraft.mob.MobAbilities;
import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobCommands;
import com.reality.mobcraft.mob.MobDrops;
import com.reality.mobcraft.mob.MobItems;
import com.reality.mobcraft.mob.MobKits;
import com.reality.mobcraft.mob.MobTicker;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.mob.kit.DeathOnce;
import com.reality.mobcraft.mob.kit.KitEvents;
import com.reality.mobcraft.mob.kit.RabbitKit;
import com.reality.mobcraft.mob.kit.ShulkerKit;
import com.reality.mobcraft.net.AbilityPayload;
import com.reality.mobcraft.net.ArmorPowerPayload;
import com.reality.mobcraft.net.AttackOrderPayload;
import com.reality.mobcraft.net.CityTeleportPayload;
import com.reality.mobcraft.net.DoubleJumpPayload;
import com.reality.mobcraft.net.GhostSyncPayload;
import com.reality.mobcraft.net.GiveItemPayload;
import com.reality.mobcraft.net.GlowPayload;
import com.reality.mobcraft.net.MoveOrderPayload;
import com.reality.mobcraft.net.OpenSettingsPayload;
import com.reality.mobcraft.net.PingPayload;
import com.reality.mobcraft.net.RtsCommandPayload;
import com.reality.mobcraft.net.RtsStatePayload;
import com.reality.mobcraft.net.SetGroupsPayload;
import com.reality.mobcraft.net.TeamPingPayload;
import com.reality.mobcraft.net.ToggleAbilityPayload;
import com.reality.mobcraft.net.ToggleBehaviorPayload;
import com.reality.mobcraft.rts.MobBehavior;
import com.reality.mobcraft.rts.RtsControl;
import com.reality.mobcraft.rts.RtsGroups;
import com.reality.mobcraft.rts.RtsStances;
import com.reality.mobcraft.rts.RtsHeld;
import com.reality.mobcraft.net.RtsSelectionPayload;
import com.reality.mobcraft.rts.RtsMobOrders;
import com.reality.mobcraft.rts.RtsOrders;
import com.reality.mobcraft.rts.RtsPings;
import com.reality.mobcraft.warden.Senses;
import com.reality.mobcraft.rts.RtsState;
import com.reality.mobcraft.warden.WardenEcholocation;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import com.reality.mobcraft.mob.Jeff;
import com.reality.mobcraft.mob.Ultimate;
import com.reality.mobcraft.mob.kit.BlazeKit;
import com.reality.mobcraft.mob.kit.VillagerKit;
import com.reality.mobcraft.net.JeffGivePayload;
import com.reality.mobcraft.net.OpenJeffMenuPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * MobCraft Season 3, common entrypoint.
 *
 * Runs on both the dedicated server and the client's integrated server.
 */
public class MobCraft implements ModInitializer {
    public static final String MOD_ID = "mobcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        MobAttachments.init();
        ModItems.init();
        com.reality.mobcraft.item.GhastWisp.register();
        com.reality.mobcraft.item.RabbitEars.register();
        ModEffects.init();
        ArmorUpgrades.register();
        com.reality.mobcraft.item.MobRelics.register();
        com.reality.mobcraft.item.ShulkerEssence.register();
        com.reality.mobcraft.item.CreeperEssence.register();
        com.reality.mobcraft.item.StrongholdTome.register();
        com.reality.mobcraft.item.TomeBinding.register();
        com.reality.mobcraft.item.Duskvein.register();
        com.reality.mobcraft.item.Gloamite.register();
        com.reality.mobcraft.item.MaceEnchants.register();
        com.reality.mobcraft.item.GloamiteTraits.register();
        com.reality.mobcraft.item.Tiers.register();
        com.reality.mobcraft.item.Shadow.register();
        com.reality.mobcraft.item.UnseenHand.register();
        com.reality.mobcraft.item.Hush.register();
        com.reality.mobcraft.item.GearEnchants.register();
        com.reality.mobcraft.item.Skybreaker.register();
        com.reality.mobcraft.item.TomePowers.register();
        com.reality.mobcraft.item.DuskveinAnvil.register();
        com.reality.mobcraft.item.EvokerEssence.register();
        ArmorUpgradeRecipe.register();
        MobKits.init();
        KitEvents.register();
        ClientCheck.register();
        AntiCheat.register();
        Ultimate.register();
        com.reality.mobcraft.mob.kit.SculkEruption.register();
        com.reality.mobcraft.mob.kit.UltimateFx.register();
        com.reality.mobcraft.mob.kit.SpellFx.register();
        com.reality.mobcraft.mob.kit.StarDeath.register();
        MobAdvancements.register();
        com.reality.mobcraft.rts.PetSystem.register();
        com.reality.mobcraft.mob.MobRoster.register();
        com.reality.mobcraft.shop.PiglinShop.register();

        // Payload types must be registered on both sides, so they live here.
        PayloadTypeRegistry.clientboundPlay().register(GlowPayload.TYPE, GlowPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GhostSyncPayload.TYPE, GhostSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(com.reality.mobcraft.net.EssenceOrbPayload.TYPE, com.reality.mobcraft.net.EssenceOrbPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenSettingsPayload.TYPE, OpenSettingsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenJeffMenuPayload.TYPE, OpenJeffMenuPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(JeffGivePayload.TYPE, JeffGivePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AbilityPayload.TYPE, AbilityPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ToggleAbilityPayload.TYPE, ToggleAbilityPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MoveOrderPayload.TYPE, MoveOrderPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(AttackOrderPayload.TYPE, AttackOrderPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RtsCommandPayload.TYPE, RtsCommandPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PingPayload.TYPE, PingPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TeamPingPayload.TYPE, TeamPingPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SetGroupsPayload.TYPE, SetGroupsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RtsStatePayload.TYPE, RtsStatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RtsSelectionPayload.TYPE, RtsSelectionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ToggleBehaviorPayload.TYPE, ToggleBehaviorPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CityTeleportPayload.TYPE, CityTeleportPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(DoubleJumpPayload.TYPE, DoubleJumpPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ArmorPowerPayload.TYPE, ArmorPowerPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(com.reality.mobcraft.net.EssencePayload.TYPE, com.reality.mobcraft.net.EssencePayload.CODEC);

        // Essence Power key: the Shulker Essence's shell.
        ServerPlayNetworking.registerGlobalReceiver(com.reality.mobcraft.net.EssencePayload.TYPE, (payload, context) ->
                com.reality.mobcraft.item.ShulkerEssence.usePower(context.player()));

        // Armor Power key: Ender Pearl Shard and Stalker Shard teleports.
        ServerPlayNetworking.registerGlobalReceiver(ArmorPowerPayload.TYPE, (payload, context) ->
                ArmorUpgrades.usePower(context.player(), payload.sneaking()));

        // Rabbit: jumping again in midair.
        ServerPlayNetworking.registerGlobalReceiver(DoubleJumpPayload.TYPE, (payload, context) ->
                RabbitKit.doubleJump(context.player()));

        // RTS: the Shulker's End City Teleport.
        ServerPlayNetworking.registerGlobalReceiver(CityTeleportPayload.TYPE, (payload, context) ->
                ShulkerKit.rtsTeleport(context.player(), payload.x(), payload.z(), payload.shulkerId()));

        // Mob Behavior page of /mobcraftsettings.
        // Also carries the Echolocation Markers switch from the main settings page.
        ServerPlayNetworking.registerGlobalReceiver(ToggleBehaviorPayload.TYPE, (payload, context) -> {
            if (Senses.SETTING.equals(payload.behavior())) {
                Senses.set(context.player(), payload.enabled());
            } else if (com.reality.mobcraft.mob.kit.SkeletonKit.HORSEMEN_SETTING.equals(payload.behavior())) {
                com.reality.mobcraft.mob.kit.SkeletonKit.setHorsemen(context.player(), payload.enabled());
            } else if (BlazeKit.ROD_SETTING.equals(payload.behavior())) {
                BlazeKit.setRodFiring(context.player(), payload.enabled());
            } else {
                MobBehavior.set(context.player(), payload.behavior(), payload.enabled());
            }
        });

        // RTS mode on or off. Taking a hit closes RTS on the player's game (RtsView).
        ServerPlayNetworking.registerGlobalReceiver(RtsStatePayload.TYPE, (payload, context) -> {
            RtsState.set(context.player(), payload.active());
            if (!payload.active()) RtsHeld.clear(context.player());
        });
        // RTS selection, so selected endermen only teleport where they're sent.
        ServerPlayNetworking.registerGlobalReceiver(RtsSelectionPayload.TYPE, (payload, context) ->
                RtsHeld.setSelection(context.player(), payload.entityIds()));

        // RTS armies don't crush themselves to death: mobs carrying out RTS orders
        // take no cramming damage when lots of them pile onto one target.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(source.is(net.minecraft.world.damagesource.DamageTypes.CRAMMING)
                        && entity instanceof net.minecraft.world.entity.Mob mob
                        && com.reality.mobcraft.rts.RtsMobOrders.isBusy(mob)));

        // Leaving the server ends RTS mode.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RtsState.set(handler.player, false);
            RtsHeld.clear(handler.player);
        });

        // RTS divisions and army groups, saved on the player.
        ServerPlayNetworking.registerGlobalReceiver(SetGroupsPayload.TYPE, (payload, context) -> {
            if (!RtsGroups.isReasonable(payload.lines())) return;
            if (payload.lines().isEmpty()) {
                context.player().removeAttached(MobAttachments.RTS_GROUPS);
            } else {
                context.player().setAttached(MobAttachments.RTS_GROUPS, List.copyOf(payload.lines()));
                MobAdvancements.award(context.player(), "first_army");
                if (payload.lines().stream().anyMatch(line -> line.endsWith("|aggressive"))) {
                    MobAdvancements.award(context.player(), "aggressive");
                }
            }
        });

        // RTS move orders. RtsOrders checks the player owns each mob.
        ServerPlayNetworking.registerGlobalReceiver(MoveOrderPayload.TYPE, (payload, context) ->
                RtsOrders.move(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(AttackOrderPayload.TYPE, (payload, context) ->
                RtsOrders.attack(context.player(), payload));

        ServerPlayNetworking.registerGlobalReceiver(RtsCommandPayload.TYPE, (payload, context) ->
                RtsOrders.command(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(PingPayload.TYPE, (payload, context) ->
                RtsPings.ping(context.player(), payload));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RtsPings.forget(handler.player));

        // RTS: whose mobs each player can command, from /rtsallow and /rtsking.
        ServerTickEvents.END_SERVER_TICK.register(RtsControl::tick);
        // RTS: mobs working through their move, attack-move and hold orders.
        ServerTickEvents.END_SERVER_TICK.register(RtsMobOrders::tick);
        // RTS: aggressive armies move faster and pick fights.
        ServerTickEvents.END_SERVER_TICK.register(RtsStances::tick);
        // RTS: commanded silverfish don't hide in stone.
        ServerTickEvents.END_SERVER_TICK.register(com.reality.mobcraft.rts.RtsNoInfest::tick);
        // After RtsMobOrders, so its own teleports this tick are known.
        ServerTickEvents.END_SERVER_TICK.register(RtsHeld::tick);
        PayloadTypeRegistry.serverboundPlay().register(GiveItemPayload.TYPE, GiveItemPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.TYPE, (payload, context) -> {
            int slot = payload.slot();
            if (slot == Ultimate.SLOT) {
                Ultimate.use(context.player());
                return;
            }
            if (slot < 0 || slot > 2) return; // ignore bad packets
            MobType type = PlayerMob.get(context.player());
            if (type == null) return;
            MobKits.get(type).onAbility(context.player(), slot, payload.sneaking());
        });

        // MobCraft Settings toggles. Only the player's own mob's abilities can be switched.
        ServerPlayNetworking.registerGlobalReceiver(ToggleAbilityPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            MobType type = PlayerMob.get(player);
            if (!MobAbilities.belongsTo(type, payload.abilityId())) return;
            MobAbilities.setEnabled(player, payload.abilityId(), payload.enabled());
        });

        // Mob Item Menu buttons. The server only gives items from the player's own mob list.
        ServerPlayNetworking.registerGlobalReceiver(GiveItemPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String message = MobItems.give(player, payload.itemId(), payload.count(), payload.drop());
            player.sendSystemMessage(Component.literal(message));
        });

        // Jeff's menu. VillagerKit checks he's still powered up before giving anything.
        ServerPlayNetworking.registerGlobalReceiver(JeffGivePayload.TYPE, (payload, context) ->
                VillagerKit.giveFromMenu(context.player(), payload.itemId(), payload.count()));

        // Real death drops: when a player with a mob dies, their mob's drops land where they died.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayer player)) return;
            MobType type = PlayerMob.get(player);
            if (type != null && DeathOnce.first(player, "drops")) MobDrops.dropAt(player, type);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            MobTicker.tick(server);
            ArmorUpgrades.tick(server);
            WardenEcholocation.tick(server);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            MobCommands.register(dispatcher);
            TestCommands.register(dispatcher);
            RtsControl.registerCommands(dispatcher);
            Jeff.registerCommand(dispatcher);
            AntiCheat.registerCommand(dispatcher);
            com.reality.mobcraft.rts.TeamList.register(dispatcher);
            com.reality.mobcraft.rts.PetSystem.registerCommands(dispatcher);
        });

        LOGGER.info("MobCraft Season 3 beta loaded.");
    }
}
