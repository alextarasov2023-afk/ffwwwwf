package fun.wonderful.mixin;

import fun.wonderful.Wonderful;
import fun.wonderful.client.modules.impl.render.ShaderSky;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.SkyRendering;

/**
 * Shader Sky: подменяет ванильный купол неба на космический шейдер.
 * Квад рисуется без теста глубины и без записи глубины — рельеф и
 * сущности рисуются позже и перекрывают его, поэтому туманность
 * видна только там, где должно быть небо.
 */
@Mixin(SkyRendering.class)
public class SkyRenderingMixin {

    @Inject(method = "renderSky(FFF)V", at = @At("HEAD"), cancellable = true)
    private void wonderful$cosmicSky(float red, float green, float blue, CallbackInfo ci) {
        if (Wonderful.INSTANCE == null || !Wonderful.isReady()) return;
        if (!ShaderSky.isCosmic()) return;

        ShaderSky.renderCosmic();
        ci.cancel();
    }
}
