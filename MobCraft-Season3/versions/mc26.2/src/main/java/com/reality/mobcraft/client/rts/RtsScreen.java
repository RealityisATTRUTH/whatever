package com.reality.mobcraft.client.rts;

import com.mojang.blaze3d.platform.InputConstants;
import com.reality.mobcraft.client.AbilityKeys;
import com.reality.mobcraft.client.ClientGlow;
import com.reality.mobcraft.client.HudConfig;
import com.reality.mobcraft.client.HudConfig.RtsPart;
import com.reality.mobcraft.client.WorldToScreen;
import com.reality.mobcraft.mob.MobAttachments;
import com.reality.mobcraft.mob.MobType;
import com.reality.mobcraft.mob.PlayerMob;
import com.reality.mobcraft.net.AttackOrderPayload;
import com.reality.mobcraft.net.CityTeleportPayload;
import com.reality.mobcraft.net.MoveOrderPayload;
import com.reality.mobcraft.net.PingPayload;
import com.reality.mobcraft.net.RtsCommandPayload;
import com.reality.mobcraft.net.RtsSelectionPayload;
import com.reality.mobcraft.net.RtsStatePayload;
import com.reality.mobcraft.net.SetGroupsPayload;
import com.reality.mobcraft.rts.MobOwnership;
import com.reality.mobcraft.rts.RtsControl;
import com.reality.mobcraft.rts.RtsGroups;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RTS mode, opened with J.
 *
 * The camera moves to a client-only stand high above you, looking straight
 * down. Your body stays where it is and shows below.
 *
 * Camera
 *   WASD or mouse at an edge   pan
 *   scroll                     zoom
 *   Q and E                    turn a quarter turn
 *   minimap                    click it to look there
 *
 * Selecting
 *   click a mob                select it (shift adds or removes)
 *   double-click a mob         select every one of your mobs of that kind on screen
 *   drag a box                 select your mobs inside (shift adds)
 *   click empty ground         clear the selection
 *   Ctrl + A                   select every mob you command nearby
 *
 * Orders (micromanaging: give any selection its own orders)
 *   right-click ground         move there, spread out in a formation
 *   right-click an enemy       attack it
 *   Ctrl + right-click         attack-move: walk there, fighting on the way
 *   Shift + right-click        add to the route instead of replacing it
 *   M then click               move order, for when right-click is awkward
 *   R then click               attack-move order
 *   X / Z                      stop / hold
 *   middle-click               ping a spot for your team
 *
 * Armies, made by you like in HOI4
 *   G                          make the selected mobs a new army
 *   bottom bar                 click an army to select it (shift adds), twice to jump to it
 *   1-9                        select that army, twice to jump to it
 *   right-click an army        the selected mobs join it, like new escorts joining Army 1
 *   Ctrl + 1-9                 the same, for the army in that place on the bar
 *   Delete                     disband the selected armies (the mobs stay yours)
 *
 * Settings button: switch the minimap, army bar, buttons, health bars, order
 * arrows, teammate markers and pings on or off, and a selection debug view.
 * Saved in config/mobcraft-client.json.
 *
 * Every position on screen comes from the real camera (WorldToScreen), and the
 * ground under the mouse from a ray into the world, so clicks land on exactly
 * what you see at any height or zoom.
 */
public class RtsScreen extends Screen {

    private static final int OWNED_COLOR = 0x40FF40;
    private static final int OTHERS_COLOR = 0x40A0FF;
    private static final int SELECTED_COLOR = 0xFFFF40;
    private static final int TARGET_COLOR = 0xFF4040;
    private static final long TARGET_GLOW_NANOS = 6_000_000_000L;

    private static final double MIN_HEIGHT = 8.0;
    private static final double MAX_HEIGHT = 80.0;
    private static final double START_HEIGHT = 24.0;
    private static final double MAX_DISTANCE = 192.0;
    private static final int MAP_SIZE = 96;
    private static final double MAP_RADIUS = 128.0;
    private static final int MAP_X = 6;
    private static final int MAP_Y = 6;
    private static final double FORMATION_SPACING = 1.6;
    private static final int EDGE = 12;
    private static final int BOTTOM_EDGE = 3;
    private static final long DOUBLE_NANOS = 400_000_000L;
    private static final int PANEL_W = 110;

    private static final int CAMERA_ID = -3_000_000;

    private enum Mode { SELECT, MOVE, ATTACK_MOVE }

    private RtsCameraStand camera;
    private double camX;
    private double camZ;
    private double groundY;
    private double camHeight = START_HEIGHT;
    private float camYaw = 180.0F;
    private long lastFrameNanos;
    private boolean cleanedUp = false;
    private boolean panNorth, panSouth, panWest, panEast;

    // Selecting
    private static final int BOX_THRESHOLD = 4;
    private boolean boxPressed = false;
    private double boxStartX;
    private double boxStartY;
    private final Set<Integer> selected = new HashSet<>();
    private Map<String, Entity> commandable = new HashMap<>(); // by UUID, refreshed every frame
    private Mode mode = Mode.SELECT;
    private int lastClickedMob = -1;
    private long lastClickNanos;

    // Armies
    private RtsGroups groups;
    private final Set<Integer> selectedArmies = new HashSet<>();
    private int lastArmyPressed = -1;
    private long lastArmyNanos;
    private List<Integer> builtArmies = List.of(); // armies the bar was built for

    // Screen
    private boolean showHelp = false;
    private boolean settingsOpen = false;
    private String flashText;
    private int flashColor;
    private long flashUntil;
    private final Map<String, Integer> allyColorCache = new HashMap<>();
    /** Attack targets, in the order they were clicked, and when their red glow ends. */
    private final Map<Integer, Long> attackTargets = new java.util.LinkedHashMap<>();

    private Button newArmyButton;
    private Button disbandButton;
    private Button stanceButton;
    private final Map<Integer, Button> armyButtons = new HashMap<>();
    private boolean needsRebuild = false;
    private boolean shiftDown = false;

    public RtsScreen() {
        super(Component.literal("RTS"));
    }

    @Override
    protected void init() {
        LocalPlayer player = this.minecraft.player;
        if (camera == null && player != null && this.minecraft.level != null) {
            camX = player.getX();
            camZ = player.getZ();
            groundY = player.getY();
            camera = new RtsCameraStand(this.minecraft.level);
            camera.setId(CAMERA_ID);
            this.minecraft.level.addEntity(camera);
            placeCamera();
            this.minecraft.setCameraEntity(camera);
            RtsView.setHandVisible(this.minecraft, false);
            lastFrameNanos = System.nanoTime();
            ClientPlayNetworking.send(new RtsStatePayload(true));
        }
        if (groups == null) {
            groups = RtsGroups.fromLines(player == null ? null : player.getAttached(MobAttachments.RTS_GROUPS));
        }

        int x = this.width - PANEL_W - 4;
        this.addRenderableWidget(Button.builder(Component.literal("Exit RTS (J)"), b -> this.onClose())
                .bounds(x, 4, PANEL_W, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal(settingsOpen ? "Close Settings" : "Settings"), b -> {
            settingsOpen = !settingsOpen;
            rebuild();
        }).bounds(x, 26, PANEL_W, 20).build());

        if (settingsOpen) {
            armyButtons.clear();
            addSettingsPanel();
            return;
        }

        newArmyButton = null;
        disbandButton = null;
        if (HudConfig.rts(RtsPart.SIDE_PANEL)) {
            int half = (PANEL_W - 2) / 2;
            int y = 52;
            this.addRenderableWidget(Button.builder(Component.literal("Move (M)"), b -> setMode(Mode.MOVE))
                    .bounds(x, y, half, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Attack (R)"), b -> setMode(Mode.ATTACK_MOVE))
                    .bounds(x + half + 2, y, PANEL_W - half - 2, 20).build());
            y += 22;
            this.addRenderableWidget(Button.builder(Component.literal("Stop (X)"), b -> command(RtsCommandPayload.STOP))
                    .bounds(x, y, half, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Hold (Z)"), b -> command(RtsCommandPayload.HOLD))
                    .bounds(x + half + 2, y, PANEL_W - half - 2, 20).build());
            y += 22;
            newArmyButton = this.addRenderableWidget(Button.builder(Component.literal("New Army (G)"), b -> newArmy())
                    .bounds(x, y, PANEL_W, 20).build());
            y += 22;
            disbandButton = this.addRenderableWidget(Button.builder(Component.literal("Disband (Del)"), b -> disband())
                    .bounds(x, y, PANEL_W, 20).build());
            y += 22;
            stanceButton = this.addRenderableWidget(Button.builder(stanceLabel(), b -> toggleStance())
                    .bounds(x, y, PANEL_W, 20).build());
            y += 22;
            this.addRenderableWidget(Button.builder(Component.literal("Clear Selection"), b -> clearSelection())
                    .bounds(x, y, PANEL_W, 20).build());
        }
        if (HudConfig.rts(RtsPart.ARMY_BAR)) addArmyBar();
    }

    /**
     * Builds the buttons again, after the armies or the settings change. Done
     * at the start of the next frame, never in the middle of a click.
     */
    private void rebuild() {
        needsRebuild = true;
    }

    private void addSettingsPanel() {
        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = 40;
        for (RtsPart part : RtsPart.values()) {
            this.addRenderableWidget(Button.builder(settingLabel(part), b -> {
                HudConfig.toggleRts(part);
                b.setMessage(settingLabel(part));
            }).bounds(x, y, w, 20).build());
            y += 22;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            settingsOpen = false;
            rebuild();
        }).bounds(x, y + 6, w, 20).build());
    }

