package gg.vyro.client.mixin;

import gg.vyro.client.features.Freelook;
import gg.vyro.client.features.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    private boolean vyro$isLocalPlayer() {
        return (Object) this == Minecraft.getInstance().player;
    }

    /** Mouse look is slower while zoomed so aiming stays controllable. */
    @ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double vyro$scaleYaw(double value) {
        return vyro$isLocalPlayer() && Zoom.active() ? value * Math.max(0.15f, Zoom.multiplier()) : value;
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double vyro$scalePitch(double value) {
        return vyro$isLocalPlayer() && Zoom.active() ? value * Math.max(0.15f, Zoom.multiplier()) : value;
    }

    /** Freelook: mouse movement rotates the camera, not the player. */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void vyro$freelookTurn(double yRot, double xRot, CallbackInfo ci) {
        if (Freelook.active() && vyro$isLocalPlayer()) {
            Freelook.turn(yRot, xRot);
            ci.cancel();
        }
    }
}
