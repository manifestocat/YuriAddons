package com.yuri.addons.mixin;

import com.yuri.addons.modules.SmoothRotations;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.Rotations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Rotations.class, remap = false)
public class RotationsMixin {
    @Inject(method = "rotate(DDIZLjava/lang/Runnable;)V", at = @At("HEAD"), cancellable = true)
    private static void onRotate(double yaw, double pitch, int priority, boolean clientSide, Runnable callback, CallbackInfo ci) {
        SmoothRotations module = Modules.get().get(SmoothRotations.class);

        if (module != null && module.isActive() && MeteorClient.mc.player != null) {
            module.addRotation(yaw, pitch, priority, clientSide, callback);
            ci.cancel();
        }
    }
}