    private static Component settingLabel(RtsPart part) {
        return Component.literal(part.label + ": " + (HudConfig.rts(part) ? "ON" : "OFF"));
    }

    private void addArmyBar() {
        List<RtsGroups.Army> armies = groups.armies();
        List<Integer> numbers = new ArrayList<>();
        for (RtsGroups.Army army : armies) numbers.add(army.number);
        builtArmies = numbers;
        armyButtons.clear();
        if (armies.isEmpty()) return;
        int buttonWidth = 74;
        int gap = 3;
        int perRow = Math.max(1, (this.width - 20) / (buttonWidth + gap));
        int count = armies.size();
        int rows = (count + perRow - 1) / perRow;
        for (int i = 0; i < count; i++) {
            RtsGroups.Army army = armies.get(i);
            int row = i / perRow;
            int inRow = Math.min(perRow, count - row * perRow);
            int left = (this.width - (inRow * (buttonWidth + gap) - gap)) / 2;
            int x = left + (i % perRow) * (buttonWidth + gap);
            int y = this.height - 24 - (rows - 1 - row) * 22;
            int number = army.number;
            armyButtons.put(number, this.addRenderableWidget(Button.builder(armyLabel(army), b -> pressArmy(number))
                    .bounds(x, y, buttonWidth, 20).build()));
        }
    }

    private Component armyLabel(RtsGroups.Army army) {
        int here = presentIds(army.mobs).size();
        String mark = selectedArmies.contains(army.number) ? "> " : "";
        int color = army.color();
        var label = Component.literal(mark + army.name() + ": " + here).withStyle(style -> style.withColor(color));
        if (army.aggressive) label.append(Component.literal(" !").withStyle(style -> style.withColor(0xFF5050)));
        return label;
    }

    /** "Aggressive: ON" when every selected army is aggressive. */
    private Component stanceLabel() {
        boolean on = !selectedArmies.isEmpty();
        for (int number : selectedArmies) {
            RtsGroups.Army army = groups.army(number);
            if (army == null || !army.aggressive) on = false;
        }
        return Component.literal("Aggressive (V): " + (on ? "ON" : "OFF"));
    }

    /**
     * Switches the selected armies between Normal and Aggressive, like HOI4.
     * Aggressive armies move faster and fight anything hostile they meet.
     */
    private void toggleStance() {
        if (selectedArmies.isEmpty()) {
            flashBad("Select an army on the bottom bar first.");
            return;
        }
        boolean allOn = true;
        for (int number : selectedArmies) {
            RtsGroups.Army army = groups.army(number);
            if (army != null && !army.aggressive) allOn = false;
        }
        for (int number : selectedArmies) {
            RtsGroups.Army army = groups.army(number);
            if (army != null) army.aggressive = !allOn;
        }
        saveGroups();
        flash(allOn ? "Stance: Normal" : "Stance: Aggressive. Faster, and they fight what they meet.",
                allOn ? 0xFF80FF80 : 0xFFFF6060);
    }

    // --- Drawing ----------------------------------------------------------------

    private void placeCamera() {
        if (camera == null) return;
        camera.absSnapTo(camX, groundY + camHeight - camera.getEyeHeight(), camZ, camYaw, 90.0F);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (HudConfig.rts(RtsPart.ARMY_BAR) && !settingsOpen && !builtArmies.equals(armyNumbers())) needsRebuild = true;
        if (needsRebuild) {
            needsRebuild = false;
            this.clearWidgets();
            this.init();
        }
        pan(mouseX, mouseY);
        refreshCommandable();
        syncSelection();
        if (newArmyButton != null) newArmyButton.active = !selected.isEmpty();
        if (disbandButton != null) disbandButton.active = !selectedArmies.isEmpty();
        if (stanceButton != null) {
            stanceButton.active = !selectedArmies.isEmpty();
            stanceButton.setMessage(stanceLabel());
        }
        for (Map.Entry<Integer, Button> entry : armyButtons.entrySet()) {
            RtsGroups.Army army = groups.army(entry.getKey());
            if (army != null) entry.getValue().setMessage(armyLabel(army));
        }

        if (settingsOpen) {
            graphics.fill(this.width / 2 - 110, 18, this.width / 2 + 110, 40 + RtsPart.values().length * 22 + 32, 0xB0000000);
            graphics.centeredText(this.font, "RTS Settings", this.width / 2, 26, 0xFFFFFFFF);
            super.extractRenderState(graphics, mouseX, mouseY, a);
            return;
        }
        super.extractRenderState(graphics, mouseX, mouseY, a);

        WorldToScreen view = view();
        allyColorCache.clear();
        if (HudConfig.rts(RtsPart.ALLY_MARKERS)) drawAllyMarkers(graphics, view);
        if (HudConfig.rts(RtsPart.PINGS)) drawPings(graphics, view);
        if (HudConfig.rts(RtsPart.HEALTH_BARS)) drawHealthBars(graphics, view);
        if (HudConfig.rts(RtsPart.MINIMAP)) drawMinimap(graphics);
        if (HudConfig.rts(RtsPart.SIDE_PANEL)) drawArmyPanel(graphics);
        if (HudConfig.rts(RtsPart.DEBUG)) drawDebug(graphics, view, mouseX, mouseY);
        drawSelectionMarkers(graphics, view, mouseX, mouseY);
        drawAttackTargets(graphics, view);
        drawSelectedArmyButtons(graphics);
        drawSelectionBox(graphics, mouseX, mouseY);
        drawStatus(graphics);
        if (showHelp) drawHelp(graphics);
    }

    private List<Integer> armyNumbers() {
        List<Integer> numbers = new ArrayList<>();
        for (RtsGroups.Army army : groups.armies()) numbers.add(army.number);
        return numbers;
    }

    /** Selection count, the order mode, and short messages. */
    private void drawStatus(GuiGraphicsExtractor graphics) {
        int x = MAP_X;
        int y = HudConfig.rts(RtsPart.MINIMAP) ? MAP_Y + MAP_SIZE + 4 : MAP_Y;
        drawSelectionBanner(graphics);
        if (mode != Mode.SELECT) {
            String text = mode == Mode.MOVE ? "Click where to move. Esc cancels." : "Click where to attack-move. Esc cancels.";
            graphics.centeredText(this.font, text, this.width / 2, 26, mode == Mode.MOVE ? 0xFF60FF60 : 0xFFFFA040);
        }
        graphics.text(this.font, "Tab: controls", x, y, 0xFF999999, true);
        if (flashText != null && System.nanoTime() < flashUntil) {
            graphics.centeredText(this.font, flashText, this.width / 2, this.height - 80, flashColor);
        }
    }

