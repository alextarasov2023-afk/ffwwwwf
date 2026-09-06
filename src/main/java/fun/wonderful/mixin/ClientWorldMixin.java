package fun.wonderful.mixin;

import fun.wonderful.client.modules.impl.render.ShaderSky;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shader Sky: подкрашивает ванильный цвет неба/тумана в тон туманности,
 * чтобы горизонт не спорил с шейдерным небом.
 */
@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void wonderful$cosmicSkyColor(Vec3d cameraPos, float tickDelta,
                                          CallbackInfoReturnable<Integer> cir) {
        if (!ShaderSky.isCosmic()) return;
        cir.setReturnValue(ShaderSky.skyColor(cir.getReturnValue()));
    }
}
