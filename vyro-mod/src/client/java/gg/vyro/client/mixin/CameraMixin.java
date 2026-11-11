package gg.vyro.client.mixin;

import gg.vyro.client.features.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /** Freelook in third person: the first boolean of Camera.setup(...) is `detached`. */
    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean vyro$detach(boolean detached) {
        return detached || Freelook.wantsThirdPerson();
    }

    /** Freelook: render from our own yaw/pitch instead of the entity's, easing back when released. */
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void vyro$setRotation(Camera instance, float yRot, float xRot) {
        Freelook.cameraHookRan();
        if (Freelook.active()) {
            this.setRotation(Freelook.yaw, Freelook.pitch);
        } else if (Freelook.returning()) {
            Freelook.Rot r = Freelook.returnStep(yRot, xRot);
            this.setRotation(r.yaw(), r.pitch());
        } else {
            Freelook.touchClock();
            this.setRotation(yRot, xRot);
        }
    }
}
