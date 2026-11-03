package dev.reality.fightbot;

import dev.reality.fightbot.flight.FlightSample;
import dev.reality.fightbot.flight.FlightSession;
import dev.reality.fightbot.flight.MechanicsConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The observation layer: reads watched players on the server every tick and turns them into flight
 * samples. Only values the server has go in. Fake players' velocity comes straight from the server.
 * Real players move on their own client, so their velocity is worked out from positions and marked so.
 */
final class FlightWatchers {
    private FlightWatchers() {
    }

    static final class Watch {
        final FlightSession session;
        double lastX = Double.NaN, lastY, lastZ;

        Watch(FlightSession s) {
            session = s;
        }
    }

    private static final Map<String, Watch> WATCHES = new LinkedHashMap<>();
    static Path root;
    private static MechanicsConfig config;

    static String gameVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }

    static MechanicsConfig config() {
        if (config == null) config = MechanicsConfig.load(root.resolve("mechanics"), gameVersion());
        return config;
    }

    /** Reloads the mechanics file, so edits apply to the next episode. */
    static void reloadConfig() {
        config = null;
    }

    static String start(String player, String label) {
        if (WATCHES.containsKey(player)) return player + " is already being recorded.";
        WATCHES.put(player, new Watch(new FlightSession(player, label, config())));
        MechanicsConfig c = config();
        return "Recording " + player + "'s movement for Minecraft " + c.gameVersion + "."
                + (c.verified ? "" : " The mechanics numbers for this version are not verified yet.");
    }

    static String stop(String player) {
        Watch w = WATCHES.remove(player);
        if (w == null) return "Not recording " + player + ".";
        w.session.end("stopped");
        return save(w.session);
    }

    static List<String> list() {
        List<String> out = new ArrayList<>();
        for (Watch w : WATCHES.values()) out.add(w.session.shortReport());
        return out;
    }

    static boolean watching(String player) {
        return WATCHES.containsKey(player);
    }

    /** The bot sent an action. Called before this tick's sample is taken. */
    static void commanded(String player, String action, long tick) {
        Watch w = WATCHES.get(player);
        if (w != null) w.session.commanded(action, tick);
    }

    static void tick(MinecraftServer server, Consumer<String> announce) {
        for (var it = WATCHES.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Watch w = e.getValue();
            ServerPlayer p = server.getPlayerList().getPlayerByName(e.getKey());
            long tick = server.getTickCount();
            FlightSample s = p == null ? FlightSample.missing(tick, System.currentTimeMillis()) : sample(p, w, tick);
            if (!w.session.add(s)) {
                it.remove();
                announce.accept(save(w.session));
            }
        }
    }

    /** Starts a session that a practice run drives itself. */
    static FlightSession practiceSession(String player, String label) {
        return new FlightSession(player, label, config());
    }

    static FlightSample sampleFor(ServerPlayer p, long tick, double[] last) {
        Watch tmp = new Watch(null);
        tmp.lastX = last[0];
        tmp.lastY = last[1];
        tmp.lastZ = last[2];
        FlightSample s = sample(p, tmp, tick);
        last[0] = p.getX();
        last[1] = p.getY();
        last[2] = p.getZ();
        return s;
    }

    static String save(FlightSession s) {
        try {
            Path dir = s.save(root.resolve("flight"));
            return s.shortReport() + " Saved to " + root.relativize(dir) + ".";
        } catch (Exception ex) {
            return "Could not save the flight episode: " + ex.getMessage();
        }
    }

    private static FlightSample sample(ServerPlayer p, Watch w, long tick) {
        double vx, vy, vz;
        String source;
        // Carpet's fake players move on the server, so the server's velocity is the real one.
        if (p.getClass().getName().contains("Fake")) {
            var v = p.getDeltaMovement();
            vx = v.x;
            vy = v.y;
            vz = v.z;
            source = "server";
        } else if (!Double.isNaN(w.lastX)) {
            vx = p.getX() - w.lastX;
            vy = p.getY() - w.lastY;
            vz = p.getZ() - w.lastZ;
            source = "position";
        } else {
            vx = vy = vz = Double.NaN;
            source = "none";
        }
        w.lastX = p.getX();
        w.lastY = p.getY();
        w.lastZ = p.getZ();

        var inv = p.getInventory();
        int fireworks = 0, wind = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            String id = BotController.id(s);
            if (id.equals("firework_rocket")) fireworks += s.getCount();
            if (id.equals("wind_charge")) wind += s.getCount();
        }
        String off = BotController.id(p.getOffhandItem());
        if (off.equals("firework_rocket")) fireworks += p.getOffhandItem().getCount();
        if (off.equals("wind_charge")) wind += p.getOffhandItem().getCount();
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        String chestId = BotController.id(chest);
        int durability = chestId.equals("elytra") && chest.isDamageableItem() ? chest.getMaxDamage() - chest.getDamageValue() : -1;

        return new FlightSample(tick, System.currentTimeMillis(), true, p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(),
                vx, vy, vz, source, p.onGround(), p.isFallFlying(), p.fallDistance, p.horizontalCollision, p.verticalCollision,
                inv.getSelectedSlot(), BotController.id(p.getMainHandItem()), chestId, fireworks, wind, durability,
                groundClearance(p, config().groundScan), solidNearby(p), p.getHealth());
    }

    /** Distance from the feet down to the top of the first block with a collision shape, or NaN if none in range. */
    private static double groundClearance(ServerPlayer p, int scan) {
        ServerLevel level = p.level();
        BlockPos start = BlockPos.containing(p.getX(), p.getY(), p.getZ());
        for (int i = 0; i <= scan; i++) {
            BlockPos pos = start.below(i);
            if (pos.getY() < level.getMinY()) return Double.NaN;
            BlockState st = level.getBlockState(pos);
            var shape = st.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double top = pos.getY() + shape.max(Direction.Axis.Y);
                if (top <= p.getY() + 1e-6) return p.getY() - top;
            }
        }
        return Double.NaN;
    }

    private static int solidNearby(ServerPlayer p) {
        ServerLevel level = p.level();
        BlockPos c = p.blockPosition();
        int n = 0;
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = 0; dy <= 2; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = c.offset(dx, dy, dz);
                    if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) n++;
                }
        return n;
    }
}
