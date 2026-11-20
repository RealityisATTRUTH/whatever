package com.reality.mobcraft.client;

import com.reality.mobcraft.MobCraft;
import com.reality.mobcraft.mob.Jeff;
import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.Ultimate;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The energy bar: "<Mob> Energy: 72 / 100", a bar under it, and the curse
 * timer under that when cursed. Players can drag it anywhere with the
 * Move Energy Bar key (H by default).
 */
public final class EnergyHud {

    /** Size of the whole element, used for dragging and keeping it on screen. */
    public static final int WIDTH = 140;
    public static final int HEIGHT = 38;

    private static final int MARGIN = 4;
    private static final int BAR_Y = 11;
    private static final int BAR_HEIGHT = 9;

    private static final int TEXT = 0xFFFFFFFF;
    private static final int CURSE_TEXT = 0xFFC060FF;
    private static final int BAR_BACK = 0xC0000000;
    private static final int BAR_BORDER = 0xFFFFFFFF;
    private static final int BAR_DEFAULT = 0xFF40C0FF;
    private static final int BAR_CURSED = 0xFF8040C0;
    private static final int MONEY = 0xFF17D46B;

    private EnergyHud() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, MobCraft.id("energy"), EnergyHud::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (HudEditorScreen.isOpen()) return; // the editor draws its own copy
        MobType type = PlayerMob.get(client.player);
        if (type == null) return;

        int x = screenX(graphics.guiWidth());
        int y = screenY(graphics.guiHeight());
        if (Jeff.isPowered(client.player)) {
            // Jeff's money is his energy.
            long money = Jeff.money(client.player);
            drawBar(graphics, client.font, x, y, "Money: " + Jeff.formatMoney(money), MONEY,
                    (float) ((double) money / Jeff.MAX_MONEY), PlayerMob.curseTicks(client.player));
            return;
        }
        draw(graphics, client.font, x, y, type.displayName, colorOf(type),
                PlayerMob.energy(client.player), type.maxEnergy, PlayerMob.curseTicks(client.player));
        Float ultimate = client.player.getAttached(MobAttachments.ULTIMATE);
        if (ultimate != null) drawUltimate(graphics, client.font, x, y, ultimate);
    }

    /**
     * A thin gold bar right under the energy bar: the ultimate's charge. When
     * it's full it pulses and says it's ready. Only drawn for mobs
     * that have an ultimate.
     */
    private static void drawUltimate(GuiGraphicsExtractor graphics, Font font, int x, int y, float charge) {
        int top = y + BAR_Y + BAR_HEIGHT + 1;
        int height = 4;
        graphics.fill(x, top, x + WIDTH, top + height, BAR_BACK);
        int filled = Math.round(WIDTH * Math.max(0.0f, Math.min(1.0f, charge / Ultimate.MAX)));
        boolean ready = charge >= Ultimate.MAX;
        int color = 0xFFFFC020;
        if (ready && (System.currentTimeMillis() / 250) % 2 == 0) color = 0xFFFFF0A0;
        graphics.fill(x, top, x + filled, top + height, color);
        if (ready) {
            graphics.text(font, "ULTIMATE READY", x + WIDTH + 4, top - 3, 0xFFFFD040, true);
        }
    }

    /** Each mob's bar color, taken from how the mob looks. A curse turns any bar dark purple. */
    public static int colorOf(MobType type) {
        if (type == null) return BAR_DEFAULT;
        return switch (type) {
            case RABBIT -> 0xFFE3CFA8;         // tan fur
            case IRON_GOLEM -> 0xFFC4BCB0;     // iron
            case SKELETON -> 0xFFDAD5BC;       // bone
            case AXOLOTL -> 0xFFF59BC8;        // pink
            case PIGLIN -> 0xFFFFD040;         // gold
            case GHAST -> 0xFFF4F4F4;          // white
            case BAT -> 0xFF8A6440;            // brown
            case CREEPER -> 0xFF5DC544;        // green
            case SILVERFISH -> 0xFF9AA4AE;     // stone grey
            case EVOKER -> 0xFF17D46B;         // emerald
            case ENDERMAN -> 0xFFCC33FF;       // ender eyes
            case VEX -> 0xFF9EC9E8;            // pale blue wings
            case SHULKER -> 0xFFB07CB0;        // shell lavender
            case WITHER -> 0xFF7A7A7A;         // dark grey
            case ILLUSIONER -> 0xFF3F6FD0;     // blue robe
            case WARDEN -> 0xFF19C0C0;         // sculk cyan
            case VILLAGER -> 0xFFC08A5A;       // robe brown
            case ENDER_DRAGON -> 0xFFE050C0;   // dragon's breath
            case BLAZE -> 0xFFFF8020;          // flame orange
            case ELDER_GUARDIAN -> 0xFF63B5A0; // prismarine
            case BREEZE -> 0xFFB8C8FF;         // wind
        };
    }

    /** Pixel position from the saved fraction, kept fully on screen. */
    public static int screenX(int guiWidth) {
        return MARGIN + Math.round(HudConfig.energyBarX() * Math.max(0, guiWidth - WIDTH - MARGIN * 2));
    }

    public static int screenY(int guiHeight) {
        return MARGIN + Math.round(HudConfig.energyBarY() * Math.max(0, guiHeight - HEIGHT - MARGIN * 2));
    }

    /** Draws the element with its top-left corner at x, y. Shared with the editor. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y,
                            String mobName, int barColor, float energy, int max, int curseTicks) {
        drawBar(graphics, font, x, y, mobName + " Energy: " + (int) energy + " / " + max, barColor,
                max <= 0 ? 0 : energy / max, curseTicks);
    }

    /** The label, the bar filled to the fraction, and the curse timer. */
    private static void drawBar(GuiGraphicsExtractor graphics, Font font, int x, int y,
                                String label, int barColor, float fraction, int curseTicks) {
        boolean cursed = curseTicks > 0;

        graphics.text(font, label, x, y, TEXT, true);

        int barTop = y + BAR_Y;
        graphics.fill(x, barTop, x + WIDTH, barTop + BAR_HEIGHT, BAR_BACK);
        graphics.outline(x, barTop, WIDTH, BAR_HEIGHT, BAR_BORDER);
        int inner = WIDTH - 2;
        int filled = Math.round(inner * Math.max(0.0f, Math.min(1.0f, fraction)));
        graphics.fill(x + 1, barTop + 1, x + 1 + filled, barTop + BAR_HEIGHT - 1, cursed ? BAR_CURSED : barColor);

        if (cursed) {
            int seconds = (curseTicks + 19) / 20;
            graphics.text(font, String.format("Cursed %d:%02d", seconds / 60, seconds % 60),
                    x, barTop + BAR_HEIGHT + 7, CURSE_TEXT, true);
        }
    }
}
