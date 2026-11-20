"""Hand edits 1.21.11 -> 1.21.10. Every edit must apply exactly once."""
import sys, pathlib
root = pathlib.Path(sys.argv[1]) / 'com/reality/mobcraft'
def edit(rel, old, new, count=1):
    f = root / rel; s = f.read_text(); n = s.count(old)
    if n != count: raise SystemExit(f'{rel}: expected {count} of {old[:70]!r}, found {n}')
    f.write_text(s.replace(old, new))

# Spears and the Parched skeleton arrived in 1.21.11 (Mounts of Mayhem). Before it there are none to check for.
edit('item/EvokerEssence.java', ' || held.is(ItemTags.SPEARS)', '')
edit('item/Tiers.java', ' || s.is(ItemTags.SPEARS)', '')
edit('item/ArmorUpgrades.java', '\n                    || mob.getType() == EntityType.PARCHED);', ');')
edit('item/SkullVisor.java', 'EntityType.BOGGED, EntityType.PARCHED, EntityType.WITHER_SKELETON', 'EntityType.BOGGED, EntityType.WITHER_SKELETON')

# Special item models: 1.21.10 collects the extents in a Set instead of a Consumer.
for rel in ['client/GrubRenderer.java', 'client/LensChain.java', 'client/TomeRenderer.java']:
    f = root / rel; s = f.read_text()
    a = s.index('public void getExtents(Consumer<Vector3fc> out) {'); b = s.index('\n    }\n', a)
    body = s[a:b].replace('public void getExtents(Consumer<Vector3fc> out) {', 'public void getExtents(java.util.Set<Vector3f> out) {').replace('out.accept(', 'out.add(')
    f.write_text(s[:a] + body + s[b:])

# Charged-item glow: a 1.21.10 quad is a packed vertex array (8 ints a vertex: x y z, color, u v, light, normal).
edit('client/ExplosiveItemGlow.java', 'texel(quad.packedUV(0),', 'texel(quad, 0,')
edit('client/ExplosiveItemGlow.java', 'texel(quad.packedUV(1),', 'texel(quad, 1,')
edit('client/ExplosiveItemGlow.java', 'texel(quad.packedUV(3),', 'texel(quad, 3,')
edit('client/ExplosiveItemGlow.java', '''    private static float[] texel(long packed, float u0, float u1, float v0, float v1, int w, int h) {
        return new float[]{(UVPair.unpackU(packed) - u0) / (u1 - u0) * w, (UVPair.unpackV(packed) - v0) / (v1 - v0) * h};
    }''', '''    private static float[] texel(BakedQuad quad, int i, float u0, float u1, float v0, float v1, int w, int h) {
        int[] v = quad.vertices();
        float u = Float.intBitsToFloat(v[i * 8 + 4]), t = Float.intBitsToFloat(v[i * 8 + 5]);
        return new float[]{(u - u0) / (u1 - u0) * w, (t - v0) / (v1 - v0) * h};
    }

    private static Vector3fc position(BakedQuad quad, int i) {
        int[] v = quad.vertices();
        return new Vector3f(Float.intBitsToFloat(v[i * 8]), Float.intBitsToFloat(v[i * 8 + 1]), Float.intBitsToFloat(v[i * 8 + 2]));
    }''')
edit('client/ExplosiveItemGlow.java', 'Vector3fc p0 = quad.position(0), p1 = quad.position(1), p3 = quad.position(3);',
     'Vector3fc p0 = position(quad, 0), p1 = position(quad, 1), p3 = position(quad, 3);')

# F3+B attack box: 1.21.10 has no Gizmos. Its hitboxes are a list each entity renderer builds, so add one more box
# to that list, as a hitbox relative to the entity like vanilla's, in the same green.
(root / 'mixin/client/EntityHitboxDebugRendererMixin.java').write_text('''package com.reality.mobcraft.mixin.client;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.reality.mobcraft.hitbox.SmallMobHitbox;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.client.renderer.entity.state.HitboxesRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Draws the dual-hitbox attack box next to the vanilla F3+B box (1.21.9 and 1.21.10).
 *
 * These versions collect each entity's F3+B boxes into a list when the entity is gathered up to draw. One more box is
 * added to that list: the normal-sized attack box, in green. The real small box stays white.
 */
@Mixin(EntityRenderer.class)
public class EntityHitboxDebugRendererMixin {
    @ModifyReturnValue(method = "extractHitboxes(Lnet/minecraft/world/entity/Entity;FZ)Lnet/minecraft/client/renderer/entity/state/HitboxesRenderState;", at = @At("RETURN"))
    private HitboxesRenderState mobcraft$addAttackBox(HitboxesRenderState boxes, Entity entity, float partialTick, boolean green) {
        if (boxes == null || !SmallMobHitbox.isSmall(entity)) return boxes;
        AABB box = SmallMobHitbox.inflateToNormal(entity, entity.getBoundingBox());
        ImmutableList<HitboxRenderState> more = ImmutableList.<HitboxRenderState>builder().addAll(boxes.hitboxes())
                .add(new HitboxRenderState(box.minX - entity.getX(), box.minY - entity.getY(), box.minZ - entity.getZ(),
                        box.maxX - entity.getX(), box.maxY - entity.getY(), box.maxZ - entity.getZ(), 0f, 1f, 0f))
                .build();
        return new HitboxesRenderState(boxes.viewX(), boxes.viewY(), boxes.viewZ(), more);
    }
}
'''.replace('import org.spongepowered.asm.mixin.Mixin;', 'import org.spongepowered.asm.mixin.Mixin;\nimport org.spongepowered.asm.mixin.injection.At;'))

