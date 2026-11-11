package gg.vyro.client.mixin;

import gg.vyro.client.config.VyroConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    /** Optional: remove the pumpkin-head screen overlay (other texture overlays are untouched). */
    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void vyro$noPumpkin(GuiGraphics graphics, Identifier texture, float alpha, CallbackInfo ci) {
        if (VyroConfig.get().features.noPumpkinOverlay && texture != null && texture.getPath().contains("pumpkinblur")) {
            ci.cancel();
        }
    }
}
