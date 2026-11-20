package com.reality.mobcraft.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.util.Optional;

/**
 * The Gloamite Upgrade template is the game's own netherite upgrade template, recoloured purple when the game loads
 * its textures. Nothing of Mojang's is shipped in the mod: the colours are changed on the player's own copy as it loads.
 * The plate turns twilight purple and the arrow turns soft lavender. If the game's texture can't be read, the mod's own
 * hand-drawn template is used instead.
 */
@Mixin(SpriteResourceLoader.class)
public interface PurpleTemplateMixin {

    @ModifyExpressionValue(method = "lambda$create$0", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/NativeImage;read(Ljava/io/InputStream;)Lcom/mojang/blaze3d/platform/NativeImage;"))
    private static NativeImage mobcraft$purpleTemplate(NativeImage image, @Local(argsOnly = true) Identifier id) {
        if (!"mobcraft".equals(id.getNamespace())) return image;
        if (id.getPath().matches(".*duskvein_anvil(_top)?(\\.png)?")) {
            String src = id.getPath().contains("duskvein_anvil_top") ? "anvil_top" : "anvil";
            return mobcraft$tinted(image, "textures/block/" + src + ".png");
        }
        if (!id.getPath().contains("gloamite_upgrade_smithing_template")) return image;
        Optional<Resource> vanilla = Minecraft.getInstance().getResourceManager()
                .getResource(Identifier.withDefaultNamespace("textures/item/netherite_upgrade_smithing_template.png"));
        if (vanilla.isEmpty()) return image;
        try (InputStream in = vanilla.get().open()) {
            NativeImage purple = NativeImage.read(in);
            float[] hsv = new float[3];
            for (int y = 0; y < purple.getHeight(); y++) {
                for (int x = 0; x < purple.getWidth(); x++) {
                    int argb = purple.getPixel(x, y);
                    int a = argb >>> 24;
                    if (a == 0) continue;
                    java.awt.Color.RGBtoHSB((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, hsv);
                    boolean gem = hsv[0] > 0.35f && hsv[0] < 0.65f;
                    int rgb = java.awt.Color.HSBtoRGB(gem ? 0.76f : 0.75f, gem ? hsv[1] * 0.55f : hsv[1], hsv[2]);
                    purple.setPixel(x, y, (a << 24) | (rgb & 0xFFFFFF));
                }
            }
            image.close();
            return purple;
        } catch (Exception e) {
            return image;
        }
    }

    /** The Duskvein Anvil: the game's own anvil texture, tinted twilight purple. */
    private static NativeImage mobcraft$tinted(NativeImage fallback, String vanillaPath) {
        Optional<Resource> vanilla = Minecraft.getInstance().getResourceManager().getResource(Identifier.withDefaultNamespace(vanillaPath));
        if (vanilla.isEmpty()) return fallback;
        try (InputStream in = vanilla.get().open()) {
            NativeImage img = NativeImage.read(in);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int argb = img.getPixel(x, y);
                    int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
                    img.setPixel(x, y, (argb & 0xFF000000) | ((r * 178 / 255) << 16) | ((g * 140 / 255) << 8) | Math.min(255, b * 236 / 255 + 14));
                }
            }
            fallback.close();
            return img;
        } catch (Exception e) {
            return fallback;
        }
    }
}
