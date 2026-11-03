package dev.reality.fightbot.core.world;

import java.util.List;

/**
 * What the bot can read about the blocks and entities around it. The server reads the real world. The
 * simulator reads a small grid. Everything here is a fact the server has, never a guess.
 */
public interface WorldView {
    enum Kind {
        /** Nothing there, a crystal or block can go here. */
        AIR,
        /** Grass, snow layers, and other blocks a placement replaces. */
        REPLACEABLE,
        /** Any other full block. */
        SOLID,
        OBSIDIAN,
        BEDROCK,
        ANCHOR,
        GLOWSTONE,
        /** A wooden door or fence gate that is shut. A hand click opens it. */
        DOOR_CLOSED,
        /** A door or fence gate standing open, which a player walks through. */
        DOOR_OPEN
    }

    /** One end crystal entity. */
    record Crystal(int id, double x, double y, double z) {
    }

    /** Armor points, armor toughness, and protection points that count against explosions. */
    record Armor(double points, double toughness, int explosionProtection) {
    }

    Kind kind(int x, int y, int z);

    /** Charges in a respawn anchor, or -1 when the block is not one. */
    int anchorCharge(int x, int y, int z);

    /** True when any entity's box overlaps this box. */
    boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1);

    List<Crystal> crystals();

    /** "overworld", "the_nether", "the_end", or another dimension's name. */
    String dimension();

    /** "peaceful", "easy", "normal", or "hard". */
    String difficulty();

    Armor armor(boolean self);

    default boolean solid(int x, int y, int z) {
        Kind k = kind(x, y, z);
        return k != Kind.AIR && k != Kind.REPLACEABLE && k != Kind.DOOR_OPEN;
    }

    /** Lava at this block, source or flowing. */
    default boolean lava(int x, int y, int z) {
        return false;
    }

    /** Fire, magma, cactus, berry bushes, and other blocks that hurt to stand in or on. */
    default boolean harmful(int x, int y, int z) {
        return false;
    }

    /** A cobweb at this block. */
    default boolean web(int x, int y, int z) {
        return false;
    }

    /** Water at this block, source or flowing. */
    default boolean water(int x, int y, int z) {
        return false;
    }

    /** Lowest block height in this dimension. Below it is the void. */
    default int minY() {
        return dimension().equals("overworld") ? -64 : 0;
    }

    /** How hard the block is to mine, as the game counts it. -1 means it cannot be mined. */
    default float hardness(int x, int y, int z) {
        Kind k = kind(x, y, z);
        return switch (k) {
            case AIR, REPLACEABLE, DOOR_OPEN -> 0;
            case BEDROCK -> -1;
            case OBSIDIAN -> 50;
            case DOOR_CLOSED -> 3;
            default -> 1.5f;
        };
    }

    /** The tool that mines it fast: "pickaxe", "axe", "shovel", "hoe", or "any". */
    default String toolFor(int x, int y, int z) {
        return kind(x, y, z) == Kind.DOOR_CLOSED ? "axe" : "pickaxe";
    }

    /** How well it stands up to explosions. Crystals break blocks well under 9. */
    default float blastResistance(int x, int y, int z) {
        Kind k = kind(x, y, z);
        return k == Kind.OBSIDIAN || k == Kind.BEDROCK ? 1200 : k == Kind.DOOR_CLOSED ? 3 : 6;
    }

    default boolean open(int x, int y, int z) {
        return !solid(x, y, z);
    }

    /** True when no solid block is in the way between two points. Steps a fifth of a block at a time. */
    default boolean clearLine(double x0, double y0, double z0, double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(len / 0.2));
        int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int bx = (int) Math.floor(x0 + dx * t), by = (int) Math.floor(y0 + dy * t), bz = (int) Math.floor(z0 + dz * t);
            if (bx == lastX && by == lastY && bz == lastZ) continue;
            lastX = bx;
            lastY = by;
            lastZ = bz;
            if (solid(bx, by, bz)) return false;
        }
        return true;
    }

    /** This view with one extra block, for scoring a spot as if a block were already placed there. */
    default WorldView with(int bx, int by, int bz, Kind k) {
        WorldView base = this;
        return new WorldView() {
            public Kind kind(int x, int y, int z) {
                return x == bx && y == by && z == bz ? k : base.kind(x, y, z);
            }

            public int anchorCharge(int x, int y, int z) {
                return x == bx && y == by && z == bz ? (k == Kind.ANCHOR ? 1 : -1) : base.anchorCharge(x, y, z);
            }

            public boolean entityIn(double x0, double y0, double z0, double x1, double y1, double z1) {
                return base.entityIn(x0, y0, z0, x1, y1, z1);
            }

            public java.util.List<Crystal> crystals() {
                return base.crystals();
            }

            public String dimension() {
                return base.dimension();
            }

            public String difficulty() {
                return base.difficulty();
            }

            public Armor armor(boolean self) {
                return base.armor(self);
            }

            public int minY() {
                return base.minY();
            }

            public boolean water(int x, int y, int z) {
                return base.water(x, y, z);
            }

            public boolean lava(int x, int y, int z) {
                return base.lava(x, y, z);
            }

            public boolean web(int x, int y, int z) {
                return base.web(x, y, z);
            }

            public float hardness(int x, int y, int z) {
                return x == bx && y == by && z == bz ? WorldView.super.hardness(x, y, z) : base.hardness(x, y, z);
            }

            public String toolFor(int x, int y, int z) {
                return x == bx && y == by && z == bz ? WorldView.super.toolFor(x, y, z) : base.toolFor(x, y, z);
            }

            public float blastResistance(int x, int y, int z) {
                return x == bx && y == by && z == bz ? WorldView.super.blastResistance(x, y, z) : base.blastResistance(x, y, z);
            }
        };
    }

    /** Respawn anchors set a spawn point in the Nether and blow up everywhere else. */
    default boolean anchorsExplode() {
        return !dimension().equals("the_nether");
    }
}
