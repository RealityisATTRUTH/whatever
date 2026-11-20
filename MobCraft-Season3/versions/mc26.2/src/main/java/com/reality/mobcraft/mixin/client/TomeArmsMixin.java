package com.reality.mobcraft.mixin.client;

import com.reality.mobcraft.client.MobLookLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reading the Stronghold Tome, seen by everyone: both arms come up and forward and meet in front
 * of the chest, holding the open book like a person reading, and the head tips down to look at it.
 * Follows how open the book is (MobLookLayer.READING), so the arms rise as it opens and drop as it shuts.
 */
@Mixin(HumanoidModel.class)
public abstract class TomeArmsMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void mobcraft$readingArms(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatar)) return;
        Float r = avatar.getData(MobLookLayer.READING);
        if (r == null || r <= 0f) return;
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        float breathe = Mth.sin(state.ageInTicks * 0.08f) * 0.03f;
        // Arms out in front, a little below level, hands a book's width apart at its covers.
        float ax = -1.05f, ay = 0.05f;
        model.rightArm.xRot = Mth.lerp(r, model.rightArm.xRot, ax + breathe);
        model.rightArm.yRot = Mth.lerp(r, model.rightArm.yRot, -ay);
        model.rightArm.zRot = Mth.lerp(r, model.rightArm.zRot, 0.0f);
        model.leftArm.xRot = Mth.lerp(r, model.leftArm.xRot, ax + breathe);
        model.leftArm.yRot = Mth.lerp(r, model.leftArm.yRot, ay);
        model.leftArm.zRot = Mth.lerp(r, model.leftArm.zRot, 0.0f);
        // Looking down into the book.
        model.head.xRot = Mth.lerp(r, model.head.xRot, Math.max(model.head.xRot, 0.35f));
    }
}