# Gloamite Sweep Hiss: 1.21.11 split the sword sweep into doSweepAttack(target, damage, source, strength). In 1.21.10
# the sweep is inside attack(target), which then calls sweepAttack() for the particle. Hook that call and read the
# damage dealt (local 10) and its source (local 4), the same values doSweepAttack is given in 1.21.11.
edit('mixin/GloamitePlayerMixin.java', '''    @Inject(method = "doSweepAttack", at = @At("TAIL"))
    private void mobcraft$sweepHiss(Entity target, float damage, DamageSource source, float strength, CallbackInfo ci) {''',
'''    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;sweepAttack()V"))
    private void mobcraft$sweepHiss(Entity target, CallbackInfo ci,
                                    @com.llamalad7.mixinextras.sugar.Local(index = 10) float damage,
                                    @com.llamalad7.mixinextras.sugar.Local(index = 4) DamageSource source) {''')
# Camera: 1.21.10 names the rotation getters getXRot()/getYRot().
edit('mixin/client/CameraShakeMixin.java', '@Shadow public abstract float xRot();', '@Shadow public abstract float getXRot();')
edit('mixin/client/CameraShakeMixin.java', '@Shadow public abstract float yRot();', '@Shadow public abstract float getYRot();')
edit('mixin/client/CameraShakeMixin.java', 'setRotation(yRot() + (float) Math.sin(time * 1.7) * amp, xRot() + (float) Math.cos(time * 2.3) * amp);',
     'setRotation(getYRot() + (float) Math.sin(time * 1.7) * amp, getXRot() + (float) Math.cos(time * 2.3) * amp);')

# Backwards troll tier, third person: 1.21.10's ItemInHandLayer.submitArmWithItem isn't given the ItemStack. So the
# item's render state is marked Backwards when its model is resolved (where the stack is known), and read here.
edit('client/ExplosiveGlowState.java', '''    default net.minecraft.world.item.ItemDisplayContext mobcraft$displayContext() { return null; }''',
'''    default net.minecraft.world.item.ItemDisplayContext mobcraft$displayContext() { return null; }

    /** The Backwards troll tier (1.21.10 and older, where the held-item layer can't see the stack). */
    default void mobcraft$markBackwards() {}
    default boolean mobcraft$backwards() { return false; }''')
edit('mixin/client/ItemStackRenderStateGlowMixin.java', '''    @Shadow public abstract void setAnimated();''', '''    @Shadow public abstract void setAnimated();

    @Unique private boolean mobcraft$backwards;

    @Override
    public void mobcraft$markBackwards() { mobcraft$backwards = true; }

    @Override
    public boolean mobcraft$backwards() { return mobcraft$backwards; }

    @org.spongepowered.asm.mixin.injection.Inject(method = "clear", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void mobcraft$clearBackwards(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        mobcraft$backwards = false;
    }''')
edit('mixin/client/ItemModelResolverGlowMixin.java', '''        if (stack.has(CreeperEssence.CHARGE)) ((ExplosiveGlowState) state).mobcraft$markExplosive();''',
'''        if (stack.has(CreeperEssence.CHARGE)) ((ExplosiveGlowState) state).mobcraft$markExplosive();
        if (com.reality.mobcraft.item.Tiers.is(stack, com.reality.mobcraft.item.Tiers.Kind.TOOL, "backwards"))
            ((ExplosiveGlowState) state).mobcraft$markBackwards();''')
edit('mixin/client/BackwardsItemMixin.java', '''                                    Operation<Void> original, @Local(argsOnly = true) ItemStack stack) {
        if (!Tiers.is(stack, Tiers.Kind.TOOL, "backwards")) {''', '''                                    Operation<Void> original) {
        if (!((com.reality.mobcraft.client.ExplosiveGlowState) item).mobcraft$backwards()) {''')

