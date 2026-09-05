package fun.wonderful.mixin;

import fun.wonderful.client.modules.impl.render.Sky;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Подмена цвета неба для модуля Sky: ванильный цвет плавно смешивается
 * с выбранным — перекрашивается и небо, и туман (fog считается от него).
 */
@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void wonderful$skyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Integer> cir) {
        int vanilla = cir.getReturnValueI();
        int mixed = Sky.mixSkyColor(vanilla);
        if (mixed != vanilla) {
            cir.setReturnValue(mixed);
        }
    }
}
