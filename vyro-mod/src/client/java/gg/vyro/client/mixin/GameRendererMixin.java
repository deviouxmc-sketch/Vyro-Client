package gg.vyro.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import gg.vyro.client.config.VyroConfig;
import gg.vyro.client.features.Freelook;
import gg.vyro.client.features.Zoom;
import org.joml.Matrix4f;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Smooth zoom: scale the finished FOV value. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void vyro$zoomFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
        float m = Zoom.multiplier();
        if (m < 0.999f) {
            cir.setReturnValue(cir.getReturnValue() * m);
        }
    }

    /** Optional: no camera shake when you take damage. */
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void vyro$noDamageTilt(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (VyroConfig.get().features.disableDamageTilt) {
            ci.cancel();
        }
    }

    /** While freelooking in third person there is no first-person hand to draw. */
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void vyro$noHandInFreelook(float partialTick, boolean renderHand, Matrix4f projection, CallbackInfo ci) {
        if (Freelook.wantsThirdPerson()) {
            ci.cancel();
        }
    }
}