    private void drawHelp(GuiGraphicsExtractor graphics) {
        String[] lines = {
                title(),
                "WASD or screen edges pan, scroll zooms, Q/E turn.",
                "Click a mob or drag a box to select, shift adds.",
                "Double-click a mob: all of that kind on screen. Ctrl+A: all.",
                "Right-click: move, or attack the enemy you click.",
                "Ctrl+right-click attack-moves, Shift+right-click queues.",
                "M or R, then click: move or attack-move order.",
                "X stops, Z holds, middle-click pings your team.",
                "G makes the selection a new army. Del disbands. V: aggressive.",
                "Shift+right-click more enemies: your mobs split up evenly between them.",
                "1-9 or the bottom bar selects an army, twice jumps to it.",
                "Right-click an army (or Ctrl+1-9): the selected mobs join it.",
                "Settings turns parts of this screen on or off.",
        };
        int width = 0;
        for (String line : lines) width = Math.max(width, this.font.width(line));
        int x = this.width / 2 - width / 2;
        int y = 30;
        graphics.fill(x - 4, y - 4, x + width + 4, y + lines.length * 10 + 2, 0xC0000000);
        for (int i = 0; i < lines.length; i++) {
            graphics.text(this.font, lines[i], x, y + i * 10, i == 0 ? 0xFFFFFFFF : 0xFFCCCCCC, true);
        }
    }

