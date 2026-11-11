package gg.vyro.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.vyro.client.config.FeatureConfig;
import gg.vyro.client.config.VyroConfig;
import gg.vyro.client.features.FireState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    /** Hide the first-person fire overlay, or push it down so it blocks less of the screen. */
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void vyro$fireStart(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        FeatureConfig f = VyroConfig.get().features;
        if (f.hideFireOverlay) {
            ci.cancel();
            return;
        }
        if (f.fireLowering > 0.0f) {
            poseStack.pushPose();
            poseStack.translate(0.0f, -f.fireLowering, 0.0f);
            FireState.pushed = true;
        }
    }

    @Inject(method = "renderFire", at = @At("TAIL"))
    private static void vyro$fireEnd(PoseStack poseStack, MultiBufferSource buffers, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (FireState.pushed) {
            poseStack.popPose();
            FireState.pushed = false;
        }
    }
}