# Grub quads: items share the block atlas before 1.21.11, and a quad is a packed vertex array
# (8 ints a vertex: x y z, color, u v, light, normal).
edit('client/GrubRenderer.java', '.getAtlasOrThrow(net.minecraft.data.AtlasIds.ITEMS)', '.getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS)')
edit('client/GrubRenderer.java', """            Vector3f[] p = new Vector3f[4];
            long[] uv = new long[4];
            for (int k = 0; k < 4; k++) {
                int o = (q + k) * 8;
                p[k] = new Vector3f(d[o], d[o + 1], d[o + 2]);
                uv[k] = net.minecraft.client.model.geom.builders.UVPair.pack(sprite.getU(d[o + 3]), sprite.getV(d[o + 4]));
            }
            int o = q * 8;
            net.minecraft.core.Direction face = net.minecraft.core.Direction.getApproximateNearest(d[o + 5], d[o + 6], d[o + 7]);
            out.add(new net.minecraft.client.renderer.block.model.BakedQuad(p[3], p[2], p[1], p[0], uv[3], uv[2], uv[1], uv[0],
                    -1, face, sprite, true, 0));""",
"""            int o = q * 8;
            net.minecraft.core.Direction face = net.minecraft.core.Direction.getApproximateNearest(d[o + 5], d[o + 6], d[o + 7]);
            int normal = (Math.round(d[o + 5] * 127) & 255) | (Math.round(d[o + 6] * 127) & 255) << 8 | (Math.round(d[o + 7] * 127) & 255) << 16;
            int[] v = new int[32];
            for (int k = 0; k < 4; k++) {
                int s = (q + 3 - k) * 8, t = k * 8;   // the corners the other way round, so the faces point out
                v[t] = Float.floatToRawIntBits(d[s]); v[t + 1] = Float.floatToRawIntBits(d[s + 1]); v[t + 2] = Float.floatToRawIntBits(d[s + 2]);
                v[t + 3] = -1;
                v[t + 4] = Float.floatToRawIntBits(sprite.getU(d[s + 3])); v[t + 5] = Float.floatToRawIntBits(sprite.getV(d[s + 4]));
                v[t + 6] = 0;
                v[t + 7] = normal;
            }
            out.add(new net.minecraft.client.renderer.block.model.BakedQuad(v, -1, face, sprite, true, 0));""")

# Charged-item glow: 1.21.10 builds a render type from shards, and all of it is public, so no invoker is needed.
edit('client/ExplosiveItemGlow.java', '''        return com.reality.mobcraft.mixin.client.GlowRenderTypeAccess.mobcraft$create("mobcraft_explosive_glow",
                net.minecraft.client.renderer.rendertype.RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", TEXTURE)
                        .setTextureTransform(new net.minecraft.client.renderer.rendertype.TextureTransform.OffsetTextureTransform(u, v))
                        .useLightmap()
                        .useOverlay()
                        .sortOnUpload()
                        .createRenderSetup());''', '''        return net.minecraft.client.renderer.RenderType.create("mobcraft_explosive_glow", 1536, false, true, pipeline,
                net.minecraft.client.renderer.RenderType.CompositeState.builder()
                        .setTextureState(new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(TEXTURE, false))
                        .setTexturingState(new net.minecraft.client.renderer.RenderStateShard.OffsetTexturingStateShard(u, v))
                        .setLightmapState(net.minecraft.client.renderer.RenderStateShard.LIGHTMAP)
                        .setOverlayState(net.minecraft.client.renderer.RenderStateShard.OVERLAY)
                        .createCompositeState(false));''')
edit('client/ExplosiveItemGlow.java', 'com.reality.mobcraft.mixin.client.GlowPipelineAccess.mobcraft$matricesFog()',
     'net.minecraft.client.renderer.RenderPipelines.MATRICES_FOG_SNIPPET')
edit('client/ExplosiveItemGlow.java', '''                            net.minecraft.client.renderer.Sheets.translucentBlockItemSheet(), net.minecraft.client.renderer.Sheets.cutoutBlockSheet(),''',
     '''                            net.minecraft.client.renderer.Sheets.cutoutBlockSheet(),''')
for rel in ['mixin/client/GlowRenderTypeAccess.java', 'mixin/client/GlowPipelineAccess.java']:
    (root / rel).unlink()
mj = root.parents[3] / 'resources/mobcraft.client.mixins.json'
s = mj.read_text(); assert s.count('    "GlowRenderTypeAccess",\n    "GlowPipelineAccess",\n') == 1
mj.write_text(s.replace('    "GlowRenderTypeAccess",\n    "GlowPipelineAccess",\n', ''))