    /** The box being dragged: bright, two pixels thick, with a live count of the mobs inside. */
    private void drawSelectionBox(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!boxPressed || !isBox(mouseX, mouseY)) return;
        int x1 = (int) Math.min(boxStartX, mouseX);
        int y1 = (int) Math.min(boxStartY, mouseY);
        int x2 = (int) Math.max(boxStartX, mouseX);
        int y2 = (int) Math.max(boxStartY, mouseY);
        graphics.fill(x1, y1, x2, y2, 0x5040FF40);
        graphics.outline(x1, y1, x2 - x1, y2 - y1, 0xFF60FF60);
        graphics.outline(x1 + 1, y1 + 1, x2 - x1 - 2, y2 - y1 - 2, 0xFF60FF60);
        int inside = mobsInBox(view(), x1, y1, x2, y2).size();
        String label = inside + (inside == 1 ? " mob" : " mobs");
        int w = this.font.width(label) + 6;
        graphics.fill(mouseX + 8, mouseY + 6, mouseX + 8 + w, mouseY + 18, 0xC0000000);
        graphics.text(this.font, label, mouseX + 11, mouseY + 8, inside > 0 ? 0xFF60FF60 : 0xFFAAAAAA, true);
    }

    /** Your mobs whose middles are inside a screen box. */
    private List<Entity> mobsInBox(WorldToScreen view, double left, double top, double right, double bottom) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : commandable.values()) {
            WorldToScreen.Point p = project(view, entity);
            if (p == null) continue;
            double slack = reach(view, entity, p) * 0.5;
            if (p.x() >= left - slack && p.x() <= right + slack && p.y() >= top - slack && p.y() <= bottom + slack) {
                found.add(entity);
            }
        }
        return found;
    }

    /**
     * Corner brackets around every selected mob, pulsing, in yellow or its
     * army's color. While a box is being dragged, white brackets show which
     * mobs it's about to pick up.
     */
    private void drawSelectionMarkers(GuiGraphicsExtractor graphics, WorldToScreen view, int mouseX, int mouseY) {
        Set<Integer> preview = new HashSet<>();
        if (boxPressed && isBox(mouseX, mouseY)) {
            for (Entity entity : mobsInBox(view, Math.min(boxStartX, mouseX), Math.min(boxStartY, mouseY),
                    Math.max(boxStartX, mouseX), Math.max(boxStartY, mouseY))) {
                preview.add(entity.getId());
            }
        }
        double pulse = Math.sin(System.nanoTime() / 150_000_000.0) * 1.5;
        for (Entity entity : commandable.values()) {
            boolean isSelected = selected.contains(entity.getId());
            boolean isPreview = preview.contains(entity.getId());
            if (!isSelected && !isPreview) continue;
            WorldToScreen.Point p = project(view, entity);
            if (p == null) continue;
            double size = Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.6 + 0.3;
            int r = (int) Math.max(7, view.pixelsPerBlock(p.depth()) * size + pulse);
            int color;
            if (isPreview && !isSelected) {
                color = 0xFFFFFFFF;
            } else {
                RtsGroups.Army army = groups.armyOf(entity.getUUID().toString());
                color = army != null && selectedArmies.contains(army.number) ? 0xFF000000 | army.color() : 0xFFFFFF40;
            }
            drawBrackets(graphics, (int) p.x(), (int) p.y(), r, color);
        }
    }

    /** Four corner brackets, two pixels thick, around a point. */
    private static void drawBrackets(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
        int len = Math.max(3, r / 2);
        int l = cx - r;
        int t = cy - r;
        int rt = cx + r;
        int b = cy + r;
        // top left
        graphics.fill(l, t, l + len, t + 2, color);
        graphics.fill(l, t, l + 2, t + len, color);
        // top right
        graphics.fill(rt - len, t, rt, t + 2, color);
        graphics.fill(rt - 2, t, rt, t + len, color);
        // bottom left
        graphics.fill(l, b - 2, l + len, b, color);
        graphics.fill(l, b - len, l + 2, b, color);
        // bottom right
        graphics.fill(rt - len, b - 2, rt, b, color);
        graphics.fill(rt - 2, b - len, rt, b, color);
    }

    /** A thick colored frame around each selected army's button on the bottom bar. */
    private void drawSelectedArmyButtons(GuiGraphicsExtractor graphics) {
        for (Map.Entry<Integer, Button> entry : armyButtons.entrySet()) {
            if (!selectedArmies.contains(entry.getKey())) continue;
            RtsGroups.Army army = groups.army(entry.getKey());
            if (army == null) continue;
            Button b = entry.getValue();
            int color = 0xFF000000 | army.color();
            graphics.outline(b.getX() - 2, b.getY() - 2, b.getWidth() + 4, b.getHeight() + 4, color);
            graphics.outline(b.getX() - 3, b.getY() - 3, b.getWidth() + 6, b.getHeight() + 6, color);
            graphics.fill(b.getX(), b.getY() - 6, b.getX() + b.getWidth(), b.getY() - 4, color);
        }
    }

    /** Top middle: what's selected, in the army's color when it's an army. */
    private void drawSelectionBanner(GuiGraphicsExtractor graphics) {
        if (selected.isEmpty()) return;
        String text;
        int color;
        if (selectedArmies.size() == 1) {
            RtsGroups.Army army = groups.army(selectedArmies.iterator().next());
            text = (army == null ? "Army" : army.name()) + (army != null && army.aggressive ? " (Aggressive)" : "")
                    + " selected: " + selected.size()
                    + (selected.size() == 1 ? " mob" : " mobs");
            color = army == null ? 0xFFFFFF40 : 0xFF000000 | army.color();
        } else if (selectedArmies.size() > 1) {
            text = selectedArmies.size() + " armies selected: " + selected.size() + " mobs";
            color = 0xFFFFFF40;
        } else {
            text = selected.size() + (selected.size() == 1 ? " mob selected" : " mobs selected");
            color = 0xFFFFFF40;
        }
        int w = this.font.width(text) + 12;
        int x = this.width / 2 - w / 2;
        graphics.fill(x, 4, x + w, 20, 0xC0000000);
        graphics.fill(x, 18, x + w, 20, color);
        graphics.centeredText(this.font, text, this.width / 2, 8, color);
    }

    private boolean isBox(double mouseX, double mouseY) {
        return Math.abs(mouseX - boxStartX) >= BOX_THRESHOLD || Math.abs(mouseY - boxStartY) >= BOX_THRESHOLD;
    }

    private void flash(String text, int color) {
        flashText = text;
        flashColor = color;
        flashUntil = System.nanoTime() + 2_500_000_000L;
    }

    private void flashBad(String text) {
        flash(text, 0xFFFF8080);
    }

    private void flashGood(String text) {
        flash(text, 0xFF80FF80);
    }

    private String title() {
        LocalPlayer player = this.minecraft.player;
        if (player != null && RtsControl.isKing(player) && player.getTeam() != null) {
            return "RTS Mode - King of " + player.getTeam().getName();
        }
        return "RTS Mode";
    }

    /**
     * Debug view: a white cross where the game thinks each of your mobs is on
     * screen, and what's under the mouse. If the crosses sit on your mobs,
     * clicking works. If there are none, nothing nearby is yours to command.
     */
    private void drawDebug(GuiGraphicsExtractor graphics, WorldToScreen view, int mouseX, int mouseY) {
        for (Entity entity : commandable.values()) {
            WorldToScreen.Point p = project(view, entity);
            if (p == null) continue;
            int x = (int) p.x();
            int y = (int) p.y();
            int r = (int) reach(view, entity, p);
            graphics.fill(x - 4, y, x + 5, y + 1, 0xFFFFFFFF);
            graphics.fill(x, y - 4, x + 1, y + 5, 0xFFFFFFFF);
            graphics.outline(x - r, y - r, r * 2, r * 2, 0x80FFFFFF);
        }
        Vec3 ground = groundAt(mouseX, mouseY);
        String[] lines = {
                "Mobs you can command nearby: " + commandable.size(),
                "Mouse: " + mouseX + ", " + mouseY + "  Ground: " + Mth.floor(ground.x) + ", " + Mth.floor(ground.z),
                "Camera height: " + Math.round(camHeight) + "  Last click: " + lastInput
                        + "  (left is " + InputConstants.MOUSE_BUTTON_LEFT + ")",
        };
        int y = this.height - 90;
        for (String line : lines) {
            graphics.text(this.font, line, MAP_X, y, 0xFFFFFFFF, true);
            y += 10;
        }
    }

    // --- Camera -------------------------------------------------------------

    private double upX = 0, upZ = -1;
    private double rightX = 1, rightZ = 0;

    private void pan(int mouseX, int mouseY) {
        long now = System.nanoTime();
        double seconds = Math.min(0.1, (now - lastFrameNanos) / 1_000_000_000.0);
        lastFrameNanos = now;
        readScreenDirections();

        double speed = camHeight * 0.8 * seconds;
        double up = 0;
        double right = 0;
        boolean edges = !settingsOpen;
        if (panNorth || (edges && mouseY <= EDGE)) up += speed;
        if (panSouth || (edges && mouseY >= this.height - BOTTOM_EDGE)) up -= speed;
        if (panEast || (edges && mouseX >= this.width - EDGE)) right += speed;
        if (panWest || (edges && mouseX <= EDGE)) right -= speed;
        camX += upX * up + rightX * right;
        camZ += upZ * up + rightZ * right;

        LocalPlayer player = this.minecraft.player;
        if (player != null) {
            double dx = camX - player.getX();
            double dz = camZ - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > MAX_DISTANCE) {
                camX = player.getX() + dx / distance * MAX_DISTANCE;
                camZ = player.getZ() + dz / distance * MAX_DISTANCE;
            }
        }
        placeCamera();
    }

    /** Which ground directions the top and right of the screen point to, from the real camera. */
    private void readScreenDirections() {
        if (this.minecraft.getCameraEntity() != camera || camera == null) return;
        Vector3fc up = this.minecraft.gameRenderer.mainCamera().upVector();
        Vector3fc left = this.minecraft.gameRenderer.mainCamera().leftVector();
        double ul = Math.sqrt(up.x() * up.x() + up.z() * up.z());
        double ll = Math.sqrt(left.x() * left.x() + left.z() * left.z());
        if (ul < 0.01 || ll < 0.01) return;
        upX = up.x() / ul;
        upZ = up.z() / ul;
        rightX = -left.x() / ll;
        rightZ = -left.z() / ll;
    }

    private void turnCamera(float degrees) {
        camYaw = Mth.wrapDegrees(camYaw + degrees);
        placeCamera();
    }

    // --- Screen and world ----------------------------------------------------

    private WorldToScreen view() {
        return new WorldToScreen(this.minecraft, this.width, this.height);
    }

    /** Where an entity's middle shows on screen, or null if it's behind the camera. */
    private static WorldToScreen.Point project(WorldToScreen view, Entity entity) {
        return view.project(entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ());
    }

    /** How close to a mob on screen a click has to be, in pixels: its size on screen, at least 10. */
    private static double reach(WorldToScreen view, Entity entity, WorldToScreen.Point p) {
        double size = Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.6 + 0.4;
        return Math.max(10.0, view.pixelsPerBlock(p.depth()) * size);
    }

    /** The block spot under a screen point: a ray from the camera into the world. */
    private Vec3 groundAt(double mouseX, double mouseY) {
        WorldToScreen view = view();
        Vec3 eye = view.eye();
        Vec3 dir = view.ray(mouseX, mouseY).normalize();
        if (this.minecraft.level != null && this.minecraft.player != null) {
            BlockHitResult hit = this.minecraft.level.clip(new ClipContext(eye, eye.add(dir.scale(400)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.minecraft.player));
            if (hit.getType() != HitResult.Type.MISS) return hit.getLocation();
        }
        // Nothing hit (unloaded chunks): use the height RTS opened at.
        if (dir.y < -0.0001) {
            double t = (groundY - eye.y) / dir.y;
            return eye.add(dir.scale(t));
        }
        return new Vec3(camX, groundY, camZ);
    }

    // --- Who you command -------------------------------------------------------

    /** Every loaded mob you can command, and the glow colors. */
    private void refreshCommandable() {
        LocalPlayer player = this.minecraft.player;
        if (player == null || this.minecraft.level == null) return;
        Map<String, Entity> found = new HashMap<>();
        Map<Integer, Integer> glows = new HashMap<>();
        Set<Integer> stillHere = new HashSet<>();
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity == camera || entity == player || !entity.isAlive()) continue;
            if (!RtsControl.canCommand(entity, player)) continue;
            found.put(entity.getUUID().toString(), entity);
            stillHere.add(entity.getId());
            glows.put(entity.getId(), selected.contains(entity.getId()) ? SELECTED_COLOR : colorOf(entity, player));
        }
        selected.retainAll(stillHere);
        commandable = found;
        for (LivingEntity target : attackTargets()) glows.put(target.getId(), TARGET_COLOR);
        ClientGlow.setLocal(glows);
    }

    private Set<Integer> sentSelection = Set.of();

    /** Tells the server what's selected when it changes, so selected endermen hold still. */
    private void syncSelection() {
        if (selected.equals(sentSelection)) return;
        sentSelection = Set.copyOf(selected);
        if (ClientPlayNetworking.canSend(RtsSelectionPayload.TYPE)) {
            ClientPlayNetworking.send(new RtsSelectionPayload(new ArrayList<>(selected)));
        }
    }

    private int colorOf(Entity mob, LocalPlayer player) {
        RtsGroups.Army army = groups.armyOf(mob.getUUID().toString());
        if (army != null) return army.color();
        return MobOwnership.isOwnedBy(mob, player) ? OWNED_COLOR : OTHERS_COLOR;
    }

    /** The attack targets still alive and still glowing, oldest first. */
    private List<LivingEntity> attackTargets() {
        List<LivingEntity> live = new ArrayList<>();
        if (this.minecraft.level == null) return live;
        long now = System.nanoTime();
        attackTargets.entrySet().removeIf(entry -> {
            if (now > entry.getValue()) return true;
            Entity target = this.minecraft.level.getEntity(entry.getKey());
            if (!(target instanceof LivingEntity living) || !living.isAlive()) return true;
            live.add(living);
            return false;
        });
        return live;
    }

    /** The mob you command closest to this screen point, within its reach, or null. */
    private Entity commandableAt(double mouseX, double mouseY) {
        WorldToScreen view = view();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : commandable.values()) {
            WorldToScreen.Point p = project(view, entity);
            if (p == null) continue;
            double dx = p.x() - mouseX;
            double dy = p.y() - mouseY;
            double d = dx * dx + dy * dy;
            double r = reach(view, entity, p);
            if (d <= r * r && d < bestDistance) {
                bestDistance = d;
                best = entity;
            }
        }
        return best;
    }

    /** Any living thing that isn't yours, you, or on your side, closest to this screen point, or null. */
    private LivingEntity enemyAt(double mouseX, double mouseY) {
        LocalPlayer player = this.minecraft.player;
        if (player == null || this.minecraft.level == null) return null;
        WorldToScreen view = view();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
            if (entity == player || entity == camera || entity.isSpectator()) continue;
            if (entity.getType() == EntityTypes.ARMOR_STAND || commandable.containsKey(entity.getUUID().toString())) continue;
            if (entity.isAlliedTo(player)) continue;
            WorldToScreen.Point p = project(view, entity);
            if (p == null) continue;
            if (!visibleFromCamera(view.eye(), entity)) continue; // underground or under a roof
            double dx = p.x() - mouseX;
            double dy = p.y() - mouseY;
            double d = dx * dx + dy * dy;
            double r = reach(view, entity, p);
            if (d <= r * r && d < bestDistance) {
                bestDistance = d;
                best = living;
            }
        }
        return best;
    }

    /**
     * Whether you can actually see this entity from the RTS camera: its head,
     * middle or feet with no block in the way. So a mob in a cave under your
     * army can't be picked as an attack target, even if it glows.
     */
    private boolean visibleFromCamera(Vec3 eye, Entity entity) {
        if (this.minecraft.level == null || this.minecraft.player == null) return true;
        double[] heights = {entity.getBbHeight() * 0.9, entity.getBbHeight() * 0.5, 0.1};
        for (double h : heights) {
            Vec3 point = new Vec3(entity.getX(), entity.getY() + h, entity.getZ());
            BlockHitResult hit = this.minecraft.level.clip(new ClipContext(eye, point,
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.minecraft.player));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < 0.25) return true;
        }
        return false;
    }

    /** Entity ids of the mobs from this set that are loaded and yours to command. */
    private Set<Integer> presentIds(Set<String> uuids) {
        Set<Integer> ids = new HashSet<>();
        for (String uuid : uuids) {
            Entity entity = commandable.get(uuid);
            if (entity != null) ids.add(entity.getId());
        }
        return ids;
    }

    private List<Entity> selectedMobs() {
        List<Entity> mobs = new ArrayList<>();
        for (Entity entity : commandable.values()) if (selected.contains(entity.getId())) mobs.add(entity);
        return mobs;
    }

    // --- Input -------------------------------------------------------------------

    /*
     * Minecraft 26.3 reads the mouse through SDL, which numbers the buttons
     * differently from the old GLFW numbers (left was 0, right 1, middle 2).
     * So buttons are compared to the game's own constants, and Shift and Ctrl
     * come from the game too, never from raw numbers or modifier bits.
     */
    private static boolean isLeft(MouseButtonEvent event) {
        return event.button() == InputConstants.MOUSE_BUTTON_LEFT;
    }

    private static boolean isRight(MouseButtonEvent event) {
        return event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    private static boolean isMiddle(MouseButtonEvent event) {
        return event.button() == InputConstants.MOUSE_BUTTON_MIDDLE;
    }

    private boolean shiftHeld() {
        return shiftDown || this.minecraft.hasShiftDown();
    }

    private boolean ctrlHeld() {
        return this.minecraft.hasControlDown();
    }

    /** The last mouse press, shown in the Selection Debug view. */
    private String lastInput = "none yet";

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        lastInput = "button " + event.button() + " at " + (int) event.x() + ", " + (int) event.y();
        // Right-click an army on the bottom bar: the selected mobs join it.
        if (!settingsOpen && isRight(event)) {
            for (Map.Entry<Integer, Button> entry : armyButtons.entrySet()) {
                if (entry.getValue().isMouseOver(event.x(), event.y())) {
                    addSelectionTo(entry.getKey());
                    return true;
                }
            }
        }
        if (settingsOpen || overButton(event.x(), event.y())) return super.mouseClicked(event, doubleClick);
        if (HudConfig.rts(RtsPart.MINIMAP) && minimapClick(event.x(), event.y())) return true;
        boolean shift = shiftHeld();
        boolean ctrl = ctrlHeld();
        if (isLeft(event)) {
            if (mode != Mode.SELECT) {
                order(event.x(), event.y(), shift, mode == Mode.ATTACK_MOVE);
                if (!shift) mode = Mode.SELECT;
                return true;
            }
            boxPressed = true;
            boxStartX = event.x();
            boxStartY = event.y();
            return true;
        }
        if (isRight(event)) {
            mode = Mode.SELECT;
            order(event.x(), event.y(), shift, ctrl);
            return true;
        }
        if (isMiddle(event)) {
            ping(event.x(), event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (isLeft(event) && boxPressed) {
            boxPressed = false;
            boolean shift = shiftHeld();
            if (isBox(event.x(), event.y())) {
                boxSelect(boxStartX, boxStartY, event.x(), event.y(), shift);
            } else {
                clickSelect(boxStartX, boxStartY, shift);
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean overButton(double mouseX, double mouseY) {
        for (var child : this.children()) {
            if (child instanceof Button button && button.visible && button.isMouseOver(mouseX, mouseY)) return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        camHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, camHeight - scrollY * 3.0));
        placeCamera();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_LSHIFT || event.key() == InputConstants.KEY_RSHIFT) shiftDown = true;
        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (settingsOpen) {
                settingsOpen = false;
                rebuild();
            } else if (mode != Mode.SELECT) {
                mode = Mode.SELECT;
            } else {
                this.onClose();
            }
            return true;
        }
        if (AbilityKeys.rtsKey().matches(event)) {
            this.onClose();
            return true;
        }
        if (ctrlHeld() && event.key() == InputConstants.KEY_A) {
            selectAll();
            return true;
        }
        if (handleNumberKey(event)) return true;
        if (setPanKey(event, true)) return true;
        switch (event.key()) {
            case InputConstants.KEY_TAB -> showHelp = !showHelp;
            case InputConstants.KEY_X -> command(RtsCommandPayload.STOP);
            case InputConstants.KEY_Z -> command(RtsCommandPayload.HOLD);
            case InputConstants.KEY_Q -> turnCamera(-90.0F);
            case InputConstants.KEY_E -> turnCamera(90.0F);
            case InputConstants.KEY_M -> setMode(Mode.MOVE);
            case InputConstants.KEY_R -> setMode(Mode.ATTACK_MOVE);
            case InputConstants.KEY_G -> newArmy();
            case InputConstants.KEY_V -> toggleStance();
            case InputConstants.KEY_DELETE, InputConstants.KEY_BACKSPACE -> disband();
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == InputConstants.KEY_LSHIFT || event.key() == InputConstants.KEY_RSHIFT) shiftDown = false;
        if (setPanKey(event, false)) return true;
        return super.keyReleased(event);
    }

    private boolean setPanKey(KeyEvent event, boolean down) {
        if (this.minecraft.options.keyUp.matches(event)) { panNorth = down; return true; }
        if (this.minecraft.options.keyDown.matches(event)) { panSouth = down; return true; }
        if (this.minecraft.options.keyLeft.matches(event)) { panWest = down; return true; }
        if (this.minecraft.options.keyRight.matches(event)) { panEast = down; return true; }
        return false;
    }

    // --- Selecting ---------------------------------------------------------------

    private void clickSelect(double mouseX, double mouseY, boolean shift) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return;
        Entity mob = commandableAt(mouseX, mouseY);
        long now = System.nanoTime();
        if (mob != null) {
            selectedArmies.clear();
            int id = mob.getId();
            boolean doubleClick = id == lastClickedMob && now - lastClickNanos < DOUBLE_NANOS;
            lastClickedMob = id;
            lastClickNanos = now;
            if (doubleClick) {
                selectKindOnScreen(mob, shift);
            } else if (shift) {
                if (!selected.remove(id)) selected.add(id);
            } else {
                selected.clear();
                selected.add(id);
            }
            return;
        }
        lastClickedMob = -1;

        // Missed every mob of yours: say why, so it's clear what happened.
        LivingEntity other = enemyAt(mouseX, mouseY);
        if (commandable.isEmpty()) {
            flashBad("None of your mobs are nearby. Summon some, or use /rtstest claim.");
        } else if (other != null) {
            flashBad("That " + other.getName().getString() + " isn't yours to command.");
        }

        if (selected.isEmpty() && other == null && PlayerMob.has(player, MobType.SHULKER)) {
            Vec3 ground = groundAt(mouseX, mouseY);
            ClientPlayNetworking.send(new CityTeleportPayload(ground.x, ground.z, shulkerNear(ground.x, ground.z)));
            return;
        }
        if (!shift) clearSelection();
    }

    private void boxSelect(double x1, double y1, double x2, double y2, boolean shift) {
        double left = Math.min(x1, x2);
        double right = Math.max(x1, x2);
        double top = Math.min(y1, y2);
        double bottom = Math.max(y1, y2);
        if (!shift) selected.clear();
        selectedArmies.clear();
        int found = 0;
        for (Entity entity : mobsInBox(view(), left, top, right, bottom)) {
            selected.add(entity.getId());
            found++;
        }
        if (found > 0) flashGood("Selected " + found + (found == 1 ? " mob" : " mobs"));
        if (commandable.isEmpty()) {
            flashBad("None of your mobs are nearby. Summon some, or use /rtstest claim.");
        } else if (found == 0) {
            flashBad("None of your mobs are in that box.");
        }
    }

    /** Every mob of yours of the same kind that's on screen. */
    private void selectKindOnScreen(Entity mob, boolean add) {
        if (!add) selected.clear();
        WorldToScreen view = view();
        int count = 0;
        for (Entity entity : commandable.values()) {
            if (entity.getType() != mob.getType()) continue;
            WorldToScreen.Point p = project(view, entity);
            if (p == null || p.x() < 0 || p.y() < 0 || p.x() > this.width || p.y() > this.height) continue;
            selected.add(entity.getId());
            count++;
        }
        flashGood("Selected " + count + " " + mob.getType().getDescription().getString());
    }

    private void selectAll() {
        selected.clear();
        selectedArmies.clear();
        for (Entity entity : commandable.values()) selected.add(entity.getId());
        flashGood("Selected all " + selected.size() + " of your mobs nearby.");
    }

    private void clearSelection() {
        selected.clear();
        selectedArmies.clear();
    }

    // --- Orders -------------------------------------------------------------------

    private void setMode(Mode newMode) {
        if (selected.isEmpty()) {
            flashBad("Select some mobs first.");
            return;
        }
        mode = mode == newMode ? Mode.SELECT : newMode;
    }

    /** Attack the enemy at this point, or walk there in formation. */
    private void order(double mouseX, double mouseY, boolean queue, boolean attackMove) {
        if (selected.isEmpty()) {
            flashBad("Select some mobs first: click one or drag a box.");
            return;
        }
        LivingEntity enemy = enemyAt(mouseX, mouseY);
        if (enemy != null) {
            attack(enemy, queue);
            return;
        }
        Vec3 ground = groundAt(mouseX, mouseY);
        int sent = moveInFormation(ground.x, ground.y, ground.z, attackMove, queue);
        if (sent > 0) flashGood((attackMove ? "Attack-moving " : "Moving ") + sent + (sent == 1 ? " mob" : " mobs"));
    }

    /**
     * Sends the selection after an enemy. A plain right-click sends everyone
     * at that one enemy. Shift + right-click adds the enemy to the target list
     * and splits the selected mobs evenly between every target on it, each mob
     * going for a close one. Each mob also gets the other targets as backups:
     * when its own target dies, it goes for the closest one left.
     */
    private void attack(LivingEntity enemy, boolean queue) {
        if (!queue) attackTargets.clear();
        long until = System.nanoTime() + TARGET_GLOW_NANOS * 5;
        attackTargets.replaceAll((id, old) -> until);
        attackTargets.put(enemy.getId(), until);
        List<LivingEntity> targets = attackTargets();
        List<Entity> mobs = selectedMobs();
        if (mobs.isEmpty() || targets.isEmpty()) return;

        if (targets.size() == 1) {
            List<Integer> ids = new ArrayList<>();
            for (Entity mob : mobs) ids.add(mob.getId());
            ClientPlayNetworking.send(new AttackOrderPayload(ids, enemy.getId(), false));
            for (int id : ids) RtsMarkers.attack(id, enemy.getId());
            flash(ids.size() + (ids.size() == 1 ? " mob attacking " : " mobs attacking ")
                    + enemy.getName().getString() + ". Shift-click more to split them up.", 0xFFFF6060);
            return;
        }

        Map<LivingEntity, List<Integer>> split = splitEvenly(mobs, targets);
        // Each group's own target first, replacing what they were doing.
        for (Map.Entry<LivingEntity, List<Integer>> entry : split.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            ClientPlayNetworking.send(new AttackOrderPayload(entry.getValue(), entry.getKey().getId(), false));
            for (int id : entry.getValue()) RtsMarkers.attack(id, entry.getKey().getId());
        }
        // Then every other target as a backup for when theirs dies.
        for (LivingEntity target : targets) {
            List<Integer> others = new ArrayList<>();
            for (Map.Entry<LivingEntity, List<Integer>> entry : split.entrySet()) {
                if (entry.getKey() != target) others.addAll(entry.getValue());
            }
            if (!others.isEmpty()) ClientPlayNetworking.send(new AttackOrderPayload(others, target.getId(), true));
        }
        flash(mobs.size() + " mobs split between " + targets.size() + " targets", 0xFFFF6060);
    }

    /**
     * Shares the mobs out evenly, a round at a time: each round, every target
     * takes the closest mob that's still free. So each target gets the same
     * number, give or take one, and mobs go for targets near them.
     */
    private static Map<LivingEntity, List<Integer>> splitEvenly(List<Entity> mobs, List<LivingEntity> targets) {
        Map<LivingEntity, List<Integer>> split = new java.util.LinkedHashMap<>();
        for (LivingEntity target : targets) split.put(target, new ArrayList<>());
        List<Entity> free = new ArrayList<>(mobs);
        while (!free.isEmpty()) {
            for (LivingEntity target : targets) {
                if (free.isEmpty()) break;
                Entity closest = free.get(0);
                double best = Double.MAX_VALUE;
                for (Entity mob : free) {
                    double d = mob.distanceToSqr(target);
                    if (d < best) {
                        best = d;
                        closest = mob;
                    }
                }
                free.remove(closest);
                split.get(target).add(closest.getId());
            }
        }
        return split;
    }

    /** Red brackets and a number over each attack target, in the order they were picked. */
    private void drawAttackTargets(GuiGraphicsExtractor graphics, WorldToScreen view) {
        int number = 1;
        for (LivingEntity target : attackTargets()) {
            WorldToScreen.Point p = project(view, target);
            if (p == null) {
                number++;
                continue;
            }
            double size = Math.max(target.getBbWidth(), target.getBbHeight()) * 0.6 + 0.3;
            int r = (int) Math.max(7, view.pixelsPerBlock(p.depth()) * size);
            drawBrackets(graphics, (int) p.x(), (int) p.y(), r, 0xFFFF4040);
            if (attackTargets.size() > 1) {
                String label = String.valueOf(number);
                graphics.fill((int) p.x() - 5, (int) p.y() - r - 12, (int) p.x() + 6, (int) p.y() - r - 2, 0xC0000000);
                graphics.centeredText(this.font, label, (int) p.x(), (int) p.y() - r - 11, 0xFFFF6060);
            }
            number++;
        }
    }

    /** A block-shaped formation lined up with the screen. Each spot goes to the closest free mob. */
    private int moveInFormation(double wx, double wy, double wz, boolean attackMove, boolean queue) {
        List<Entity> mobs = selectedMobs();
        int count = mobs.size();
        if (count == 0) return 0;
        int columns = (int) Math.ceil(Math.sqrt(count));
        int rows = (int) Math.ceil(count / (double) columns);
        double centerX = Mth.floor(wx) + 0.5;
        double centerZ = Mth.floor(wz) + 0.5;

        List<Integer> ids = new ArrayList<>();
        List<Float> xs = new ArrayList<>();
        List<Float> ys = new ArrayList<>();
        List<Float> zs = new ArrayList<>();
        List<Entity> left = new ArrayList<>(mobs);
        for (int i = 0; i < count; i++) {
            double across = (i % columns - (columns - 1) / 2.0) * FORMATION_SPACING;
            double ahead = ((rows - 1) / 2.0 - i / columns) * FORMATION_SPACING;
            double x = count == 1 ? centerX : centerX + rightX * across + upX * ahead;
            double z = count == 1 ? centerZ : centerZ + rightZ * across + upZ * ahead;
            Entity closest = left.get(0);
            double best = Double.MAX_VALUE;
            for (Entity mob : left) {
                double d = mob.distanceToSqr(x, mob.getY(), z);
                if (d < best) {
                    best = d;
                    closest = mob;
                }
            }
            left.remove(closest);
            ids.add(closest.getId());
            xs.add((float) x);
            ys.add((float) wy);
            zs.add((float) z);
            RtsMarkers.addSpot(closest.getId(), x, z, attackMove, queue);
        }
        ClientPlayNetworking.send(new MoveOrderPayload(ids, xs, ys, zs, attackMove ? 1 : 0, queue));
        return count;
    }

    private void command(int command) {
        if (selected.isEmpty()) {
            flashBad("Select some mobs first.");
            return;
        }
        ClientPlayNetworking.send(new RtsCommandPayload(new ArrayList<>(selected), command));
        for (int id : selected) RtsMarkers.clear(id);
        flashGood((command == RtsCommandPayload.STOP ? "Stopped " : "Holding ") + selected.size()
                + (selected.size() == 1 ? " mob" : " mobs"));
    }

    private void ping(double mouseX, double mouseY) {
        if (this.minecraft.level == null) return;
        Vec3 ground = groundAt(mouseX, mouseY);
        ClientPlayNetworking.send(new PingPayload(ground.x, ground.y, ground.z));
    }

    private int shulkerNear(double wx, double wz) {
        int found = -1;
        double best = 1.5 * 1.5;
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (entity.getType() != EntityTypes.SHULKER) continue;
            double dx = entity.getX() - wx;
            double dz = entity.getZ() - wz;
            double d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                found = entity.getId();
            }
        }
        return found;
    }

    // --- Armies -----------------------------------------------------------------

    private void newArmy() {
        Set<String> uuids = new HashSet<>();
        for (Entity mob : selectedMobs()) uuids.add(mob.getUUID().toString());
        if (uuids.isEmpty()) {
            flashBad("Select some mobs first, then press New Army.");
            return;
        }
        RtsGroups.Army army = groups.create(uuids);
        if (army == null) {
            flashBad("You have " + RtsGroups.MAX_ARMIES + " armies already. Disband one first.");
            return;
        }
        selectedArmies.clear();
        selectedArmies.add(army.number);
        saveGroups();
        flashGood(army.name() + " made with " + uuids.size() + (uuids.size() == 1 ? " mob." : " mobs."));
    }

    private void disband() {
        if (selectedArmies.isEmpty()) {
            flashBad("Select an army on the bottom bar first.");
            return;
        }
        for (int number : selectedArmies) groups.disband(number);
        flashGood(selectedArmies.size() == 1 ? "Army disbanded." : selectedArmies.size() + " armies disbanded.");
        selectedArmies.clear();
        saveGroups();
    }

    /**
     * The selected mobs join an army you already have, like new Rabbit escorts
     * joining Army 1. They leave any army they were in. Afterwards the whole
     * army is selected, so you can give it orders straight away.
     */
    private void addSelectionTo(int number) {
        RtsGroups.Army army = groups.army(number);
        if (army == null) return;
        Set<String> uuids = new HashSet<>();
        for (Entity mob : selectedMobs()) {
            String uuid = mob.getUUID().toString();
            if (!army.mobs.contains(uuid)) uuids.add(uuid);
        }
        if (uuids.isEmpty()) {
            flashBad(selected.isEmpty()
                    ? "Select the new mobs first, then right-click the army."
                    : "Those mobs are already in " + army.name() + ".");
            return;
        }
        groups.addTo(number, uuids);
        saveGroups();
        selected.clear();
        selectedArmies.clear();
        selected.addAll(presentIds(army.mobs));
        selectedArmies.add(number);
        flashGood(uuids.size() + (uuids.size() == 1 ? " mob joined " : " mobs joined ") + army.name() + ".");
    }

    /** Click: select the army (shift adds). Twice quickly: jump the camera to it. */
    private void pressArmy(int number) {
        long now = System.nanoTime();
        boolean twice = number == lastArmyPressed && now - lastArmyNanos < DOUBLE_NANOS;
        lastArmyPressed = number;
        lastArmyNanos = now;
        selectArmy(number, shiftDown);
        if (twice) centerOn(selected);
    }

    private void selectArmy(int number, boolean add) {
        RtsGroups.Army army = groups.army(number);
        if (army == null) return;
        if (!add) {
            selected.clear();
            selectedArmies.clear();
        }
        Set<Integer> ids = presentIds(army.mobs);
        selected.addAll(ids);
        selectedArmies.add(number);
        if (ids.isEmpty()) flashBad(army.name() + " has no mobs nearby.");
    }

    /** 1-9 selects the army in that place on the bar, twice jumps to it. */
    private boolean handleNumberKey(KeyEvent event) {
        for (int i = 0; i < 9; i++) {
            if (!this.minecraft.options.keyHotbarSlots[i].matches(event)) continue;
            List<RtsGroups.Army> armies = groups.armies();
            if (i < armies.size() && ctrlHeld()) {
                addSelectionTo(armies.get(i).number);
            } else if (i < armies.size()) {
                int number = armies.get(i).number;
                long now = System.nanoTime();
                boolean twice = number == lastArmyPressed && now - lastArmyNanos < DOUBLE_NANOS;
                lastArmyPressed = number;
                lastArmyNanos = now;
                selectArmy(number, shiftHeld());
                if (twice) centerOn(selected);
            }
            return true;
        }
        return false;
    }

    private void centerOn(Set<Integer> ids) {
        double sumX = 0;
        double sumZ = 0;
        int count = 0;
        for (Entity entity : commandable.values()) {
            if (!ids.contains(entity.getId())) continue;
            sumX += entity.getX();
            sumZ += entity.getZ();
            count++;
        }
        if (count == 0) return;
        camX = sumX / count;
        camZ = sumZ / count;
        placeCamera();
    }

    private void saveGroups() {
        ClientPlayNetworking.send(new SetGroupsPayload(groups.toLines()));
    }

    /** Under the buttons on the right: each army with mobs nearby, its size and health. */
    private void drawArmyPanel(GuiGraphicsExtractor graphics) {
        int x = this.width - PANEL_W - 4;
        int y = 52 + 22 * 6 + 6;
        boolean any = false;
        for (RtsGroups.Army army : groups.armies()) {
            int count = 0;
            float health = 0;
            float max = 0;
            for (String uuid : army.mobs) {
                if (commandable.get(uuid) instanceof LivingEntity living) {
                    count++;
                    health += living.getHealth();
                    max += living.getMaxHealth();
                }
            }
            if (!any) {
                graphics.text(this.font, "Armies", x, y, 0xFFFFFFFF, true);
                y += 11;
                any = true;
            }
            float fraction = max <= 0 ? 0 : health / max;
            String mark = selectedArmies.contains(army.number) ? "> " : "";
            String name = mark + army.name() + ": " + count;
            graphics.text(this.font, name, x, y, 0xFF000000 | army.color(), true);
            if (army.aggressive) graphics.text(this.font, "AGG", x + this.font.width(name) + 4, y, 0xFFFF5050, true);
            if (count > 0) {
                String percent = Math.round(fraction * 100) + "%";
                graphics.text(this.font, percent, x + PANEL_W - this.font.width(percent), y, healthColor(fraction), true);
            }
            y += 10;
        }
        if (!any) graphics.text(this.font, "No armies yet. Select mobs, press G.", x - 80, y, 0xFF999999, true);
    }

    // --- Markers over the world ----------------------------------------------------

    private Integer allyColor(Entity mob, LocalPlayer player) {
        String owner = mob.getAttached(MobAttachments.OWNER);
        if (owner == null || owner.equals(player.getStringUUID()) || commandable.containsKey(mob.getUUID().toString())) {
            return null;
        }
        if (allyColorCache.containsKey(owner)) return allyColorCache.get(owner);
        Integer color = null;
        try {
            Player ownerPlayer = this.minecraft.level.getPlayerByUUID(UUID.fromString(owner));
            if (ownerPlayer != null && ownerPlayer.isAlliedTo(player)) color = AllyColors.of(owner);
        } catch (IllegalArgumentException badUuid) {
            // not a UUID: nobody's
        }
        allyColorCache.put(owner, color);
        return color;
    }

    private void drawAllyMarkers(GuiGraphicsExtractor graphics, WorldToScreen view) {
        LocalPlayer player = this.minecraft.player;
        if (player == null || this.minecraft.level == null) return;
        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity)) continue;
            Integer color = allyColor(entity, player);
            if (color == null) continue;
            WorldToScreen.Point p = project(view, entity);
            if (p == null) continue;
            int x = (int) p.x();
            int y = (int) p.y() - 12;
            graphics.fill(x - 3, y - 3, x + 3, y + 3, 0xC0000000);
            graphics.fill(x - 2, y - 2, x + 2, y + 2, color);
        }
    }

    private void drawPings(GuiGraphicsExtractor graphics, WorldToScreen view) {
        for (TeamPings.Ping ping : TeamPings.all()) {
            if (!ping.visible()) continue;
            WorldToScreen.Point p = view.project(ping.x, ping.y, ping.z);
            if (p == null) continue;
            int x = (int) p.x();
            int y = (int) p.y();
            graphics.outline(x - 5, y - 5, 11, 11, ping.color);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, ping.color);
            graphics.centeredText(this.font, ping.fromName, x, y - 16, ping.color);
        }
    }

    private void drawHealthBars(GuiGraphicsExtractor graphics, WorldToScreen view) {
        for (Entity entity : commandable.values()) {
            if (entity instanceof LivingEntity living) drawHealthBar(graphics, view, living);
        }
        for (LivingEntity target : attackTargets()) drawHealthBar(graphics, view, target);
    }

    private void drawHealthBar(GuiGraphicsExtractor graphics, WorldToScreen view, LivingEntity living) {
        WorldToScreen.Point p = view.project(living.getX(), living.getY() + living.getBbHeight(), living.getZ());
        if (p == null) return;
        int x = (int) p.x() - 8;
        int y = (int) p.y() - 6;
        if (x < -16 || y < -4 || x > this.width || y > this.height) return;
        float fraction = living.getMaxHealth() <= 0 ? 0 : Math.min(1.0f, living.getHealth() / living.getMaxHealth());
        graphics.fill(x - 1, y - 1, x + 17, y + 3, 0xC0000000);
        graphics.fill(x, y, x + Math.max(1, Math.round(16 * fraction)), y + 2, healthColor(fraction));
    }

    private static int healthColor(float fraction) {
        if (fraction > 0.5f) return 0xFF40E040;
        if (fraction > 0.25f) return 0xFFE0E040;
        return 0xFFE04040;
    }

    // --- Minimap ------------------------------------------------------------------

    private void drawMinimap(GuiGraphicsExtractor graphics) {
        LocalPlayer player = this.minecraft.player;
        if (player == null || this.minecraft.level == null) return;
        graphics.fill(MAP_X, MAP_Y, MAP_X + MAP_SIZE, MAP_Y + MAP_SIZE, 0xA0000000);

        // What the camera sees, as a frame: the ground under two corners of the screen.
        Vec3 corner1 = groundAt(0, 0);
        Vec3 corner2 = groundAt(this.width, this.height);
        int[] a = mapPointClamped(player, corner1.x, corner1.z);
        int[] b = mapPointClamped(player, corner2.x, corner2.z);
        int left = Math.min(a[0], b[0]);
        int top = Math.min(a[1], b[1]);
        int w = Math.abs(a[0] - b[0]);
        int h = Math.abs(a[1] - b[1]);
        if (w >= 2 && h >= 2) graphics.outline(left, top, w, h, 0x80FFFF40);

        for (Entity entity : this.minecraft.level.entitiesForRendering()) {
            int color;
            int size;
            if (commandable.containsKey(entity.getUUID().toString())) {
                Integer glow = ClientGlow.colorFor(entity.getId());
                color = 0xFF000000 | (glow != null ? glow : OWNED_COLOR);
                size = 2;
            } else if (entity instanceof Player other && other != player && !other.isSpectator()) {
                color = other.isAlliedTo(player) ? AllyColors.of(other.getStringUUID()) : 0xFFFF4040;
                size = 3;
            } else if (allyColor(entity, player) != null) {
                color = allyColor(entity, player);
                size = 2;
            } else if (attackTargets.containsKey(entity.getId()) && entity.isAlive()) {
                color = 0xFFFF4040;
                size = 3;
            } else {
                continue;
            }
            drawMapDot(graphics, player, entity.getX(), entity.getZ(), color, size);
        }
        for (TeamPings.Ping ping : TeamPings.all()) {
            if (!ping.visible()) continue;
            int[] p = mapPoint(player, ping.x, ping.z);
            if (p != null) graphics.outline(p[0] - 3, p[1] - 3, 7, 7, ping.color);
        }
        drawMapDot(graphics, player, player.getX(), player.getZ(), 0xFFFFFFFF, 3);
        graphics.outline(MAP_X, MAP_Y, MAP_SIZE, MAP_SIZE, 0xFFFFFFFF);
        graphics.text(this.font, "Map", MAP_X + 3, MAP_Y + 3, 0xFFCCCCCC, true);
    }

    private static double mapScale() {
        return MAP_SIZE / 2.0 / MAP_RADIUS;
    }

    private double[] mapRaw(LocalPlayer player, double x, double z) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        return new double[]{
                MAP_X + MAP_SIZE / 2.0 + (dx * rightX + dz * rightZ) * mapScale(),
                MAP_Y + MAP_SIZE / 2.0 - (dx * upX + dz * upZ) * mapScale()};
    }

    private int[] mapPoint(LocalPlayer player, double x, double z) {
        double[] p = mapRaw(player, x, z);
        if (p[0] < MAP_X || p[1] < MAP_Y || p[0] >= MAP_X + MAP_SIZE || p[1] >= MAP_Y + MAP_SIZE) return null;
        return new int[]{(int) p[0], (int) p[1]};
    }

    private int[] mapPointClamped(LocalPlayer player, double x, double z) {
        double[] p = mapRaw(player, x, z);
        return new int[]{
                (int) Math.max(MAP_X, Math.min(MAP_X + MAP_SIZE, p[0])),
                (int) Math.max(MAP_Y, Math.min(MAP_Y + MAP_SIZE, p[1]))};
    }

    private void drawMapDot(GuiGraphicsExtractor graphics, LocalPlayer player, double x, double z, int color, int size) {
        int[] p = mapPoint(player, x, z);
        if (p == null) return;
        int half = size / 2;
        graphics.fill(p[0] - half, p[1] - half, p[0] - half + size, p[1] - half + size, color);
    }

    private boolean minimapClick(double mouseX, double mouseY) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return false;
        if (mouseX < MAP_X || mouseY < MAP_Y || mouseX >= MAP_X + MAP_SIZE || mouseY >= MAP_Y + MAP_SIZE) return false;
        double sx = (mouseX - MAP_X - MAP_SIZE / 2.0) / mapScale();
        double sy = (MAP_Y + MAP_SIZE / 2.0 - mouseY) / mapScale();
        camX = player.getX() + rightX * sx + upX * sy;
        camZ = player.getZ() + rightZ * sx + upZ * sy;
        placeCamera();
        return true;
    }

    // --- Closing --------------------------------------------------------------

    @Override
    public void removed() {
        cleanUp();
        super.removed();
    }

    private void cleanUp() {
        if (cleanedUp) return;
        cleanedUp = true;
        ClientGlow.clearLocal();
        if (ClientPlayNetworking.canSend(RtsStatePayload.TYPE)) {
            ClientPlayNetworking.send(new RtsStatePayload(false));
        }
        if (this.minecraft.player != null) this.minecraft.setCameraEntity(this.minecraft.player);
        RtsView.setHandVisible(this.minecraft, true);
        if (camera != null && this.minecraft.level != null) {
            this.minecraft.level.removeEntity(camera.getId(), Entity.RemovalReason.DISCARDED);
        }
        camera = null;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Escape is handled in keyPressed: it cancels an order or closes settings first
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
