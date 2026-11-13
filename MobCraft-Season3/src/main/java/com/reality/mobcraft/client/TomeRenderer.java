package com.reality.mobcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import com.reality.mobcraft.item.ModItems;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * The Stronghold Tome (item/StrongholdTome), drawn with the enchanting table's own book model and
 * texture. Closed, it's a shut book. Hold right-click and the covers swing open, the book rises
 * and tips toward you, and the pages keep flipping, like the book over an enchanting table when
 * you walk up to it. Let go and it closes again, smoothly. How open it is is kept per holder,
 * so every player's book opens and shuts on its own, and others see yours open too.
 */
public final class TomeRenderer implements SpecialModelRenderer<TomeRenderer.Arg> {

    public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

    /** open 0 to 1, page flip in turns, time in ticks, and where it's drawn. */
    public record Arg(float open, float flip, float time, ItemDisplayContext context, int burns) {
        public Arg(float open, float flip, float time, ItemDisplayContext context) { this(open, flip, time, context, 0); }
        Arg withBurns(int b) { return new Arg(open, flip, time, context, b); }
    }

    /** Char drawn over a burnt Tome (textures/entity/tome_burn_1..4.png). */
    private static final net.minecraft.resources.Identifier[] BURN = {
            com.reality.mobcraft.MobCraft.id("textures/entity/tome_burn_1.png"), com.reality.mobcraft.MobCraft.id("textures/entity/tome_burn_2.png"),
            com.reality.mobcraft.MobCraft.id("textures/entity/tome_burn_3.png"), com.reality.mobcraft.MobCraft.id("textures/entity/tome_burn_4.png")};

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Arg> {
        @Override
        public SpecialModelRenderer<Arg> bake(SpecialModelRenderer.BakingContext context) {
            return new TomeRenderer(new BookModel(context.entityModelSet().bakeLayer(ModelLayers.BOOK)), context.sprites());
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private final BookModel model;
    private final SpriteGetter sprites;

    private TomeRenderer(BookModel model, SpriteGetter sprites) {
        this.model = model;
        this.sprites = sprites;
        instance = this;
    }

    /** Per holder: how open the book is, the page flip, and when it was last stepped (nanoseconds). */
    private static final Map<Object, float[]> OPEN = new WeakHashMap<>();
    private static final long START = System.nanoTime();

    /** The renderer, once the game has built it, so the first-person hands can draw the book too. */
    private static TomeRenderer instance;

    public static float now() {
        return (System.nanoTime() - START) / 5e7f;   // ticks
    }

    public static boolean reading(LivingEntity living) {
        if (living == null) return false;
        return (living.isUsingItem() && living.getUseItem().is(ModItems.STRONGHOLD_TOME)) || peeking(living);
    }

    /** Turning the page (sneak + right-click): the book comes up, opens partway and a page flips over. */
    private static final Map<Object, float[]> PAGE = new WeakHashMap<>();   // last power seen, peek until (ticks)
    private static boolean peeking(LivingEntity living) {
        ItemStack tome = living.getMainHandItem().is(ModItems.STRONGHOLD_TOME) ? living.getMainHandItem()
                : living.getOffhandItem().is(ModItems.STRONGHOLD_TOME) ? living.getOffhandItem() : ItemStack.EMPTY;
        if (tome.isEmpty()) return false;
        int power = com.reality.mobcraft.item.TomePowers.power(tome);
        float[] p = PAGE.computeIfAbsent(living, k -> new float[]{power, 0f});
        if (p[0] != power) {
            p[0] = power;
            p[1] = now() + 34f;
            float[] s = OPEN.get(living);
            FLIPS.put(living, 1f);
        }
        return now() < p[1];
    }
    private static final Map<Object, Float> FLIPS = new WeakHashMap<>();

    /**
     * Steps this holder's book: how far through opening it is (0 to 1) and how far the pages have flipped. Opening
     * takes about a second and a half, like a person would: first the hands come up and take the book (lift), then
     * the covers part and fall open (cover). Shutting runs it backwards a little quicker: the covers close, then the
     * hands come down. Pages flip once it's open, one turn about every two seconds. The arms (TomeArmsMixin), the
     * first-person hands (TomeHandsMixin) and the book all share this, so they always move together.
     */
    public static float[] step(Object holder, boolean reading) {
        float time = now();
        float[] s = OPEN.computeIfAbsent(holder, k -> new float[]{0f, 0f, time});
        float dt = Mth.clamp(time - s[2], 0f, 5f);
        if (dt > 0f) {
            s[2] = time;
            s[0] = Mth.clamp(s[0] + (reading ? dt / 30f : -dt / 20f), 0f, 1f);
            if (cover(s[0]) > 0.8f) s[1] += dt / 40f;
            Float pending = FLIPS.get(holder);
            if (pending != null && cover(s[0]) > 0.3f) {   // the turned page flips over quickly once it's open
                float step = Math.min(pending, dt / 8f);
                s[1] += step;
                if (pending - step <= 0f) FLIPS.remove(holder); else FLIPS.put(holder, pending - step);
            }
        }
        return s;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    /** The first part of opening: hands rising to take the book, eased. */
    public static float lift(float progress) {
        return smooth(progress / 0.45f);
    }

    /** The second part: the covers parting, 0 to 1 (eased again where it's drawn). */
    public static float cover(float progress) {
        return Mth.clamp((progress - 0.4f) / 0.6f, 0f, 1f);
    }

    /** How far this holder's hands have come up for the book, for posing arms. */
    public static float openness(LivingEntity living) {
        return lift(step(living, reading(living))[0]);
    }

    @Override
    public Arg extractArgument(ItemStack stack) {
        ItemDisplayContext context = LensChain.updatingContext();
        ItemOwner owner = LensChain.updatingOwner();
        float time = now();
        int burns = com.reality.mobcraft.item.StrongholdTome.burns(stack);
        if (owner == null || context == ItemDisplayContext.GUI) return new Arg(0f, 0f, time, context, burns);
        LivingEntity living = owner.asLivingEntity();
        float[] s = step(living != null ? living : owner, reading(living));
        // Held by someone, the hand-held book only shows while it's shut and down; otherwise the two-handed
        // versions take over (submitHeld, submitInArms). Anywhere else, open is how far the covers have parted.
        return new Arg(owner != null && s[0] > 0f && context != ItemDisplayContext.NONE ? s[0] : cover(s[0]), s[1], time, context, burns);
    }

    /** First person while reading: the book held open in both hands (TomeHandsMixin), facing the reader. */
    public static void submitHeld(LivingEntity reader, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        if (instance == null) return;
        float[] s = step(reader, reading(reader));
        ItemStack held = reader.getUseItem().is(ModItems.STRONGHOLD_TOME) ? reader.getUseItem()
                : reader.getMainHandItem().is(ModItems.STRONGHOLD_TOME) ? reader.getMainHandItem() : reader.getOffhandItem();
        instance.draw(new Arg(cover(s[0]), s[1], now(), ItemDisplayContext.NONE, com.reality.mobcraft.item.StrongholdTome.burns(held)), poseStack, collector, light, 0);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        out.accept(new Vector3f(0.1f, 0.1f, 0.1f));
        out.accept(new Vector3f(0.9f, 0.9f, 0.9f));
    }

    @Override
    public void submit(Arg arg, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil,
                       int outlineColor) {
        if (arg == null) return;
        ItemDisplayContext c = arg.context();
        boolean thirdPerson = c == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || c == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        boolean firstPerson = c == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || c == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        // In first person an open book is drawn between both hands instead (TomeHandsMixin).
        if (firstPerson && arg.open() > 0f) return;
        // Seen by others while it's open, it's held in both hands in front of the chest instead (submitInArms).
        if (thirdPerson && arg.open() > 0f) return;
        poseStack.pushPose();
        draw(arg, poseStack, collector, light, outlineColor);
        poseStack.popPose();
    }

    /**
     * Seen by others: the open book held out in both hands in front of the chest, the pages tipped up toward the
     * reader's face. Drawn from the player's own model space (MobLookLayer), where the arms (TomeArmsMixin) meet.
     */
    public static void submitInArms(float lift, float cover, float flip, int burns, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        if (instance == null || lift <= 0f) return;
        poseStack.pushPose();
        // Model space: y runs down from the neck, the front is -z. The book starts in the right hand, where the
        // arm hangs (shoulder 5 px right of center, 2 px down, hand about 10 px down the arm), and rides up with
        // the arms (TomeArmsMixin) to the middle between both hands: 8.5 px down, 10.5 px out, pages tipped up.
        float armX = Mth.lerp(lift, 0f, -1.05f);
        float handX = -5f, handY = 2f + 10f * Mth.cos(armX), handZ = 10f * Mth.sin(armX);
        poseStack.translate(Mth.lerp(lift, handX, 0f) / 16f, Mth.lerp(lift, handY, 8.5f) / 16f,
                Mth.lerp(lift, handZ, -10.5f) / 16f);
        poseStack.rotateDegrees(Axis.ZP, 180f);
        poseStack.rotateDegrees(Axis.YP, 180f);
        poseStack.rotateDegrees(Axis.XP, Mth.lerp(lift, -10f, 62f));
        float scale = Mth.lerp(lift, 0.55f, 0.75f);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        instance.draw(new Arg(cover, flip, now(), ItemDisplayContext.NONE, burns), poseStack, collector, light, 0);
        poseStack.popPose();
    }

    private void draw(Arg arg, PoseStack poseStack, SubmitNodeCollector collector, int light, int outlineColor) {
        float open = arg.open();
        float ease = open * open * (3 - 2 * open);
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5f, 0.5f);
        // Open, it lifts a little and bobs gently, like the book over an enchanting table.
        poseStack.translate(0f, ease * (0.12f + Mth.sin(arg.time() * 0.1f) * 0.02f), 0f);
        // The book model stands upright with its spine up the middle. Held, it faces you and opens
        // toward you; the item's display settings turn it the right way for each view.
        poseStack.rotateDegrees(Axis.YP, 90f);
        float flip = arg.flip();
        float flip1 = Mth.clamp(Mth.frac(flip + 0.25f) * 1.6f - 0.3f, 0f, 1f);
        float flip2 = Mth.clamp(Mth.frac(flip + 0.75f) * 1.6f - 0.3f, 0f, 1f);
        BookModel.State state = BookModel.State.forAnimation(arg.time(), flip1, flip2, ease);
        collector.submitModel(model, state, poseStack, light, OverlayTexture.NO_OVERLAY, -1,
                EnchantTableRenderer.BOOK_TEXTURE, sprites, outlineColor);
        if (arg.burns() > 0) {
            // The char, drawn over the same book a hair bigger so it sits on top.
            poseStack.scale(1.006f, 1.006f, 1.006f);
            collector.order(1).submitModel(model, state, poseStack,
                    net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(BURN[Math.min(arg.burns(), 4) - 1]),
                    light, OverlayTexture.NO_OVERLAY, outlineColor);
        }
        poseStack.popPose();
    }
}
