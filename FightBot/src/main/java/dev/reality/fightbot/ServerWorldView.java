package dev.reality.fightbot;

import dev.reality.fightbot.core.world.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/** The real world around the bot, read straight from the server each time it is asked. */
final class ServerWorldView implements WorldView {
    private final ServerLevel level;
    private final ServerPlayer bot, target;

    ServerWorldView(ServerPlayer bot, ServerPlayer target) {
        this.level = bot.level();
        this.bot = bot;
        this.target = target;
    }

    @Override
    public Kind kind(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState st = level.getBlockState(pos);
        if (st.isAir()) return Kind.AIR;
        if (st.is(Blocks.OBSIDIAN)) return Kind.OBSIDIAN;
        if (st.is(Blocks.BEDROCK)) return Kind.BEDROCK;
        if (st.is(Blocks.RESPAWN_ANCHOR)) return Kind.ANCHOR;
        if (st.is(Blocks.GLOWSTONE)) return Kind.GLOWSTONE;
        // Doors and gates a hand can open. Iron doors and gates need redstone, so they count as walls.
        if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door && door.type().canOpenByHand()) {
            return st.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) ? Kind.DOOR_OPEN : Kind.DOOR_CLOSED;
        }
        if (st.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
            return st.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN) ? Kind.DOOR_OPEN : Kind.DOOR_CLOSED;
        }
        // Grass, flowers, and the like: not solid, but not empty either, so no crystal goes there.
        if (st.canBeReplaced() || st.getCollisionShape(level, pos).isEmpty()) return Kind.REPLACEABLE;
        return Kind.SOLID;
    }

    @Override
    public boolean lava(int x, int y, int z) {
        return level.getFluidState(new BlockPos(x, y, z)).is(net.minecraft.tags.FluidTags.LAVA);
    }

    @Override
    public boolean harmful(int x, int y, int z) {
        BlockState st = level.getBlockState(new BlockPos(x, y, z));
        return st.is(net.minecraft.tags.BlockTags.FIRE) || st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.CACTUS)
                || st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.WITHER_ROSE) || st.is(Blocks.POWDER_SNOW)
                || st.is(Blocks.CAMPFIRE) || st.is(Blocks.SOUL_CAMPFIRE);
    }

    @Override
    public boolean web(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).is(Blocks.COBWEB);
    }

    @Override
    public boolean water(int x, int y, int z) {
        return level.getFluidState(new BlockPos(x, y, z)).is(net.minecraft.tags.FluidTags.WATER);
    }

    @Override
    public int minY() {
        return level.getMinY();
    }

    @Override
    public float hardness(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        return level.getBlockState(pos).getDestroySpeed(level, pos);
    }

    @Override
    public String toolFor(int x, int y, int z) {
        BlockState st = level.getBlockState(new BlockPos(x, y, z));
        if (st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)) return "pickaxe";
        if (st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)) return "axe";
        if (st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) return "shovel";
        if (st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE)) return "hoe";
        return "any";
    }

    @Override
    public float blastResistance(int x, int y, int z) {
        return level.getBlockState(new BlockPos(x, y, z)).getBlock().getExplosionResistance();
    }

    @Override
    public int anchorCharge(int x, int y, int z) {
        BlockState st = level.getBlockState(new BlockPos(x, y, z));
        return st.is(Blocks.RESPAWN_ANCHOR) ? st.getValue(RespawnAnchorBlock.CHARGE) : -1;
    }

    @Override
    public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
        return !level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(x0, y0, z0, x1, y1, z1), e -> !e.isSpectator()).isEmpty();
    }

    @Override
    public List<Crystal> crystals() {
        List<Crystal> out = new ArrayList<>();
        for (EndCrystal c : level.getEntitiesOfClass(EndCrystal.class, bot.getBoundingBox().inflate(12))) {
            out.add(new Crystal(c.getId(), c.getX(), c.getY(), c.getZ()));
        }
        return out;
    }

    @Override
    public String dimension() {
        if (level.dimension() == Level.NETHER) return "the_nether";
        if (level.dimension() == Level.END) return "the_end";
        if (level.dimension() == Level.OVERWORLD) return "overworld";
        return "other";
    }

    @Override
    public String difficulty() {
        return level.getDifficulty().name().toLowerCase();
    }

    @Override
    public Armor armor(boolean self) {
        ServerPlayer p = self ? bot : target;
        int epf = 0;
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = p.getItemBySlot(slot);
            for (var e : s.getEnchantments().entrySet()) {
                String name = e.getKey().getRegisteredName();
                if (name.endsWith(":protection")) epf += e.getIntValue();
                if (name.endsWith(":blast_protection")) epf += 2 * e.getIntValue();
            }
        }
        return new Armor(p.getArmorValue(), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS), epf);
    }
}
